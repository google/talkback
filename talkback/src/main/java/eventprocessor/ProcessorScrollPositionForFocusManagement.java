/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventInterpretation;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.utils.DelayHandler;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages scroll position feedback. If an interpreted {@link AccessibilityEvent#TYPE_VIEW_SCROLLED}
 * event passes through this processor and no further events are received for a specified duration,
 * a "scroll position" message is spoken.
 */
public class ProcessorScrollPositionForFocusManagement implements ScrollEventHandler {

  /** Delay before sending a scroll position notification for non-viewpager node. */
  private static final long DELAY_SCROLL_FEEDBACK = 1000;

  /** Delay before sending a scroll position notification for viewpager. */
  private static final long DELAY_PAGE_FEEDBACK = 500;

  private static final EventInterpretation EVENT_INTERPRETATION_SCROLL_POSITION;

  static {
    EVENT_INTERPRETATION_SCROLL_POSITION =
        new EventInterpretation(Compositor.EVENT_SCROLL_POSITION);
    EVENT_INTERPRETATION_SCROLL_POSITION.setReadOnly();
  }

  private final Compositor compositor;

  private final DelayHandler<Object> delayHandler =
      new DelayHandler<Object>() {
        @Override
        public void handle(Object arg) {
          announce();
        }
      };

  @Nullable private AccessibilityEvent eventToAnnounce;
  @Nullable private EventId eventId;

  /**
   * Verbosity of scroll announcement.
   *
   * <ul>
   *   <li>When set to {@code true}, announce scroll position regardless of the source action.
   *   <li>When set to {@code false}, only announce scroll position changed by scroll gesture.
   * </ul>
   */
  private boolean isVerbose;

  public ProcessorScrollPositionForFocusManagement(Compositor compositor) {
    this.compositor = compositor;
  }

  @Override
  public void onScrollEvent(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {
    if (!interpretation.hasValidIndex || interpretation.isDuplicateEvent) {
      return;
    }
    if (!isVerbose
        && (interpretation.userAction != ScrollEventInterpreter.ACTION_SCROLL_SHORTCUT)) {
      return;
    }

    scheduleAnnouncement(event, eventId);
  }

  public void setVerboseAnnouncement(boolean isVerbose) {
    this.isVerbose = isVerbose;
  }

  private void scheduleAnnouncement(AccessibilityEvent event, EventId eventId) {
    // Scrolling a ViewPager2 also scrolls its tabs. Suppress the tab scroll announcement if the
    // scheduled announcement originates from a page scroll, so the page position announcement isn't
    // stopped. There's only a small possibility that this suppressed event comes from an unrelated
    // HorizontalScrollView within the 500ms time frame.
    if (Role.getSourceRole(eventToAnnounce) == Role.ROLE_PAGER) {
      if (Role.getSourceRole(event) == Role.ROLE_HORIZONTAL_SCROLL_VIEW) {
        return;
      }
    }
    clearCachedEvent();
    eventToAnnounce = AccessibilityEvent.obtain(event);
    this.eventId = eventId;

    delayHandler.removeMessages();
    delayHandler.delay(
        (Role.getSourceRole(event) == Role.ROLE_PAGER)
            ? DELAY_PAGE_FEEDBACK
            : DELAY_SCROLL_FEEDBACK,
        null);
  }

  private void announce() {
    if (eventToAnnounce == null) {
      return;
    }
    compositor.handleEvent(eventToAnnounce, eventId, EVENT_INTERPRETATION_SCROLL_POSITION);
    clearCachedEvent();
  }

  private void clearCachedEvent() {
    if (eventToAnnounce != null) {
      eventToAnnounce.recycle();
    }
    eventToAnnounce = null;
    eventId = null;
  }
}
