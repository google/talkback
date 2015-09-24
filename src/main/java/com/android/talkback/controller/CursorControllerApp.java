/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.talkback.controller;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.DatePicker;
import android.widget.NumberPicker;

import com.android.talkback.CursorGranularity;
import com.android.talkback.CursorGranularityManager;
import com.android.talkback.KeyComboManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.eventprocessor.EventState;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.NodeFilter;
import com.android.utils.PerformActionUtils;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.WebInterfaceUtils;
import com.android.utils.WindowManager;
import com.android.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.android.utils.traversal.NodeFocusFinder;
import com.android.utils.traversal.OrderedTraversalStrategy;
import com.android.utils.traversal.TraversalStrategy;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles screen reader cursor management.
 */
public class CursorControllerApp implements
        CursorController, AccessibilityEventListener, KeyComboManager.KeyComboListener {
    /** Represents navigation to next element. */
    private static final int NAVIGATION_DIRECTION_NEXT = 1;

    private static final String LOGTAG = "CursorControllerApp";

    /** Represents navigation to previous element. */
    private static final int NAVIGATION_DIRECTION_PREVIOUS = -1;

    /** The host service. Used to access the root node. */
    private final TalkBackService mService;

    /** Handles traversal using granularity. */
    private final CursorGranularityManager mGranularityManager;

    /** Whether the user hit an edge with the last swipe. */
    private boolean mReachedEdge;
    private boolean mGranularityNavigationReachedEdge;

    private final Set<GranularityChangeListener> mGranularityListeners = new HashSet<>();

    private final Set<ScrollListener> mScrollListeners = new HashSet<>();

    private int mSwitchNodeWithGranularityDirection = 0;

    /**
     * Creates a new cursor controller using the specified input controller.
     *
     * @param service The accessibility service. Used to obtain the current root
     *            node.
     */
    public CursorControllerApp(TalkBackService service) {
        mService = service;
        mGranularityManager = new CursorGranularityManager(service);
    }

    @Override
    public void addGranularityListener(GranularityChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }

        mGranularityListeners.add(listener);
    }

    @Override
    public void removeGranularityListener(GranularityChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }

        mGranularityListeners.remove(listener);
    }

    @Override
    public void addScrollListener(ScrollListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }

        mScrollListeners.add(listener);
    }

    @Override
    public void shutdown() {
        mGranularityManager.shutdown();
    }

    @Override
    public boolean refocus() {
        final AccessibilityNodeInfoCompat node = getCursor();
        if (node == null) {
            return false;
        }

        clearCursor(node);
        final boolean result = setCursor(node);
        node.recycle();
        return result;
    }

    @Override
    public boolean next(boolean shouldWrap, boolean shouldScroll,
                        boolean useInputFocusAsPivotIfEmpty) {
        return navigateWithGranularity(NAVIGATION_DIRECTION_NEXT, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty);
    }

    @Override
    public boolean previous(boolean shouldWrap, boolean shouldScroll,
                            boolean useInputFocusAsPivotIfEmpty) {
        return navigateWithGranularity(NAVIGATION_DIRECTION_PREVIOUS, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty);
    }

    @Override
    public boolean jumpToTop() {
        clearCursor();
        mReachedEdge = true;
        return next(true /*shouldWrap*/, false /*shouldScroll*/,
                false /*useInputFocusAsPivotIfEmpty*/);
    }

    @Override
    public boolean jumpToBottom() {
        clearCursor();
        mReachedEdge = true;
        return previous(true /*shouldWrap*/, false /*shouldScroll*/,
                false /*useInputFocusAsPivotIfEmpty*/);
    }

    @Override
    public boolean more() {
        return attemptScrollToDirection(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
    }

    @Override
    public boolean less() {
        return attemptScrollToDirection(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
    }

    private boolean attemptScrollToDirection(int direction) {
        AccessibilityNodeInfoCompat cursor = null;
        AccessibilityNodeInfoCompat rootNode = null;
        AccessibilityNodeInfoCompat bfsScrollableNode = null;
        boolean result = false;
        try {
            cursor = getCursor();
            if (cursor != null) {
                result = attemptScrollAction(cursor, direction);
            }

            if (!result) {
                rootNode = AccessibilityServiceCompatUtils
                        .getRootInAccessibilityFocusedWindow(mService);

                bfsScrollableNode = AccessibilityNodeInfoUtils.searchFromBfs(
                        rootNode, AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);

                if (bfsScrollableNode != null && isLogicalScrollableWidget(bfsScrollableNode)) {
                    result = attemptScrollAction(bfsScrollableNode, direction);
                }
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(cursor, rootNode, bfsScrollableNode);
        }

        return result;
    }

    @Override
    public boolean clickCurrent() {
        return performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
    }

    @Override
    public boolean nextGranularity() {
        return adjustGranularity(1);
    }

    @Override
    public boolean previousGranularity() {
        return adjustGranularity(-1);
    }

    @Override
    public boolean setGranularity(CursorGranularity granularity, boolean fromUser) {
        AccessibilityNodeInfoCompat current = null;

        try {
            current = getCursor();
            return setGranularity(granularity, current, fromUser);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current);
        }
    }

    @Override
    public boolean setGranularity(CursorGranularity granularity, AccessibilityNodeInfoCompat node,
                                  boolean fromUser) {
        if (node == null) {
            return false;
        }

        if (!mGranularityManager.setGranularityAt(node, granularity)) {
            return false;
        }

        granularityUpdated(granularity, fromUser);
        return true;
    }

    @Override
    public boolean setCursor(AccessibilityNodeInfoCompat node) {
        return PerformActionUtils.performAction(node,
                AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
    }

    @Override
    public void setSelectionModeActive(AccessibilityNodeInfoCompat node, boolean active) {
        if (active && !mGranularityManager.isLockedTo(node)) {
            setGranularity(CursorGranularity.CHARACTER, false /* fromUser */);
        }

        mGranularityManager.setSelectionModeActive(active);
    }

    @Override
    public boolean isSelectionModeActive() {
        return mGranularityManager.isSelectionModeActive();
    }

    @Override
    public void clearCursor() {
        AccessibilityNodeInfoCompat currentNode = getCursor();
        if (currentNode == null) {
            return;
        }

        clearCursor(currentNode);
        currentNode.recycle();
    }

    @Override
    public void clearCursor(AccessibilityNodeInfoCompat currentNode) {
        if (currentNode == null) {
            return;
        }
        PerformActionUtils.performAction(currentNode,
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
    }

    @Override
    public AccessibilityNodeInfoCompat getCursor() {
        return getAccessibilityFocusedOrRootNode();
    }

    private AccessibilityNodeInfoCompat getAccessibilityFocusedOrRootNode() {
        final AccessibilityNodeInfoCompat compatRoot = AccessibilityServiceCompatUtils
                .getRootInAccessibilityFocusedWindow(mService);

        if (compatRoot == null) {
            return null;
        }

        AccessibilityNodeInfoCompat focusedNode = getAccessibilityFocusedNode(compatRoot);

        // TODO(KM): If there's no focused node, we should either mimic following
        // focus from new window or try to be smart for things like list views.
        if (focusedNode == null) {
            return compatRoot;
        }

        return focusedNode;
    }

    public AccessibilityNodeInfoCompat getAccessibilityFocusedOrInputFocusedEditableNode() {
        final AccessibilityNodeInfoCompat compatRoot = AccessibilityServiceCompatUtils
                .getRootInAccessibilityFocusedWindow(mService);

        if (compatRoot == null) {
            return null;
        }

        AccessibilityNodeInfoCompat focusedNode = getAccessibilityFocusedNode(compatRoot);

        // TODO(KM): If there's no focused node, we should either mimic following
        // focus from new window or try to be smart for things like list views.
        if (focusedNode == null) {
            AccessibilityNodeInfoCompat inputFocusedNode = getInputFocusedNode();
            if (inputFocusedNode != null && inputFocusedNode.isEditable()) {
                focusedNode = inputFocusedNode;
            }
        }

        return focusedNode;
    }

    public AccessibilityNodeInfoCompat getAccessibilityFocusedNode(
            AccessibilityNodeInfoCompat compatRoot) {
        if (compatRoot == null) {
            return null;
        }

        AccessibilityNodeInfoCompat focusedNode = compatRoot.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);

        if (focusedNode == null) {
            return null;
        }

        if (!AccessibilityNodeInfoUtils.isVisible(focusedNode)) {
            focusedNode.recycle();
            return null;
        }

        return focusedNode;
    }

    private AccessibilityNodeInfoCompat getInputFocusedNode() {
        AccessibilityNodeInfoCompat activeRoot =
                AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
        if (activeRoot != null) {
            try {
                return activeRoot.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
            } finally {
                activeRoot.recycle();
            }
        }

        return null;
    }

    public boolean isLinearNavigationLocked(AccessibilityNodeInfoCompat node) {
        return mGranularityManager.isLockedTo(node);
    }

    @Override
    public CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node) {
        if (mGranularityManager.isLockedTo(node)) {
            return mGranularityManager.getCurrentGranularity();
        }

        return CursorGranularity.DEFAULT;
    }

    /**
     * Attempts to scroll using the specified action.
     *
     * @param action The scroll action to perform.
     * @return Whether the action was performed.
     */
    private boolean attemptScrollAction(AccessibilityNodeInfoCompat cursor, int action) {
        if (cursor == null) {
            return false;
        }

        AccessibilityNodeInfoCompat scrollableNode = null;
        try {
            scrollableNode = getBestScrollableNode(cursor, action);
            if (scrollableNode == null) {
                return false;
            }

            final boolean performedAction = PerformActionUtils.performAction(
                    scrollableNode, action);
            if (performedAction) {
                final Set<ScrollListener> listeners = new HashSet<>(mScrollListeners);
                for (ScrollListener listener : listeners) {
                    listener.onScroll(scrollableNode, action);
                }
            }

            return performedAction;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(scrollableNode);
        }
    }

    private AccessibilityNodeInfoCompat getBestScrollableNode(
            AccessibilityNodeInfoCompat cursor, final int action) {
        final AccessibilityNodeInfoCompat predecessor =
                AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(cursor,
                        AccessibilityNodeInfoUtils.FILTER_SCROLLABLE.and(new NodeFilter() {
                            @Override
                            public boolean accept(AccessibilityNodeInfoCompat node) {
                                return node != null &&
                                        AccessibilityNodeInfoUtils.supportsAnyAction(node, action);
                            }
                        }));

        if (predecessor != null && isLogicalScrollableWidget(predecessor)) {
            return predecessor;
        }

        return null;
    }

    // TODO that is hack to temporary not to scroll DatePicker and Number picker while they are
    // unusable on that case
    private boolean isLogicalScrollableWidget(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        CharSequence className = node.getClassName();
        return !(TextUtils.equals(className, DatePicker.class.getName()) ||
                TextUtils.equals(className, NumberPicker.class.getName()));
    }

    /**
     * Attempts to adjust granularity in the direction indicated.
     *
     * @param direction The direction to adjust granularity. One of
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
     * @return true on success, false if no nodes in the current hierarchy
     *         support a granularity other than the default.
     */
    private boolean adjustGranularity(int direction) {
        AccessibilityNodeInfoCompat currentNode = null;

        try {
            currentNode = getCursor();
            if (currentNode == null) {
                return false;
            }

            final boolean wasAdjusted = mGranularityManager.adjustGranularityAt(
                    currentNode, direction);
            if (wasAdjusted) {
                granularityUpdated(mGranularityManager.getCurrentGranularity(), true);
            }

            return wasAdjusted;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        }
    }

    /**
     * Attempts to move in the direction indicated.
     * <p>
     * If a navigation granularity other than DEFAULT has been applied, attempts
     * to move within the current object at the specified granularity.
     * </p>
     * <p>
     * If no granularity has been applied, or if the DEFAULT granularity has
     * been applied, attempts to move in the specified direction using
     * {@link android.view.View#focusSearch(int)}.
     * </p>
     *
     * @param direction The direction to move.
     * @param shouldWrap Whether navigating past the last item on the screen
     *            should wrap around to the first item on the screen.
     * @param shouldScroll Whether navigating past the last visible item in a
     *            scrollable container should automatically scroll to the next
     *            visible item.
     * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
     *                                    focused editable node if there is no node with
     *                                    accessibility focus
     * @return true on success, false on failure.
     */
    private boolean navigateWithGranularity(int direction,
                                            boolean shouldWrap,
                                            boolean shouldScroll,
                                            boolean useInputFocusAsPivotIfEmpty) {
        final int navigationAction;
        final int scrollDirection;
        final int focusSearchDirection;
        final int edgeDirection;

        // Map the navigation action to various directions.
        if (direction == NAVIGATION_DIRECTION_NEXT) {
            navigationAction = AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
            scrollDirection = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
            focusSearchDirection = TraversalStrategy.SEARCH_FOCUS_FORWARD;
            edgeDirection = 1;
        } else {
            navigationAction = AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
            scrollDirection = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
            focusSearchDirection = TraversalStrategy.SEARCH_FOCUS_BACKWARD;
            edgeDirection = -1;
        }

        AccessibilityNodeInfoCompat current = null;
        AccessibilityNodeInfoCompat target = null;
        TraversalStrategy traversalStrategy = null;
        AccessibilityNodeInfoCompat rootNode = null;
        boolean processResult = false;

        try {
            current = getCurrentCursor(useInputFocusAsPivotIfEmpty);
            if (current == null) {
                processResult = false;
                return processResult;
            }

            // If granularity is set to anything other than default, restrict
            // navigation to the current node.
            if (mGranularityManager.isLockedTo(current)) {
                final int result = mGranularityManager.navigate(navigationAction);
                if (result == CursorGranularityManager.SUCCESS) {
                    mGranularityNavigationReachedEdge = false;
                    processResult = true;
                    return processResult;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 &&
                        result == CursorGranularityManager.HIT_EDGE && !current.isEditable()) {
                    if (!mGranularityNavigationReachedEdge) {
                        // skip one swipe when hit edge during granularity navigation
                        mGranularityNavigationReachedEdge = true;
                        processResult = false;
                        return processResult;
                    } else {
                        mSwitchNodeWithGranularityDirection = focusSearchDirection;
                        EventState.getInstance().addEvent(
                                EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
                    }
                } else {
                    processResult = false;
                    return processResult;
                }
            }

            // If the current node has web content, attempt HTML navigation.
            if (WebInterfaceUtils.supportsWebActions(current)
                    && attemptHtmlNavigation(current, direction)) {
                return true;
            }

            // If the user has disabled automatic scrolling, don't attempt to scroll.
            // TODO(CB): Remove once auto-scroll is settled.
            if (shouldScroll) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                        mService);
                shouldScroll = SharedPreferencesUtils.getBooleanPref(prefs,
                        mService.getResources(), R.string.pref_auto_scroll_key,
                        R.bool.pref_auto_scroll_default);
            }

            rootNode = AccessibilityNodeInfoUtils.getRoot(current);
            traversalStrategy = new OrderedTraversalStrategy(rootNode);

            // If the current item is at the edge of a scrollable view, try to
            // automatically scroll the view in the direction of navigation.
            if (shouldScroll && AccessibilityNodeInfoUtils.isAutoScrollEdgeListItem(
                    current, edgeDirection, traversalStrategy) &&
                    attemptScrollAction(current, scrollDirection)) {
                processResult = true;
                return processResult;
            }

            // Otherwise, move focus to next or previous focusable node.
            target = navigateFrom(current, focusSearchDirection, traversalStrategy);
            if ((target != null)) {
                //TODO change to Build.VERSION_CODE_CONSTANT (b/23384092)
                if (Build.VERSION.SDK_INT >= 23 && shouldScroll &&
                        AccessibilityNodeInfoUtils.isAutoScrollEdgeListItem(
                                target, edgeDirection, traversalStrategy)) {
                    //comment for lint
                    //noinspection Annotator
                    PerformActionUtils.performAction(target,
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                }

                if (setCursor(target)) {
                    mReachedEdge = false;
                    processResult = true;
                    return processResult;
                }
            }

            // skip one swipe if in the border of window and no other application window to
            // move focus to
            if (!mReachedEdge && needPauseInTraversalAfterCurrentWindow(focusSearchDirection)) {
                mReachedEdge = true;
                processResult = false;
                return processResult;
            }

            // move focus from application to next application window
            if (navigateToNextApplicationWindow(focusSearchDirection)) {
                mReachedEdge = false;
                processResult = true;
                return processResult;
            }

            if (mReachedEdge && shouldWrap) {
                mReachedEdge = false;
                processResult =  navigateWrapAround(rootNode, focusSearchDirection,
                        traversalStrategy);
                return processResult;
            }

            processResult = false;
            return processResult;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current, target, rootNode);
            if (traversalStrategy != null) {
                traversalStrategy.recycle();
            }

            if (!processResult) {
                mSwitchNodeWithGranularityDirection = 0;
                EventState.getInstance().clearEvent(
                        EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
            }
        }
    }

    private AccessibilityNodeInfoCompat getCurrentCursor(boolean useInputFocusAsPivotIfEmpty) {
        AccessibilityNodeInfoCompat cursor = null;
        if (useInputFocusAsPivotIfEmpty) {
            cursor = getAccessibilityFocusedOrInputFocusedEditableNode();
        }

        if (cursor == null) {
            cursor = getAccessibilityFocusedOrRootNode();
        }

        return cursor;
    }

    @SuppressLint("InlinedApi")
    private boolean needPauseInTraversalAfterCurrentWindow(int direction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            // always pause before loop in one-window conditions
            return true;
        }

        WindowManager windowsManager = new WindowManager();
        windowsManager.setWindows(mService.getWindows());

        if (!windowsManager.isApplicationWindowFocused()) {
            // need pause before looping traversal in non-application window
            return true;
        }

        if (direction == NodeFocusFinder.SEARCH_FORWARD) {
            return windowsManager.isLastWindow(windowsManager.getCurrentWindow(),
                    AccessibilityWindowInfo.TYPE_APPLICATION);
        } else {
            return windowsManager.isFirstWindow(windowsManager.getCurrentWindow(),
                    AccessibilityWindowInfo.TYPE_APPLICATION);
        }
    }

    private boolean navigateToNextApplicationWindow(int direction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager windowsManager = new WindowManager();
            windowsManager.setWindows(mService.getWindows());
            if (!windowsManager.isApplicationWindowFocused()) {
                return false;
            }

            AccessibilityWindowInfo currentWindow = windowsManager.getCurrentWindow();
            if (currentWindow == null) {
                return false;
            }

            AccessibilityWindowInfo targetWindow = null;
            AccessibilityWindowInfo pivotWindow = currentWindow;
            while (!currentWindow.equals(targetWindow)) {
                switch (direction) {
                    case NodeFocusFinder.SEARCH_FORWARD:
                        targetWindow = windowsManager.getNextWindow(pivotWindow);
                        break;
                    case NodeFocusFinder.SEARCH_BACKWARD:
                        targetWindow = windowsManager.getPreviousWindow(pivotWindow);
                        break;
                }

                pivotWindow = targetWindow;

                if (targetWindow == null) {
                    return false;
                }

                if (targetWindow.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }

                AccessibilityNodeInfo windowRoot = targetWindow.getRoot();
                if (windowRoot == null) {
                    continue;
                }

                AccessibilityNodeInfoCompat compatRoot = new AccessibilityNodeInfoCompat(windowRoot);
                TraversalStrategy traversalStrategy = new OrderedTraversalStrategy(compatRoot);
                if (navigateWrapAround(compatRoot, direction, traversalStrategy)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean navigateWrapAround(AccessibilityNodeInfoCompat root, int direction,
                                       TraversalStrategy traversalStrategy) {
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfoCompat tempNode = null;
        AccessibilityNodeInfoCompat wrapNode = null;

        try {
            switch (direction) {
                case OrderedTraversalStrategy.SEARCH_FOCUS_FORWARD:
                    tempNode = traversalStrategy.focusFirst(root);
                    wrapNode = navigateSelfOrFrom(tempNode, direction, traversalStrategy);
                    break;
                case OrderedTraversalStrategy.SEARCH_FOCUS_BACKWARD:
                    tempNode = traversalStrategy.focusLast(root);
                    wrapNode = navigateSelfOrFrom(tempNode, direction, traversalStrategy);
                    break;
            }

            if (wrapNode == null) {
                if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                    Log.e(LOGTAG, "Failed to wrap navigation");
                }
                return false;
            }

            return setCursor(wrapNode);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(tempNode, wrapNode);
        }
    }

    private AccessibilityNodeInfoCompat navigateSelfOrFrom(AccessibilityNodeInfoCompat node,
                                                           int direction, TraversalStrategy traversalStrategy) {
        if (node == null) {
            return null;
        }

        if (AccessibilityNodeInfoUtils.shouldFocusNode(node,
                traversalStrategy.getSpeakingNodesCache())) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return navigateFrom(node, direction, traversalStrategy);
    }

    private AccessibilityNodeInfoCompat navigateFrom(
            AccessibilityNodeInfoCompat node, int direction,
            final TraversalStrategy traversalStrategy) {
        if (node == null) {
            return null;
        }

        NodeFilter filter = new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return node != null && AccessibilityNodeInfoUtils.shouldFocusNode(node,
                        traversalStrategy.getSpeakingNodesCache());
            }
        };

        return AccessibilityNodeInfoUtils.searchFocus(traversalStrategy, node, direction, filter);
    }

    private void granularityUpdated(CursorGranularity granularity, boolean fromUser) {
        final Set<GranularityChangeListener> localListeners = new HashSet<>(mGranularityListeners);

        for (GranularityChangeListener listener : localListeners) {
            listener.onGranularityChanged(granularity);
        }

        if (fromUser) {
            mService.getSpeechController().speak(mService.getString(granularity.resourceId),
                    SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
        }
    }

    /**
     * Performs the specified action on the current cursor.
     *
     * @param action The action to perform on the current cursor.
     * @return {@code true} if successful.
     */
    private boolean performAction(int action) {
        AccessibilityNodeInfoCompat current = null;

        try {
            current = getCursor();
            return current != null &&
                    PerformActionUtils.performAction(current, action);

        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current);
        }
    }

    @Override
    public boolean onComboPerformed(int id) {
        switch (id) {
            case KeyComboManager.ACTION_NAVIGATE_NEXT:
                next(true, true, true);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS:
                previous(true, true, true);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_FIRST:
                jumpToTop();
                return true;
            case KeyComboManager.ACTION_NAVIGATE_LAST:
                jumpToBottom();
                return true;
            case KeyComboManager.ACTION_PERFORM_CLICK:
                clickCurrent();
                return true;
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            final AccessibilityNodeInfo node = event.getSource();
            if (node == null) {
                if (LogUtils.LOG_LEVEL <= Log.WARN) {
                    Log.w(LOGTAG, "TYPE_VIEW_ACCESSIBILITY_FOCUSED event without a source.");
                }
                return;
            }

            // When a new view gets focus, clear the state of the granularity
            // manager if this event came from a different node than the locked
            // node but from the same window.
            final AccessibilityNodeInfoCompat nodeCompat = new AccessibilityNodeInfoCompat(node);
            mGranularityManager.onNodeFocused(nodeCompat);
            if (mSwitchNodeWithGranularityDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
                mGranularityManager.navigate(
                        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            } else if (mSwitchNodeWithGranularityDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
                mGranularityManager.startFromLastNode();
                mGranularityManager.navigate(
                        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
            }
            mSwitchNodeWithGranularityDirection = 0;
            nodeCompat.recycle();
            mReachedEdge = false;
            mGranularityNavigationReachedEdge = false;
        }
    }

    /**
     * Attempts to navigate the node using HTML navigation.
     *
     * @param node to navigate on
     * @param direction The direction to navigate, one of:
     *            <ul>
     *            <li>{@link #NAVIGATION_DIRECTION_NEXT}</li>
     *            <li>{@link #NAVIGATION_DIRECTION_PREVIOUS}</li>
     *            </ul>
     * @return {@code true} if navigation succeeded.
     */
    private static boolean attemptHtmlNavigation(AccessibilityNodeInfoCompat node, int direction) {
        final int action = (direction == NAVIGATION_DIRECTION_NEXT)
                ? AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT
                : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT;
        return PerformActionUtils.performAction(node, action);
    }
}
