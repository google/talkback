/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.accessibility.utils.gestures.GestureUtils.MM_PER_CM;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.google.android.accessibility.utils.R;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;

/**
 * This class is responsible for matching one-finger swipe gestures. Each instance matches one swipe
 * gesture. A swipe is specified as a series of one or more directions e.g. left, left and up, etc.
 * At this time swipes with more than two directions are not supported.
 */
class Swipe extends GestureMatcher {

  // Direction constants.
  public static final int NONE = -1;
  public static final int LEFT = 0;
  public static final int RIGHT = 1;
  public static final int UP = 2;
  public static final int DOWN = 3;
  // This is the calculated movement threshold used track if the user is still
  // moving their finger.
  private final float gestureDetectionThresholdPixels;

  // Buffer for storing points for gesture detection.
  private final ArrayList<PointF> strokeBuffer = new ArrayList<>(100);

  // Constants for sampling motion event points.
  // We sample based on a minimum distance between points, primarily to improve accuracy by
  // reducing noisy minor changes in direction.
  private static final float MIN_CM_BETWEEN_SAMPLES = 0.25f;

  private final int[] directions;
  private float baseX;
  private float baseY;
  private long baseTime;
  private float previousGestureX;
  private float previousGestureY;
  private final float minPixelsBetweenSamplesX;
  private final float minPixelsBetweenSamplesY;
  // Time threshold in millisecond to determine if an interaction is a gesture or not.
  private final int maxStartThreshold;
  // Time threshold in millisecond to determine if a gesture should be cancelled.
  private final int maxContinueThreshold;
  // The minmimum distance the finger must travel before we evaluate the initial direction of the
  // swipe.
  // Anything less is still considered a touch.
  private int touchSlop;

  // Constants for separating gesture segments
  private static final float ANGLE_THRESHOLD = 0.0f;

  Swipe(Context context, int direction, int gesture, GestureMatcher.StateChangeListener listener) {
    this(context, new int[] {direction}, gesture, listener);
  }

  Swipe(
      Context context,
      int direction1,
      int direction2,
      int gesture,
      GestureMatcher.StateChangeListener listener) {
    this(context, new int[] {direction1, direction2}, gesture, listener);
  }

  private Swipe(
      Context context, int[] directions, int gesture, GestureMatcher.StateChangeListener listener) {
    super(gesture, new Handler(context.getMainLooper()), listener);
    float gestureConfirmDistanceCm =
        context.getResources().getFloat(R.dimen.config_gesture_confirm_distance_cm);
    int maxTimeToStartSwipeMsPerCm =
        context.getResources().getInteger(R.integer.config_max_time_to_start_swipe_ms_per_cm);
    int maxTimeToContinueSwipeMsPerCm =
        context.getResources().getInteger(R.integer.config_max_time_to_continue_swipe_ms_per_cm);
    maxStartThreshold = (int) (maxTimeToStartSwipeMsPerCm * gestureConfirmDistanceCm);
    maxContinueThreshold = (int) (maxTimeToContinueSwipeMsPerCm * gestureConfirmDistanceCm);
    this.directions = directions;
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    gestureDetectionThresholdPixels =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, MM_PER_CM, displayMetrics)
            * gestureConfirmDistanceCm;
    // Calculate minimum gesture velocity
    final float pixelsPerCmX = displayMetrics.xdpi / 2.54f;
    final float pixelsPerCmY = displayMetrics.ydpi / 2.54f;
    minPixelsBetweenSamplesX = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmX;
    minPixelsBetweenSamplesY = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmY;
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    clear();
  }

  @Override
  public void clear() {
    baseX = Float.NaN;
    baseY = Float.NaN;
    baseTime = 0;
    previousGestureX = Float.NaN;
    previousGestureY = Float.NaN;
    strokeBuffer.clear();
    super.clear();
  }

  @Override
  protected void onDown(MotionEvent event) {
    if (Float.isNaN(baseX) && Float.isNaN(baseY)) {
      baseX = event.getX();
      baseY = event.getY();
      baseTime = event.getEventTime();
      previousGestureX = baseX;
      previousGestureY = baseY;
    }
    // Otherwise do nothing because this event doesn't make sense in the middle of a gesture.
  }

  @Override
  protected void onMove(MotionEvent event) {
    final float x = event.getX();
    final float y = event.getY();
    final long time = event.getEventTime();
    final float dX = Math.abs(x - previousGestureX);
    final float dY = Math.abs(y - previousGestureY);
    final double moveDelta = Math.hypot(Math.abs(x - baseX), Math.abs(y - baseY));
    final long timeDelta = time - baseTime;
    LogUtils.v(
        getGestureName(),
        "moveDelta: %g,  mGestureDetectionThreshold: %g",
        moveDelta,
        gestureDetectionThresholdPixels);
    if (getState() == STATE_CLEAR) {
      if (moveDelta < touchSlop) {
        // This still counts as a touch not a swipe.
        return;
      } else if (strokeBuffer.isEmpty()) {
        // First, make sure the pointer is going in the right direction.
        int direction = toDirection(x - baseX, y - baseY);
        if (direction != directions[0]) {
          cancelGesture(event);
          return;
        }
        // This is confirmed to be some kind of swipe so start tracking points.
        strokeBuffer.add(new PointF(baseX, baseY));
      }
    }
    if (moveDelta > gestureDetectionThresholdPixels) {
      // This is a gesture, not touch exploration.
      baseX = x;
      baseY = y;
      baseTime = time;
      startGesture(event);
    } else if (getState() == STATE_CLEAR) {
      if (timeDelta > maxStartThreshold) {
        // The user isn't moving fast enough.
        cancelGesture(event);
        return;
      }
    } else if (getState() == STATE_GESTURE_STARTED) {
      if (timeDelta > maxContinueThreshold) {
        cancelGesture(event);
        return;
      }
    }
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      // At this point gesture detection has started and we are sampling points.
      previousGestureX = x;
      previousGestureY = y;
      strokeBuffer.add(new PointF(x, y));
    }
  }

  @Override
  protected void onUp(MotionEvent event) {
    switch (getState()) {
      case STATE_GESTURE_STARTED:
        break;
      case STATE_CLEAR:
        // For Swipe gestures, this is the very last motion event. When any of the swipe gesture
        // detectors matches, the others will enter the clear state. We should not Cancel the
        // detector again for the Up event, or it cannot detect new gesture immediately.
        // On the other hand, if we don't do clear(), the followed onDown event will credit the last
        // stroke data, which caused miss-identified gesture.
        clear();
        return;
      default:
        cancelGesture(event);
        return;
    }

    final float x = event.getX();
    final float y = event.getY();
    final float dX = Math.abs(x - previousGestureX);
    final float dY = Math.abs(y - previousGestureY);
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      strokeBuffer.add(new PointF(x, y));
    }
    recognizeGesture(event);
  }

  @Override
  protected void onPointerDown(MotionEvent event) {
    cancelGesture(event);
  }

  @Override
  protected void onPointerUp(MotionEvent event) {
    cancelGesture(event);
  }

  /**
   * Looks at the sequence of motions in mStrokeBuffer, classifies the gesture, then calls Listener
   * callbacks for success or failure.
   *
   * @param event The raw motion event to pass to the listener callbacks.
   */
  private void recognizeGesture(MotionEvent event) {
    if (strokeBuffer.size() < 2) {
      cancelGesture(event);
      return;
    }

    // Look at mStrokeBuffer and extract 2 line segments, delimited by near-perpendicular
    // direction change.
    // Method: for each sampled motion event, check the angle of the most recent motion vector
    // versus the preceding motion vector, and segment the line if the angle is about
    // 90 degrees.

    ArrayList<PointF> path = new ArrayList<>();
    PointF lastDelimiter = strokeBuffer.get(0);
    path.add(lastDelimiter);

    float dX = 0; // Sum of unit vectors from last delimiter to each following point
    float dY = 0;
    int count = 0; // Number of points since last delimiter
    float length = 0; // Vector length from delimiter to most recent point

    PointF next = null;
    for (int i = 1; i < strokeBuffer.size(); ++i) {
      next = strokeBuffer.get(i);
      if (count > 0) {
        // Average of unit vectors from delimiter to following points
        float currentDX = dX / count;
        float currentDY = dY / count;

        // newDelimiter is a possible new delimiter, based on a vector with length from
        // the last delimiter to the previous point, but in the direction of the average
        // unit vector from delimiter to previous points.
        // Using the averaged vector has the effect of "squaring off the curve",
        // creating a sharper angle between the last motion and the preceding motion from
        // the delimiter. In turn, this sharper angle achieves the splitting threshold
        // even in a gentle curve.
        PointF newDelimiter =
            new PointF(length * currentDX + lastDelimiter.x, length * currentDY + lastDelimiter.y);

        // Unit vector from newDelimiter to the most recent point
        float nextDX = next.x - newDelimiter.x;
        float nextDY = next.y - newDelimiter.y;
        float nextLength = (float) Math.hypot(nextDX, nextDY);
        nextDX = nextDX / nextLength;
        nextDY = nextDY / nextLength;

        // Compare the initial motion direction to the most recent motion direction,
        // and segment the line if direction has changed by about 90 degrees.
        float dot = currentDX * nextDX + currentDY * nextDY;
        if (dot < ANGLE_THRESHOLD) {
          path.add(newDelimiter);
          lastDelimiter = newDelimiter;
          dX = 0;
          dY = 0;
          count = 0;
        }
      }

      // Vector from last delimiter to most recent point
      float currentDX = next.x - lastDelimiter.x;
      float currentDY = next.y - lastDelimiter.y;
      length = (float) Math.hypot(currentDX, currentDY);

      // Increment sum of unit vectors from delimiter to each following point
      count = count + 1;
      dX = dX + currentDX / length;
      dY = dY + currentDY / length;
    }

    path.add(next);
    LogUtils.v(getGestureName(), "path = %s", path.toString());
    // Classify line segments, and call Listener callbacks.
    recognizeGesturePath(event, path);
  }

  /**
   * Classifies a pair of line segments, by direction. Calls Listener callbacks for success or
   * failure.
   *
   * @param event The raw motion event to pass to the listener's onGestureCanceled method.
   * @param path A sequence of motion line segments derived from motion points in mStrokeBuffer.
   */
  private void recognizeGesturePath(MotionEvent event, ArrayList<PointF> path) {
    if (path.size() != directions.length + 1) {
      cancelGesture(event);
      return;
    }
    for (int i = 0; i < path.size() - 1; ++i) {
      PointF start = path.get(i);
      PointF end = path.get(i + 1);

      float dX = end.x - start.x;
      float dY = end.y - start.y;
      int direction = toDirection(dX, dY);
      if (direction != directions[i]) {
        LogUtils.v(
            getGestureName(),
            "Found direction %s  when expecting %s",
            directionToString(direction),
            directionToString(directions[i]));
        cancelGesture(event);
        return;
      }
    }
    LogUtils.v(getGestureName(), "Completed.");
    completeGesture(event);
  }

  private static int toDirection(float dX, float dY) {
    if (Math.abs(dX) > Math.abs(dY)) {
      // Horizontal
      return (dX < 0) ? LEFT : RIGHT;
    } else {
      // Vertical
      return (dY < 0) ? UP : DOWN;
    }
  }

  public static String directionToString(int direction) {
    switch (direction) {
      case LEFT:
        return "left";
      case RIGHT:
        return "right";
      case UP:
        return "up";
      case DOWN:
        return "down";
      default:
        return "Unknown Direction";
    }
  }

  @Override
  protected String getGestureName() {
    StringBuilder builder = new StringBuilder();
    builder.append("Swipe ").append(directionToString(directions[0]));
    for (int i = 1; i < directions.length; ++i) {
      builder.append(" and ").append(directionToString(directions[i]));
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(super.toString());
    if (getState() != STATE_GESTURE_CANCELED) {
      builder
          .append(", mBaseX: ")
          .append(baseX)
          .append(", mBaseY: ")
          .append(baseY)
          .append(", mGestureDetectionThreshold:")
          .append(gestureDetectionThresholdPixels)
          .append(", mMinPixelsBetweenSamplesX:")
          .append(minPixelsBetweenSamplesX)
          .append(", mMinPixelsBetweenSamplesY:")
          .append(minPixelsBetweenSamplesY);
    }
    return builder.toString();
  }
}
