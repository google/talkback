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
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Range;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.FeatureFlagReader;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.input.Swipe.Direction;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
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

  private final Handler handler = new Handler();
  private final HoldRecognizer holdRecognizer;
  private final MultitouchResultListener multitouchResultListener;

  private boolean isAccumulationMode;
  private boolean isProcessed;

  private float tapMaxDistancePixels;
  private float swipeMinSpeedPixelsPerSecond;
  private float swipeMinDistancePixels;

  private long longHoldDurationMinMillis;

  /** Interface for recognizing a Hold. */
  interface HoldRecognizer {
    /**
     * Returns true if, in the current context, a Hold for calibration should occur.
     *
     * @param pointersHeldCount how many pointers are currently being held
     */
    boolean isCalibrationHoldRecognized(int pointersHeldCount);
    /**
     * Return true if, in the current context, a Hold should occur.
     *
     * @param pointersHeldCount how many pointers are currently being held
     */
    boolean isHoldRecognized(int pointersHeldCount);
  }

  /** Listens multi-touch events. */
  interface MultitouchResultListener {
    @CanIgnoreReturnValue
    boolean detect(Optional<MultitouchResult> resultOptional);
  }

  /** Constructs a MultitouchHandler with an optional {@link HoldRecognizer}. */
  MultitouchHandler(
      Resources resources,
      @Nullable HoldRecognizer holdRecognizer,
      MultitouchResultListener multitouchResultListener) {
    tapMaxDistancePixels = Utils.mmToPixels(resources, TAP_MAX_DISTANCE_MM);
    swipeMinSpeedPixelsPerSecond = Utils.mmToPixels(resources, SWIPE_MIN_SPEED_MM_PER_SECOND);
    swipeMinDistancePixels = Utils.mmToPixels(resources, SWIPE_MIN_DISTANCE_MM);
    longHoldDurationMinMillis = HOLD_MIN_DURATION_MS;
    this.multitouchResultListener = multitouchResultListener;
    isAccumulationMode = false;
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

  private final Runnable tapOrSwipeRunnable =
      new Runnable() {
        @Override
        public void run() {
          BrailleImeLog.logD(TAG, "tap or swipe task is running.");
          Optional<Swipe> swipe =
              createSwipe(
                  getLastRecentlyInactivatedPointsHistory(SystemClock.uptimeMillis()),
                  getRecentlyInactivatedPointsHistory(SystemClock.uptimeMillis()));
          BrailleImeLog.logD(TAG, "swipe is present: " + swipe.isPresent());
          if (swipe.isPresent()) {
            multitouchResultListener.detect(
                Optional.of(
                    MultitouchResult.createSwipe(
                        swipe.get(),
                        getRecentlyInactivatedInitialPoints(SystemClock.uptimeMillis()))));
          } else if (getMaximumDistanceMovedAmongInactivePointers() <= tapMaxDistancePixels) {
            // Check if the motion is a legitimate tap.
            multitouchResultListener.detect(
                Optional.of(
                    MultitouchResult.createTap(
                        getRecentlyInactivatedCurrentPoints(SystemClock.uptimeMillis()))));
          }
          clearPointerCollections();
          handler.removeCallbacksAndMessages(null);
        }
      };

  private final Runnable holdRunnable =
      new Runnable() {
        @Override
        public void run() {
          BrailleImeLog.logD(TAG, "hold task is running.");
          if (holdRecognizer != null && holdRecognizer.isHoldRecognized(getActivePoints().size())) {
            for (MultitouchHandler.PointerWithHistory value : activePointers.values()) {
              value.isHoldInProgress = true;
            }
            Optional<PointerWithHistory> inactivePointHistory =
                getLastRecentlyInactivatedPointsHistory(
                    SystemClock.uptimeMillis() - ViewConfiguration.getLongPressTimeout());
            if (inactivePointHistory.isPresent()
                && !inactivePointHistory.get().isHoldInProgress
                && !holdRecognizer.isHoldRecognized(getActivePoints().size() + 1)) {
              return;
            }
            isProcessed =
                multitouchResultListener.detect(
                    Optional.of(MultitouchResult.createHold(getHeldPoints())));
            BrailleImeLog.logD(TAG, "hold result: " + isProcessed);
          }
        }
      };

  private final Runnable longHoldRunnable =
      new Runnable() {
        @Override
        public void run() {
          BrailleImeLog.logD(TAG, "long hold task is running.");
          if (holdRecognizer != null
              && holdRecognizer.isCalibrationHoldRecognized(getActivePoints().size())) {
            MultitouchResult result = MultitouchResult.createCalibrationHold(getActivePoints());
            isProcessed = multitouchResultListener.detect(Optional.of(result));
            BrailleImeLog.logD(TAG, "long hold result: " + isProcessed);
          }
        }
      };

  private final Runnable holdAndSwipeRunnable =
      new Runnable() {
        @Override
        public void run() {
          BrailleImeLog.logD(TAG, "hold and swipe is running.");
          List<PointF> heldPoints = getHeldPoints();
          if (!heldPoints.isEmpty()) {
            if (heldPoints.size() == getActivePoints().size()) {
              Optional<Swipe> swipe =
                  createSwipe(
                      getLastRecentlyInactivatedPointsHistory(SystemClock.uptimeMillis()),
                      getRecentlyInactivatedPointsHistory(SystemClock.uptimeMillis()));
              BrailleImeLog.logD(
                  TAG, "swipe is present: " + (swipe.isPresent() ? swipe.get() : false));
              if (swipe.isPresent()) {
                MultitouchResult result =
                    MultitouchResult.createHoldAndDotSwipe(
                        getActivePoints(),
                        swipe.get(),
                        getRecentlyInactivatedInitialPoints(SystemClock.uptimeMillis()));
                isProcessed = multitouchResultListener.detect(Optional.of(result));
                BrailleImeLog.logD(TAG, "hold and swipe result: " + isProcessed);
                handler.removeCallbacksAndMessages(null);
              }
            }
          }
        }
      };

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
  boolean onTouchEvent(Context context, MotionEvent event) {
    int action = event.getActionMasked();
    int actionPointerIndex = event.getActionIndex();
    int actionPointerId = event.getPointerId(actionPointerIndex);
    long eventTime = event.getEventTime();

    if (!activePointers.isEmpty()) {
      // Update the active pointers
      // All of the PointerWithHistory objects get updated because ACTION_MOVE events are not sent
      // on a per pointer basis (instead they ride along the initial 'action' pointer).
      for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
        PointerWithHistory pointerWithHistory =
            activePointers.get(event.getPointerId(pointerIndex));
        if (pointerWithHistory == null) {
          pointerWithHistory =
              new PointerWithHistory(
                  actionPointerId,
                  new PointF(event.getX(actionPointerIndex), event.getY(actionPointerIndex)),
                  eventTime);
          activePointers.put(actionPointerId, pointerWithHistory);
        } else {
          pointerWithHistory.updateCurrentPoint(
              (int) event.getX(pointerIndex), (int) event.getY(pointerIndex));
        }
      }
    }

    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
      isProcessed = false;
      handler.removeCallbacksAndMessages(null);
      PointF point = new PointF(event.getX(actionPointerIndex), event.getY(actionPointerIndex));
      PointerWithHistory pointerWithHistory =
          new PointerWithHistory(actionPointerId, point, eventTime);
      activePointers.put(actionPointerId, pointerWithHistory);
      if (FeatureFlagReader.useHoldAndSwipeGesture(context)) {
        handler.postDelayed(holdRunnable, ViewConfiguration.getLongPressTimeout());
      }
      handler.postDelayed(longHoldRunnable, longHoldDurationMinMillis);
    } else if (action == MotionEvent.ACTION_CANCEL) {
      clearPointerCollections();
      handler.removeCallbacksAndMessages(null);
    } else if (action == MotionEvent.ACTION_POINTER_UP) {
      // ACTION_POINTER_UP: non-final pointer was released.
      transferPointerToInactive(actionPointerId, eventTime);
      handler.removeCallbacksAndMessages(null);
      if (FeatureFlagReader.useHoldAndSwipeGesture(context)) {
        handler.post(holdAndSwipeRunnable);
        handler.postDelayed(holdRunnable, ViewConfiguration.getLongPressTimeout());
      }
      handler.postDelayed(longHoldRunnable, longHoldDurationMinMillis);
    } else if (action == MotionEvent.ACTION_UP) {
      // ACTION_UP: final pointer was released.
      if (isProcessed) {
        clearPointerCollections();
      } else {
        // If no previous callback is processed.
        transferPointerToInactive(actionPointerId, eventTime);
        handler.post(tapOrSwipeRunnable);
      }
    }
    // Must be true so that next touch event would come.
    return true;
  }

  private Optional<Swipe> createSwipe(
      Optional<PointerWithHistory> finalPointer,
      List<PointerWithHistory> recentlyInactivatedPoints) {
    if (!finalPointer.isPresent()) {
      return Optional.empty();
    }
    float xDiff = finalPointer.get().pointCurrent.x - finalPointer.get().pointInitial.x;
    float yDiff = finalPointer.get().pointCurrent.y - finalPointer.get().pointInitial.y;
    float xExcess = Math.abs(xDiff) - swipeMinDistancePixels;
    float yExcess = Math.abs(yDiff) - swipeMinDistancePixels;
    // Check if the motion is a legitimate swipe.
    if (xExcess > 0
        && yExcess > 0
        && !Utils.isVectorNearlyCardinal(new PointF(xDiff, yDiff), SWIPE_MIN_RATIO)) {
      // Both x and y displacement thresholds were met, but the vector is too diagonal.
      return Optional.empty();
    }
    Optional<Direction> optionalDirection = getDirection(xExcess, yExcess, xDiff, yDiff);
    if (!optionalDirection.isPresent()) {
      return Optional.empty();
    }
    Direction direction = optionalDirection.get();
    Speed speed = inactivePointers.get(finalPointer.get().pointerId).computeSpeed();
    if (direction == Direction.LEFT || direction == Direction.RIGHT) {
      // X displacement threshold was met, and exceeds y displacement.
      if (speed.x < swipeMinSpeedPixelsPerSecond
          || !fingersTravelSameDirection(
              recentlyInactivatedPoints,
              pointer -> pointer.pointCurrent.x - pointer.pointInitial.x)) {
        // Not quick enough or fingers moving different directions.
        return Optional.empty();
      }
    } else if (direction == Direction.UP || direction == Direction.DOWN) {
      // Y displacement threshold was met, and exceeds x displacement.
      if (speed.y < swipeMinSpeedPixelsPerSecond
          || !fingersTravelSameDirection(
              recentlyInactivatedPoints,
              pointer -> pointer.pointCurrent.y - pointer.pointInitial.y)) {
        // Not quick enough or fingers moving different directions.
        return Optional.empty();
      }
    }
    return Optional.of(new Swipe(direction, recentlyInactivatedPoints.size()));
  }

  private Optional<Direction> getDirection(float xExcess, float yExcess, float xDiff, float yDiff) {
    if (xExcess > 0 && (xExcess > yExcess)) {
      return Optional.of(xDiff < 0 ? Direction.LEFT : Direction.RIGHT);
    }
    if (yExcess > 0 && (yExcess > xExcess)) {
      return Optional.of(yDiff < 0 ? Direction.UP : Direction.DOWN);
    }
    return Optional.empty();
  }

  private boolean fingersTravelSameDirection(
      List<PointerWithHistory> recentlyInactivatedPoints,
      Function<PointerWithHistory, Float> directionProvider) {
    int signedAccumulation =
        recentlyInactivatedPoints.stream()
            .mapToInt(point -> Integer.signum(directionProvider.apply(point).intValue()))
            .sum();
    return Math.abs(signedAccumulation) == recentlyInactivatedPoints.size();
  }

  private double getMaximumDistanceMovedAmongInactivePointers() {
    return inactivePointers.values().stream()
        .mapToDouble(PointerWithHistory::distanceMoved)
        .max()
        .orElse(Double.MAX_VALUE);
  }

  private void clearPointerCollections() {
    activePointers.clear();
    inactivePointers.clear();
    isProcessed = false;
  }

  private List<PointF> getRecentlyInactivatedCurrentPoints(long eventTime) {
    Range<Long> recentRange = new Range<>(eventTime - RECENCY_MAX_MS, eventTime);
    return inactivePointers.values().stream()
        .filter(
            pointerWithHistory ->
                isAccumulationMode || recentRange.contains(pointerWithHistory.momentMadeInactive))
        .map(pointerWithHistory -> pointerWithHistory.pointCurrent)
        .collect(Collectors.toList());
  }

  private List<PointF> getRecentlyInactivatedInitialPoints(long eventTime) {
    Range<Long> recentRange = new Range<>(eventTime - RECENCY_MAX_MS, eventTime);
    return inactivePointers.values().stream()
        .filter(pointerWithHistory -> recentRange.contains(pointerWithHistory.momentMadeInactive))
        .map(pointerWithHistory -> pointerWithHistory.pointInitial)
        .collect(Collectors.toList());
  }

  private List<PointerWithHistory> getRecentlyInactivatedPointsHistory(long eventTime) {
    long now = eventTime;
    Range<Long> recentRange = new Range<>(now - RECENCY_MAX_MS, now);
    return inactivePointers.values().stream()
        .filter(pointerWithHistory -> recentRange.contains(pointerWithHistory.momentMadeInactive))
        .collect(Collectors.toList());
  }

  private Optional<PointerWithHistory> getLastRecentlyInactivatedPointsHistory(long eventTime) {
    Range<Long> recentRange = new Range<>(eventTime - RECENCY_MAX_MS, eventTime);
    return inactivePointers.values().stream()
        .filter(pointerWithHistory -> recentRange.contains(pointerWithHistory.momentMadeInactive))
        .findFirst();
  }

  private List<PointF> getHeldPoints() {
    return activePointers.values().stream()
        .filter(pointerWithHistory -> pointerWithHistory.isHoldInProgress)
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
    long momentMadeInitial;
    boolean isHoldInProgress;

    private PointerWithHistory(int pointerId, PointF pointInitial, long initialEventTime) {
      this.pointerId = pointerId;
      this.pointInitial = new PointF(pointInitial.x, pointInitial.y);
      this.pointCurrent = new PointF(pointInitial.x, pointInitial.y);
      momentMadeInitial = initialEventTime;
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

    private Speed computeSpeed() {
      double pointerDurationInSeconds = pointerDurationInMillis() / (double) 1000;
      try {
        return new Speed(
            (float) Math.abs(displacementX() / pointerDurationInSeconds),
            (float) Math.abs(displacementY() / pointerDurationInSeconds));
      } catch (ArithmeticException exception) {
        BrailleImeLog.logE(
            TAG, "Divided by zero: pointerDurationInSeconds = " + pointerDurationInSeconds);
        return new Speed(0, 0);
      }
    }

    private float displacementX() {
      return pointCurrent.x - pointInitial.x;
    }

    private float displacementY() {
      return pointCurrent.y - pointInitial.y;
    }

    private long pointerDurationInMillis() {
      return momentMadeInactive - momentMadeInitial;
    }
  }

  @VisibleForTesting
  void testing_set_holdDurationMinMillis(int holdDurationMinMillis) {
    this.longHoldDurationMinMillis = holdDurationMinMillis;
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
