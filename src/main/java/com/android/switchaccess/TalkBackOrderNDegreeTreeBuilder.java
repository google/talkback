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
import android.content.Context;
import android.os.Build;

import com.android.talkback.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Build an n-ary tree of OptionScanNodes which enables the user to traverse the options using
 * n switches. The views in a window are grouped into n-clusters, with each cluster being
 * associated with a particular switch */
public class TalkBackOrderNDegreeTreeBuilder {
    /* TODO(pweaver) should extend TreeBuilder. */
    /**
     * These are the IDs, in order, of the key assignment preferences for option scanning
     * TODO(pweaver) When we add more option scanning builders, this list needs a proper home
     */
    static public final int OPTION_SCAN_SWITCH_CONFIG_IDS[] = {
            R.string.pref_key_mapped_to_click_key, R.string.pref_key_mapped_to_next_key,
            R.string.pref_key_mapped_to_switch_3_key, R.string.pref_key_mapped_to_switch_4_key,
            R.string.pref_key_mapped_to_switch_5_key};

    static private final int CONTEXT_MENU_NODE = 0;
    static private final int OPTION_SCAN_SELECTION_NODE = 1;

    private int mDegree;

    public TalkBackOrderNDegreeTreeBuilder(Context context) {
        reloadPreferences(context);
    }

    /**
     * Build an n-ary tree from a window list. Construct a list of all the nodes as an intermediate
     * state and then construct the n-ary tree from this list.
     *
     * @param windowList The list of windows.
     * @param contextMenuTree The tree which represents the context menu presented as the last
     *         option to the user.
     * @return An n-ary tree including nodes from all windows in the list if the window list is
     *         not null, otherwise the contextMenuTree which was passed in as an argument is
     *         returned. If the contextMenuTree itself is also null, a {@code ClearFocusNode} is
     *         returned.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public OptionScanNode buildTreeFromWindowList(List<SwitchAccessWindowInfo> windowList,
            OptionScanNode contextMenuTree) {
        contextMenuTree = (contextMenuTree == null) ? new ClearFocusNode(): contextMenuTree;
        if (windowList == null || windowList.size() == 0) {
            return contextMenuTree;
        }
        List<OptionScanNode> treeNodes = new ArrayList<>();
        for (SwitchAccessWindowInfo window : windowList) {
            SwitchAccessNodeCompat windowRoot = window.getRoot();
            if (windowRoot != null) {
                treeNodes.addAll(getNodeListFromNodeTree(windowRoot));
                windowRoot.recycle();
            }
        }
        return buildTreeFromNodeList(treeNodes, OPTION_SCAN_SELECTION_NODE, contextMenuTree);
    }

    public void reloadPreferences(Context context) {
        /*
         * Update degree to match the number of option scan switches configured. They must be
         * configured consecutively to count.
         */
        int numSwitchesConfigured = 0;
        while ((OPTION_SCAN_SWITCH_CONFIG_IDS.length > numSwitchesConfigured)
                && KeyComboPreference.getKeyCodesForPreference(
                context, OPTION_SCAN_SWITCH_CONFIG_IDS[numSwitchesConfigured]).size() > 0) {
            numSwitchesConfigured++;
        }
        /* We can't build a tree with degree less than 2. */
        mDegree = Math.max(numSwitchesConfigured, 2);
    }

    /**
     * Given the root of the tree of SwitchAccessNodeCompat, constructs a list of actions
     * associated with each compat node in this tree. If a particular node has more than one
     * action, a n-ary tree representing a context menu with all the available actions is added to
     * the list instead.
     *
     * @param root The root of the tree of SwitchAccessNodeCompat
     * @return A list of OptionScanNodes, which represent either AccessibilityNodeActionNodes or
     *         ContextMenuNodes (for nodes with multiple actions).
     */
    private List<OptionScanNode> getNodeListFromNodeTree(SwitchAccessNodeCompat root) {
        List<OptionScanNode> treeNodes = new ArrayList<>();
        List<SwitchAccessNodeCompat> talkBackOrderList =
                TreeBuilderUtils.getNodesInTalkBackOrder(root);
        for (SwitchAccessNodeCompat node : talkBackOrderList) {
            List<AccessibilityNodeActionNode> actionNodes =
                    TreeBuilderUtils.getCompatActionNodes(node);
            node.recycle();
            if (actionNodes.size() == 1) {
                treeNodes.add(actionNodes.get(0));
            } else if (actionNodes.size() > 1) {
                treeNodes.add(buildContextMenuTree(actionNodes));
            }
        }
        return treeNodes;
    }

    /**
     * Builds an n-ary tree from a list of nodes to be included in the tree.
     *
     * @param nodeList The list of nodes to be included in the tree
     * @param treeNodeType The type of nodes in the constructed tree.
     * @param lastScanNode The node that can be presented as the last option regardless of the path
     *        followed in the constructed tree. For example when a context menu tree is constructed,
     *        the ClearFocusNode should be a leaf node at the end of any possible path followed in
     *        the constructed tree.
     * @return An n-ary tree containing all the nodes included in the list.
     */
    private OptionScanNode buildTreeFromNodeList(List<OptionScanNode> nodeList, int treeNodeType,
             OptionScanNode lastScanNode) {
        if (nodeList.size() == mDegree) {
            /* the {@code nodeList} contains degree action nodes, however the {@code lastScanNode}
             * would increase the degree of the node to degree + 1. Hence the first degree-1
             * options are kept as children of the parent node while a new node is created to hold
             * the last option and the {@code lastScanNode}. This node is then kept as the last
             * child of the parent node returned */
            nodeList.add(lastScanNode);
            List<OptionScanNode> children = nodeList.subList(0, nodeList.size() - 2);
            OptionScanNode lastChild = createTree(nodeList.subList(nodeList.size() - 2,
                    nodeList.size()), treeNodeType);
            children.add(lastChild);
            return createTree(children, treeNodeType);
        } else if (nodeList.size() < mDegree) {
            /* regardless of the path the user chooses, the last option presented to the user
             * will be the contextMenu. The last scan node of a context menu itself is a
             * ClearFocusNode */
            nodeList.add(lastScanNode);
            return createTree(nodeList, treeNodeType);
        } else {
            List<OptionScanNode> subtrees = new ArrayList<>();
            /* The number of elements that each subtree will contain */
            int elemNum = nodeList.size() / mDegree;
            /* If the number of elements was not divisible by the degree specified, the remaining
             * elements, which will be less than the number of subtrees, will be distributed among
             * the first k-subtrees. Hence some subtrees will have at most one more element than
             * other subtrees. */
            int elemRemainder = nodeList.size() % mDegree;
            int startIndex = 0, endIndex = 0;
            List<OptionScanNode> subtreeNodes;
            while (startIndex < nodeList.size()) {
                endIndex = (elemRemainder > 0) ? endIndex + elemNum + 1 : endIndex + elemNum;
                elemRemainder--;
                subtreeNodes = new ArrayList<>(nodeList.subList(startIndex, endIndex));
                if ((subtreeNodes.size() == 1) && (endIndex < nodeList.size())) {
                    // Selecting a single option that isn't last in the list is unambiguous
                    subtrees.add(subtreeNodes.get(0));
                } else {
                    subtrees.add(buildTreeFromNodeList(subtreeNodes, treeNodeType, lastScanNode));
                }
                startIndex = endIndex;
            }
            /* make the subtrees children of the same parent node */
            return createTree(subtrees, treeNodeType);
        }
    }

    /**
     * Given a list of nodes, unites the nodes under a common parent, by making them children of a
     * common OptionScanNode
     *
     * @param treeNodes The list of nodes to be included as children of a common parent
     * @param nodeType The type of the parent node to be created.
     * @return A parent node whose children are all the nodes in the {@code treeNodes} list.
     */
    private OptionScanNode createTree(List<OptionScanNode> treeNodes, int nodeType) {
        if (treeNodes == null || treeNodes.isEmpty()) {
            return null;
        } else if (treeNodes.size() == 1) {
            return treeNodes.get(0);
        } else {
            List<OptionScanNode> otherChildren = treeNodes.subList(2, treeNodes.size());
            if (nodeType == CONTEXT_MENU_NODE) {
                return new ContextMenuNode((ContextMenuItem) treeNodes.get(0),
                        (ContextMenuItem) treeNodes.get(1), otherChildren.toArray(
                        new ContextMenuItem[otherChildren.size()]));
            } else if (nodeType == OPTION_SCAN_SELECTION_NODE) {
                return new OptionScanSelectionNode(treeNodes.get(0), treeNodes.get(1),
                        otherChildren.toArray(new OptionScanNode[otherChildren.size()]));
            }
            return null;
        }
    }

    /**
     * Given a list of actions, builds a n-ary tree representing the context menu
     *
     * @param actions The actions to choose from, in the order they should be scanned.
     * @return A tree containing context menu nodes. If no actions are provided a
     *         {@code ClearFocusNode} is returned.
     */
    public OptionScanNode buildContextMenuTree(
            List<? extends OptionScanActionNode> actions) {
        if (actions.size() == 0) {
            return new ClearFocusNode();
        }
        return buildTreeFromNodeList(new ArrayList<OptionScanNode>(actions), CONTEXT_MENU_NODE,
                new ClearFocusNode());
    }
}
