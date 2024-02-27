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
import android.view.MotionEvent;

/**
 * This class matches gestures of the form multi-tap and hold. The number of taps for each instance
 * is specified in the constructor.
 *
 * @hide
 */
public class MultiTapAndHold extends MultiTap {

  public MultiTapAndHold(
      Context context, int taps, int gesture, GestureMatcher.StateChangeListener listener) {
    super(context, taps, gesture, listener);
  }

  @Override
  protected void onDown(MotionEvent event) {
    super.onDown(event);
    if (currentTaps + 1 == targetTaps && getState() != STATE_GESTURE_CANCELED) {
      // We should check the detector state in advance because it may enter Cancel state in base
      // class (MultiTap).
      completeAfterLongPressTimeout(event);
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
        cancelGesture(event);
        return;
      }
      // Needs more taps.
    } else {
      // Either too many taps or nonsensical event stream.
      cancelGesture(event);
      return;
    }
    cancelAfterDoubleTapTimeout(event);
  }

  @Override
  public String getGestureName() {
    switch (targetTaps) {
      case 2:
        return "Double Tap and Hold";
      case 3:
        return "Triple Tap and Hold";
      default:
        return Integer.toString(targetTaps) + " Taps and Hold";
    }
  }
}
