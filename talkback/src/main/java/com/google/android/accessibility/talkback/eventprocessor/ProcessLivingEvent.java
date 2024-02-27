/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;

/**
 * This class supports the tracking of two activities.
 *
 * <ul>
 *   <li>1. TYPE_VIEW_CLICKED: when some view on the screen was clicked.
 *   <li>2. TYPE_VIEW_ACCESSIBILITY_FOCUSED: when the Accessibility focus was set on a view.
 * </ul>
 *
 * <p>Note: If an analytic module is implemented, the two events should be recorded, which can help
 * to identify any user' activities, either from touch screen, keyboard, mouse, ...,etc.
 */
public class ProcessLivingEvent implements AccessibilityEventListener {
  private final TalkBackAnalytics analytics;

  public ProcessLivingEvent(TalkBackAnalytics analytics) {
    this.analytics = analytics;
  }

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_VIEW_CLICKED
        | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_CLICKED:
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        analytics.onTalkBackActivitiesEvent(event.getEventType());
        break;
      default: // fall out
    }
  }
}
