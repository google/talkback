/*
 * Copyright (C) The Android Open Source Project
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
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils

import android.content.Context
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.traversal.TraversalStrategy
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import java.util.Collections

/** Utility class for sending commands to Chrome. */
object WebInterfaceUtils {

  private const val KEY_WEB_IMAGE = "AccessibilityNodeInfo.hasImage"
  private const val VALUE_HAS_WEB_IMAGE = "true"

  private const val ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES =
    "ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES"

  /** Direction constant for forward movement within a page. */
  const val DIRECTION_FORWARD = 1

  /** Direction constant for backward movement within a page. */
  const val DIRECTION_BACKWARD = -1

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page section.
   */
  const val HTML_ELEMENT_MOVE_BY_SECTION = "SECTION"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING = "HEADING"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page section.
   */
  const val HTML_ELEMENT_MOVE_BY_LANDMARK = "LANDMARK"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous link.
   */
  const val HTML_ELEMENT_MOVE_BY_LINK = "LINK"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous list.
   */
  const val HTML_ELEMENT_MOVE_BY_LIST = "LIST"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous control.
   */
  const val HTML_ELEMENT_MOVE_BY_CONTROL = "CONTROL"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous button.
   */
  const val HTML_ELEMENT_MOVE_BY_BUTTON = "BUTTON"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous checkbox.
   */
  const val HTML_ELEMENT_MOVE_BY_CHECKBOX = "CHECKBOX"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous radio.
   */
  const val HTML_ELEMENT_MOVE_BY_RADIO = "RADIO"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous edit field.
   */
  const val HTML_ELEMENT_MOVE_BY_EDIT_FIELD = "TEXT_FIELD"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous focusable item.
   */
  const val HTML_ELEMENT_MOVE_BY_FOCUSABLE_ITEM = "FOCUSABLE"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading 1.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING_1 = "H1"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading 2.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING_2 = "H2"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading 3.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING_3 = "H3"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading 4.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING_4 = "H4"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading 5.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING_5 = "H5"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous page heading 6.
   */
  const val HTML_ELEMENT_MOVE_BY_HEADING_6 = "H6"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous image.
   */
  const val HTML_ELEMENT_MOVE_BY_GRAPHIC = "GRAPHIC"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous list item.
   */
  const val HTML_ELEMENT_MOVE_BY_LIST_ITEM = "LIST_ITEM"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous table.
   */
  const val HTML_ELEMENT_MOVE_BY_TABLE = "TABLE"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous combo box.
   */
  const val HTML_ELEMENT_MOVE_BY_COMBOBOX = "COMBOBOX"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous visited link.
   */
  const val HTML_ELEMENT_MOVE_BY_VISITED_LINK = "VISITED_LINK"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous unvisited link.
   */
  const val HTML_ELEMENT_MOVE_BY_UNVISITED_LINK = "UNVISITED_LINK"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous column.
   */
  const val HTML_ELEMENT_MOVE_BY_COLUMN = "COLUMN"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous row.
   */
  const val HTML_ELEMENT_MOVE_BY_ROW = "ROW"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous column bounds.
   */
  const val HTML_ELEMENT_MOVE_BY_COLUMN_BOUNDS = "COLUMN_BOUNDS"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous row bounds.
   */
  const val HTML_ELEMENT_MOVE_BY_ROW_BOUNDS = "ROW_BOUNDS"

  /**
   * HTML element argument to use with {@link AccessibilityNodeInfoCompat#performAction()} to
   * instruct Chrome to move to the next or previous table bounds.
   */
  const val HTML_ELEMENT_MOVE_BY_TABLE_BOUNDS = "TABLE_BOUNDS"

  private val URL_BAR_IDS: ImmutableMap<String, ImmutableList<String>> =
    ImmutableMap.ofEntries(
      java.util.Map.entry("com.android.chrome", ImmutableList.of("com.android.chrome:id/url_bar")),
      java.util.Map.entry("com.chrome.beta", ImmutableList.of("com.chrome.beta:id/url_bar")),
      java.util.Map.entry("com.chrome.dev", ImmutableList.of("com.chrome.dev:id/url_bar")),
      java.util.Map.entry(
        "org.mozilla.firefox",
        ImmutableList.of(
          "org.mozilla.firefox:id/url",
          "org.mozilla.firefox:id/url_bar_title",
          "org.mozilla.firefox:id/url_edit_text",
          "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
          "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view")),
      java.util.Map.entry(
        "org.mozilla.firefox_beta",
        ImmutableList.of(
          "org.mozilla.firefox_beta:id/url",
          "org.mozilla.firefox_beta:id/url_bar_title",
          "org.mozilla.firefox_beta:id/url_edit_text",
          "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
          "org.mozilla.firefox_beta:id/mozac_browser_toolbar_edit_url_view")),
      java.util.Map.entry(
        "com.sec.android.app.sbrowser",
        ImmutableList.of("com.sec.android.app.sbrowser:id/location_bar_edit_text")),
      java.util.Map.entry("com.android.browser", ImmutableList.of("com.android.browser:id/url")),
      java.util.Map.entry("com.opera.android", ImmutableList.of("com.opera.android:id/url_field")),
      java.util.Map.entry("com.opera.browser", ImmutableList.of("com.opera.browser:id/url_field")),
      java.util.Map.entry(
        "com.hsv.freeadblockerbrowser", ImmutableList.of("com.opera.browser:id/url_field")),
      java.util.Map.entry("com.microsoft.emmx", ImmutableList.of("com.microsoft.emmx:id/url_bar")),
    )

  /**
   * Filter for WebView container node. See {@link
   * #ascendToWebViewContainer(AccessibilityNodeInfoCompat)}.
   */
  private val FILTER_WEB_VIEW_CONTAINER: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        if (node == null) {
          return false
        }

        return Role.getRole(node) == Role.ROLE_WEB_VIEW &&
          Role.getRole(node.parent) != Role.ROLE_WEB_VIEW
      }
    }

  /** Filter for WebView node. See {@link #ascendToWebView(AccessibilityNodeInfoCompat)}. */
  private val FILTER_WEB_VIEW: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean =
        node != null && Role.getRole(node) == Role.ROLE_WEB_VIEW
    }

  @JvmStatic
  fun searchDirectionToWebNavigationDirection(
    context: Context,
    @TraversalStrategy.SearchDirectionOrUnknown searchDirection: Int,
  ): Int {
    if (searchDirection == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return 0
    }
    @TraversalStrategy.SearchDirectionOrUnknown
    val logicalDirection =
      TraversalStrategyUtils.getLogicalDirection(
        searchDirection, WindowUtils.isScreenLayoutRTL(context))
    return if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      DIRECTION_FORWARD
    } else {
      DIRECTION_BACKWARD
    }
  }

  /**
   * Gets supported html elements, such as HEADING, LANDMARK, LINK and LIST, by
   * AccessibilityNodeInfoCompat.
   *
   * @param node The node containing supported html elements
   * @return supported html elements
   */
  @JvmStatic
  fun getSupportedHtmlElements(node: AccessibilityNodeInfoCompat?): Array<String>? {
    val supportedHtmlNodeCollector = SupportedHtmlNodeCollector()
    AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(node, supportedHtmlNodeCollector)
    if (supportedHtmlNodeCollector.supportedTypes.isEmpty()) {
      return null
    }
    return supportedHtmlNodeCollector.supportedTypes.toTypedArray()
  }

  private class SupportedHtmlNodeCollector : Filter<AccessibilityNodeInfoCompat>() {
    val supportedTypes = ArrayList<String>()

    override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
      if (node == null) {
        return false
      }
      val bundle = node.extras
      val supportedHtmlElements = bundle.getCharSequence(ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES)

      if (supportedHtmlElements != null) {
        Collections.addAll(
          supportedTypes,
          *supportedHtmlElements.toString().split(",").toTypedArray(),
        )
        return true
      }
      return false
    }
  }

  /**
   * Returns the WebView container node if the {@code node} is a web element. <strong>Note:</strong>
   * A web content node tree is always constructed with a WebView root node, a second level WebView
   * node, and all other nodes attached beneath the second level WebView node. When referring to the
   * WebView container, we prefer the root node instead of the second level node, because attributes
   * like isVisibleToUser() sometimes are not correctly exposed at second level WebView node.
   */
  @JvmStatic
  fun ascendToWebViewContainer(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (!supportsWebActions(node)) {
      return null
    }
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_WEB_VIEW_CONTAINER)
  }

  /** Returns the closest ancestor(inclusive) WebView node if the {@code node} is a web element. */
  @JvmStatic
  fun ascendToWebView(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (!supportsWebActions(node)) {
      return null
    }
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_WEB_VIEW)
  }

  /**
   * Determines whether or not the given node contains web content.
   *
   * @param node The node to evaluate
   * @return {@code true} if the node contains web content, {@code false} otherwise
   */
  @JvmStatic
  fun supportsWebActions(node: AccessibilityNodeInfoCompat?): Boolean =
    AccessibilityNodeInfoUtils.supportsAnyAction(
      node,
      AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
      AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT,
    )

  /**
   * Determines whether or not the given node contains native web content (and not Chrome).
   *
   * @param node The node to evaluate
   * @return {@code true} if the node contains native web content, {@code false} otherwise
   */
  @JvmStatic
  fun hasNativeWebContent(node: AccessibilityNodeInfoCompat?): Boolean = supportsWebActions(node)

  /**
   * Returns whether the given node has navigable web content, either legacy (Chrome) or native web
   * content.
   *
   * @param node The node to check for web content.
   * @return Whether the given node has navigable web content.
   */
  @JvmStatic
  fun hasNavigableWebContent(node: AccessibilityNodeInfoCompat?): Boolean = supportsWebActions(node)

  /** Check if node is web container */
  @JvmStatic
  fun isWebContainer(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }
    return hasNativeWebContent(node) || isNodeFromFirefox(node)
  }

  /** Returns {@code true} if the {@code node} or its descendant contains image. */
  @JvmStatic
  fun containsImage(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }
    val extras = node.extras
    return extras != null && VALUE_HAS_WEB_IMAGE == extras.getString(KEY_WEB_IMAGE)
  }

  @JvmStatic
  fun findUrlBar(root: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? =
    AccessibilityNodeInfoUtils.searchFromBfs(
      root,
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(node: AccessibilityNodeInfoCompat): Boolean =
          URL_BAR_IDS
            .getOrDefault(node.packageName.toString(), ImmutableList.of())!!
            .contains(node.viewIdResourceName)
      },
    )

  private fun isNodeFromFirefox(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    val packageName = if (node.packageName != null) node.packageName.toString() else ""
    return packageName.startsWith("org.mozilla.")
  }
}
