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

import static android.view.MotionEvent.INVALID_POINTER_ID;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.google.android.accessibility.utils.R;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is responsible for matching one-finger swipe gestures. Each instance matches one swipe
 * gesture. A swipe is specified as a series of one or more directions e.g. left, left and up, etc.
 * At this time swipes with more than two directions are not supported.
 */
class MultiFingerSwipe extends GestureMatcher {

  // Direction constants.
  public static final int LEFT = 0;
  public static final int RIGHT = 1;
  public static final int UP = 2;
  public static final int DOWN = 3;
  public static final int UNCERTAIN = 4;

  // Buffer for storing points for gesture detection.
  private final List<List<PointF>> strokeBuffers;

  // The swipe direction for this matcher.
  private int targetDirection;
  private int[] pointerIds;
  // The starting point of each finger's path in the gesture.
  private PointF[] base;
  // The most recent entry in each finger's gesture path.
  private PointF[] previousGesturePoint;
  private int targetFingerCount;
  private int currentFingerCount;
  // Whether the appropriate number of fingers have gone down at some point. This is reset only on
  // clear.
  private boolean targetFingerCountReached = false;
  // Constants for sampling motion event points.
  // We sample based on a minimum distance between points, primarily to improve accuracy by
  // reducing noisy minor changes in direction.
  private static final float MIN_CM_BETWEEN_SAMPLES = 0.25f;
  private final float minPixelsBetweenSamplesX;
  private final float minPixelsBetweenSamplesY;
  // The minmimum distance the finger must travel before we evaluate the initial direction of the
  // swipe.
  // Anything less is still considered a touch.
  private int touchSlop;

  MultiFingerSwipe(
      Context context,
      int fingerCount,
      int direction,
      int gesture,
      GestureMatcher.StateChangeListener listener) {
    super(gesture, new Handler(context.getMainLooper()), listener);
    targetFingerCount = fingerCount;
    pointerIds = new int[targetFingerCount];
    base = new PointF[targetFingerCount];
    previousGesturePoint = new PointF[targetFingerCount];
    strokeBuffers = new ArrayList<>();
    for (int i = 0; i < targetFingerCount; ++i) {
      strokeBuffers.add(new ArrayList<PointF>());
    }
    targetDirection = direction;
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    // Calculate gesture sampling interval.
    final float pixelsPerCmX = displayMetrics.xdpi / GestureUtils.CM_PER_INCH;
    final float pixelsPerCmY = displayMetrics.ydpi / GestureUtils.CM_PER_INCH;
    minPixelsBetweenSamplesX = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmX;
    minPixelsBetweenSamplesY = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmY;
    touchSlop =
        ViewConfiguration.get(context).getScaledTouchSlop()
            * context.getResources().getInteger(R.integer.config_slop_default_multiplier);
    clear();
  }

  @Override
  public void clear() {
    targetFingerCountReached = false;
    currentFingerCount = 0;
    for (int i = 0; i < targetFingerCount; ++i) {
      pointerIds[i] = INVALID_POINTER_ID;
      if (base[i] == null) {
        base[i] = new PointF();
      }
      base[i].x = Float.NaN;
      base[i].y = Float.NaN;
      if (previousGesturePoint[i] == null) {
        previousGesturePoint[i] = new PointF();
      }
      previousGesturePoint[i].x = Float.NaN;
      previousGesturePoint[i].y = Float.NaN;
      strokeBuffers.get(i).clear();
    }
    super.clear();
  }

  @Override
  protected void onDown(MotionEvent event) {
    if (currentFingerCount > 0) {
      cancelGesture(event);
      return;
    }
    currentFingerCount = 1;
    final int actionIndex = event.getActionIndex();
    final int pointerId = event.getPointerId(actionIndex);
    int pointerIndex = event.getPointerCount() - 1;
    if (pointerId < 0) {
      // Nonsensical pointer id.
      cancelGesture(event);
      return;
    }
    if (pointerIds[pointerIndex] != INVALID_POINTER_ID) {
      // Inconsistent event stream.
      cancelGesture(event);
      return;
    }
    pointerIds[pointerIndex] = pointerId;
    if (Float.isNaN(base[pointerIndex].x) && Float.isNaN(base[pointerIndex].y)) {
      final float x = event.getX(actionIndex);
      final float y = event.getY(actionIndex);
      if (x < 0f || y < 0f) {
        cancelGesture(event);
        return;
      }
      base[pointerIndex].x = x;
      base[pointerIndex].y = y;
      previousGesturePoint[pointerIndex].x = x;
      previousGesturePoint[pointerIndex].y = y;
    } else {
      // This event doesn't make sense in the middle of a gesture.
      cancelGesture(event);
      return;
    }
  }

  @Override
  protected void onPointerDown(MotionEvent event) {
    if (event.getPointerCount() > targetFingerCount) {
      cancelGesture(event);
      return;
    }
    currentFingerCount += 1;
    if (currentFingerCount != event.getPointerCount()) {
      cancelGesture(event);
      return;
    }
    if (currentFingerCount == targetFingerCount) {
      targetFingerCountReached = true;
    }
    final int actionIndex = event.getActionIndex();
    final int pointerId = event.getPointerId(actionIndex);
    if (pointerId < 0) {
      // Nonsensical pointer id.
      cancelGesture(event);
      return;
    }
    int pointerIndex = currentFingerCount - 1;
    if (pointerIds[pointerIndex] != INVALID_POINTER_ID) {
      // Inconsistent event stream.
      cancelGesture(event);
      return;
    }
    pointerIds[pointerIndex] = pointerId;
    if (Float.isNaN(base[pointerIndex].x) && Float.isNaN(base[pointerIndex].y)) {
      final float x = event.getX(actionIndex);
      final float y = event.getY(actionIndex);
      if (x < 0f || y < 0f) {
        cancelGesture(event);
        return;
      }
      base[pointerIndex].x = x;
      base[pointerIndex].y = y;
      previousGesturePoint[pointerIndex].x = x;
      previousGesturePoint[pointerIndex].y = y;
    } else {
      cancelGesture(event);
      return;
    }
  }

  @Override
  protected void onPointerUp(MotionEvent event) {
    if (!targetFingerCountReached) {
      cancelGesture(event);
      return;
    }
    currentFingerCount -= 1;
    final int actionIndex = event.getActionIndex();
    final int pointerId = event.getPointerId(actionIndex);
    if (pointerId < 0) {
      // Nonsensical pointer id.
      cancelGesture(event);
      return;
    }
    final int pointerIndex = Arrays.binarySearch(pointerIds, pointerId);
    if (pointerIndex < 0) {
      cancelGesture(event);
      return;
    }
    final float x = event.getX(actionIndex);
    final float y = event.getY(actionIndex);
    if (x < 0f || y < 0f) {
      cancelGesture(event);
      return;
    }
    final float dX = Math.abs(x - previousGesturePoint[pointerIndex].x);
    final float dY = Math.abs(y - previousGesturePoint[pointerIndex].y);
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      strokeBuffers.get(pointerIndex).add(new PointF(x, y));
    }
    // We will evaluate all the paths on ACTION_UP.
  }

  @Override
  protected void onMove(MotionEvent event) {
    for (int pointerIndex = 0; pointerIndex < targetFingerCount; ++pointerIndex) {
      if (pointerIds[pointerIndex] == INVALID_POINTER_ID) {
        // Fingers have started to move before the required number of fingers are down.
        // However, they can still move less than the touch slop and still be considered
        // touching, not moving.
        // So we just ignore fingers that haven't been assigned a pointer id and process
        // those who have.
        continue;
      }
      LogUtils.v(getGestureName(), "Processing move on finger %d", pointerIndex);
      int index = event.findPointerIndex(pointerIds[pointerIndex]);
      if (index < 0) {
        // This finger is not present in this event. It could have gone up just before this
        // movement.
        LogUtils.v(getGestureName(), "Finger %d not found in this event. skipping.", pointerIndex);
        continue;
      }
      final float x = event.getX(index);
      final float y = event.getY(index);
      if (x < 0f || y < 0f) {
        cancelGesture(event);
        return;
      }
      final float dX = Math.abs(x - previousGesturePoint[pointerIndex].x);
      final float dY = Math.abs(y - previousGesturePoint[pointerIndex].y);
      final double moveDelta =
          Math.hypot(Math.abs(x - base[pointerIndex].x), Math.abs(y - base[pointerIndex].y));
      LogUtils.v(getGestureName(), "moveDelta%g", moveDelta);
      if (getState() == STATE_CLEAR) {
        if (moveDelta < (targetFingerCount * touchSlop)) {
          // This still counts as a touch not a swipe.
          continue;
        }
        // First, make sure we have the right number of fingers down.
        if (currentFingerCount != targetFingerCount) {
          cancelGesture(event);
          return;
        }
        // Then, make sure the pointer is going in the right direction.
        int direction = toDirection(x - base[pointerIndex].x, y - base[pointerIndex].y);
        if (direction != targetDirection) {
          cancelGesture(event);
          return;
        }
        // This is confirmed to be some kind of swipe so start tracking points.
        startGesture(event);
        for (int i = 0; i < targetFingerCount; ++i) {
          strokeBuffers.get(i).add(new PointF(base[i]));
        }
      } else if (getState() == STATE_GESTURE_STARTED) {
        // Cancel if the finger starts to go the wrong way.
        // Note that this only works because this matcher assumes one direction.
        int direction = toDirection(x - base[pointerIndex].x, y - base[pointerIndex].y);
        if (direction != UNCERTAIN && direction != targetDirection) {
          cancelGesture(event);
          return;
        }
        if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
          // Sample every 2.5 MM in order to guard against minor variations in path.
          previousGesturePoint[pointerIndex].x = x;
          previousGesturePoint[pointerIndex].y = y;
          strokeBuffers.get(pointerIndex).add(new PointF(x, y));
        }
      }
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
        return;
      default:
        cancelGesture(event);
        return;
    }
    currentFingerCount = 0;
    final int actionIndex = event.getActionIndex();
    final int pointerId = event.getPointerId(actionIndex);
    final int pointerIndex = Arrays.binarySearch(pointerIds, pointerId);
    if (pointerIndex < 0) {
      cancelGesture(event);
      return;
    }
    final float x = event.getX(actionIndex);
    final float y = event.getY(actionIndex);
    if (x < 0f || y < 0f) {
      cancelGesture(event);
      return;
    }
    final float dX = Math.abs(x - previousGesturePoint[pointerIndex].x);
    final float dY = Math.abs(y - previousGesturePoint[pointerIndex].y);
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      strokeBuffers.get(pointerIndex).add(new PointF(x, y));
    }
    recognizeGesture(event);
  }

  /**
   * Looks at the sequence of motions in mStrokeBuffer, classifies the gesture, then transitions to
   * the complete or cancel state depending on the result.
   */
  private void recognizeGesture(MotionEvent event) {
    // Check the path of each finger against the specified direction.
    // Note that we sample every 2.5 MMm, and the direction matching is extremely tolerant (each
    // direction has a 90-degree arch of tolerance) meaning that minor perpendicular movements
    // should not create false negatives.
    for (int i = 0; i < targetFingerCount; ++i) {
      LogUtils.v(getGestureName(), "Recognizing finger: %d", i);
      if (strokeBuffers.get(i).size() < 2) {
        Log.d(getGestureName(), "Too few points.");
        cancelGesture(event);
        return;
      }
      List<PointF> path = strokeBuffers.get(i);

      LogUtils.v(getGestureName(), "path= %s", path.toString());
      // Classify line segments, and call Listener callbacks.
      if (!recognizeGesturePath(path)) {
        cancelGesture(event);
        return;
      }
    }
    // If we reach this point then all paths match.
    completeGesture(event);
  }

  /**
   * Tests the path of a given finger against the direction specified in this matcher.
   *
   * @return True if the path matches the specified direction for this matcher, otherwise false.
   */
  private boolean recognizeGesturePath(List<PointF> path) {
    for (int i = 0; i < path.size() - 1; ++i) {
      PointF start = path.get(i);
      PointF end = path.get(i + 1);

      float dX = end.x - start.x;
      float dY = end.y - start.y;
      int direction = toDirection(dX, dY);
      if (direction != targetDirection) {
        LogUtils.v(
            getGestureName(),
            "Found direction %s when expecting %s",
            directionToString(direction),
            directionToString(this.targetDirection));
        return false;
      }
    }
    LogUtils.v(getGestureName(), "Completed.");
    return true;
  }

  private static int toDirection(float dX, float dY) {
    if (dX == 0 && dY == 0) {
      return UNCERTAIN;
    }
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
      case UNCERTAIN:
        return "still";
      default:
        return "Unknown Direction";
    }
  }

  @Override
  protected String getGestureName() {
    StringBuilder builder = new StringBuilder();
    builder.append(targetFingerCount).append("-finger ");
    builder.append("Swipe ").append(directionToString(targetDirection));
    return builder.toString();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(super.toString());
    if (getState() != STATE_GESTURE_CANCELED) {
      builder
          .append(", mBase: ")
          .append(Arrays.toString(base))
          .append(", mMinPixelsBetweenSamplesX:")
          .append(minPixelsBetweenSamplesX)
          .append(", mMinPixelsBetweenSamplesY:")
          .append(minPixelsBetweenSamplesY);
    }
    return builder.toString();
  }
}
