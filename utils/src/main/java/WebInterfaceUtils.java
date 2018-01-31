/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.utils;

import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.HashSet;

/** Utility class for sending commands to ChromeVox. */
public class WebInterfaceUtils {

  private static final String ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES =
      "ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES";

  /** Direction constant for forward movement within a page. */
  public static final int DIRECTION_FORWARD = 1;

  /** Direction constant for backward movement within a page. */
  public static final int DIRECTION_BACKWARD = -1;

  /**
   * Action argument to use with {@link #performSpecialAction(AccessibilityNodeInfoCompat, int, int,
   * EventId)} to instruct ChromeVox to move into or out of the special content navigation mode.
   *
   * <p>Using this constant also requires specifying a direction. {@link #DIRECTION_FORWARD}
   * indicates ChromeVox should move into this content navigation mode, {@link #DIRECTION_BACKWARD}
   * indicates ChromeVox should move out of this mode.
   */
  private static final int ACTION_TOGGLE_SPECIAL_CONTENT = -4;

  /**
   * HTML element argument to use with {@link
   * #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat, int, String, EventId)} to
   * instruct ChromeVox to move to the next or previous page section.
   */
  public static final String HTML_ELEMENT_MOVE_BY_SECTION = "SECTION";

  /**
   * HTML element argument to use with {@link
   * #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat, int, String, EventId)} to
   * instruct ChromeVox to move to the next or previous link.
   */
  public static final String HTML_ELEMENT_MOVE_BY_LINK = "LINK";

  /**
   * HTML element argument to use with {@link
   * #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat, int, String, EventId)} to
   * instruct ChromeVox to move to the next or previous list.
   */
  public static final String HTML_ELEMENT_MOVE_BY_LIST = "LIST";

  /**
   * HTML element argument to use with {@link
   * #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat, int, String, EventId)} to
   * instruct ChromeVox to move to the next or previous control.
   */
  public static final String HTML_ELEMENT_MOVE_BY_CONTROL = "CONTROL";

  /**
   * Filter for WebView container node. See {@link
   * #ascendToWebViewContainer(AccessibilityNodeInfoCompat)}.
   */
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_WEB_VIEW_CONTAINER =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (node == null) {
            return false;
          }
          AccessibilityNodeInfoCompat parent = node.getParent();
          try {
            return Role.getRole(node) == Role.ROLE_WEB_VIEW
                && Role.getRole(parent) != Role.ROLE_WEB_VIEW;
          } finally {
            AccessibilityNodeInfoUtils.recycleNodes(parent);
          }
        }
      };

  /** Filter for WebView node. See {@link #ascendToWebView(AccessibilityNodeInfoCompat)}. */
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_WEB_VIEW =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null && Role.getRole(node) == Role.ROLE_WEB_VIEW;
        }
      };

  /**
   * Sends an instruction to ChromeVox to read the specified HTML element in the given direction
   * within a node.
   *
   * <p>WARNING: Calling this method with a source node of {@link android.webkit.WebView} has the
   * side effect of closing the IME if currently displayed.
   *
   * @param node The node containing web content with ChromeVox to which the message should be sent
   * @param direction {@link #DIRECTION_FORWARD} or {@link #DIRECTION_BACKWARD}
   * @param htmlElement The HTML tag to send
   * @return {@code true} if the action was performed, {@code false} otherwise.
   */
  public static boolean performNavigationToHtmlElementAction(
      AccessibilityNodeInfoCompat node, int direction, String htmlElement, EventId eventId) {
    final int action =
        (direction == DIRECTION_FORWARD)
            ? AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT
            : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT;
    final Bundle args = new Bundle();
    args.putString(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_HTML_ELEMENT_STRING, htmlElement);
    return PerformActionUtils.performAction(node, action, args, eventId);
  }

  public static String[] getSupportedHtmlElements(AccessibilityNodeInfoCompat node) {
    HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<AccessibilityNodeInfoCompat>();

    while (node != null) {
      if (visitedNodes.contains(node)) {
        return null;
      }

      visitedNodes.add(node);

      Bundle bundle = node.getExtras();
      CharSequence supportedHtmlElements =
          bundle.getCharSequence(ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES);

      if (supportedHtmlElements != null) {
        return supportedHtmlElements.toString().split(",");
      }

      node = node.getParent();
    }

    return null;
  }

  /**
   * Sends an instruction to ChromeVox to move within a page at a specified granularity in a given
   * direction.
   *
   * <p>WARNING: Calling this method with a source node of {@link android.webkit.WebView} has the
   * side effect of closing the IME if currently displayed.
   *
   * @param node The node containing web content with ChromeVox to which the message should be sent
   * @param direction {@link #DIRECTION_FORWARD} or {@link #DIRECTION_BACKWARD}
   * @param granularity The granularity with which to move or a special case argument.
   * @return {@code true} if the action was performed, {@code false} otherwise.
   */
  public static boolean performNavigationAtGranularityAction(
      AccessibilityNodeInfoCompat node, int direction, int granularity, EventId eventId) {
    final int action =
        (direction == DIRECTION_FORWARD)
            ? AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
    final Bundle args = new Bundle();
    args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity);
    return PerformActionUtils.performAction(node, action, args, eventId);
  }

  /**
   * Sends instruction to ChromeVox to perform one of the special actions defined by the ACTION
   * constants in this class.
   *
   * <p>WARNING: Calling this method with a source node of {@link android.webkit.WebView} has the
   * side effect of closing the IME if currently displayed.
   *
   * @param node The node containing web content with ChromeVox to which the message should be sent
   * @param action The ACTION constant in this class match the special action that ChromeVox should
   *     perform.
   * @param direction The DIRECTION constant in this class to add as an extra argument to the
   *     special action.
   * @return {@code true} if the action was performed, {@code false} otherwise.
   */
  private static boolean performSpecialAction(
      AccessibilityNodeInfoCompat node, int action, int direction, EventId eventId) {
    /*
     * We use performNavigationAtGranularity to communicate with ChromeVox
     * for these actions because it is side-effect-free. If we use
     * performNavigationToHtmlElementAction and ChromeVox isn't injected,
     * we'll actually move selection within the fallback implementation. We
     * use the granularity field to hold a value that ChromeVox interprets
     * as a special command.
     */
    return performNavigationAtGranularityAction(
        node, direction, action /* fake granularity */, eventId);
  }

  /**
   * Sends a message to ChromeVox indicating that it should enter or exit special content
   * navigation. This is applicable for things like tables and math expressions.
   *
   * <p>NOTE: further navigation should occur at the default movement granularity.
   *
   * @param node The node representing the web content
   * @param enabled Whether this mode should be entered or exited
   * @return {@code true} if the action was performed, {@code false} otherwise.
   */
  public static boolean setSpecialContentModeEnabled(
      AccessibilityNodeInfoCompat node, boolean enabled, EventId eventId) {
    final int direction = (enabled) ? DIRECTION_FORWARD : DIRECTION_BACKWARD;
    return performSpecialAction(node, ACTION_TOGGLE_SPECIAL_CONTENT, direction, eventId);
  }

  /**
   * Returns the WebView container node if the {@code node} is a web element. <strong>Note:</strong>
   * A web content node tree is always constructed with a WebView root node, a second level WebView
   * node, and all other nodes attached beneath the second level WebView node. When referring to the
   * WebView container, we prefer the root node instead of the second level node, because attributes
   * like isVisibleToUser() sometimes are not correctly exposed at second level WebView node.
   */
  public static AccessibilityNodeInfoCompat ascendToWebViewContainer(
      AccessibilityNodeInfoCompat node) {
    if (!WebInterfaceUtils.supportsWebActions(node)) {
      return null;
    }
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_WEB_VIEW_CONTAINER);
  }

  /** Returns the closest ancestor(inclusive) WebView node if the {@code node} is a web element. */
  public static AccessibilityNodeInfoCompat ascendToWebView(AccessibilityNodeInfoCompat node) {
    if (!WebInterfaceUtils.supportsWebActions(node)) {
      return null;
    }
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_WEB_VIEW);
  }

  /**
   * Determines whether or not the given node contains web content.
   *
   * @param node The node to evaluate
   * @return {@code true} if the node contains web content, {@code false} otherwise
   */
  public static boolean supportsWebActions(AccessibilityNodeInfoCompat node) {
    return AccessibilityNodeInfoUtils.supportsAnyAction(
        node,
        AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
  }

  /**
   * Determines whether or not the given node contains native web content (and not ChromeVox).
   *
   * @param node The node to evaluate
   * @return {@code true} if the node contains native web content, {@code false} otherwise
   */
  public static boolean hasNativeWebContent(AccessibilityNodeInfoCompat node) {
    return supportsWebActions(node);
  }

  /**
   * Returns whether the given node has navigable web content, either legacy (ChromeVox) or native
   * web content.
   *
   * @param node The node to check for web content.
   * @return Whether the given node has navigable web content.
   */
  public static boolean hasNavigableWebContent(AccessibilityNodeInfoCompat node) {
    return supportsWebActions(node);
  }

  /** Check if node is web container */
  public static boolean isWebContainer(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    return hasNativeWebContent(node) || isNodeFromFirefox(node);
  }

  private static boolean isNodeFromFirefox(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    final String packageName =
        node.getPackageName() != null ? node.getPackageName().toString() : "";
    return packageName.startsWith("org.mozilla.");
  }
}
