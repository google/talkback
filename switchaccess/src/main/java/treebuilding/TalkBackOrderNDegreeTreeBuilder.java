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
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.treenodes.ClearFocusNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSystemProvidedNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds an n-ary tree of TreeScanNodes which enables the user to traverse the options using n
 * switches. The views in a window are grouped into n-clusters, with each cluster being associated
 * with a particular switch
 */
public class TalkBackOrderNDegreeTreeBuilder extends TreeBuilder {
  private static final int MIN_DEGREE = 2;

  public TalkBackOrderNDegreeTreeBuilder(AccessibilityService service) {
    super(service);
  }

  @Override
  public TreeScanNode addViewHierarchyToTree(
      SwitchAccessNodeCompat node, TreeScanNode treeToBuildOn, boolean includeNonActionableItems) {
    /* Not currently used */
    return treeToBuildOn;
  }

  public TreeScanNode addWindowListToTree(
      List<SwitchAccessWindowInfo> windowList,
      TreeScanNode treeToBuildOn,
      boolean includeNonActionableItems) {
    treeToBuildOn = (treeToBuildOn == null) ? new ClearFocusNode() : treeToBuildOn;
    if (windowList == null || windowList.isEmpty()) {
      return treeToBuildOn;
    }
    List<TreeScanNode> treeNodes = new ArrayList<>();
    for (SwitchAccessWindowInfo window : windowList) {
      SwitchAccessNodeCompat windowRoot = window.getRoot();
      if (windowRoot != null) {
        treeNodes.addAll(getNodeListFromNodeTree(windowRoot, includeNonActionableItems));
        windowRoot.recycle();
      }
    }
    return buildTreeFromNodeList(treeNodes, treeToBuildOn);
  }

  public int getDegree() {
    return Math.max(SwitchAccessPreferenceUtils.getNumSwitches(service), MIN_DEGREE);
  }

  /**
   * Given the root of the tree of SwitchAccessNodeCompat, constructs a list of actions associated
   * with each compat node in this tree. If a particular node has more than one action, a n-ary tree
   * representing a context menu with all the available actions is added to the list instead.
   *
   * @param root The root of the tree of SwitchAccessNodeCompat
   * @param shouldScanNonActionableItems Whether non-actionable items should be included when
   *     constructing the list
   * @return A list of TreeScanNodes
   */
  private List<TreeScanNode> getNodeListFromNodeTree(
      SwitchAccessNodeCompat root, boolean shouldScanNonActionableItems) {
    List<TreeScanNode> treeNodes = new ArrayList<>();
    List<SwitchAccessNodeCompat> talkBackOrderList = getNodesInTalkBackOrder(root);
    for (SwitchAccessNodeCompat node : talkBackOrderList) {
      TreeScanSystemProvidedNode treeScanSystemProvidedNode =
          createNodeIfImportant(node, shouldScanNonActionableItems);

      if (treeScanSystemProvidedNode != null) {
        treeNodes.add(treeScanSystemProvidedNode);
      }
      node.recycle();
    }
    return treeNodes;
  }

  /**
   * Builds an n-ary tree from a list of nodes to be included in the tree.
   *
   * @param nodeList The list of nodes to be included in the tree.
   * @param lastTreeScanNode The node that can be presented as the last option regardless of the
   *     path followed in the constructed tree. For example when a context menu tree is constructed,
   *     the ClearFocusNode should be a leaf node at the end of any possible path followed in the
   *     constructed tree.
   * @return An n-ary tree containing all the nodes included in the list.
   */
  private TreeScanNode buildTreeFromNodeList(
      List<TreeScanNode> nodeList, TreeScanNode lastTreeScanNode) {
    int degree = getDegree();
    if (nodeList.size() == degree) {
      // The {@code nodeList} contains degree action nodes, however the
      // {@code lastTreeScanNode} would increase the degree of the node to degree + 1. Hence
      // the first degree-1 options are kept as children of the parent node while a new node
      // is created to hold the last option and the {@code lastTreeScanNode}. This node is
      // then kept as the last child of the parent node returned.
      nodeList.add(lastTreeScanNode);
      List<TreeScanNode> children = nodeList.subList(0, nodeList.size() - 2);
      TreeScanNode lastChild = createTree(nodeList.subList(nodeList.size() - 2, nodeList.size()));
      children.add(lastChild);
      return createTree(children);
    } else if (nodeList.size() < degree) {
      // Regardless of the path the user chooses, the last option presented to the user
      // will be the contextMenu. The last scan node of a context menu itself is a
      // ClearFocusNode.
      nodeList.add(lastTreeScanNode);
      return createTree(nodeList);
    } else {
      List<TreeScanNode> subtrees = new ArrayList<>();
      // The number of elements that each subtree will contain
      int elemNum = nodeList.size() / degree;
      // If the number of elements was not divisible by the degree specified, the remaining
      // elements, which will be less than the number of subtrees, will be distributed among
      // the first k-subtrees. Hence some subtrees will have at most one more element than
      // other subtrees.
      int elemRemainder = nodeList.size() % degree;
      int startIndex = 0;
      int endIndex = 0;
      List<TreeScanNode> subtreeNodes;
      while (startIndex < nodeList.size()) {
        endIndex = (elemRemainder > 0) ? endIndex + elemNum + 1 : endIndex + elemNum;
        elemRemainder--;
        subtreeNodes = new ArrayList<>(nodeList.subList(startIndex, endIndex));
        if ((subtreeNodes.size() == 1) && (endIndex < nodeList.size())) {
          // Selecting a single option that isn't last in the list is unambiguous
          subtrees.add(subtreeNodes.get(0));
        } else {
          subtrees.add(buildTreeFromNodeList(subtreeNodes, lastTreeScanNode));
        }
        startIndex = endIndex;
      }
      /* make the subtrees children of the same parent node */
      return createTree(subtrees);
    }
  }

  /**
   * Given a list of nodes, unites the nodes under a common parent, by making them children of a
   * common TreeScanNode
   *
   * @param treeNodes The list of nodes to be included as children of a common parent
   * @return A parent node whose children are all the nodes in the {@code treeNodes} list.
   */
  private TreeScanNode createTree(List<TreeScanNode> treeNodes) {
    if (treeNodes.size() == 1) {
      return treeNodes.get(0);
    } else {
      List<TreeScanNode> otherChildren = treeNodes.subList(2, treeNodes.size());
      return new TreeScanSelectionNode(
          treeNodes.get(0),
          treeNodes.get(1),
          otherChildren.toArray(new TreeScanNode[otherChildren.size()]));
    }
  }
}
