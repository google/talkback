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

import android.content.Context;
import android.support.annotation.NonNull;
import com.google.android.accessibility.switchaccess.ClearFocusNode;
import com.google.android.accessibility.switchaccess.ProbabilityModelReader;
import com.google.android.accessibility.switchaccess.ShowActionsMenuNode;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.TreeScanNode;
import com.google.android.accessibility.switchaccess.TreeScanSelectionNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Builds a Huffman tree based on the probabilities of the views in a window. TODO This builder
 * needs to be made available to users. It is currently dead code.
 */
public class HuffmanTreeBuilder extends TreeBuilder {

  /* TODO This default probability value is temporary. It's currently set to be the
   * lowest value. Not sure if the option that is not part of the model should always be the
   * lowest likely option. I feel that it would be nice if each of the nodes knew what its
   * probability was and all the TreeScanNodes had a default value. */
  private static final Double DEFAULT_PROBABILITY = 0.0001;

  ProbabilityModelReader mProbabilityModelReader;
  int mDegree;

  public HuffmanTreeBuilder(
      Context context, int degree, ProbabilityModelReader probabilityModelReader) {
    super(context);
    if (degree < 2) {
      throw new IllegalArgumentException("The tree degree must be greater than one");
    }
    mDegree = degree;
    mProbabilityModelReader = probabilityModelReader;
  }

  /**
   * Builds a Huffman tree with all the clickable nodes in the tree anchored at
   * {@code windowRoot}. The context provides information about actions the user has taken so far
   * and allows the probabilities for the views in a window to be adjusted based on that.
   *
   * @param windowRoot The root of the tree of SwitchAccessNodeCompat
   * @param treeToBuildOn A tree of TreeScanNodes that should be included as part of the
   *        Huffman tree.
   * @param context The actions the user has taken so far. In case of an IME, this would be what
   *        the user has typed so far.
   * @return A Huffman tree of TreeScanNodes including the tree {@code treeToBuildOn} and all
   *        clickable nodes from the {@code windowRoot} tree. If there are no clickable nodes in
   *        {@code windowRoot and the treeToBuildOn is {@code null}, a {@code ClearFocusNode} is
   *        returned.
   */
  /* TODO It will probably not be possible to capture context using a string only.
   * Once we understand how to capture context better we need to change this. */
  public TreeScanNode buildTreeFromNodeTree(
      SwitchAccessNodeCompat windowRoot, TreeScanNode treeToBuildOn, String context) {
    PriorityQueue<HuffmanNode> scanNodeProbabilities =
        getTreeScanNodeProbabilities(context, windowRoot);
    ClearFocusNode clearFocusNode = new ClearFocusNode();
    if (treeToBuildOn != null) {
      scanNodeProbabilities.add(new HuffmanNode(treeToBuildOn, DEFAULT_PROBABILITY));
    } else if (scanNodeProbabilities.isEmpty()) {
      return clearFocusNode;
    }
    scanNodeProbabilities.add(
        createParentNode(
            scanNodeProbabilities,
            getNodesPerParent(scanNodeProbabilities.size()),
            clearFocusNode));
    while (scanNodeProbabilities.size() > 1) {
      scanNodeProbabilities.add(createParentNode(scanNodeProbabilities, mDegree, clearFocusNode));
    }
    return scanNodeProbabilities.peek().getTreeScanNode();
  }

  /**
   * It adds the ClearFocusNode to the list. If the list contains mDegree children, it creates a new
   * branch adding the last child and a ClearFocusNode. This way the number of children remains the
   * same. If on the other hand, the list contains less than mDegree children, it simply adds the
   * ClearFocusNode as another child.
   *
   * @param branchNodes The nodes to be included as children of a common parent node.
   * @param clearFocusNode The ClearFocusNode that is included if the resulting branch would not
   *     contain a ClearFocusNode
   */
  private void addClearFocusNodeToBranch(
      List<TreeScanNode> branchNodes, ClearFocusNode clearFocusNode) {
    if (branchNodes.size() < mDegree) {
      branchNodes.add(clearFocusNode);
    } else {
      TreeScanNode nodeWithClearFocus = branchNodes.get(branchNodes.size() - 1);
      branchNodes.remove(nodeWithClearFocus);
      nodeWithClearFocus = new TreeScanSelectionNode(nodeWithClearFocus, clearFocusNode);
      branchNodes.add(nodeWithClearFocus);
    }
  }

  /**
   * Given a priority queue of HuffmanNodes and the number of nodes per parent, a new parent
   * HuffmanNode is constructed. The probability of the parent HuffmanNode is the sum of the
   * probabilities of all its children. If none of the children branches have a {@code
   * ClearFocusNode}, one is included when the parent node is created.
   *
   * @param nodes The total nodes that will be included in the Huffman tree.
   * @param nodesPerParent The number of children the parent node will have.
   * @param clearFocusNode The clear focus node to be included if none of the children branches have
   *     a {@code ClearFocusNode} included.
   * @return The parent HuffmanNode created.
   */
  private HuffmanNode createParentNode(
      PriorityQueue<HuffmanNode> nodes, int nodesPerParent, ClearFocusNode clearFocusNode) {
    if (nodesPerParent < 2 || nodes.size() < nodesPerParent) {
      throw new IllegalArgumentException();
    }
    Double childrenProbability = 0.0;
    List<TreeScanNode> children = new ArrayList<>(nodesPerParent);
    Boolean clearFocusNodePresence = false;
    for (int i = 0; i < nodesPerParent; i++) {
      HuffmanNode huffmanNode = nodes.poll();
      childrenProbability += huffmanNode.getProbability();
      children.add(huffmanNode.getTreeScanNode());
      if ((i == nodesPerParent - 1) && huffmanNode.hasClearFocusNode()) {
        clearFocusNodePresence = true;
      }
    }
    if (!clearFocusNodePresence) {
      addClearFocusNodeToBranch(children, clearFocusNode);
    }
    List<TreeScanNode> otherChildren = children.subList(2, children.size());
    TreeScanNode parent =
        new TreeScanSelectionNode(
            children.get(0),
            children.get(1),
            otherChildren.toArray(new TreeScanNode[otherChildren.size()]));
    HuffmanNode parentHuffmanNode = new HuffmanNode(parent, childrenProbability);
    parentHuffmanNode.setClearFocusNodePresence();
    return parentHuffmanNode;
  }

  /**
   * When constructing a Huffman tree of degree greater than 2, not all sets of source nodes can
   * properly form an n-ary tree. If the number of source nodes is congruent to 1 modulo degree-1,
   * then the set of source nodes will form a proper Huffman tree. For example, if we constructed a
   * tree of degree 4, then the set of source nodes to 1 % 3 (i.e {1, 4, 7, 10 , ...}) would form a
   * proper Huffman tree.
   *
   * <p>However if this is not the case, to form a proper Huffman tree, the very first time a
   * Huffman Node is constructed instead of picking degree nodes, we pick 2 <= degree' <= degree. If
   * we let A = totalNodes mod (degree - 1), then degree' is congruent to A mod (degree -1). This
   * function simply implements this logic and computes degree'.
   *
   * @param totalNodes The total nodes that will be included in the Huffman tree.
   * @return The number of children that the Huffman node will contain.
   */
  private int getNodesPerParent(int totalNodes) {
    if (totalNodes <= mDegree) {
      return totalNodes;
    }
    int nodesPerParent = totalNodes % (mDegree - 1);
    while (nodesPerParent < 2) {
      nodesPerParent += (mDegree - 1);
    }
    return nodesPerParent;
  }

  /**
   * Creates a HuffmanNode for each of the nodes in the {@code windowRoot}. The HuffmanNode
   * internally keeps track of the probability for each of these nodes. Finally, all the
   * HuffmanNodes are added to a priority queue to keep them sorted on an ascending order based on
   * their probabilities.
   *
   * @param userContext The actions the user has taken so far. In case of an IME, this would be what
   *     the user has typed so far.
   * @param windowRoot The root of the tree of SwitchAccessNodeCompats
   * @return Returns a TreeSet which contains all the HuffmanNodes in ascending order based on their
   *     probabilities. If the {@code windowRoot} contains no clickable nodes, an empty TreeSet is
   *     returned.
   */
  private PriorityQueue<HuffmanNode> getTreeScanNodeProbabilities(
      String userContext, SwitchAccessNodeCompat windowRoot) {
    LinkedList<SwitchAccessNodeCompat> talkBackOrderList = getNodesInTalkBackOrder(windowRoot);
    Set<SwitchAccessNodeCompat> talkBackOrderSet = new HashSet<>(talkBackOrderList);
    Map<SwitchAccessNodeCompat, Double> probabilityDistribution =
        mProbabilityModelReader.getProbabilityDistribution(userContext, talkBackOrderSet);
    PriorityQueue<HuffmanNode> scanNodeProbabilities = new PriorityQueue<>();
    for (SwitchAccessNodeCompat currentNode : talkBackOrderSet) {
      Double currentNodeProbability = probabilityDistribution.get(currentNode);
      ShowActionsMenuNode actionNode =
          ShowActionsMenuNode.createNodeIfHasActions(mContext, currentNode);
      /* TODO: need to think about the correct behaviour when there are more
       * than one actions associated with a node */
      if (actionNode != null) {
        scanNodeProbabilities.add(new HuffmanNode(actionNode, currentNodeProbability));
      }
      currentNode.recycle();
    }
    return scanNodeProbabilities;
  }

  @Override
  public TreeScanNode addViewHierarchyToTree(
      SwitchAccessNodeCompat node,
      TreeScanNode treeToBuildOn,
      boolean shouldScanNonActionableItems) {
    return null;
  }

  @Override
  public TreeScanNode addWindowListToTree(
      List<SwitchAccessWindowInfo> windowList,
      TreeScanNode treeToBuildOn,
      boolean shouldPlaceTreeFirst,
      boolean includeNonActionableItems) {
    return null;
  }

  /**
   * A HuffmanNode is a wrapper class that associates an TreeScanNode with a certain probability. It
   * implements the Comparable interface so that the natural ordering of HuffmanNodes is based on
   * their probabilities (ascending order).
   */
  private static class HuffmanNode implements Comparable<HuffmanNode> {
    private final TreeScanNode mTreeScanNode;
    private final Double mProbability;
    private Boolean mHasClearFocusNode = false;

    public HuffmanNode(TreeScanNode scanNode, Double probability) {
      mTreeScanNode = scanNode;
      mProbability = probability;
    }

    public TreeScanNode getTreeScanNode() {
      return mTreeScanNode;
    }

    public Double getProbability() {
      return mProbability;
    }

    public Boolean hasClearFocusNode() {
      return mHasClearFocusNode;
    }

    public void setClearFocusNodePresence() {
      mHasClearFocusNode = true;
    }

    @Override
    public int compareTo(@NonNull HuffmanNode node) {
      return this.mProbability.compareTo(node.mProbability);
    }
  }
}
