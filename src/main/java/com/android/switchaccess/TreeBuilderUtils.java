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

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.NodeFilter;
import com.android.utils.traversal.OrderedTraversalController;

import java.io.PrintStream;
import java.util.*;

/**
 * Useful methods common to more than one tree builder
 */
public class TreeBuilderUtils {
    /* TODO(PW) Support all actions, perhaps conditioned on user preferences */
    private static final Set<Integer> SUPPORTED_ACTIONS = new HashSet<>(Arrays.asList(
            AccessibilityNodeInfoCompat.ACTION_CLICK,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD));

    /**
     * Add the specified node to the tree. If the node has more than one action, a linearly-
     * scanned context menu is added to select from them.
     *
     * @param compat The node whose actions should be added
     * @param tree The tree to add them to
     * @return The new tree with the added actions for the specified node. If no actions are
     * associated with the node, the original tree is returned.
     */
    public static OptionScanNode addCompatToTree(final SwitchAccessNodeCompat compat,
            OptionScanNode tree) {
        List<AccessibilityNodeActionNode> actionNodes = getCompatActionNodes(compat);
        if (actionNodes.size() == 1) {
            tree = new OptionScanSelectionNode(actionNodes.get(0), tree);
        } else if (actionNodes.size() > 1) {
            tree = new OptionScanSelectionNode(
                    LinearScanTreeBuilder.buildContextMenuTree(actionNodes), tree);
        }
        return tree;
    }

    /**
     * Get the actions associated with the given compat node.
     *
     * @param compat The node whose actions should be obtained.
     * @return A list of {@code AccessibilityNodeActionNodes}, representing all the actions
     * associated with the specified node. If no actions are associated with the node, an empty
     * list is returned.
     */
    public static List<AccessibilityNodeActionNode> getCompatActionNodes(
            final SwitchAccessNodeCompat compat) {
        if(!compat.isVisibleToUser()) {
            return new ArrayList<>(0);
        }
        List<AccessibilityNodeActionNode> actionNodes = new ArrayList<>();
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions =
                compat.getActionList();
        for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action : actions) {
            if (SUPPORTED_ACTIONS.contains(action.getId())) {
                actionNodes.add(new AccessibilityNodeActionNode(compat, action));
            }
        }
        return actionNodes;
    }

    /**
     * Print a tree to a specified stream. This method is intended for debugging.
     *
     * @param tree The tree to print
     * @param printStream The stream to which to print
     * @param prefix Any prefix that should be prepended to each line.
     */
    @SuppressWarnings("unused")
    public static void printTree(OptionScanNode tree, PrintStream printStream, String prefix) {
        String treeClassName = tree.getClass().getSimpleName();
        if (tree instanceof AccessibilityNodeActionNode) {
            Iterator<Rect> rects = tree.getRectsForNodeHighlight().iterator();
            if (rects.hasNext()) {
                Rect rect = rects.next();
                printStream.println(prefix + treeClassName + " with rect: " + rect.toString());
            }
            return;
        }
        printStream.println(prefix + treeClassName);
        if (tree instanceof OptionScanSelectionNode) {
            OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) tree;
            for (int i = 0; i < selectionNode.getChildCount(); ++i) {
                printTree(selectionNode.getChild(i), printStream, prefix + "-");
            }
        }
    }

    /**
     * Obtain a list of nodes in the order TalkBack would traverse them
     *
     * @param root The root of the tree to traverse
     * @return The nodes in {@code root}'s subtree (including root) in the order TalkBack would
     * traverse them.
     */
    public static LinkedList<SwitchAccessNodeCompat> getNodesInTalkBackOrder(
            SwitchAccessNodeCompat root) {
        LinkedList<SwitchAccessNodeCompat> outList = new LinkedList<>();
        OrderedTraversalController traversalController = new OrderedTraversalController();
        traversalController.initOrder(root);
        AccessibilityNodeInfoCompat node = traversalController.findFirst();
        while (node != null) {
            outList.add(new SwitchAccessNodeCompat(node.getInfo(), root.getWindowsAbove()));
            node = traversalController.findNext(node);
        }
        traversalController.recycle();
        return outList;
    }

    private static boolean nodeHasSupportedAction(SwitchAccessNodeCompat node) {
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions = node.getActionList();
        for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action : actions) {
            if (SUPPORTED_ACTIONS.contains(action.getId())) {
                return true;
            }
        }
        return false;
    }

}
