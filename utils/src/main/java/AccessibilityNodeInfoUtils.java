/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo objects. NOTE: This
 * class only recycles unused nodes that were collected internally. Any node passed into or returned
 * from a public method is retained and TalkBack should recycle it when appropriate.
 */
public class AccessibilityNodeInfoUtils {
  /**
   * Class for Samsung's TouchWiz implementation of AdapterView. May be {@code null} on non-Samsung
   * devices.
   */
  private static final Class<?> CLASS_TOUCHWIZ_TWADAPTERVIEW =
      CompatUtils.getClass("com.sec.android.touchwiz.widget.TwAdapterView");

  /** Key to get accessibility web hints from the web */
  private static final String HINT_TEXT_KEY = "AccessibilityNodeInfo.hint";

  private static final Pattern RESOURCE_NAME_SPLIT_PATTERN = Pattern.compile(":id/");

  /**
   * A wrapper over AccessibilityNodeInfoCompat constructor, so that we can add any desired error
   * checking and memory management.
   *
   * @param nodeInfo The AccessibilityNodeInfo which will be wrapped. The caller retains the
   *     responsibility to recycle nodeInfo.
   * @return Encapsulating AccessibilityNodeInfoCompat, or null if input is invalid.
   */
  public static @Nullable AccessibilityNodeInfoCompat toCompat(
      @Nullable AccessibilityNodeInfo nodeInfo) {
    if (nodeInfo == null) {
      return null;
    }
    return new AccessibilityNodeInfoCompat(nodeInfo);
  }

  private static final int SYSTEM_ACTION_MAX = 0x01FFFFFF;

  public static final int WINDOW_TYPE_NONE = -1;
  public static final int WINDOW_TYPE_PICTURE_IN_PICTURE = 1000;

  /**
   * Filter for scrollable items. One of the following must be true:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isScrollable()} returns {@code true}
   *   <li>{@link AccessibilityNodeInfoCompat#getActions()} supports {@link
   *       AccessibilityNodeInfoCompat#ACTION_SCROLL_FORWARD}
   *   <li>{@link AccessibilityNodeInfoCompat#getActions()} supports {@link
   *       AccessibilityNodeInfoCompat#ACTION_SCROLL_BACKWARD}
   * </ul>
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SCROLLABLE =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return isScrollable(node);
        }
      };

  /**
   * Filter for items that should receive accessibility focus. Equivalent to calling {@link
   * #shouldFocusNode(AccessibilityNodeInfoCompat)}.
   *
   * <p><strong>Note:</strong> Use {@link #FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW} has a filter for
   * {@link AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events.
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SHOULD_FOCUS =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null && shouldFocusNode(node);
        }
      };

  /**
   * Filter for items that should receive accessibility focus from {@link
   * AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events. WebView container node should not be focus
   * for hover enter actions.
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW =
      FILTER_SHOULD_FOCUS.and(
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return Role.getRole(node) != Role.ROLE_WEB_VIEW;
            }
          });

  /**
   * This filter accepts scrollable views that break if we place accessibility focus on their child
   * items. Instead, we should just place focus on the entire scrollable view. Note: Only include
   * Android TV views that cannot be updated (i.e. part of a bundled app).
   */
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_BROKEN_LISTS_TV_M =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (node == null) {
            return false;
          }

          CharSequence viewId = node.getViewIdResourceName();
          return "com.android.tv.settings:id/setup_scroll_list".equals(viewId)
              || "com.google.android.gsf.notouch:id/setup_scroll_list".equals(viewId)
              || "com.android.vending:id/setup_scroll_list".equals(viewId);
        }
      };

  public static boolean hasApplicationWebRole(AccessibilityNodeInfoCompat node) {
    return node != null && node.getExtras() != null
        && node.getExtras().containsKey("AccessibilityNodeInfo.chromeRole")
        && node.getExtras().get("AccessibilityNodeInfo.chromeRole").equals("application");
  }

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_IN_WEB_APPLICATION =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return hasApplicationWebRole(node);
        }
      };

  /**
   * Returns true if |node| has role=application, i.e. |node| has JavaScript
   * that handles key events.
   */
  public static boolean isWebApplication(AccessibilityNodeInfoCompat node) {
    // When a WebView-like view (an actual WebView or a browser) has focus:
    // Check the web content's accessibility tree's first node.
    // If that node wants raw key event, instead of first "tabbing" the green
    // rect to it, skip ahead and let the web app directly decide where to go.
    boolean firstWebNode = WebInterfaceUtils.supportsWebActions(node)
        && !WebInterfaceUtils.supportsWebActions(node.getParent());
    boolean firstWebNodeWantsKeyEvents = firstWebNode
        && node.getChildCount() > 0
        && hasApplicationWebRole(node.getChild(0));

    return firstWebNodeWantsKeyEvents
        || AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_IN_WEB_APPLICATION) != null;
  }

  private AccessibilityNodeInfoUtils() {
    // This class is not instantiable.
  }

  /**
   * Gets the text of a <code>node</code> by returning the content description (if available) or by
   * returning the text.
   *
   * @param node The node.
   * @return The node text.
   */
  public static CharSequence getNodeText(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    // Prefer content description over text.
    // TODO: Why are we checking the trimmed length?
    final CharSequence contentDescription = node.getContentDescription();
    if (!TextUtils.isEmpty(contentDescription)
        && (TextUtils.getTrimmedLength(contentDescription) > 0)) {
      return contentDescription;
    }

    final CharSequence text = node.getText();
    if (!TextUtils.isEmpty(text) && (TextUtils.getTrimmedLength(text) > 0)) {
      return text;
    }

    return null;
  }

  /**
   * Gets the textual representation of the view ID that can be used when no custom label is
   * available. For better readability/listenability, the "_" characters are replaced with spaces.
   *
   * @param node The node
   * @return Readable text of the view Id
   */
  public static String getViewIdText(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    String resourceName = node.getViewIdResourceName();
    if (resourceName == null) {
      return null;
    }

    String[] parsedResourceName = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2);
    if (parsedResourceName.length != 2
        || TextUtils.isEmpty(parsedResourceName[0])
        || TextUtils.isEmpty(parsedResourceName[1])) {
      return null;
    }

    return parsedResourceName[1].replace('_', ' '); // readable View ID text
  }

  public static List<AccessibilityActionCompat> getCustomActions(AccessibilityNodeInfoCompat node) {
    List<AccessibilityActionCompat> customActions = new ArrayList<>();
    for (AccessibilityActionCompat action : node.getActionList()) {
      if (isCustomAction(action)) {
        // We don't use custom actions that doesn't have a label
        if (!TextUtils.isEmpty(action.getLabel())) {
          customActions.add(action);
        }
      }
    }

    return customActions;
  }

  public static boolean isCustomAction(AccessibilityActionCompat action) {
    return action.getId() > SYSTEM_ACTION_MAX;
  }

  /** Returns the root node of the tree containing {@code node}. */
  public static AccessibilityNodeInfoCompat getRoot(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    AccessibilityNodeInfoCompat current = null;
    AccessibilityNodeInfoCompat parent = AccessibilityNodeInfoCompat.obtain(node);

    try {
      do {
        if (current != null) {
          if (visitedNodes.contains(current)) {
            current.recycle();
            parent.recycle();
            return null;
          }
          visitedNodes.add(current);
        }

        current = parent;
        parent = current.getParent();
      } while (parent != null);
    } finally {
      recycleNodes(visitedNodes);
    }

    return current;
  }

  /** Returns the type of the window containing {@code nodeCompat}. */
  public static int getWindowType(AccessibilityNodeInfoCompat nodeCompat) {
    if (nodeCompat == null) {
      return WINDOW_TYPE_NONE;
    }

    AccessibilityWindowInfoCompat windowInfoCompat = nodeCompat.getWindow();
    if (windowInfoCompat == null) {
      return WINDOW_TYPE_NONE;
    }

    if (isPictureInPicture(nodeCompat)) {
      return WINDOW_TYPE_PICTURE_IN_PICTURE;
    }

    int windowType = windowInfoCompat.getType();
    windowInfoCompat.recycle();
    return windowType;
  }

  /**
   * Returns whether a node should receive focus from focus traversal or touch exploration. One of
   * the following must be true:
   *
   * <ul>
   *   <li>The node is actionable (see {@link
   *       #isActionableForAccessibility(AccessibilityNodeInfoCompat)})
   *   <li>The node is a top-level list item (see {@link
   *       #isTopLevelScrollItem(AccessibilityNodeInfoCompat)})
   * </ul>
   *
   * @param node The node to check.
   * @return {@code true} of the node is accessibility focusable.
   */
  public static boolean isAccessibilityFocusable(AccessibilityNodeInfoCompat node) {
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      return isAccessibilityFocusableInternal(node, null, visitedNodes);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
    }
  }

  private static boolean isAccessibilityFocusableInternal(
      AccessibilityNodeInfoCompat node,
      Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      Set<AccessibilityNodeInfoCompat> visitedNodes) {
    if (node == null) {
      return false;
    }

    // Never focus invisible nodes.
    if (!isVisible(node)) {
      return false;
    }

    // Always focus "actionable" nodes.
    if (isActionableForAccessibility(node)) {
      return true;
    }

    return isTopLevelScrollItem(node) && isSpeakingNode(node, speakingNodeCache, visitedNodes);
  }

  /**
   * Returns whether a node should receive accessibility focus from navigation. This method should
   * never be called recursively, since it traverses up the parent hierarchy on every call.
   *
   * @see #findFocusFromHover(AccessibilityNodeInfoCompat)
   */
  public static boolean shouldFocusNode(AccessibilityNodeInfoCompat node) {
    return shouldFocusNode(node, null, true);
  }

  public static boolean shouldFocusNode(
      final AccessibilityNodeInfoCompat node,
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache) {
    return shouldFocusNode(node, speakingNodeCache, true);
  }

  public static boolean shouldFocusNode(
      final AccessibilityNodeInfoCompat node,
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      boolean checkChildren) {
    if (node == null) {
      return false;
    }

    // Inside views that support web navigation, we delegate focus to the view itself and
    // assume that it navigates to and focuses the correct elements.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      // In history, we loosen the "visibility" check for web element: A web node can be focused
      // even if it's not visibleToUser(). However we should hold the baseline that if the WebView
      // container is not visible, we should not focus on its descendants.
      AccessibilityNodeInfoCompat webViewContainer =
          WebInterfaceUtils.ascendToWebViewContainer(node);
      try {
        return webViewContainer != null && webViewContainer.isVisibleToUser();
      } finally {
        recycleNodes(webViewContainer);
      }
    }

    if (!isVisible(node)) {
      LogUtils.log(
          AccessibilityNodeInfoUtils.class, Log.VERBOSE, "Don't focus, node is not visible");
      return false;
    }

    if (isPictureInPicture(node)) {
      // For picture-in-picture, allow focusing the root node, and any app controls inside the
      // pic-in-pic window.
      return true;
    } else {
      // Reject all non-leaf nodes that have the same bounds as the window.
      if (areBoundsIdenticalToWindow(node) && node.getChildCount() > 0) {
        LogUtils.log(
            AccessibilityNodeInfoUtils.class,
            Log.VERBOSE,
            "Don't focus, node bounds are same as window root node bounds");
        return false;
      }
    }

    HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      boolean accessibilityFocusable =
          isAccessibilityFocusableInternal(node, speakingNodeCache, visitedNodes);

      if (!checkChildren) {
        // End of the line. Don't check children and don't allow any recursion.
        return accessibilityFocusable;
      }

      if (accessibilityFocusable) {
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        visitedNodes.clear();
        // TODO: This may still result in focusing non-speaking nodes, but it
        // won't prevent unlabeled buttons from receiving focus.
        if (!hasVisibleChildren(node)) {
          LogUtils.log(
              AccessibilityNodeInfoUtils.class,
              Log.VERBOSE,
              "Focus, node is focusable and has no visible children");
          return true;
        } else if (isSpeakingNode(node, speakingNodeCache, visitedNodes)) {
          LogUtils.log(
              AccessibilityNodeInfoUtils.class,
              Log.VERBOSE,
              "Focus, node is focusable and has something to speak");
          return true;
        } else {
          LogUtils.log(
              AccessibilityNodeInfoUtils.class,
              Log.VERBOSE,
              "Don't focus, node is focusable but has nothing to speak");
          return false;
        }
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
    }

    // If this node has no focusable ancestors, but it still has text,
    // then it should receive focus from navigation and be read aloud.
    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return shouldFocusNode(node, speakingNodeCache, false);
          }
        };

    if (!hasMatchingAncestor(node, filter) && hasText(node)) {
      LogUtils.log(
          AccessibilityNodeInfoUtils.class,
          Log.VERBOSE,
          "Focus, node has text and no focusable ancestors");
      return true;
    }

    LogUtils.log(
        AccessibilityNodeInfoUtils.class,
        Log.VERBOSE,
        "Don't focus, failed all focusability tests");
    return false;
  }

  public static boolean isPictureInPicture(AccessibilityNodeInfoCompat node) {
    return isPictureInPicture((AccessibilityNodeInfo) node.getInfo());
  }

  public static boolean isPictureInPicture(AccessibilityNodeInfo node) {
    return node != null && AccessibilityWindowInfoUtils.isPictureInPicture(node.getWindow());
  }

  /**
   * Returns the node that should receive focus from hover by starting from the touched node and
   * calling {@link #shouldFocusNode} at each level of the view hierarchy and exclude WebView
   * container node.
   */
  public static AccessibilityNodeInfoCompat findFocusFromHover(
      AccessibilityNodeInfoCompat touched) {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
        touched, FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW);
  }

  private static boolean isSpeakingNode(
      AccessibilityNodeInfoCompat node,
      Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      Set<AccessibilityNodeInfoCompat> visitedNodes) {
    if (speakingNodeCache != null && speakingNodeCache.containsKey(node)) {
      return speakingNodeCache.get(node);
    }

    boolean result = false;
    if (hasText(node)) {
      LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE, "Speaking, has text");
      result = true;
    } else if (node.isCheckable()) { // Special case for check boxes.
      LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE, "Speaking, is checkable");
      result = true;
    } else if (hasNonActionableSpeakingChildren(node, speakingNodeCache, visitedNodes)) {
      // Special case for containers with non-focusable content.
      LogUtils.log(
          AccessibilityNodeInfoUtils.class,
          Log.VERBOSE,
          "Speaking, has non-actionable speaking children");
      result = true;
    }

    if (speakingNodeCache != null) {
      speakingNodeCache.put(node, result);
    }

    return result;
  }

  private static boolean hasNonActionableSpeakingChildren(
      AccessibilityNodeInfoCompat node,
      Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      Set<AccessibilityNodeInfoCompat> visitedNodes) {
    final int childCount = node.getChildCount();

    AccessibilityNodeInfoCompat child;

    // Has non-actionable, speaking children?
    for (int i = 0; i < childCount; i++) {
      child = node.getChild(i);

      if (child == null) {
        LogUtils.log(
            AccessibilityNodeInfoUtils.class, Log.VERBOSE, "Child %d is null, skipping it", i);
        continue;
      }

      if (!visitedNodes.add(child)) {
        child.recycle();
        return false;
      }

      // Ignore invisible nodes.
      if (!isVisible(child)) {
        LogUtils.log(
            AccessibilityNodeInfoUtils.class, Log.VERBOSE, "Child %d is invisible, skipping it", i);
        continue;
      }

      // Ignore focusable nodes.
      if (isAccessibilityFocusableInternal(child, speakingNodeCache, visitedNodes)) {
        LogUtils.log(
            AccessibilityNodeInfoUtils.class, Log.VERBOSE, "Child %d is focusable, skipping it", i);
        continue;
      }

      // Recursively check non-focusable child nodes.
      if (isSpeakingNode(child, speakingNodeCache, visitedNodes)) {
        LogUtils.log(
            AccessibilityNodeInfoUtils.class,
            Log.VERBOSE,
            "Does have actionable speaking children (child %d)",
            i);
        return true;
      }
    }

    LogUtils.log(
        AccessibilityNodeInfoUtils.class,
        Log.VERBOSE,
        "Does not have non-actionable speaking children");
    return false;
  }

  private static boolean hasVisibleChildren(AccessibilityNodeInfoCompat node) {
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null) {
        try {
          if (child.isVisibleToUser()) {
            return true;
          }
        } finally {
          child.recycle();
        }
      }
    }

    return false;
  }

  public static int countVisibleChildren(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return 0;
    }
    int childCount = node.getChildCount();
    int childVisibleCount = 0;
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null) {
        try {
          if (child.isVisibleToUser()) {
            ++childVisibleCount;
          }
        } finally {
          child.recycle();
        }
      }
    }
    return childVisibleCount;
  }

  /**
   * Returns whether a node is actionable. That is, the node supports one of the following actions:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isClickable()}
   *   <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
   *   <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
   * </ul>
   *
   * This parities the system method View#isActionableForAccessibility(), which was added in
   * JellyBean.
   *
   * @param node The node to examine.
   * @return {@code true} if node is actionable.
   */
  public static boolean isActionableForAccessibility(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // Nodes that are clickable are always actionable.
    if (isClickable(node) || isLongClickable(node)) {
      return true;
    }

    if (node.isFocusable()) {
      return true;
    }

    if (WebInterfaceUtils.hasNativeWebContent(node)) {
      return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS);
    }

    return supportsAnyAction(
        node,
        AccessibilityNodeInfoCompat.ACTION_FOCUS,
        AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
  }

  public static boolean isSelfOrAncestorFocused(AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isAccessibilityFocused()
            || hasMatchingAncestor(
                node,
                new Filter<AccessibilityNodeInfoCompat>() {
                  @Override
                  public boolean accept(AccessibilityNodeInfoCompat node) {
                    return node.isAccessibilityFocused();
                  }
                }));
  }

  /**
   * Returns whether a node is clickable. That is, the node supports at least one of the following:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isClickable()}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_CLICK}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is clickable.
   */
  public static boolean isClickable(AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isClickable()
            || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK));
  }

  /**
   * Returns whether a node is long clickable. That is, the node supports at least one of the
   * following:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_LONG_CLICK}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is long clickable.
   */
  public static boolean isLongClickable(AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isLongClickable()
            || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK));
  }

  /**
   * Returns whether a node is expandable. That is, the node supports the following action:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_EXPAND}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is expandable.
   */
  public static boolean isExpandable(AccessibilityNodeInfoCompat node) {
    return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_EXPAND);
  }

  /**
   * Returns whether a node is collapsible. That is, the node supports the following action:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_COLLAPSE}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is collapsible.
   */
  public static boolean isCollapsible(AccessibilityNodeInfoCompat node) {
    return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
  }

  /**
   * Returns whether a node can be dismissed by the user. the node supports the following action:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_DISMISS}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is dismissible.
   */
  public static boolean isDismissible(AccessibilityNodeInfoCompat node) {
    return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_DISMISS);
  }

  /**
   * Returns whether a node is editable.
   *
   * @param node The node to examine.
   * @return {@code true} if node is editable.
   */
  public static boolean isEditable(AccessibilityNodeInfoCompat node) {
    return ((AccessibilityNodeInfo) node.getInfo()).isEditable();
  }

  /** Caller retains ownership of source node. */
  public static boolean isKeyboard(AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
    return isKeyboard(event, source.unwrap());
  }

  /** Caller retains ownership of source node. */
  public static boolean isKeyboard(AccessibilityEvent event, AccessibilityNodeInfo source) {
    if (BuildVersionUtils.isAtLeastLMR1()) {
      if (source == null) {
        return false;
      }
      AccessibilityWindowInfo window = source.getWindow();
      if (window == null) {
        return false;
      }
      boolean isKeyboard = (window.getType() == AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD);
      window.recycle();
      return isKeyboard;
    } else {
      // For old platforms, we can't check the window type directly, so just
      // manually check the classname.
      return event != null
          && TextUtils.equals("com.android.inputmethod.keyboard.Key", event.getClassName());
    }
  }

  /**
   * Check whether a given node has a scrollable ancestor.
   *
   * @param node The node to examine.
   * @return {@code true} if one of the node's ancestors is scrollable.
   */
  public static boolean hasMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return false;
    }

    final AccessibilityNodeInfoCompat result = getMatchingAncestor(node, filter);
    if (result == null) {
      return false;
    }

    result.recycle();
    return true;
  }

  /**
   * Check whether a given node or any of its ancestors matches the given filter.
   *
   * @param node The node to examine.
   * @param filter The filter to match the nodes against.
   * @return {@code true} if the node or one of its ancestors matches the filter.
   */
  public static boolean isOrHasMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return false;
    }

    final AccessibilityNodeInfoCompat result = getSelfOrMatchingAncestor(node, filter);
    if (result == null) {
      return false;
    }

    result.recycle();
    return true;
  }

  /** Check whether a given node has any descendant matching a given filter. */
  public static boolean hasMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return false;
    }

    final AccessibilityNodeInfoCompat result = getMatchingDescendant(node, filter);
    if (result == null) {
      return false;
    }

    result.recycle();
    return true;
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor.
   * Returns {@code null} if no nodes match.
   */
  public static AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    if (filter.accept(node)) {
      return AccessibilityNodeInfoCompat.obtain(node);
    }

    return getMatchingAncestor(node, filter);
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor,
   * ending the ancestor search once it reaches {@code end}. The search is inclusive of {@code node}
   * but exclusive of {@code end}. If {@code node} equals {@code end}, then {@code node} is an
   * eligible match. Returns {@code null} if no nodes match.
   */
  public static AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
      AccessibilityNodeInfoCompat node,
      AccessibilityNodeInfoCompat end,
      Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    if (filter.accept(node)) {
      return AccessibilityNodeInfoCompat.obtain(node);
    }

    return getMatchingAncestor(node, end, filter);
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching descendant.
   * Returns {@code null} if no nodes match.
   */
  public static AccessibilityNodeInfoCompat getSelfOrMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    if (filter.accept(node)) {
      return AccessibilityNodeInfoCompat.obtain(node);
    }

    return getMatchingDescendant(node, filter);
  }

  /**
   * Determines whether the two nodes are in the same branch; that is, they are equal or one is the
   * ancestor of the other.
   */
  public static boolean areInSameBranch(
      @Nullable final AccessibilityNodeInfoCompat node1,
      @Nullable final AccessibilityNodeInfoCompat node2) {
    if (node1 != null && node2 != null) {
      // Same node?
      if (node1.equals(node2)) {
        return true;
      }

      // Is node1 an ancestor of node2?
      Filter<AccessibilityNodeInfoCompat> matchNode1 =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return node != null && node.equals(node1);
            }
          };
      if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node2, matchNode1)) {
        return true;
      }

      // Is node2 an ancestor of node1?
      Filter<AccessibilityNodeInfoCompat> matchNode2 =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return node != null && node.equals(node2);
            }
          };
      if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node1, matchNode2)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}. Returns {@code
   * null} if no nodes match.
   */
  public static AccessibilityNodeInfoCompat getMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    return getMatchingAncestor(node, null, filter);
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}, terminating the
   * search once it reaches {@code end}. The search is exclusive of both {@code node} and {@code
   * end}. Returns {@code null} if no nodes match.
   */
  private static AccessibilityNodeInfoCompat getMatchingAncestor(
      AccessibilityNodeInfoCompat node,
      AccessibilityNodeInfoCompat end,
      Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    final HashSet<AccessibilityNodeInfoCompat> ancestors = new HashSet<>();

    try {
      ancestors.add(AccessibilityNodeInfoCompat.obtain(node));
      node = node.getParent();

      while (node != null) {
        if (!ancestors.add(node)) {
          // Already seen this node, so abort!
          node.recycle();
          return null;
        }

        if (end != null && node.equals(end)) {
          // Reached the end node, so abort!
          // Don't recycle the node here, it was added to ancestors and will be recycled.
          return null;
        }

        if (filter.accept(node)) {
          // Send a copy since node gets recycled.
          return AccessibilityNodeInfoCompat.obtain(node);
        }

        node = node.getParent();
      }
    } finally {
      recycleNodes(ancestors);
    }

    return null;
  }

  /**
   * Returns the number of ancestors matching the given filter. Does not include the current node in
   * the count, even if it matches the filter. If there is a cycle in the ancestor hierarchy, then
   * this method will return 0.
   */
  public static int countMatchingAncestors(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return 0;
    }

    final HashSet<AccessibilityNodeInfoCompat> ancestors = new HashSet<>();
    int matchingAncestors = 0;

    try {
      ancestors.add(AccessibilityNodeInfoCompat.obtain(node));
      node = node.getParent();

      while (node != null) {
        if (!ancestors.add(node)) {
          // Already seen this node, so abort!
          node.recycle();
          return 0;
        }

        if (filter.accept(node)) {
          matchingAncestors++;
        }

        node = node.getParent();
      }
    } finally {
      recycleNodes(ancestors);
    }

    return matchingAncestors;
  }

  /**
   * Returns the first child (by depth-first search) of {@code node} that matches the {@code
   * filter}. Returns {@code null} if no nodes match. The caller is responsible for recycling all
   * nodes in {@code visitedNodes} and the node returned by this method, if non-{@code null}.
   */
  private static AccessibilityNodeInfoCompat getMatchingDescendant(
      AccessibilityNodeInfoCompat node,
      Filter<AccessibilityNodeInfoCompat> filter,
      HashSet<AccessibilityNodeInfoCompat> visitedNodes) {
    if (node == null) {
      return null;
    }

    if (visitedNodes.contains(node)) {
      return null;
    } else {
      visitedNodes.add(AccessibilityNodeInfoCompat.obtain(node));
    }

    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);

      if (child == null) {
        continue;
      }

      if (filter.accept(child)) {
        return child; // child was already obtained by node.getChild().
      }

      try {
        AccessibilityNodeInfoCompat childMatch = getMatchingDescendant(child, filter, visitedNodes);
        if (childMatch != null) {
          return childMatch;
        }
      } finally {
        child.recycle();
      }
    }

    return null;
  }

  private static AccessibilityNodeInfoCompat getMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    final HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      return getMatchingDescendant(node, filter, visitedNodes);
    } finally {
      recycleNodes(visitedNodes);
    }
  }

  /**
   * Check whether a given node is scrollable.
   *
   * @param node The node to examine.
   * @return {@code true} if the node is scrollable.
   */
  public static boolean isScrollable(AccessibilityNodeInfoCompat node) {
    return node.isScrollable()
        || supportsAnyAction(
            node,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
  }

  /**
   * Returns whether the specified node has text. For the purposes of this check, any node with a
   * CollectionInfo is considered to not have text since its text and content description are used
   * only for collection transitions.
   *
   * @param node The node to check.
   * @return {@code true} if the node has text.
   */
  private static boolean hasText(AccessibilityNodeInfoCompat node) {
    return node != null
        && node.getCollectionInfo() == null
        && (!TextUtils.isEmpty(node.getText()) || !TextUtils.isEmpty(node.getContentDescription()));
  }

  /**
   * Determines whether a node is a top-level item in a scrollable container.
   *
   * @param node The node to test.
   * @return {@code true} if {@code node} is a top-level item in a scrollable container.
   */
  public static boolean isTopLevelScrollItem(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    AccessibilityNodeInfoCompat parent = null;
    AccessibilityNodeInfoCompat grandparent = null;

    try {
      parent = node.getParent();
      if (parent == null) {
        // Not a child node of anything.
        return false;
      }

      // Certain scrollable views in M's Android TV SetupWraith are permanently broken and
      // won't ever be fixed because the setup wizard is bundled. This affects <= M only.
      if (!BuildVersionUtils.isAtLeastN() && FILTER_BROKEN_LISTS_TV_M.accept(parent)) {
        return false;
      }

      if (isScrollable(node)) {
        return true;
      }

      // Top-level items in a scrolling pager are actually two levels down since the first
      // level items in pagers are the pages themselves.
      grandparent = parent.getParent();

      if (Role.getRole(grandparent) == Role.ROLE_PAGER) return true;

      @Role.RoleName int parentRole = Role.getRole(parent);
      // Note that ROLE_DROP_DOWN_LIST(Spinner) is not accepted.
      // RecyclerView is classified as a list or grid based on its CollectionInfo.
      // TODO: Check if we should consider AdapterViewAnimator.
      return parentRole == Role.ROLE_LIST
          || parentRole == Role.ROLE_GRID
          || parentRole == Role.ROLE_SCROLL_VIEW
          || parentRole == Role.ROLE_HORIZONTAL_SCROLL_VIEW
          || nodeMatchesAnyClassByType(parent, CLASS_TOUCHWIZ_TWADAPTERVIEW);
    } finally {
      recycleNodes(parent, grandparent);
    }
  }

  public static boolean hasAncestor(
      AccessibilityNodeInfoCompat node, final AccessibilityNodeInfoCompat targetAncestor) {
    if (node == null || targetAncestor == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return targetAncestor.equals(node);
          }
        };

    AccessibilityNodeInfoCompat foundAncestor = getMatchingAncestor(node, filter);
    if (foundAncestor != null) {
      foundAncestor.recycle();
      return true;
    }

    return false;
  }

  public static boolean hasDescendant(
      AccessibilityNodeInfoCompat node, final AccessibilityNodeInfoCompat targetDescendant) {
    if (node == null || targetDescendant == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return targetDescendant.equals(node);
          }
        };

    AccessibilityNodeInfoCompat foundAncestor = getMatchingDescendant(node, filter);
    if (foundAncestor != null) {
      foundAncestor.recycle();
      return true;
    }

    return false;
  }

  /**
   * Determines if the generating class of an {@link AccessibilityNodeInfoCompat} matches any of the
   * given {@link Class}es by type.
   *
   * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by the accessibility
   *     framework.
   * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object matches the {@link
   *     Class} by type or inherited type, {@code false} otherwise.
   * @param referenceClasses A variable-length list of {@link Class} objects to match by type or
   *     inherited type.
   */
  public static boolean nodeMatchesAnyClassByType(
      AccessibilityNodeInfoCompat node, Class<?>... referenceClasses) {
    if (node == null) {
      return false;
    }

    for (Class<?> referenceClass : referenceClasses) {
      if (ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClass)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if the class of an {@link AccessibilityNodeInfoCompat} matches a given {@link Class}
   * by package and name.
   *
   * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by the accessibility
   *     framework.
   * @param referenceClassName A class name to match.
   * @return {@code true} if the {@link AccessibilityNodeInfoCompat} matches the class name.
   */
  public static boolean nodeMatchesClassByName(
      AccessibilityNodeInfoCompat node, CharSequence referenceClassName) {
    return node != null
        && ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClassName);
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(Collection<AccessibilityNodeInfoCompat> nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNodeInfoCompat node : nodes) {
      if (node != null) {
        node.recycle();
      }
    }

    nodes.clear();
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(AccessibilityNodeInfo... nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNodeInfo node : nodes) {
      if (node != null) {
        node.recycle();
      }
    }
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(AccessibilityNodeInfoCompat... nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNodeInfoCompat node : nodes) {
      if (node != null) {
        node.recycle();
      }
    }
  }

  /**
   * Returns {@code true} if the node supports at least one of the specified actions. To check
   * whether a node supports multiple actions, combine them using the {@code |} (logical OR)
   * operator.
   *
   * <p>Note: this method will check against the getActions() method of AccessibilityNodeInfo, which
   * will not contain information for actions introduced in API level 21 or later.
   *
   * @param node The node to check.
   * @param actions The actions to check.
   * @return {@code true} if at least one action is supported.
   */
  public static boolean supportsAnyAction(AccessibilityNodeInfoCompat node, int... actions) {
    if (node != null) {
      final int supportedActions = node.getActions();

      for (int action : actions) {
        if ((supportedActions & action) == action) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns {@code true} if the node supports the specified action. This method supports actions
   * introduced in API level 21 and later. However, it does not support bitmasks.
   */
  public static boolean supportsAction(AccessibilityNodeInfoCompat node, int action) {
    // New actions in >= API 21 won't appear in getActions() but in getActionList().
    // On Lollipop+ devices, pre-API 21 actions will also appear in getActionList().
    List<AccessibilityActionCompat> actions = node.getActionList();
    int size = actions.size();
    for (int i = 0; i < size; ++i) {
      AccessibilityActionCompat actionCompat = actions.get(i);
      if (actionCompat.getId() == action) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the result of applying a filter using breadth-first traversal.
   *
   * @param node The root node to traverse from.
   * @param filter The filter to satisfy.
   * @return The first node reached via BFS traversal that satisfies the filter.
   */
  public static AccessibilityNodeInfoCompat searchFromBfs(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    final LinkedList<AccessibilityNodeInfoCompat> queue = new LinkedList<>();
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();

    queue.add(AccessibilityNodeInfoCompat.obtain(node));

    try {
      while (!queue.isEmpty()) {
        final AccessibilityNodeInfoCompat item = queue.removeFirst();
        visitedNodes.add(item);

        if (filter.accept(item)) {
          return item;
        }

        final int childCount = item.getChildCount();

        for (int i = 0; i < childCount; i++) {
          final AccessibilityNodeInfoCompat child = item.getChild(i);

          if (child != null && !visitedNodes.contains(child)) {
            queue.addLast(child);
          }
        }
        item.recycle();
      }
    } finally {
      while (!queue.isEmpty()) {
        queue.removeFirst().recycle();
      }
    }

    return null;
  }

  /** Safely obtains a copy of node. Caller must recycle returned node info. */
  public static AccessibilityNodeInfoCompat obtain(AccessibilityNodeInfoCompat node) {
    return (node == null) ? null : AccessibilityNodeInfoCompat.obtain(node);
  }

  /** Safely obtains a copy of node. Caller must recycle returned node info. */
  public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo node) {
    return (node == null) ? null : AccessibilityNodeInfo.obtain(node);
  }

  /**
   * Replaces a node with a refreshed node.
   *
   * @param node A source node which may be stale, and which will be recycled.
   * @return A refreshed node, which the caller must recycle.
   */
  public static AccessibilityNodeInfoCompat replaceWithFreshNode(AccessibilityNodeInfoCompat node) {
    try {
      return AccessibilityNodeInfoUtils.refreshNode(node);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  /**
   * Returns a fresh copy of {@code node} with properties that are less likely to be stale. Returns
   * {@code null} if the node can't be found anymore.
   */
  public static AccessibilityNodeInfoCompat refreshNode(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
    if (nodeCopy.refresh()) {
      return nodeCopy;
    } else {
      nodeCopy.recycle();
      return null;
    }
  }

  /**
   * Returns a fresh copy of node by traversing the given window for a similar node. For example,
   * the node that you want might be in a popup window that has closed and re-opened, causing the
   * accessibility IDs of its views to be different. Note: you must recycle the node that is
   * returned from this method.
   */
  public static AccessibilityNodeInfoCompat refreshNodeFuzzy(
      final AccessibilityNodeInfoCompat node, AccessibilityWindowInfo window) {
    if (window == null || node == null) {
      return null;
    }

    AccessibilityNodeInfo root = window.getRoot();
    if (root == null) {
      return null;
    }

    Filter<AccessibilityNodeInfoCompat> similarFilter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat other) {
            return other != null && TextUtils.equals(node.getText(), other.getText());
          }
        };

    AccessibilityNodeInfoCompat rootCompat = AccessibilityNodeInfoUtils.toCompat(root);
    try {
      return getMatchingDescendant(rootCompat, similarFilter);
    } finally {
      rootCompat.recycle();
    }
  }

  /**
   * Gets the location of specific range of node text. It returns null if the node doesn't support
   * text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   */
  @TargetApi(Build.VERSION_CODES.O)
  @Nullable
  public static List<Rect> getTextLocations(
      AccessibilityNodeInfoCompat node, int fromCharIndex, int toCharIndex) {
    if (node == null || !BuildVersionUtils.isAtLeastO()) {
      return null;
    }

    if (fromCharIndex < 0
        || !PrimitiveUtils.isInInterval(
            toCharIndex, fromCharIndex, node.getText().length(), true)) {
      return null;
    }
    AccessibilityNodeInfo info = (AccessibilityNodeInfo) node.getInfo();
    if (info == null) {
      return null;
    }
    Bundle args = new Bundle();
    args.putInt(
        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, fromCharIndex);
    args.putInt(
        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
        toCharIndex - fromCharIndex);
    if (!info.refreshWithExtraData(
        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)) {
      return null;
    }

    Bundle extras = info.getExtras();
    Parcelable[] data =
        extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
    if (data == null) {
      return null;
    }
    List<Rect> result = new ArrayList<>(data.length);
    for (Parcelable item : data) {
      if (item == null) {
        continue;
      }
      RectF rectF = (RectF) item;
      result.add(
          new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom));
    }
    return result;
  }

  /** Returns true if the node supports text location data. */
  @TargetApi(Build.VERSION_CODES.O)
  public static boolean supportsTextLocation(AccessibilityNodeInfoCompat node) {
    if (!BuildVersionUtils.isAtLeastO() || node == null) {
      return false;
    }
    AccessibilityNodeInfo info = (AccessibilityNodeInfo) node.getInfo();
    if (info == null) {
      return false;
    }
    List<String> extraData = info.getAvailableExtraData();
    return extraData != null
        && extraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
  }

  /** Helper method that returns {@code true} if the specified node is visible to the user */
  public static boolean isVisible(AccessibilityNodeInfoCompat node) {
    // We need to move focus to invisible node in WebView to scroll it but we don't want to
    // move focus if WebView itself is invisible.
    return node != null
        && (node.isVisibleToUser()
            || (WebInterfaceUtils.isWebContainer(node)
                && Role.getRole(node) != Role.ROLE_WEB_VIEW));
  }

  /** Determines whether the specified node has bounds identical to the bounds of its window. */
  private static boolean areBoundsIdenticalToWindow(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    AccessibilityWindowInfoCompat window = node.getWindow();
    if (window == null) {
      return false;
    }

    Rect windowBounds = new Rect();
    window.getBoundsInScreen(windowBounds);

    Rect nodeBounds = new Rect();
    node.getBoundsInScreen(nodeBounds);

    return windowBounds.equals(nodeBounds);
  }

  /**
   * Returns the node to which the given node's window is anchored, if there is an anchor. Note: you
   * must recycle the node that is returned from this method.
   */
  public static AccessibilityNodeInfoCompat getAnchor(@Nullable AccessibilityNodeInfoCompat node) {
    if (!BuildVersionUtils.isAtLeastN()) {
      return null;
    }

    if (node == null) {
      return null;
    }

    AccessibilityNodeInfo nativeNode = (AccessibilityNodeInfo) node.getInfo();
    if (nativeNode == null) {
      return null;
    }

    AccessibilityWindowInfo nativeWindow = nativeNode.getWindow();
    if (nativeWindow == null) {
      return null;
    }

    AccessibilityNodeInfo nativeAnchor = nativeWindow.getAnchor();
    if (nativeAnchor == null) {
      return null;
    }

    return AccessibilityNodeInfoUtils.toCompat(nativeAnchor);
  }

  public static boolean isEmptyEditTextRegardlessOfHint(
      @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null || !(isEditable(node))) {
      return false;
    }

    if (TextUtils.isEmpty(node.getText())) {
      return true;
    }
    return !supportsAction(node, AccessibilityNodeInfo.ACTION_SET_SELECTION);
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static CharSequence getHintText(AccessibilityNodeInfoCompat node) {
    // TODO: Use AccessibilityNodeInfoCompat once support for O is added.
    AccessibilityNodeInfo nodeInfo = node.unwrap();

    CharSequence hintText = null;

    // Incase of O, first get hint from getHintText, if its not available, then check bundle.
    // For lower Android version, always use bundle.
    if (BuildVersionUtils.isAtLeastO()) {
      hintText = nodeInfo.getHintText();
    }
    if (hintText == null) {
      Bundle bundle = node.getExtras();
      if (bundle != null) {
        hintText = bundle.getCharSequence(HINT_TEXT_KEY);
      }
    }

    return hintText;
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static boolean isShowingHint(AccessibilityNodeInfoCompat node) {
    // TODO: Use AccessibilityNodeInfoCompat once support for O is added.
    if (BuildVersionUtils.isAtLeastO()) {
      AccessibilityNodeInfo nodeInfo = node.unwrap();
      return nodeInfo.isShowingHintText();
    }
    return false;
  }

  public static String actionToString(int action) {
    switch (action) {
      case AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS:
        return "ACTION_ACCESSIBILITY_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
        return "ACTION_CLEAR_ACCESSIBILITY_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS:
        return "ACTION_CLEAR_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION:
        return "ACTION_CLEAR_SELECTION";
      case AccessibilityNodeInfoCompat.ACTION_CLICK:
        return "ACTION_CLICK";
      case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
        return "ACTION_COLLAPSE";
      case AccessibilityNodeInfoCompat.ACTION_COPY:
        return "ACTION_COPY";
      case AccessibilityNodeInfoCompat.ACTION_CUT:
        return "ACTION_CUT";
      case AccessibilityNodeInfoCompat.ACTION_DISMISS:
        return "ACTION_DISMISS";
      case AccessibilityNodeInfoCompat.ACTION_EXPAND:
        return "ACTION_EXPAND";
      case AccessibilityNodeInfoCompat.ACTION_FOCUS:
        return "ACTION_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
        return "ACTION_LONG_CLICK";
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        return "ACTION_NEXT_AT_MOVEMENT_GRANULARITY";
      case AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT:
        return "ACTION_NEXT_HTML_ELEMENT";
      case AccessibilityNodeInfoCompat.ACTION_PASTE:
        return "ACTION_PASTE";
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY";
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT:
        return "ACTION_PREVIOUS_HTML_ELEMENT";
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
        return "ACTION_SCROLL_BACKWARD";
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
        return "ACTION_SCROLL_FORWARD";
      case AccessibilityNodeInfoCompat.ACTION_SELECT:
        return "ACTION_SELECT";
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return "ACTION_SET_SELECTION";
      case AccessibilityNodeInfoCompat.ACTION_SET_TEXT:
        return "ACTION_SET_TEXT";
      default:
        return "(unhandled)";
    }
  }
}
