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

package com.google.android.accessibility.talkback.controller;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.CursorGranularityManager;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.SpannableTraversalUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Handles screen reader cursor management. */
public class CursorControllerApp implements CursorController {

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

  // Framework starts to provide stable supports for ClickableSpan from O. For pre-O devices, we
  // should check URLSpan only.
  private static final Class TARGET_SPAN_CLASS =
      (BuildVersionUtils.isAtLeastO()) ? ClickableSpan.class : URLSpan.class;

  /** Event types that are handled by CursorController. */
  private static final int MASK_EVENTS_HANDLED_BY_CURSOR_CONTROLLER =
      AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

  /** The host service. Used to access the root node. */
  private final TalkBackService mService;

  /** Handles traversal using granularity. */
  private final CursorGranularityManager mGranularityManager;

  private final GlobalVariables mGlobalVariables;

  /** Whether we should drive input focus instead of accessibility focus where possible. */
  private final boolean mControlInputFocus;

  /** Whether the current device supports navigating between multiple windows. */
  private final boolean mIsWindowNavigationAvailable;

  /** Whether the user hit an edge with the last swipe. */
  private boolean mReachedEdge;

  private boolean mGranularityNavigationReachedEdge;

  private boolean mIsNavigationEnabled = true;

  /** Map of window id to last-focused-node. Used to restore focus when closing popup window. */
  private final Map<Integer, AccessibilityNodeInfoCompat> mLastFocusedNodeMap = new HashMap<>();
  // TODO: Investigate whether this is redundant with {@code TalkBackService.mSavedNode}.

  private final Set<GranularityChangeListener> mGranularityListeners = new HashSet<>();

  private final Set<ScrollListener> mScrollListeners = new HashSet<>();

  private final Set<CursorListener> mCursorListeners = new HashSet<>();

  /** The last input-focused editable node. */
  private AccessibilityNodeInfoCompat mLastEditable;

  /**
   * The parent of the node that had the accessibility focus while navigating with native macro
   * granularity.
   */
  private AccessibilityNodeInfoCompat mLastFocusedNodeParent;

  private int mSwitchNodeWithGranularityDirection = 0;

  /** The last navigation action that triggered auto-scroll. */
  private Runnable mLastNavigationRunnable = null;

  /**
   * Creates a new cursor controller using the specified input controller.
   *
   * @param service The accessibility service. Used to obtain the current root node.
   */
  public CursorControllerApp(TalkBackService service, GlobalVariables globalVariables) {
    mService = service;
    mGlobalVariables = globalVariables;
    mGranularityManager = new CursorGranularityManager(globalVariables);

    boolean isTv = FormFactorUtils.getInstance(service).isTv();
    mControlInputFocus = isTv;
    mIsWindowNavigationAvailable = BuildVersionUtils.isAtLeastLMR1() && !isTv;
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
  public void setNavigationEnabled(boolean value) {
    mIsNavigationEnabled = value;
  }

  @Override
  public boolean refocus(EventId eventId) {
    final AccessibilityNodeInfoCompat node = getCursor();
    if (node == null) {
      return false;
    }

    EventState.getInstance().setFlag(EventState.EVENT_NODE_REFOCUSED);
    clearCursor(node, eventId);
    final boolean result = setCursor(node, eventId);
    node.recycle();
    return result;
  }

  @Override
  public boolean next(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithGranularity(
        TraversalStrategy.SEARCH_FOCUS_FORWARD,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        false,
        inputMode,
        eventId);
  }

  @Override
  public boolean previous(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithGranularity(
        TraversalStrategy.SEARCH_FOCUS_BACKWARD,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        false,
        inputMode,
        eventId);
  }

  @Override
  public boolean left(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithGranularity(
        TraversalStrategy.SEARCH_FOCUS_LEFT,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        false,
        inputMode,
        eventId);
  }

  @Override
  public boolean right(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithGranularity(
        TraversalStrategy.SEARCH_FOCUS_RIGHT,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        false,
        inputMode,
        eventId);
  }

  @Override
  public boolean up(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithGranularity(
        TraversalStrategy.SEARCH_FOCUS_UP,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        false,
        inputMode,
        eventId);
  }

  @Override
  public boolean down(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithGranularity(
        TraversalStrategy.SEARCH_FOCUS_DOWN,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        false,
        inputMode,
        eventId);
  }

  @Override
  public boolean jumpToTop(int inputMode, EventId eventId) {
    clearCursor(eventId);
    // Save the granularity before jumping to the top node and restore it after navigating to
    // the top node.
    CursorGranularity granularityBeforeNavigation = getCurrentGranularity();
    if (granularityBeforeNavigation != CursorGranularity.DEFAULT) {
      setGranularityToDefault();
    }
    mReachedEdge = true;
    boolean result =
        next(
            true /*shouldWrap*/,
            false /*shouldScroll*/,
            false /*useInputFocusAsPivotIfEmpty*/,
            inputMode,
            eventId);
    if (granularityBeforeNavigation != CursorGranularity.DEFAULT) {
      setGranularity(granularityBeforeNavigation, false /* not from user */, eventId);
    }
    return result;
  }

  @Override
  public boolean jumpToBottom(int inputMode, EventId eventId) {
    clearCursor(eventId);
    // Save the granularity before jumping to the bottom node and restore it after navigating to
    // the bottom node.
    CursorGranularity granularityBeforeNavigation = getCurrentGranularity();
    if (granularityBeforeNavigation != CursorGranularity.DEFAULT) {
      setGranularityToDefault();
    }
    mReachedEdge = true;
    boolean result =
        previous(
            true /*shouldWrap*/,
            false /*shouldScroll*/,
            false /*useInputFocusAsPivotIfEmpty*/,
            inputMode,
            eventId);
    if (granularityBeforeNavigation != CursorGranularity.DEFAULT) {
      setGranularity(granularityBeforeNavigation, false /* not from user */, eventId);
    }
    return result;
  }

  @Override
  public boolean more(EventId eventId) {
    return attemptScrollToDirection(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, eventId);
  }

  @Override
  public boolean less(EventId eventId) {
    return attemptScrollToDirection(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, eventId);
  }

  @Override
  public boolean nextWithSpecifiedGranularity(
      CursorGranularity granularity,
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithSpecifiedGranularity(
        TraversalStrategy.SEARCH_FOCUS_FORWARD, granularity, inputMode, eventId);
  }

  @Override
  public boolean previousWithSpecifiedGranularity(
      CursorGranularity granularity,
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId) {
    return navigateWithSpecifiedGranularity(
        TraversalStrategy.SEARCH_FOCUS_BACKWARD, granularity, inputMode, eventId);
  }

  @Override
  public boolean nextHtmlElement(String htmlElement, int inputMode, EventId eventId) {
    return navigateToHTMLElement(htmlElement, true /* forward */, inputMode, eventId);
  }

  @Override
  public boolean previousHtmlElement(String htmlElement, int inputMode, EventId eventId) {
    return navigateToHTMLElement(htmlElement, false /* backward */, inputMode, eventId);
  }

  private boolean isSupportedHtmlElement(String htmlElement) {
    AccessibilityNodeInfoCompat node = getCursor();
    if (node == null) {
      return false;
    }

    String[] supportedHtmlElements = WebInterfaceUtils.getSupportedHtmlElements(node);
    AccessibilityNodeInfoUtils.recycleNodes(node);
    return supportedHtmlElements != null
        && Arrays.asList(supportedHtmlElements).contains(htmlElement);
  }

  private boolean navigateToHTMLElement(
      String htmlElement, boolean forward, int inputMode, EventId eventId) {
    AccessibilityNodeInfoCompat node = getCursor();
    if (node == null) {
      return false;
    }

    try {
      int direction =
          forward ? WebInterfaceUtils.DIRECTION_FORWARD : WebInterfaceUtils.DIRECTION_BACKWARD;

      if (WebInterfaceUtils.performNavigationToHtmlElementAction(
          node, direction, htmlElement, eventId)) {
        mService.getInputModeManager().setInputMode(inputMode);
        return true;
      } else {
        return false;
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  private boolean attemptScrollToDirection(int direction, EventId eventId) {
    AccessibilityNodeInfoCompat cursor = null;
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat bfsScrollableNode = null;
    boolean result = false;
    try {
      cursor = getCursor();
      // if focus is on a scrollable view... scroll/increment
      if (cursor != null) {
        result = attemptScrollAction(cursor, direction, false, false, eventId);
      }

      // try to find scollable content above current node
      if (!result) {
        rootNode = AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
        bfsScrollableNode =
            AccessibilityNodeInfoUtils.searchFromBfs(
                rootNode,
                new Filter<AccessibilityNodeInfoCompat>() {
                  @Override
                  public boolean accept(AccessibilityNodeInfoCompat node) {
                    return AccessibilityNodeInfoUtils.isScrollable(node)
                        && isLogicalScrollableWidget(node)
                        && Role.getRole(node) != Role.ROLE_SEEK_CONTROL;
                  }
                });
        if (bfsScrollableNode != null) {
          result = attemptScrollAction(bfsScrollableNode, direction, false, false, eventId);
        }
      }

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(cursor, rootNode, bfsScrollableNode);
    }

    return result;
  }

  @Override
  public boolean clickCurrent(EventId eventId) {
    return performAction(AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
  }

  @Override
  public boolean clickCurrentHierarchical(EventId eventId) {
    Filter<AccessibilityNodeInfoCompat> clickFilter =
        new Filter<AccessibilityNodeInfoCompat>() {
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
      return PerformActionUtils.performAction(
          match, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(cursor, match);
    }
  }

  @Override
  public boolean longClickCurrent(EventId eventId) {
    return performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, eventId);
  }

  @Override
  public boolean nextGranularity(EventId eventId) {
    return adjustGranularity(1, eventId);
  }

  @Override
  public boolean previousGranularity(EventId eventId) {
    return adjustGranularity(-1, eventId);
  }

  @Override
  public boolean setGranularity(CursorGranularity granularity, boolean fromUser, EventId eventId) {
    AccessibilityNodeInfoCompat current = null;

    try {
      current = getCursorOrInputCursor();
      return setGranularity(granularity, current, fromUser, eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(current);
    }
  }

  @Override
  public boolean setGranularity(
      CursorGranularity granularity,
      AccessibilityNodeInfoCompat node,
      boolean fromUser,
      EventId eventId) {
    // Setting macro granularity should be allowed even when the node is null.
    if (node == null && !granularity.isNativeMacroGranularity()) {
      return false;
    }

    if (!mGranularityManager.setGranularityAt(node, granularity, eventId)) {
      return false;
    }

    granularityUpdated(granularity, fromUser, eventId);
    return true;
  }

  /** Called to reset granularity after window transition. */
  @Override
  public void setGranularityToDefault() {
    mGranularityManager.setGranularityToDefault();
    mService.getAnalytics().clearPendingGranularityChange();
  }

  @Override
  public void resetLastFocusedInfo() {
    AccessibilityNodeInfoUtils.recycleNodes(mLastFocusedNodeParent);
    mLastFocusedNodeParent = null;
  }

  @Override
  public boolean setCursor(AccessibilityNodeInfoCompat node, EventId eventId) {
    // Accessibility focus follows input focus; on TVs we want to set both simultaneously,
    // so we change the input focus if possible and let the ProcessorFocusAndSingleTap
    // handle changing the accessibility focus.
    if (mControlInputFocus && node.isFocusable() && !node.isFocused()) {
      if (setCursor(node, AccessibilityNodeInfoCompat.ACTION_FOCUS, eventId)) {
        return true;
      }
    }

    // Set accessibility focus otherwise (or as a fallback if setting input focus failed).
    return setCursor(node, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
  }

  private boolean setCursor(AccessibilityNodeInfoCompat node, int action, EventId eventId) {
    final Set<CursorListener> listeners = new HashSet<>(mCursorListeners);
    for (CursorListener listener : listeners) {
      listener.beforeSetCursor(node, action);
    }

    boolean performedAction = PerformActionUtils.performAction(node, action, eventId);
    if (performedAction) {
      rememberLastFocusedNode(node);

      for (CursorListener listener : listeners) {
        listener.onSetCursor(node, action);
      }
    }

    return performedAction;
  }

  @Override
  public void setSelectionModeActive(
      AccessibilityNodeInfoCompat node, boolean active, EventId eventId) {
    if (active && !mGranularityManager.isLockedTo(node)) {
      setGranularity(CursorGranularity.CHARACTER, false /* fromUser */, eventId);
    }

    mGranularityManager.setSelectionModeActive(active);
  }

  @Override
  public boolean isSelectionModeActive() {
    return mGranularityManager.isSelectionModeActive();
  }

  @Override
  public void clearCursor(EventId eventId) {
    AccessibilityNodeInfoCompat currentNode = getCursor();
    if (currentNode == null) {
      return;
    }

    clearCursor(currentNode, eventId);
    currentNode.recycle();
  }

  @Override
  public void clearCursor(AccessibilityNodeInfoCompat currentNode, EventId eventId) {
    if (currentNode == null) {
      return;
    }
    PerformActionUtils.performAction(
        currentNode, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId);
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
    final AccessibilityNodeInfoCompat compatRoot =
        AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);

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
    final AccessibilityNodeInfoCompat compatRoot =
        AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);

    if (compatRoot == null) {
      return null;
    }

    AccessibilityNodeInfoCompat focusedNode = getAccessibilityFocusedNode(compatRoot);

    // TODO: If there's no focused node, we should either mimic following
    // focus from new window or try to be smart for things like list views.
    if (focusedNode == null) {
      AccessibilityNodeInfoCompat inputFocusedNode = getInputFocusedNode();
      if (inputFocusedNode != null
          && inputFocusedNode.isFocused()
          && inputFocusedNode.isEditable()) {
        focusedNode = inputFocusedNode;
      }
    }

    // If we can't find the focused node but the keyboard is showing, return the last editable.
    // This will occur if the input-focused view is actually a virtual view (e.g. in WebViews).
    // Note: need to refresh() in order to verify that the node is still available on-screen.
    if (focusedNode == null && mLastEditable != null && mLastEditable.refresh()) {
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

    AccessibilityNodeInfoCompat focusedNode =
        compatRoot.findFocus(AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);

    if (focusedNode == null) {
      return null;
    }

    if (!AccessibilityNodeInfoUtils.isVisible(focusedNode)) {
      focusedNode.recycle();
      return null;
    }

    return focusedNode;
  }

  @Override
  public void initLastEditable() {
    maybeUpdateLastEditable(getInputFocusedNode());
  }

  /** If focus is edit-text, set mLastEditable. Takes ownership of focus node. */
  private void maybeUpdateLastEditable(AccessibilityNodeInfoCompat focus) {
    if (focus == null) {
      return;
    }
    if (focus.isEditable() || Role.getRole(focus) == Role.ROLE_EDIT_TEXT) {
      setLastEditable(focus);
    } else {
      AccessibilityNodeInfoUtils.recycleNodes(focus);
    }
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

  @Override
  public CursorGranularity getCurrentGranularity() {
    return mGranularityManager.getCurrentGranularity();
  }

  @Override
  public void repeatLastNavigationAction() {
    if (mLastNavigationRunnable != null) {
      mLastNavigationRunnable.run();
      mLastNavigationRunnable = null;
    }
  }

  /**
   * Attempts to scroll using the specified action.
   *
   * @param action The scroll action to perform.
   * @param auto If {@code true}, then the scroll was initiated automatically. If {@code false},
   *     then the user initiated the scroll action.
   * @return Whether the action was performed.
   */
  private boolean attemptScrollAction(
      AccessibilityNodeInfoCompat cursor,
      int action,
      boolean auto,
      boolean isRetryAutoScroll,
      EventId eventId) {
    if (cursor == null) {
      return false;
    }

    AccessibilityNodeInfoCompat scrollableNode = null;
    try {
      scrollableNode = getBestScrollableNode(cursor, action, auto);
      if (scrollableNode == null) {
        return false;
      }

      setNavigationEnabled(false);
      final boolean performedAction =
          PerformActionUtils.performAction(scrollableNode, action, eventId);
      if (performedAction) {
        final Set<ScrollListener> listeners = new HashSet<>(mScrollListeners);
        for (ScrollListener listener : listeners) {
          listener.onScroll(scrollableNode, action, auto, isRetryAutoScroll);
        }
      } else {
        setNavigationEnabled(true);
      }

      return performedAction;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(scrollableNode);
    }
  }

  private AccessibilityNodeInfoCompat getBestScrollableNode(
      AccessibilityNodeInfoCompat cursor, final int action, final boolean skipCursor) {
    final Filter<AccessibilityNodeInfoCompat> nodeFilter =
        AccessibilityNodeInfoUtils.FILTER_SCROLLABLE.and(
            new Filter<AccessibilityNodeInfoCompat>() {
              @Override
              public boolean accept(AccessibilityNodeInfoCompat node) {
                return node != null && AccessibilityNodeInfoUtils.supportsAction(node, action);
              }
            });
    final AccessibilityNodeInfoCompat predecessor =
        skipCursor
            ? AccessibilityNodeInfoUtils.getMatchingAncestor(cursor, nodeFilter)
            : AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(cursor, nodeFilter);

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

    @RoleName int role = Role.getRole(node);
    return role != Role.ROLE_DATE_PICKER && role != Role.ROLE_TIME_PICKER;
  }

  /**
   * Attempts to adjust granularity in the direction indicated.
   *
   * @param direction The direction to adjust granularity. One of {@link
   *     CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or {@link
   *     CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
   * @return true on success, false if no nodes in the current hierarchy support a granularity other
   *     than the default.
   */
  private boolean adjustGranularity(int direction, EventId eventId) {
    AccessibilityNodeInfoCompat currentNode = null;

    try {
      currentNode = getCursorOrInputCursor();

      final boolean wasAdjusted =
          mGranularityManager.adjustGranularityAt(currentNode, direction, eventId);

      CursorGranularity currentGranularity = mGranularityManager.getCurrentGranularity();

      if (wasAdjusted) {
        // If the current granularity after change is default or native macro granularity
        // (Headings, controls, etc), we want to keep that change even if the currentNode is null.
        // The idea is to relax the constraint for native macro granularity to  always have
        // accessibility focus on screen to switch between them.
        if (currentGranularity.isNativeMacroGranularity()
            || currentGranularity == CursorGranularity.DEFAULT
            || currentNode != null) {
          granularityUpdated(currentGranularity, true, eventId);
        } else {
          // If the current node is null and the granularity after change is not native macro,
          // we want to discard the change as micro granularities (Characters, words, etc) are
          // always dependent on the node having accessibility focus.
          mGranularityManager.adjustGranularityAt(currentNode, direction * -1, eventId);
          return false;
        }
      }

      return wasAdjusted;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentNode);
    }
  }

  /** Try to navigate with specified granularity. */
  private boolean navigateWithSpecifiedGranularity(
      int direction, CursorGranularity granularity, int inputMode, EventId eventId) {
    // Keep current granularity to set it back after this operation.
    CursorGranularity currentGranularity = mGranularityManager.getCurrentGranularity();
    boolean sameGranularity = currentGranularity == granularity;

    // Navigate with specified granularity.
    if (!sameGranularity) {
      setGranularity(granularity, false /* not from user */, eventId);
    }
    boolean result =
        navigateWithGranularity(direction, false, true, true, false, inputMode, eventId);

    // Set back to the granularity which is used before this operation.
    if (!sameGranularity) {
      setGranularity(currentGranularity, false /* not from user */, eventId);
    }

    return result;
  }

  /** Associates the logical direction with the navigation action */
  private static int getNavigationAction(@TraversalStrategy.SearchDirection int logicalDirection) {

    final int navigationAction;
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      navigationAction = AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
    } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      navigationAction = AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
    } else {
      throw new IllegalStateException("Unknown logical direction");
    }
    return navigationAction;
  }

  private static void getTargetOnScreen(
      AccessibilityNodeInfoCompat target,
      TraversalStrategy traversalStrategy,
      @TraversalStrategy.SearchDirection int direction,
      boolean shouldScroll,
      EventId eventId) {
    // The `spatial` condition provides a work-around for RecyclerViews.
    // Currently RecyclerViews do not support ACTION_SCROLL_LEFT, UP, etc.
    // TODO: Remove `spatial` check when RecyclerViews support new scroll actions.
    final boolean spatial = TraversalStrategyUtils.isSpatialDirection(direction);
    boolean autoScroll =
        TraversalStrategyUtils.isAutoScrollEdgeListItem(target, direction, traversalStrategy)
            || spatial;
    if (BuildVersionUtils.isAtLeastM() && shouldScroll && autoScroll) {
      PerformActionUtils.performAction(
          target, AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId(), eventId);
    }
  }

  // If we're in a background window, we need to return the cursor to the current
  // window and prevent navigation within the background window.
  private AccessibilityNodeInfoCompat getCursorToCurrentWindow(
      AccessibilityNodeInfoCompat current) {
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
    return current;
  }

  // Alert when we navigate to the edge with a web granularity.
  private void onWebNavigationHitEdge(
      @TraversalStrategy.SearchDirection int logicalDirection, EventId eventId) {
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
      alertWebNavigationHitEdge(
          htmlElement, logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD, eventId);
    }
  }

  private boolean getScrollPreference() {
    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
    boolean shouldScroll =
        SharedPreferencesUtils.getBooleanPref(
            prefs,
            mService.getResources(),
            R.string.pref_auto_scroll_key,
            R.bool.pref_auto_scroll_default);
    return shouldScroll;
  }

  @Override
  public void setLastFocusedNodeParent(AccessibilityNodeInfoCompat scrolledNode) {
    AccessibilityNodeInfoUtils.recycleNodes(mLastFocusedNodeParent);
    mLastFocusedNodeParent = AccessibilityNodeInfoCompat.obtain(scrolledNode);
  }

  /**
   * Attempts to move in the direction indicated.
   *
   * <p>If a navigation granularity other than DEFAULT has been applied, attempts to move within the
   * current object at the specified granularity.
   *
   * <p>If no granularity has been applied, or if the DEFAULT granularity has been applied, attempts
   * to move in the specified direction using {@link android.view.View#focusSearch(int)}.
   *
   * @param direction The direction to move.
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @return true on success, false on failure.
   */
  private boolean navigateWithGranularity(
      @TraversalStrategy.SearchDirection final int direction,
      final boolean shouldWrap,
      boolean shouldScroll,
      final boolean useInputFocusAsPivotIfEmpty,
      final boolean isRepeatNavigationAction,
      final int inputMode,
      final EventId eventId) {
    if (!mIsNavigationEnabled && !isRepeatNavigationAction) {
      LogUtils.log(this, Log.WARN, "Cannot navigate until auto-scroll action completes.");
      return false;
    }

    // We don't log immediately the settings changed linearly(e.g. change granularity with gestures,
    // change verbosity with Selector) until the user performs a navigation action.
    mService.getAnalytics().logPendingChanges();

    @TraversalStrategy.SearchDirection
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            direction, WindowManager.isScreenLayoutRTL(mService));

    final int navigationAction = getNavigationAction(logicalDirection);

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
        // Get cursor out of the background window.
        current = getCursorToCurrentWindow(current);
      }

      // If granularity is more specific than default, and locked to current node, then
      // restrict navigation to the current node.
      if (mGranularityManager.isLockedTo(current)) {
        final int result = mGranularityManager.navigate(navigationAction, eventId);
        if (result == CursorGranularityManager.SUCCESS) {
          mGranularityNavigationReachedEdge = false;
          processResult = true;
          return processResult;
        }

        if (BuildVersionUtils.isAtLeastLMR1()
            && result == CursorGranularityManager.HIT_EDGE
            && !isEditing(current)) {
          if (!mGranularityNavigationReachedEdge) {
            onWebNavigationHitEdge(logicalDirection, eventId);
            // Skip one swipe when hit edge during granularity navigation.
            // mGranularityNavigationReachedEdge value needs to be changed
            // only if its not a web granularity navigation. For web granularity navigation
            // we would want to enter this loop and create alert as many times we hit the edge.
            if (!mGranularityManager.getCurrentGranularity().isWebGranularity()) {
              mGranularityNavigationReachedEdge = true;
            }
            processResult = false;
            return processResult;
          }
        } else {
          // Granular navigation did not reach the edge, but failed for some other reason.
          processResult = false;
          return processResult;
        }
      }
      // We shouldn't navigate past the last "link", "heading", etc. when
      // navigating with a web granularity.
      // It makes sense to navigate to the next node with other kinds of
      // granularities(characters, words, etc.).
      if (mGranularityManager.getCurrentGranularity().isWebGranularity()) {
        processResult = false;
        return processResult;
      }

      // If the current node has web content, attempt HTML navigation only in 2 conditions:
      // 1. If currently focused is not a web view container OR
      // 2. If currently focused is a web view container but the logical direction is forward.
      // Consider the following linear order when navigating between web
      // views and native views assuming that a web view is in between native elements:
      // Native elements -> web view container -> inside web view container -> native elements.
      // Web view container should be focused only in the above order.
      if (WebInterfaceUtils.supportsWebActions(current)
          && (Role.getRole(current) != Role.ROLE_WEB_VIEW
              || logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD)) {
        if (attemptHtmlNavigation(current, direction, eventId)) {
          // Succeeded finding destination inside WebView
          AccessibilityNodeInfoCompat currentFocusedNode =
              getAccessibilityFocusedNode(AccessibilityNodeInfoUtils.getRoot(current));
          // We might want to mute feedback from target node if current granularity is not default
          // and we are moving from one node to another using default granularity
          muteTargetNodeFeedbackForNonDefaultGranularity(
              currentFocusedNode, navigationAction, eventId);
          processResult = true;
          return true;
        } else {
          // Ascend to closest WebView node, preparing to navigate past WebView with normal
          // navigation.
          AccessibilityNodeInfoCompat webView = WebInterfaceUtils.ascendToWebView(current);
          if (webView != null) {
            current.recycle();
            current = webView;
          }
        }
      }

      // If the user has disabled automatic scrolling, don't attempt to scroll.
      // TODO: Remove once auto-scroll is settled.
      if (shouldScroll) {
        shouldScroll = getScrollPreference();
      }

      rootNode = AccessibilityNodeInfoUtils.getRoot(current);
      traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(rootNode, direction);

      // If the current item is at the edge of a scrollable view, try to
      // automatically scroll the view in the direction of navigation.
      if (shouldScroll
          && TraversalStrategyUtils.isAutoScrollEdgeListItem(current, direction, traversalStrategy)
          && attemptScrollAction(
              current, scrollDirection, true, isRepeatNavigationAction, eventId)) {
        if (!isRepeatNavigationAction) {
          initializeLastNavigationRunnable(
              direction, shouldWrap, useInputFocusAsPivotIfEmpty, inputMode, eventId);
        }
        processResult = true;
        return processResult;
      }

      // Otherwise, move focus to next or previous focusable node.
      target = navigateFrom(current, direction, traversalStrategy);

      // If the target is a web view, avoid focusing on it when the direction is backward.
      // Consider the following linear order when navigating between web
      // views and native views assuming that a web view is in between native elements:
      // Native elements -> web view container -> inside web view container -> native elements.
      // Web view container should be focused only in the above order.
      if (target != null && Role.getRole(target) == Role.ROLE_WEB_VIEW) {
        // If the currently focused node is a native element and we are trying to move in the web
        // view in a backward direction, attempt html navigation and skip focusing the
        // web view container.
        if (!WebInterfaceUtils.supportsWebActions(current)
            && logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
          boolean result = attemptHtmlNavigation(target, direction, eventId);
          // Succeeded finding destination inside WebView
          if (result) {
            AccessibilityNodeInfoCompat currentFocusedNode =
                getAccessibilityFocusedNode(AccessibilityNodeInfoUtils.getRoot(target));
            // We might want to mute feedback from target node if current granularity is not default
            // and we are moving from one node to another using default granularity
            muteTargetNodeFeedbackForNonDefaultGranularity(
                currentFocusedNode, navigationAction, eventId);
            processResult = true;
            return true;
          }
        }
      }

      if (isNativeMacroGranularity()) {
        boolean scrolled =
            scrollForNativativeMacroGranularity(
                rootNode,
                target,
                shouldScroll,
                scrollDirection,
                isRepeatNavigationAction,
                direction,
                shouldWrap,
                useInputFocusAsPivotIfEmpty,
                inputMode,
                traversalStrategy,
                eventId);

        // If the scroll was unsuccessful, we should not return.
        if (scrolled) {
          return true;
        }
      }

      if ((target != null)) {

        if (!mGranularityManager.getCurrentGranularity().isNativeMacroGranularity()) {
          // We might want to mute feedback from target node if current granularity
          // is micro granularity (characters, words, etc) and not default
          // and we are moving from one node to another using default granularity.
          muteTargetNodeFeedbackForNonDefaultGranularity(target, navigationAction, eventId);
        }

        getTargetOnScreen(target, traversalStrategy, direction, shouldScroll, eventId);

        if (setCursor(target, eventId)) {
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
      if (navigateToNextOrPreviousWindow(
          direction,
          WINDOW_TYPE_APPLICATION | WINDOW_TYPE_SPLIT_SCREEN_DIVIDER,
          FOCUS_STRATEGY_WRAP_AROUND,
          false /* useInputFocusAsPivot */,
          inputMode,
          eventId)) {
        mReachedEdge = false;
        processResult = true;
        return processResult;
      }

      if (mReachedEdge && shouldWrap) {
        mReachedEdge = false;
        processResult =
            navigateWrapAround(rootNode, direction, traversalStrategy, inputMode, eventId);
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
        mGlobalVariables.clearFlag(
            GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
        EventState.getInstance().clearFlag(EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE);
      }
    }
  }

  private boolean scrollForNativativeMacroGranularity(
      AccessibilityNodeInfoCompat rootNode,
      AccessibilityNodeInfoCompat target,
      boolean shouldScroll,
      int scrollDirection,
      boolean isRepeatNavigationAction,
      @TraversalStrategy.SearchDirection int direction,
      boolean shouldWrap,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      TraversalStrategy traversalStrategy,
      EventId eventId) {

    AccessibilityNodeInfoCompat referenceNode = null;
    try {
      AccessibilityNodeInfoCompat a11yFocusedNode = getAccessibilityFocusedNode(rootNode);

      // If a11y focus is null, we try to get the first child of the last scrolled container to
      // keep as a reference for scrolling. A visibility check is not required as it is just a
      // reference to start the scroll.
      if (a11yFocusedNode == null
          && mLastFocusedNodeParent != null
          && mLastFocusedNodeParent.getChildCount() > 0) {
        if (mLastFocusedNodeParent.getChild(0) != null) {
          referenceNode = AccessibilityNodeInfoCompat.obtain(mLastFocusedNodeParent.getChild(0));
        }
      }

      AccessibilityNodeInfoCompat focusedOrReferenceNode =
          (a11yFocusedNode == null) ? referenceNode : a11yFocusedNode;

      AccessibilityNodeInfoCompat focusedNodeParent =
          (a11yFocusedNode == null) ? mLastFocusedNodeParent : a11yFocusedNode.getParent();

      // If we are navigating within a scrollable container with native macro granularity, we want
      // to make sure we have traversed the complete list before jumping to an element that is on
      // screen but out of the scrollable container. So the target that is found to be out of the
      // scrollable container is ignored.
      if (AccessibilityNodeInfoUtils.isTopLevelScrollItem(focusedOrReferenceNode)
          && target != null
          && !target.getParent().equals(focusedNodeParent)) {
        target = null;
      }

      // If we find no target on screen for native macro granularity, we do our best attempt to
      // scroll to the next screen and place the focus on the new screen if it exists.
      if (target == null) {
        if (shouldScroll
            && attemptScrollAction(
                focusedOrReferenceNode, scrollDirection, true, isRepeatNavigationAction, eventId)) {
          if (!isRepeatNavigationAction) {
            initializeLastNavigationRunnable(
                direction, shouldWrap, useInputFocusAsPivotIfEmpty, inputMode, eventId);
          }
          return true;
        } else {
          // If the attempt to scroll fails, we try and look for a valid focus that is on screen.
          AccessibilityNodeInfoCompat startNode =
              (getAccessibilityFocusedNode(rootNode) == null)
                  ? mLastFocusedNodeParent
                  : getAccessibilityFocusedNode(rootNode);

          target = navigateFrom(startNode, direction, traversalStrategy);
        }
      }
      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(referenceNode);
    }
  }

  private void initializeLastNavigationRunnable(
      final @TraversalStrategy.SearchDirection int direction,
      final boolean shouldWrap,
      final boolean useInputFocusAsPivotIfEmpty,
      final int inputMode,
      final EventId eventId) {
    mLastNavigationRunnable =
        new Runnable() {
          @Override
          public void run() {
            navigateWithGranularity(
                direction, shouldWrap, true, useInputFocusAsPivotIfEmpty, true, inputMode, eventId);
          }
        };
  }

  /**
   * If target node supports desired non-default granularity... (We might be trying to navigate with
   * specific granularity, but temporarily using default granularity. Example: navigating to a node
   * that does not support the specific granularity. Example: specific-granularity navigation
   * reached the end of one node, and must use default-granularity navigation to reach the next
   * node.)
   */
  private void muteTargetNodeFeedbackForNonDefaultGranularity(
      AccessibilityNodeInfoCompat target, int navigationAction, EventId eventId) {

    if (mGranularityManager.getCurrentGranularity() != CursorGranularity.DEFAULT
        || mGranularityManager.getSavedGranularity() != CursorGranularity.DEFAULT) {
      final List<CursorGranularity> targetGranularities =
          CursorGranularityManager.getSupportedGranularities(mService, target, eventId);
      CursorGranularity savedGranularity = mGranularityManager.getSavedGranularity();
      if (targetGranularities != null && targetGranularities.contains(savedGranularity)) {
        // Mute reading target node's full contents.
        mSwitchNodeWithGranularityDirection = navigationAction;
        mGlobalVariables.setFlag(
            GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
        EventState.getInstance().setFlag(EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE);
      }
    }
  }

  private boolean isEditing(AccessibilityNodeInfoCompat node) {
    return node.isEditable() && node.isFocused();
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
    if (!BuildVersionUtils.isAtLeastLMR1()) {
      // always pause before loop in one-window conditions
      return true;
    }

    WindowManager windowManager = new WindowManager(mService);

    if (!windowManager.isApplicationWindowFocused()
        && !windowManager.isSplitScreenDividerFocused()) {
      // need pause before looping traversal in non-application window
      return true;
    }

    @TraversalStrategy.SearchDirection
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            direction, WindowManager.isScreenLayoutRTL(mService));
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
      int windowTypeFilter,
      int focusStrategy,
      boolean useInputFocusAsPivot,
      int inputMode,
      EventId eventId) {
    if (!mIsWindowNavigationAvailable) {
      return false;
    }

    WindowManager windowManager = new WindowManager(mService);

    AccessibilityWindowInfo pivotWindow = windowManager.getCurrentWindow(useInputFocusAsPivot);
    if (pivotWindow == null || !matchWindowType(pivotWindow, windowTypeFilter)) {
      return false;
    }

    AccessibilityWindowInfo targetWindow = pivotWindow;
    while (true) {
      @TraversalStrategy.SearchDirection
      int logicalDirection =
          TraversalStrategyUtils.getLogicalDirection(
              direction, WindowManager.isScreenLayoutRTL(mService));
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

      AccessibilityNodeInfoCompat compatRoot = AccessibilityNodeInfoUtils.toCompat(windowRoot);

      if (focusStrategy == FOCUS_STRATEGY_RESUME_FOCUS) {
        if (resumeLastFocus(targetWindow.getId(), inputMode, eventId)) {
          return true;
        }

        // If it cannot resume last focus, try to focus the first focusable element.
        TraversalStrategy traversalStrategy =
            TraversalStrategyUtils.getTraversalStrategy(
                compatRoot, TraversalStrategy.SEARCH_FOCUS_FORWARD);
        if (navigateWrapAround(
            compatRoot,
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            traversalStrategy,
            inputMode,
            eventId)) {
          return true;
        }
      } else {
        TraversalStrategy traversalStrategy =
            TraversalStrategyUtils.getTraversalStrategy(compatRoot, direction);
        if (navigateWrapAround(compatRoot, direction, traversalStrategy, inputMode, eventId)) {
          return true;
        }
      }
    }
  }

  private boolean matchWindowType(AccessibilityWindowInfo window, int windowTypeFilter) {
    int windowType = window.getType();
    if ((windowTypeFilter & WINDOW_TYPE_SYSTEM) != 0
        && windowType == AccessibilityWindowInfo.TYPE_SYSTEM) {
      return true;
    } else if ((windowTypeFilter & WINDOW_TYPE_APPLICATION) != 0
        && windowType == AccessibilityWindowInfo.TYPE_APPLICATION) {
      return true;
    } else if ((windowTypeFilter & WINDOW_TYPE_SPLIT_SCREEN_DIVIDER) != 0
        && windowType == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
      return true;
    } else {
      return false;
    }
  }

  private boolean navigateWrapAround(
      AccessibilityNodeInfoCompat root,
      @TraversalStrategy.SearchDirection int direction,
      TraversalStrategy traversalStrategy,
      int inputMode,
      EventId eventId) {
    if (root == null) {
      return false;
    }

    AccessibilityNodeInfoCompat tempNode = null;
    AccessibilityNodeInfoCompat wrapNode = null;

    try {
      tempNode = traversalStrategy.focusInitial(root, direction);
      wrapNode = navigateSelfOrFrom(tempNode, direction, traversalStrategy);

      if (wrapNode == null) {
        LogUtils.log(this, Log.ERROR, "Failed to wrap navigation");
        return false;
      }

      @TraversalStrategy.SearchDirection
      int logicalDirection =
          TraversalStrategyUtils.getLogicalDirection(
              direction, WindowManager.isScreenLayoutRTL(mService));

      final int navigationAction = getNavigationAction(logicalDirection);

      if (setCursor(wrapNode, eventId)) {
        // We might want to mute feedback from target node if current granularity is not default
        // and we are moving from one node to another using default granularity.
        muteTargetNodeFeedbackForNonDefaultGranularity(wrapNode, navigationAction, eventId);
        mService.getInputModeManager().setInputMode(inputMode);
        return true;
      } else {
        return false;
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(tempNode, wrapNode);
    }
  }

  private boolean resumeLastFocus(int windowId, int inputMode, EventId eventId) {
    AccessibilityNodeInfoCompat lastFocusedNode = mLastFocusedNodeMap.get(windowId);
    if (lastFocusedNode == null) {
      return false;
    }

    if (setCursor(lastFocusedNode, eventId)) {
      mService.getInputModeManager().setInputMode(inputMode);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public Filter<AccessibilityNodeInfoCompat> getFilter(
      final TraversalStrategy traversalStrategy, final boolean useSpeakingNodesCache) {

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null
                && AccessibilityNodeInfoUtils.shouldFocusNode(
                    node, useSpeakingNodesCache ? traversalStrategy.getSpeakingNodesCache() : null);
          }
        };
    Filter<AccessibilityNodeInfoCompat> filterWithAdditionalChecks = null;

    CursorGranularity currentGranularity = mGranularityManager.getSavedGranularity();
    switch (currentGranularity) {
      case HEADING:
        {
          filterWithAdditionalChecks =
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return (node.getCollectionItemInfo() != null)
                      && node.getCollectionItemInfo().isHeading();
                }
              };
          break;
        }
      case CONTROL:
        {
          filterWithAdditionalChecks =
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return (Role.getRole(node) == Role.ROLE_BUTTON
                      || Role.getRole(node) == Role.ROLE_IMAGE_BUTTON
                      || Role.getRole(node) == Role.ROLE_EDIT_TEXT);
                }
              };
          break;
        }
      case LINK:
        {
          filterWithAdditionalChecks =
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return SpannableTraversalUtils.hasTargetSpanInNodeTreeDescription(
                      node, TARGET_SPAN_CLASS);
                }
              };
          break;
        }
      default:
        {
          filterWithAdditionalChecks =
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return true;
                }
              };
        }
    }
    return filter.and(filterWithAdditionalChecks);
  }

  @Override
  public boolean isNativeMacroGranularity() {
    return mGranularityManager.getSavedGranularity().isNativeMacroGranularity();
  }

  private AccessibilityNodeInfoCompat navigateSelfOrFrom(
      AccessibilityNodeInfoCompat node,
      @TraversalStrategy.SearchDirection int direction,
      TraversalStrategy traversalStrategy) {
    if (node == null) {
      return null;
    }
    Filter<AccessibilityNodeInfoCompat> filter = getFilter(traversalStrategy, true);

    if (filter.accept(node)) {
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
    Filter<AccessibilityNodeInfoCompat> filter = getFilter(traversalStrategy, true);

    AccessibilityNodeInfoCompat returnNode =
        TraversalStrategyUtils.searchFocus(traversalStrategy, node, direction, filter);

    return returnNode;
  }

  private void granularityUpdated(
      CursorGranularity granularity, boolean fromUser, EventId eventId) {
    final Set<GranularityChangeListener> localListeners = new HashSet<>(mGranularityListeners);

    for (GranularityChangeListener listener : localListeners) {
      listener.onGranularityChanged(granularity);
    }

    if (fromUser) {
      mService
          .getSpeechController()
          .speak(
              mService.getString(granularity.resourceId), /* Text */
              SpeechController.QUEUE_MODE_INTERRUPT, /* QueueMode */
              FeedbackItem.FLAG_FORCED_FEEDBACK, /* Flags */
              null, /* SpeechParams */
              eventId);
    }
  }

  /**
   * Performs the specified action on the current cursor.
   *
   * @param action The action to perform on the current cursor.
   * @return {@code true} if successful.
   */
  private boolean performAction(int action, EventId eventId) {
    AccessibilityNodeInfoCompat current = null;

    try {
      current = getCursor();
      return current != null && PerformActionUtils.performAction(current, action, eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(current);
    }
  }

  @Override
  public boolean onComboPerformed(int id, EventId eventId) {
    switch (id) {
      case KeyComboManager.ACTION_NAVIGATE_NEXT:
        next(
            true /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS:
        previous(
            true /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_DEFAULT:
        nextWithSpecifiedGranularity(
            CursorGranularity.DEFAULT,
            true /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_DEFAULT:
        previousWithSpecifiedGranularity(
            CursorGranularity.DEFAULT,
            true /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_UP:
        up(true, true, true, InputModeManager.INPUT_MODE_KEYBOARD, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_DOWN:
        down(true, true, true, InputModeManager.INPUT_MODE_KEYBOARD, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_FIRST:
        jumpToTop(InputModeManager.INPUT_MODE_KEYBOARD, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_LAST:
        jumpToBottom(InputModeManager.INPUT_MODE_KEYBOARD, eventId);
        return true;
      case KeyComboManager.ACTION_PERFORM_CLICK:
        clickCurrent(eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_WORD:
        nextWithSpecifiedGranularity(
            CursorGranularity.WORD,
            false /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WORD:
        previousWithSpecifiedGranularity(
            CursorGranularity.WORD,
            false /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CHARACTER:
        nextWithSpecifiedGranularity(
            CursorGranularity.CHARACTER,
            false /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHARACTER:
        previousWithSpecifiedGranularity(
            CursorGranularity.CHARACTER,
            false /* shouldWrap */,
            true /* shouldScroll */,
            true /* useInputFocusAsPivotIfEmpty */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_PERFORM_LONG_CLICK:
        longClickCurrent(eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_BUTTON:
        performWebNavigationKeyCombo(HTML_ELEMENT_BUTTON, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_BUTTON:
        performWebNavigationKeyCombo(HTML_ELEMENT_BUTTON, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CHECKBOX:
        performWebNavigationKeyCombo(HTML_ELEMENT_CHECKBOX, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHECKBOX:
        performWebNavigationKeyCombo(HTML_ELEMENT_CHECKBOX, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_ARIA_LANDMARK:
        performWebNavigationKeyCombo(HTML_ELEMENT_ARIA_LANDMARK, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_ARIA_LANDMARK:
        performWebNavigationKeyCombo(HTML_ELEMENT_ARIA_LANDMARK, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_EDIT_FIELD:
        performWebNavigationKeyCombo(HTML_ELEMENT_EDIT_FIELD, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_EDIT_FIELD:
        performWebNavigationKeyCombo(HTML_ELEMENT_EDIT_FIELD, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_FOCUSABLE_ITEM:
        performWebNavigationKeyCombo(HTML_ELEMENT_FOCUSABLE_ITEM, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_FOCUSABLE_ITEM:
        performWebNavigationKeyCombo(HTML_ELEMENT_FOCUSABLE_ITEM, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_1:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_1, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_1:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_1, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_2:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_2, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_2:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_2, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_3:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_3, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_3:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_3, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_4:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_4, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_4:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_4, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_5:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_5, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_5:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_5, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_6:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_6, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_6:
        performWebNavigationKeyCombo(HTML_ELEMENT_HEADING_6, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LINK:
        performWebNavigationKeyCombo(HTML_ELEMENT_LINK, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LINK:
        performWebNavigationKeyCombo(HTML_ELEMENT_LINK, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CONTROL:
        performWebNavigationKeyCombo(HTML_ELEMENT_CONTROL, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CONTROL:
        performWebNavigationKeyCombo(HTML_ELEMENT_CONTROL, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_GRAPHIC:
        performWebNavigationKeyCombo(HTML_ELEMENT_GRAPHIC, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_GRAPHIC:
        performWebNavigationKeyCombo(HTML_ELEMENT_GRAPHIC, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST_ITEM:
        performWebNavigationKeyCombo(HTML_ELEMENT_LIST_ITEM, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST_ITEM:
        performWebNavigationKeyCombo(HTML_ELEMENT_LIST_ITEM, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST:
        performWebNavigationKeyCombo(HTML_ELEMENT_LIST, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST:
        performWebNavigationKeyCombo(HTML_ELEMENT_LIST, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_TABLE:
        performWebNavigationKeyCombo(HTML_ELEMENT_TABLE, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_TABLE:
        performWebNavigationKeyCombo(HTML_ELEMENT_TABLE, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_COMBOBOX:
        performWebNavigationKeyCombo(HTML_ELEMENT_COMBOBOX, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_COMBOBOX:
        performWebNavigationKeyCombo(HTML_ELEMENT_COMBOBOX, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_WINDOW:
        navigateToNextOrPreviousWindow(
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            WINDOW_TYPE_SYSTEM | WINDOW_TYPE_APPLICATION,
            FOCUS_STRATEGY_RESUME_FOCUS,
            true /* useInputFocusAsPivot */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WINDOW:
        navigateToNextOrPreviousWindow(
            TraversalStrategy.SEARCH_FOCUS_BACKWARD,
            WINDOW_TYPE_SYSTEM | WINDOW_TYPE_APPLICATION,
            FOCUS_STRATEGY_RESUME_FOCUS,
            true /* useInputFocusAsPivot */,
            InputModeManager.INPUT_MODE_KEYBOARD,
            eventId);
        return true;
      default: // fall out
    }

    return false;
  }

  private void alertWebNavigationHitEdge(String htmlElement, boolean forward, EventId eventId) {
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
      default: // fall out
    }
    mService
        .getSpeechController()
        .speak(
            mService.getString(resId, displayName), /* Text */
            SpeechController.QUEUE_MODE_INTERRUPT, /* QueueMode */
            FeedbackItem.FLAG_FORCED_FEEDBACK, /* Flags */
            null, /* SpeechParams */
            eventId);
  }

  private boolean performWebNavigationKeyCombo(
      String htmlElement, boolean forward, EventId eventId) {
    if (isSupportedHtmlElement(htmlElement)) {
      boolean navigationSucceeded =
          forward
              ? nextHtmlElement(htmlElement, InputModeManager.INPUT_MODE_KEYBOARD, eventId)
              : previousHtmlElement(htmlElement, InputModeManager.INPUT_MODE_KEYBOARD, eventId);
      if (!navigationSucceeded) {
        alertWebNavigationHitEdge(htmlElement, forward, eventId);
      }
      return navigationSucceeded;
    }

    mService
        .getSpeechController()
        .speak(
            mService.getString(R.string.keycombo_announce_shortcut_not_supported), /* Text */
            SpeechController.QUEUE_MODE_INTERRUPT, /* QueueMode */
            FeedbackItem.FLAG_NO_HISTORY | FeedbackItem.FLAG_FORCED_FEEDBACK, /* Flags */
            null, /* SpeechParams*/
            eventId);

    return false;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_CURSOR_CONTROLLER;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    int eventType = event.getEventType();
    if (eventType == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      final AccessibilityNodeInfo node = event.getSource();
      if (node == null) {
        LogUtils.log(this, Log.WARN, "TYPE_VIEW_ACCESSIBILITY_FOCUSED event without a source.");
        return;
      }

      // When a new view gets focus, clear the state of the granularity
      // manager if this event came from a different node than the locked
      // node but from the same window.
      final AccessibilityNodeInfoCompat nodeCompat = AccessibilityNodeInfoUtils.toCompat(node);

      // recenter magnifier
      if (nodeCompat != null) {
        recenterMagnifier(nodeCompat);
      }
      // Do not clear locked node or retain granularity when TalkBack is in "read from top"
      // or "read from next item" mode.
      // CursorGranularityManager.onNodeFocused() tries to clear text selection on focused
      // node, which might trigger TYPE_VIEW_TEXT_SELECTION_CHANGED event and interrupt "read
      // from top".
      if (!mService.getFullScreenReadController().isActive()) {
        mGranularityManager.onNodeFocused(event, nodeCompat, eventId);
      }
      if (mSwitchNodeWithGranularityDirection
          == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) {
        mGranularityManager.navigate(
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, eventId);
      } else if (mSwitchNodeWithGranularityDirection
          == AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) {
        mGranularityManager.startFromLastNode();
        mGranularityManager.navigate(
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, eventId);
      }
      mSwitchNodeWithGranularityDirection = 0;
      nodeCompat.recycle();
      mReachedEdge = false;
      mGranularityNavigationReachedEdge = false;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
      final AccessibilityNodeInfo node = event.getSource();
      if (node != null) {
        final AccessibilityNodeInfoCompat nodeCompat = AccessibilityNodeInfoUtils.toCompat(node);

        // recenter magnifier
        if (nodeCompat != null) {
          recenterMagnifier(nodeCompat);
        }

        // Note: we also need to check ROLE_EDIT_TEXT for JB MR1 and lower and for
        // Chrome/WebView 51 and lower. We should check isEditable() first because it's
        // more semantically appropriate for what we want.
        maybeUpdateLastEditable(nodeCompat);
      }
    } else if (mIsWindowNavigationAvailable
        && eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      // Remove last focused nodes of non-existing windows.
      Set<Integer> windowIdsToBeRemoved = new HashSet(mLastFocusedNodeMap.keySet());
      for (AccessibilityWindowInfo window : mService.getWindows()) {
        windowIdsToBeRemoved.remove(window.getId());
      }
      for (Integer windowIdToBeRemoved : windowIdsToBeRemoved) {
        AccessibilityNodeInfoCompat removedNode = mLastFocusedNodeMap.remove(windowIdToBeRemoved);
        if (removedNode != null) {
          removedNode.recycle();
        }
      }
    }
  }

  /**
   * Recenter magnifier on focused sourceNode. Aligns magnifier with sourceNode upper-left corner
   * (for left-to-right languages; but for right-to-left languages focus goes to upper-right
   * corner).
   *
   * @param sourceNode The node that TalkBack is focusing on.
   */
  @TargetApi(Build.VERSION_CODES.N)
  protected void recenterMagnifier(AccessibilityNodeInfoCompat sourceNode) {
    if (!BuildVersionUtils.isAtLeastN()) {
      return;
    }
    Rect sourceNodeBounds = new Rect();
    sourceNode.getBoundsInScreen(sourceNodeBounds);
    AccessibilityService.MagnificationController magControl = mService.getMagnificationController();
    Region magRegion = magControl.getMagnificationRegion();
    Rect magBounds = (magRegion == null) ? null : magRegion.getBounds();
    float magScale = magControl.getScale();
    float halfMagWidth = (float) magBounds.width() / (2f * magScale); // use screen coordinates
    float halfMagHeight = (float) magBounds.height() / (2f * magScale);
    float margin = 5.0f;
    float newMagCenterY = sourceNodeBounds.top + halfMagHeight - margin;
    float newMagCenterX = -1;
    if (isLeftToRight(sourceNode)) {
      // Align upper left corner of sourceNode and magnifier
      newMagCenterX = sourceNodeBounds.left + halfMagWidth - margin;
    } else {
      // Align upper right corner of sourceNode and magnifier
      newMagCenterX = sourceNodeBounds.right - halfMagWidth + margin;
    }
    // Require that magnifier center is within magnifiable region
    float tolerance = 1.0f;
    newMagCenterX = Math.max(newMagCenterX, magBounds.left + tolerance);
    newMagCenterX = Math.min(newMagCenterX, magBounds.right - tolerance);
    newMagCenterY = Math.max(newMagCenterY, magBounds.top + tolerance);
    newMagCenterY = Math.min(newMagCenterY, magBounds.bottom - tolerance);
    magControl.setCenter(newMagCenterX, newMagCenterY, true /* animate */);
  }

  /**
   * Decides whether a node's content starts at left or right side.
   *
   * @param node The node whose direction we want.
   * @return {@code true} if node direction is left-to-right or unknown.
   */
  public static boolean isLeftToRight(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return true;
    }
    CharSequence text = node.getText();
    if (text == null || TextUtils.isEmpty(text)) {
      return true;
    }
    int direction = Character.getDirectionality(text.charAt(0));
    return (direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT
        && direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC);
  }

  private void rememberLastFocusedNode(AccessibilityNodeInfoCompat lastFocusedNode) {
    if (!mIsWindowNavigationAvailable) {
      return;
    }

    AccessibilityNodeInfoCompat oldNode =
        mLastFocusedNodeMap.put(
            lastFocusedNode.getWindowId(), AccessibilityNodeInfoCompat.obtain(lastFocusedNode));
    if (oldNode != null) {
      oldNode.recycle();
    }
  }

  /**
   * Attempts to navigate the node using HTML navigation.
   *
   * @param node to navigate on
   * @param direction The direction to navigate, one of {@link TraversalStrategy.SearchDirection}.
   * @return {@code true} if navigation succeeded.
   */
  private boolean attemptHtmlNavigation(
      AccessibilityNodeInfoCompat node,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {
    @TraversalStrategy.SearchDirection
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            direction, WindowManager.isScreenLayoutRTL(mService));
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return WebInterfaceUtils.performNavigationToHtmlElementAction(
          node, WebInterfaceUtils.DIRECTION_FORWARD, "", eventId);
    } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return WebInterfaceUtils.performNavigationToHtmlElementAction(
          node, WebInterfaceUtils.DIRECTION_BACKWARD, "", eventId);
    } else {
      return false;
    }
  }

  private void setLastEditable(AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoUtils.recycleNodes(mLastEditable);
    mLastEditable = node;
    boolean lastTextEditIsPassword = (mLastEditable != null) && mLastEditable.isPassword();
    mGlobalVariables.setLastTextEditIsPassword(lastTextEditIsPassword);
  }
}
