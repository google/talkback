/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.libraries.accessibility.utils.eventfilter;

import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;

/**
 * Forwards {@link AccessibilityEvent}s handled by the registered {@link
 * AccessibilityEventProcessor}.
 */
public class AccessibilityEventFilter implements AccessibilityEventListener {

  /** Event types that are forwarded to the registered {@link AccessibilityEventProcessor}. */
  private static final int MASK_EVENTS_HANDLED_BY_SERVICE =
      AccessibilityEvent.TYPE_ANNOUNCEMENT
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
          | AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEvent.TYPE_VIEW_HOVER_EXIT
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;

  /**
   * Event types that should be processed with a very minor delay in order to wait for the state to
   * catch up. The delay time is specified by {@link #EVENT_PROCESSING_DELAY}.
   */
  private static final int MASK_DELAYED_EVENT_TYPES = AccessibilityEvent.TYPE_VIEW_CLICKED;

  /**
   * Delay (ms) to wait for the state to catch up before processing events that match the mask
   * {@link #MASK_DELAYED_EVENT_TYPES}. This delay should be nearly imperceptible; practical testing
   * has determined that the minimum delay is ~150ms, but a 150ms delay should be barely
   * perceptible. The 150ms delay has been tested on a variety of Nexus/non-Nexus devices.
   */
  private static final long EVENT_PROCESSING_DELAY = 150;

  private final AccessibilityEventProcessor accessibilityEventProcessor;

  private final Handler handler;

  public AccessibilityEventFilter(AccessibilityEventProcessor accessibilityEventProcessor) {
    this.accessibilityEventProcessor = accessibilityEventProcessor;
    handler = new Handler();
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_SERVICE;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null) {
      return;
    }
    int eventType = event.getEventType();

    // Checking event types to avoid unexpected feedback, which are only meant for TalkBack,
    // from being provided to other accessibility services. The event types listed here are the
    // announcement event and events related with View and Window changes which are forwarded to the
    // registered AccessibilityEventProcessor. Other events such as gesture interactions, text input
    // interaction, etc. are not forwarded.
    if ((eventType & MASK_EVENTS_HANDLED_BY_SERVICE) != 0) {
      if ((eventType & MASK_DELAYED_EVENT_TYPES) != 0) {
        AccessibilityEvent eventCopy = AccessibilityEvent.obtain(event);
        handler.postDelayed(
            () -> accessibilityEventProcessor.processAccessibilityEvent(eventCopy),
            EVENT_PROCESSING_DELAY);
      } else {
        accessibilityEventProcessor.processAccessibilityEvent(event);
      }
    }
  }

  /** Event processor that is notified when the given event is being forwarded. */
  public interface AccessibilityEventProcessor {
    void processAccessibilityEvent(AccessibilityEvent event);
  }
}
