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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.HashSet;
import java.util.List;

/**
 * A data structure to cache information during window transition.
 *
 * <p><strong>Window transition</strong> happens when the user opens/closes an application, dialog,
 * soft keyboard, etc. An accessibility service might receive <b>multiple</b> {@link
 * AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} events during the transition. This class caches two
 * kinds of information from the events:
 *
 * <ul>
 *   <li>ID of window being changed, retrieved by {@link AccessibilityEvent#getWindowId()}
 *   <li>Title of window, retrieved from node tree or {@link AccessibilityEvent#getText()}.
 * </ul>
 */
public class WindowTransitionInfo {
  /**
   * Caches window title retrieved from TYPE_WINDOW_STATE_CHANGED events during the window
   * transition.
   */
  private SparseArray<CharSequence> cachedWindowTitlesFromEvents = new SparseArray<>();

  /** Caches ID of windows changed during the transition. */
  private HashSet<Integer> stateChangedWindows = new HashSet<>();

  /**
   * Caches window title and ID from {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} or {@link
   * AccessibilityEvent#TYPE_WINDOWS_CHANGED} event.
   *
   * @param sourceWindow source window of the event matched from {@link
   *     android.accessibilityservice.AccessibilityService#getWindows()}. <b>Note:</b> For alert
   *     dialogs on pre-O devices, if we try to fetch the windowInfo right after we receive the
   *     event, the windowInfo might be null.
   * @param event An {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED} or {@link
   *     AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} event with valid window Id.
   */
  public void updateTransitionInfoFromEvent(
      @Nullable AccessibilityWindowInfo sourceWindow, AccessibilityEvent event) {
    int windowId = event.getWindowId();
    if (windowId == -1) {
      return;
    }
    stateChangedWindows.add(windowId);
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      cacheWindowTitleFromEvent(sourceWindow, event);
    }
  }

  /** Returns whether the window with given windowId is changed during transition. */
  public boolean isWindowStateRecentlyChanged(int windowId) {
    return stateChangedWindows.contains(windowId);
  }

  /** Returns the window title retrieved from accessibility events. */
  public CharSequence getWindowTitle(int windowId) {
    return cachedWindowTitlesFromEvents.get(windowId);
  }

  /** Clears all the cached information during the transition. */
  public void clear() {
    cachedWindowTitlesFromEvents.clear();
    stateChangedWindows.clear();
  }

  SparseArray<CharSequence> getWindowTitleMap() {
    return cachedWindowTitlesFromEvents;
  }

  private void cacheWindowTitleFromEvent(
      @Nullable AccessibilityWindowInfo sourceWindow, AccessibilityEvent event) {
    if (shouldIgnorePaneChanges(event)) {
      return;
    }

    final CharSequence windowTitle;
    final @RoleName int eventSourceRole = Role.getSourceRole(event);

    // For special layouts, assign window title even if event title is empty.
    // It's ok to hard code the title since it's used as an identifier.
    switch (eventSourceRole) {
      case Role.ROLE_DRAWER_LAYOUT:
        windowTitle = getEventTitle(sourceWindow, event) + " Menu";
        break;
      case Role.ROLE_ICON_MENU:
        windowTitle = "Options";
        break;
      case Role.ROLE_SLIDING_DRAWER:
        windowTitle = "Sliding drawer";
        break;
      default:
        windowTitle = getEventTitle(sourceWindow, event);
        break;
    }

    if (!TextUtils.isEmpty(windowTitle)) {
      cachedWindowTitlesFromEvents.put(event.getWindowId(), windowTitle);
    }
  }

  private static boolean shouldIgnorePaneChanges(AccessibilityEvent event) {
    if (!BuildVersionUtils.isAtLeastP()
        || (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
      return false;
    }
    if (event.getContentChangeTypes() == AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) {
      // The event text is the title of pane being removed.
      return true;
    }

    // . We need to differentiate between pane changes with CONTENT_CHANGE_TYPE_UNDEFINED,
    // and events filed by getDecorView().sendAccessibilityEvent(WINDOW_STATE_CHANGE). The only way
    // I can find is to check whether the source node is null.
    return (event.getContentChangeTypes() == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED)
        && !AccessibilityEventUtils.hasSourceNode(event);
  }

  private static CharSequence getEventTitle(
      @Nullable AccessibilityWindowInfo sourceWindow, AccessibilityEvent event) {
    // Event texts from IME and system windows are used to announce other information,
    // thus we don't get event title from these windows.
    if ((sourceWindow != null) && isSystemOrImeWindow(sourceWindow)) {
      return null;
    }

    List<CharSequence> texts = event.getText();
    if (texts == null || texts.size() == 0) {
      return null;
    }

    if (texts.size() == 1) {
      // TODO: The text might be invalid in some known use cases, we need strong rule to
      // eliminate fake title.
      return texts.get(0);
    } else {
      // If the event text contains multiple items, which is a collection of node texts gathered
      // from the node tree, instead of directly getting the first item as title, we prefer to
      // manually search for and validate window title from node tree. There is a special corner
      // case on pre-O devices, when an alert dialog shows up, the TYPE_WINDOW_STATE_CHANGED event
      // is filed, but at that time the AccessibilityWindowInfo is not added to window list yet.
      // In this case we simply use the first text as the window title.
      if (sourceWindow == null) {
        return texts.get(0);
      } else {
        return getWindowTitleFromNodeTree(sourceWindow);
      }
    }
  }

  private static boolean isSystemOrImeWindow(AccessibilityWindowInfo window) {
    return (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        || (window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM);
  }

  @Nullable
  private static CharSequence getWindowTitleFromNodeTree(AccessibilityWindowInfo window) {
    // Nodes to be recycled.
    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat windowTitleNode = null;

    try {
      root = AccessibilityNodeInfoUtils.toCompat(AccessibilityWindowInfoUtils.getRoot(window));
      if (root == null) {
        return null;
      }

      windowTitleNode = findFirstNodeWithText(root);
      if (windowTitleNode == null) {
        return null;
      }

      // TODO: Revisit the logic how we validate window title node.
      boolean isValidWindowTitleNode =
          !AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(
              windowTitleNode, AccessibilityNodeInfoUtils.FILTER_ILLEGAL_TITLE_NODE_ANCESTOR);

      return isValidWindowTitleNode ? windowTitleNode.getText() : null;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, windowTitleNode);
    }
  }

  /** Returns the first node with non-empty text in the node tree. */
  private static AccessibilityNodeInfoCompat findFirstNodeWithText(
      AccessibilityNodeInfoCompat root) {
    TraversalStrategy traversalStrategy = null;
    try {
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(root, TraversalStrategy.SEARCH_FOCUS_FORWARD);
      return TraversalStrategyUtils.searchFocus(
          traversalStrategy,
          root,
          TraversalStrategy.SEARCH_FOCUS_FORWARD,
          AccessibilityNodeInfoUtils.FILTER_HAS_TEXT);
    } finally {
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }
}
