/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * This class matches second-finger multi-tap gestures. A second-finger multi-tap gesture is where
 * one finger is held down and a second finger executes the taps. The number of taps for each
 * instance is specified in the constructor.
 */
class SecondFingerTap extends GestureMatcher {
  private final int targetTaps;
  private final int doubleTapTimeout;
  private int currentTaps;
  private float baseX;
  private float baseY;
  private long firstDownTime;

  SecondFingerTap(
      Context context, int taps, int gesture, GestureMatcher.StateChangeListener listener) {
    super(gesture, new Handler(context.getMainLooper()), listener);
    targetTaps = taps;
    doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
    clear();
  }

  @Override
  public void clear() {
    currentTaps = 0;
    baseX = Float.NaN;
    baseY = Float.NaN;
    firstDownTime = Long.MAX_VALUE;
    super.clear();
  }

  // Instead of clear the detector, this method restore the state variables to detect the next tap
  // event.
  private void restart() {
    currentTaps = 0;
    baseX = Float.NaN;
    baseY = Float.NaN;
  }

  @Override
  protected void onDown(MotionEvent event) {
    firstDownTime = event.getEventTime();
  }

  @Override
  protected void onPointerDown(MotionEvent event) {
    long timeDelta = event.getEventTime() - firstDownTime;
    if (timeDelta < doubleTapTimeout) {
      cancelGesture(event);
      return;
    }

    if (event.getPointerCount() > 2) {
      LogUtils.v(getGestureName(), "onPointerDown/getPointerCount=%d", event.getPointerCount());
      cancelGesture(event);
      return;
    }
    // Second finger has gone down.
    int index = event.getActionIndex();
    if (Float.isNaN(baseX) && Float.isNaN(baseY)) {
      baseX = event.getX(index);
      baseY = event.getY(index);
    }
    baseX = event.getX(index);
    baseY = event.getY(index);
  }

  @Override
  protected void onPointerUp(MotionEvent event) {
    LogUtils.v(getGestureName(), "onPointerUp/onPointerUp");
    if (event.getPointerCount() > 2) {
      LogUtils.v(getGestureName(), "onPointerUp/getPointerCount=%d", event.getPointerCount());
      cancelGesture(event);
      return;
    }
    if (getState() == STATE_GESTURE_STARTED || getState() == STATE_CLEAR) {
      currentTaps++;
      LogUtils.v(getGestureName(), "onPointerUp/getState=%d", getState());
      if (currentTaps == targetTaps) {
        LogUtils.v(getGestureName(), "onPointerUp/currentTaps=%d", currentTaps);
        // Done.
        completeGesture(event);
        restart();
        startGesture(event);
      }
    } else {
      LogUtils.v(getGestureName(), "onPointerUp/currentTaps=%d", currentTaps);
      // Nonsensical event stream.
      cancelGesture(event);
    }
  }

  @Override
  protected void onUp(MotionEvent event) {
    LogUtils.v(getGestureName(), "onUp");
    // Cancel early when possible, or it will take precedence over two-finger double tap.
    cancelGesture(event);
  }

  @Override
  public String getGestureName() {
    switch (targetTaps) {
      case 1:
        return "Second Finger Tap";
      case 2:
        return "Second Finger Double Tap";
      case 3:
        return "Second Finger Triple Tap";
      default:
        return "Second Finger " + targetTaps + " Taps";
    }
  }

  @Override
  public String toString() {
    return super.toString()
        + ", Taps:"
        + currentTaps
        + ", mBaseX: "
        + Float.toString(baseX)
        + ", mBaseY: "
        + baseY;
  }
}
