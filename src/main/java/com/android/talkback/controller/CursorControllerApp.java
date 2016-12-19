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
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.DatePicker;
import android.widget.NumberPicker;

import com.android.talkback.CursorGranularity;
import com.android.talkback.CursorGranularityManager;
import com.android.talkback.FeedbackItem;
import com.android.talkback.InputModeManager;
import com.android.talkback.KeyComboManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.eventprocessor.EventState;
import com.android.utils.Role;
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
import com.android.utils.traversal.TraversalStrategy;
import com.android.utils.traversal.TraversalStrategyUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handles screen reader cursor management.
 */
public class CursorControllerApp implements
        CursorController, AccessibilityEventListener, KeyComboManager.KeyComboListener {

    private static final String LOGTAG = "CursorControllerApp";

    private static final String HTML_ELEMENT_HEADING = "HEADING";
    private static final String HTML_ELEMENT_BUTTON = "BUTTON";
    private static final String HTML_ELEMENT_CHECKBOX = "CHECKBOX";
    private static final String HTML_ELEMENT_ARIA_LANDMARK = "LANDMARK";
    private static final String HTML_ELEMENT_EDIT_FIELD = "TEXT_FIELD";
    private static final String HTML_ELEMENT_FOCUSABLE_ITEM = "FOCUSABLE";
    private static final String HTML_ELEMENT_HEADING_1 = "H1";
    private static final String HTML_ELEMENT_HEADING_2 = "H2";
    private static final String HTML_ELEMENT_HEADING_3 = "H3";
    private static final String HTML_ELEMENT_HEADING_4 = "H4";
    private static final String HTML_ELEMENT_HEADING_5 = "H5";
    private static final String HTML_ELEMENT_HEADING_6 = "H6";
    private static final String HTML_ELEMENT_LINK = "LINK";
    private static final String HTML_ELEMENT_CONTROL = "CONTROL";
    private static final String HTML_ELEMENT_GRAPHIC = "GRAPHIC";
    private static final String HTML_ELEMENT_LIST_ITEM = "LIST_ITEM";
    private static final String HTML_ELEMENT_LIST = "LIST";
    private static final String HTML_ELEMENT_TABLE = "TABLE";
    private static final String HTML_ELEMENT_COMBOBOX = "COMBOBOX";
    private static final String HTML_ELEMENT_SECTION = "SECTION";

    private static final int WINDOW_TYPE_SYSTEM = 1;
    private static final int WINDOW_TYPE_APPLICATION = 1 << 1;
    private static final int WINDOW_TYPE_SPLIT_SCREEN_DIVIDER = 1 << 2;

    private static final int FOCUS_STRATEGY_WRAP_AROUND = 0;
    private static final int FOCUS_STRATEGY_RESUME_FOCUS = 1;

    /** The host service. Used to access the root node. */
    private final TalkBackService mService;

    /** Handles traversal using granularity. */
    private final CursorGranularityManager mGranularityManager;

    /** Whether we should drive input focus instead of accessibility focus where possible. */
    private final boolean mControlInputFocus;

    /** Whether the current device supports navigating between multiple windows. */
    private final boolean mIsWindowNavigationAvailable;

    /** Whether the user hit an edge with the last swipe. */
    private boolean mReachedEdge;
    private boolean mGranularityNavigationReachedEdge;

    private final Map<Integer, AccessibilityNodeInfoCompat> mLastFocusedNodeMap = new HashMap<>();

    private final Set<GranularityChangeListener> mGranularityListeners = new HashSet<>();

    private final Set<ScrollListener> mScrollListeners = new HashSet<>();

    private final Set<CursorListener> mCursorListeners = new HashSet<>();

    /** The last input-focused editable node. */
    private AccessibilityNodeInfoCompat mLastEditable;

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

        mControlInputFocus = service.isDeviceTelevision();
        mIsWindowNavigationAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && !service.isDeviceTelevision();
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
    public void addCursorListener(CursorListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }

        mCursorListeners.add(listener);
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

        EventState.getInstance().addEvent(EventState.EVENT_NODE_REFOCUSED);
        clearCursor(node);
        final boolean result = setCursor(node);
        node.recycle();
        return result;
    }

    @Override
    public boolean next(boolean shouldWrap, boolean shouldScroll,
                        boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithGranularity(TraversalStrategy.SEARCH_FOCUS_FORWARD, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean previous(boolean shouldWrap, boolean shouldScroll,
                            boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithGranularity(TraversalStrategy.SEARCH_FOCUS_BACKWARD, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean left(boolean shouldWrap, boolean shouldScroll,
            boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithGranularity(TraversalStrategy.SEARCH_FOCUS_LEFT, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean right(boolean shouldWrap, boolean shouldScroll,
            boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithGranularity(TraversalStrategy.SEARCH_FOCUS_RIGHT, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean up(boolean shouldWrap, boolean shouldScroll,
            boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithGranularity(TraversalStrategy.SEARCH_FOCUS_UP, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean down(boolean shouldWrap, boolean shouldScroll,
            boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithGranularity(TraversalStrategy.SEARCH_FOCUS_DOWN, shouldWrap,
                shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean jumpToTop(int inputMode) {
        clearCursor();
        mReachedEdge = true;
        return next(true /*shouldWrap*/, false /*shouldScroll*/,
                false /*useInputFocusAsPivotIfEmpty*/, inputMode);
    }

    @Override
    public boolean jumpToBottom(int inputMode) {
        clearCursor();
        mReachedEdge = true;
        return previous(true /*shouldWrap*/, false /*shouldScroll*/,
                false /*useInputFocusAsPivotIfEmpty*/, inputMode);
    }

    @Override
    public boolean more() {
        return attemptScrollToDirection(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
    }

    @Override
    public boolean less() {
        return attemptScrollToDirection(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean nextWithSpecifiedGranularity(CursorGranularity granularity, boolean shouldWrap,
            boolean shouldScroll, boolean useInputFocusAsPivotIfEmpty, int inputMode) {
        return navigateWithSpecifiedGranularity(TraversalStrategy.SEARCH_FOCUS_FORWARD,
                granularity, shouldWrap, shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean previousWithSpecifiedGranularity(CursorGranularity granularity,
            boolean shouldWrap, boolean shouldScroll, boolean useInputFocusAsPivotIfEmpty,
            int inputMode) {
        return navigateWithSpecifiedGranularity(TraversalStrategy.SEARCH_FOCUS_BACKWARD,
                granularity, shouldWrap, shouldScroll, useInputFocusAsPivotIfEmpty, inputMode);
    }

    @Override
    public boolean nextHtmlElement(String htmlElement, int inputMode) {
        return navigateToHTMLElement(htmlElement, true /* forward */, inputMode);
    }

    @Override
    public boolean previousHtmlElement(String htmlElement, int inputMode) {
        return navigateToHTMLElement(htmlElement, false /* backward */, inputMode);
    }

    private boolean isSupportedHtmlElement(String htmlElement) {
        AccessibilityNodeInfoCompat node = getCursor();
        if (node == null) {
            return false;
        }

        String[] supportedHtmlElements = WebInterfaceUtils.getSupportedHtmlElements(node);
        AccessibilityNodeInfoUtils.recycleNodes(node);
        return supportedHtmlElements != null &&
                Arrays.asList(supportedHtmlElements).contains(htmlElement);
    }

    private boolean navigateToHTMLElement(String htmlElement, boolean forward, int inputMode) {
        AccessibilityNodeInfoCompat node = getCursor();
        if (node == null) {
            return false;
        }

        try {
            int direction = forward ? WebInterfaceUtils.DIRECTION_FORWARD :
                    WebInterfaceUtils.DIRECTION_BACKWARD;

            if (WebInterfaceUtils.performNavigationToHtmlElementAction(
                    node, direction, htmlElement)) {
                mService.getInputModeManager().setInputMode(inputMode);
                return true;
            } else {
                return false;
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(node);
        }
    }

    private boolean attemptScrollToDirection(int direction) {
        AccessibilityNodeInfoCompat cursor = null;
        AccessibilityNodeInfoCompat rootNode = null;
        AccessibilityNodeInfoCompat bfsScrollableNode = null;
        boolean result = false;
        try {
            cursor = getCursor();
            if (cursor != null) {
                result = attemptScrollAction(cursor, direction, false);
            }

            if (!result) {
                rootNode = AccessibilityServiceCompatUtils
                        .getRootInAccessibilityFocusedWindow(mService);

                bfsScrollableNode = AccessibilityNodeInfoUtils.searchFromBfs(
                        rootNode, AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);

                if (bfsScrollableNode != null && isLogicalScrollableWidget(bfsScrollableNode)) {
                    result = attemptScrollAction(bfsScrollableNode, direction, false);
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
    public boolean clickCurrentHierarchical() {
        NodeFilter clickFilter = new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return AccessibilityNodeInfoUtils.isClickable(node);
            }
        };

        AccessibilityNodeInfoCompat cursor = null;
        AccessibilityNodeInfoCompat match = null;
        try {
            cursor = getCursor();
            match = AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(cursor, clickFilter);
            return PerformActionUtils.performAction(match,
                    AccessibilityNodeInfoCompat.ACTION_CLICK);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(cursor, match);
        }
    }

    @Override
    public boolean longClickCurrent() {
        return performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
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
            current = getCursorOrInputCursor();
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
        // Accessibility focus follows input focus; on TVs we want to set both simultaneously,
        // so we change the input focus if possible and let the ProcessorFocusAndSingleTap
        // handle changing the accessibility focus.
        if (mControlInputFocus && node.isFocusable() && !node.isFocused()) {
            if (setCursor(node, AccessibilityNodeInfoCompat.ACTION_FOCUS)) {
                return true;
            }
        }

        // Set accessibility focus otherwise (or as a fallback if setting input focus failed).
        return setCursor(node, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
    }

    private boolean setCursor(AccessibilityNodeInfoCompat node, int action) {
        final Set<CursorListener> listeners = new HashSet<>(mCursorListeners);
        for (CursorListener listener : listeners) {
            listener.beforeSetCursor(node, action);
        }

        boolean performedAction = PerformActionUtils.performAction(node, action);
        if (performedAction) {
            rememberLastFocusedNode(node);

            for (CursorListener listener : listeners) {
                listener.onSetCursor(node, action);
            }
        }

        return performedAction;
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

    @Override
    public AccessibilityNodeInfoCompat getCursorOrInputCursor() {
        return getAccessibilityFocusedOrInputFocusedEditableNode();
    }

    private AccessibilityNodeInfoCompat getAccessibilityFocusedOrRootNode() {
        final AccessibilityNodeInfoCompat compatRoot = AccessibilityServiceCompatUtils
                .getRootInAccessibilityFocusedWindow(mService);

        if (compatRoot == null) {
            return null;
        }

        AccessibilityNodeInfoCompat focusedNode = getAccessibilityFocusedNode(compatRoot);

        // TODO: If there's no focused node, we should either mimic following
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

        // TODO: If there's no focused node, we should either mimic following
        // focus from new window or try to be smart for things like list views.
        if (focusedNode == null) {
            AccessibilityNodeInfoCompat inputFocusedNode = getInputFocusedNode();
            if (inputFocusedNode != null && inputFocusedNode.isFocused()
                    && inputFocusedNode.isEditable() ) {
                focusedNode = inputFocusedNode;
            }
        }

        // If we can't find the focused node but the keyboard is showing, return the last editable.
        // This will occur if the input-focused view is actually a virtual view (e.g. in WebViews).
        // Note: need to refresh() in order to verify that the node is still available on-screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && focusedNode == null &&
                mLastEditable != null && mLastEditable.refresh()) {
            WindowManager windowManager = new WindowManager(false); // RTL state doesn't matter.
            windowManager.setWindows(mService.getWindows());
            if (windowManager.isInputWindowOnScreen()) {
                focusedNode = AccessibilityNodeInfoCompat.obtain(mLastEditable);
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
     * @param auto If {@code true}, then the scroll was initiated automatically. If
     *     {@code false}, then the user initiated the scroll action.
     * @return Whether the action was performed.
     */
    private boolean attemptScrollAction(AccessibilityNodeInfoCompat cursor, int action,
            boolean auto) {
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
                    listener.onScroll(scrollableNode, action, auto);
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
                                        AccessibilityNodeInfoUtils.supportsAction(node, action);
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
            currentNode = getCursorOrInputCursor();
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
     * Try to navigate with specified granularity.
     */
    private boolean navigateWithSpecifiedGranularity(int direction, CursorGranularity granularity,
            boolean shouldWrap, boolean shouldScroll, boolean useInputFocusAsPivotIfEmpty,
            int inputMode) {
        // Keep current granularity to set it back after this operation.
        CursorGranularity currentGranularity = mGranularityManager.getCurrentGranularity();
        boolean sameGranularity = currentGranularity == granularity;

        // Navigate with specified granularity.
        if (!sameGranularity) {
            setGranularity(granularity, false /* not from user */);
        }
        boolean result = navigateWithGranularity(direction, false, true, true, inputMode);

        // Set back to the granularity which is used before this operation.
        if (!sameGranularity) {
            setGranularity(currentGranularity, false /* not from user */);
        }

        return result;
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
    private boolean navigateWithGranularity(@TraversalStrategy.SearchDirection int direction,
                                            boolean shouldWrap,
                                            boolean shouldScroll,
                                            boolean useInputFocusAsPivotIfEmpty,
                                            int inputMode) {
        @TraversalStrategy.SearchDirection int logicalDirection =
                TraversalStrategyUtils.getLogicalDirection(direction, mService.isScreenLayoutRTL());

        final int navigationAction;
        if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
            navigationAction = AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
        } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
            navigationAction = AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
        } else {
            throw new IllegalStateException("Unknown logical direction");
        }

        mService.getInputModeManager().setInputMode(inputMode);

        final int scrollDirection =
                TraversalStrategyUtils.convertSearchDirectionToScrollAction(direction);
        if (scrollDirection == 0) {
            // We won't be able to handle scrollable views very well on older SDK versions,
            // so don't allow d-pad navigation,
            return false;
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

            if (!mIsWindowNavigationAvailable) {
                // If we're in a background window, we need to return the cursor to the current
                // window and prevent navigation within the background window.
                AccessibilityWindowInfoCompat currentWindow = current.getWindow();
                if (currentWindow != null) {
                    if (!currentWindow.isActive()) {
                        AccessibilityNodeInfoCompat activeRoot =
                                AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
                        if (activeRoot != null) {
                            current.recycle();
                            current = activeRoot;
                        }
                    }
                    currentWindow.recycle();
                }
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
                        // alert when we navigate to the edge with a web granularity.
                        if (mGranularityManager.getCurrentGranularity().isWebGranularity()) {
                            int resId = mGranularityManager.getCurrentGranularity().resourceId;
                            String htmlElement = null;
                            if (resId == CursorGranularity.WEB_CONTROL.resourceId) {
                                htmlElement = HTML_ELEMENT_CONTROL;
                            } else if (resId == CursorGranularity.WEB_LINK.resourceId) {
                                htmlElement = HTML_ELEMENT_LINK;
                            } else if (resId == CursorGranularity.WEB_LIST.resourceId) {
                                htmlElement = HTML_ELEMENT_LIST;
                            } else if (resId == CursorGranularity.WEB_SECTION.resourceId) {
                                htmlElement = HTML_ELEMENT_SECTION;
                            }
                            alertWebNavigationHitEdge(htmlElement,
                                    logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD);
                        }
                        // skip one swipe when hit edge during granularity navigation
                        mGranularityNavigationReachedEdge = true;
                        processResult = false;
                        return processResult;
                    } else {
                        // We shouldn't navigate past the last "link", "heading", etc. when
                        // navigating with a web granularity.
                        // It makes sense to navigate to the next node with other kinds of
                        // granularities(characters, words, etc.).
                        if (mGranularityManager.getCurrentGranularity().isWebGranularity()) {
                            processResult = false;
                            return processResult;
                        }
                        mSwitchNodeWithGranularityDirection = navigationAction;
                        EventState.getInstance().addEvent(
                                EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
                        EventState.getInstance().addEvent(
                                EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE);
                    }
                } else {
                    processResult = false;
                    return processResult;
                }
            }

            // If the current node has web content, attempt HTML navigation.
            if (shouldAttemptHtmlNavigation(current, direction)) {
                if (attemptHtmlNavigation(current, direction)) {
                    // Succeeded finding destination inside WebView
                    processResult = true;
                    return true;
                } else {
                    // Ascend to WebView, preparing to navigate past WebView with normal navigation
                    AccessibilityNodeInfoCompat webView = ascendToWebView(current);
                    if (webView != null) {
                        current.recycle();
                        current = webView;
                    }
                }
            }

            // If the user has disabled automatic scrolling, don't attempt to scroll.
            // TODO: Remove once auto-scroll is settled.
            if (shouldScroll) {
                final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(
                        mService);
                shouldScroll = SharedPreferencesUtils.getBooleanPref(prefs,
                        mService.getResources(), R.string.pref_auto_scroll_key,
                        R.bool.pref_auto_scroll_default);
            }

            rootNode = AccessibilityNodeInfoUtils.getRoot(current);
            traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(rootNode, direction);

            // If the current item is at the edge of a scrollable view, try to
            // automatically scroll the view in the direction of navigation.
            if (shouldScroll && AccessibilityNodeInfoUtils.isAutoScrollEdgeListItem(
                    current, direction, traversalStrategy) &&
                    attemptScrollAction(current, scrollDirection, true)) {
                processResult = true;
                return processResult;
            }

            // Otherwise, move focus to next or previous focusable node.
            target = navigateFrom(current, direction, traversalStrategy);
            if ((target != null)) {
                // The `spatial` condition provides a work-around for RecyclerViews.
                // Currently RecyclerViews do not support ACTION_SCROLL_LEFT, UP, etc.
                // TODO: Remove `spatial` check when RecyclerViews support new scroll actions.
                final boolean spatial = TraversalStrategyUtils.isSpatialDirection(direction);
                boolean autoScroll = AccessibilityNodeInfoUtils.isAutoScrollEdgeListItem(target,
                        direction, traversalStrategy) || spatial;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldScroll && autoScroll) {
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
            if (!mReachedEdge && needPauseInTraversalAfterCurrentWindow(direction)) {
                mReachedEdge = true;
                processResult = false;
                return processResult;
            }

            // move focus from application to next application window
            if (navigateToNextOrPreviousWindow(direction,
                        WINDOW_TYPE_APPLICATION | WINDOW_TYPE_SPLIT_SCREEN_DIVIDER,
                        FOCUS_STRATEGY_WRAP_AROUND, false /* useInputFocusAsPivot */, inputMode)) {
                mReachedEdge = false;
                processResult = true;
                return processResult;
            }

            if (mReachedEdge && shouldWrap) {
                mReachedEdge = false;
                processResult = navigateWrapAround(rootNode, direction, traversalStrategy,
                        inputMode);
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
                EventState.getInstance().clearEvent(
                        EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE);
            }
        }
    }

    private AccessibilityNodeInfoCompat ascendToWebView(AccessibilityNodeInfoCompat current) {
        return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(current, null,
                new NodeFilter() {
                    @Override
                    public boolean accept(AccessibilityNodeInfoCompat node) {
                        return (node != null && Role.getRole(node) == Role.ROLE_WEB_VIEW);
                    }
                });
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
    private boolean needPauseInTraversalAfterCurrentWindow(
            @TraversalStrategy.SearchDirection int direction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            // always pause before loop in one-window conditions
            return true;
        }

        WindowManager windowManager = new WindowManager(mService.isScreenLayoutRTL());
        windowManager.setWindows(mService.getWindows());

        if (!windowManager.isApplicationWindowFocused() &&
                !windowManager.isSplitScreenDividerFocused()) {
            // need pause before looping traversal in non-application window
            return true;
        }

        @TraversalStrategy.SearchDirection int logicalDirection =
                TraversalStrategyUtils.getLogicalDirection(direction, mService.isScreenLayoutRTL());
        if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
            return windowManager.isLastWindow(
                    windowManager.getCurrentWindow(false /* useInputFocus */),
                    AccessibilityWindowInfo.TYPE_APPLICATION);
        } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
            return windowManager.isFirstWindow(
                    windowManager.getCurrentWindow(false /* useInputFocus */),
                    AccessibilityWindowInfo.TYPE_APPLICATION);
        } else {
            throw new IllegalStateException("Unknown logical direction");
        }
    }

    private boolean navigateToNextOrPreviousWindow(
            @TraversalStrategy.SearchDirection int direction,
            int windowTypeFilter, int focusStrategy, boolean useInputFocusAsPivot,
            int inputMode) {
        if (!mIsWindowNavigationAvailable) {
            return false;
        }

        WindowManager windowManager = new WindowManager(mService.isScreenLayoutRTL());
        windowManager.setWindows(mService.getWindows());

        AccessibilityWindowInfo pivotWindow = windowManager.getCurrentWindow(useInputFocusAsPivot);
        if (pivotWindow == null || !matchWindowType(pivotWindow, windowTypeFilter)) {
            return false;
        }

        AccessibilityWindowInfo targetWindow = pivotWindow;
        while (true) {
            @TraversalStrategy.SearchDirection int logicalDirection = TraversalStrategyUtils
                    .getLogicalDirection(direction, mService.isScreenLayoutRTL());
            if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
                targetWindow = windowManager.getNextWindow(targetWindow);
            } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
                targetWindow = windowManager.getPreviousWindow(targetWindow);
            } else {
                throw new IllegalStateException("Unknown logical direction");
            }

            if (targetWindow == null || pivotWindow.equals(targetWindow)) {
                return false;
            }

            if (!matchWindowType(targetWindow, windowTypeFilter)) {
                continue;
            }

            AccessibilityNodeInfo windowRoot = targetWindow.getRoot();
            if (windowRoot == null) {
                continue;
            }

            AccessibilityNodeInfoCompat compatRoot = new AccessibilityNodeInfoCompat(windowRoot);

            if (focusStrategy == FOCUS_STRATEGY_RESUME_FOCUS) {
                if (resumeLastFocus(targetWindow.getId(), inputMode)) {
                    return true;
                }

                // If it cannot resume last focus, try to focus the first focusable element.
                TraversalStrategy traversalStrategy =
                        TraversalStrategyUtils.getTraversalStrategy(compatRoot,
                                TraversalStrategy.SEARCH_FOCUS_FORWARD);
                if (navigateWrapAround(compatRoot, TraversalStrategy.SEARCH_FOCUS_FORWARD,
                            traversalStrategy, inputMode)) {
                    return true;
                }
            } else {
                TraversalStrategy traversalStrategy =
                        TraversalStrategyUtils.getTraversalStrategy(compatRoot, direction);
                if (navigateWrapAround(compatRoot, direction, traversalStrategy, inputMode)) {
                    return true;
                }
            }
        }
    }

    private boolean matchWindowType(AccessibilityWindowInfo window, int windowTypeFilter) {
        int windowType = window.getType();
        if ((windowTypeFilter & WINDOW_TYPE_SYSTEM) != 0 &&
                windowType == AccessibilityWindowInfo.TYPE_SYSTEM) {
            return true;
        } else if ((windowTypeFilter & WINDOW_TYPE_APPLICATION) != 0 &&
                windowType == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return true;
        } else if ((windowTypeFilter & WINDOW_TYPE_SPLIT_SCREEN_DIVIDER) != 0 &&
                windowType == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
            return true;
        } else {
            return false;
        }
    }

    private boolean navigateWrapAround(AccessibilityNodeInfoCompat root,
                                       @TraversalStrategy.SearchDirection int direction,
                                       TraversalStrategy traversalStrategy, int inputMode) {
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfoCompat tempNode = null;
        AccessibilityNodeInfoCompat wrapNode = null;

        try {
            tempNode = traversalStrategy.focusInitial(root, direction);
            wrapNode = navigateSelfOrFrom(tempNode, direction, traversalStrategy);

            if (wrapNode == null) {
                if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                    Log.e(LOGTAG, "Failed to wrap navigation");
                }
                return false;
            }

            if (setCursor(wrapNode)) {
                mService.getInputModeManager().setInputMode(inputMode);
                return true;
            } else {
                return false;
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(tempNode, wrapNode);
        }
    }

    private boolean resumeLastFocus(int windowId, int inputMode) {
        AccessibilityNodeInfoCompat lastFocusedNode = mLastFocusedNodeMap.get(windowId);
        if (lastFocusedNode == null) {
            return false;
        }

        if (setCursor(lastFocusedNode)) {
            mService.getInputModeManager().setInputMode(inputMode);
            return true;
        } else {
            return false;
        }
    }

    private AccessibilityNodeInfoCompat navigateSelfOrFrom(AccessibilityNodeInfoCompat node,
            @TraversalStrategy.SearchDirection int direction,
            TraversalStrategy traversalStrategy) {
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
            AccessibilityNodeInfoCompat node,
            @TraversalStrategy.SearchDirection int direction,
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
                nextWithSpecifiedGranularity(CursorGranularity.DEFAULT, true /* shouldWrap */,
                        true /* shouldScroll */, true /* useInputFocusAsPivotIfEmpty */,
                        InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS:
                previousWithSpecifiedGranularity(CursorGranularity.DEFAULT, true /* shouldWrap */,
                        true /* shouldScroll */, true /* useInputFocusAsPivotIfEmpty */,
                        InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_UP:
                up(true, true, true, InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_DOWN:
                down(true, true, true, InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_FIRST:
                jumpToTop(InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_LAST:
                jumpToBottom(InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_PERFORM_CLICK:
                clickCurrent();
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_WORD:
                nextWithSpecifiedGranularity(CursorGranularity.WORD, false /* shouldWrap */,
                        true /* shouldScroll */, true /* useInputFocusAsPivotIfEmpty */,
                        InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WORD:
                previousWithSpecifiedGranularity(CursorGranularity.WORD, false /* shouldWrap */,
                        true /* shouldScroll */, true /* useInputFocusAsPivotIfEmpty */,
                        InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_CHARACTER:
                nextWithSpecifiedGranularity(CursorGranularity.CHARACTER, false /* shouldWrap */,
                        true /* shouldScroll */, true /* useInputFocusAsPivotIfEmpty */,
                        InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHARACTER:
                previousWithSpecifiedGranularity(CursorGranularity.CHARACTER,
                        false /* shouldWrap */, true /* shouldScroll */,
                        true /* useInputFocusAsPivotIfEmpty */,
                        InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_PERFORM_LONG_CLICK:
                longClickCurrent();
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_BUTTON:
                performWebNavigationKeyCombo(HTML_ELEMENT_BUTTON, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_BUTTON:
                performWebNavigationKeyCombo(HTML_ELEMENT_BUTTON, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_CHECKBOX:
                performWebNavigationKeyCombo(HTML_ELEMENT_CHECKBOX, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHECKBOX:
                performWebNavigationKeyCombo(HTML_ELEMENT_CHECKBOX, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_ARIA_LANDMARK:
                performWebNavigationKeyCombo(HTML_ELEMENT_ARIA_LANDMARK, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_ARIA_LANDMARK:
                performWebNavigationKeyCombo(HTML_ELEMENT_ARIA_LANDMARK, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_EDIT_FIELD:
                performWebNavigationKeyCombo(HTML_ELEMENT_EDIT_FIELD, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_EDIT_FIELD:
                performWebNavigationKeyCombo(HTML_ELEMENT_EDIT_FIELD, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_FOCUSABLE_ITEM:
                performWebNavigationKeyCombo(HTML_ELEMENT_FOCUSABLE_ITEM, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_FOCUSABLE_ITEM:
                performWebNavigationKeyCombo(HTML_ELEMENT_FOCUSABLE_ITEM, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_1:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_1, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_1:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_1, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_2:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_2, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_2:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_2, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_3:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_3, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_3:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_3, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_4:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_4, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_4:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_4, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_5:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_5, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_5:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_5, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_6:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_6, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_6:
                performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_6, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_LINK:
                performWebNavigationKeyCombo(HTML_ELEMENT_LINK, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LINK:
                performWebNavigationKeyCombo(HTML_ELEMENT_LINK, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_CONTROL:
                performWebNavigationKeyCombo(HTML_ELEMENT_CONTROL, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CONTROL:
                performWebNavigationKeyCombo(HTML_ELEMENT_CONTROL, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_GRAPHIC:
                performWebNavigationKeyCombo(HTML_ELEMENT_GRAPHIC, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_GRAPHIC:
                performWebNavigationKeyCombo(HTML_ELEMENT_GRAPHIC, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST_ITEM:
                performWebNavigationKeyCombo(HTML_ELEMENT_LIST_ITEM, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST_ITEM:
                performWebNavigationKeyCombo(HTML_ELEMENT_LIST_ITEM, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST:
                performWebNavigationKeyCombo(HTML_ELEMENT_LIST, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST:
                performWebNavigationKeyCombo(HTML_ELEMENT_LIST, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_TABLE:
                performWebNavigationKeyCombo(HTML_ELEMENT_TABLE, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_TABLE:
                performWebNavigationKeyCombo(HTML_ELEMENT_TABLE, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_COMBOBOX:
                performWebNavigationKeyCombo(HTML_ELEMENT_COMBOBOX, true /* forward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_COMBOBOX:
                performWebNavigationKeyCombo(HTML_ELEMENT_COMBOBOX, false /* backward */);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT_WINDOW:
                navigateToNextOrPreviousWindow(TraversalStrategy.SEARCH_FOCUS_FORWARD,
                        WINDOW_TYPE_SYSTEM | WINDOW_TYPE_APPLICATION, FOCUS_STRATEGY_RESUME_FOCUS,
                        true /* useInputFocusAsPivot */, InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WINDOW:
                navigateToNextOrPreviousWindow(TraversalStrategy.SEARCH_FOCUS_BACKWARD,
                        WINDOW_TYPE_SYSTEM | WINDOW_TYPE_APPLICATION, FOCUS_STRATEGY_RESUME_FOCUS,
                        true /* useInputFocusAsPivot */, InputModeManager.INPUT_MODE_KEYBOARD);
                return true;
        }

        return false;
    }

    private void alertWebNavigationHitEdge(String htmlElement, boolean forward) {
        int resId = forward ? R.string.end_of_web : R.string.start_of_web;
        String displayName = null;
        switch (htmlElement) {
            case HTML_ELEMENT_HEADING:
                displayName = mService.getString(R.string.display_name_heading);
                break;
            case HTML_ELEMENT_BUTTON:
                displayName = mService.getString(R.string.display_name_button);
                break;
            case HTML_ELEMENT_CHECKBOX:
                displayName = mService.getString(R.string.display_name_checkbox);
                break;
            case HTML_ELEMENT_ARIA_LANDMARK:
                displayName = mService.getString(R.string.display_name_aria_landmark);
                break;
            case HTML_ELEMENT_EDIT_FIELD:
                displayName = mService.getString(R.string.display_name_edit_field);
                break;
            case HTML_ELEMENT_FOCUSABLE_ITEM:
                displayName = mService.getString(R.string.display_name_focusable_item);
                break;
            case HTML_ELEMENT_HEADING_1:
                displayName = mService.getString(R.string.display_name_heading_1);
                break;
            case HTML_ELEMENT_HEADING_2:
                displayName = mService.getString(R.string.display_name_heading_2);
                break;
            case HTML_ELEMENT_HEADING_3:
                displayName = mService.getString(R.string.display_name_heading_3);
                break;
            case HTML_ELEMENT_HEADING_4:
                displayName = mService.getString(R.string.display_name_heading_4);
                break;
            case HTML_ELEMENT_HEADING_5:
                displayName = mService.getString(R.string.display_name_heading_5);
                break;
            case HTML_ELEMENT_HEADING_6:
                displayName = mService.getString(R.string.display_name_heading_6);
                break;
            case HTML_ELEMENT_LINK:
                displayName = mService.getString(R.string.display_name_link);
                break;
            case HTML_ELEMENT_CONTROL:
                displayName = mService.getString(R.string.display_name_control);
                break;
            case HTML_ELEMENT_GRAPHIC:
                displayName = mService.getString(R.string.display_name_graphic);
                break;
            case HTML_ELEMENT_LIST_ITEM:
                displayName = mService.getString(R.string.display_name_list_item);
                break;
            case HTML_ELEMENT_LIST:
                displayName = mService.getString(R.string.display_name_list);
                break;
            case HTML_ELEMENT_TABLE:
                displayName = mService.getString(R.string.display_name_table);
                break;
            case HTML_ELEMENT_COMBOBOX:
                displayName = mService.getString(R.string.display_name_combobox);
                break;
            case HTML_ELEMENT_SECTION:
                displayName = mService.getString(R.string.display_name_section);
                break;
        }
        mService.getSpeechController().speak(
                mService.getString(resId, displayName),
                SpeechController.QUEUE_MODE_INTERRUPT,
                0,
                null);
    }

    private boolean performWebNavigationKeyCombo(String htmlElement, boolean forward) {
        if (isSupportedHtmlElement(htmlElement)) {
            boolean navigationSucceeded = forward ?
                    nextHtmlElement(htmlElement, InputModeManager.INPUT_MODE_KEYBOARD) :
                    previousHtmlElement(htmlElement, InputModeManager.INPUT_MODE_KEYBOARD);
            if (!navigationSucceeded) {
                alertWebNavigationHitEdge(htmlElement, forward);
            }
            return navigationSucceeded;
        }

        mService.getSpeechController().speak(
                mService.getString(R.string.keycombo_announce_shortcut_not_supported),
                SpeechController.QUEUE_MODE_INTERRUPT,
                FeedbackItem.FLAG_NO_HISTORY,
                null);

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        if (eventType == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
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
            if (mSwitchNodeWithGranularityDirection ==
                    AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) {
                mGranularityManager.navigate(
                        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            } else if (mSwitchNodeWithGranularityDirection ==
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) {
                mGranularityManager.startFromLastNode();
                mGranularityManager.navigate(
                        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
            }
            mSwitchNodeWithGranularityDirection = 0;
            nodeCompat.recycle();
            mReachedEdge = false;
            mGranularityNavigationReachedEdge = false;
        } else if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            final AccessibilityNodeInfo node = event.getSource();
            if (node != null) {
                final AccessibilityNodeInfoCompat nodeCompat =
                        new AccessibilityNodeInfoCompat(node);

                // Note: we also need to check ROLE_EDIT_TEXT for JB MR1 and lower and for
                // Chrome/WebView 51 and lower. We should check isEditable() first because it's
                // more semantically appropriate for what we want.
                if (nodeCompat.isEditable() || Role.getRole(nodeCompat) == Role.ROLE_EDIT_TEXT) {
                    AccessibilityNodeInfoUtils.recycleNodes(mLastEditable);
                    mLastEditable = nodeCompat;
                } else {
                    nodeCompat.recycle();
                }
            }
        } else if (mIsWindowNavigationAvailable &&
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Remove last focused nodes of non-existing windows.
            Set<Integer> windowIdsToBeRemoved = new HashSet(mLastFocusedNodeMap.keySet());
            for (AccessibilityWindowInfo window : mService.getWindows()) {
                windowIdsToBeRemoved.remove(window.getId());
            }
            for (Integer windowIdToBeRemoved : windowIdsToBeRemoved) {
                AccessibilityNodeInfoCompat removedNode =
                        mLastFocusedNodeMap.remove(windowIdToBeRemoved);
                if (removedNode != null) {
                    removedNode.recycle();
                }
            }
        }
    }

    private void rememberLastFocusedNode(AccessibilityNodeInfoCompat lastFocusedNode) {
        if (!mIsWindowNavigationAvailable) {
            return;
        }

        AccessibilityNodeInfoCompat oldNode = mLastFocusedNodeMap.put(lastFocusedNode.getWindowId(),
                AccessibilityNodeInfoCompat.obtain(lastFocusedNode));
        if (oldNode != null) {
            oldNode.recycle();
        }
    }

    /**
     * Determines if we should try web navigation on a node. Returns false if we should just do
     * normal navigation instead.
     *
     * @param node to navigate on
     * @param direction The direction to navigate, one of {@link TraversalStrategy.SearchDirection}.
     * @return {@code true} to attempt web navigation.
     */
    private boolean shouldAttemptHtmlNavigation(AccessibilityNodeInfoCompat node,
            @TraversalStrategy.SearchDirection int direction) {
        if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD ||
                direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
            return WebInterfaceUtils.supportsWebActions(node);
        } else {
            return false;
        }
    }

    /**
     * Attempts to navigate the node using HTML navigation.
     *
     * @param node to navigate on
     * @param direction The direction to navigate, one of {@link TraversalStrategy.SearchDirection}.
     * @return {@code true} if navigation succeeded.
     */
    private boolean attemptHtmlNavigation(AccessibilityNodeInfoCompat node,
            @TraversalStrategy.SearchDirection int direction) {
        if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
            return WebInterfaceUtils.performNavigationToHtmlElementAction(node,
                    WebInterfaceUtils.DIRECTION_FORWARD, "");
        } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
            return WebInterfaceUtils.performNavigationToHtmlElementAction(node,
                    WebInterfaceUtils.DIRECTION_BACKWARD, "");
        } else {
            return false;
        }
    }
}
