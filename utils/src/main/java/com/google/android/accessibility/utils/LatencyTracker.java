/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.utils;

import com.google.android.accessibility.utils.Performance.EventData;
import com.google.android.accessibility.utils.Performance.GestureEventData;
import java.util.concurrent.Executor;

/** Interface to track various latencies. */
public interface LatencyTracker {
  /**
   * Tracks the latency of processing the event from user input or framework.
   *
   * <p>This method is invoked when the feedback is heard by the user.
   */
  void onFeedbackOutput(EventData eventData);

  /**
   * Tracks the latency of processing the event from user input or framework.
   *
   * <p>This method is invoked when {@link
   * android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction} is performed.
   */
  default void onAccessibilityActionPerformed(EventData eventData) {}

  /**
   * Tracks the latency of gesture detection. In general, it tracks all the way from the 1st action
   * down to the gesture completes; for Split-typing gesture, the 1st action down event would be
   * immediately follows the previous onGestureRecognized callback.
   */
  default void onGestureRecognized(GestureEventData gestureEventData) {}

  /** Gets the executor on which to run the callback. */
  Executor getExecutor();
}
