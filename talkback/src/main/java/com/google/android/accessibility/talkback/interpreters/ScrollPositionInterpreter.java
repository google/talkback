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

package com.google.android.accessibility.talkback.interpreters;

import static com.google.android.accessibility.compositor.Compositor.EVENT_SCROLL_POSITION;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.DelayHandler;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages scroll position feedback. If an interpreted {@link AccessibilityEvent#TYPE_VIEW_SCROLLED}
 * event passes through this processor and no further events are received for a specified duration,
 * a "scroll position" message is spoken.
 */
public class ScrollPositionInterpreter implements ScrollEventHandler {

  /** Delay before sending a scroll position notification for non-viewpager node. */
  private static final long DELAY_SCROLL_FEEDBACK = 1000;

  /** Delay before sending a scroll position notification for viewpager. */
  private static final long DELAY_PAGE_FEEDBACK = 500;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final DelayHandler<Object> delayHandler =
      new DelayHandler<Object>() {
        @Override
        public void handle(Object arg) {
          sendInterpretation();
        }
      };

  @Nullable private AccessibilityEvent eventDeduplicated;
  @Nullable private EventId eventId;
  private Pipeline.InterpretationReceiver pipeline;

  /**
   * Verbosity of scroll announcement.
   *
   * <ul>
   *   <li>When set to {@code true}, announce scroll position regardless of the source action.
   *   <li>When set to {@code false}, only announce scroll position changed by scroll gesture.
   * </ul>
   */
  private boolean isVerbose;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void setPipeline(Pipeline.InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  public void setVerboseAnnouncement(boolean isVerbose) {
    this.isVerbose = isVerbose;
  }

  @Override
  public void onScrollEvent(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {

    // Filter scroll-events.
    if (!interpretation.hasValidIndex || interpretation.isDuplicateEvent) {
      return;
    }
    if (!isVerbose
        && (interpretation.userAction != ScrollEventInterpreter.ACTION_SCROLL_SHORTCUT)
        && (interpretation.userAction != ScrollEventInterpreter.ACTION_MANUAL_SCROLL)) {
      return;
    }
    // Scrolling a ViewPager2 also scrolls its tabs. Suppress the tab scroll announcement if the
    // scheduled announcement originates from a page scroll, so the pager announcement isn't
    // stopped. There's only a small possibility that this suppressed event comes from a
    // non-TabLayout (having CollectionInfo & same parent) within the 500ms time frame.
    if (Role.getSourceRole(eventDeduplicated) == Role.ROLE_PAGER) {
      AccessibilityNode eventSource = AccessibilityNode.takeOwnership(event.getSource());
      AccessibilityNode deduplicatedSource =
          AccessibilityNode.takeOwnership(eventDeduplicated.getSource());
      try {
        if (deduplicatedSource != null
            && !deduplicatedSource.equals(eventSource)
            && eventSource.getCollectionInfo() != null
            && AccessibilityNode.shareParent(eventSource, deduplicatedSource)) {
          return;
        }
      } finally {
        AccessibilityNode.recycle(
            "ScrollPositionInterpreter.onScrollEvent", eventSource, deduplicatedSource);
      }
    }

    // Rate-limit scroll-events.
    clearEventDeduplicated();
    eventDeduplicated = AccessibilityEvent.obtain(event);
    this.eventId = eventId;
    delayHandler.removeMessages();
    delayHandler.delay(
        (Role.getSourceRole(event) == Role.ROLE_PAGER)
            ? DELAY_PAGE_FEEDBACK
            : DELAY_SCROLL_FEEDBACK,
        null);
  }

  private void sendInterpretation() {
    if (eventDeduplicated == null) {
      return;
    }
    pipeline.input(
        eventId, eventDeduplicated, new Interpretation.CompositorID(EVENT_SCROLL_POSITION));
    clearEventDeduplicated();
  }

  private void clearEventDeduplicated() {
    if (eventDeduplicated != null) {
      eventDeduplicated.recycle();
    }
    eventDeduplicated = null;
    eventId = null;
  }
}
