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

package com.google.android.accessibility.talkback.trainingcommon;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/** Class handles the repeated announcement using accessibility event. */
public class RepeatedAnnouncingHandler {
  private final AccessibilityManager accessibilityManager;
  private final String announcement;
  private final int initialDelayMs;
  private final int repeatedDelayMs;

  private final Handler handler;
  private final Runnable idleAnnouncementTask =
      new Runnable() {
        @Override
        public void run() {
          if (accessibilityManager.isEnabled() && !TextUtils.isEmpty(announcement)) {
            useAccessibilityEventToAnnounce(announcement);
            handler.postDelayed(idleAnnouncementTask, repeatedDelayMs);
          }
        }
      };

  public RepeatedAnnouncingHandler(
      Context context, String announcement, int initialDelayMs, int repeatedDelayMs) {
    this.announcement = announcement;
    this.initialDelayMs = initialDelayMs;
    this.repeatedDelayMs = repeatedDelayMs;
    accessibilityManager =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    handler = new Handler();
  }

  /**
   * Starts announcing announcement after {@link RepeatedAnnouncingHandler#initialDelayMs} and then
   * infinitely announces after {@link RepeatedAnnouncingHandler#repeatedDelayMs} util stop.
   */
  public void start() {
    handler.postDelayed(idleAnnouncementTask, initialDelayMs);
  }

  /** Stops announcing. */
  public void stop() {
    handler.removeCallbacksAndMessages(null);
  }

  private void useAccessibilityEventToAnnounce(String announcement) {
    AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
    event.setContentDescription(announcement);
    accessibilityManager.sendAccessibilityEvent(event);
  }
}
