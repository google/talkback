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

import androidx.annotation.IntDef
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.Locale

/**
 * Strategy the is defined an order of traversing through the nodes of AccessibilityNodeInfo
 * hierarchy
 */
interface TraversalStrategy {

  /** Direction to search for an item to focus. */
  @IntDef(
    SEARCH_FOCUS_FORWARD,
    SEARCH_FOCUS_BACKWARD,
    SEARCH_FOCUS_LEFT,
    SEARCH_FOCUS_RIGHT,
    SEARCH_FOCUS_UP,
    SEARCH_FOCUS_DOWN,
  )
  @Retention(AnnotationRetention.SOURCE)
  annotation class SearchDirection

  /** Direction to search for an item to focus, or unknown. */
  @IntDef(
    SEARCH_FOCUS_UNKNOWN,
    SEARCH_FOCUS_FORWARD,
    SEARCH_FOCUS_BACKWARD,
    SEARCH_FOCUS_LEFT,
    SEARCH_FOCUS_RIGHT,
    SEARCH_FOCUS_UP,
    SEARCH_FOCUS_DOWN,
  )
  @Retention(AnnotationRetention.SOURCE)
  annotation class SearchDirectionOrUnknown

  /**
   * The method searches next node to be focused
   *
   * @param startNode - pivot node the search is start from
   * @param direction - direction to find focus
   * @return {@link androidx.core.view.accessibility.AccessibilityNodeInfoCompat} node that has next
   *     focus
   */
  fun findFocus(
    startNode: AccessibilityNodeInfoCompat?,
    @SearchDirection direction: Int,
  ): AccessibilityNodeInfoCompat?

  /**
   * Finds the first focusable accessibility node in hierarchy started from root node when searching
   * in the given direction.
   *
   * <p>For example, if {@code direction} is {@link #SEARCH_FOCUS_FORWARD}, then the method should
   * return the first node in the traversal order. If {@code direction} is {@link
   * #SEARCH_FOCUS_BACKWARD} then the method should return the last node in the traversal order.
   *
   * @param root root node
   * @param direction the direction to search from
   * @return returns the first node that could be focused
   */
  fun focusFirst(
    root: AccessibilityNodeInfoCompat?,
    @SearchDirection direction: Int,
  ): AccessibilityNodeInfoCompat?

  /**
   * Finds the initial focusable accessibility node in hierarchy started from root node.
   *
   * <p>This method should respect the result of {@link
   * AccessibilityNodeInfoCompat#hasRequestInitialAccessibilityFocus()}.
   *
   * @param root root node
   * @return returns the initial node that could be focused
   */
  fun focusInitial(root: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? = null

  /**
   * Calculating if node is speaking node according to AccessibilityNodeInfoUtils.isSpeakingNode()
   * method is time consuming. Traversal strategy may use cache for already calculated values. If
   * traversal strategy does not need in such cache use it could return null.
   *
   * @return speaking node cache map. Could be null if cache is not used by traversal strategy
   */
  fun getSpeakingNodesCache(): Map<AccessibilityNodeInfoCompat, Boolean>?

  companion object {
    const val SEARCH_FOCUS_UNKNOWN = 0
    const val SEARCH_FOCUS_FORWARD = 1
    const val SEARCH_FOCUS_BACKWARD = 2
    const val SEARCH_FOCUS_LEFT = 3
    const val SEARCH_FOCUS_RIGHT = 4
    const val SEARCH_FOCUS_UP = 5
    const val SEARCH_FOCUS_DOWN = 6

    @JvmStatic
    fun getSymbolicName(@SearchDirection direction: Int): String =
      when (direction) {
        SEARCH_FOCUS_FORWARD -> "SEARCH_FOCUS_FORWARD"
        SEARCH_FOCUS_BACKWARD -> "SEARCH_FOCUS_BACKWARD"
        SEARCH_FOCUS_LEFT -> "SEARCH_FOCUS_LEFT"
        SEARCH_FOCUS_RIGHT -> "SEARCH_FOCUS_RIGHT"
        SEARCH_FOCUS_UP -> "SEARCH_FOCUS_UP"
        SEARCH_FOCUS_DOWN -> "SEARCH_FOCUS_DOWN"
        else -> String.format(Locale.ENGLISH, "unavailable direction: %d", direction)
      }
  }
}
