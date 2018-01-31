/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;

/**
 * A class works as an interpreter for {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} events.
 *
 * <p>With accessibility service, a view can be scrolled in two different ways:
 *
 * <ul>
 *   <li>The user scrolls a view by dragging with two fingers on screen. We call this manual scroll
 *       action.
 *   <li>Accessibility service calls {@link AccessibilityNodeInfoCompat#performAction(int, Bundle)}
 *       to scroll a node. We call this auto-scroll action.
 * </ul>
 *
 * It has majorly two functions based on the two types of scroll actions:
 *
 * <ul>
 *   <li>It provides an API {@link #scroll(AccessibilityNodeInfoCompat, int, AutoScrollCallback,
 *       EventId)} to perform auto-scroll action on a node, and register callback to invoke when the
 *       result {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received.
 *   <li>It parses scroll events result from user's manual scroll action, and notifies {@link
 *       AccessibilityFocusManager}.
 * </ul>
 */
// TODO: There is another class ProcessorScrollPosition that parses scroll event and
// announces "Showing item x to y in z". We might need to merge the logics together to serve
// as a general interpreter for Compositor and AccessibilityFocusManager.
public class ScrollController implements AccessibilityEventListener {

  /** Event types that are handled by ScrollController. */
  private static final int MASK_EVENTS_HANDLED_BY_SCROLL_CONTROLLER =
      AccessibilityEvent.TYPE_VIEW_SCROLLED;

  /**
   * Caches information of auto-scroll action. It's instantiated when {@link
   * ScrollController#scroll(AccessibilityNodeInfoCompat, int, AutoScrollCallback, EventId)} is
   * called, and used to match event to action when TYPE_VIEW_SCROLLED event is received.
   */
  private static final class AutoScrollRecord {
    final AccessibilityNodeInfoCompat autoScrolledNode;
    final AutoScrollCallback autoScrollCallback;
    // SystemClock.uptimeMillis()
    final long autoScrolledTime;

    AutoScrollRecord(
        @NonNull AccessibilityNodeInfoCompat autoScrolledNode,
        @Nullable AutoScrollCallback autoScrollCallback,
        long autoScrolledTime) {
      this.autoScrolledNode = autoScrolledNode;
      this.autoScrollCallback = autoScrollCallback;
      this.autoScrolledTime = autoScrolledTime;
    }
  }

  /**
   * Callback to be invoked when the result event of auto-scroll action is received.
   *
   * @see ScrollController#scroll(AccessibilityNodeInfoCompat, int, AutoScrollCallback, EventId)
   */
  public interface AutoScrollCallback {

    /**
     * Called when the result event of auto-scroll action is received.
     *
     * @param eventId EventId for performance tracking.
     */
    void onAutoScrolled(EventId eventId);
  }

  @SuppressWarnings("unused")
  private final AccessibilityFocusManager mAccessibilityFocusManager;

  public ScrollController(AccessibilityFocusManager accessibilityFocusManager) {
    mAccessibilityFocusManager = accessibilityFocusManager;
  }

  // Timeout to determine whether a scroll event could be resulted from the last scroll action.
  @VisibleForTesting static final int SCROLL_TIMEOUT_MS = 2000;

  private AutoScrollRecord mAutoScrollRecord = null;

  // A copy of TYPE_VIEW_SCROLLED event, which is used to calculate scroll direction.
  private AccessibilityEvent mLastScrollEvent = null;

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_SCROLL_CONTROLLER;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (event == null || event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
      return;
    }
    handleScrollEvent(event, eventId);
    // TODO: Listen to other events and clear the cached state correctly.
    // We've seen some bugs that accessibility focus is set randomly in a list, which is
    // resulted from that cached information is not cleared properly.
  }

  /**
   * Performs scroll action at the given node. Invoke the callback when the result {@link
   * AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received.
   *
   * @param node Node to scroll
   * @param scrollActionId Id of the scroll action
   * @param callback Callback to be invoked when the result event is received.
   * @param eventId EventId for performance tracking.
   * @return {@code true} If the action is successfully performed.
   */
  public boolean scroll(
      @Nullable AccessibilityNodeInfoCompat node,
      int scrollActionId,
      AutoScrollCallback callback,
      EventId eventId) {
    if (node == null) {
      return false;
    }
    long currentTime = SystemClock.uptimeMillis();
    boolean result = PerformActionUtils.performAction(node, scrollActionId, eventId);
    if (result) {
      // TODO: performAction(ACTION_SCROLL) returning TRUE cannot guarantee that we'll
      // receive TYPE_VIEW_SCROLL event for that. We need to check and defend against this case.
      saveAutoScrollRecord(node, callback, currentTime);
    }
    return result;
  }

  private void handleScrollEvent(AccessibilityEvent event, EventId eventId) {
    AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (source == null) {
      return;
    }

    if (isFromAutoScrollAction(event)) {
      notifyViewAutoScrolled(eventId);
      clearAutoScrollRecord();
    } else if (isFromManualScrollAction(event)) {
      @SearchDirectionOrUnknown int direction = getScrollDirection(event);
      if (direction != TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
        notifyViewManuallyScrolled(source, direction, eventId);
      }
    }

    if (mLastScrollEvent != null) {
      mLastScrollEvent.recycle();
    }
    mLastScrollEvent = AccessibilityEvent.obtain(event);

    AccessibilityNodeInfoUtils.recycleNodes(source);
  }

  @SearchDirectionOrUnknown
  private int getScrollDirection(AccessibilityEvent event) {
    if (mLastScrollEvent == null) {
      return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
    }

    // fromIndex, toIndex, scrollX and scrollY are four metrics to determine scroll position.
    // We calculate scroll direction by comparing the scroll position of current scroll event and
    // the last scroll event.

    int lastScrollFromIndex = mLastScrollEvent.getFromIndex();
    int lastScrollToIndex = mLastScrollEvent.getToIndex();
    int lastScrollX = mLastScrollEvent.getScrollX();
    int lastScrollY = mLastScrollEvent.getScrollY();
    // Check scroll of AdapterViews
    if (event.getFromIndex() > lastScrollFromIndex || event.getToIndex() > lastScrollToIndex) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getFromIndex() < lastScrollFromIndex
        || event.getToIndex() < lastScrollToIndex) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    // Check scroll of ScrollViews.
    if (event.getScrollX() > lastScrollX || event.getScrollY() > lastScrollY) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getScrollX() < lastScrollX || event.getScrollY() < lastScrollY) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  private boolean isFromManualScrollAction(AccessibilityEvent event) {
    if (mLastScrollEvent == null) {
      return false;
    }

    AccessibilityNodeInfoCompat lastScrolledNode =
        AccessibilityNodeInfoUtils.toCompat(mLastScrollEvent.getSource());
    AccessibilityNodeInfoCompat currentScrolledNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    try {
      return (lastScrolledNode != null) && (lastScrolledNode.equals(currentScrolledNode));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(lastScrolledNode, currentScrolledNode);
    }
  }

  /**
   * Checks whether the {@link AccessibilityEvent} is resulted from the last auto-scroll action
   * triggered by {@link #scroll(AccessibilityNodeInfoCompat, int, AutoScrollCallback, EventId)}.
   *
   * @param event The {@link AccessibilityEvent} to check.
   * @return {@code true} if the event is resulted from the auto-scroll action.
   */
  private boolean isFromAutoScrollAction(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat node = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (node == null || mAutoScrollRecord == null) {
      return false;
    }
    long timeDiff = event.getEventTime() - mAutoScrollRecord.autoScrolledTime;

    try {
      return ((timeDiff >= 0L) && (timeDiff <= SCROLL_TIMEOUT_MS))
          && (node.equals(mAutoScrollRecord.autoScrolledNode));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  @VisibleForTesting
  void notifyViewAutoScrolled(EventId eventId) {
    if (mAutoScrollRecord == null) {
      return;
    }
    AutoScrollCallback callback = mAutoScrollRecord.autoScrollCallback;
    if (callback != null) {
      callback.onAutoScrolled(eventId);
    }
  }

  @VisibleForTesting
  void notifyViewManuallyScrolled(
      AccessibilityNodeInfoCompat node,
      @TraversalStrategy.SearchDirectionOrUnknown int direction,
      EventId eventId) {
    if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return;
    }

    mAccessibilityFocusManager.notifyNodeManuallyScrolled(node, direction, eventId);
  }

  private void clearAutoScrollRecord() {
    if (mAutoScrollRecord != null) {
      AccessibilityNodeInfoUtils.recycleNodes(mAutoScrollRecord.autoScrolledNode);
    }
    mAutoScrollRecord = null;
  }

  private void saveAutoScrollRecord(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      @Nullable AutoScrollCallback callback,
      long scrollTime) {
    clearAutoScrollRecord();
    mAutoScrollRecord = new AutoScrollRecord(scrolledNode, callback, scrollTime);
  }
}
