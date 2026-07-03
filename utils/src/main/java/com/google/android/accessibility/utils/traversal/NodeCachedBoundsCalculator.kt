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
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.libraries.accessibility.utils.log.LogUtils
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates the utility bounds of the node. If node is not supposed to get accessibility focus the
 * utility bounds is calculated on the base of minimum rect that contains all accessibility
 * focusable nodes inside node hierarchy rooted by this node.
 */
class NodeCachedBoundsCalculator {

  private val boundsMap: MutableMap<AccessibilityNodeInfoCompat, Rect> = HashMap()
  private val calculatingNodes: MutableSet<AccessibilityNodeInfoCompat> = HashSet()
  private val tempRect = Rect()
  private var speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>? = null

  fun setSpeakingNodesCache(speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>?) {
    this.speakingNodesCache = speakingNodesCache
  }

  fun getBounds(node: AccessibilityNodeInfoCompat?): Rect? {
    val bounds = getBoundsInternal(node)
    if (bounds == EMPTY_RECT) {
      return null
    }

    return bounds
  }

  private fun getBoundsInternal(node: AccessibilityNodeInfoCompat?): Rect {
    if (node == null) {
      return EMPTY_RECT
    }

    if (calculatingNodes.contains(node)) {
      LogUtils.w(TAG, "node tree loop detected while calculating node bounds")
      return EMPTY_RECT
    }

    var bounds = boundsMap[node]
    if (bounds == null) {
      calculatingNodes.add(node)
      bounds = fetchBound(node)
      boundsMap[node] = bounds
      calculatingNodes.remove(node)
    }

    return bounds
  }

  private fun fetchBound(node: AccessibilityNodeInfoCompat?): Rect {
    if (node == null || !AccessibilityNodeInfoUtils.isVisible(node)) {
      return EMPTY_RECT
    }

    if (AccessibilityNodeInfoUtils.shouldFocusNode(node, speakingNodesCache)) {
      val bounds = Rect()
      node.getBoundsInScreen(bounds)
      return bounds
    }

    val childCount = node.childCount
    var minTop = Int.MAX_VALUE
    var minLeft = Int.MAX_VALUE
    var maxBottom = Int.MIN_VALUE
    var maxRight = Int.MIN_VALUE
    var child: AccessibilityNodeInfoCompat?
    var hasChildBounds = false
    for (i in 0 until childCount) {
      child = node.getChild(i)
      val bounds = getBoundsInternal(child)
      if (bounds != EMPTY_RECT) {
        hasChildBounds = true
        if (bounds.top < minTop) {
          minTop = bounds.top
        }

        if (bounds.left < minLeft) {
          minLeft = bounds.left
        }

        if (bounds.right > maxRight) {
          maxRight = bounds.right
        }

        if (bounds.bottom > maxBottom) {
          maxBottom = bounds.bottom
        }
      }
    }

    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    if (hasChildBounds) {
      bounds.top = max(minTop, bounds.top)
      bounds.left = max(minLeft, bounds.left)
      bounds.right = min(maxRight, bounds.right)
      bounds.bottom = min(maxBottom, bounds.bottom)
    }

    return bounds
  }

  /**
   * If node is not supposed to be accessibility focused by TalkBack NodeBoundsCalculator calculates
   * useful bounds of focusable children. The method checks if the node uses its children useful
   * bounds or uses its own bounds
   */
  fun usesChildrenBounds(node: AccessibilityNodeInfoCompat?): Boolean {
    if (node == null) {
      return false
    }

    val bounds = getBounds(node) ?: return false

    node.getBoundsInScreen(tempRect)
    return tempRect != bounds
  }

  private companion object {
    const val TAG = "NodeCachedBoundsCalculator"

    val EMPTY_RECT = Rect()
  }
}
