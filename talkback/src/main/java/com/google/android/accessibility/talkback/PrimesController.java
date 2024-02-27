/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback;

import android.app.Application;
import android.os.SystemClock;

/** Initialize and configures Primes to collect performance metrics. */
public class PrimesController {

  /** Timer for measuring latency. */
  public enum TimerAction {
    START_UP,
    GESTURE_EVENT,
    KEY_EVENT,
    DPAD_NAVIGATION,
    TTS_DELAY,
    INITIAL_FOCUS_RESTORE,
    INITIAL_FOCUS_FOLLOW_INPUT,
    INITIAL_FOCUS_FIRST_CONTENT,
    IMAGE_CAPTION_OCR_SUCCEED,
    IMAGE_CAPTION_OCR_FAILED,
    IMAGE_CAPTION_ICON_LABEL_SUCCEED,
    IMAGE_CAPTION_ICON_LABEL_FAILED,
    IMAGE_CAPTION_IMAGE_DESCRIPTION_SUCCEED,
    IMAGE_CAPTION_IMAGE_DESCRIPTION_FAILED,
    IMAGE_CAPTION_IMAGE_PROCESS_BLOCK_OVERLAY,

    EVENT_BASED_HEARING_FEEDBACK,
  }

  public void initialize(Application app) {}

  public void startTimer(TimerAction timerAction) {}

  public void startTimer(TimerAction timerAction, String id) {}

  public void stopTimer(TimerAction timerAction) {}


  public void recordDuration(TimerAction timerAction, long startMs, long endMs) {}

  public void recordDuration(TimerAction timerAction, long duration) {}

  public long getTime() {
    return SystemClock.uptimeMillis();
  }
}
