/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.input;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.PointF;
import androidx.annotation.VisibleForTesting;
import android.util.Range;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import androidx.annotation.Nullable;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.input.Swipe.Direction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides custom multi-pointer touch input support by processing MotionEvents and returning {@link
 * MultitouchResult} from {@link MultitouchHandler#onTouchEvent} if commission occurred.
 *
 * <p>A client uses this class by continuously forwarding MotionEvents to it via {@link
 * MultitouchHandler#onTouchEvent}. If that method decides that a commission has occurred, then it
 * will return a present {@link MultitouchResult}. There are 3 types of commission that can occur:
 * {@link MultitouchResult#TYPE_TAP}, {@link MultitouchResult#TYPE_SWIPE}, {@link
 * MultitouchResult#TYPE_HOLD}.
 *
 * <p>A {@link MultitouchResult#TYPE_TAP} occurs if the finger(s) press down and eventually all lift
 * up with the final pointer having not travelled too long a distance.
 *
 * <p>A {@link MultitouchResult#TYPE_SWIPE} occurs if the finger(s) press down and eventually all
 * lift up with the final pointer having travelled a sufficient distance, with sufficient speed.
 * Motions that would otherwise produce a Swipe but are not cardinal enough, or not fast enough, do
 * not produce a Swipe, nor do they produce anything else.
 *
 * <p>A {@link MultitouchResult#TYPE_HOLD} occurs if the finger(s) press down and no new pointers
 * are added or removed for a fixed duration, provided that a {@link HoldRecognizer} has been
 * provided to decide how many pointers are needed in order for a Hold to occur.
 */
class MultitouchHandler {

  private static final String TAG = "MultitouchHandler";

  /**
   * Pointers that were released this close in time to the final pointer release are considered
   * recent enough to contribute to a {@link MultitouchResult#TYPE_TAP} or {@link
   * MultitouchResult#TYPE_SWIPE}, even if accumulation mode is false.
   */
  private static final int RECENCY_MAX_MS = 250;

  /** A Tap is not recognizable if any of the pointers travelled more than this distance. */
  private static final int TAP_MAX_DISTANCE_MM = 5;

  /**
   * A Swipe is not recognizable if the max ratio of the x and y components of the absolute
   * displacement vector (between final and initial points) is less than the following threshold.
   * For example, a value of 1.0f means that a perfectly diagonal swipe is recognizable. A value of
   * 2.0f means that the distance travelled in the dominant direction must be twice as long as the
   * distance travelled in the non-dominant direction. This value must exceed 1.0f.
   */
  private static final float SWIPE_MIN_RATIO = 2.0f;

  /**
   * A Swipe is not recognizable if the speed in the dominant direction does not exceed the
   * following threshold.
   */
  private static final int SWIPE_MIN_SPEED_MM_PER_SECOND = 45;

  /**
   * A Swipe is not recognizable if the distance from initial to final point in the dominant
   * direction does not exceed the following threshold.
   */
  private static final int SWIPE_MIN_DISTANCE_MM = 9;

  /**
   * Pointers held for this duration, without additional pointers added or removed, qualify as a
   * possible hold. Whether a hold is actually produced depends on the {@link HoldRecognizer}.
   */
  private static final long HOLD_MIN_DURATION_MS = 2000;

  /** Maps pointerId's to active PointerWithHistory objects. */
  @SuppressLint("UseSparseArrays")
  private final HashMap<Integer, PointerWithHistory> activePointers = new HashMap<>();

  /** Maps pointerId's to inactive PointerWithHistory objects. */
  @SuppressLint("UseSparseArrays")
  private final HashMap<Integer, PointerWithHistory> inactivePointers = new HashMap<>();

  private boolean isAccumulationMode;

  private float tapMaxDistancePixels;

  private final VelocityTracker velocityTracker;
  private float swipeMinSpeedPixelsPerSecond;
  private float swipeMinDistancePixels;

  private long holdStartTimeInMillis;
  private final HoldRecognizer holdRecognizer;
  private boolean isHoldInProgress;
  private long holdDurationMinMillis;

  /** Interface for recognizing a Hold. */
  interface HoldRecognizer {

    /**
     * Return true if, in the current context, a Hold should occur.
     *
     * @param pointersHeldCount how many pointers are currently being held
     */
    boolean isHoldRecognized(int pointersHeldCount);
  }

  /** Constructs a MultitouchHandler with an optional {@link HoldRecognizer}. */
  MultitouchHandler(Resources resources, @Nullable HoldRecognizer holdRecognizer) {
    tapMaxDistancePixels = Utils.mmToPixels(resources, TAP_MAX_DISTANCE_MM);
    swipeMinSpeedPixelsPerSecond = Utils.mmToPixels(resources, SWIPE_MIN_SPEED_MM_PER_SECOND);
    swipeMinDistancePixels = Utils.mmToPixels(resources, SWIPE_MIN_DISTANCE_MM);
    holdDurationMinMillis = HOLD_MIN_DURATION_MS;
    isAccumulationMode = false;
    velocityTracker = VelocityTracker.obtain();
    this.holdRecognizer = holdRecognizer;
  }

  /**
   * Sets the accumulation mode on or off.
   *
   * <p>In accumulation mode, all pointers pressed after the initial pointer will contribute to a
   * potential final commission regardless of when they are released. With accumulation mode off,
   * only those pointers released just prior to the release of the initial pointer will contribute
   * to a potential final commission.
   */
  void setAccumulationMode(boolean accumulationMode) {
    this.isAccumulationMode = accumulationMode;
  }

  /** Gets the accumulation mode. */
  boolean isAccumulationMode() {
    return isAccumulationMode;
  }

  /** Gets a copy of the currently active touch points. */
  List<PointF> getActivePoints() {
    return activePointers.values().stream()
        .map(pointerWithHistory -> pointerWithHistory.pointCurrent)
        .collect(Collectors.toList());
  }

  /**
   * Processes touch input and returns a {@link MultitouchResult} if commission occurred.
   *
   * <p>The types of commission are discussed atop this file. If no commission has occurred, then an
   * empty {@link Optional) is returned.
   *
   * <p>If a {@code TYPE_HOLD} has occurred, then all subsequent calls to this method will return
   * null until the motion is resolved by either being cancelled or the final pointer has come up.
   *
   * @param event the {@link MotionEvent} as received by the owning view.
   */
  Optional<MultitouchResult> onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    int actionPointerIndex = event.getActionIndex();
    int actionPointerId = event.getPointerId(actionPointerIndex);
    long eventTime = event.getEventTime();

    // Update the active pointers
    if (!activePointers.isEmpty()) {
      if (action == MotionEvent.ACTION_MOVE) {
        // All of the PointerWithHistory objects get updated because ACTION_MOVE events are not sent
        // on a per pointer basis (instead they ride along the initial 'action' pointer).
        for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
          PointerWithHistory pointer = activePointers.get(event.getPointerId(pointerIndex));
          pointer.updateCurrentPoint(
              (int) event.getX(pointerIndex), (int) event.getY(pointerIndex));
        }
      }
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
        PointerWithHistory pointer = activePointers.get(actionPointerId);
        if (pointer != null) {
          pointer.updateCurrentPoint(
              (int) event.getX(actionPointerIndex), (int) event.getY(actionPointerIndex));
        }
      }
    }
    // The hold mode is exited on either ACTION_UP or ACTION_CANCEL.
    if (isHoldInProgress) {
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        isHoldInProgress = false;
      }
      return Optional.empty();
    }

    velocityTracker.addMovement(event);

    if (action == MotionEvent.ACTION_MOVE) {
      // Though it may seem counter-intuitive, ACTION_MOVE events are used here to determine whether
      // a HOLD is produced, which works well in practice; otherwise, the continuous scheduling of
      // short-term alarms (probably via a Handler) would be needed.
      if (eventTime - holdStartTimeInMillis >= holdDurationMinMillis
          && holdRecognizer != null
          && holdRecognizer.isHoldRecognized(getActivePoints().size())) {
        MultitouchResult result = MultitouchResult.createHold(getActivePoints());
        clearPointerCollections();
        isHoldInProgress = true;
        return Optional.of(result);
      }

    } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
      // ACTION_DOWN: initial pointer now down; ACTION_POINTER_DOWN: non-initial pointer now down.
      if (action == MotionEvent.ACTION_DOWN) {
        velocityTracker.clear();
      }
      holdStartTimeInMillis = eventTime;
      PointF point = new PointF(event.getX(actionPointerIndex), event.getY(actionPointerIndex));
      PointerWithHistory pointerWithHistory = new PointerWithHistory(actionPointerId, point);
      activePointers.put(actionPointerId, pointerWithHistory);

    } else if (action == MotionEvent.ACTION_CANCEL) {
      clearPointerCollections();

    } else if (action == MotionEvent.ACTION_POINTER_UP) {
      // ACTION_POINTER_UP: non-final pointer was released.
      holdStartTimeInMillis = eventTime;
      transferPointerToInactive(actionPointerId, eventTime);

    } else if (action == MotionEvent.ACTION_UP) {
      // ACTION_UP: final pointer was released.
      Optional<MultitouchResult> result = onFinalPointerUp(actionPointerId, eventTime);
      clearPointerCollections();
      return result;
    }
    return Optional.empty();
  }

  private Optional<MultitouchResult> onFinalPointerUp(int actionPointerId, long eventTime) {
    PointerWithHistory finalPointer = activePointers.get(actionPointerId);
    if (finalPointer == null) {
      return Optional.empty();
    }
    transferPointerToInactive(actionPointerId, eventTime);
    List<PointF> recentlyInactivatedPoints = getRecentlyInactivatedPoints(eventTime);
    int contributorCount = recentlyInactivatedPoints.size();
    float xDiff = finalPointer.pointCurrent.x - finalPointer.pointInitial.x;
    float yDiff = finalPointer.pointCurrent.y - finalPointer.pointInitial.y;
    float xExcess = Math.abs(xDiff) - swipeMinDistancePixels;
    float yExcess = Math.abs(yDiff) - swipeMinDistancePixels;

    // Check if the motion is a legitimate swipe.
    if (xExcess > 0
        && yExcess > 0
        && !Utils.isVectorNearlyCardinal(new PointF(xDiff, yDiff), SWIPE_MIN_RATIO)) {
      // Both x and y displacement thresholds were met, but the vector is too diagonal.
      return Optional.empty();
    }
    Speed speed = computeSpeedPixelsPerSecond(finalPointer.pointerId);
    if (xExcess > 0 && (xExcess > yExcess)) {
      // X displacement threshold was met, and exceeds y displacement.
      if (speed.x < swipeMinSpeedPixelsPerSecond
          || !fingersTravelSameDirection(
              pointer -> pointer.pointCurrent.x - pointer.pointInitial.x)) {
        // Not quick enough or fingers moving different directions.
        return Optional.empty();
      }
      return Optional.of(
          MultitouchResult.createSwipe(
              new Swipe(xDiff < 0 ? Direction.LEFT : Direction.RIGHT, contributorCount)));
    }
    if (yExcess > 0 && (yExcess > xExcess)) {
      // Y displacement threshold was met, and exceeds x displacement.
      if (speed.y < swipeMinSpeedPixelsPerSecond
          || !fingersTravelSameDirection(
              pointer -> pointer.pointCurrent.y - pointer.pointInitial.y)) {
        // Not quick enough or fingers moving different directions.
        return Optional.empty();
      }
      return Optional.of(
          MultitouchResult.createSwipe(
              new Swipe(yDiff < 0 ? Direction.UP : Direction.DOWN, contributorCount)));
    }
    // Check if the motion is a legitimate tap.
    if (getMaximumDistanceMovedAmongInactivePointers() > tapMaxDistancePixels) {
      return Optional.empty();
    }
    return Optional.of(MultitouchResult.createTap(recentlyInactivatedPoints));
  }

  private boolean fingersTravelSameDirection(
      Function<PointerWithHistory, Float> directionProvider) {
    List<PointerWithHistory> points = new ArrayList<>(inactivePointers.values());
    int signedAccumulation =
        points.stream()
            .mapToInt(point -> Integer.signum(directionProvider.apply(point).intValue()))
            .sum();
    return Math.abs(signedAccumulation) == points.size();
  }

  private double getMaximumDistanceMovedAmongInactivePointers() {
    return inactivePointers.values().stream()
        .mapToDouble(PointerWithHistory::distanceMoved)
        .max()
        .orElse(Double.MAX_VALUE);
  }

  private Speed computeSpeedPixelsPerSecond(int pointerId) {
    // Invoke computeCurrentVelocity with 1000 because we want units to be in pixels / second.
    velocityTracker.computeCurrentVelocity(1000);
    return new Speed(
        Math.abs(velocityTracker.getXVelocity(pointerId)),
        Math.abs(velocityTracker.getYVelocity(pointerId)));
  }

  private void clearPointerCollections() {
    activePointers.clear();
    inactivePointers.clear();
  }

  private List<PointF> getRecentlyInactivatedPoints(long eventTime) {
    long now = eventTime;
    Range<Long> recentRange = new Range<>(now - RECENCY_MAX_MS, now);
    return inactivePointers.values().stream()
        .filter(
            pointerWithHistory ->
                isAccumulationMode || recentRange.contains(pointerWithHistory.momentMadeInactive))
        .map(pointerWithHistory -> pointerWithHistory.pointCurrent)
        .collect(Collectors.toList());
  }

  private void transferPointerToInactive(int pointerId, long eventTime) {
    PointerWithHistory pointerWithHistory = activePointers.get(pointerId);
    if (pointerWithHistory != null) {
      pointerWithHistory.markReleased(eventTime);
      inactivePointers.put(pointerId, pointerWithHistory);
      activePointers.remove(pointerId);
    }
  }

  private static class Speed {
    final float x;
    final float y;

    private Speed(float x, float y) {
      this.x = x;
      this.y = y;
    }
  }

  private static class PointerWithHistory {
    final int pointerId;
    final PointF pointInitial;
    final PointF pointCurrent;
    long momentMadeInactive;

    private PointerWithHistory(int pointerId, PointF pointInitial) {
      this.pointerId = pointerId;
      this.pointInitial = new PointF(pointInitial.x, pointInitial.y);
      this.pointCurrent = new PointF(pointInitial.x, pointInitial.y);
    }

    private void markReleased(long eventTime) {
      this.momentMadeInactive = eventTime;
    }

    private void updateCurrentPoint(int x, int y) {
      pointCurrent.x = x;
      pointCurrent.y = y;
    }

    private double distanceMoved() {
      return Utils.distance(pointCurrent, pointInitial);
    }
  }

  @VisibleForTesting
  void testing_set_holdDurationMinMillis(int holdDurationMinMillis) {
    this.holdDurationMinMillis = holdDurationMinMillis;
  }

  @VisibleForTesting
  void testing_set_tapMaxDistancePixels(int tapMaxDistancePixels) {
    this.tapMaxDistancePixels = tapMaxDistancePixels;
  }

  @VisibleForTesting
  void testing_set_swipeMinDistance(int swipeMinDistancePixels) {
    this.swipeMinDistancePixels = swipeMinDistancePixels;
  }

  @VisibleForTesting
  void testing_set_swipeMinSpeedPixelsPerSecond(int swipeMinSpeedPixelsPerSecond) {
    this.swipeMinSpeedPixelsPerSecond = swipeMinSpeedPixelsPerSecond;
  }
}
