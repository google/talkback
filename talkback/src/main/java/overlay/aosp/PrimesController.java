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

/** Initialize and configures Primes to collect performance metrics. */
public class PrimesController {

  /** Timer for measuring latency. */
  public enum Timer {
    START_UP,
    GESTURE_EVENT,
    INITIALIZE_FOCUS
  }

  public void initialize(Application app) {}

  public void startTimer(Timer timerString) {}

  public void stopTimer(Timer timerString) {}
}
