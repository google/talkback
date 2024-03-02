/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.utils.traversal;

import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Logger;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class OrderedTraversalController {

  private static final String TAG = "OrderedTraversalCont";

  private Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache;
  private @Nullable WorkingTree tree;
  private final Map<AccessibilityNodeInfoCompat, WorkingTree> nodeTreeMap;
  private @Nullable AccessibilityNodeInfoCompat initialFocusNode;

  public OrderedTraversalController() {
    nodeTreeMap = new LinkedHashMap<>();
  }

  public void setSpeakingNodesCache(Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache) {
    this.speakingNodesCache = speakingNodesCache;
  }

  /**
   * before start next traversal node search the controller must be initialized. The initialisation
   * step includes traversal through all accessibility nodes hierarchy to collect information about
   * traversal order of separate subtrees and moving subtries that has custom before/after traverse
   * view order
   *
   * @param compatRoot - accessibility node that serves as root node for tree hierarchy the
   *     controller works with
   * @param includeChildrenOfNodesWithWebActions whether to calculator order for nodes that support
   *     web actions. Although TalkBack uses the naviagation order specified by the nodes, Switch
   *     Access needs to know about all nodes at the time the tree is being created.
   */
  public void initOrder(
      @Nullable AccessibilityNodeInfoCompat compatRoot,
      boolean includeChildrenOfNodesWithWebActions) {
    if (compatRoot == null) {
      return;
    }

    NodeCachedBoundsCalculator boundsCalculator = new NodeCachedBoundsCalculator();
    boundsCalculator.setSpeakingNodesCache(speakingNodesCache);
    tree =
        createWorkingTree(compatRoot, null, boundsCalculator, includeChildrenOfNodesWithWebActions);
    reorderTree();
  }

  /**
   * Creates tree that reproduces AccessibilityNodeInfoCompat tree hierarchy
   *
   * @param rootNode root node that is starting point for tree reproduction
   * @param parent parent WorkingTree node for subtree that would be returned in this method
   * @param includeChildrenOfNodesWithWebActions whether to calculator order for nodes that support
   *     web actions. Although TalkBack uses the naviagation order specified by the nodes, Switch
   *     Access needs to know about all nodes at the time the tree is being created.
   * @return subtree that reproduces accessibility node hierarchy
   */
  private @Nullable WorkingTree createWorkingTree(
      @NonNull AccessibilityNodeInfoCompat rootNode,
      @Nullable WorkingTree parent,
      @NonNull NodeCachedBoundsCalculator boundsCalculator,
      boolean includeChildrenOfNodesWithWebActions) {
    if (nodeTreeMap.containsKey(rootNode)) {
      LogUtils.w(TAG, "creating node tree with looped nodes - break the loop edge");
      return null;
    }

    WorkingTree tree = new WorkingTree(rootNode, parent);
    nodeTreeMap.put(rootNode, tree);

    // When we reach a node that supports web navigation, we traverse using the web navigation
    // actions, so we should not try to determine the ordering of its descendants.
    if (!includeChildrenOfNodesWithWebActions && WebInterfaceUtils.supportsWebActions(rootNode)) {
      return tree;
    }

    ReorderedChildrenIterator iterator =
        ReorderedChildrenIterator.createAscendingIterator(rootNode, boundsCalculator);
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat child = iterator.next();
      WorkingTree childSubTree =
          createWorkingTree(child, tree, boundsCalculator, includeChildrenOfNodesWithWebActions);
      if (childSubTree != null) {
        tree.addChild(childSubTree);
      }
    }
    return tree;
  }

  /**
   * reorder previously created tree according to after/before view traversal order on separate
   * nodes
   */
  private void reorderTree() {
    for (WorkingTree subtree : nodeTreeMap.values()) {
      AccessibilityNodeInfoCompat node = subtree.getNode();
      if (AccessibilityNodeInfoUtils.hasRequestInitialAccessibilityFocus(node)) {
        // TODO: Add test case after Roboletric in Google3 supports API 34.
        initialFocusNode = node;
      }
      AccessibilityNodeInfoCompat beforeNode = node.getTraversalBefore();
      if (beforeNode != null) {
        WorkingTree targetTree = nodeTreeMap.get(beforeNode);
        moveNodeBefore(subtree, targetTree);
      } else {
        AccessibilityNodeInfoCompat afterNode = node.getTraversalAfter();
        if (afterNode != null) {
          WorkingTree targetTree = nodeTreeMap.get(afterNode);
          moveNodeAfter(subtree, targetTree);
        }
      }
    }
  }

  /** Moves movingTree before targetTree. */
  private void moveNodeBefore(@Nullable WorkingTree movingTree, @Nullable WorkingTree targetTree) {
    if (movingTree == null || targetTree == null) {
      return;
    }

    if (movingTree.hasDescendant(targetTree)) {
      // no operation if move child before parent
      return;
    }

    // Find subtree to move.
    WorkingTree movingTreeRoot = getParentsThatAreMovedBeforeOrSameNode(movingTree);

    // Find destination for movingTreeRoot.
    WorkingTree parent = targetTree.getParent();
    if (movingTreeRoot.hasDescendant(parent)) {
      return; // Moving movingTreeRoot under its own descendant would create a loop.
    }

    // Unlink moving subtree from tree.
    detachSubtreeFromItsParent(movingTreeRoot);

    // swap target node with moving node on targets node parent children list
    if (parent != null) {
      parent.swapChild(targetTree, movingTreeRoot);
    }

    movingTreeRoot.setParent(parent);

    // add target node as last child of moving node
    movingTree.addChild(targetTree);
    targetTree.setParent(movingTree);
  }

  /**
   * This method is called before moving subtree. It checks if parent of that node was moved on its
   * place because it has before property to that node. In that case parent node should be moved
   * with movingTree node.
   *
   * @return top node that should be moved with movingTree node.
   */
  private WorkingTree getParentsThatAreMovedBeforeOrSameNode(WorkingTree movingTree) {
    WorkingTree parent = movingTree.getParent();
    if (parent == null) {
      return movingTree;
    }

    AccessibilityNodeInfoCompat parentNode = parent.getNode();
    AccessibilityNodeInfoCompat parentNodeBefore = parentNode.getTraversalBefore();
    if (parentNodeBefore == null) {
      return movingTree;
    }

    if (parentNodeBefore.equals(movingTree.getNode())) {
      return getParentsThatAreMovedBeforeOrSameNode(parent);
    }

    return movingTree;
  }

  private void detachSubtreeFromItsParent(WorkingTree subtree) {
    WorkingTree movingTreeParent = subtree.getParent();
    if (movingTreeParent != null) {
      movingTreeParent.removeChild(subtree);
    }
    subtree.setParent(null);
  }

  private void moveNodeAfter(@Nullable WorkingTree movingTree, @Nullable WorkingTree targetTree) {
    if (movingTree == null || targetTree == null) {
      return;
    }

    if (movingTree.hasDescendant(targetTree)) {
      return; // Moving movingTree under its own descendant would create a loop.
    }
    movingTree = getParentsThatAreMovedBeforeOrSameNode(movingTree);
    if (movingTree.hasDescendant(targetTree)) {
      return; // Moving movingTree under its own descendant would create a loop.
    }
    detachSubtreeFromItsParent(movingTree);
    targetTree.addChild(movingTree);
    movingTree.setParent(targetTree);
  }

  public @Nullable AccessibilityNodeInfoCompat findNext(AccessibilityNodeInfoCompat node) {
    WorkingTree tree = nodeTreeMap.get(node);
    if (tree == null) {
      LogUtils.w(TAG, "findNext(), can't find WorkingTree for AccessibilityNodeInfo");
      return null;
    }

    WorkingTree nextTree = tree.getNext();
    if (nextTree != null) {
      return nextTree.getNode();
    }

    return null;
  }

  public @Nullable AccessibilityNodeInfoCompat findPrevious(AccessibilityNodeInfoCompat node) {
    WorkingTree tree = nodeTreeMap.get(node);
    if (tree == null) {
      LogUtils.w(TAG, "findPrevious(), can't find WorkingTree for AccessibilityNodeInfo");
      return null;
    }

    WorkingTree prevTree = tree.getPrevious();
    if (prevTree != null) {
      return prevTree.getNode();
    }

    return null;
  }

  /** Searches first node to be focused */
  public @Nullable AccessibilityNodeInfoCompat findFirst() {
    if (tree == null) {
      return null;
    }

    return tree.getRoot().getNode();
  }

  public @Nullable AccessibilityNodeInfoCompat findFirst(AccessibilityNodeInfoCompat rootNode) {
    if (rootNode == null) {
      return null;
    }

    WorkingTree tree = nodeTreeMap.get(rootNode);
    if (tree == null) {
      return null;
    }

    return tree.getNode();
  }

  public @Nullable AccessibilityNodeInfoCompat findInitial(AccessibilityNodeInfoCompat rootNode) {
    if (rootNode == null) {
      return null;
    }

    WorkingTree tree = nodeTreeMap.get(rootNode);
    if (tree == null) {
      return null;
    }

    if (tree.hasDescendant(nodeTreeMap.get(initialFocusNode))) {
      return initialFocusNode;
    }

    return null;
  }

  /** Searches last node to be focused */
  public @Nullable AccessibilityNodeInfoCompat findLast() {
    if (tree == null) {
      return null;
    }

    return tree.getRoot().getLastNode().getNode();
  }

  public @Nullable AccessibilityNodeInfoCompat findLast(AccessibilityNodeInfoCompat rootNode) {
    if (rootNode == null) {
      return null;
    }

    WorkingTree tree = nodeTreeMap.get(rootNode);
    if (tree == null) {
      return null;
    }

    return tree.getLastNode().getNode();
  }

  /** Dumps the traversal order tree. */
  protected void dumpTree(@NonNull Logger treeDebugLogger) {
    AccessibilityNodeInfoCompat node = findFirst();
    while (node != null) {
      treeDebugLogger.log(
          " (%d)%s%s",
          node.hashCode(),
          TreeDebug.nodeDebugDescription(node),
          getCustomizedTraversalNodeString(node));
      node = findNext(node);
    }
  }

  /**
   * Returns the string contains the attribute {@link AccessibilityNodeInfo#getTraversalBefore()}
   * and {@link AccessibilityNodeInfo#getTraversalAfter()} of the target node.
   */
  private static String getCustomizedTraversalNodeString(AccessibilityNodeInfoCompat node) {
    StringBuilder builder = new StringBuilder();
    AccessibilityNodeInfoCompat beforeNode = node.getTraversalBefore();
    AccessibilityNodeInfoCompat afterNode = node.getTraversalAfter();
    if (beforeNode != null) {
      builder.append(" before:");
      builder.append(beforeNode.hashCode());
    }
    if (afterNode != null) {
      builder.append(" after:");
      builder.append(afterNode.hashCode());
    }
    return builder.toString();
  }
}
