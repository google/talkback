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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.google.android.accessibility.utils.traversal.DirectionalTraversalStrategy
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy
import com.google.android.accessibility.utils.traversal.TraversalStrategy
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils
import com.google.android.libraries.accessibility.utils.log.LogUtils

/**
 * Wrapper around a scrollable {@link AccessibilityNodeInfoCompat}.
 *
 * <p>Facilitates fallbacks for search directions not natively supported by widgets.
 *
 * @param node scrollable {@link AccessibilityNodeInfoCompat}
 * @param rtl {@code true} if the ui locale is right-to-left. It is Required for mapping logical
 *     to spatial directions.
 */
class ScrollableNodeInfo(
  /** The single node which is wrapped by the instance. */
  val node: AccessibilityNodeInfoCompat,
  private val rtl: Boolean,
) {
  private var supportsUpDownScrolling = false
  private var supportsLeftRightScrolling = false

  init {
    initSupportedDirections()
  }

  /**
   * Returns a supported {@code TraversalStrategy} for the specified {@code direction} if one
   * exists, and empty otherwise.
   *
   * @param direction {@link SearchDirection} for the node. The actual returned strategy may use a
   *     different, fallback direction.
   * @param focusFinder Reference to a {@link FocusFinder} that is needed to create a {@link
   *     DirectionalTraversalStrategy}.
   */
  fun getSupportedTraversalStrategy(
    @TraversalStrategy.SearchDirection direction: Int,
    focusFinder: FocusFinder?,
  ): TraversalStrategy? {
    val supportedDirection = getSupportedScrollDirection(direction) ?: return null
    if (TraversalStrategyUtils.isLogicalDirection(supportedDirection)) {
      return getLogicalTraversalStrategy()
    }
    if (TraversalStrategyUtils.isSpatialDirection(supportedDirection)) {
      return getSpatialTraversalStrategy(focusFinder)
    }
    return null
  }

  /**
   * Returns a supported {@link SearchDirection} that is equivalent to the specified {@code
   * searchDirection} if one exists, and {@code null} otherwise.
   */
  fun getSupportedScrollDirection(
    @TraversalStrategy.SearchDirection searchDirection: Int,
  ): Int? {
    val nativeDirection = getDirectionIfNativelySupported(searchDirection)
    if (nativeDirection != null) {
      return nativeDirection
    }
    // fallback
    if (TraversalStrategyUtils.isLogicalDirection(searchDirection)) {
      if (supportsUpDownScrolling && supportsLeftRightScrolling) {
        // If two axes can be scrolled, we would not know which one to scroll.
        return null
      }
      if (supportsUpDownScrolling) {
        return getDirectionIfNativelySupported(
          if (searchDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
            TraversalStrategy.SEARCH_FOCUS_DOWN
          } else {
            TraversalStrategy.SEARCH_FOCUS_UP
          },
        )
      }
      if (supportsLeftRightScrolling) {
        @TraversalStrategy.SearchDirection
        val forward =
          if (rtl) TraversalStrategy.SEARCH_FOCUS_LEFT else TraversalStrategy.SEARCH_FOCUS_RIGHT
        @TraversalStrategy.SearchDirection
        val backward =
          if (rtl) TraversalStrategy.SEARCH_FOCUS_RIGHT else TraversalStrategy.SEARCH_FOCUS_LEFT
        return getDirectionIfNativelySupported(
          if (searchDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) forward else backward,
        )
      }
    }
    if (TraversalStrategyUtils.isSpatialDirection(searchDirection)) {
      return getDirectionIfNativelySupported(
        TraversalStrategyUtils.getLogicalDirection(searchDirection, rtl),
      )
    }

    return null
  }

  private fun getDirectionIfNativelySupported(
    @TraversalStrategy.SearchDirection searchDirection: Int,
  ): Int? {
    val desiredAction = TraversalStrategyUtils.convertSearchDirectionToScrollAction(searchDirection)
    for (action in node.actionList) {
      if (action.id == desiredAction) {
        return searchDirection
      }
    }
    return null
  }

  private fun initSupportedDirections() {
    supportsUpDownScrolling = false
    supportsLeftRightScrolling = false
    for (action in node.actionList) {
      if (action == AccessibilityActionCompat.ACTION_SCROLL_UP ||
        action == AccessibilityActionCompat.ACTION_SCROLL_DOWN
      ) {
        supportsUpDownScrolling = true
      }
      if (action == AccessibilityActionCompat.ACTION_SCROLL_LEFT ||
        action == AccessibilityActionCompat.ACTION_SCROLL_RIGHT
      ) {
        supportsLeftRightScrolling = true
      }
    }
  }

  private fun getLogicalTraversalStrategy(): TraversalStrategy =
    OrderedTraversalStrategy(AccessibilityNodeInfoUtils.getRoot(node))

  private fun getSpatialTraversalStrategy(focusFinder: FocusFinder?): TraversalStrategy =
    DirectionalTraversalStrategy(AccessibilityNodeInfoUtils.getRoot(node), focusFinder)

  companion object {
    private const val TAG = "ScrollableNodeInfo"

    /**
     * Returns a {@link ScrollableNodeInfo} for a node that is an ancestor of {@code pivot} and can
     * be scrolled in the specified {@code direction} or an equivalent fallback direction if one
     * exists, and {@code null} otherwise.
     *
     * @param direction The direction in which a scroll is requested.
     * @param pivot The node for which an ancestor is searched.
     * @param includeSelf Whether the {@code pivot} is allowed to be the ancestor.
     * @param rtl Whether the window has RTL direction. This is required to map logical to spatial
     *     direction and vice-versa.
     */
    @JvmStatic
    fun findScrollableNodeForDirection(
      @TraversalStrategy.SearchDirectionOrUnknown direction: Int,
      pivot: AccessibilityNodeInfoCompat,
      includeSelf: Boolean,
      rtl: Boolean,
    ): ScrollableNodeInfo? {
      if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
        return null
      }
      if (includeSelf) {
        val match = findMatchingScrollable(direction, pivot, rtl)
        if (match != null) {
          return match
        }
      }
      if (pivot.parent != null) {
        val visited = HashSet<AccessibilityNodeInfoCompat>()
        return findScrollableNodeForDirectionRecursive(direction, pivot.parent, rtl, visited)
      }
      return null
    }

    private fun findScrollableNodeForDirectionRecursive(
      @TraversalStrategy.SearchDirection direction: Int,
      node: AccessibilityNodeInfoCompat?,
      rtl: Boolean,
      visited: MutableSet<AccessibilityNodeInfoCompat>,
    ): ScrollableNodeInfo? {
      // If node already checked... quit.
      if (node == null || visited.contains(node)) {
        return null
      } else {
        visited.add(node)
      }

      val match = findMatchingScrollable(direction, node, rtl)
      if (match != null) {
        return match
      }
      if (node.parent != null) {
        return findScrollableNodeForDirectionRecursive(direction, node.parent, rtl, visited)
      }
      return null
    }

    private fun findMatchingScrollable(
      @TraversalStrategy.SearchDirection direction: Int,
      node: AccessibilityNodeInfoCompat,
      rtl: Boolean,
    ): ScrollableNodeInfo? {
      if (!AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL.accept(node)) {
        return null
      }

      val scrollableNodeInfo = ScrollableNodeInfo(node, rtl)
      val supportedDirection = scrollableNodeInfo.getSupportedScrollDirection(direction)
      if (supportedDirection != null) {
        val scrollableFilter =
          NodeActionFilter(
            TraversalStrategyUtils.convertSearchDirectionToScrollAction(supportedDirection))
        if (scrollableFilter.accept(node)) {
          return scrollableNodeInfo
        }
      } else {
        LogUtils.d(
          TAG,
          "findMatchingScrollable - supportedDirection is null, direction = %s",
          TraversalStrategy.getSymbolicName(direction),
        )
      }
      return null
    }
  }
}
