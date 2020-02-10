/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.switchaccess.treebuilding;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.treenodes.ClearFocusNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Builds a binary tree for row-column scanning. The rows are linear scanned, as are the elements
 * within the row. Note that this builder ignores the hierarchy of the views entirely. It just
 * groups Views based on their spatial location. That works fine for something like a keyboard, but
 * will not be ideal for all UIs.
 */
public class RowColumnTreeBuilder extends BinaryTreeBuilder {
  /* Any rows shorter than this should just be linearly scanned */
  private static final int MIN_NODES_PER_ROW = 3;

  private static final Comparator<RowBounds> ROW_BOUNDS_COMPARATOR =
      (rowBounds, otherRowBounds) -> {
        if (!rowBounds.equals(otherRowBounds)
            && (rowBounds.top <= otherRowBounds.top)
            && (rowBounds.bottom >= otherRowBounds.bottom)
            && (rowBounds.left != otherRowBounds.left)) {
          /*
           * For rows that vertically span multiple other rows, traverse the left ones earlier. For
           * example, the diagram below shows the correct traversal of the given rows.
           * _____________
           * |   |   |   |
           * | 1 | 2 |   |
           * |___|___|   |
           * |   |   |   |
           * | 3 | 4 | 7 |
           * |___|___|   |
           * |   |   |   |
           * | 5 | 6 |   |
           * |___|___|___|
           */
          return otherRowBounds.left - rowBounds.left;
        } else if (rowBounds.top != otherRowBounds.top) {
          /* Want higher y coordinates to be traversed earlier. */
          return otherRowBounds.top - rowBounds.top;
        }
        /* Want larger views to be traversed earlier. */
        return rowBounds.bottom - otherRowBounds.bottom;
      };

  private static class RowBounds {
    private final int top;
    private final int bottom;

    // Used only for sorting, not for equality. If two RowBounds have equal tops and bottoms but
    // different lefts, they are still equal by definition of a row. i.e. items next to each other
    // with identical tops and bottoms are considered to be in the same row, regardless of
    // horizontal position.
    private final int left;

    public RowBounds(int top, int bottom, int left) {
      this.top = top;
      this.bottom = bottom;
      this.left = left;
    }

    @Override
    public int hashCode() {
      /* Not the most general hash, but sufficient for reasonable screen sizes */
      return (top << 16) + bottom;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return (o instanceof RowBounds)
          && (((RowBounds) o).top == top)
          && (((RowBounds) o).bottom == bottom);
    }
  }

  public RowColumnTreeBuilder(AccessibilityService service) {
    super(service);
  }

  @Override
  public TreeScanNode addViewHierarchyToTree(
      SwitchAccessNodeCompat root,
      TreeScanNode treeToBuildOn,
      boolean shouldScanNonActionableItems) {
    TreeScanNode tree = (treeToBuildOn != null) ? treeToBuildOn : new ClearFocusNode();
    SortedMap<RowBounds, SortedMap<Integer, SwitchAccessNodeCompat>> nodesByXYCoordinate =
        getMapOfNodesByXYCoordinate(root, shouldScanNonActionableItems);
    for (SortedMap<Integer, SwitchAccessNodeCompat> nodesInThisRow : nodesByXYCoordinate.values()) {
      if (nodesInThisRow.size() < MIN_NODES_PER_ROW) {
        for (SwitchAccessNodeCompat node : nodesInThisRow.values()) {
          tree = addCompatToTree(node, tree, shouldScanNonActionableItems);
          node.recycle();
        }
      } else {
        TreeScanNode rowTree = new ClearFocusNode();
        for (SwitchAccessNodeCompat node : nodesInThisRow.values()) {
          rowTree = addCompatToTree(node, rowTree, shouldScanNonActionableItems);
          node.recycle();
        }
        tree = new TreeScanSelectionNode(rowTree, tree);
      }
    }
    return tree;
  }

  private SortedMap<RowBounds, SortedMap<Integer, SwitchAccessNodeCompat>>
      getMapOfNodesByXYCoordinate(
          SwitchAccessNodeCompat root, boolean shouldScanNonActionableItems) {
    SortedMap<RowBounds, SortedMap<Integer, SwitchAccessNodeCompat>> nodesByXYCoordinate =
        new TreeMap<>(ROW_BOUNDS_COMPARATOR);
    List<SwitchAccessNodeCompat> talkBackOrderList = getNodesInTalkBackOrder(root);
    Rect boundsInScreen = new Rect();
    for (SwitchAccessNodeCompat node : talkBackOrderList) {
      /* Only add the node to list if it will be added to the tree */
      TreeScanNode treeWithCurrentNode =
          addCompatToTree(node, new ClearFocusNode(), shouldScanNonActionableItems);
      if (treeWithCurrentNode instanceof TreeScanSelectionNode) {
        node.getVisibleBoundsInScreen(boundsInScreen);
        /*
         * Use negative value so traversal will start with the last elements, so the first
         * ones end up at the top of the tree.
         */
        RowBounds rowBounds =
            new RowBounds(boundsInScreen.top, boundsInScreen.bottom, boundsInScreen.left);
        SortedMap<Integer, SwitchAccessNodeCompat> mapOfNodes = nodesByXYCoordinate.get(rowBounds);
        if (mapOfNodes == null) {
          mapOfNodes = new TreeMap<>();
          nodesByXYCoordinate.put(rowBounds, mapOfNodes);
        }
        int nodeKey = -boundsInScreen.left;
        // Make sure we don't overwrite an existing value.
        while (mapOfNodes.containsKey(nodeKey)) {
          nodeKey += 1;
        }
        mapOfNodes.put(nodeKey, node);
      } else {
        node.recycle();
      }
      treeWithCurrentNode.recycle();
    }
    return nodesByXYCoordinate;
  }
}
