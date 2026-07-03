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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.WebInterfaceUtils

/**
 * Children nodes iterator that iterates its children according the order of AccessibilityNodeInfo
 * hierarchy. But for nodes that are not considered to be focused according to
 * AccessibilityNodeInfoUtils.shouldFocusNode() rules we calculate new bounds that is minimum
 * rectangle that contains all focusable children nodes. If that rectangle differs from real node
 * bounds that node is reordered according needSwapNodeOrder() logic and could be traversed later.
 *
 * <p>This class obtains new instances of AccessibilityNodeCompat.
 */
class ReorderedChildrenIterator private constructor(
  private val parent: AccessibilityNodeInfoCompat,
  private val isAscending: Boolean,
  boundsCalculator: NodeCachedBoundsCalculator?,
) : MutableIterator<AccessibilityNodeInfoCompat> {

  private var currentIndex: Int
  private val nodes: MutableList<AccessibilityNodeInfoCompat>
  private val boundsCalculator: NodeCachedBoundsCalculator =
    boundsCalculator ?: NodeCachedBoundsCalculator()

  // Avoid constantly creating and discarding Rects.
  private val mTempLeftBounds = Rect()
  private val mTempRightBounds = Rect()

  init {
    nodes = ArrayList(this.parent.childCount)
    init(this.parent)
    currentIndex = if (this.isAscending) 0 else nodes.size - 1
  }

  private fun init(node: AccessibilityNodeInfoCompat) {
    fillNodesFromParent()
    if (!WebInterfaceUtils.isWebContainer(node) && needReordering(nodes)) {
      reorder(nodes)
    }
  }

  private fun needReordering(nodes: List<AccessibilityNodeInfoCompat>?): Boolean {
    if (nodes == null || nodes.size == 1) {
      return false
    }

    for (node in nodes) {
      if (boundsCalculator.usesChildrenBounds(node)) {
        return true
      }
    }

    return false
  }

  private fun reorder(nodes: MutableList<AccessibilityNodeInfoCompat>?) {
    if (nodes == null || nodes.size == 1) {
      return
    }

    val size = nodes.size
    val nodeArray = arrayOfNulls<AccessibilityNodeInfoCompat>(size)
    for (i in nodes.indices) {
      nodeArray[i] = nodes[i]
    }

    var currentIndex = size - 2
    while (currentIndex >= 0) {
      val currentNode = nodeArray[currentIndex]
      if (boundsCalculator.usesChildrenBounds(currentNode)) {
        moveNodeIfNecessary(nodeArray, currentIndex)
      }

      currentIndex--
    }

    nodes.clear()
    for (node in nodeArray) {
      nodes.add(node!!)
    }
  }

  private fun moveNodeIfNecessary(nodeArray: Array<AccessibilityNodeInfoCompat?>, index: Int) {
    val size = nodeArray.size
    var nextIndex = index + 1
    val currentNode = nodeArray[index]
    while (nextIndex < size && needSwapNodeOrder(currentNode, nodeArray[nextIndex])) {
      nodeArray[nextIndex - 1] = nodeArray[nextIndex]
      nodeArray[nextIndex] = currentNode
      nextIndex++
    }
  }

  private fun needSwapNodeOrder(
    leftNode: AccessibilityNodeInfoCompat?,
    rightNode: AccessibilityNodeInfoCompat?,
  ): Boolean {
    if (leftNode == null || rightNode == null) {
      return false
    }

    val leftBounds = boundsCalculator.getBounds(leftNode)
    val rightBounds = boundsCalculator.getBounds(rightNode)

    // Sometimes the bounds compare() is overzealous, so swap the items only if the adjusted
    // (mBoundsCalculator) leftBounds > rightBounds but the original leftBounds < rightBounds,
    // i.e. the compare() method returns the existing ordering for the original bounds but
    // wants a swap for the adjusted bounds.
    // Simply, if compare() says that the original system ordering is wrong, then we cannot
    // trust its judgment in the adjusted bounds case.
    //
    // Example:
    // (1) Page scrolled to top  (2) Page scrolled to bottom.
    // +----------+              +----------+
    // | App bar  |              | App bar  |
    // +----------+              +----------+
    // | Item 1   |              | Item 2   |
    // | Item 2   |              | Item 3   |
    // | Item 3   |              | (spacer) |
    // +----------+              +----------+
    // Note: App bar overlays the top part of the list; the top, left, and right edges of the
    // list line up with the app bar. Assume that the spacer is not important for accessibility.
    // In this example, the traversal order for (1) is Item 1 -> Item 2 -> Item 3 -> App bar
    // but the traversal order for (2) gets reordered to App bar -> Item 2 -> Item 3.
    // So during auto-scrolling the app bar is actually excluded from the traversal order until
    // after the wrap-around.
    if (compare(leftBounds, rightBounds) > 0) {
      leftNode.getBoundsInScreen(mTempLeftBounds)
      rightNode.getBoundsInScreen(mTempRightBounds)
      return compare(mTempLeftBounds, mTempRightBounds) < 0
    }

    return false
  }

  /**
   * Returns a negative value if the inputs are ordered {@code {leftBounds, rightBounds}} and a
   * positive value if the inputs are ordered {@code {rightBounds, leftBounds}}. Guaranteed to not
   * return 0.
   *
   * <p>The ordering is determined via an algorithm similar to the {@link
   * android.view.ViewGroup.ViewLocationHolder#COMPARISON_STRATEGY_STRIPE} strategy used by the
   * framework to sort children of ViewGroups. This is essentially copied from {@link
   * android.view.ViewGroup.ViewLocationHolder#compareTo} with minor modifications.
   */
  private fun compare(leftBounds: Rect?, rightBounds: Rect?): Int {
    if (leftBounds == null || rightBounds == null) {
      return -1
    }

    // First is above second.
    if (leftBounds.bottom - rightBounds.top <= 0) {
      return -1
    }
    // First is below second.
    if (leftBounds.top - rightBounds.bottom >= 0) {
      return 1
    }

    // We are ordering left-to-right, top-to-bottom.
    if (RIGHT_TO_LEFT) {
      val rightDifference = leftBounds.right - rightBounds.right
      if (rightDifference != 0) {
        return -rightDifference
      }
    } else { // LTR
      val leftDifference = leftBounds.left - rightBounds.left
      if (leftDifference != 0) {
        return leftDifference
      }
    }
    // We are ordering left-to-right, top-to-bottom.
    val topDifference = leftBounds.top - rightBounds.top
    if (topDifference != 0) {
      return topDifference
    }
    // Break tie by height.
    val heightDifference = leftBounds.height() - rightBounds.height()
    if (heightDifference != 0) {
      return -heightDifference
    }
    // Break tie by width.
    val widthDifference = leftBounds.width() - rightBounds.width()
    if (widthDifference != 0) {
      return -widthDifference
    }
    // Break tie somehow.
    return -1
  }

  private fun fillNodesFromParent() {
    val count = parent.childCount
    for (i in 0 until count) {
      val node = parent.getChild(i)
      if (node != null) {
        nodes.add(node)
      }
    }
  }

  override fun hasNext(): Boolean = if (isAscending) currentIndex < nodes.size else currentIndex >= 0

  override fun next(): AccessibilityNodeInfoCompat {
    val nextNode = nodes[currentIndex]
    if (isAscending) {
      currentIndex++
    } else {
      currentIndex--
    }

    return nextNode
  }

  override fun remove() {
    throw UnsupportedOperationException(
      "ReorderedChildrenIterator does not support remove operation")
  }

  companion object {
    // TODO: Refactor to get RTL state.
    private const val RIGHT_TO_LEFT = false

    @JvmStatic
    fun createAscendingIterator(parent: AccessibilityNodeInfoCompat): ReorderedChildrenIterator =
      createAscendingIterator(parent, /* boundsCalculator= */ null)!!

    @JvmStatic
    fun createDescendingIterator(parent: AccessibilityNodeInfoCompat): ReorderedChildrenIterator =
      createDescendingIterator(parent, /* boundsCalculator= */ null)!!

    @JvmStatic
    fun createAscendingIterator(
      parent: AccessibilityNodeInfoCompat?,
      boundsCalculator: NodeCachedBoundsCalculator?,
    ): ReorderedChildrenIterator? {
      if (parent == null) {
        return null
      }

      return ReorderedChildrenIterator(parent, /* isAscending= */ true, boundsCalculator)
    }

    @JvmStatic
    fun createDescendingIterator(
      parent: AccessibilityNodeInfoCompat?,
      boundsCalculator: NodeCachedBoundsCalculator?,
    ): ReorderedChildrenIterator? {
      if (parent == null) {
        return null
      }

      return ReorderedChildrenIterator(parent, /* isAscending= */ false, boundsCalculator)
    }
  }
}
