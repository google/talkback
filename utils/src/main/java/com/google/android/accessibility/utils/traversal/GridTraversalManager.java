package com.google.android.accessibility.utils.traversal;

import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.hasAncestor;

import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Utility class for managing traversal in grids. */
public class GridTraversalManager {
  private static final String TAG = "GridTraversalManager";

  // Prevent instantiation.
  private GridTraversalManager() {}

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
  @Nullable
  public static Pair<Integer, Integer> suggestOffScreenTarget(
      @NonNull AccessibilityNodeInfoCompat gridNode,
      @NonNull AccessibilityNodeInfoCompat currentNode,
      @NonNull AccessibilityNodeInfoCompat targetNode,
      int searchDirection) {
    Pair<Integer, Integer> alternateTarget =
        getAlternateTarget(gridNode, currentNode, targetNode, searchDirection);
    if (alternateTarget != null) {
      int targetRow = targetNode.getCollectionItemInfo().getRowIndex();
      int targetColumn = targetNode.getCollectionItemInfo().getColumnIndex();
      // Ensure that the suggested target is different from the existing target.
      if (targetRow != alternateTarget.first || targetColumn != alternateTarget.second) {
        return alternateTarget;
      }
      LogUtils.d(
          TAG, "No need to scroll because suggested row/column match the current " + "target");
    }
    LogUtils.d(TAG, "No suggested target for scrolling");
    return null;
  }

  @Nullable
  private static Pair<Integer, Integer> getAlternateTarget(
      @NonNull AccessibilityNodeInfoCompat gridNode,
      @NonNull AccessibilityNodeInfoCompat currentNode,
      @NonNull AccessibilityNodeInfoCompat targetNode,
      int searchDirection) {
    if (!checkPreconditions(gridNode, currentNode, targetNode)) {
      return null;
    }

    int numRows = gridNode.getCollectionInfo().getRowCount();
    int numColumns = gridNode.getCollectionInfo().getColumnCount();
    int currentRow = currentNode.getCollectionItemInfo().getRowIndex();
    int currentColumn = currentNode.getCollectionItemInfo().getColumnIndex();

    switch (searchDirection) {
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        if (currentColumn + 1 == numColumns) {
          if (currentRow + 1 == numRows) {
            // End of grid.
            return null;
          }
          // Send to the beginning of the next row.
          return Pair.create(currentRow + 1, 0);
        }
        return Pair.create(currentRow, currentColumn + 1);
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        if (currentColumn == 0) {
          if (currentRow == 0) {
            // Beginning of grid.
            return null;
          }
          // Send to the end of the previous row.
          return Pair.create(currentRow - 1, numColumns - 1);
        }
        return Pair.create(currentRow, currentColumn - 1);
      default:
        return null;
    }
  }

  private static boolean checkPreconditions(
      @NonNull AccessibilityNodeInfoCompat gridNode,
      @NonNull AccessibilityNodeInfoCompat currentNode,
      @NonNull AccessibilityNodeInfoCompat targetNode) {
    return hasAncestor(currentNode, gridNode)
        && hasAncestor(targetNode, gridNode)
        && AccessibilityNodeInfoUtils.hasUsableCollectionItemInfo(currentNode, gridNode)
        && AccessibilityNodeInfoUtils.hasUsableCollectionItemInfo(targetNode, gridNode)
        && gridNode.getActionList().contains(AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION)
        && Role.getRole(gridNode) == Role.ROLE_GRID;
  }
}
