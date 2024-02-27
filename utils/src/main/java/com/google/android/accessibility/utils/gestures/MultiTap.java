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

import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.google.android.accessibility.utils.R;

/**
 * This class matches multi-tap gestures. The number of taps for each instance is specified in the
 * constructor.
 *
 * @hide
 */
public class MultiTap extends GestureMatcher {

  // Maximum reasonable number of taps.
  public static final int MAX_TAPS = 10;
  final int targetTaps;
  // The acceptable distance between two taps
  int doubleTapSlop;
  // The acceptable distance the pointer can move and still count as a tap.
  int touchSlop;
  int tapTimeout;
  int doubleTapTimeout;
  int currentTaps;
  float baseX;
  float baseY;
  long lastDownTime;
  long lastUpTime;

  public MultiTap(
      Context context, int taps, int gesture, GestureMatcher.StateChangeListener listener) {
    super(gesture, new Handler(context.getMainLooper()), listener);
    targetTaps = taps;
    doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop();
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    int deltaTapTimeout = context.getResources().getInteger(R.integer.config_tap_timeout_delta);
    tapTimeout = ViewConfiguration.getTapTimeout() + deltaTapTimeout;
    doubleTapTimeout = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS;
    clear();
  }

  @Override
  public void clear() {
    currentTaps = 0;
    baseX = Float.NaN;
    baseY = Float.NaN;
    lastDownTime = Long.MAX_VALUE;
    lastUpTime = Long.MAX_VALUE;
    super.clear();
  }

  @Override
  protected void onDown(MotionEvent event) {
    long time = event.getEventTime();
    long timeDelta = time - lastUpTime;
    if (timeDelta > doubleTapTimeout) {
      cancelGesture(event);
      return;
    }
    lastDownTime = time;
    if (Float.isNaN(baseX) && Float.isNaN(baseY)) {
      baseX = event.getX();
      baseY = event.getY();
    }
    if (!isInsideSlop(event, doubleTapSlop)) {
      cancelGesture(event);
      return;
    }
    baseX = event.getX();
    baseY = event.getY();
    if (currentTaps + 1 == targetTaps) {
      // Start gesture detecting on down of final tap.
      // Note that if this instance is matching double tap,
      // and the service is not requesting to handle double tap, GestureManifold will
      // ignore this.
      startGesture(event);
    }
  }

  @Override
  protected void onUp(MotionEvent event) {
    if (!isValidUpEvent(event)) {
      cancelGesture(event);
      return;
    }
    if (getState() == STATE_GESTURE_STARTED || getState() == STATE_CLEAR) {
      currentTaps++;
      if (currentTaps == targetTaps) {
        // Done.
        completeGesture(event);
        return;
      }
      // Needs more taps.
    } else {
      // Either too many taps or nonsensical event stream.
      cancelGesture(event);
    }
  }

  @Override
  protected void onMove(MotionEvent event) {
    if (!isInsideSlop(event, touchSlop)) {
      cancelGesture(event);
    }
  }

  @Override
  protected void onPointerDown(MotionEvent event) {
    cancelGesture(event);
  }

  @Override
  protected void onPointerUp(MotionEvent event) {
    cancelGesture(event);
  }

  @Override
  public String getGestureName() {
    switch (targetTaps) {
      case 2:
        return "Double Tap";
      case 3:
        return "Triple Tap";
      default:
        return Integer.toString(targetTaps) + " Taps";
    }
  }

  private boolean isInsideSlop(MotionEvent event, int slop) {
    final float deltaX = baseX - event.getX();
    final float deltaY = baseY - event.getY();
    if (deltaX == 0 && deltaY == 0) {
      return true;
    }
    final double moveDelta = Math.hypot(deltaX, deltaY);
    return moveDelta <= slop;
  }

  protected boolean isValidUpEvent(MotionEvent upEvent) {
    long time = upEvent.getEventTime();
    long timeDelta = time - lastDownTime;
    if (timeDelta > tapTimeout) {
      return false;
    }
    lastUpTime = time;
    if (!isInsideSlop(upEvent, touchSlop)) {
      return false;
    }
    return true;
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
