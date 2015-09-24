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

package com.android.switchaccess;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;

import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * Build an option scanning tree for row-column scanning. The rows are linear scanned, as are the
 * elements withing the row.
 * Note that this builder ignores the hierarchy of the views entirely. It just groups Views based
 * on their spatial location. That works fine for something like a keyboard, but will not be
 * ideal for all UIs.
 */
public class RowColumnTreeBuilder  implements TreeBuilder {
    /* Any rows shorter than this should just be linearly scanned */
    private static int MIN_NODES_PER_ROW = 3;

    private static final Comparator<RowBounds> ROW_BOUNDS_COMPARATOR = new Comparator<RowBounds>() {
        @Override
        public int compare(RowBounds rowBounds, RowBounds t1) {
            if (rowBounds.mTop != t1.mTop) {
                /* Want higher y coords to be traversed later */
                return  t1.mTop - rowBounds.mTop;
            }
            /* Want larger views to be traversed earlier */
            return rowBounds.mBottom - t1.mBottom;
        }
    };

    private static class RowBounds {
        private final int mTop, mBottom;

        public RowBounds(int top, int bottom) {
            mTop = top;
            mBottom = bottom;
        }

        @Override
        public int hashCode() {
            /* Not the most general hash, but sufficient for reasonable screen sizes */
            return (mTop << 16) + mBottom;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RowBounds)) {
                return false;
            }
            return (((RowBounds) o).mTop == mTop) && (((RowBounds) o).mBottom == mBottom);
        }
    }

    /**
     * Build a tree with all clickable nodes in the tree anchored at root.
     * @param root The root of the tree of SwitchAccessNodeCompat
     * @param treeToBuildOn The tree that should be traversed if the user doesn't select
     * any options found under {@code root}. If this option is {@code null}, a
     * {@code ClearActionNode} terminates the tree.
     * @return A tree of OptionScanNode objects to be scanned. The tree terminates with a
     * ClearFocusNode. If no clickable nodes are found, a single ClearFocusNode is returned.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public OptionScanNode buildTreeFromNodeTree(SwitchAccessNodeCompat root,
            OptionScanNode treeToBuildOn) {
        OptionScanNode tree = (treeToBuildOn != null) ? treeToBuildOn : new ClearFocusNode();
        SortedMap<RowBounds, SortedMap<Integer, SwitchAccessNodeCompat>> nodesByXYCoordinate =
                getMapOfNodesByXYCoordinate(root);
        for (SortedMap<Integer, SwitchAccessNodeCompat> nodesInThisRow
                : nodesByXYCoordinate.values()) {
            if (nodesInThisRow.size() < MIN_NODES_PER_ROW) {
                for (SwitchAccessNodeCompat node : nodesInThisRow.values()) {
                    tree = TreeBuilderUtils.addCompatToTree(node, tree);
                    node.recycle();
                }
            } else {
                OptionScanNode rowTree = new ClearFocusNode();
                for (SwitchAccessNodeCompat node : nodesInThisRow.values()) {
                    rowTree = TreeBuilderUtils.addCompatToTree(node, rowTree);
                    node.recycle();
                }
                tree = new OptionScanSelectionNode(rowTree, tree);
            }
        }
        return tree;
    }

    private static SortedMap<RowBounds, SortedMap<Integer, SwitchAccessNodeCompat>>
            getMapOfNodesByXYCoordinate(SwitchAccessNodeCompat root) {
        SortedMap<RowBounds, SortedMap<Integer, SwitchAccessNodeCompat>> nodesByXYCoordinate =
                new TreeMap<>(ROW_BOUNDS_COMPARATOR);
        List<SwitchAccessNodeCompat> talkBackOrderList =
                TreeBuilderUtils.getNodesInTalkBackOrder(root);
        Rect boundsInScreen = new Rect();
        for (SwitchAccessNodeCompat node : talkBackOrderList) {
            /* Only add the node to list if it will be added to the tree */
            OptionScanNode treeWithCurrentNode = TreeBuilderUtils.addCompatToTree(node,
                    new ClearFocusNode());
            if (treeWithCurrentNode instanceof OptionScanSelectionNode) {
                node.getVisibleBoundsInScreen(boundsInScreen);
                /*
                 * Use negative value so traversal will start with the last elements, so the first
                 * ones end up at the top of the tree.
                 */
                RowBounds rowBounds = new RowBounds(boundsInScreen.top, boundsInScreen.bottom);
                SortedMap<Integer, SwitchAccessNodeCompat> mapOfNodes =
                        nodesByXYCoordinate.get(rowBounds);
                if (mapOfNodes == null) {
                    mapOfNodes = new TreeMap<>();
                    nodesByXYCoordinate.put(rowBounds, mapOfNodes);
                }
                mapOfNodes.put(-boundsInScreen.left, node);
            } else {
                node.recycle();
            }
            treeWithCurrentNode.recycle();
        }
        return nodesByXYCoordinate;
    }
}
