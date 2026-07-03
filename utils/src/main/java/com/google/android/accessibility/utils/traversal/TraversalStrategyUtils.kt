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

package com.google.android.accessibility.utils.traversal

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.DiagnosticOverlayUtils
import com.google.android.accessibility.utils.Filter
import com.google.android.accessibility.utils.FocusFinder
import com.google.android.accessibility.utils.Role
import com.google.android.accessibility.utils.ScrollableNodeInfo
import com.google.android.accessibility.utils.WebInterfaceUtils
import com.google.android.accessibility.utils.traversal.TraversalStrategy.Companion.SEARCH_FOCUS_BACKWARD
import com.google.android.accessibility.utils.traversal.TraversalStrategy.Companion.SEARCH_FOCUS_DOWN
import com.google.android.accessibility.utils.traversal.TraversalStrategy.Companion.SEARCH_FOCUS_FORWARD
import com.google.android.accessibility.utils.traversal.TraversalStrategy.Companion.SEARCH_FOCUS_LEFT
import com.google.android.accessibility.utils.traversal.TraversalStrategy.Companion.SEARCH_FOCUS_RIGHT
import com.google.android.accessibility.utils.traversal.TraversalStrategy.Companion.SEARCH_FOCUS_UP
import com.google.android.libraries.accessibility.utils.log.LogUtils

object TraversalStrategyUtils {

  private const val TAG = "TraversalStrategyUtils"

  /**
   * Recycles the given traversal strategy.
   *
   * @deprecated Accessibility is discontinuing recycling.
   */
  @Deprecated("Accessibility is discontinuing recycling.")
  @JvmStatic
  fun recycle(traversalStrategy: TraversalStrategy?) {}

  /**
   * Depending on whether the direction is spatial or logical, returns the appropriate traversal
   * strategy to handle the case.
   */
  @JvmStatic
  fun getTraversalStrategy(
    root: AccessibilityNodeInfoCompat?,
    focusFinder: FocusFinder?,
    @TraversalStrategy.SearchDirection direction: Int,
  ): TraversalStrategy = getTraversalStrategy(root, focusFinder, direction, false, false)

  /**
   * Depending on whether the direction is spatial or logical, returns the appropriate traversal
   * strategy to handle the case.
   */
  @JvmStatic
  fun getTraversalStrategy(
    root: AccessibilityNodeInfoCompat?,
    focusFinder: FocusFinder?,
    config: OrderedTraversalStrategyConfig,
  ): TraversalStrategy =
    getTraversalStrategy(
      root,
      focusFinder,
      config.searchDirection(),
      config.includeChildrenOfNodesWithWebActions(),
      config.makeFabFirst(),
    )

  /** TODO: Experimental code. Remove {@code makeFabFirst} when experiment is done. */
  @JvmStatic
  fun getTraversalStrategy(
    root: AccessibilityNodeInfoCompat?,
    focusFinder: FocusFinder?,
    @TraversalStrategy.SearchDirection direction: Int,
    includeChildrenOfNodesWithWebActions: Boolean,
    makeFabFirst: Boolean,
  ): TraversalStrategy {
    when (direction) {
      SEARCH_FOCUS_BACKWARD,
      SEARCH_FOCUS_FORWARD -> {
        return OrderedTraversalStrategy(root, includeChildrenOfNodesWithWebActions, makeFabFirst)
      }
      SEARCH_FOCUS_LEFT,
      SEARCH_FOCUS_RIGHT,
      SEARCH_FOCUS_UP,
      SEARCH_FOCUS_DOWN -> {
        return DirectionalTraversalStrategy(root, focusFinder)
      }
      else -> {}
    }

    throw IllegalArgumentException("direction must be a SearchDirection")
  }

  /** Converts {@link TraversalStrategy.SearchDirection} to view focus direction. */
  @JvmStatic
  fun nodeSearchDirectionToViewSearchDirection(
    @TraversalStrategy.SearchDirection direction: Int,
  ): Int =
    when (direction) {
      SEARCH_FOCUS_FORWARD -> View.FOCUS_FORWARD
      SEARCH_FOCUS_BACKWARD -> View.FOCUS_BACKWARD
      SEARCH_FOCUS_LEFT -> View.FOCUS_LEFT
      SEARCH_FOCUS_RIGHT -> View.FOCUS_RIGHT
      SEARCH_FOCUS_UP -> View.FOCUS_UP
      SEARCH_FOCUS_DOWN -> View.FOCUS_DOWN
      else -> throw IllegalArgumentException("Direction must be a SearchDirection")
    }

  /**
   * Determines whether the given search direction corresponds to an actual spatial direction as
   * opposed to a logical direction.
   */
  @JvmStatic
  fun isSpatialDirection(@TraversalStrategy.SearchDirection direction: Int): Boolean {
    when (direction) {
      SEARCH_FOCUS_FORWARD, SEARCH_FOCUS_BACKWARD -> return false
      SEARCH_FOCUS_UP, SEARCH_FOCUS_DOWN, SEARCH_FOCUS_LEFT, SEARCH_FOCUS_RIGHT -> return true
      else -> {}
    }

    throw IllegalArgumentException("direction must be a SearchDirection")
  }

  /** Returns {@code true} if {@code searchDirection} is logical (forward or backward). */
  @JvmStatic
  fun isLogicalDirection(@TraversalStrategy.SearchDirection direction: Int): Boolean =
    direction == SEARCH_FOCUS_FORWARD || direction == SEARCH_FOCUS_BACKWARD

  /**
   * Converts a spatial direction to a logical direction based on whether the user is LTR or RTL. If
   * the direction is already a logical direction, it is returned.
   */
  @JvmStatic
  @TraversalStrategy.SearchDirection
  fun getLogicalDirection(
    @TraversalStrategy.SearchDirection direction: Int,
    isRtl: Boolean,
  ): Int {
    @TraversalStrategy.SearchDirection val left: Int
    @TraversalStrategy.SearchDirection val right: Int
    if (isRtl) {
      left = SEARCH_FOCUS_FORWARD
      right = SEARCH_FOCUS_BACKWARD
    } else {
      left = SEARCH_FOCUS_BACKWARD
      right = SEARCH_FOCUS_FORWARD
    }

    when (direction) {
      SEARCH_FOCUS_LEFT -> return left
      SEARCH_FOCUS_RIGHT -> return right
      SEARCH_FOCUS_UP, SEARCH_FOCUS_BACKWARD -> return SEARCH_FOCUS_BACKWARD
      SEARCH_FOCUS_DOWN, SEARCH_FOCUS_FORWARD -> return SEARCH_FOCUS_FORWARD
      else -> {}
    }

    throw IllegalArgumentException("direction must be a SearchDirection")
  }

  /**
   * Returns the scroll action for the given {@link TraversalStrategy.SearchDirection} if the scroll
   * action is available on the current SDK version. Otherwise, returns 0.
   */
  @JvmStatic
  fun convertSearchDirectionToScrollAction(
    @TraversalStrategy.SearchDirection direction: Int,
  ): Int {
    if (direction == SEARCH_FOCUS_FORWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
    } else if (direction == SEARCH_FOCUS_BACKWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
    } else {
      if (direction == SEARCH_FOCUS_LEFT) {
        return AccessibilityAction.ACTION_SCROLL_LEFT.id
      } else if (direction == SEARCH_FOCUS_RIGHT) {
        return AccessibilityAction.ACTION_SCROLL_RIGHT.id
      } else if (direction == SEARCH_FOCUS_UP) {
        return AccessibilityAction.ACTION_SCROLL_UP.id
      } else if (direction == SEARCH_FOCUS_DOWN) {
        return AccessibilityAction.ACTION_SCROLL_DOWN.id
      }
    }

    return 0
  }

  /**
   * Returns the {@link TraversalStrategy.SearchDirectionOrUnknown} for the given scroll action;
   * {@link TraversalStrategy#SEARCH_FOCUS_UNKNOWN} is returned for a scroll action that can't be
   * handled (e.g. because the current API level doesn't support it).
   */
  @JvmStatic
  @TraversalStrategy.SearchDirectionOrUnknown
  fun convertScrollActionToSearchDirection(scrollAction: Int): Int {
    if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
      return SEARCH_FOCUS_FORWARD
    } else if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      return SEARCH_FOCUS_BACKWARD
    } else {
      if (scrollAction == AccessibilityAction.ACTION_SCROLL_LEFT.id) {
        return SEARCH_FOCUS_LEFT
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_RIGHT.id) {
        return SEARCH_FOCUS_RIGHT
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_UP.id) {
        return SEARCH_FOCUS_UP
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_DOWN.id) {
        return SEARCH_FOCUS_DOWN
      }
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN
  }

  /**
   * Convenience method determining if the current item is at the edge of a scrollable view and
   * suitable autoscroll. Calls {@code isEdgeListItem} with {@code FILTER_AUTO_SCROLL}.
   *
   * @param pivot The node to check.
   * @param scrollableNodeInfo The info about the scrollable container for which is checked if the
   *     pivot is at the edge or not.
   * @param ignoreDescendantsOfPivot Whether to ignore descendants of pivot when search down the
   *     node tree.
   * @param searchDirection The direction in which to check.
   * @return true if the current item is at the edge of a list.
   */
  @JvmStatic
  fun isAutoScrollEdgeListItem(
    pivot: AccessibilityNodeInfoCompat,
    scrollableNodeInfo: ScrollableNodeInfo,
    ignoreDescendantsOfPivot: Boolean,
    @TraversalStrategy.SearchDirection searchDirection: Int,
    focusFinder: FocusFinder,
  ): Boolean {
    val supportedDirection =
      scrollableNodeInfo.getSupportedScrollDirection(searchDirection) ?: return false

    val traversalStrategy =
      scrollableNodeInfo.getSupportedTraversalStrategy(supportedDirection, focusFinder)
    return isMatchingEdgeListItem(
      pivot,
      scrollableNodeInfo.node,
      ignoreDescendantsOfPivot,
      supportedDirection,
      AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL,
      traversalStrategy,
    )
  }

  /**
   * Utility method for determining if a searching past a particular node will fall off the edge of
   * a scrollable container.
   *
   * @param cursor Node to check.
   * @param scrollableNode The scrollable container that for checking the cursor is at the edge or
   *     not.
   * @param ignoreDescendantsOfCursor Whether to ignore descendants of cursor when search down the
   *     node tree.
   * @param direction The direction in which to move from the cursor.
   * @param filter Filter used to validate list-type ancestors.
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return {@code true} if focusing search in the specified direction will fall off the edge of
   *     the container.
   */
  private fun isMatchingEdgeListItem(
    cursor: AccessibilityNodeInfoCompat,
    scrollableNode: AccessibilityNodeInfoCompat,
    ignoreDescendantsOfCursor: Boolean,
    @TraversalStrategy.SearchDirection direction: Int,
    filter: Filter<AccessibilityNodeInfoCompat>,
    traversalStrategy: TraversalStrategy?,
  ): Boolean {
    var webViewNode: AccessibilityNodeInfoCompat? = null

    val cursorNodeNotContainedInScrollableList =
      !scrollableNode.isScrollable ||
        !(AccessibilityNodeInfoUtils.hasAncestor(cursor, scrollableNode) ||
          scrollableNode == cursor)

    if (cursorNodeNotContainedInScrollableList) {
      return false
    }

    var focusNodeFilter: Filter<AccessibilityNodeInfoCompat> =
      AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS
    if (ignoreDescendantsOfCursor) {
      focusNodeFilter =
        focusNodeFilter.and(
          object : Filter<AccessibilityNodeInfoCompat>() {
            override fun accept(obj: AccessibilityNodeInfoCompat): Boolean {
              return !AccessibilityNodeInfoUtils.hasAncestor(obj, cursor)
            }
          },
        )
    }
    val nextFocusNode = searchFocus(traversalStrategy, cursor, direction, focusNodeFilter)

    if (nextFocusNode == null || nextFocusNode == scrollableNode) {
      // Can't move from this position.
      return true
    }

    // if nextFocusNode is in WebView and not visible to user we still could set
    // accessibility  focus on it and WebView scrolls itself to show newly focused item
    // on the screen. But there could be situation that node is inside WebView bounds but
    // WebView is [partially] outside the screen bounds. In that case we don't ask WebView
    // to set accessibility focus but try to scroll scrollable parent to get the WebView
    // with nextFocusNode inside it to the screen bounds.
    if (!nextFocusNode.isVisibleToUser && WebInterfaceUtils.hasNativeWebContent(nextFocusNode)) {
      webViewNode =
        AccessibilityNodeInfoUtils.getMatchingAncestor(
          nextFocusNode,
          object : Filter<AccessibilityNodeInfoCompat>() {
            override fun accept(node: AccessibilityNodeInfoCompat): Boolean {
              return Role.getRole(node) == Role.ROLE_WEB_VIEW
            }
          },
        )

      if (webViewNode != null &&
        (!webViewNode.isVisibleToUser || isNodeInBoundsOfOther(webViewNode, nextFocusNode))
      ) {
        return true
      }
    }

    var searchedAncestor = AccessibilityNodeInfoUtils.getMatchingAncestor(nextFocusNode, filter)
    while (searchedAncestor != null) {
      if (scrollableNode == searchedAncestor) {
        return false
      }
      searchedAncestor = AccessibilityNodeInfoUtils.getMatchingAncestor(searchedAncestor, filter)
    }
    // Moves outside of the scrollable container.
    return true
  }

  /**
   * Search focus that satisfied specified node filter from currentFocus to specified direction
   * according to OrderTraversal strategy
   *
   * @param traversal - order traversal strategy
   * @param currentFocus - node that is starting point of focus search
   * @param direction - direction the target focus is searching to
   * @param filter - filters focused node candidate
   * @return node that could be focused next
   */
  @JvmStatic
  fun searchFocus(
    traversal: TraversalStrategy?,
    currentFocus: AccessibilityNodeInfoCompat?,
    @TraversalStrategy.SearchDirection direction: Int,
    filter: Filter<AccessibilityNodeInfoCompat>?,
  ): AccessibilityNodeInfoCompat? {
    if (traversal == null || currentFocus == null) {
      return null
    }

    val nodeFilter = filter ?: DEFAULT_FILTER

    var targetNode: AccessibilityNodeInfoCompat? = currentFocus
    val seenNodes = HashSet<AccessibilityNodeInfoCompat>()

    do {
      seenNodes.add(targetNode!!)
      targetNode = traversal.findFocus(targetNode, direction)
      DiagnosticOverlayUtils.appendLog(DiagnosticOverlayUtils.SEARCH_FOCUS_FAIL, targetNode)

      if (seenNodes.contains(targetNode)) {
        LogUtils.e(TAG, "Found duplicate during traversal: %s", targetNode)
        return null
      }
    } while (targetNode != null && !nodeFilter.accept(targetNode))

    return targetNode
  }

  /**
   * Finds the first focusable accessibility node in hierarchy started from root node when searching
   * in the given direction.
   *
   * <p>For example, if {@code direction} is {@link TraversalStrategy#SEARCH_FOCUS_FORWARD}, then
   * the method should return the first node in the traversal order. If {@code direction} is {@link
   * TraversalStrategy#SEARCH_FOCUS_BACKWARD} then the method should return the last node in the
   * traversal order.
   *
   * @param traversalStrategy the traversal strategy
   * @param root the root node
   * @param direction the direction to search from
   * @param nodeFilter the {@link Filter} to determine which nodes to focus
   * @return returns the first node that matches nodeFilter
   */
  @JvmStatic
  fun findFirstFocusInNodeTree(
    traversalStrategy: TraversalStrategy,
    root: AccessibilityNodeInfoCompat?,
    @TraversalStrategy.SearchDirection direction: Int,
    nodeFilter: Filter<AccessibilityNodeInfoCompat>,
  ): AccessibilityNodeInfoCompat? {
    if (root == null) {
      return null
    }
    val firstNode = traversalStrategy.focusFirst(root, direction)

    if (nodeFilter.accept(firstNode)) {
      return firstNode
    }
    return searchFocus(traversalStrategy, firstNode, direction, nodeFilter)
  }

  private fun isNodeInBoundsOfOther(
    outerNode: AccessibilityNodeInfoCompat?,
    innerNode: AccessibilityNodeInfoCompat?,
  ): Boolean {
    if (outerNode == null || innerNode == null) {
      return false
    }

    val outerRect = Rect()
    val innerRect = Rect()
    outerNode.getBoundsInScreen(outerRect)
    innerNode.getBoundsInScreen(innerRect)

    if (outerRect.top > innerRect.bottom || outerRect.bottom < innerRect.top) {
      return false
    }

    if (outerRect.left > innerRect.right || outerRect.right < innerRect.left) {
      return false
    }

    return true
  }

  private val DEFAULT_FILTER: Filter<AccessibilityNodeInfoCompat> =
    object : Filter<AccessibilityNodeInfoCompat>() {
      override fun accept(node: AccessibilityNodeInfoCompat?): Boolean = node != null
    }

  @JvmStatic
  fun directionToString(@TraversalStrategy.SearchDirectionOrUnknown direction: Int): String =
    when (direction) {
      SEARCH_FOCUS_FORWARD -> "SEARCH_FOCUS_FORWARD"
      SEARCH_FOCUS_BACKWARD -> "SEARCH_FOCUS_BACKWARD"
      SEARCH_FOCUS_LEFT -> "SEARCH_FOCUS_LEFT"
      SEARCH_FOCUS_RIGHT -> "SEARCH_FOCUS_RIGHT"
      SEARCH_FOCUS_UP -> "SEARCH_FOCUS_UP"
      SEARCH_FOCUS_DOWN -> "SEARCH_FOCUS_DOWN"
      TraversalStrategy.SEARCH_FOCUS_UNKNOWN -> "SEARCH_FOCUS_UNKNOWN"
      else -> "(unhandled)"
    }
}
