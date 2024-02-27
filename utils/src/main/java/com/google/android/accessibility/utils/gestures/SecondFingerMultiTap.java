/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * This class matches second-finger multi-tap gestures. A second-finger multi-tap gesture is where
 * one finger is held down and a second finger executes the taps. The number of taps for each
 * instance is specified in the constructor.
 */
class SecondFingerMultiTap extends GestureMatcher {
  private final int targetTaps;
  private int doubleTapSlop;
  private int touchSlop;
  private int tapTimeout;
  private int doubleTapTimeout;
  private int currentTaps;
  private int secondFingerPointerId;
  float baseX;
  float baseY;
  long lastDownTime;
  long lastUpTime;

  SecondFingerMultiTap(
      Context context, int taps, int gesture, GestureMatcher.StateChangeListener listener) {
    super(gesture, new Handler(context.getMainLooper()), listener);
    targetTaps = taps;
    doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop();

    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    tapTimeout = ViewConfiguration.getTapTimeout();
    doubleTapTimeout = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS;
    clear();
  }

  @Override
  public void clear() {
    currentTaps = 0;
    baseX = Float.NaN;
    baseY = Float.NaN;
    secondFingerPointerId = INVALID_POINTER_ID;
    lastDownTime = Long.MAX_VALUE;
    lastUpTime = Long.MAX_VALUE;
    super.clear();
  }

  @Override
  protected void onPointerDown(MotionEvent event) {
    if (event.getPointerCount() > 2) {
      cancelGesture(event);
      return;
    }
    // Second finger has gone down.
    int index = event.getActionIndex();
    secondFingerPointerId = event.getPointerId(index);
    long time = event.getEventTime();
    long timeDelta = time - lastUpTime;
    if (timeDelta > doubleTapTimeout) {
      cancelGesture(event);
      return;
    }
    lastDownTime = time;
    if (Float.isNaN(baseX) && Float.isNaN(baseY)) {
      baseX = event.getX(index);
      baseY = event.getY(index);
    }
    if (!isSecondFingerInsideSlop(event, doubleTapSlop)) {
      cancelGesture(event);
    }
    baseX = event.getX(index);
    baseY = event.getY(index);
  }

  @Override
  protected void onPointerUp(MotionEvent event) {
    if (event.getPointerCount() > 2) {
      cancelGesture(event);
      return;
    }
    long time = event.getEventTime();
    long timeDelta = time - lastDownTime;
    if (timeDelta > tapTimeout) {
      cancelGesture(event);
      return;
    }
    lastUpTime = time;
    if (!isSecondFingerInsideSlop(event, touchSlop)) {
      cancelGesture(event);
    }
    if (getState() == STATE_GESTURE_STARTED || getState() == STATE_CLEAR) {
      currentTaps++;
      if (currentTaps == targetTaps) {
        // Done.
        completeGesture(event);
        return;
      }
    } else {
      // Nonsensical event stream.
      cancelGesture(event);
    }
  }

  @Override
  protected void onMove(MotionEvent event) {
    switch (event.getPointerCount()) {
      case 1:
        // We don't need to track anything about one-finger movements.
        break;
      case 2:
        if (!isSecondFingerInsideSlop(event, touchSlop)) {
          cancelGesture(event);
        }
        break;
      default:
        // More than two fingers means we stop tracking.
        cancelGesture(event);
        break;
    }
  }

  @Override
  protected void onUp(MotionEvent event) {
    // Cancel early when possible, or it will take precedence over two-finger double tap.
    cancelGesture(event);
  }

  @Override
  public String getGestureName() {
    switch (targetTaps) {
      case 2:
        return "Second Finger Double Tap";
      case 3:
        return "Second Finger Triple Tap";
      default:
        return "Second Finger " + Integer.toString(targetTaps) + " Taps";
    }
  }

  private boolean isSecondFingerInsideSlop(MotionEvent event, int slop) {
    int pointerIndex = event.findPointerIndex(secondFingerPointerId);
    if (pointerIndex == -1) {
      LogUtils.e(getGestureName(), "Unable to find pointer.");
      return false;
    }
    final float deltaX = baseX - event.getX(pointerIndex);
    final float deltaY = baseY - event.getY(pointerIndex);
    if (deltaX == 0 && deltaY == 0) {
      return true;
    }
    final double moveDelta = Math.hypot(deltaX, deltaY);
    LogUtils.v(getGestureName(), "moveDelta: %g", moveDelta);
    return moveDelta <= slop;
  }

  @Override
  public String toString() {
    return super.toString()
        + ", Taps:"
        + currentTaps
        + ", mBaseX: "
        + Float.toString(baseX)
        + ", mBaseY: "
        + Float.toString(baseY);
  }
}
