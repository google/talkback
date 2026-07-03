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

import android.util.Pair
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.hasAncestor
import com.google.android.accessibility.utils.Role
import com.google.android.libraries.accessibility.utils.log.LogUtils

/** Utility class for managing traversal in grids. */
object GridTraversalManager {
  private const val TAG = "GridTraversalManager"

  /**
   * Uses the {@link AccessibilityNodeInfoCompat.CollectionInfoCompat} of the grid, the {@link
   * AccessibilityNodeInfoCompat.CollectionItemInfoCompat} of the view currently holding
   * accessibility focus, and the {@link AccessibilityNodeInfoCompat.CollectionItemInfoCompat} of
   * the view targeted for accessibility focus to evaluate the correctness of the current target and
   * optionally recommend a different target node for accessibility focus.
   *
   * @param gridNode The node representing the grid.
   * @param currentNode The node representing a cell within the grid which currently holds
   *     accessibility focus.
   * @param targetNode The node representing a cell within the grid which is targeted for
   *     accessibility focus.
   * @param searchDirection The search direction for finding the target node.
   * @return The row, column positions of the suggested target node, or null if either the {@link
   *     AccessibilityNodeInfoCompat.CollectionInfoCompat} or {@link
   *     AccessibilityNodeInfoCompat.CollectionItemInfoCompat} are absent or incomplete, or if the
   *     grid does not support {@link
   *     android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction#ACTION_SCROLL_TO_POSITION},
   *     or if {@code target} is already correct.
   */
  @JvmStatic
  fun suggestOffScreenTarget(
    gridNode: AccessibilityNodeInfoCompat,
    currentNode: AccessibilityNodeInfoCompat,
    targetNode: AccessibilityNodeInfoCompat,
    searchDirection: Int,
  ): Pair<Int, Int>? {
    val alternateTarget = getAlternateTarget(gridNode, currentNode, targetNode, searchDirection)
    if (alternateTarget != null) {
      val targetRow = targetNode.collectionItemInfo!!.rowIndex
      val targetColumn = targetNode.collectionItemInfo!!.columnIndex
      // Ensure that the suggested target is different from the existing target.
      if (targetRow != alternateTarget.first || targetColumn != alternateTarget.second) {
        return alternateTarget
      }
      LogUtils.d(TAG, "No need to scroll because suggested row/column match the current " + "target")
    }
    LogUtils.d(TAG, "No suggested target for scrolling")
    return null
  }

  private fun getAlternateTarget(
    gridNode: AccessibilityNodeInfoCompat,
    currentNode: AccessibilityNodeInfoCompat,
    targetNode: AccessibilityNodeInfoCompat,
    searchDirection: Int,
  ): Pair<Int, Int>? {
    if (!checkPreconditions(gridNode, currentNode, targetNode)) {
      return null
    }

    val numRows = gridNode.collectionInfo!!.rowCount
    val numColumns = gridNode.collectionInfo!!.columnCount
    val currentRow = currentNode.collectionItemInfo!!.rowIndex
    val currentColumn = currentNode.collectionItemInfo!!.columnIndex

    return when (searchDirection) {
      TraversalStrategy.SEARCH_FOCUS_FORWARD -> {
        if (currentColumn + 1 == numColumns) {
          if (currentRow + 1 == numRows) {
            // End of grid.
            return null
          }
          // Send to the beginning of the next row.
          return Pair.create(currentRow + 1, 0)
        }
        Pair.create(currentRow, currentColumn + 1)
      }
      TraversalStrategy.SEARCH_FOCUS_BACKWARD -> {
        if (currentColumn == 0) {
          if (currentRow == 0) {
            // Beginning of grid.
            return null
          }
          // Send to the end of the previous row.
          return Pair.create(currentRow - 1, numColumns - 1)
        }
        Pair.create(currentRow, currentColumn - 1)
      }
      else -> null
    }
  }

  private fun checkPreconditions(
    gridNode: AccessibilityNodeInfoCompat,
    currentNode: AccessibilityNodeInfoCompat,
    targetNode: AccessibilityNodeInfoCompat,
  ): Boolean =
    hasAncestor(currentNode, gridNode) &&
      hasAncestor(targetNode, gridNode) &&
      AccessibilityNodeInfoUtils.hasUsableCollectionItemInfo(currentNode, gridNode) &&
      AccessibilityNodeInfoUtils.hasUsableCollectionItemInfo(targetNode, gridNode) &&
      gridNode.actionList.contains(AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION) &&
      Role.getRole(gridNode) == Role.ROLE_GRID
}
