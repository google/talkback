/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A class to manage accessibility focus. */
class FocusManagerInternal implements AccessibilityEventListener {

  /** Event types that are handled by FocusManagerInternal. */
  private static final int MASK_EVENTS_HANDLED_BY_FOCUS_MANAGER_INTERNAL =
      AccessibilityEvent.TYPE_WINDOWS_CHANGED;

  private final AccessibilityService mService;

  private final AccessibilityManager mAccessibilityManager;

  /** The last input-focused editable node. */
  private AccessibilityNodeInfoCompat mLastEditable;

  /** Whether we should drive input focus instead of accessibility focus where possible. */
  private final boolean mControlInputFocus;

  /** Whether the current device supports navigating between multiple windows. */
  private final boolean mIsWindowNavigationAvailable;

  /** Map of window id to last-focused-node. Used to restore focus when closing popup window. */
  private final Map<Integer, AccessibilityNodeInfoCompat> mLastFocusedNodeMap = new HashMap<>();
  // TODO: Investigate whether this is redundant with {@code TalkBackService.mSavedNode}.

  public FocusManagerInternal(AccessibilityService service) {
    mService = service;
    mAccessibilityManager =
        (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);

    boolean isTv = FormFactorUtils.getInstance(service).isTv();
    mControlInputFocus = isTv;
    mIsWindowNavigationAvailable = BuildVersionUtils.isAtLeastLMR1() && !isTv;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_FOCUS_MANAGER_INTERNAL;
  }

  // TODO: This method needs to move out of this file.
  // This is related to maintaing the history.
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!mAccessibilityManager.isTouchExplorationEnabled()) {
      // Don't manage focus when touch exploration is disabled.
      return;
    }
    int eventType = event.getEventType();
    if (mIsWindowNavigationAvailable && eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
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
   * Tries to set accessibility focus on the given node. It's used by {@link FocusProcessor}s to set
   * accessibility focus.
   *
   * <p>This method attempts to focus the node only when the node is not accessibility focus or when
   * {@code forceRefocusIfAlreadyFocused} is {@code true}.
   *
   * <p><strong>Note: </strong> Caller is responsible to recycle the node.
   *
   * @param node Node to be focused.
   * @param forceRefocusIfAlreadyFocused Whether we should perform ACTION_ACCESSIBILITY_FOCUS if the
   *     node is already accessibility focused.
   * @param eventId The EventId for performance tracking.
   * @return Whether the node is already accessibility focused or we successfully put accessibility
   *     focus on the node.
   */
  boolean setAccessibilityFocus(
      AccessibilityNodeInfoCompat node,
      boolean forceRefocusIfAlreadyFocused,
      FocusActionInfo focusActionInfo,
      EventId eventId) {
    if (!forceRefocusIfAlreadyFocused && node.isAccessibilityFocused()) {
      return true;
    }

    // Accessibility focus follows input focus on TVs, we want to set both simultaneously,
    // so we change the input focus if possible and sync a11y focus to input focus later.
    if (mControlInputFocus && node.isFocusable() && !node.isFocused()) {
      // TODO: Similar to ScrollController, we should implement an InputFocusController to
      // invoke callback when INPUT_FOCUS event is received, and propagate the FocusActionInfo when
      // we finally sync a11y focus to input focus.
      if (PerformActionUtils.performAction(
          node, AccessibilityNodeInfoCompat.ACTION_FOCUS, eventId)) {
        return true;
      }
    }
    long currentTime = SystemClock.uptimeMillis();
    boolean result =
        PerformActionUtils.performAction(
            node, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
    if (result) {
      // AccessibilityFocusActionHistory makes copy of the node, no need to obtain() here.
      AccessibilityFocusActionHistory.getInstance()
          .onAccessibilityFocusAction(node, focusActionInfo, currentTime);
    }
    return result;
  }

  /**
   * Returns the last accessibility focus.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the node.
   */
  @Nullable
  AccessibilityNodeInfoCompat getLastAccessibilityFocus() {
    FocusActionRecord record =
        AccessibilityFocusActionHistory.getInstance().getLastFocusActionRecord();
    if (record == null) {
      return null;
    }
    return record.getFocusedNode();
  }

  /**
   * Returns the current accessibility focus on screen.
   *
   * <p><strong>Note: </strong> Caller is responsible to recycle the node.
   */
  AccessibilityNodeInfoCompat getAccessibilityFocus() {
    return getAccessibilityFocus(
        /* rootCompat= */ null,
        /* returnRootIfA11yFocusIsNull= */ false,
        /* returnInputFocusedEditableNodeIfA11yFocusIsNull= */ false);
  }

  // There are several ways in which getAccessibilityFocus is called from various modules.
  // The 4 available methods are: getAccessibilityFocusedOrRootNode(),
  // getAccessibilityFocusedOrInputFocusedEditableNode(), getAccessibilityFocus(),
  // getAccessibilityFocus(AccessibilityNodeInfoCompat rootCompat). All these should be places
  // in the A11yFocusManager which is exposed to all the other modules. A11yFocusManager
  // will then eventually call getAccessibilityFocus in this file with correct parameters.

  /**
   * Returns the node or the root from the active window or the given root that has accessibility
   * focus or input focus depending on the parameters.
   *
   * <p><strong>Note:</strong>
   *
   * <ul>
   *   <li>The client is responsible for recycling the resulting node.
   *   <li>The returned node might not pass {@link
   *       AccessibilityNodeInfoUtils#shouldFocusNode(AccessibilityNodeInfoCompat)}, the caller
   *       should validate the result if needed.
   * </ul>
   *
   * @return the node or the root in the active window that has accessibility focus or input focus.
   */
  // TODO : Check if the isVisible check can be completely removed from this method
  // TODO: There are two approaches to get accessibility focused node:
  // 1. Track TYPE_VIEW_ACCESSIBILITY_FOCUSED and TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED events and
  // cache the source node from the event.
  // 2. Search down in the node tree to find the focused node.
  // The first approach is light-weight but we need to test its reliability.
  // The second approach is slower because it'll invoke IPC method.
  // For now we use the second approach, we should probably think about the first approach in the
  // future.

  AccessibilityNodeInfoCompat getAccessibilityFocus(
      @Nullable AccessibilityNodeInfoCompat rootCompat,
      boolean returnRootIfA11yFocusIsNull,
      boolean returnInputFocusedEditableNodeIfA11yFocusIsNull) {
    // First, see if we've already placed accessibility focus.
    AccessibilityNodeInfoCompat root = rootCompat;
    if (rootCompat == null) {
      root = AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
    }
    if (root == null) {
      return null;
    }
    AccessibilityNodeInfoCompat focusedNode =
        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

    if (returnRootIfA11yFocusIsNull
        && (focusedNode == null || !AccessibilityNodeInfoUtils.isVisible(focusedNode))) {
      // TODO: If there's no focused node, we should either mimic following
      // focus from new window or try to be smart for things like list views.
      return root;
    }
    if (returnInputFocusedEditableNodeIfA11yFocusIsNull
        && (focusedNode == null || !AccessibilityNodeInfoUtils.isVisible(focusedNode))) {
      // TODO: If there's no focused node, we should either mimic following
      // focus from new window or try to be smart for things like list views.
      AccessibilityNodeInfoCompat inputFocusedNode = getInputFocusedNode();
      if (inputFocusedNode != null
          && inputFocusedNode.isFocused()
          && inputFocusedNode.isEditable()) {
        focusedNode = inputFocusedNode;
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

  // TODO: This method needs to move out of this file.
  // This is related to maintaining the history.
  public void setLastEditableNode(AccessibilityNodeInfoCompat node) {
    if (mLastEditable != null) {
      AccessibilityNodeInfoUtils.recycleNodes(mLastEditable);
    }
    mLastEditable = node;
  }

  /**
   * Clears accessibility focus on screen.
   *
   * @return {@code true} if accessibility focus exists and successfully perform {@link
   *     AccessibilityNodeInfo#ACTION_CLEAR_ACCESSIBILITY_FOCUS} on the focused node.
   */
  boolean clearAccessibilityFocus(EventId eventId) {
    AccessibilityNodeInfoCompat currentNode = getAccessibilityFocus();
    try {
      return clearAccessibilityFocus(currentNode, eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentNode);
    }
  }

  /**
   * Clears accessibility focus on the given node.
   *
   * <p><strong>Note: </strong> Caller is responsible to recycle the node.
   *
   * @return {@code true} if successfully perform {@link
   *     AccessibilityNodeInfo#ACTION_CLEAR_ACCESSIBILITY_FOCUS} on the given node.
   */
  boolean clearAccessibilityFocus(AccessibilityNodeInfoCompat currentNode, EventId eventId) {
    return PerformActionUtils.performAction(
        currentNode, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId);
  }

  // TODO: This method needs to move out of this file.
  // This is related to maintaining the history.
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
}
