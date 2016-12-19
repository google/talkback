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

package com.android.talkback.eventprocessor;

import android.os.SystemClock;
import android.util.Pair;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.Role;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.tutorial.AccessibilityTutorialActivity;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.NodeFilter;
import com.android.utils.PerformActionUtils;
import com.android.utils.WeakReferenceHandler;
import com.android.utils.WebInterfaceUtils;
import com.android.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.android.utils.traversal.TraversalStrategy;
import com.android.utils.traversal.TraversalStrategyUtils;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Places focus in response to various {@link AccessibilityEvent} types,
 * including hover events, list scrolling, and placing input focus. Also handles
 * single-tap activation in response to touch interaction events.
 */
public class ProcessorFocusAndSingleTap implements AccessibilityEventListener,
        CursorController.ScrollListener {
    /** Single-tap requires JellyBean (API 17). */
    public static final int MIN_API_LEVEL_SINGLE_TAP = Build.VERSION_CODES.JELLY_BEAN_MR1;

    /** Whether refocusing is enabled. Requires API 17. */
    private static final boolean SUPPORTS_INTERACTION_EVENTS =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);

    /** The timeout after which an event is no longer considered a tap. */
    private static final long TAP_TIMEOUT = ViewConfiguration.getJumpTapTimeout();

    private static final int MAX_CACHED_FOCUSED_RECORD_QUEUE = 10;

    private final TalkBackService mService;
    private final SpeechController mSpeechController;
    private final CursorController mCursorController;
    private final AccessibilityManager mAccessibilityManager;

    // The previous AccessibilityRecordCompat that failed to focus, but it is potentially
    // focusable when view scrolls, or window state changes.
    private final ArrayDeque<Pair<AccessibilityRecordCompat, Integer>>
        mCachedPotentiallyFocusableRecordQueue = new ArrayDeque<>(MAX_CACHED_FOCUSED_RECORD_QUEUE);

    private @TraversalStrategy.SearchDirectionOrUnknown int mLastScrollDirection;
    private int mLastScrollFromIndex = -1;
    private int mLastScrollToIndex = -1;
    private int mLastScrollX = -1;
    private int mLastScrollY = -1;

    /**
     * Whether single-tap activation is enabled, always {@code false} on
     * versions prior to Jelly Bean MR1.
     */
    private boolean mSingleTapEnabled;

    /** The first focused item touched during the current touch interaction. */
    private AccessibilityNodeInfoCompat mFirstFocusedItem;

    private AccessibilityNodeInfoCompat mActionScrolledNode;
    private AccessibilityNodeInfoCompat mLastFocusedItem;

    /** The number of items focused during the current touch interaction. */
    private int mFocusedItems;

    /** Whether the current interaction may result in refocusing. */
    private boolean mMaybeRefocus;

    /** Whether the current interaction may result in a single tap. */
    private boolean mMaybeSingleTap;

    private long mLastRefocusStartTime = 0;
    private long mLastRefocusEndTime = 0;
    private AccessibilityNodeInfoCompat mLastRefocusedNode = null;

    private FirstWindowFocusManager mFirstWindowFocusManager;

    public ProcessorFocusAndSingleTap(CursorController cursorController,
                                      FeedbackController feedbackController,
                                      SpeechController speechController,
                                      TalkBackService service) {
        if (cursorController == null) throw new IllegalStateException();
        if (feedbackController == null) throw new IllegalStateException();
        if (speechController == null) throw new IllegalStateException();

        mService = service;
        mSpeechController = speechController;
        mCursorController = cursorController;
        mCursorController.addScrollListener(this);
        mHandler = new FollowFocusHandler(this, feedbackController);
        mAccessibilityManager = (AccessibilityManager) service.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        mFirstWindowFocusManager = new FirstWindowFocusManager(service);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
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
                boolean isViewFocusedEvent =
                        (AccessibilityEvent.TYPE_VIEW_FOCUSED == event.getEventType());
                if (!setFocusOnView(record, isViewFocusedEvent)) {
                    // It is possible that the only speakable child of source node is invisible
                    // at the moment, but could be made visible when view scrolls, or window state
                    // changes. Cache it now. And try to focus on the cached record on:
                    // VIEW_SCROLLED, WINDOW_CONTENT_CHANGED, WINDOW_STATE_CHANGED.
                    // The above 3 are the events that could affect view visibility.
                    if(mCachedPotentiallyFocusableRecordQueue.size() ==
                            MAX_CACHED_FOCUSED_RECORD_QUEUE) {
                        mCachedPotentiallyFocusableRecordQueue.remove().first.recycle();
                    }

                    mCachedPotentiallyFocusableRecordQueue.add(
                            new Pair<>(AccessibilityRecordCompat.obtain(record),
                                    event.getEventType()));
                } else {
                    emptyCachedPotentialFocusQueue();
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                final AccessibilityNodeInfoCompat touchedNode = record.getSource();
                try {
                    if ((touchedNode != null) && !setFocusFromViewHoverEnter(touchedNode)) {
                        mHandler.sendEmptyTouchAreaFeedbackDelayed(touchedNode);
                    }
                } finally {
                    AccessibilityNodeInfoUtils.recycleNodes(touchedNode);
                }

                break;
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
                mHandler.cancelEmptyTouchAreaFeedback();
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    AccessibilityNodeInfoCompat compatSource =
                            new AccessibilityNodeInfoCompat(source);
                    mLastFocusedItem = AccessibilityNodeInfoCompat.obtain(compatSource);
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                mFirstWindowFocusManager.registerWindowChange(event);
                handleWindowStateChange(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handleWindowContentChanged();
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                handleViewScrolled(event, record);
                break;
            case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START:
                // This event type only exists on API 17+ (JB MR1).
                handleTouchInteractionStart();
                break;
            case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END:
                // This event type only exists on API 17+ (JB MR1).
                handleTouchInteractionEnd();
                break;
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
     * Sets whether single-tap activation is enabled. If it is, the follow focus
     * processor needs to avoid re-focusing items that are already focused.
     *
     * @param enabled Whether single-tap activation is enabled.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void setSingleTapEnabled(boolean enabled) {
        mSingleTapEnabled = enabled;
    }

    private void handleWindowStateChange(AccessibilityEvent event) {
        if (mLastFocusedItem != null) {
            mLastFocusedItem.recycle();
            mLastFocusedItem = null;
        }

        clearScrollAction();
        mLastScrollFromIndex = -1;
        mLastScrollToIndex = -1;

        // Since we may get WINDOW_STATE_CHANGE events from the keyboard even
        // though the active window is still another app, only clear focus if
        // the event's window ID matches the cursor's window ID.
        final AccessibilityNodeInfoCompat cursor = mCursorController.getCursor();
        if ((cursor != null) && (cursor.getWindowId() == event.getWindowId())) {
            ensureFocusConsistency();
        }
        if(cursor != null) {
            cursor.recycle();
        }
        tryFocusCachedRecord();
    }

    private void handleWindowContentChanged() {
        mHandler.followContentChangedDelayed();

        tryFocusCachedRecord();
    }

    private void handleViewScrolled(AccessibilityEvent event, AccessibilityRecordCompat record) {
        AccessibilityNodeInfoCompat source = null;

        @TraversalStrategy.SearchDirectionOrUnknown int direction;
        boolean wasScrollAction;
        if (mActionScrolledNode != null) {
            source = record.getSource();
            if (source == null) return;
            if (source.equals(mActionScrolledNode)) {
                direction = mLastScrollDirection;
                wasScrollAction = true;
                clearScrollAction();
            } else {
                direction = getScrollDirection(event);
                wasScrollAction = false;
            }
        } else {
            direction = getScrollDirection(event);
            wasScrollAction = false;
        }

        followScrollEvent(source, record, direction, wasScrollAction);

        mLastScrollFromIndex = record.getFromIndex();
        mLastScrollToIndex = record.getToIndex();
        mLastScrollX = record.getScrollX();
        mLastScrollY = record.getScrollY();

        tryFocusCachedRecord();
    }

    private @TraversalStrategy.SearchDirectionOrUnknown int getScrollDirection(
            AccessibilityEvent event) {
        //check scroll of AdapterViews
        if (event.getFromIndex() > mLastScrollFromIndex ||
                event.getToIndex() > mLastScrollToIndex) {
            return TraversalStrategy.SEARCH_FOCUS_FORWARD;
        } else if(event.getFromIndex() < mLastScrollFromIndex ||
                event.getToIndex() < mLastScrollToIndex) {
            return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
        }

        //check scroll of ScrollViews
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

        mActionScrolledNode = null;
    }

    private void tryFocusCachedRecord() {
        if (mCachedPotentiallyFocusableRecordQueue.isEmpty()) {
            return;
        }

        Iterator<Pair<AccessibilityRecordCompat, Integer>> iterator =
                mCachedPotentiallyFocusableRecordQueue.descendingIterator();

        while(iterator.hasNext()) {
            Pair<AccessibilityRecordCompat, Integer> focusableRecord = iterator.next();
            AccessibilityRecordCompat record = focusableRecord.first;
            int eventType = focusableRecord.second;
            if (setFocusOnView(record,
                    eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED)) {
                emptyCachedPotentialFocusQueue();
                return;
            }
        }
    }

    private void followScrollEvent(AccessibilityNodeInfoCompat source,
                                   AccessibilityRecordCompat record,
                                   @TraversalStrategy.SearchDirectionOrUnknown int direction,
                                   boolean wasScrollAction) {
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
            boolean validAccessibilityFocus = AccessibilityNodeInfoUtils.shouldFocusNode(
                    accessibilityFocused);
            // there are cases when scrollable container was scrolled and application set
            // focus on node that is on new container page. We should keep this focus
            boolean hasInputFocus = accessibilityFocused != null
                    && accessibilityFocused.isFocused();

            if (validAccessibilityFocus && (hasInputFocus || !wasScrollAction)) {
                // focused on valid node and scrolled not by scroll action
                // keep focus
                return;
            }

            if (validAccessibilityFocus) {
                // focused on valid node and scrolled by scroll action
                // focus on next focusable node
                if (source == null) {
                    source = record.getSource();
                    if (source == null) return;
                }
                if (!AccessibilityNodeInfoUtils.hasAncestor(accessibilityFocused, source)) {
                    return;
                }
                TraversalStrategy traversal = TraversalStrategyUtils.getTraversalStrategy(root,
                        direction);
                try {
                    focusNextFocusedNode(traversal, accessibilityFocused, direction);
                } finally {
                    traversal.recycle();
                }
            } else {
                if (mLastFocusedItem == null) {
                    // there was no focus - don't set focus
                    return;
                }

                if (source == null) {
                    source = record.getSource();
                    if (source == null) return;
                }
                if (mLastFocusedItem.equals(source) ||
                        AccessibilityNodeInfoUtils.hasAncestor(mLastFocusedItem, source)) {

                    // There is no focus now, but it was on source node's child before
                    // Try focusing the appropriate child node.
                    if (tryFocusingChild(source, direction)) {
                        return;
                    }

                    // Finally, try focusing the scrollable node itself.
                    tryFocusing(source);
                }
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(root, accessibilityFocused);
        }
    }

    private boolean focusNextFocusedNode(TraversalStrategy traversal,
                                         AccessibilityNodeInfoCompat node,
                                         @TraversalStrategy.SearchDirection int direction) {
        if (node == null) {
            return false;
        }

        NodeFilter filter = new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return node != null && AccessibilityNodeInfoUtils.shouldFocusNode(node) &&
                        PerformActionUtils.performAction(node,
                                AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
            }
        };

        AccessibilityNodeInfoCompat candidateFocus = AccessibilityNodeInfoUtils.searchFocus(
                traversal, node, direction, filter);

        return candidateFocus != null;
    }

    /**
     * @param record the AccessbilityRecord for the event
     * @param isViewFocusedEvent true if the event is TYPE_VIEW_FOCUSED, otherwise it is
     * TYPE_VIEW_SELECTED.
     */
    private boolean setFocusOnView(AccessibilityRecordCompat record, boolean isViewFocusedEvent) {
        AccessibilityNodeInfoCompat source = null;
        AccessibilityNodeInfoCompat existing = null;
        AccessibilityNodeInfoCompat child = null;

        try {
            source = record.getSource();
            if (source == null) {
                return false;
            }

            if (record.getItemCount() > 0) {
                final int index = (record.getCurrentItemIndex() - record.getFromIndex());
                if (index >= 0 && index < source.getChildCount()) {
                    child = source.getChild(index);
                    if (child != null) {
                        if (AccessibilityNodeInfoUtils.isTopLevelScrollItem(child) &&
                                tryFocusing(child)) {
                            return true;
                        }
                    }
                }
            }

            if (!isViewFocusedEvent) {
                return false;
            }

            // Logic below is only specific to TYPE_VIEW_FOCUSED event
            // Try focusing the source node.
            if (tryFocusing(source)) {
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
            child = AccessibilityNodeInfoUtils.searchFromBfs(source,
                    AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
            return child != null && tryFocusing(child);

        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source, existing, child);
        }
    }

    /**
     * Attempts to place focus within a new window.
     */
    private boolean ensureFocusConsistency() {
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

                LogUtils.log(Log.VERBOSE, "Clearing focus from invalid node");
                PerformActionUtils.performAction(focused,
                        AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(root, focused);
        }
    }

    /**
     * Handles the beginning of a new touch interaction event.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
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
                mService.interruptAllFeedback();
            }
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        } else {
            mMaybeRefocus = true;
        }

        mMaybeSingleTap = true;
        mFocusedItems = 0;
    }

    /**
     * Handles the end of an ongoing touch interaction event.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void handleTouchInteractionEnd() {
        if (mFirstFocusedItem == null) {
            return;
        }

        if (mSingleTapEnabled && mMaybeSingleTap) {
            mHandler.cancelRefocusTimeout(false);
            performClick(mFirstFocusedItem);
        }

        mFirstFocusedItem.recycle();
        mFirstFocusedItem = null;
    }

    /**
     * Attempts to place focus on an accessibility-focusable node, starting from
     * the {@code touchedNode}.
     */
    private boolean setFocusFromViewHoverEnter(AccessibilityNodeInfoCompat touchedNode) {
        AccessibilityNodeInfoCompat focusable = null;

        try {
            focusable = AccessibilityNodeInfoUtils.findFocusFromHover(touchedNode);
            if (focusable == null) {
                return false;
            }

            if (SUPPORTS_INTERACTION_EVENTS && (mFirstFocusedItem == null) && (mFocusedItems == 0)
                    && focusable.isAccessibilityFocused()) {
                mFirstFocusedItem = AccessibilityNodeInfoCompat.obtain(focusable);

                if (mSingleTapEnabled) {
                    mHandler.refocusAfterTimeout(focusable);
                    return false;
                }

                return attemptRefocusNode(focusable);
            }

            if (!tryFocusing(focusable)) {
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

    /**
     * Ensures that a single-tap will not occur when the current touch
     * interaction ends.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void cancelSingleTap() {
        mMaybeSingleTap = false;
    }

    private boolean attemptRefocusNode(AccessibilityNodeInfoCompat node) {
        if (!mMaybeRefocus || mSpeechController.isSpeaking()) {
            return false;
        }

        // Never refocus legacy web content, it will just read the title again.
        if (WebInterfaceUtils.hasLegacyWebContent(node)) {
            return false;
        }

        mLastRefocusStartTime = SystemClock.uptimeMillis();
        if (mLastRefocusedNode != null) {
            mLastRefocusedNode.recycle();
        }
        mLastRefocusedNode = AccessibilityNodeInfoCompat.obtain(node);
        boolean result = PerformActionUtils.performAction(node,
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                && tryFocusing(node, true /* force */);
        mLastRefocusEndTime = SystemClock.uptimeMillis();
        return result;
    }

    public boolean isFromRefocusAction(AccessibilityEvent event) {
        long eventTime = event.getEventTime();
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                eventType != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
            return false;
        }
        AccessibilityNodeInfo source = event.getSource();
        try {
            return mLastRefocusStartTime < eventTime &&
                    (mLastRefocusEndTime > eventTime ||
                            mLastRefocusEndTime < mLastRefocusStartTime) &&
                    mLastRefocusedNode != null &&
                    mLastRefocusedNode.getInfo().equals(source);
        } finally {
            if (source != null) {
                source.recycle();
            }
        }
    }

    private void followContentChangedEvent() {
        ensureFocusConsistency();
    }

    /**
     * If {@code wasMovingForward} is true, moves to the first focusable child.
     * Otherwise, moves to the last focusable child.
     */
    private boolean tryFocusingChild(AccessibilityNodeInfoCompat parent,
            @TraversalStrategy.SearchDirection int direction) {
        AccessibilityNodeInfoCompat child = null;

        try {
            child = findChildFromNode(parent, direction);
            return child != null && tryFocusing(child);

        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(child);
        }
    }

    /**
     * Returns the first focusable child found while traversing the child of the
     * specified node in a specific direction. Only traverses direct children.
     *
     * @param root The node to search within.
     * @param direction The direction to search, one of the
     *            {@link TraversalStrategy.SearchDirection} constants.
     * @return The first focusable child encountered in the specified direction.
     */
    private AccessibilityNodeInfoCompat findChildFromNode(AccessibilityNodeInfoCompat root,
            @TraversalStrategy.SearchDirection int direction) {
        if (root == null || root.getChildCount() == 0) {
            return null;
        }

        final TraversalStrategy traversalStrategy =
                TraversalStrategyUtils.getTraversalStrategy(root, direction);

        AccessibilityNodeInfoCompat pivotNode = traversalStrategy.focusInitial(root, direction);

        NodeFilter filter = new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return node != null && AccessibilityNodeInfoUtils.shouldFocusNode(node,
                        traversalStrategy.getSpeakingNodesCache());
            }
        };

        try {
            if (filter.accept(pivotNode)) {
                return AccessibilityNodeInfoCompat.obtain(pivotNode);
            }

            return AccessibilityNodeInfoUtils.searchFocus(traversalStrategy, pivotNode,
                    direction, filter);
        } finally {
            if (pivotNode != null) {
                pivotNode.recycle();
            }
        }
    }

    /**
     * If the source node does not have accessibility focus, attempts to focus the source node.
     * Returns {@code true} if the node was successfully focused or already had accessibility focus.
     * Note that nothing is done for source nodes that already have accessibility focus, but
     * {@code true} is returned anyways.
     */
    private boolean tryFocusing(AccessibilityNodeInfoCompat source) {
        return tryFocusing(source, false);
    }

    /**
     * If the source node does not have accessibility focus or {@code force} is {@code true},
     * attempts to focus the source node. Returns {@code true} if the node was successfully focused
     * or already had accessibility focus.
     */
    private boolean tryFocusing(AccessibilityNodeInfoCompat source, boolean force) {
        if (source == null) {
            return false;
        }

        if (!AccessibilityNodeInfoUtils.shouldFocusNode(source)) {
            return false;
        }

        boolean shouldPerformAction = force || !source.isAccessibilityFocused();
        if (shouldPerformAction && !PerformActionUtils.performAction(
                source, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)) {
            return false;
        }

        mHandler.interruptFollowDelayed();
        return true;
    }

    private void performClick(AccessibilityNodeInfoCompat node) {
        // Performing a click on an EditText does not show the IME, so we need
        // to place input focus on it. If the IME was already connected and is
        // hidden, there is nothing we can do.
        if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
            PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS);
            return;
        }

        // If a user quickly touch explores in web content (event stream <
        // TAP_TIMEOUT), we'll send an unintentional ACTION_CLICK. Switch
        // off clicking on web content for now.
        if (WebInterfaceUtils.supportsWebActions(node)) {
            return;
        }

        PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK);
    }

    /**
     * Listens for scroll events.
     *
     * @param action The type of scroll event received.
     * @param auto If {@code true}, then the scroll was initiated automatically. If
     *     {@code false}, then the user initiated the scroll action.
     */
    @Override
    public void onScroll(AccessibilityNodeInfoCompat scrolledNode, int action, boolean auto) {
        if (scrolledNode == null) {
            clearScrollAction();
        }

        @TraversalStrategy.SearchDirectionOrUnknown int direction =
                TraversalStrategyUtils.convertScrollActionToSearchDirection(action);
        if (direction != TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
            mLastScrollDirection = direction;
            if (mActionScrolledNode != null) {
                mActionScrolledNode.recycle();
            }

            if (scrolledNode != null) {
                mActionScrolledNode = AccessibilityNodeInfoCompat.obtain(scrolledNode);
            }
        }
    }

    private final FollowFocusHandler mHandler;

    private static class FollowFocusHandler
            extends WeakReferenceHandler<ProcessorFocusAndSingleTap> {
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

        public FollowFocusHandler(ProcessorFocusAndSingleTap parent,
                                  FeedbackController feedbackController) {
            super(parent);
            mFeedbackController = feedbackController;
        }

        @Override
        public void handleMessage(Message msg, ProcessorFocusAndSingleTap parent) {
            switch (msg.what) {
                case FOCUS_AFTER_CONTENT_CHANGED:
                    mHasContentChangeMessage = false;
                    parent.followContentChangedEvent();
                    break;
                case REFOCUS_AFTER_TIMEOUT:
                    parent.cancelSingleTap();
                    cancelRefocusTimeout(true);
                    break;
                case EMPTY_TOUCH_AREA:
                    if (!AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(mCachedTouchedNode)) {
                        mFeedbackController.playHaptic(R.array.view_hovered_pattern);
                        mFeedbackController.playAuditory(R.raw.view_entered, 1.3f, 1);
                    }

                    break;
            }
        }

        /**
         * Ensure that focus is placed after content change actions, but use a delay to
         * avoid consuming too many resources.
         */
        public void followContentChangedDelayed() {
            if (!mHasContentChangeMessage) {
                mHasContentChangeMessage = true;
                sendMessageDelayed(obtainMessage(FOCUS_AFTER_CONTENT_CHANGED),
                        FOCUS_AFTER_CONTENT_CHANGED_DELAY);
            }
        }

        /**
         * Attempts to refocus the specified node after a timeout period, unless
         * {@link #cancelRefocusTimeout} is called first.
         *
         * @param source The node to refocus after a timeout.
         */
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public void refocusAfterTimeout(AccessibilityNodeInfoCompat source) {
            removeMessages(REFOCUS_AFTER_TIMEOUT);

            if (mCachedFocusedNode != null) {
                mCachedFocusedNode.recycle();
                mCachedFocusedNode = null;
            }

            mCachedFocusedNode = AccessibilityNodeInfoCompat.obtain(source);

            final Message msg = obtainMessage(REFOCUS_AFTER_TIMEOUT);
            sendMessageDelayed(msg, TAP_TIMEOUT);
        }

        /**
         * Provides feedback indicating an empty or unfocusable area after a
         * delay.
         */
        public void sendEmptyTouchAreaFeedbackDelayed(AccessibilityNodeInfoCompat touchedNode) {
            cancelEmptyTouchAreaFeedback();
            mCachedTouchedNode = AccessibilityNodeInfoCompat.obtain(touchedNode);

            final Message msg = obtainMessage(EMPTY_TOUCH_AREA);
            sendMessageDelayed(msg, EMPTY_TOUCH_AREA_DELAY);
        }

        /**
         * Cancels a refocus timeout initiated by {@link #refocusAfterTimeout}
         * and optionally refocuses the target node immediately.
         *
         * @param shouldRefocus Whether to refocus the target node immediately.
         */
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public void cancelRefocusTimeout(boolean shouldRefocus) {
            removeMessages(REFOCUS_AFTER_TIMEOUT);

            final ProcessorFocusAndSingleTap parent = getParent();
            if (parent == null) {
                return;
            }

            if (shouldRefocus && (mCachedFocusedNode != null)) {
                parent.attemptRefocusNode(mCachedFocusedNode);
            }

            if (mCachedFocusedNode != null) {
                mCachedFocusedNode.recycle();
                mCachedFocusedNode = null;
            }
        }

        /**
         * Interrupt any pending follow-focus messages.
         */
        public void interruptFollowDelayed() {
            mHasContentChangeMessage = false;
            removeMessages(FOCUS_AFTER_CONTENT_CHANGED);
        }

        /**
         * Cancel any pending messages for delivering feedback indicating an
         * empty or unfocusable area.
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
        // TODO: Revisit the delay due to TV transitions if BUG changes.
        private static final int MISS_FOCUS_DELAY_TV = 1200; // Longer transitions on TV.

        private long mLastWindowStateChangeEventTime;
        private long mLastWindowId;
        private boolean mIsFirstFocusInWindow;
        private final TalkBackService mService;

        public FirstWindowFocusManager(TalkBackService service) {
            mService = service;
            mService.getCursorController().addCursorListener(this);
        }

        public void registerWindowChange(AccessibilityEvent event) {
            mLastWindowStateChangeEventTime = event.getEventTime();
            if (mLastWindowId != event.getWindowId()) {
                mLastWindowId = event.getWindowId();
                mIsFirstFocusInWindow = true;
            }
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

            int focusDelay = mService.isDeviceTelevision() ?
                    MISS_FOCUS_DELAY_TV : MISS_FOCUS_DELAY_NORMAL;

            return !isFirstFocus ||
                    event.getEventTime() - mLastWindowStateChangeEventTime > focusDelay;
        }
    }
}
