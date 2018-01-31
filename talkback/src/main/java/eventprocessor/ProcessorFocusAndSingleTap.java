/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Pair;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Places focus in response to various {@link AccessibilityEvent} types, including hover events,
 * list scrolling, and placing input focus. Also handles single-tap activation in response to touch
 * interaction events.
 */
public class ProcessorFocusAndSingleTap
    implements AccessibilityEventListener, CursorController.ScrollListener {
  /** The timeout after which an event is no longer considered a tap. */
  private static final long TAP_TIMEOUT = ViewConfiguration.getJumpTapTimeout();

  private static final int MAX_CACHED_FOCUSED_RECORD_QUEUE = 10;

  /** Event types that are handled by ProcessorFocusAndSingleTap. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_FOCUS_AND_SINGLE_TAP =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_WINDOWS_CHANGED
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END;

  private final TalkBackService mService;
  private final SpeechController mSpeechController;
  private final CursorController mCursorController;
  private final AccessibilityManager mAccessibilityManager;
  private final GlobalVariables mGlobalVariables;

  private final FollowFocusHandler mFollowFocusHandler;
  private final SyncFocusHandler mSyncFocusHandler;
  private final AutoScrollHandler mAutoScrollHandler;
  // The previous AccessibilityRecordCompat that failed to focus, but it is potentially
  // focusable when view scrolls, or window state changes.
  private final ArrayDeque<Pair<AccessibilityRecordCompat, Integer>>
      mCachedPotentiallyFocusableRecordQueue = new ArrayDeque<>(MAX_CACHED_FOCUSED_RECORD_QUEUE);

  private @TraversalStrategy.SearchDirectionOrUnknown int mLastScrollDirection;
  private int mLastScrollFromIndex = -1;
  private int mLastScrollToIndex = -1;
  private int mLastScrollX = -1;
  private int mLastScrollY = -1;

  private static final int SYNC_FOCUS_DELAY = 100; //ms
  private static final int SYNC_FOCUS_DELAY_WITH_IME = 500; // ms
  private static final int CLEAR_SCROLLED_NODE_DELAY = 1000; // ms

  /**
   * Whether single-tap activation is enabled, always {@code false} on versions prior to Jelly Bean
   * MR1.
   */
  private boolean mSingleTapEnabled = false;

  /** The first focused item touched during the current touch interaction. */
  private AccessibilityNodeInfoCompat mFirstFocusedItem;

  private AccessibilityNodeInfoCompat mActionScrolledNode;
  private AccessibilityNodeInfoCompat mLastFocusedItem;
  private boolean mIsRetryAutoScroll = false;

  /** The number of items focused during the current touch interaction. */
  private int mFocusedItems;

  /** Whether the current interaction may result in refocusing. */
  private boolean mMaybeRefocus;

  /** Whether the current interaction may result in a single tap. */
  private boolean mMaybeSingleTap;

  /** Whether the IME was open the last time the window state was changed. */
  private boolean mWasImeOpen;

  private long mLastRefocusStartTime = 0;
  private long mLastRefocusEndTime = 0;
  private AccessibilityNodeInfoCompat mLastRefocusedNode = null;

  private FirstWindowFocusManager mFirstWindowFocusManager;

  public ProcessorFocusAndSingleTap(
      CursorController cursorController,
      FeedbackController feedbackController,
      SpeechController speechController,
      TalkBackService service,
      GlobalVariables globalVariables) {
    if (cursorController == null) throw new IllegalStateException();
    if (feedbackController == null) throw new IllegalStateException();
    if (speechController == null) throw new IllegalStateException();

    mService = service;
    mSpeechController = speechController;
    mCursorController = cursorController;
    mCursorController.addScrollListener(this);
    mGlobalVariables = globalVariables;
    mFollowFocusHandler = new FollowFocusHandler(this, feedbackController);
    mSyncFocusHandler = new SyncFocusHandler();
    mAutoScrollHandler = new AutoScrollHandler(this);
    mAccessibilityManager =
        (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);
    mFirstWindowFocusManager = new FirstWindowFocusManager(service);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_FOCUS_AND_SINGLE_TAP;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!mAccessibilityManager.isTouchExplorationEnabled()) {
      // Don't manage focus when touch exploration is disabled.
      return;
    }

    final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);

    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_CLICKED:
        // Prevent conflicts between lift-to-type and single tap. This
        // is only necessary when a CLICKED event occurs during a touch
        // interaction sequence (e.g. before an INTERACTION_END event),
        // but it isn't harmful to call more often.
        cancelSingleTap();
        break;
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
      case AccessibilityEvent.TYPE_VIEW_SELECTED:
        if (!mFirstWindowFocusManager.shouldProcessFocusEvent(event)) {
          return;
        }
        boolean isViewFocusedEvent = (AccessibilityEvent.TYPE_VIEW_FOCUSED == event.getEventType());
        if (!setFocusOnView(record, isViewFocusedEvent, eventId)) {
          // It is possible that the only speakable child of source node is invisible
          // at the moment, but could be made visible when view scrolls, or window state
          // changes. Cache it now. And try to focus on the cached record on:
          // VIEW_SCROLLED, WINDOW_CONTENT_CHANGED, WINDOW_STATE_CHANGED.
          // The above 3 are the events that could affect view visibility.
          if (mCachedPotentiallyFocusableRecordQueue.size() == MAX_CACHED_FOCUSED_RECORD_QUEUE) {
            mCachedPotentiallyFocusableRecordQueue.remove().first.recycle();
          }

          mCachedPotentiallyFocusableRecordQueue.add(
              new Pair<>(AccessibilityRecordCompat.obtain(record), event.getEventType()));
        } else {
          emptyCachedPotentialFocusQueue();
        }
        break;
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
        final AccessibilityNodeInfoCompat touchedNode = record.getSource();
        try {
          if ((touchedNode != null) && !setFocusFromViewHoverEnter(touchedNode, eventId)) {
            mFollowFocusHandler.sendEmptyTouchAreaFeedbackDelayed(touchedNode);
          }
        } finally {
          AccessibilityNodeInfoUtils.recycleNodes(touchedNode);
        }

        break;
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        mFollowFocusHandler.cancelEmptyTouchAreaFeedback();
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
          AccessibilityNodeInfoCompat compatSource = AccessibilityNodeInfoUtils.toCompat(source);
          mLastFocusedItem = compatSource;
        }
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        if (!EventState.getInstance()
            .checkAndClearRecentFlag(EventState.EVENT_SKIP_FOCUS_SYNC_FROM_WINDOWS_CHANGED)) {
          scheduleSyncFocus(eventId);
        }
        break;
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        if (!EventState.getInstance()
            .checkAndClearRecentFlag(EventState.EVENT_SKIP_FOCUS_SYNC_FROM_WINDOW_STATE_CHANGED)) {
          scheduleSyncFocus(eventId);
        }
        mFirstWindowFocusManager.registerWindowChange(event);
        handleWindowStateChange(event, eventId);
        break;
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        handleWindowContentChanged(eventId);
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        handleViewScrolled(event, record, eventId);
        break;
      case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START:
        // This event type only exists on API 17+ (JB MR1).
        handleTouchInteractionStart();
        break;
      case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END:
        // This event type only exists on API 17+ (JB MR1).
        handleTouchInteractionEnd(eventId);
        break;
      default: // fall out
    }
  }

  private void emptyCachedPotentialFocusQueue() {
    if (mCachedPotentiallyFocusableRecordQueue.isEmpty()) {
      return;
    }

    for (Pair<AccessibilityRecordCompat, Integer> focusableRecord :
        mCachedPotentiallyFocusableRecordQueue) {
      focusableRecord.first.recycle();
    }
    mCachedPotentiallyFocusableRecordQueue.clear();
  }

  /**
   * Sets whether single-tap activation is enabled. If it is, the follow focus processor needs to
   * avoid re-focusing items that are already focused.
   *
   * @param enabled Whether single-tap activation is enabled.
   */
  public void setSingleTapEnabled(boolean enabled) {
    mSingleTapEnabled = enabled;
  }

  private void handleWindowStateChange(AccessibilityEvent event, EventId eventId) {
    if (mLastFocusedItem != null) {
      mLastFocusedItem.recycle();
      mLastFocusedItem = null;
    }

    clearScrollAction();
    mCursorController.setNavigationEnabled(true);

    mLastScrollFromIndex = -1;
    mLastScrollToIndex = -1;

    // Since we may get WINDOW_STATE_CHANGE events from the keyboard even
    // though the active window is still another app, only clear focus if
    // the event's window ID matches the cursor's window ID.
    final AccessibilityNodeInfoCompat cursor = mCursorController.getCursor();
    if ((cursor != null) && (cursor.getWindowId() == event.getWindowId())) {
      ensureFocusConsistency(eventId);
    }
    if (cursor != null) {
      cursor.recycle();
    }
    tryFocusCachedRecord(eventId);
  }

  private void handleWindowContentChanged(EventId eventId) {
    mFollowFocusHandler.followContentChangedDelayed(eventId);

    tryFocusCachedRecord(eventId);
  }

  private void handleViewScrolled(
      AccessibilityEvent event, AccessibilityRecordCompat record, EventId eventId) {
    final AccessibilityNodeInfoCompat source = record.getSource();
    if (source == null) {
      return;
    }

    if (mActionScrolledNode == null) {
      handleViewManualScrolled(source, getScrollDirection(event), eventId);
    } else if (source.equals(mActionScrolledNode)) {
      handleViewAutoScrolled(source, mLastScrollDirection, eventId);
    } else {
      // mActionScrolledNode is assigned immediately after successfully performing auto
      // scroll action on a node. Thus the next TYPE_VIEW_SCROLLED event should be fired
      // by this node. If for some reason we don't receive the event from the correct
      // node, do nothing here and let the {@link mAutoScrollHandler} to clear the state.
      source.recycle();
      return;
    }

    source.recycle();

    mLastScrollFromIndex = record.getFromIndex();
    mLastScrollToIndex = record.getToIndex();
    mLastScrollX = record.getScrollX();
    mLastScrollY = record.getScrollY();
    tryFocusCachedRecord(eventId);
  }

  private void handleViewAutoScrolled(
      @NonNull AccessibilityNodeInfoCompat scrolledNode, // recycled by the calling function
      @TraversalStrategy.SearchDirectionOrUnknown int direction,
      EventId eventId) {
    clearScrollAction();
    // SEARCH_FOCUS_UNKNOWN can be passed, so need to guarantee that direction is a
    // @TraversalStrategy.SearchDirection before continuing.
    if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return;
    }

    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat accessibilityFocused = null;

    try {
      // First, see if we've already placed accessibility focus.
      root = AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
      if (root == null) {
        return;
      }

      accessibilityFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      final boolean validAccessibilityFocus =
          AccessibilityNodeInfoUtils.shouldFocusNode(accessibilityFocused);
      // There are cases when scrollable container was scrolled and application set
      // focus on node that is on new container page. We should keep this focus.
      final boolean hasInputFocus =
          accessibilityFocused != null && accessibilityFocused.isFocused();

      if (validAccessibilityFocus && hasInputFocus) {
        // Focused on valid node and scrolled not by scroll action, then keep focus.
        return;
      }

      if (validAccessibilityFocus) {
        // After auto-scroll action, we still have a valid a11y focus on the screen.
        if (!AccessibilityNodeInfoUtils.hasAncestor(accessibilityFocused, scrolledNode)) {
          return;
        }
        final TraversalStrategy traversal =
            TraversalStrategyUtils.getTraversalStrategy(root, direction);
        try {
          if (!focusNextFocusedNode(
                  traversal, accessibilityFocused, scrolledNode, direction, eventId)
              && !mIsRetryAutoScroll) {
            // If we cannot find the next accessibility focus under the scrolled node,
            // then this scroll action might be just scrolling to show the margin of
            // the container, and doesn't expose more children node in the container.
            // We should repeat the last navigation action.
            mCursorController.repeatLastNavigationAction();
          }
        } finally {
          traversal.recycle();
        }
      } else {
        if (mLastFocusedItem == null) {
          // There was no focus - don't set focus.
          return;
        }

        // We have to check both upwards(ancestor) and downwards(descendant) to avoid
        // false positive cases introduced by some accessibility cache update issue.
        // (Sometimes hasAncestor(A, B) returns true, but hasDescendant(B, A) check
        // returns false.)
        if (!mCursorController.isNativeMacroGranularity()
            && AccessibilityNodeInfoUtils.hasAncestor(mLastFocusedItem, scrolledNode)
            && AccessibilityNodeInfoUtils.hasDescendant(scrolledNode, mLastFocusedItem)) {
          TraversalStrategy traversal =
              TraversalStrategyUtils.getTraversalStrategy(root, direction);
          // If the previous focus is still under the node tree of the scrollable view,
          // but it's now not valid for some reason(e.g. invisible). Then look for the
          // next focusable node starting from the previous focus.
          try {
            if (focusNextFocusedNode(traversal, mLastFocusedItem, null, direction, eventId)) {
              return;
            }
          } finally {
            traversal.recycle();
          }
        }
        // mLastFocusedItem is not in the source
        // If the previous focus has been detached from the scrollable view, or it's not a
        // auto-scroll action, we should conservatively focus on the very first/last child
        // of the source.
        if (tryFocusingChild(scrolledNode, direction, eventId)) {
          return;
        }

        // Finally, try focusing the scrollable node itself.
        tryFocusing(scrolledNode, eventId);
        mCursorController.setLastFocusedNodeParent(scrolledNode);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, accessibilityFocused);
      mCursorController.setNavigationEnabled(true);
    }
  }

  private void handleViewManualScrolled(
      @NonNull AccessibilityNodeInfoCompat scrolledNode, // recycled by the calling function
      @TraversalStrategy.SearchDirectionOrUnknown int direction,
      EventId eventId) {
    // SEARCH_FOCUS_UNKNOWN can be passed, so need to guarantee that direction is a
    // @TraversalStrategy.SearchDirection before continuing.
    if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return;
    }

    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat accessibilityFocused = null;

    try {
      // First, see if we've already placed accessibility focus.
      root = AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
      if (root == null) {
        return;
      }

      accessibilityFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      final boolean validAccessibilityFocus =
          AccessibilityNodeInfoUtils.shouldFocusNode(accessibilityFocused);
      // There are cases when scrollable container was scrolled and application set
      // focus on node that is on new container page. We should keep this focus.

      if (!validAccessibilityFocus) {
        if (mLastFocusedItem == null || !mLastFocusedItem.isAccessibilityFocused()) {
          // There was no focus - don't set focus.
          return;
        }

        // Check if mLastFocusedItem is in the same accessibility node tree as the scrolled item
        // If the previous focus has been detached from the scrollable view, or it's not a
        // auto-scroll action, we should conservatively focus on the very first/last child
        // of the source.
        if ((AccessibilityNodeInfoUtils.hasAncestor(mLastFocusedItem, scrolledNode)
            || AccessibilityNodeInfoUtils.hasDescendant(scrolledNode, mLastFocusedItem))) {
          if (tryFocusingChild(scrolledNode, direction, eventId)) {
            return;
          }
        }

        // Finally, try focusing the scrollable node itself.
        tryFocusing(scrolledNode, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, accessibilityFocused);
    }
  }

  private @TraversalStrategy.SearchDirectionOrUnknown int getScrollDirection(
      AccessibilityEvent event) {
    // Check scroll of AdapterViews
    if (event.getFromIndex() > mLastScrollFromIndex || event.getToIndex() > mLastScrollToIndex) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getFromIndex() < mLastScrollFromIndex
        || event.getToIndex() < mLastScrollToIndex) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    // Check scroll of ScrollViews.
    if (event.getScrollX() > mLastScrollX || event.getScrollY() > mLastScrollY) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getScrollX() < mLastScrollX || event.getScrollY() < mLastScrollY) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  private void clearScrollAction() {
    mLastScrollDirection = TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
    if (mActionScrolledNode != null) {
      mActionScrolledNode.recycle();
    }

    mAutoScrollHandler.cancelTimeout();
    mActionScrolledNode = null;
    mIsRetryAutoScroll = false;
  }

  private void tryFocusCachedRecord(EventId eventId) {
    if (mCachedPotentiallyFocusableRecordQueue.isEmpty()) {
      return;
    }

    Iterator<Pair<AccessibilityRecordCompat, Integer>> iterator =
        mCachedPotentiallyFocusableRecordQueue.descendingIterator();

    while (iterator.hasNext()) {
      Pair<AccessibilityRecordCompat, Integer> focusableRecord = iterator.next();
      AccessibilityRecordCompat record = focusableRecord.first;
      int eventType = focusableRecord.second;
      if (setFocusOnView(record, eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED, eventId)) {
        emptyCachedPotentialFocusQueue();
        return;
      }
    }
  }

  private boolean focusNextFocusedNode(
      TraversalStrategy traversal,
      AccessibilityNodeInfoCompat node,
      final AccessibilityNodeInfoCompat root,
      @TraversalStrategy.SearchDirection int direction,
      final EventId eventId) {
    if (node == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filterDefault =
        mCursorController.getFilter(traversal, false);
    Filter<AccessibilityNodeInfoCompat> filterWithAdditionalCheck =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return (root == null || AccessibilityNodeInfoUtils.hasAncestor(node, root))
                && PerformActionUtils.performAction(
                    node, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
          }
        };
    Filter<AccessibilityNodeInfoCompat> filter = filterDefault.and(filterWithAdditionalCheck);

    AccessibilityNodeInfoCompat candidateFocus =
        TraversalStrategyUtils.searchFocus(traversal, node, direction, filter);

    return candidateFocus != null;
  }

  /**
   * @param record the AccessbilityRecord for the event.
   * @param isViewFocusedEvent true if the event is TYPE_VIEW_FOCUSED, otherwise it is
   *     TYPE_VIEW_SELECTED.
   */
  private boolean setFocusOnView(
      AccessibilityRecordCompat record, boolean isViewFocusedEvent, EventId eventId) {
    AccessibilityNodeInfoCompat source = null;
    AccessibilityNodeInfoCompat existing = null;
    AccessibilityNodeInfoCompat child = null;

    try {
      source = record.getSource();
      if (source == null || !source.refresh()) {
        return false;
      }

      if (record.getItemCount() > 0) {
        final int index = (record.getCurrentItemIndex() - record.getFromIndex());
        if (index >= 0 && index < source.getChildCount()) {
          child = source.getChild(index);
          if (child != null) {
            if (AccessibilityNodeInfoUtils.isTopLevelScrollItem(child)
                && tryFocusing(child, eventId)) {
              return true;
            }
          }
        }
      }

      if (!isViewFocusedEvent) {
        return false;
      }

      // Logic below is only specific to TYPE_VIEW_FOCUSED event.
      // Try focusing the source node.
      if (tryFocusing(source, eventId)) {
        return true;
      }

      // If we fail and the source node already contains focus, abort.
      existing = source.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (existing != null) {
        return false;
      }

      // If we fail to focus a node, perhaps because it is a focusable
      // but non-speaking container, we should still attempt to place
      // focus on a speaking child within the container.
      child =
          AccessibilityNodeInfoUtils.searchFromBfs(
              source, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
      return child != null && tryFocusing(child, eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source, existing, child);
    }
  }

  /** Attempts to place focus within a new window. */
  private boolean ensureFocusConsistency(EventId eventId) {
    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat focused = null;

    try {
      root = AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
      if (root == null) {
        return false;
      }

      // First, see if we've already placed accessibility focus.
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (focused != null) {
        if (AccessibilityNodeInfoUtils.shouldFocusNode(focused)) {
          return true;
        }

        PerformActionUtils.performAction(
            focused, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId);
      }

      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, focused);
    }
  }

  /** Handles the beginning of a new touch interaction event. */
  private void handleTouchInteractionStart() {
    if (mFirstFocusedItem != null) {
      mFirstFocusedItem.recycle();
      mFirstFocusedItem = null;
    }

    if (mSpeechController.isSpeaking()) {
      mMaybeRefocus = false;

      final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
      // Don't silence speech on first touch if the tutorial is active
      // or if a WebView is active. This works around an issue where
      // the IME is unintentionally dismissed by WebView's
      // performAction implementation.
      if (!AccessibilityTutorialActivity.isTutorialActive()
          && Role.getRole(currentNode) != Role.ROLE_WEB_VIEW) {
        mService.interruptAllFeedback(false /* stopTtsSpeechCompletely */);
      }
      AccessibilityNodeInfoUtils.recycleNodes(currentNode);
    } else {
      mMaybeRefocus = true;
    }

    mMaybeSingleTap = true;
    mFocusedItems = 0;
  }

  /** Handles the end of an ongoing touch interaction event. */
  private void handleTouchInteractionEnd(EventId eventId) {
    if (mFirstFocusedItem == null) {
      return;
    }

    if (mSingleTapEnabled && mMaybeSingleTap) {
      mFollowFocusHandler.cancelRefocusTimeout(false, eventId);
      performClick(mFirstFocusedItem, eventId);
    }

    mFirstFocusedItem.recycle();
    mFirstFocusedItem = null;
  }

  /**
   * Attempts to place focus on an accessibility-focusable node, starting from the {@code
   * touchedNode}.
   */
  private boolean setFocusFromViewHoverEnter(
      AccessibilityNodeInfoCompat touchedNode, EventId eventId) {
    AccessibilityNodeInfoCompat focusable = null;

    try {
      focusable = AccessibilityNodeInfoUtils.findFocusFromHover(touchedNode);
      if (focusable == null) {
        return false;
      }

      if ((mFirstFocusedItem == null)
          && (mFocusedItems == 0)
          && focusable.isAccessibilityFocused()) {
        mFirstFocusedItem = AccessibilityNodeInfoCompat.obtain(focusable);

        if (mSingleTapEnabled) {
          mFollowFocusHandler.refocusAfterTimeout(focusable, eventId);
          return false;
        }

        return attemptRefocusNode(focusable, eventId);
      }

      if (!tryFocusing(focusable, eventId)) {
        return false;
      }

      mService.getInputModeManager().setInputMode(InputModeManager.INPUT_MODE_TOUCH);

      // If something received focus, single tap cannot occur.
      if (mSingleTapEnabled) {
        cancelSingleTap();
      }

      mFocusedItems++;

      return true;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(focusable);
    }
  }

  /** Ensures that a single-tap will not occur when the current touch interaction ends. */
  private void cancelSingleTap() {
    mMaybeSingleTap = false;
  }

  private boolean attemptRefocusNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (!mMaybeRefocus || mSpeechController.isSpeaking()) {
      return false;
    }

    mLastRefocusStartTime = SystemClock.uptimeMillis();
    if (mLastRefocusedNode != null) {
      mLastRefocusedNode.recycle();
    }
    mLastRefocusedNode = AccessibilityNodeInfoCompat.obtain(node);
    boolean result =
        PerformActionUtils.performAction(
                node, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId)
            && tryFocusing(node, true /* force */, eventId);
    mLastRefocusEndTime = SystemClock.uptimeMillis();
    return result;
  }

  public boolean isFromRefocusAction(AccessibilityEvent event) {
    long eventTime = event.getEventTime();
    int eventType = event.getEventType();
    if (eventType != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
        && eventType != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
      return false;
    }
    AccessibilityNodeInfo source = event.getSource();
    try {
      return mLastRefocusStartTime < eventTime
          && (mLastRefocusEndTime > eventTime || mLastRefocusEndTime < mLastRefocusStartTime)
          && mLastRefocusedNode != null
          && mLastRefocusedNode.getInfo().equals(source);
    } finally {
      if (source != null) {
        source.recycle();
      }
    }
  }

  private void followContentChangedEvent(EventId eventId) {
    ensureFocusConsistency(eventId);
  }

  /**
   * If {@code wasMovingForward} is true, moves to the first focusable child. Otherwise, moves to
   * the last focusable child.
   */
  private boolean tryFocusingChild(
      AccessibilityNodeInfoCompat parent,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {
    AccessibilityNodeInfoCompat child = null;

    try {
      child = findChildFromNode(parent, direction);
      return child != null && tryFocusing(child, eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(child);
    }
  }

  /**
   * Returns the first focusable child found while traversing the child of the specified node in a
   * specific direction. Only traverses direct children.
   *
   * @param root The node to search within.
   * @param direction The direction to search, one of the {@link TraversalStrategy.SearchDirection}
   *     constants.
   * @return The first focusable child encountered in the specified direction.
   */
  private AccessibilityNodeInfoCompat findChildFromNode(
      AccessibilityNodeInfoCompat root, @TraversalStrategy.SearchDirection int direction) {
    if (root == null || root.getChildCount() == 0) {
      return null;
    }

    final TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(root, direction);

    AccessibilityNodeInfoCompat pivotNode = traversalStrategy.focusInitial(root, direction);

    Filter<AccessibilityNodeInfoCompat> filter =
        mCursorController.getFilter(traversalStrategy, true);

    try {
      if (filter.accept(pivotNode)) {
        return AccessibilityNodeInfoCompat.obtain(pivotNode);
      }

      return TraversalStrategyUtils.searchFocus(traversalStrategy, pivotNode, direction, filter);
    } finally {
      if (pivotNode != null) {
        pivotNode.recycle();
      }
    }
  }

  /**
   * If the source node does not have accessibility focus, attempts to focus the source node.
   * Returns {@code true} if the node was successfully focused or already had accessibility focus.
   * Note that nothing is done for source nodes that already have accessibility focus, but {@code
   * true} is returned anyways.
   */
  private boolean tryFocusing(AccessibilityNodeInfoCompat source, EventId eventId) {
    return tryFocusing(source, false, eventId);
  }

  /**
   * If the source node does not have accessibility focus or {@code force} is {@code true}, attempts
   * to focus the source node. Returns {@code true} if the node was successfully focused or already
   * had accessibility focus.
   */
  private boolean tryFocusing(AccessibilityNodeInfoCompat source, boolean force, EventId eventId) {
    if (source == null) {
      return false;
    }

    if (!AccessibilityNodeInfoUtils.shouldFocusNode(source)) {
      return false;
    }

    boolean shouldPerformAction = force || !source.isAccessibilityFocused();
    if (shouldPerformAction
        && !PerformActionUtils.performAction(
            source, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId)) {
      return false;
    }

    mFollowFocusHandler.interruptFollowDelayed();
    return true;
  }

  private void performClick(AccessibilityNodeInfoCompat node, EventId eventId) {
    // Performing a click on an EditText does not show the IME, so we need
    // to place input focus on it. If the IME was already connected and is
    // hidden, there is nothing we can do.
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS, eventId);
      return;
    }

    // If a user quickly touch explores in web content (event stream <
    // TAP_TIMEOUT), we'll send an unintentional ACTION_CLICK. Switch
    // off clicking on web content for now.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      return;
    }

    PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
  }

  /**
   * Listens for scroll events.
   *
   * @param action The type of scroll event received.
   * @param auto If {@code true}, then the scroll was initiated automatically. If {@code false},
   *     then the user initiated the scroll action.
   */
  @Override
  public void onScroll(
      AccessibilityNodeInfoCompat scrolledNode,
      int action,
      boolean auto,
      boolean isRepeatNavigationForAutoScroll) {
    if (scrolledNode == null) {
      clearScrollAction();
    } else {
      mAutoScrollHandler.cancelTimeout();
    }
    mIsRetryAutoScroll = isRepeatNavigationForAutoScroll;
    @TraversalStrategy.SearchDirectionOrUnknown
    int direction = TraversalStrategyUtils.convertScrollActionToSearchDirection(action);
    if (direction != TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      mLastScrollDirection = direction;
      if (mActionScrolledNode != null) {
        mActionScrolledNode.recycle();
      }

      if (scrolledNode != null) {
        mActionScrolledNode = AccessibilityNodeInfoCompat.obtain(scrolledNode);
        mAutoScrollHandler.clearAutoScrollNodeDelayed();
      }
    }
  }

  public void scheduleSyncFocus(EventId eventId) {
    mSyncFocusHandler.removeMessages(SyncFocusHandler.MESSAGE_WHAT_SYNC);
    mSyncFocusHandler.sendMessageDelayed(
        mSyncFocusHandler.obtainMessage(SyncFocusHandler.MESSAGE_WHAT_SYNC, eventId),
        mWasImeOpen ? SYNC_FOCUS_DELAY_WITH_IME : SYNC_FOCUS_DELAY);
  }

  private void attemptSyncA11yAndInputFocus(EventId eventId) {
    AccessibilityNodeInfoCompat root =
        AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
    if (root == null) {
      return;
    }

    WindowManager windowManager = new WindowManager(false /* ignore RTL state */);
    windowManager.setWindows(mService.getWindows());
    boolean isImeOpen = windowManager.isInputWindowOnScreen();

    AccessibilityNodeInfoCompat a11yFocus =
        root.findFocus(AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
    AccessibilityNodeInfoCompat inputFocus =
        root.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
    try {
      if (a11yFocus == null
          && inputFocus != null
          && AccessibilityNodeInfoUtils.isEditable(inputFocus)) {
        // If the IME was recently closed, don't re-announce the node when focusing it.
        if (mWasImeOpen && !isImeOpen) {
          mGlobalVariables.setFlag(GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_IME_CLOSED);
        }

        mGlobalVariables.setFlag(GlobalVariables.EVENT_SYNCED_ACCESSIBILITY_FOCUS);
        EventState.getInstance().setFlag(EventState.EVENT_HINT_FOR_SYNCED_ACCESSIBILITY_FOCUS);
        PerformActionUtils.performAction(
            inputFocus, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, a11yFocus, inputFocus);
    }

    mWasImeOpen = isImeOpen;
  }

  private class SyncFocusHandler extends Handler {
    public static final int MESSAGE_WHAT_SYNC = 1;

    @Override
    public void handleMessage(Message message) {
      if (message.what != MESSAGE_WHAT_SYNC) {
        return;
      }
      EventId eventId = (EventId) message.obj;
      attemptSyncA11yAndInputFocus(eventId);
    }
  }

  /**
   * A handler to reset the cached auto-scrollable node after timeout. When we perform auto-scroll
   * action in {@link CursorController}, we cache the scrolled node as {@link
   * ProcessorFocusAndSingleTap#mActionScrolledNode}. This node will be reset to null when we
   * receive {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} event from that node. Sometimes due to
   * the app implementation, even if the scroll action is successfully performed, no corresponding
   * event is received. Then we use this handler to reset the cached node.
   */
  private static class AutoScrollHandler extends WeakReferenceHandler<ProcessorFocusAndSingleTap> {
    private static final int MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE = 1;

    public AutoScrollHandler(ProcessorFocusAndSingleTap parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, ProcessorFocusAndSingleTap parent) {
      if (msg.what == MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE) {
        parent.clearScrollAction();
        parent.mCursorController.setNavigationEnabled(true);
      }
    }

    /** Sets {@link ProcessorFocusAndSingleTap#mActionScrolledNode} to null after timeout. */
    public void clearAutoScrollNodeDelayed() {
      sendEmptyMessageDelayed(MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE, CLEAR_SCROLLED_NODE_DELAY);
    }

    /**
     * Cancels any pending messages to set
     *
     * @link ProcessorFocusAndSingleTap#mActionScrolledNode} to null.
     */
    public void cancelTimeout() {
      removeMessages(MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE);
    }
  }

  private static class FollowFocusHandler extends WeakReferenceHandler<ProcessorFocusAndSingleTap> {
    private static final int FOCUS_AFTER_CONTENT_CHANGED = 2;
    private static final int REFOCUS_AFTER_TIMEOUT = 3;
    private static final int EMPTY_TOUCH_AREA = 5;

    /** Delay after a scroll event before checking focus. */
    private static final long FOCUS_AFTER_CONTENT_CHANGED_DELAY = 500;

    /** Delay for indicating the user has explored into an unfocusable area. */
    private static final long EMPTY_TOUCH_AREA_DELAY = 100;

    private AccessibilityNodeInfoCompat mCachedFocusedNode;
    private AccessibilityNodeInfoCompat mCachedTouchedNode;
    private final FeedbackController mFeedbackController;
    boolean mHasContentChangeMessage = false;

    public FollowFocusHandler(
        ProcessorFocusAndSingleTap parent, FeedbackController feedbackController) {
      super(parent);
      mFeedbackController = feedbackController;
    }

    @Override
    public void handleMessage(Message msg, ProcessorFocusAndSingleTap parent) {
      switch (msg.what) {
        case FOCUS_AFTER_CONTENT_CHANGED:
          {
            EventId eventId = (EventId) msg.obj;
            mHasContentChangeMessage = false;
            parent.followContentChangedEvent(eventId);
          }
          break;
        case REFOCUS_AFTER_TIMEOUT:
          {
            EventId eventId = (EventId) msg.obj;
            parent.cancelSingleTap();
            cancelRefocusTimeout(true, eventId);
          }
          break;
        case EMPTY_TOUCH_AREA:
          if (!AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(mCachedTouchedNode)) {
            mFeedbackController.playHaptic(R.array.view_hovered_pattern);
            mFeedbackController.playAuditory(R.raw.view_entered, 1.3f, 1);
          }
          break;
        default: // fall out
      }
    }

    /**
     * Ensure that focus is placed after content change actions, but use a delay to avoid consuming
     * too many resources.
     */
    public void followContentChangedDelayed(EventId eventId) {
      if (!mHasContentChangeMessage) {
        mHasContentChangeMessage = true;
        sendMessageDelayed(
            obtainMessage(FOCUS_AFTER_CONTENT_CHANGED, eventId), FOCUS_AFTER_CONTENT_CHANGED_DELAY);
      }
    }

    /**
     * Attempts to refocus the specified node after a timeout period, unless {@link
     * #cancelRefocusTimeout} is called first.
     *
     * @param source The node to refocus after a timeout.
     */
    public void refocusAfterTimeout(AccessibilityNodeInfoCompat source, EventId eventId) {
      removeMessages(REFOCUS_AFTER_TIMEOUT);

      if (mCachedFocusedNode != null) {
        mCachedFocusedNode.recycle();
        mCachedFocusedNode = null;
      }

      mCachedFocusedNode = AccessibilityNodeInfoCompat.obtain(source);

      final Message msg = obtainMessage(REFOCUS_AFTER_TIMEOUT, eventId);
      sendMessageDelayed(msg, TAP_TIMEOUT);
    }

    /** Provides feedback indicating an empty or unfocusable area after a delay. */
    public void sendEmptyTouchAreaFeedbackDelayed(AccessibilityNodeInfoCompat touchedNode) {
      cancelEmptyTouchAreaFeedback();
      mCachedTouchedNode = AccessibilityNodeInfoCompat.obtain(touchedNode);

      final Message msg = obtainMessage(EMPTY_TOUCH_AREA);
      sendMessageDelayed(msg, EMPTY_TOUCH_AREA_DELAY);
    }

    /**
     * Cancels a refocus timeout initiated by {@link #refocusAfterTimeout} and optionally refocuses
     * the target node immediately.
     *
     * @param shouldRefocus Whether to refocus the target node immediately.
     */
    public void cancelRefocusTimeout(boolean shouldRefocus, EventId eventId) {
      removeMessages(REFOCUS_AFTER_TIMEOUT);

      final ProcessorFocusAndSingleTap parent = getParent();
      if (parent == null) {
        return;
      }

      if (shouldRefocus && (mCachedFocusedNode != null)) {
        parent.attemptRefocusNode(mCachedFocusedNode, eventId);
      }

      if (mCachedFocusedNode != null) {
        mCachedFocusedNode.recycle();
        mCachedFocusedNode = null;
      }
    }

    /** Interrupt any pending follow-focus messages. */
    public void interruptFollowDelayed() {
      mHasContentChangeMessage = false;
      removeMessages(FOCUS_AFTER_CONTENT_CHANGED);
    }

    /**
     * Cancel any pending messages for delivering feedback indicating an empty or unfocusable area.
     */
    public void cancelEmptyTouchAreaFeedback() {
      removeMessages(EMPTY_TOUCH_AREA);

      if (mCachedTouchedNode != null) {
        mCachedTouchedNode.recycle();
        mCachedTouchedNode = null;
      }
    }
  }

  private static class FirstWindowFocusManager implements CursorController.CursorListener {
    private static final int MISS_FOCUS_DELAY_NORMAL = 300;
    // TODO: Revisit the delay due to TV transitions if changes.
    private static final int MISS_FOCUS_DELAY_TV = 1200; // Longer transitions on TV.

    private static final String SOFT_INPUT_WINDOW = "android.inputmethodservice.SoftInputWindow";

    private long mLastWindowStateChangeEventTime;
    private int mLastWindowId;
    private boolean mIsFirstFocusInWindow;
    private final TalkBackService mService;

    public FirstWindowFocusManager(TalkBackService service) {
      mService = service;
      mService.getCursorController().addCursorListener(this);
    }

    public void registerWindowChange(AccessibilityEvent event) {
      mLastWindowStateChangeEventTime = event.getEventTime();
      if (mLastWindowId != event.getWindowId() && !shouldIgnoreWindowChangeEvent(event)) {
        mLastWindowId = event.getWindowId();
        mIsFirstFocusInWindow = true;
      }
    }

    /**
     * Decides whether to ignore an event for purposes of registering the first-focus window change;
     * returns true events that come from non-main windows such as IMEs.
     */
    private boolean shouldIgnoreWindowChangeEvent(AccessibilityEvent event) {
      if (event.getWindowId() == -1) {
        return true;
      }

      // The specific SoftInputWindow check seems to be necessary for Android TV.
      if (SOFT_INPUT_WINDOW.equals(event.getClassName())) {
        return true;
      }

      return AccessibilityEventUtils.isNonMainWindowEvent(event);
    }

    @Override
    public void beforeSetCursor(AccessibilityNodeInfoCompat newCursor, int action) {
      // Manual focus actions should go through, even if mLastWindowId doesn't match.
      if (action == AccessibilityNodeInfoCompat.ACTION_FOCUS) {
        mLastWindowId = newCursor.getWindowId();
      }
    }

    @Override
    public void onSetCursor(AccessibilityNodeInfoCompat newCursor, int action) {}

    public boolean shouldProcessFocusEvent(AccessibilityEvent event) {
      boolean isFirstFocus = mIsFirstFocusInWindow;
      mIsFirstFocusInWindow = false;

      if (mLastWindowId != event.getWindowId()) {
        mLastWindowId = event.getWindowId();
        return false;
      }

      int focusDelay =
          FormFactorUtils.getInstance(mService).isTv()
              ? MISS_FOCUS_DELAY_TV
              : MISS_FOCUS_DELAY_NORMAL;

      return !isFirstFocus || event.getEventTime() - mLastWindowStateChangeEventTime > focusDelay;
    }
  }
}
