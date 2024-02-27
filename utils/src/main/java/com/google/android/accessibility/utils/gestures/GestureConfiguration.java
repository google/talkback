/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.view.ViewConfiguration;

/** This class contains timeout values that control the behavior of gesture detection. */
public final class GestureConfiguration {

  /**
   * The maximum number of milliseconds that can be between two taps for them to count as a
   * multi-tap gesture.
   */
  public static final int DOUBLE_TAP_TIMEOUT_MS = ViewConfiguration.getDoubleTapTimeout() - 50;

  private GestureConfiguration() {}
}
