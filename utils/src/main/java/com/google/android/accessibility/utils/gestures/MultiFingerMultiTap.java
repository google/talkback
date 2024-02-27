/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils.gestures;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class matches multi-finger multi-tap gestures. The number of fingers and the number of taps
 * for each instance is specified in the constructor.
 */
class MultiFingerMultiTap extends GestureMatcher {

  // The target number of taps.
  final int mTargetTapCount;
  // The target number of fingers.
  final int targetFingerCount;
  // The acceptable distance between two taps of a finger.
  private int doubleTapSlop;
  private int doubleTapTimeout;
  private int tapTimeout;
  // The acceptable distance the pointer can move and still count as a tap.
  private int touchSlop;
  // A tap counts when target number of fingers are down and up once.
  protected int completedTapCount;
  // A flag set to true when target number of fingers have touched down at once before.
  // Used to indicate what next finger action should be. Down when false and lift when true.
  protected boolean isTargetFingerCountReached = false;
  // Store initial down points for slop checking and update when next down if is inside slop.
  private PointF[] bases;
  // The points in bases that already have slop checked when onDown or onPointerDown.
  // It prevents excluded points matched multiple times by other pointers from next check.
  private ArrayList<PointF> excludedPointsForDownSlopChecked;
  private long lastDownTime;
  private long lastUpTime;

  /**
   * @throws IllegalArgumentException if <code>fingers<code/> is less than 2
   *                                  or <code>taps<code/> is not positive.
   */
  MultiFingerMultiTap(
      Context context,
      int fingers,
      int taps,
      int gestureId,
      GestureMatcher.StateChangeListener listener) {
    super(gestureId, new Handler(context.getMainLooper()), listener);
    mTargetTapCount = taps;
    targetFingerCount = fingers;
    doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop() * fingers;
    doubleTapTimeout = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS;
    tapTimeout = targetFingerCount * ViewConfiguration.getTapTimeout();
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * fingers;

    bases = new PointF[targetFingerCount];
    for (int i = 0; i < bases.length; i++) {
      bases[i] = new PointF();
    }
    excludedPointsForDownSlopChecked = new ArrayList<>(targetFingerCount);
    clear();
  }

  @Override
  public void clear() {
    completedTapCount = 0;
    isTargetFingerCountReached = false;
    for (int i = 0; i < bases.length; i++) {
      bases[i].set(Float.NaN, Float.NaN);
    }
    excludedPointsForDownSlopChecked.clear();
    lastDownTime = Long.MAX_VALUE;
    lastUpTime = Long.MAX_VALUE;
    super.clear();
  }

  @Override
  protected void onDown(MotionEvent event) {
    // Before the matcher state transit to completed,
    // Cancel when an additional down arrived after reaching the target number of taps.
    if (completedTapCount == mTargetTapCount) {
      cancelGesture(event);
      return;
    }
    long timeDelta = event.getEventTime() - lastUpTime;
    if (timeDelta > doubleTapTimeout) {
      cancelGesture(event);
      return;
    }
    lastDownTime = event.getEventTime();
    if (completedTapCount == 0) {
      initBaseLocation(event);
      return;
    }
    // As fingers go up and down, their pointer ids will not be the same.
    // Therefore we require that a given finger be in slop range of any one
    // of the fingers from the previous tap.
    final PointF nearest = findNearestPoint(event, doubleTapSlop, true);
    if (nearest != null) {
      // Update pointer location to nearest one as a new base for next slop check.
      final int index = event.getActionIndex();
      nearest.set(event.getX(index), event.getY(index));
    } else {
      cancelGesture(event);
    }
  }

  @Override
  protected void onUp(MotionEvent event) {
    // Because this is a multi-finger gesture, we must have received ACTION_POINTER_UP before this
    // so we calculate timeDelta relative to lastUpTime.
    long timeDelta = event.getEventTime() - lastUpTime;
    if (timeDelta > tapTimeout) {
      cancelGesture(event);
      return;
    }
    lastUpTime = event.getEventTime();
    final PointF nearest = findNearestPoint(event, touchSlop, false);
    if ((getState() == STATE_GESTURE_STARTED || getState() == STATE_CLEAR) && null != nearest) {
      // Increase current tap count when the user have all fingers lifted
      // within the tap timeout since the target number of fingers are down.
      if (isTargetFingerCountReached) {
        completedTapCount++;
        isTargetFingerCountReached = false;
        excludedPointsForDownSlopChecked.clear();
      }

      // Start gesture detection here to avoid the conflict to 2nd finger double tap
      // that never actually started gesture detection.
      if (completedTapCount == 1) {
        startGesture(event);
      }
      if (completedTapCount == mTargetTapCount) {
        // Done.
        completeAfterDoubleTapTimeout(event);
      }
    } else {
      // Either too many taps or nonsensical event stream.
      cancelGesture(event);
    }
  }

  @Override
  protected void onMove(MotionEvent event) {
    // Outside the touch slop
    if (null == findNearestPoint(event, touchSlop, false)) {
      cancelGesture(event);
    }
  }

  @Override
  protected void onPointerDown(MotionEvent event) {
    // Reset timeout to ease the use for some people
    // with certain impairments to get all their fingers down.
    long timeDelta = event.getEventTime() - lastDownTime;
    if (timeDelta > tapTimeout) {
      cancelGesture(event);
      return;
    }
    lastDownTime = event.getEventTime();
    final int currentFingerCount = event.getPointerCount();
    // Accept down only before target number of fingers are down
    // or the finger count is not more than target.
    if ((currentFingerCount > targetFingerCount) || isTargetFingerCountReached) {
      isTargetFingerCountReached = false;
      cancelGesture(event);
      return;
    }

    final PointF nearest;
    if (completedTapCount == 0) {
      nearest = initBaseLocation(event);
    } else {
      nearest = findNearestPoint(event, doubleTapSlop, true);
    }
    if ((getState() == STATE_GESTURE_STARTED || getState() == STATE_CLEAR) && nearest != null) {
      // The user have all fingers down within the tap timeout since first finger down,
      // setting the timeout for fingers to be lifted.
      if (currentFingerCount == targetFingerCount) {
        isTargetFingerCountReached = true;
      }
      // Update pointer location to nearest one as a new base for next slop check.
      final int index = event.getActionIndex();
      nearest.set(event.getX(index), event.getY(index));
    } else {
      cancelGesture(event);
    }
  }

  @Override
  protected void onPointerUp(MotionEvent event) {
    // Accept up only after target number of fingers are down.
    if (!isTargetFingerCountReached) {
      cancelGesture(event);
      return;
    }

    if (getState() == STATE_GESTURE_STARTED || getState() == STATE_CLEAR) {
      // Needs more fingers lifted within the tap timeout
      // after reaching the target number of fingers are down.
      // Calculate timeDelta relative to whichever baseline is most recent, lastUpTime or
      // lastDownTime.
      long timeDelta = event.getEventTime() - Math.max(lastDownTime, lastUpTime);
      if (timeDelta > tapTimeout) {
        cancelGesture(event);
        return;
      }
      lastUpTime = event.getEventTime();
    } else {
      cancelGesture(event);
    }
  }

  @Override
  public String getGestureName() {
    final StringBuilder builder = new StringBuilder();
    builder.append(targetFingerCount).append("-Finger ");
    if (mTargetTapCount == 1) {
      builder.append("Single");
    } else if (mTargetTapCount == 2) {
      builder.append("Double");
    } else if (mTargetTapCount == 3) {
      builder.append("Triple");
    } else if (mTargetTapCount > 3) {
      builder.append(mTargetTapCount);
    }
    return builder.append(" Tap").toString();
  }

  private PointF initBaseLocation(MotionEvent event) {
    final int index = event.getActionIndex();
    final int baseIndex = event.getPointerCount() - 1;
    final PointF p = bases[baseIndex];
    if (Float.isNaN(p.x) && Float.isNaN(p.y)) {
      p.set(event.getX(index), event.getY(index));
    }
    return p;
  }

  /**
   * Find the nearest location to the given event in the bases. If no one found, it could be not
   * inside {@code slop}, filtered or empty bases. When {@code filterMatched} is true, if the
   * location of given event matches one of the points in {@link #mExcludedPointsForDownSlopChecked}
   * it would be ignored. Otherwise, the location will be added to {@link
   * #mExcludedPointsForDownSlopChecked}.
   *
   * @param event to find nearest point in bases.
   * @param slop to check to the given location of the event.
   * @param filterMatched true to exclude points already matched other pointers.
   * @return the point in bases closed to the location of the given event.
   */
  @Nullable
  private PointF findNearestPoint(MotionEvent event, float slop, boolean filterMatched) {
    float moveDelta = Float.MAX_VALUE;
    PointF nearest = null;
    for (int i = 0; i < bases.length; i++) {
      final PointF p = bases[i];
      if (Float.isNaN(p.x) && Float.isNaN(p.y)) {
        continue;
      }
      if (filterMatched && excludedPointsForDownSlopChecked.contains(p)) {
        continue;
      }
      final int index = event.getActionIndex();
      final float dX = p.x - event.getX(index);
      final float dY = p.y - event.getY(index);
      if (dX == 0 && dY == 0) {
        if (filterMatched) {
          excludedPointsForDownSlopChecked.add(p);
        }
        return p;
      }
      final float delta = (float) Math.hypot(dX, dY);
      if (moveDelta > delta) {
        moveDelta = delta;
        nearest = p;
      }
    }
    if (moveDelta < slop) {
      if (filterMatched) {
        excludedPointsForDownSlopChecked.add(nearest);
      }
      return nearest;
    }
    return null;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder(super.toString());
    if (getState() != STATE_GESTURE_CANCELED) {
      builder.append(", CompletedTapCount: ");
      builder.append(completedTapCount);
      builder.append(", IsTargetFingerCountReached: ");
      builder.append(isTargetFingerCountReached);
      builder.append(", Bases: ");
      builder.append(Arrays.toString(bases));
      builder.append(", ExcludedPointsForDownSlopChecked: ");
      builder.append(excludedPointsForDownSlopChecked.toString());
    }
    return builder.toString();
  }
}
