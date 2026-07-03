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
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.LocaleList
import android.os.Parcelable
import android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.text.style.SuggestionSpan
import android.text.style.URLSpan
import android.util.Pair
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.GridView
import android.widget.ListView
import androidx.annotation.VisibleForTesting
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_ID_NONE
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_TYPE_NONE
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_NOT_SPEAKABLE
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_NOT_VISIBLE
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.NONE
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.DiagnosticType
import com.google.android.accessibility.utils.Role.ROLE_GRID
import com.google.android.accessibility.utils.Role.ROLE_HORIZONTAL_SCROLL_VIEW
import com.google.android.accessibility.utils.Role.ROLE_LIST
import com.google.android.accessibility.utils.Role.ROLE_PAGER
import com.google.android.accessibility.utils.Role.ROLE_SCROLL_VIEW
import com.google.android.accessibility.utils.Role.ROLE_WEB_VIEW
import com.google.android.accessibility.utils.Role.RoleName
import com.google.android.accessibility.utils.SpannableUtils.SpannableWithOffset
import com.google.android.accessibility.utils.compat.CompatUtils
import com.google.android.accessibility.utils.traversal.SpannableTraversalUtils
import com.google.android.libraries.accessibility.utils.log.LogUtils
import com.google.android.libraries.accessibility.utils.url.SpannableUrl
import com.google.common.base.Function
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.FormatMethod
import com.google.errorprone.annotations.FormatString
import java.util.ArrayDeque
import java.util.Locale
import java.util.regex.Pattern

/** Provides a series of utilities for interacting with AccessibilityNodeInfo objects. */
object AccessibilityNodeInfoUtils {

  /** Internal AccessibilityNodeInfoCompat extras bundle key constants. */
  // The minimum amount of pixels that must be visible for a view to be surfaced to the user as
  // visible (i.e. for this node to be added to the tree).
  const val MIN_VISIBLE_PIXELS = 15

  private val CLASS_LISTVIEW: String = ListView::class.java.name
  private val CLASS_GRIDVIEW: String = GridView::class.java.name

  private val actionIdToName: HashMap<Int, String> = initActionIds()

  /** Returns text from an accessibility-node, including spans. */
  @JvmStatic
  fun getText(node: AccessibilityNodeInfoCompat?): CharSequence? {
    return if (node == null) null else node.text
  }

  @FormatMethod
  private fun logError(functionName: String, @FormatString format: String, vararg args: Any?) {
    LogUtils.e(TAG, functionName + "() " + String.format(format, *args))
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private const val TAG = "AccessibilityNodeInfoUtils"

  /**
   * Class for Samsung's TouchWiz implementation of AdapterView. May be {@code null} on non-Samsung
   * devices.
   */
  private val CLASS_TOUCHWIZ_TWADAPTERVIEW: Class<*>? =
    CompatUtils.getClass("com.sec.android.touchwiz.widget.TwAdapterView")

  /** Key to get accessibility web hints from the web */
  private const val HINT_TEXT_KEY = "AccessibilityNodeInfo.hint"

  private val RESOURCE_NAME_SPLIT_PATTERN: Pattern = Pattern.compile(":id/")

  /** Class used to find clickable-spans in text. */
  @JvmField val BASE_CLICKABLE_SPAN: Class<out ClickableSpan> = ClickableSpan::class.java

  private const val VIEW_ID_RESOURCE_NAME_PIN_ENTRY = "com.android.systemui:id/pinEntry"

  @VisibleForTesting
  internal const val VIEW_ID_WEAR_UNREAD_NOTIFICATION_DOT =
    "com.google.android.wearable.sysui:id/unread_dot"

  @VisibleForTesting internal const val THRESHOLD_HEIGHT_DP_FOR_SMALL_NODE = 32

  /** Key to get the chrome role from the node */
  private const val EXTRAS_KEY_CHROME_ROLE = "AccessibilityNodeInfo.chromeRole"

  /**
   * Chrome role for link. The role string should come from `ToString(ax::mojom::Role role)` at
   * ui/accessibility/ax_enum_util.cc in the Chromium repo.
   * https://source.chromium.org/chromium/chromium/src/+/main:ui/accessibility/ax_enum_util.cc?q=%22ToString(ax::mojom::Role%20role)%22%20f:ui%2Faccessibility%2Fax_enum_util.cc
   */
  private const val CHROME_ROLE_LINK = "link"

  /**
   * A wrapper over AccessibilityNodeInfoCompat constructor, so that we can add any desired error
   * checking and memory management.
   *
   * @param nodeInfo The AccessibilityNodeInfo which will be wrapped.
   * @return Encapsulating AccessibilityNodeInfoCompat, or null if input is null.
   */
  @JvmStatic
  fun toCompat(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfoCompat? {
    if (nodeInfo == null) {
      return null
    }
    return AccessibilityNodeInfoCompat.wrap(nodeInfo)
  }

  private const val SYSTEM_ACTION_MAX = 0x01FFFFFF

  const val WINDOW_TYPE_PICTURE_IN_PICTURE = 1000

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
  @JvmField
  val FILTER_SCROLLABLE: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return isScrollable(node)
      }
    }

  /** Filter for items that could be scrolled forward. */
  @JvmField
  val FILTER_COULD_SCROLL_FORWARD: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return node != null &&
          supportsAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
      }
    }

  /** Filter for items that could be scrolled backward. */
  @JvmField
  val FILTER_COULD_SCROLL_BACKWARD: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return node != null &&
          supportsAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
      }
    }

  /**
   * Filter for items that should receive accessibility focus. Equivalent to calling {@link
   * #shouldFocusNode(AccessibilityNodeInfoCompat)}.
   *
   * <p><strong>Note:</strong> Use {@link #FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW} has a filter for
   * {@link AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events.
   */
  @JvmField
  val FILTER_SHOULD_FOCUS: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return node != null && shouldFocusNode(node)
      }
    }

  /**
   * Filter for items that should receive accessibility focus from {@link
   * AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events. WebView container node should not be focus
   * for hover enter actions.
   */
  @JvmField
  val FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW: Filter<AccessibilityNodeInfoCompat> =
    FILTER_SHOULD_FOCUS.and(
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
          return Role.getRole(node) != Role.ROLE_WEB_VIEW
        }
      })

  /** Filter for heading items in collections. */
  @JvmField
  val FILTER_HEADING: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return (node != null) && isHeading(node)
      }
    }

  private val CONTAINER_ROLES: ImmutableSet<Int> =
    ImmutableSet.of(
      ROLE_LIST,
      ROLE_GRID,
      ROLE_PAGER,
      ROLE_SCROLL_VIEW,
      ROLE_HORIZONTAL_SCROLL_VIEW,
      ROLE_WEB_VIEW)

  /** Filter for container. */
  @JvmField
  val FILTER_CONTAINER: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return node != null &&
          (CONTAINER_ROLES.contains(Role.getRole(node)) ||
            !TextUtils.isEmpty(node.containerTitle))
      }
    }

  /**
   * Filter for focusable containers with a descendant that is an unfocusable heading. This filter
   * aids navigation by headings granularity when the node that is semantically a heading isn't
   * focusable (for instance, because its text is combined with the text of other nodes to create
   * speakable text for a container in a list context).
   */
  @JvmField
  val FILTER_CONTAINER_WITH_UNFOCUSABLE_HEADING: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return searchFromBfs(
          node,
          FILTER_HEADING.and(
            object : Filter<AccessibilityNodeInfoCompat>() {
              override fun accept(childNode: AccessibilityNodeInfoCompat): Boolean {
                return childNode.childCount == 0 && !shouldFocusNode(childNode)
              }
            })) != null
      }
    }

  /** Filter for scrollable grids. */
  @JvmField
  val FILTER_SCROLLABLE_GRID: Filter<AccessibilityNodeInfoCompat> =
    FILTER_SCROLLABLE.and(Filter.node { n -> Role.getRole(n) == Role.ROLE_GRID })

  /** Filter for table. */
  private val FILTER_TABLE: Filter<AccessibilityNodeInfoCompat> =
    Filter.node { node -> isTableRoot(node) }

  /** Filter for table cell. */
  @JvmField
  val FILTER_TABLE_CELL: Filter<AccessibilityNodeInfoCompat> =
    Filter.node { node -> isTableCell(node) }

  /** Filter for table cell and check if it is in a table. */
  @JvmField
  val FILTER_TABLE_CELL_UNDER_TABLE: Filter<AccessibilityNodeInfoCompat> =
    Filter.node { node -> isTableCellUnderTable(node) }

  /** Filter the node matched the voice dictation definition. */
  @JvmField
  val FILTER_VOICE_DICTATION: Filter<AccessibilityNodeInfoCompat> =
    Filter.node { node -> isVoiceDictationNode(node) }

  /**
   * Filter that also checks for {@param node}'s non-focusable but visible children. Sometimes, a
   * node that passes the filter can be embedded in a parent and might be not focusable by itself.
   * In those cases it is important to focus the parent. Example would be for "Control" granularity,
   * if a switch is not focusable but is embedded into a focusable parent, its parent should be
   * focused.
   */
  @JvmStatic
  fun getFilterIncludingChildren(
    filter: Filter<AccessibilityNodeInfoCompat>
  ): Filter<AccessibilityNodeInfoCompat> {
    return object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        if (node == null) {
          return false
        }
        // If the node does not pass the filter, check its non focusable, visible children.
        if (!filter.accept(node)) {
          return hasMatchingDescendant(node, filter.and(FILTER_NON_FOCUSABLE_VISIBLE_NODE))
        }
        return true
      }
    }
  }

  // TODO: Provides an overall experience of focusing on small nodes on both watch and
  // phone devices.
  /** Filters out nodes which are small and located on the top and bottom borders. */
  @JvmStatic
  fun getFilterExcludingSmallTopAndBottomBorderNode(
    context: Context
  ): Filter<AccessibilityNodeInfoCompat> {
    // For a watch device, we don't want to put focus on the small border nodes. These nodes
    // could be located at the middle of AdapterView and they could be distorted to fit in a
    // round screen when they are near top or bottom borders.
    val screenPxSize = DisplayUtils.getScreenPixelSizeWithoutWindowDecor(context)
    return object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat): Boolean {
        return !AccessibilityNodeInfoUtils.isSmallNodeInHeight(context, node) ||
          !AccessibilityNodeInfoUtils.isTopOrBottomBorderNode(screenPxSize, node)
      }
    }
  }

  /** Filter to identify nodes which are not focusable but visible. */
  @JvmField
  val FILTER_NON_FOCUSABLE_VISIBLE_NODE: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return isVisible(node) && !isAccessibilityFocusable(node)
      }
    }

  /** Filter to identify nodes which are not focusable and not visible but has text. */
  @JvmField
  val FILTER_NON_FOCUSABLE_NON_VISIBLE_HAS_TEXT_NODE: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return !isVisible(node) &&
          !isAccessibilityFocusable(node) &&
          !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node))
      }
    }

  /** Filter for controllable elements. */
  @JvmField
  val FILTER_CONTROL: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        if (node == null) {
          return false
        }
        @RoleName val role = Role.getRole(node)
        return (role == Role.ROLE_BUTTON) ||
          (role == Role.ROLE_IMAGE_BUTTON) ||
          (role == Role.ROLE_EDIT_TEXT) ||
          (role == Role.ROLE_CHECK_BOX) ||
          (role == Role.ROLE_RADIO_BUTTON) ||
          (role == Role.ROLE_TOGGLE_BUTTON) ||
          (role == Role.ROLE_SWITCH) ||
          (role == Role.ROLE_DROP_DOWN_LIST) ||
          (role == Role.ROLE_SEEK_CONTROL) ||
          (role == Role.ROLE_FLOATING_ACTION_BUTTON) ||
          (role == Role.ROLE_VOICE_DICTATION_BUTTON) ||
          // The clickable view in a collection may not be a control, such as each setting item
          // in the Settings page.
          (!nodeIsListOrGridItem(node) && (isClickable(node) || isLongClickable(node)))
      }
    }

  /** Filter for Spannables with links. */
  @JvmField
  val FILTER_LINK: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return SpannableTraversalUtils.hasTargetClickableSpanInNodeTree(
          node, BASE_CLICKABLE_SPAN)
      }
    }

  @JvmField
  val FILTER_CLICKABLE: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        return AccessibilityNodeInfoUtils.isClickable(node)
      }
    }

  @JvmStatic
  fun getFilterIllegalTitleNodeAncestor(
    context: Context
  ): Filter<AccessibilityNodeInfoCompat> {
    return object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        if (isClickable(node) || isLongClickable(node)) {
          return true
        }

        if (FeatureSupport.isWatch(context)) {
          // A window title node can be a descendant of AdapterView in a watch device since the
          // title node may be the first node in a AdapterView.
          return false
        } else {
          @RoleName val role = Role.getRole(node)
          // A window title node should not be a descendant of AdapterView.
          return (role == Role.ROLE_LIST) || (role == Role.ROLE_GRID)
        }
      }
    }
  }

  /**
   * Filter that defines which types of views should be auto-scrolled. Generally speaking, only
   * accepts views that are capable of showing partially-visible data.
   *
   * <p>Accepts the following classes (and sub-classes thereof):
   *
   * <ul>
   *   <li>{@link androidx.recyclerview.widget.RecyclerView} (Should be classified as a List or Grid.)
   *   <li>{@link android.widget.AbsListView} (including both ListView and GridView)
   *   <li>{@link android.widget.AbsSpinner}
   *   <li>{@link android.widget.ScrollView}
   *   <li>{@link android.widget.HorizontalScrollView}
   *   <li>{@code com.sec.android.touchwiz.widget.TwAbsListView}
   * </ul>
   *
   * <p>Specifically excludes {@link android.widget.AdapterViewAnimator} and sub-classes, since they
   * represent overlapping views. Also excludes {@link androidx.viewpager.widget.ViewPager} since it
   * exclusively represents off-screen views.
   */
  @JvmField
  val FILTER_AUTO_SCROLL: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
        if (!isScrollable(node) || !isVisible(node)) {
          return false
        }
        @Role.RoleName val role = Role.getRole(node)
        // TODO: Check if we should include ROLE_ADAPTER_VIEW as a target Role.
        return role == Role.ROLE_DROP_DOWN_LIST ||
          role == Role.ROLE_LIST ||
          role == Role.ROLE_GRID ||
          role == Role.ROLE_SCROLL_VIEW ||
          role == Role.ROLE_HORIZONTAL_SCROLL_VIEW ||
          AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
            node, CLASS_TOUCHWIZ_TWADAPTERVIEW)
      }
    }

  @JvmField
  val FILTER_COLLECTION: Filter<AccessibilityNodeInfoCompat> =
    Filter.node { node ->
      val role = Role.getRole(node)
      (role == Role.ROLE_LIST) ||
        (role == Role.ROLE_GRID) ||
        (role == Role.ROLE_PAGER) ||
        (node != null && node.collectionInfo != null)
    }

  @JvmField
  val FILTER_COLLECTION_ITEM: Filter<AccessibilityNodeInfoCompat> =
    Filter.node { node -> node != null && node.collectionItemInfo != null }

  // This class is not instantiable.

  /**
   * Gets the text of a <code>node</code> by returning the content description (if available) or by
   * returning the text.
   *
   * @param node The node.
   * @return The node text.
   */
  @JvmStatic
  fun getNodeText(node: AccessibilityNodeInfoCompat?): CharSequence? {
    if (node == null) {
      return null
    }

    // Prefer content description over text.
    // TODO: Why are we checking the trimmed length?
    val contentDescription = node.contentDescription
    if (!TextUtils.isEmpty(contentDescription) &&
      (TextUtils.getTrimmedLength(contentDescription) > 0)) {
      return contentDescription
    }

    val text = AccessibilityNodeInfoUtils.getText(node)
    if (!TextUtils.isEmpty(text) && (TextUtils.getTrimmedLength(text) > 0)) {
      return text
    }

    return null
  }

  /**
   * Gets the state description of a <code>node</code>.
   *
   * @param node The node.
   * @return The node state description.
   */
  @JvmStatic
  fun getState(node: AccessibilityNodeInfoCompat?): CharSequence? {
    if (node == null) {
      return null
    }

    val state = node.stateDescription
    if (!TextUtils.isEmpty(state) && (TextUtils.getTrimmedLength(state) > 0)) {
      return state
    }

    return null
  }

  /**
   * Gets the Selected text of a <code>node</code> by returning the selected text.
   *
   * @param node The node.
   * @return The selected node text.
   */
  @JvmStatic
  fun getSelectedNodeText(node: AccessibilityNodeInfoCompat?): CharSequence? {
    if (node == null) {
      return null
    }

    val selectedText =
      subsequenceSafe(
        AccessibilityNodeInfoUtils.getText(node),
        node.textSelectionStart,
        node.textSelectionEnd)
    if (!TextUtils.isEmpty(selectedText) && (TextUtils.getTrimmedLength(selectedText) > 0)) {
      return selectedText
    }

    return null
  }

  /** Returns a sub-string or empty-string, without crashing on invalid subsequence range. */
  @JvmStatic
  fun subsequenceSafe(text: CharSequence?, startIndex: Int, endIndex: Int): CharSequence {
    if (text == null) {
      return ""
    }
    var startIndexVar = startIndex
    var endIndexVar = endIndex
    // Swap start and end.
    if (endIndexVar < startIndexVar) {
      val newStartIndex = endIndexVar
      endIndexVar = startIndexVar
      startIndexVar = newStartIndex
    }
    // Enforce string bounds.
    if (startIndexVar < 0) {
      startIndexVar = 0
    } else if (startIndexVar > text.length) {
      startIndexVar = text.length
    }
    if (endIndexVar < 0) {
      endIndexVar = 0
    } else if (endIndexVar > text.length) {
      endIndexVar = text.length
    }

    return text.subSequence(startIndexVar, endIndexVar)
  }

  /**
   * Gets the text selection indexes safe by adjusting the checking the selection bounds.
   *
   * @param node The node
   * @return the selection indexes
   */
  @JvmStatic
  fun getSelectionIndexesSafe(node: AccessibilityNodeInfoCompat): Pair<Int, Int> {
    var selectionStart = node.textSelectionStart
    var selectionEnd = node.textSelectionEnd
    if (selectionStart < 0) {
      selectionStart = 0
    }
    if (selectionEnd < 0) {
      selectionEnd = selectionStart
    }
    if (selectionEnd < selectionStart) {
      // Swap start and end to make sure they are in order.
      val newStart = selectionEnd
      selectionEnd = selectionStart
      selectionStart = newStart
    }
    return Pair.create(selectionStart, selectionEnd)
  }

  /**
   * Gets the textual representation of the view ID that can be used when no custom label is
   * available. For better readability/listenability, the "_" characters are replaced with spaces.
   *
   * @param node The node
   * @return Readable text of the view Id
   */
  @JvmStatic
  fun getViewIdText(node: AccessibilityNodeInfoCompat?): String? {
    if (node == null) {
      return null
    }

    val resourceName = node.viewIdResourceName ?: return null

    val parsedResourceName = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2)
    if (parsedResourceName.size != 2 ||
      TextUtils.isEmpty(parsedResourceName[0]) ||
      TextUtils.isEmpty(parsedResourceName[1])) {
      return null
    }

    return parsedResourceName[1].replace('_', ' ') // readable View ID text
  }

  @JvmStatic
  fun isPage(node: AccessibilityNodeInfoCompat?): Boolean {
    val parent = if (node == null) null else node.parent
    return (parent != null) && (Role.getRole(parent) == Role.ROLE_PAGER)
  }

  @JvmStatic
  fun getSelectedPageTitle(viewPager: AccessibilityNodeInfoCompat?): CharSequence? {
    if ((viewPager == null) || (Role.getRole(viewPager) != Role.ROLE_PAGER)) {
      return null
    }

    val numChildren = viewPager.childCount // Not the number of pages!
    var title: CharSequence? = null
    for (i in 0 until numChildren) {
      val child = viewPager.getChild(i)
      if (child != null && child.isVisibleToUser) {
        if (title == null) {
          // Try to roughly match RulePagerPage, which uses getNodeText
          // (but completely matching all the time is not critical).
          title = getNodeText(child)
        } else {
          // Multiple visible children, abort.
          return null
        }
      }
    }

    return title
  }

  @JvmStatic
  fun getCustomActions(node: AccessibilityNodeInfoCompat): List<AccessibilityActionCompat> {
    val customActions = ArrayList<AccessibilityActionCompat>()
    for (action in node.actionList) {
      if (isCustomAction(action)) {
        // We don't use custom actions that doesn't have a label
        if (!TextUtils.isEmpty(action.label)) {
          customActions.add(action)
        }
      }
    }

    return customActions
  }

  @JvmStatic
  fun isCustomAction(action: AccessibilityActionCompat): Boolean {
    return action.id > SYSTEM_ACTION_MAX
  }

  /** Returns the root node of the tree containing {@code node}. */
  @JvmStatic
  fun getRoot(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }

    val window = getWindow(node)
    if (window != null) {
      return AccessibilityWindowInfoUtils.getRoot(window)
    }

    val visitedNodes = HashSet<AccessibilityNodeInfoCompat>()
    var current: AccessibilityNodeInfoCompat? = null
    var parent: AccessibilityNodeInfoCompat? = node

    do {
      if (current != null) {
        if (visitedNodes.contains(current)) {
          return null
        }
        visitedNodes.add(current)
      }

      current = parent
      parent = current!!.parent
    } while (parent != null)

    return current
  }

  /**
   * Returns the node of the tree at {@code targetDepth} from the root of the tree containing {@code
   * nodeCompat} with the root node considered as depth 0. This returns the last node available if
   * the target depth is greater than the number of ancestors.
   */
  @JvmStatic
  fun getNthAncestorFromRoot(
    nodeCompat: AccessibilityNodeInfoCompat?, targetDepth: Int
  ): AccessibilityNodeInfoCompat? {
    if (nodeCompat == null || targetDepth <= 0) {
      return null
    }

    var targetDepthVar = targetDepth
    val visitedNodes = ArrayList<AccessibilityNodeInfoCompat>()
    var current: AccessibilityNodeInfoCompat? = nodeCompat

    do {
      if (visitedNodes.contains(current)) {
        break
      }

      visitedNodes.add(current!!)
      current = current.parent
    } while (current != null)

    if (targetDepthVar >= visitedNodes.size) {
      targetDepthVar = visitedNodes.size - 1
    }

    val nodeIndex = visitedNodes.size - 1 - targetDepthVar
    return visitedNodes[nodeIndex]
  }

  /** Returns the type of the window containing {@code nodeCompat}. */
  @JvmStatic
  fun getWindowType(nodeCompat: AccessibilityNodeInfoCompat?): Int {
    if (nodeCompat == null) {
      return WINDOW_TYPE_NONE
    }

    val windowInfoCompat = getWindow(nodeCompat) ?: return WINDOW_TYPE_NONE

    if (isPictureInPicture(nodeCompat)) {
      return WINDOW_TYPE_PICTURE_IN_PICTURE
    }

    return windowInfoCompat.type
  }

  /** Wrapper for AccessibilityNodeInfoCompat.getWindow() that handles SecurityException. */
  @JvmStatic
  fun getWindow(node: AccessibilityNodeInfoCompat?): AccessibilityWindowInfoCompat? {
    // This implementation is redundant with getWindow(AccessibilityNodeInfo) because there are no
    // un/wrap() functions for AccessibilityWindowInfoCompat.

    if (node == null) {
      return null
    }

    try {
      return node.window
    } catch (e: SecurityException) {
      LogUtils.e(TAG, "SecurityException in AccessibilityWindowInfoCompat.getWindow()")
      return null
    }
  }

  @JvmStatic
  fun getWindow(node: AccessibilityNodeInfo?): AccessibilityWindowInfo? {
    if (node == null) {
      return null
    }

    try {
      return node.window
    } catch (e: SecurityException) {
      LogUtils.e(TAG, "SecurityException in AccessibilityWindowInfo.getWindow()")
      return null
    }
  }

  /**
   * Returns whether a node can receive focus from focus traversal or touch exploration. One of the
   * following must be true:
   *
   * <ul>
   *   <li>The node is actionable (see {@link #isFocusableOrClickable(AccessibilityNodeInfoCompat)})
   *   <li>The node is a top-level list item (see {@link
   *       #isTopLevelScrollItem(AccessibilityNodeInfoCompat)} and is a speaking node
   * </ul>
   *
   * @param node The node to check.
   * @return {@code true} of the node is accessibility focusable.
   */
  @JvmStatic
  fun isAccessibilityFocusable(node: AccessibilityNodeInfoCompat?): Boolean {
    return isFocusableOrClickable(node) ||
      (isTopLevelScrollItem(node) && isSpeakingNode(node!!, null, HashSet()))
  }

  /**
   * Returns whether a node should receive accessibility focus from navigation. This method should
   * never be called recursively, since it traverses up the parent hierarchy on every call.
   *
   * @see #findFocusFromHover(AccessibilityNodeInfoCompat) for touch exploration
   * @see
   *     com.google.android.accessibility.talkback.focusmanagement.NavigationTarget#createNodeFilter(int,
   *     Map) for linear navigation
   */
  @JvmStatic
  fun shouldFocusNode(node: AccessibilityNodeInfoCompat?): Boolean {
    return shouldFocusNode(node, null, true)
  }

  @JvmStatic
  fun shouldFocusNode(
    node: AccessibilityNodeInfoCompat?,
    speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>?
  ): Boolean {
    return shouldFocusNode(node, speakingNodesCache, true)
  }

  @JvmStatic
  fun shouldFocusNode(
    node: AccessibilityNodeInfoCompat?,
    speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>?,
    checkChildren: Boolean
  ): Boolean {
    if (node == null) {
      LogUtils.v(TAG, "Don't focus, node=null")
      return false
    }
    // Inside views that support web navigation, we delegate focus to the view itself and
    // assume that it navigates to and focuses the correct elements.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      // In history, we loosen the "visibility" check for web element: A web node can be focused
      // even if it's not visibleToUser(). However we should hold the baseline that if the WebView
      // container is not visible, we should not focus on its descendants.
      val webViewContainer = WebInterfaceUtils.ascendToWebViewContainer(node)
      return webViewContainer != null && webViewContainer.isVisibleToUser
    }

    if (!isVisible(node)) {
      logShouldFocusNode(
        checkChildren, FOCUS_FAIL_NOT_VISIBLE, "Don't focus, is not visible: ", node)
      return false
    }

    if (isPictureInPicture(node)) {
      // For picture-in-picture, allow focusing the root node, and any app controls inside the
      // pic-in-pic window.
      return true
    } else {
      // Reject all non-leaf nodes that are neither actionable nor focusable, and have the same
      // bounds as the window.
      if (areBoundsIdenticalToWindow(node) &&
        node.childCount > 0 &&
        !isFocusableOrClickable(node)) {
        logShouldFocusNode(
          checkChildren,
          FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN,
          "Don't focus, bounds are same as window root node bounds, node has children and" +
            " is neither actionable nor focusable: ",
          node)
        return false
      }
    }

    val visitedNodes = HashSet<AccessibilityNodeInfoCompat>()
    // This checks if a node is clickable, focusable, screen reader focusable, or a direct
    // spekaing child of a scrollable container.
    val accessibilityFocusable =
      isFocusableOrClickable(node) ||
        (isTopLevelScrollItem(node) && isSpeakingNode(node, null, visitedNodes))

    if (!checkChildren) {
      // End of the line. Don't check children and don't allow any recursion.
      // checkChildren is only false in the shouldFocusNode call below. This is to avoid
      // repetitive checks down the tree when looking up at the ancestors.
      LogUtils.d(
        TAG, "checkChildren=false and isAccessibilityFocusable=%s", accessibilityFocusable)
      return accessibilityFocusable
    }

    // A node that is deemed accessibility focusable shouldn't actually get focus if it has
    // nothing to speak. For example, a view may be focusable, but if it has no text and all of
    // its children are clickable, focus should go on each child individually and not on this
    // view.
    // Note: This is redundant for nodes that pass isSpeakingNode above
    // Note: A special case exists for unlabeled buttons which otherwise wouldn't get focus.
    if (accessibilityFocusable) {
      visitedNodes.clear()
      // For TalkBack labeling feature, but this may still result in focusing non-speaking nodes.
      // We should try to narrow down the check to close to TalkBackLabelManager#needsLabel.
      if (node.childCount == 0) {
        logShouldFocusNode(
          checkChildren, NONE, "Focus, is focusable and cannot keep search children: ", node)
        return true
      } else if (isSpeakingNode(node, speakingNodesCache, visitedNodes)) {
        logShouldFocusNode(
          checkChildren, NONE, "Focus, is focusable and has something to speak: ", node)
        return true
      } else {
        logShouldFocusNode(
          checkChildren,
          FOCUS_FAIL_NOT_SPEAKABLE,
          "Don't focus, is focusable but has nothing to speak: ",
          node)
        return false
      }
    }

    // At this point, the node is an unfocusable target.
    // If it has no focusable ancestors, but it still has text, then it should receive focus and be
    // read aloud.
    val filter =
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
          return shouldFocusNode(node, speakingNodesCache, false)
        }
      }

    if (!hasMatchingAncestor(node, filter) && (hasText(node) || hasStateDescription(node))) {
      logShouldFocusNode(checkChildren, NONE, "Focus, has text and no focusable ancestors: ", node)
      return true
    }

    logShouldFocusNode(
      checkChildren,
      FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS,
      "Don't focus, failed all focusability tests: ",
      node)
    return false
  }

  private fun logShouldFocusNode(
    checkChildren: Boolean,
    @DiagnosticType diagnosticType: Int?,
    message: String,
    node: AccessibilityNodeInfoCompat
  ) {
    // When shouldFocusNode calls itself, the logs get inundated by unnecessary info about the
    // ancestors. So only log when checkChildren is true.
    if (checkChildren) {
      if (diagnosticType != NONE) {
        DiagnosticOverlayUtils.appendLog(diagnosticType, node)
      }
      // Show debug logs for #shouldFocusNode. Verbose logs will show for #isSpeakingNode
      LogUtils.v(TAG, "%s %s", message, node)
    }
  }

  @JvmStatic
  fun isPictureInPicture(node: AccessibilityNodeInfoCompat): Boolean {
    return isPictureInPicture(node.unwrap())
  }

  @JvmStatic
  fun isPictureInPicture(node: AccessibilityNodeInfo?): Boolean {
    return node != null && AccessibilityWindowInfoUtils.isPictureInPicture(getWindow(node))
  }

  /**
   * Returns the node that should receive focus from hover by starting from the touched node and
   * calling {@link #shouldFocusNode} at each level of the view hierarchy and exclude WebView
   * container node.
   */
  @JvmStatic
  fun findFocusFromHover(
    touched: AccessibilityNodeInfoCompat?
  ): AccessibilityNodeInfoCompat? {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
      touched, FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW)
  }

  /**
   * Returns whether a node can be spoken.
   *
   * <p>A node should be spoken if it has text, is checkable, or has children that should be spoken
   * but can't be focused themselves. This method can call itself recursively through {@link
   * #hasNonActionableSpeakingChildren}.
   *
   * <p>Note: This is called in the context of looking for a a11y focusable node through {@link
   * #shouldFocusNode} and {@link #isAccessibilityFocusable}
   *
   * @param node the node to check
   * @param speakingNodesCache the cache that holds the speaking results for visited nodes
   * @param visitedNodes the set of nodes that have already been visited
   * @return {@code true} if the node can be spoken
   */
  private fun isSpeakingNode(
    node: AccessibilityNodeInfoCompat,
    speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>?,
    visitedNodes: MutableSet<AccessibilityNodeInfoCompat>
  ): Boolean {
    if (speakingNodesCache != null && speakingNodesCache.containsKey(node)) {
      return speakingNodesCache[node]!!
    }

    var result = false
    if (hasText(node)) {
      LogUtils.v(TAG, "Speaking, has text")
      result = true
    } else if (hasStateDescription(node)) {
      LogUtils.v(TAG, "Speaking, has state description")
      result = true
    } else if (node.isCheckable) { // Special case for check boxes.
      LogUtils.v(TAG, "Speaking, is checkable")
      result = true
    } else if (hasNonActionableSpeakingChildren(node, speakingNodesCache, visitedNodes)) {
      // Special case for containers with non-focusable content. In this case, the container should
      // speak its non-focusable yet speakable content.
      LogUtils.v(TAG, "Speaking, has non-actionable speaking children")
      result = true
    }

    if (speakingNodesCache != null) {
      speakingNodesCache[node] = result
    }

    return result
  }

  /**
   * Returns whether a node has children that are not actionable/focusable but should be spoken.
   *
   * <p>This is done by ignoring any children nodes that are actionable/focusable, and checking the
   * remaining for speaking ability. Also considers offscreen/invisible children which are
   * non-actionable but which have speakable text.
   *
   * @param node the node to check
   * @param speakingNodesCache the cache that holds the speaking results for visited nodes
   * @param visitedNodes the set of nodes that have already been visited.
   * @return {@code true} if the node has children that are speaking
   */
  private fun hasNonActionableSpeakingChildren(
    node: AccessibilityNodeInfoCompat,
    speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>?,
    visitedNodes: MutableSet<AccessibilityNodeInfoCompat>
  ): Boolean {
    val childCount = node.childCount

    for (i in 0 until childCount) {
      val child = node.getChild(i)

      if (child == null) {
        LogUtils.v(TAG, "Child %d is null, skipping it", i)
        continue
      }

      if (!visitedNodes.add(child)) {
        return false
      }

      // Ignore invisible nodes.
      if (!isVisible(child)) {
        LogUtils.v(TAG, "Child %d, %s is invisible, skipping it", i, printId(node))
        continue
      }

      // Ignore focusable nodes
      if (isFocusableOrClickable(child)) {
        LogUtils.v(TAG, "Child %d, %s is focusable or clickable, skipping it", i, printId(node))
        continue
      }

      // Ignore top level scroll items that 1) are speaking and 2) have non-clickable parents. This
      // means that a scrollable container that is clickable should get focus before its children.
      if ((isTopLevelScrollItem(child) && isSpeakingNode(child, speakingNodesCache, visitedNodes)) &&
        !(isClickable(node) || isLongClickable(node))) {

        LogUtils.v(TAG, "Child %d, %s is a top level scroll item, skipping it", i, printId(node))
        continue
      }

      // Recursively check non-focusable child nodes.
      if (isSpeakingNode(child, speakingNodesCache, visitedNodes)) {
        LogUtils.v(TAG, "Does have actionable speaking children (child %d, %s)", i, printId(node))
        return true
      }
    }

    LogUtils.v(TAG, "Does not have non-actionable speaking children. Examining invisible children")
    return hasInvisibleNonActionableSpeakingChildren(node, childCount)
  }

  private fun hasInvisibleNonActionableSpeakingChildren(
    node: AccessibilityNodeInfoCompat, childCount: Int
  ): Boolean {
    // We don't want the presence of invisible children to lead to focus being set on a scrollable
    // parent that is capable of showing partially-visible data.
    if (FILTER_AUTO_SCROLL.accept(node)) {
      return false
    }

    // We look at invisible children and return true if an invisible child is non-actionable and
    // has associated text. Without this check, a parent would be considered unfocusable, and this
    // would cause ACTION_SHOW_ON_SCREEN to fail when the non-actionable/speakable child nodes of
    // a container are offscreen.
    for (i in 0 until childCount) {
      val child = node.getChild(i)

      if (child == null) {
        LogUtils.v(TAG, "Child %d is null, skipping it", i)
        continue
      }

      if (!child.isVisibleToUser &&
        hasText(child) &&
        !(child.isScreenReaderFocusable || isActionableForAccessibility(child))) {
        LogUtils.v(
          TAG, "Non-actionable invisible node with text found (child %d, %s)", i, printId(node))
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun countVisibleChildren(node: AccessibilityNodeInfoCompat?): Int {
    if (node == null) {
      return 0
    }
    val childCount = node.childCount
    var childVisibleCount = 0
    for (i in 0 until childCount) {
      val child = node.getChild(i)
      if (child != null && child.isVisibleToUser) {
        ++childVisibleCount
      }
    }
    return childVisibleCount
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
  @JvmStatic
  fun isActionableForAccessibility(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    // Nodes that are clickable are always actionable.
    if (isClickable(node) || isLongClickable(node)) {
      return true
    }

    if (node.isFocusable) {
      return true
    }

    if (WebInterfaceUtils.hasNativeWebContent(node)) {
      return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS)
    }

    return supportsAnyAction(
      node,
      AccessibilityNodeInfoCompat.ACTION_FOCUS,
      AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
      AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT)
  }

  @JvmStatic
  fun isSelfOrAncestorFocused(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      (node.isAccessibilityFocused ||
        hasMatchingAncestor(
          node,
          object : Filter<AccessibilityNodeInfoCompat>() {
            override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
              return (node != null) && node.isAccessibilityFocused
            }
          }))
  }

  /** Returns whether {@code node} is editable or has an ancestor that is editable. */
  @JvmStatic
  fun isSelfOrAncestorEditable(node: AccessibilityNodeInfoCompat?): Boolean {
    return getSelfOrMatchingAncestor(node, Filter.node { n -> n.isEditable }) != null
  }

  @JvmStatic
  fun isSelfOrAncestorRoleEditText(node: AccessibilityNodeInfoCompat?): Boolean {
    return isSelfOrAncestorWithRole(node, Role.ROLE_EDIT_TEXT)
  }

  @JvmStatic
  fun isSelfOrAncestorRoleWebView(node: AccessibilityNodeInfoCompat?): Boolean {
    return isSelfOrAncestorWithRole(node, Role.ROLE_WEB_VIEW)
  }

  private fun isSelfOrAncestorWithRole(node: AccessibilityNodeInfoCompat?, role: Int): Boolean {
    return getSelfOrMatchingAncestor(node, Filter.node { n -> Role.getRole(n) == role }) != null
  }

  /** Returns whether {@code node} or its ancestor has the given {@code chromeRole}. */
  @JvmStatic
  fun isSelfOrAncestorWithChromeRole(
    node: AccessibilityNodeInfoCompat?, chromeRole: String
  ): Boolean {
    return getSelfOrMatchingAncestor(
      node, Filter.node { n -> TextUtils.equals(getChromeRole(n), chromeRole) }) != null
  }

  /** Returns whether {@code node} has the chrome role "link". */
  @JvmStatic
  fun isChromeRoleLink(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null && TextUtils.equals(getChromeRole(node), CHROME_ROLE_LINK)
  }

  private fun getChromeRole(node: AccessibilityNodeInfoCompat?): CharSequence? {
    if (node == null) {
      return ""
    }
    val info = node.unwrap() ?: return ""
    return info.extras.getCharSequence(EXTRAS_KEY_CHROME_ROLE)
  }

  /**
   * Returns whether {@code node} is interactable with arrow keys. That is, the node supports at
   * least one of the following:
   *
   * <ul>
   *   <li>{@link Role.ROLE_SEEK_CONTROL}
   * </ul>
   *
   * @return {@code true} if node is self interactable with arrow keys.
   */
  @JvmStatic
  fun isInteractableWithArrowKeys(node: AccessibilityNodeInfoCompat?): Boolean {
    return Role.getRole(node) == Role.ROLE_SEEK_CONTROL
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
  @JvmStatic
  fun isClickable(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      (node.isClickable ||
        supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK))
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
  @JvmStatic
  fun isLongClickable(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      (node.isLongClickable ||
        supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK))
  }

  /**
   * Returns whether the node is focusable. That is, the node supports at least one of the
   * following:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_FOCUS}
   * </ul>
   */
  @JvmStatic
  fun isFocusable(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      (node.isFocusable ||
        supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS))
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
  @JvmStatic
  fun isExpandable(node: AccessibilityNodeInfoCompat?): Boolean {
    return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_EXPAND)
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
  @JvmStatic
  fun isCollapsible(node: AccessibilityNodeInfoCompat?): Boolean {
    return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_COLLAPSE)
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
  @JvmStatic
  fun isDismissible(node: AccessibilityNodeInfoCompat?): Boolean {
    return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_DISMISS)
  }

  /** Returns {@code true} if the node is on keyboard. */
  @JvmStatic
  fun isKeyboard(source: AccessibilityNodeInfo?): Boolean {
    return isKeyboard(AccessibilityNodeInfoUtils.toCompat(source))
  }

  /** Returns {@code true} if the node is on keyboard. */
  @JvmStatic
  fun isKeyboard(source: AccessibilityNodeInfoCompat?): Boolean {
    if (source == null) {
      return false
    }
    val window = getWindow(source) ?: return false
    return AccessibilityWindowInfoUtils.isImeWindow(window)
  }

  /**
   * Check whether a given node has a matching ancestor given a filter.
   *
   * @param node The node to examine.
   * @param filter The filter to match the nodes against.
   * @return {@code true} if one of the node's ancestors is matching the filter.
   */
  @JvmStatic
  fun hasMatchingAncestor(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): Boolean {
    return (node != null) && (getMatchingAncestor(node, filter) != null)
  }

  // TODO: Discuss with framework owner to make unread notification context available
  //  to the app side.
  /**
   * Checks whether the node is the unread notification dot on the wearable sysUI.
   *
   * @param node the node to check
   * @return {@code true} if the node is the unread notification dot on the wearable sysUI.
   */
  @JvmStatic
  fun isWearUnreadNotificationDot(node: AccessibilityNodeInfoCompat?): Boolean {
    return (node != null) &&
      TextUtils.equals(node.viewIdResourceName, VIEW_ID_WEAR_UNREAD_NOTIFICATION_DOT)
  }

  /** Returns whether the node is the Pin edit field at unlock screen. */
  @JvmStatic
  fun isPinEntry(node: AccessibilityNodeInfo?): Boolean {
    return isPinEntry(AccessibilityNodeInfoUtils.toCompat(node))
  }

  @JvmStatic
  fun isPinEntry(node: AccessibilityNodeInfoCompat?): Boolean {
    return (node != null) &&
      TextUtils.equals(node.viewIdResourceName, VIEW_ID_RESOURCE_NAME_PIN_ENTRY)
  }

  /**
   * Check whether a given node or any of its ancestors matches the given filter.
   *
   * @param node The node to examine.
   * @param filter The filter to match the nodes against.
   * @return {@code true} if the node or one of its ancestors matches the filter.
   */
  @JvmStatic
  fun isOrHasMatchingAncestor(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): Boolean {
    return (node != null) && (getSelfOrMatchingAncestor(node, filter) != null)
  }

  /** Check whether a given node has any descendant matching a given filter. */
  @JvmStatic
  fun hasMatchingDescendant(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): Boolean {
    return (node != null) && (getMatchingDescendant(node, filter) != null)
  }

  /** Checks whether a given node or any of its descendants matches the given filter. */
  @JvmStatic
  fun isOrHasMatchingDescendant(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): Boolean {
    return (node != null) && (getSelfOrMatchingDescendant(node, filter) != null)
  }

  /** Returns depth of node in node-tree, where root has depth=0. */
  @JvmStatic
  fun findDepth(node: AccessibilityNodeInfoCompat?): Int {
    if (node == null) {
      return -1
    }
    val counter = NodeCounter()
    processSelfAndAncestors(node, counter)
    return counter.count - 1
  }

  private class NodeCounter : Filter<AccessibilityNodeInfoCompat>() {
    var count = 0

    override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
      ++count
      return false
    }
  }

  /** Applies filter to ancestor nodes. */
  @JvmStatic
  fun processSelfAndAncestors(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ) {
    if (node != null) {
      isOrHasMatchingAncestor(node, filter)
    }
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor.
   * Returns {@code null} if no nodes match.
   */
  @JvmStatic
  fun getSelfOrMatchingAncestor(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }
    if (filter.accept(node)) {
      return node
    }

    return getMatchingAncestor(node, filter)
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor,
   * ending the ancestor search once it reaches {@code end}. The search is inclusive of {@code node}
   * but exclusive of {@code end}. If {@code node} equals {@code end}, then {@code node} is an
   * eligible match. Returns {@code null} if no nodes match.
   */
  @JvmStatic
  fun getSelfOrMatchingAncestor(
    node: AccessibilityNodeInfoCompat?,
    end: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }
    if (filter.accept(node)) {
      return node
    }
    return getMatchingAncestor(node, end, filter)
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching descendant.
   * Returns {@code null} if no nodes match.
   */
  @JvmStatic
  fun getSelfOrMatchingDescendant(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }
    if (filter.accept(node)) {
      return node
    }
    return getMatchingDescendant(node, filter)
  }

  /** Processes subtree of root by {@code filter}. */
  @JvmStatic
  fun processSubtree(
    root: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ) {

    AccessibilityNodeInfoUtils.getSelfOrMatchingDescendant(
      root,
      Filter.node { node ->
        filter.accept(node)
        false // Force search to traverse whole subtree.
      })
  }

  /**
   * Determines whether the two nodes are in the same branch; that is, they are equal or one is the
   * ancestor of the other.
   */
  @JvmStatic
  fun areInSameBranch(
    node1: AccessibilityNodeInfoCompat?,
    node2: AccessibilityNodeInfoCompat?
  ): Boolean {
    if (node1 != null && node2 != null) {
      // Same node?
      if (node1 == node2) {
        return true
      }

      // Is node1 an ancestor of node2?
      val matchNode1 =
        object : Filter<AccessibilityNodeInfoCompat>() {
          override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
            return node != null && node == node1
          }
        }
      if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node2, matchNode1)) {
        return true
      }

      // Is node2 an ancestor of node1?
      val matchNode2 =
        object : Filter<AccessibilityNodeInfoCompat>() {
          override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
            return node != null && node == node2
          }
        }
      if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node1, matchNode2)) {
        return true
      }
    }

    return false
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}. Returns {@code
   * null} if no nodes match.
   */
  @JvmStatic
  fun getMatchingAncestor(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    return getMatchingAncestor(node, null, filter)
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}, terminating the
   * search once it reaches {@code end}. The search is exclusive of both {@code node} and {@code
   * end}. Returns {@code null} if no nodes match.
   */
  private fun getMatchingAncestor(
    node: AccessibilityNodeInfoCompat?,
    end: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }

    val ancestors = HashSet<AccessibilityNodeInfoCompat>()

    ancestors.add(node)
    var current: AccessibilityNodeInfoCompat? = node.parent

    while (current != null) {
      if (!ancestors.add(current)) {
        // Already seen this node, so abort!
        return null
      }

      if (end != null && current == end) {
        // Reached the end node, so abort!
        return null
      }

      if (filter.accept(current)) {
        return current
      }

      current = current.parent
    }

    return null
  }

  /**
   * Returns the number of ancestors matching the given filter. Does not include the current node in
   * the count, even if it matches the filter. If there is a cycle in the ancestor hierarchy, then
   * this method will return 0.
   */
  @JvmStatic
  fun countMatchingAncestors(
    node: AccessibilityNodeInfoCompat?, filter: Filter<AccessibilityNodeInfoCompat>
  ): Int {
    if (node == null) {
      return 0
    }

    val ancestors = HashSet<AccessibilityNodeInfoCompat>()
    var matchingAncestors = 0

    ancestors.add(node)
    var current: AccessibilityNodeInfoCompat? = node.parent

    while (current != null) {
      if (!ancestors.add(current)) {
        // Already seen this node, so abort!
        return 0
      }

      if (filter.accept(current)) {
        matchingAncestors++
      }

      current = current.parent
    }

    return matchingAncestors
  }

  private fun getMatchingDescendant(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>,
    endFilter: Filter<AccessibilityNodeInfoCompat>?,
    visitedNodes: HashSet<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }

    if (visitedNodes.contains(node)) {
      return null
    } else {
      visitedNodes.add(node)
    }

    val childCount = node.childCount
    for (i in 0 until childCount) {
      val child = node.getChild(i) ?: continue

      if (filter.accept(child)) {
        return child // child was already obtained by node.getChild().
      }

      if (endFilter != null && endFilter.accept(child)) {
        continue
      }

      val childMatch = getMatchingDescendant(child, filter, endFilter, visitedNodes)
      if (childMatch != null) {
        return childMatch
      }
    }

    return null
  }

  /**
   * Returns the first child (by depth-first search) of {@code node} that matches the {@code
   * filter}, and skips the nodes that match the {@code endFilter}. Returns {@code null} if no nodes
   * match.
   */
  @JvmStatic
  fun getMatchingDescendant(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>,
    endFilter: Filter<AccessibilityNodeInfoCompat>?
  ): AccessibilityNodeInfoCompat? {
    return getMatchingDescendant(node, filter, endFilter, HashSet())
  }

  /**
   * Returns the first child (by depth-first search) of {@code node} that matches the {@code
   * filter}. Returns {@code null} if no nodes match.
   */
  @JvmStatic
  fun getMatchingDescendant(
    node: AccessibilityNodeInfoCompat?, filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    return getMatchingDescendant(node, filter, /* endFilter= */ null, HashSet())
  }

  /** Returns all descendants that match filter but skips the nested. */
  @JvmStatic
  fun getMatchingDescendantsNotNested(
    node: AccessibilityNodeInfoCompat?, filter: Filter<AccessibilityNodeInfoCompat>
  ): List<AccessibilityNodeInfoCompat>? {
    if (node == null) {
      return null
    }
    val matches = ArrayList<AccessibilityNodeInfoCompat>()
    getMatchingDescendants(node, filter, /* matchChild= */ false, HashSet(), matches)
    return matches
  }

  /** Returns all descendants that match filter. */
  @JvmStatic
  fun getMatchingDescendantsOrRoot(
    node: AccessibilityNodeInfoCompat?, filter: Filter<AccessibilityNodeInfoCompat>
  ): List<AccessibilityNodeInfoCompat>? {
    if (node == null) {
      return null
    }
    val matches = ArrayList<AccessibilityNodeInfoCompat>()
    getMatchingDescendants(node, filter, /* matchChild= */ true, HashSet(), matches)
    return matches
  }

  /**
   * Returns all descendants that match filter, until the stopNode is found. At that point, the
   * search will stop. Note that the stopNode is included in the results, if it matches the filter.
   */
  @JvmStatic
  fun getMatchingDescendantsOrRootUntilNode(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>,
    stopNode: AccessibilityNodeInfoCompat?
  ): List<AccessibilityNodeInfoCompat>? {
    if (node == null) {
      return null
    }
    val matches = ArrayList<AccessibilityNodeInfoCompat>()
    getMatchingDescendantsCore(
      node,
      filter,
      /* matchChild= */ true,
      HashSet(),
      matches,
      /* stopNode= */ stopNode,
      /* searchControlFlag= */ SearchControlFlag())
    return matches
  }

  /**
   * Collects all descendants that match filter, into matches.
   *
   * @param node The root node to start searching.
   * @param filter The filter to match the nodes against.
   * @param matchChild Flag that allows match with the childs of the matched nodes.
   * @param visitedNodes The set of nodes already visited, for protection against loops. This will
   *     be modified.
   * @param matches The list of nodes matching filter. This will be appended to.
   */
  private fun getMatchingDescendants(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>,
    matchChild: Boolean,
    visitedNodes: MutableSet<AccessibilityNodeInfoCompat>,
    matches: MutableList<AccessibilityNodeInfoCompat>
  ) {
    getMatchingDescendantsCore(
      node,
      filter,
      matchChild,
      visitedNodes,
      matches,
      /* stopNode= */ null,
      /* searchControlFlag= */ null)
  }

  /**
   * A flag to indicate whether the stop node has been found. Using a class instead of a boolean
   * flag allows {@link #getMatchingDescendantsCore} to modify the flag and have the updated value
   * reflected in other branches of the recursive search.
   */
  private class SearchControlFlag {
    var stopNodeHasBeenFound = false
  }

  /**
   * Collects all descendants that match filter, into matches.
   *
   * @param node The root node to start searching.
   * @param filter The filter to match the nodes against.
   * @param matchChild Flag that allows match with the childs of the matched nodes.
   * @param visitedNodes The set of nodes already visited, for protection against loops. This will
   *     be modified.
   * @param matches The list of nodes matching filter. This will be appended to.
   * @param stopNode The node to stop searching at. Note that this node is included in the matches,
   *     if it matches the filter.
   * @param searchControlFlag A flag to indicate whether the stop node has been found. See {@link
   *     SearchControlFlag} for details.
   */
  private fun getMatchingDescendantsCore(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>,
    matchChild: Boolean,
    visitedNodes: MutableSet<AccessibilityNodeInfoCompat>,
    matches: MutableList<AccessibilityNodeInfoCompat>,
    stopNode: AccessibilityNodeInfoCompat?,
    searchControlFlag: SearchControlFlag?
  ) {

    if (node == null) {
      return
    }

    // Update visited nodes.
    if (visitedNodes.contains(node)) {
      return
    } else {
      visitedNodes.add(node)
    }

    // Stop searching if the stop node has been found.
    if (searchControlFlag != null && searchControlFlag.stopNodeHasBeenFound) {
      return
    }

    // If node matches filter... collect node.
    if (filter.accept(node)) {
      matches.add(node)
    }

    // If the stop node has been found, future searches can be skipped, even if the stopNode does
    // not match the filter.
    if (searchControlFlag != null && node == stopNode) {
      searchControlFlag.stopNodeHasBeenFound = true
    }

    // For each child of node...
    if (!matches.contains(node) || matchChild) {
      val childCount = node.childCount
      for (i in 0 until childCount) {
        val child = node.getChild(i) ?: continue
        getMatchingDescendantsCore(
          child, filter, matchChild, visitedNodes, matches, stopNode, searchControlFlag)
      }
    }
  }

  /**
   * Check whether a given node is scrollable.
   *
   * @param node The node to examine.
   * @return {@code true} if the node is scrollable.
   */
  @JvmStatic
  fun isScrollable(node: AccessibilityNodeInfoCompat?): Boolean {
    // In some cases node#isScrollable lies. (Notably, some nodes that correspond to WebViews claim
    // to be scrollable, but do not support any scroll actions. This seems to stem from a bug in the
    // translation from the DOM to the AccessibilityNodeInfo.) To avoid labeling views that don't
    // support scrolling (e.g. REFERTO), check for the explicit presence of
    // AccessibilityActions.
    return supportsAnyAction(
      node,
      AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
      AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
      AccessibilityActionCompat.ACTION_SCROLL_DOWN,
      AccessibilityActionCompat.ACTION_SCROLL_UP,
      AccessibilityActionCompat.ACTION_SCROLL_RIGHT,
      AccessibilityActionCompat.ACTION_SCROLL_LEFT)
  }

  /**
   * Returns whether the specified node has text. For the purposes of this check, any node with a
   * CollectionInfo is considered to not have text since its text and content description are used
   * only for collection transitions.
   *
   * @param node The node to check.
   * @return {@code true} if the node has text.
   */
  private fun hasText(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      node.collectionInfo == null &&
      (!TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node)) ||
        !TextUtils.isEmpty(node.contentDescription) ||
        !TextUtils.isEmpty(node.hintText))
  }

  /**
   * Returns whether the specified node has state description.
   *
   * @param node The node to check.
   * @return {@code true} if the node has state description.
   */
  private fun hasStateDescription(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      (!TextUtils.isEmpty(node.stateDescription) ||
        node.isCheckable ||
        hasValidRangeInfo(node))
  }

  /**
   * Returns if a node is focusable or clickable.
   *
   * <p>This is used in {@link #shouldFocusNode} and {@link #isAccessibilityFocusable}
   *
   * @param node the node to check
   * @return {@code true} if the node is focusable or clickable
   */
  private fun isFocusableOrClickable(node: AccessibilityNodeInfoCompat?): Boolean {
    return (node != null) &&
      isVisible(node) &&
      (node.isScreenReaderFocusable || isActionableForAccessibility(node))
  }

  /**
   * Determines whether a node is a top-level item in a scrollable container.
   *
   * @param node The node to test.
   * @return {@code true} if {@code node} is a top-level item in a scrollable container.
   */
  @JvmStatic
  fun isTopLevelScrollItem(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    if (!isVisible(node)) {
      return false
    }

    val parent = node.parent
    return isScrollItem(parent)
  }

  private fun isScrollItem(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      // Not a child node of anything.
      return false
    }

    // Drop down lists (spinners) are not included to retain the old behavior of focusing on
    // the spinner itself rather than on the single visible item.
    // A spinner being scrollable is disingenuous since the scrollable list inside isn't exposed
    // without interaction.
    if (Role.getRole(node) == Role.ROLE_DROP_DOWN_LIST) {
      return false
    }

    // A node with a scrollable parent is a top level scroll item.
    if (isScrollable(node)) {
      return true
    }

    @Role.RoleName val parentRole = Role.getRole(node)
    // Note that ROLE_DROP_DOWN_LIST(Spinner) is not accepted.
    // RecyclerView is classified as a list or grid based on its CollectionInfo.
    // These parents may not be scrollable in some cases, like if the list is too short to be
    // scrolled, but their children should still be considered top level scroll items.
    return parentRole == Role.ROLE_LIST ||
      parentRole == Role.ROLE_GRID ||
      parentRole == Role.ROLE_SCROLL_VIEW ||
      parentRole == Role.ROLE_HORIZONTAL_SCROLL_VIEW ||
      nodeMatchesAnyClassByType(node, CLASS_TOUCHWIZ_TWADAPTERVIEW)
  }

  @JvmStatic
  fun hasAncestor(
    node: AccessibilityNodeInfoCompat?, targetAncestor: AccessibilityNodeInfoCompat?
  ): Boolean {
    if (node == null || targetAncestor == null) {
      return false
    }

    val filter =
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
          return targetAncestor == node
        }
      }

    return (getMatchingAncestor(node, filter) != null)
  }

  @JvmStatic
  fun hasDescendant(
    node: AccessibilityNodeInfoCompat?,
    targetDescendant: AccessibilityNodeInfoCompat?
  ): Boolean {
    if (node == null || targetDescendant == null) {
      return false
    }

    val filter =
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
          return targetDescendant == node
        }
      }

    return (getMatchingDescendant(node, filter) != null)
  }

  /**
   * Determines if the generating class of an {@link AccessibilityNodeInfoCompat} matches a given
   * {@link Class} by type.
   *
   * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by the accessibility
   *     framework.
   * @param referenceClass A {@link Class} to match by type or inherited type.
   * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object matches the {@link
   *     Class} by type or inherited type, {@code false} otherwise.
   */
  @JvmStatic
  fun nodeMatchesClassByType(
    node: AccessibilityNodeInfoCompat?, referenceClass: Class<*>?
  ): Boolean {
    if ((node == null) || (referenceClass == null)) {
      return false
    }

    // Attempt to take a shortcut.
    val nodeClassName = node.className
    if (TextUtils.equals(nodeClassName, referenceClass.name)) {
      return true
    }

    return ClassLoadingCache.checkInstanceOf(nodeClassName, referenceClass)
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
  @JvmStatic
  fun nodeMatchesAnyClassByType(
    node: AccessibilityNodeInfoCompat?, vararg referenceClasses: Class<*>?
  ): Boolean {
    if (node == null) {
      return false
    }

    for (referenceClass in referenceClasses) {
      if (ClassLoadingCache.checkInstanceOf(node.className, referenceClass)) {
        return true
      }
    }

    return false
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  @JvmStatic
  fun recycleNodes(nodes: MutableCollection<AccessibilityNodeInfoCompat>) {
    nodes.clear()
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  @JvmStatic
  fun recycleNodes(vararg nodes: AccessibilityNodeInfo?) {}

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  @JvmStatic
  fun recycleNodes(vararg nodes: AccessibilityNodeInfoCompat?) {}

  /**
   * Returns {@code true} if the node supports at least one of the specified actions. This method
   * supports actions introduced in API level 21 and later. However, it does not support bitmasks.
   *
   * @param node The node to check
   * @param actions The actions to check
   * @return {@code true} if at least one action is supported
   */
  // TODO: Use A11yActionCompat once AccessibilityActionCompat#equals is overridden
  @JvmStatic
  fun supportsAnyAction(
    node: AccessibilityNodeInfoCompat?, vararg actions: AccessibilityActionCompat
  ): Boolean {
    if (node == null) {
      return false
    }
    // Unwrap the node and compare AccessibilityActions because AccessibilityActions, unlike
    // AccessibilityActionCompats, are static (so checks for equality work correctly).
    val supportedActions = node.actionList

    for (action in actions) {
      if (supportedActions.contains(action)) {
        return true
      }
    }

    return false
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
  // TODO: Remove this method once AccessibilityActionCompat#equals is overridden
  @JvmStatic
  fun supportsAnyAction(node: AccessibilityNodeInfoCompat?, vararg actions: Int): Boolean {
    if (node != null) {
      val supportedActions = node.actions

      for (action in actions) {
        if ((supportedActions and action) == action) {
          return true
        }
      }
    }

    return false
  }

  /**
   * Returns {@code true} if the node supports the specified action. This method supports actions
   * introduced in API level 21 and later. However, it does not support bitmasks.
   */
  @JvmStatic
  fun supportsAction(node: AccessibilityNodeInfoCompat, action: Int): Boolean {
    // New actions in >= API 21 won't appear in getActions() but in getActionList().
    // On Lollipop+ devices, pre-API 21 actions will also appear in getActionList().
    val actions = node.actionList
    val size = actions.size
    for (i in 0 until size) {
      val actionCompat = actions[i]
      if (actionCompat.id == action) {
        return true
      }
    }
    return false
  }

  /**
   * Returns the action label on the node by given action ID, or an empty text if the node doesn't
   * support the action.
   */
  @JvmStatic
  fun getActionLabelById(node: AccessibilityNodeInfoCompat, action: Int): CharSequence? {
    val actions = node.actionList
    val size = actions.size
    for (i in 0 until size) {
      val actionCompat = actions[i]
      if (actionCompat.id == action) {
        return actionCompat.label
      }
    }
    return ""
  }

  /**
   * Returns the result of applying a filter using breadth-first traversal.
   *
   * @param node The root node to traverse from.
   * @param filter The filter to satisfy.
   * @return The first node reached via BFS traversal that satisfies the filter.
   */
  @JvmStatic
  fun searchFromBfs(
    node: AccessibilityNodeInfoCompat?, filter: Filter<AccessibilityNodeInfoCompat>
  ): AccessibilityNodeInfoCompat? {
    return searchFromBfs(node, filter, /* filterToSkip= */ null)
  }

  /**
   * Returns the result of applying a filter using breadth-first traversal. It allows skip nodes to
   * speed up the BFS traversal.
   *
   * @param node The root node to traverse from.
   * @param filter The filter to satisfy.
   * @param filterToSkip The filter for skipping nodes, all childs under the node will be skipped.
   * @return The first node reached via BFS traversal that satisfies the filter.
   */
  @JvmStatic
  fun searchFromBfs(
    node: AccessibilityNodeInfoCompat?,
    filter: Filter<AccessibilityNodeInfoCompat>,
    filterToSkip: Filter<AccessibilityNodeInfoCompat>?
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }

    val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
    val visitedNodes = HashSet<AccessibilityNodeInfoCompat>()

    queue.add(node)

    while (!queue.isEmpty()) {
      val item = queue.removeFirst()
      visitedNodes.add(item)

      if (filterToSkip != null && filterToSkip.accept(item)) {
        continue
      }

      if (filter.accept(item)) {
        return item
      }

      val childCount = item.childCount

      for (i in 0 until childCount) {
        val child = item.getChild(i)

        if (child != null && !visitedNodes.contains(child)) {
          queue.addLast(child)
        }
      }
    }
    return null
  }

  /** Safely obtains a copy of node. */
  @Deprecated("Deprecated in Java")
  @JvmStatic
  fun obtain(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    return if (node == null) null else AccessibilityNodeInfoCompat.obtain(node)
  }

  /**
   * Returns a fresh copy of {@code node} with properties that are less likely to be stale. Returns
   * {@code null} if the node can't be found anymore.
   */
  @JvmStatic
  fun refreshNode(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    return if ((node == null) || !node.refresh()) null else node
  }

  /**
   * Gets the location of specific range of node text. It returns null if the node doesn't support
   * text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   */
  @JvmStatic
  fun getTextLocations(
    node: AccessibilityNodeInfoCompat?, fromCharIndex: Int, toCharIndex: Int
  ): List<Rect>? {
    return getTextLocations(
      node, AccessibilityNodeInfoUtils.getText(node), fromCharIndex, toCharIndex)
  }

  /**
   * Gets the location of specific range of node {@code text}. It returns null if the node doesn't
   * support text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param text The node's text. This is typically the text, but can also be the content
   *     description if the node was not properly created. If the content description is used, its
   *     text location will only be returned if it's visible on the screen.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   */
  @JvmStatic
  fun getTextLocations(
    node: AccessibilityNodeInfoCompat?,
    text: CharSequence?,
    fromCharIndex: Int,
    toCharIndex: Int
  ): List<Rect>? {
    return getTextLocations(node, text, fromCharIndex, toCharIndex, true)
  }

  /**
   * Gets the location of specific range of node {@code text}. It returns null if the node doesn't
   * support text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param text The node's text. This is typically the text, but can also be the content
   *     description if the node was not properly created. If the content description is used, its
   *     text location will only be returned if it's visible on the screen.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   * @param useWindowBound experimental feature that should more accurately determine word position.
   */
  @JvmStatic
  fun getTextLocations(
    node: AccessibilityNodeInfoCompat?,
    text: CharSequence?,
    fromCharIndex: Int,
    toCharIndex: Int,
    useWindowBound: Boolean
  ): List<Rect>? {
    if (node == null) {
      return null
    }

    if (fromCharIndex < 0 ||
      TextUtils.isEmpty(text) ||
      !PrimitiveUtils.isInInterval(toCharIndex, fromCharIndex, text!!.length, true)) {
      return null
    }
    val info = node.unwrap() ?: return null
    // Prefer character bounds in window, but fall back to character bounds in screen if not
    // available.
    var key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
    var isBoundsInWindow = false
    if (useWindowBound &&
      BuildVersionUtils.isAtLeastBaklava() &&
      info.availableExtraData
        .contains(
          AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY)) {
      key = AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY
      isBoundsInWindow = true
    }
    val args = Bundle()
    args.putInt(
      AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, fromCharIndex)
    args.putInt(
      AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
      toCharIndex - fromCharIndex)
    if (!info.refreshWithExtraData(key, args)) {
      return null
    }

    val extras = info.extras
    val data: Array<Parcelable?>? = extras.getParcelableArray(key)
    if (data == null) {
      return null
    }

    val windowBounds = Rect()
    if (isBoundsInWindow) {
      val windowInfo = info.window
      windowInfo.getBoundsInScreen(windowBounds)
    }
    val result = ArrayList<Rect>(data.size)
    for (item in data) {
      if (item == null) {
        continue
      }
      val rectF = item as RectF
      if (isBoundsInWindow) {
        rectF.offset(windowBounds.left.toFloat(), windowBounds.top.toFloat())
      }
      result.add(
        Rect(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt()))
    }
    return result
  }

  /** Returns true if the node supports text location data. */
  @JvmStatic
  fun supportsTextLocation(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }
    val info = node.unwrap() ?: return false
    val extraData = info.availableExtraData
    return extraData != null &&
      (extraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY) ||
        (BuildVersionUtils.isAtLeastBaklava() &&
          extraData.contains(
            AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY)))
  }

  /** Helper method that returns {@code true} if the specified node is visible to the user */
  @JvmStatic
  fun isVisible(node: AccessibilityNodeInfoCompat?): Boolean {
    // We need to move focus to invisible node in WebView to scroll it but we don't want to
    // move focus if WebView itself is invisible.
    return node != null &&
      (node.isVisibleToUser ||
        (WebInterfaceUtils.isWebContainer(node) &&
          Role.getRole(node) != Role.ROLE_WEB_VIEW))
  }

  /**
   * Checks whether the node's height is smaller than the threshold
   *
   * @param context the context
   * @param node the node to check
   * @return {@code true} if the node's height is smaller than the dp threshold.
   */
  @JvmStatic
  fun isSmallNodeInHeight(context: Context, node: AccessibilityNodeInfoCompat): Boolean {
    val nodeRect = Rect()
    node.getBoundsInScreen(nodeRect)

    return nodeRect.height() < DisplayUtils.dpToPx(context, THRESHOLD_HEIGHT_DP_FOR_SMALL_NODE)
  }

  /**
   * Checks whether the node is a top or bottom border node or not. Horizontal scrolling with a
   * check of left or right border isn't yet supported in this method.
   *
   * @param screenPxSize the pixel size of a screen
   * @param node the node to check
   * @return {@code true} if the node is at top or bottom border.
   */
  @JvmStatic
  fun isTopOrBottomBorderNode(
    screenPxSize: Point, node: AccessibilityNodeInfoCompat
  ): Boolean {

    val nodeRect = Rect()
    node.getBoundsInScreen(nodeRect)

    // check the screen's border
    if (isTopOrBottomBorderNode(nodeRect, screenPxSize)) {
      return true
    }

    // check the scrollable container's border
    val parentRect = Rect()
    val filter =
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(parent: AccessibilityNodeInfoCompat?): Boolean {
          if (isScrollItem(parent)) {
            parent!!.getBoundsInScreen(parentRect)
            return parentRect.top == nodeRect.top || parentRect.bottom == nodeRect.bottom
          }
          return false
        }
      }

    return hasMatchingAncestor(node, filter)
  }

  private fun isTopOrBottomBorderNode(nodeRect: Rect, screenPxSize: Point): Boolean {
    return nodeRect.top <= 0 || nodeRect.bottom >= screenPxSize.y
  }

  /** Determines whether the specified node has bounds identical to the bounds of its window. */
  private fun areBoundsIdenticalToWindow(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    val window = getWindow(node) ?: return false

    val windowBounds = Rect()
    window.getBoundsInScreen(windowBounds)

    val nodeBounds = Rect()
    node.getBoundsInScreen(nodeBounds)

    return windowBounds == nodeBounds
  }

  /**
   * Analyses if the edit text has no text.
   *
   * <p>If there is a text field with hint text and no text, {@link
   * AccessibilityNodeInfoUtils#getText()} returns hint text. Hence this method checks for {@link
   * AccessibilityNodeInfo#ACTION_SET_SELECTION} to disregard the hint text.
   */
  @JvmStatic
  fun isEmptyEditTextRegardlessOfHint(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null || !node.isEditable) {
      return false
    }

    if (TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
      return true
    }
    return !supportsAction(node, AccessibilityNodeInfo.ACTION_SET_SELECTION)
  }

  /** * Checks if node represents non-editable selectable text. */
  @JvmStatic
  fun isNonEditableSelectableText(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node != null && FeatureSupport.supportsIsTextSelectable()) {
      return !node.isEditable && node.unwrap().isTextSelectable
    }
    return false
  }

  /** * Checks if node represents selectable text. Editable text is selectable. */
  @JvmStatic
  fun isTextSelectable(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }
    val isEditable = Role.getRole(node) == Role.ROLE_EDIT_TEXT || node.isEditable
    val isNonEditableSelectableText =
      AccessibilityNodeInfoUtils.isNonEditableSelectableText(node)
    return isEditable || isNonEditableSelectableText
  }

  /**
   * Gets a list of URLs contained within an {@link AccessibilityNodeInfoCompat}.
   *
   * @param node The node that will be searched for links
   * @return A list of {@link SpannableUrl}s from the URLs found within the Node
   */
  @JvmStatic
  fun getNodeUrls(node: AccessibilityNodeInfoCompat): List<SpannableUrl> {
    return getNodeClickableElements(
      node,
      URLSpan::class.java,
      Function { input -> SpannableUrl.create(input!!.first, input.second as URLSpan) })
  }

  /**
   * Gets a list of ClickableSpans paired with the String they span within a node's text.
   *
   * @param node The node that will be searched for spans
   * @return A list of Clickable elements found within the Node.
   */
  @JvmStatic
  fun getNodeClickableStrings(node: AccessibilityNodeInfoCompat): List<ClickableString> {
    return getNodeClickableElements(
      node,
      ClickableSpan::class.java,
      Function { input -> ClickableString.create(input!!.first, input.second) })
  }

  /**
   * Gets a list of the clickable elements within a node.
   *
   * @param node the node to get the clickable elements from
   * @param clickableType the type of clickable span that we look for within the node
   * @param clickableElementFn a function taking the visual string representation and the clickable
   *     portion of the clickable element to produces the desired format that will be displayable to
   *     the user
   * @param <E> the displayable format representation of the clickable element
   * @return a list of clickable elements, empty if there is none
   */
  private fun <E> getNodeClickableElements(
    node: AccessibilityNodeInfoCompat,
    clickableType: Class<out ClickableSpan>,
    clickableElementFn: Function<Pair<String, ClickableSpan>, E>
  ): List<E> {
    val spannableStrings = ArrayList<SpannableWithOffset>()
    SpannableTraversalUtils.getSpannableStringsWithTargetClickableSpanInNodeTree(
      node, clickableType, spannableStrings)

    val clickables = ArrayList<E>(1)
    for (spannableOffset in spannableStrings) {
      if (spannableOffset == null || spannableOffset.spannableString == null) {
        continue
      }
      val spannable: SpannableString = spannableOffset.spannableString
      for (span in spannable.getSpans(0, spannable.length, clickableType)) {
        // Child classes may not use #getUrl, so just check that the class is a URLSpan, instead of
        // a child class with "instanceof".
        if ((span.javaClass == URLSpan::class.java) &&
          Strings.isNullOrEmpty((span as URLSpan).url)) {
          continue
        }
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        if (end > start) {
          val chars = CharArray(end - start)
          spannable.getChars(start, end, chars, 0)
          clickables.add(clickableElementFn.apply(Pair.create(String(chars), span)))
        }
      }
    }
    return clickables
  }

  @JvmStatic
  fun getMovementGranularity(node: AccessibilityNodeInfoCompat): Int {
    // Some nodes in Webview have movement granularities even its content description/text is
    // empty.
    if (WebInterfaceUtils.supportsWebActions(node) &&
      TextUtils.isEmpty(node.contentDescription) &&
      TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
      return 0
    }

    return node.movementGranularities
  }

  @JvmStatic
  fun getHintText(node: AccessibilityNodeInfoCompat): CharSequence? {
    var hintText = node.hintText
    if (TextUtils.isEmpty(hintText)) {
      val bundle = node.extras
      if (bundle != null) {
        // Hint text for WebView.
        hintText = bundle.getCharSequence(HINT_TEXT_KEY)
      }
    }

    return hintText
  }

  /**
   * To setup a hashmap for AccessibilityAction id and the display string. We only build into the
   * hash map with identifiers which are supported in the running platform.
   */
  private fun initActionIds(): HashMap<Int, String> {
    val actionIdHashMap = HashMap<Int, String>()

    actionIdHashMap.put(AccessibilityAction.ACTION_SHOW_ON_SCREEN.id, "ACTION_SHOW_ON_SCREEN")
    actionIdHashMap.put(
      AccessibilityAction.ACTION_SCROLL_TO_POSITION.id, "ACTION_SCROLL_TO_POSITION")
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_UP.id, "ACTION_SCROLL_UP")
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_LEFT.id, "ACTION_SCROLL_LEFT")
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_DOWN.id, "ACTION_SCROLL_DOWN")
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_RIGHT.id, "ACTION_SCROLL_RIGHT")
    actionIdHashMap.put(AccessibilityAction.ACTION_CONTEXT_CLICK.id, "ACTION_CONTEXT_CLICK")
    actionIdHashMap.put(AccessibilityAction.ACTION_SET_PROGRESS.id, "ACTION_SET_PROGRESS")
    actionIdHashMap.put(AccessibilityAction.ACTION_MOVE_WINDOW.id, "ACTION_MOVE_WINDOW")

    if (BuildVersionUtils.isAtLeastP()) {
      actionIdHashMap.put(AccessibilityAction.ACTION_SHOW_TOOLTIP.id, "ACTION_SHOW_TOOLTIP")
      actionIdHashMap.put(AccessibilityAction.ACTION_HIDE_TOOLTIP.id, "ACTION_HIDE_TOOLTIP")
    }
    if (BuildVersionUtils.isAtLeastQ()) {
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_RIGHT.id, "ACTION_PAGE_RIGHT")
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_LEFT.id, "ACTION_PAGE_LEFT")
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_DOWN.id, "ACTION_PAGE_DOWN")
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_UP.id, "ACTION_PAGE_UP")
    }
    if (BuildVersionUtils.isAtLeastR()) {
      actionIdHashMap.put(
        AccessibilityAction.ACTION_PRESS_AND_HOLD.id, "ACTION_PRESS_AND_HOLD")
      actionIdHashMap.put(AccessibilityAction.ACTION_IME_ENTER.id, "ACTION_IME_ENTER")
    }
    return actionIdHashMap
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for displaying node data

  @JvmStatic
  fun actionToString(action: Int): String {
    when (action) {
      AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> {
        return "ACTION_ACCESSIBILITY_FOCUS"
      }
      AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
        return "ACTION_CLEAR_ACCESSIBILITY_FOCUS"
      }
      AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS -> {
        return "ACTION_CLEAR_FOCUS"
      }
      AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION -> {
        return "ACTION_CLEAR_SELECTION"
      }
      AccessibilityNodeInfoCompat.ACTION_CLICK -> {
        return "ACTION_CLICK"
      }
      AccessibilityNodeInfoCompat.ACTION_COLLAPSE -> {
        return "ACTION_COLLAPSE"
      }
      AccessibilityNodeInfoCompat.ACTION_COPY -> {
        return "ACTION_COPY"
      }
      AccessibilityNodeInfoCompat.ACTION_CUT -> {
        return "ACTION_CUT"
      }
      AccessibilityNodeInfoCompat.ACTION_DISMISS -> {
        return "ACTION_DISMISS"
      }
      AccessibilityNodeInfoCompat.ACTION_EXPAND -> {
        return "ACTION_EXPAND"
      }
      AccessibilityNodeInfoCompat.ACTION_FOCUS -> {
        return "ACTION_FOCUS"
      }
      AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
        return "ACTION_LONG_CLICK"
      }
      AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> {
        return "ACTION_NEXT_AT_MOVEMENT_GRANULARITY"
      }
      AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT -> {
        return "ACTION_NEXT_HTML_ELEMENT"
      }
      AccessibilityNodeInfoCompat.ACTION_PASTE -> {
        return "ACTION_PASTE"
      }
      AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> {
        return "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY"
      }
      AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT -> {
        return "ACTION_PREVIOUS_HTML_ELEMENT"
      }
      AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
        return "ACTION_SCROLL_BACKWARD"
      }
      AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
        return "ACTION_SCROLL_FORWARD"
      }
      AccessibilityNodeInfoCompat.ACTION_SELECT -> {
        return "ACTION_SELECT"
      }
      AccessibilityNodeInfoCompat.ACTION_SET_SELECTION -> {
        return "ACTION_SET_SELECTION"
      }
      AccessibilityNodeInfoCompat.ACTION_SET_TEXT -> {
        return "ACTION_SET_TEXT"
      }
      else -> {}
    }
    val actionName = actionIdToName[action]
    return if (actionName == null) "(unhandled action:$action)" else actionName
  }

  @JvmStatic
  fun toStringShort(node: AccessibilityNodeInfo?): String {
    return toStringShort(toCompat(node))
  }

  @JvmStatic
  fun toStringShort(node: AccessibilityNodeInfoCompat?): String {
    if (node == null) {
      return "null"
    }
    return StringBuilderUtils.joinFields(
      "AccessibilityNodeInfoCompat",
      StringBuilderUtils.optionalInt("id", node.hashCode(), -1),
      StringBuilderUtils.optionalText("class", node.className),
      StringBuilderUtils.optionalText("package", node.packageName),
      // TODO: Uses hash value in production build
      StringBuilderUtils.optionalText(
        "text",
        if (AccessibilityNodeInfoUtils.getText(node) == null)
          null
        else if (FeatureSupport.logcatIncludePsi())
        // Logs for DEBUG build or user had opt-in
          AccessibilityNodeInfoUtils.getText(node)
        else "***"),
      StringBuilderUtils.optionalText("state", node.stateDescription),
      StringBuilderUtils.optionalText("content", node.contentDescription),
      StringBuilderUtils.optionalText("viewIdResName", node.viewIdResourceName),
      StringBuilderUtils.optionalText("hint", node.hintText),
      StringBuilderUtils.optionalTag("enabled", node.isEnabled),
      StringBuilderUtils.optionalTag("checkable", node.isCheckable),
      StringBuilderUtils.optionalTag("checked", node.isChecked),
      StringBuilderUtils.optionalTag("accessibilityFocused", node.isAccessibilityFocused),
      StringBuilderUtils.optionalTag("focusable", isFocusable(node)),
      StringBuilderUtils.optionalTag("screenReaderFocusable", node.isScreenReaderFocusable),
      StringBuilderUtils.optionalTag("focused", node.isFocused),
      StringBuilderUtils.optionalTag("selected", node.isSelected),
      StringBuilderUtils.optionalTag("clickable", isClickable(node)),
      StringBuilderUtils.optionalTag("longClickable", isLongClickable(node)),
      StringBuilderUtils.optionalTag("password", node.isPassword),
      StringBuilderUtils.optionalTag("textEntryKey", node.isTextEntryKey),
      StringBuilderUtils.optionalTag("scrollable", isScrollable(node)),
      StringBuilderUtils.optionalTag(
        "heading", FeatureSupport.isHeadingWorks() && node.isHeading),
      StringBuilderUtils.optionalTag("collapsible", isCollapsible(node)),
      StringBuilderUtils.optionalTag("expandable", isExpandable(node)),
      StringBuilderUtils.optionalTag("dismissable", isDismissible(node)),
      StringBuilderUtils.optionalTag("pinEntry", isPinEntry(node)),
      StringBuilderUtils.optionalTag("visible", node.isVisibleToUser))
  }

  /** Copied from AccessibilityNodeInfo.java */
  @JvmStatic
  fun getMovementGranularitySymbolicName(granularity: Int): String? {
    if (granularity == 0) {
      return null
    }
    return when (granularity) {
      AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> "MOVEMENT_GRANULARITY_CHARACTER"
      AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD -> "MOVEMENT_GRANULARITY_WORD"
      AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE -> "MOVEMENT_GRANULARITY_LINE"
      AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH -> "MOVEMENT_GRANULARITY_PARAGRAPH"
      AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE -> "MOVEMENT_GRANULARITY_PAGE"
      else -> Integer.toHexString(granularity)
    }
  }

  /**
   * Given a double value, get the int percentage (0 to 100, both inclusive). Only return 0 or 100
   * when percentage is exactly 0 or 100 percent.
   */
  @JvmStatic
  fun roundForProgressPercent(percent: Double): Int {
    if (percent < 0.0f) {
      return 0
    } else if (percent > 0.0f && percent < 1.0f) {
      return 1
    } else if (percent > 99.0f && percent < 100.0f) {
      return 99
    } else if (percent > 100.0f) {
      return 100
    }
    return Math.round(percent).toInt()
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for node properties

  /**
   * Returns {@code true} if the height and width of the {@link AccessibilityNodeInfoCompat}'s
   * visible bounds on the screen are greater than a specified number of minimum pixels. This can be
   * used to prune tiny elements or elements off the screen.
   *
   * <p>{@link AccessibilityNodeInfo#isVisibleToUser()} sometimes returns {@code true} for {@link
   * android.webkit.WebView} items off the screen, so this method allows us to better ignore WebView
   * content off the screen.
   *
   * @param node The node that will be checked for a minimum number of pixels on the screen
   * @return {@code true} if the node has at least the number of minimum visible pixels in both
   *     width and height on the screen
   */
  @JvmStatic
  fun hasMinimumPixelsVisibleOnScreen(node: AccessibilityNodeInfoCompat): Boolean {
    val visibleBounds = Rect()
    node.getBoundsInScreen(visibleBounds)
    return ((Math.abs(visibleBounds.height()) >= MIN_VISIBLE_PIXELS) &&
      (Math.abs(visibleBounds.width()) >= MIN_VISIBLE_PIXELS))
  }

  /**
   * Returns the progress percentage from the node. The value will be in the range [0, 100].
   *
   * @param node The node from which to obtain the progress percentage.
   * @return The progress percentage.
   */
  @JvmStatic
  fun getProgressPercent(node: AccessibilityNodeInfoCompat?): Float {
    if (node == null) {
      return 0.0f
    }

    val rangeInfo = node.rangeInfo ?: return 0.0f

    val maxProgress = rangeInfo.max
    val minProgress = rangeInfo.min
    val currentProgress = rangeInfo.current
    val diffProgress = maxProgress - minProgress
    if (diffProgress <= 0.0f) {
      logError("getProgressPercent", "Range is invalid. [%f, %f]", minProgress, maxProgress)
      return 0.0f
    }

    if (currentProgress < minProgress) {
      logError(
        "getProgressPercent",
        "Current percent is out of range. Current: %f Range: [%f, %f]",
        currentProgress,
        minProgress,
        maxProgress)
      return 0.0f
    }

    if (currentProgress > maxProgress) {
      logError(
        "getProgressPercent",
        "Current percent is out of range. Current: %f Range: [%f, %f]",
        currentProgress,
        minProgress,
        maxProgress)
      return 100.0f
    }

    val percent = (currentProgress - minProgress) / diffProgress
    return (100.0f * Math.max(0.0f, Math.min(1.0f, percent)))
  }

  /**
   * Returns whether the node has valid RangeInfo.
   *
   * @param node The node to check.
   * @return Whether the node has valid RangeInfo.
   */
  @JvmStatic
  fun hasValidRangeInfo(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    val rangeInfo = node.rangeInfo ?: return false

    val maxProgress = rangeInfo.max
    val minProgress = rangeInfo.min
    val currentProgress = rangeInfo.current
    val diffProgress = maxProgress - minProgress
    return (diffProgress > 0.0f) &&
      (currentProgress >= minProgress) &&
      (currentProgress <= maxProgress)
  }

  /** Checks whether the given node is still in the window. */
  @JvmStatic
  fun isInWindow(
    checkingNode: AccessibilityNodeInfoCompat,
    windowInfoCompat: AccessibilityWindowInfoCompat?
  ): Boolean {
    if (windowInfoCompat == null) {
      return false
    }
    val windowId = checkingNode.windowId
    if (windowId != WINDOW_ID_NONE && windowId != windowInfoCompat.id) {
      return false
    }
    return hasDescendant(windowInfoCompat.root, checkingNode)
  }

  /** Checks whether the given node is still in the window. */
  @JvmStatic
  fun isInWindow(
    checkingNode: AccessibilityNodeInfoCompat, windowInfo: AccessibilityWindowInfo?
  ): Boolean {
    if (windowInfo == null) {
      return false
    }
    val windowId = checkingNode.windowId
    if (windowId != WINDOW_ID_NONE && windowId != windowInfo.id) {
      return false
    }
    return hasDescendant(toCompat(windowInfo.root), checkingNode)
  }

  /**
   * Checks whether the given node is a header.
   *
   * <p>On M devices, the return value is always false if the node is an item in ListView or
   * GridView but not in WebView.
   */
  // TODO On pre-N devices, the framework ListView/GridView will mark non-headers
  // as headers. The workaround should be removed when TalkBack doesn't support android M.
  @JvmStatic
  fun isHeading(node: AccessibilityNodeInfoCompat): Boolean {
    if (!FeatureSupport.isHeadingWorks()) {
      val collectionRoot = getCollectionRoot(node)
      if (nodeIsListOrGrid(collectionRoot) && !WebInterfaceUtils.isWebContainer(collectionRoot)) {
        return false
      }
    }
    return node.isHeading
  }

  /**
   * Returns the collection root for the given node. As it searches for the collection root, if
   * there are more than one collection item along the way upwards, this function will return null
   * as the a11y tree is formatted incorrectly.
   *
   * <p>For nested collection items, a collection node must always exist between an ancestor and a
   * descendant collection item. If this function is called on a descendant item that is directly
   * nested under an ancestor item (without an intermediary collection node), it will return null.
   * See b/409569562#4.
   *
   * @param node The node to search for the collection root.
   * @return The collection root, or {@code null} if no collection root is found.
   */
  @JvmStatic
  fun getCollectionRoot(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }

    val filter = FILTER_COLLECTION.or(FILTER_COLLECTION_ITEM)

    var collectionRoot = getSelfOrMatchingAncestor(node, filter)
    if (collectionRoot == null || FILTER_COLLECTION.accept(collectionRoot)) {
      return collectionRoot
    }

    collectionRoot = getMatchingAncestor(collectionRoot, filter)
    if (collectionRoot == null || FILTER_COLLECTION.accept(collectionRoot)) {
      return collectionRoot
    }

    return null
  }

  /**
   * Returns the collection root for the given node, excluding the node itself from the search.
   *
   * @param node The node to search for the collection root.
   * @return The collection root, or {@code null} if no collection root is found.
   */
  @JvmStatic
  fun getCollectionRootExcludeSelf(
    node: AccessibilityNodeInfoCompat?
  ): AccessibilityNodeInfoCompat? {
    if (node == null) {
      return null
    }

    if (FILTER_COLLECTION.accept(node)) {
      return getCollectionRoot(node.parent)
    }

    return getCollectionRoot(node)
  }

  /** Returns a table root containing the given node. */
  @JvmStatic
  fun getTableRoot(descendant: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(descendant, FILTER_TABLE)
  }

  /** Returns whether the given node is a table root. */
  private fun isTableRoot(node: AccessibilityNodeInfoCompat): Boolean {
    val collectionInfo: CollectionInfoCompat? = node.collectionInfo
    return collectionInfo != null &&
      collectionInfo.rowCount > 1 &&
      collectionInfo.columnCount > 1
  }

  /** Returns a table cell under table containing the given node. */
  @JvmStatic
  fun getTableCellUnderTable(
    descendant: AccessibilityNodeInfoCompat?
  ): AccessibilityNodeInfoCompat? {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
      descendant, FILTER_TABLE_CELL_UNDER_TABLE)
  }

  /** Returns a node that mapped to the voice dictation clickable view. */
  @JvmStatic
  fun getVoiceDictationNode(
    descendant: AccessibilityNodeInfoCompat?
  ): AccessibilityNodeInfoCompat? {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingDescendant(
      descendant, FILTER_VOICE_DICTATION)
  }

  private fun isVoiceDictationNode(node: AccessibilityNodeInfoCompat?): Boolean {
    return Role.getRole(node) == Role.ROLE_VOICE_DICTATION_BUTTON
  }

  /** Returns whether the given node is a table cell. */
  private fun isTableCell(node: AccessibilityNodeInfoCompat): Boolean {
    val collectionItemInfo: CollectionItemInfoCompat? = node.collectionItemInfo
    return collectionItemInfo != null &&
      collectionItemInfo.rowIndex >= 0 &&
      collectionItemInfo.columnIndex >= 0
  }

  /** Returns whether the given node is a table cell in a table. */
  private fun isTableCellUnderTable(node: AccessibilityNodeInfoCompat): Boolean {
    val collectionItemInfo: CollectionItemInfoCompat? = node.collectionItemInfo
    return collectionItemInfo != null &&
      collectionItemInfo.rowIndex >= 0 &&
      collectionItemInfo.columnIndex >= 0 &&
      getTableRoot(node) != null
  }

  /** Checks if given node is ListView or GirdView. */
  @JvmStatic
  fun nodeIsListOrGrid(node: AccessibilityNodeInfoCompat?): Boolean {
    return nodeMatchesAnyClassName(node, CLASS_LISTVIEW, CLASS_GRIDVIEW)
  }

  /** Returns {@code true} if the parent of the {@code node} is a collection. */
  @JvmStatic
  fun nodeIsListOrGridItem(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    val parent = node.parent ?: return false

    @RoleName val role = Role.getRole(parent)
    return role == Role.ROLE_LIST || role == Role.ROLE_GRID
  }

  /** Returns true if the {@code node} is in a collection. */
  @JvmStatic
  fun isInCollection(node: AccessibilityNodeInfoCompat?): Boolean {
    return AccessibilityNodeInfoUtils.hasMatchingAncestor(
      node,
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(ancestor: AccessibilityNodeInfoCompat?): Boolean {
          @RoleName val role = Role.getRole(ancestor)
          return role == Role.ROLE_LIST ||
            role == Role.ROLE_GRID ||
            (ancestor != null && ancestor.collectionInfo != null)
        }
      })
  }

  @JvmStatic
  fun getGridRowTitle(node: AccessibilityNodeInfoCompat): String? {
    if (FeatureSupport.supportGridTitle() && node.unwrap() != null) {
      val itemInfo: CollectionItemInfo? = node.unwrap().collectionItemInfo
      if (itemInfo != null) {
        return itemInfo.rowTitle
      }
    }
    return null
  }

  @JvmStatic
  fun getGridColumnTitle(node: AccessibilityNodeInfoCompat): String? {
    if (FeatureSupport.supportGridTitle() && node.unwrap() != null) {
      val itemInfo: CollectionItemInfo? = node.unwrap().collectionItemInfo
      if (itemInfo != null) {
        return itemInfo.columnTitle
      }
    }
    return null
  }

  /**
   * Returns true if the {@link
   * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat} associated
   * with {@code node} is not null and reflects the presence of at least 1 row and 1 column.
   */
  @JvmStatic
  fun hasUsableCollectionInfo(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      node.collectionInfo != null &&
      node.collectionInfo.rowCount >= 1 &&
      node.collectionInfo.columnCount >= 1
  }

  /**
   * Returns true if the {@link
   * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat}
   * associated with {@code node} is not null and contains legal collection row and column indices.
   */
  @JvmStatic
  fun hasUsableCollectionItemInfo(node: AccessibilityNodeInfoCompat?): Boolean {
    return node != null &&
      node.collectionItemInfo != null &&
      node.collectionItemInfo.rowIndex >= 0 &&
      node.collectionItemInfo.columnIndex >= 0
  }

  /**
   * Returns true if the {@link
   * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat}
   * associated with {@code node} is not null, and it contains legal collection row and column
   * indices, which fall within the row and column bounds of {@code parent}.
   */
  @JvmStatic
  fun hasUsableCollectionItemInfo(
    item: AccessibilityNodeInfoCompat?, collection: AccessibilityNodeInfoCompat?
  ): Boolean {
    return hasUsableCollectionItemInfo(item) &&
      hasUsableCollectionInfo(collection) &&
      item!!.collectionItemInfo.rowIndex < collection!!.collectionInfo.rowCount &&
      item.collectionItemInfo.columnIndex < collection.collectionInfo.columnCount
  }

  /**
   * Returns the {@link Rect} of the node bounds in screen coordinates, and returns an empty Rect if
   * the given node is null.
   */
  @JvmStatic
  fun getNodeBoundsInScreen(node: AccessibilityNodeInfoCompat?): Rect {
    val nodeBounds = Rect()
    if (node != null) {
      node.getBoundsInScreen(nodeBounds)
    }
    return nodeBounds
  }

  /**
   * Returns a list of {@link SpellingSuggestion} for all {@link SuggestionSpan}s at the cursor
   * position in the given {@link AccessibilityNodeInfoCompat} if the input method for the node is
   * able to display spelling suggestions.
   *
   * @param node The node to check
   */
  @JvmStatic
  fun getSpellingSuggestions(
    context: Context, node: AccessibilityNodeInfoCompat?
  ): ImmutableList<SpellingSuggestion> {
    return getSpellingSuggestions(context, node, /* activeSpellCheck= */ true)
  }

  /**
   * Returns a list of {@link SpellingSuggestion} for all {@link SuggestionSpan}s at the cursor
   * position in the given {@link AccessibilityNodeInfoCompat}.
   *
   * @param node the node to check
   * @param cursorPosition index of the cursor position
   */
  @JvmStatic
  fun getSpellingSuggestions(
    context: Context, node: AccessibilityNodeInfoCompat, cursorPosition: Int
  ): ImmutableList<SpellingSuggestion> {
    return getSpellingSuggestions(context, node, cursorPosition, /* activeSpellCheck= */ true)
  }

  /**
   * Returns a list of {@link SpellingSuggestion} for all {@link SuggestionSpan}s at the cursor
   * position in the given {@link AccessibilityNodeInfoCompat} if the input method for the node is
   * able to display spelling suggestions.
   *
   * @param node The node to check
   * @param activeSpellCheck Perform in service spell check or not
   */
  @JvmStatic
  fun getSpellingSuggestions(
    context: Context, node: AccessibilityNodeInfoCompat?, activeSpellCheck: Boolean
  ): ImmutableList<SpellingSuggestion> {
    if (node == null || hasNoSuggestionsNeed(node.inputType)) {
      return ImmutableList.of()
    }

    val start = node.textSelectionStart
    val end = node.textSelectionEnd

    if (start != end) {
      LogUtils.v(TAG, "Spelling suggestion does not work when text is selected.")
      return ImmutableList.of()
    }

    return getSpellingSuggestions(context, node, end, activeSpellCheck)
  }

  /**
   * Returns a list of {@link SpellingSuggestion} for all {@link SuggestionSpan}s at the cursor
   * position in the given {@link AccessibilityNodeInfoCompat}.
   *
   * @param node the node to check
   * @param cursorPosition index of the cursor position
   */
  // common_typos_disable
  @JvmStatic
  fun getSpellingSuggestions(
    context: Context,
    node: AccessibilityNodeInfoCompat,
    cursorPosition: Int,
    activeSpellCheck: Boolean
  ): ImmutableList<SpellingSuggestion> {
    var cursorPositionVar = cursorPosition
    val text: CharSequence? =
      if (activeSpellCheck) SpellChecker.getTextWithSuggestionSpans(context, node) else node.text
    val spellingSuggestions = ArrayList<SpellingSuggestion>()
    if (TextUtils.isEmpty(text) || text !is Spannable) {
      LogUtils.v(TAG, "getSpellingSuggestions() text is null or not a Spannable")
      return ImmutableList.of()
    }
    val spannedText: Spannable = text

    // Returns the suggestion if just a space or punctuation is between the typo and the cursor.
    // For example: helllo,|
    if (cursorPositionVar > 0) {
      if (cursorPositionVar < text.length) {
        // Do not return the suggestion if a word is after the cursor. For example: helllo |world
        if (!Character.isLetterOrDigit(text[cursorPositionVar - 1]) &&
          !Character.isLetterOrDigit(text[cursorPositionVar])) {
          cursorPositionVar--
        }
      } else if (cursorPositionVar == text.length) {
        // It is unnecessary to check the character after the cursor because the cursor is at the
        // end of the line. For example: helllo |
        if (!Character.isLetterOrDigit(text[cursorPositionVar - 1])) {
          cursorPositionVar--
        }
      }
    }

    val spans = spannedText.getSpans(0, text.length, SuggestionSpan::class.java)
    val logMessage =
      StringBuilder(
        String.format(
          Locale.ENGLISH,
          "cursor=[%d] suggestion_spans text=[%s] spans=[%d]",
          cursorPositionVar,
          text,
          spans.size))
    // TODO: Uses stream to simplify it.
    for (span in spans) {
      val start = spannedText.getSpanStart(span)
      val end = spannedText.getSpanEnd(span)
      if (start <= cursorPositionVar && end >= cursorPositionVar) {
        val spellingSuggestion =
          SpellingSuggestion.create(start, end, text.subSequence(start, end), span)
        // Ignore the span which has no suggestion to avoid announcing suggestions available but
        // there is no suggestion that can be chosen.
        if (span.suggestions.size > 0) {
          spellingSuggestions.add(spellingSuggestion)
        } else {
          LogUtils.v(TAG, "%s no suggestion", text.subSequence(start, end))
        }

        logMessage.append("\n")
        logMessage.append(spellingSuggestion)
      }
    }

    LogUtils.v(TAG, logMessage.toString())
    return ImmutableList.copyOf(spellingSuggestions)
  }

  /**
   * Returns the total number of typos which are in the edit field.
   *
   * @return 0, there is no typo or the input method for the node won't display spelling
   *     suggestions.
   */
  @JvmStatic
  fun getTypoCount(context: Context, node: AccessibilityNodeInfoCompat?): Int {
    return getSuggestionSpans(context, node).size
  }

  /**
   * Returns {@code true} if the given {@link AccessibilityNodeInfoCompat} text includes misspelled
   * words which have spelling suggestions and the input method for the node is able to display
   * spelling suggestions.
   */
  @JvmStatic
  fun hasSpellingSuggestionsForTypos(
    context: Context, node: AccessibilityNodeInfoCompat?
  ): Boolean {
    val spans = getSuggestionSpans(context, node)
    for (span in spans) {
      if (span.suggestions.size > 0) {
        return true
      }
    }
    return false
  }

  /**
   * Returns {@link Locale} if the given {@link AccessibilityNodeInfoCompat} supports App Locale.
   */
  @JvmStatic
  fun getLocalesByNode(node: AccessibilityNodeInfoCompat?): Locale? {
    if (node == null || !FeatureSupport.supportAccessibilityAppLocale()) {
      return null
    }
    val windowInfoCompat = node.window ?: return null
    val windowInfo = windowInfoCompat.unwrap() ?: return null
    val localeList: LocaleList? = windowInfo.locales
    val defaultLocal = Locale.getDefault()

    val count = if (localeList == null) 0 else localeList.size()
    if (count == 0 || defaultLocal == localeList!![0]) {
      // AccessibilityWindowInfo#getLocales may return the system default locale. When the 1st entry
      // matches the default locale, we don't insert the locale which will invalidate the locale
      // embedded within the content.
      return null
    }
    return localeList[0]
  }

  /** Returns whether the node has requested initial accessibility focus. */
  @JvmStatic
  fun hasRequestInitialAccessibilityFocus(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    var hasRequestInitialAccessibilityFocus = node.hasRequestInitialAccessibilityFocus()

    // In the early version of AndroidX, the property was retrieved from the AccessibilityNodeInfo.
    // See b/279108748 for details.
    if (!hasRequestInitialAccessibilityFocus &&
      FeatureSupport.supportRequestInitialAccessibilityFocusNative()) {
      val unwrap = node.unwrap()
      if (unwrap != null) {
        hasRequestInitialAccessibilityFocus = unwrap.hasRequestInitialAccessibilityFocus()
      }
    }

    return hasRequestInitialAccessibilityFocus
  }

  /**
   * Returns the rate update limitation (in milli-second) if the given {@link
   * AccessibilityNodeInfoCompat} supports it.
   */
  @JvmStatic
  fun getMinDurationBetweenContentChangesMillis(node: AccessibilityNodeInfoCompat?): Long {
    if (node == null) {
      LogUtils.w(TAG, "Failed to getMinDurationBetweenContentChangesMillis/node is null")
      return 0L
    }
    return node.minDurationBetweenContentChangesMillis
  }

  /**
   * Returns a list of {@link SuggestionSpan} in the given {@link AccessibilityNodeInfoCompat} text
   * or an empty list if the input method for the node won't display spelling suggestions.
   */
  private fun getSuggestionSpans(
    context: Context, node: AccessibilityNodeInfoCompat?
  ): ImmutableList<SuggestionSpan> {
    if (node == null) {
      return ImmutableList.of()
    }
    return getSuggestionSpans(context, node.text, node.inputType)
  }

  /**
   * Returns a list of {@link SuggestionSpan} in the given text or an empty list if the input type
   * is no suggestion.
   */
  @JvmStatic
  fun getSuggestionSpans(
    context: Context, text: CharSequence?, inputType: Int
  ): ImmutableList<SuggestionSpan> {
    if (TextUtils.isEmpty(text) || hasNoSuggestionsNeed(inputType)) {
      return ImmutableList.of()
    }

    val textWithSuggestionSpans: CharSequence? =
      SpellChecker.getTextWithSuggestionSpans(context, text)
    if (TextUtils.isEmpty(textWithSuggestionSpans) ||
      textWithSuggestionSpans !is Spannable) {
      return ImmutableList.of()
    }
    val spannedText: Spannable = textWithSuggestionSpans

    val spans =
      spannedText.getSpans(0, textWithSuggestionSpans.length, SuggestionSpan::class.java)
    if (spans.size == 0) {
      return ImmutableList.of()
    }

    return ImmutableList.copyOf(spans)
  }

  /**
   * Returns {@code true}, if the input method for the {@code node} won't display spelling
   * suggestions.
   */
  private fun hasNoSuggestionsNeed(input: Int): Boolean {
    return input == TYPE_TEXT_FLAG_NO_SUGGESTIONS
  }

  private fun nodeMatchesAnyClassName(
    node: AccessibilityNodeInfoCompat?, vararg classNames: CharSequence?
  ): Boolean {
    if (node == null || node.className == null) {
      return false
    }

    for (name in classNames) {
      if (TextUtils.equals(node.className, name)) {
        return true
      }
    }

    return false
  }

  /**
   * Splits a fully-qualified resource identifier name into its package and ID name. For example,
   * "com.android.deskclock:id/analog_appwidget" which provides by {@link
   * AccessibilityNodeInfoCompat#getViewIdResourceName()}
   */
  class ViewResourceName private constructor(
    private val packageName: String,
    private val viewIdName: String,
  ) {
    fun packageName(): String = packageName

    fun viewIdName(): String = viewIdName

    override fun equals(other: Any?): Boolean {
      if (other === this) return true
      if (other !is ViewResourceName) return false
      return packageName == other.packageName && viewIdName == other.viewIdName
    }

    override fun hashCode(): Int {
      var h = 1
      h *= 1000003
      h = h xor packageName.hashCode()
      h *= 1000003
      h = h xor viewIdName.hashCode()
      return h
    }

    override fun toString(): String {
      return "ViewResourceName= " +
        StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalText("packageName", packageName),
          StringBuilderUtils.optionalText("viewIdName", viewIdName))
    }

    companion object {
      /** Creates a ViewResourceName instance by {@link AccessibilityNodeInfoCompat}. */
      @JvmStatic
      fun create(node: AccessibilityNodeInfoCompat): ViewResourceName? {
        val resourceName = node.viewIdResourceName
        if (TextUtils.isEmpty(resourceName)) {
          return null
        }

        val splitId = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2)
        if (splitId.size != 2 || TextUtils.isEmpty(splitId[0]) || TextUtils.isEmpty(splitId[1])) {
          // Invalid view resource name.
          LogUtils.w(TAG, "Failed to parse resource: %s", resourceName)
          return null
        }

        return ViewResourceName(splitId[0], splitId[1])
      }
    }
  }

  /**
   * Represents a {@link ClickableSpan} and the string it spans to reduce the effort of downstream
   * consumers; getting the spanned string is non-trivial.
   */
  class ClickableString private constructor(
    private val string: String,
    private val clickableSpan: ClickableSpan,
  ) {
    fun string(): String = string

    fun clickableSpan(): ClickableSpan = clickableSpan

    // ClickableSpan.onClick is actually fine with a null param.
    fun onClick() {
      // The platform annotates ClickableSpan.onClick(View) as @NonNull, but the original Java
      // code intentionally passes null; erase the nullability with an unchecked generic cast.
      clickableSpan().onClick(uncheckedNullView())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uncheckedNullView(): T = null as T

    override fun equals(other: Any?): Boolean {
      if (other === this) return true
      if (other !is ClickableString) return false
      return string == other.string && clickableSpan == other.clickableSpan
    }

    override fun hashCode(): Int {
      var h = 1
      h *= 1000003
      h = h xor string.hashCode()
      h *= 1000003
      h = h xor clickableSpan.hashCode()
      return h
    }

    override fun toString(): String =
      "ClickableString{" +
        "string=$string, " +
        "clickableSpan=$clickableSpan}"

    companion object {
      @JvmStatic
      fun create(string: String, clickableSpan: ClickableSpan): ClickableString =
        ClickableString(string, clickableSpan)
    }
  }

  /** A wrapper of {@link SuggestionSpan}. */
  class SpellingSuggestion private constructor(
    private val start: Int,
    private val end: Int,
    private val misspelledWord: CharSequence,
    private val suggestionSpan: SuggestionSpan,
  ) {
    fun start(): Int = start

    fun end(): Int = end

    fun misspelledWord(): CharSequence = misspelledWord

    fun suggestionSpan(): SuggestionSpan = suggestionSpan

    override fun equals(other: Any?): Boolean {
      if (other === this) return true
      if (other !is SpellingSuggestion) return false
      return start == other.start &&
        end == other.end &&
        misspelledWord == other.misspelledWord &&
        suggestionSpan == other.suggestionSpan
    }

    override fun hashCode(): Int {
      var h = 1
      h *= 1000003
      h = h xor start
      h *= 1000003
      h = h xor end
      h *= 1000003
      h = h xor misspelledWord.hashCode()
      h *= 1000003
      h = h xor suggestionSpan.hashCode()
      return h
    }

    override fun toString(): String {
      val suggestionsString =
        StringBuilder()
          .append(
            String.format(Locale.ENGLISH, "[%d-%d][%s]", start(), end(), misspelledWord()))
      for (suggestion in suggestionSpan().suggestions) {
        suggestionsString.append(String.format(Locale.ENGLISH, "[suggestion=%s]", suggestion))
      }

      return suggestionsString.toString()
    }

    companion object {
      @JvmStatic
      fun create(
        start: Int, end: Int, misspelledWord: CharSequence, suggestionSpan: SuggestionSpan
      ): SpellingSuggestion =
        SpellingSuggestion(start, end, misspelledWord, suggestionSpan)
    }
  }

  private fun printId(node: AccessibilityNodeInfoCompat): String {
    return String.format("Node(id=%s class=%s)", node.hashCode(), node.className)
  }
}
