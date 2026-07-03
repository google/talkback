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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Window could have its content views hierarchy. Views in that hierarchy could be traversed one
 * after another. Every view inside that hierarchy could change its natural traverse order by
 * setting traversal before/after view. See {@link android.view.View.getTraversalBefore()}, {@link
 * android.view.View.getTraversalAfter()}.
 *
 * <p>This strategy considers changes in the traverse order according to after/before view movements
 */
class OrderedTraversalStrategy
@JvmOverloads
constructor(
  rootNode: AccessibilityNodeInfoCompat?,
  includeChildrenOfNodesWithWebActions: Boolean = false,
  makeFabFirst: Boolean = false,
) : TraversalStrategy {

  private val controller: OrderedTraversalController
  private val speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean> = HashMap()

  init {
    controller = OrderedTraversalController(makeFabFirst)
    controller.setSpeakingNodesCache(speakingNodesCache)
    controller.initOrder(rootNode, includeChildrenOfNodesWithWebActions)
  }

  override fun getSpeakingNodesCache(): Map<AccessibilityNodeInfoCompat, Boolean> =
    speakingNodesCache

  override fun findFocus(
    startNode: AccessibilityNodeInfoCompat?,
    @TraversalStrategy.SearchDirection direction: Int,
  ): AccessibilityNodeInfoCompat? {
    when (direction) {
      TraversalStrategy.SEARCH_FOCUS_FORWARD -> {
        return focusNext(startNode)
      }
      TraversalStrategy.SEARCH_FOCUS_BACKWARD -> {
        return focusPrevious(startNode)
      }
      else -> {}
    }

    return null
  }

  private fun focusNext(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? =
    controller.findNext(node)

  private fun focusPrevious(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? =
    controller.findPrevious(node)

  override fun focusFirst(
    root: AccessibilityNodeInfoCompat?,
    @TraversalStrategy.SearchDirection direction: Int,
  ): AccessibilityNodeInfoCompat? =
    if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      controller.findFirst(root)
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      controller.findLast(root)
    } else {
      null
    }

  override fun focusInitial(root: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? =
    controller.findInitial(root)

  /** Dumps the traversal order tree. */
  fun dumpTree(): List<AccessibilityNodeInfoCompat> = controller.dumpTree()
}
