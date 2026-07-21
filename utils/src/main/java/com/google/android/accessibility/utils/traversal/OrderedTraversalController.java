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

import android.content.res.Resources;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class OrderedTraversalController {

  private static final String TAG = "OrderedTraversalCont";

  private Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache;
  private @Nullable WorkingTree tree;
  private final Map<AccessibilityNodeInfoCompat, WorkingTree> nodeTreeMap;
  private @Nullable AccessibilityNodeInfoCompat initialFocusNode;
  private final boolean makeFabFirst;

  public OrderedTraversalController() {
    this(false);
  }

  public OrderedTraversalController(boolean makeFabFirst) {
    nodeTreeMap = new LinkedHashMap<>();
    this.makeFabFirst = makeFabFirst;
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
    reorderTree(compatRoot);
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
  private void reorderTree(AccessibilityNodeInfoCompat compatRoot) {
    for (WorkingTree subtree : nodeTreeMap.values()) {
      AccessibilityNodeInfoCompat node = subtree.getNode();
      if (makeFabFirst && isFab(node)) {
        WorkingTree targetTree = nodeTreeMap.get(compatRoot).getNext();
        moveNodeBefore(subtree, targetTree);
      }
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
      LogUtils.w(
          TAG,
          "findNext(), can't find WorkingTree for AccessibilityNodeInfo; falling back to"
              + " topmost visible node");
      WorkingTree fallback = findVisibleEdgeWorkingTree(/* forward= */ true);
      return (fallback == null) ? null : fallback.getNode();
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
      LogUtils.w(
          TAG,
          "findPrevious(), can't find WorkingTree for AccessibilityNodeInfo; falling back to"
              + " bottommost visible node");
      WorkingTree fallback = findVisibleEdgeWorkingTree(/* forward= */ false);
      return (fallback == null) ? null : fallback.getNode();
    }

    WorkingTree prevTree = tree.getPrevious();
    if (prevTree != null) {
      return prevTree.getNode();
    }

    return null;
  }

  /**
   * Fallback used when the search pivot node is no longer present in {@link #nodeTreeMap} (e.g.
   * its underlying view was recycled by the app after a large or multi-step auto-scroll, possibly
   * across a list that is itself growing via pagination). The pivot's last-known on-screen bounds
   * are stale once a scroll like that has happened, so comparing them against the freshly-queried
   * bounds of nodes in the rebuilt tree is unreliable. Instead, this picks the node currently
   * nearest to the relevant edge of the visible viewport -- the topmost visible node for {@code
   * forward} (the first thing the user would now encounter reading down), or the bottommost
   * visible node otherwise -- which is invariant to how far or in how many steps the scroll
   * actually moved.
   */
  private @Nullable WorkingTree findVisibleEdgeWorkingTree(boolean forward) {
    int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

    WorkingTree best = null;
    int bestEdge = forward ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    for (Map.Entry<AccessibilityNodeInfoCompat, WorkingTree> entry : nodeTreeMap.entrySet()) {
      Rect bounds = new Rect();
      entry.getKey().getBoundsInScreen(bounds);
      // Skip nodes that aren't actually on screen (e.g. still attached but scrolled fully out of
      // the viewport).
      if (bounds.bottom <= 0 || bounds.top >= screenHeight) {
        continue;
      }
      if (forward ? (bounds.top < bestEdge) : (bounds.bottom > bestEdge)) {
        bestEdge = forward ? bounds.top : bounds.bottom;
        best = entry.getValue();
      }
    }
    return best;
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

  /**
   * Checks for the resource id and package name of specific apps. We do not check for calendar or
   * dialer since they both force accessibility focus and disrupt reordering for FABs.
   */
  private boolean isFab(AccessibilityNodeInfoCompat node) {
    if (Role.getRole(node) == Role.ROLE_FLOATING_ACTION_BUTTON) {
      return true;
    }
    if (node.getViewIdResourceName() == null || node.getPackageName() == null) {
      return false;
    }

    String resourceName = node.getViewIdResourceName();
    CharSequence packageName = node.getPackageName();
    // Clock and Drive
    if (resourceName.contains("fab")
        && (packageName.toString().equals("com.google.android.deskclock")
            || packageName.toString().equals("com.google.android.apps.docs"))) {
      return true;
    }
    // Calendar and Contacts
    if (resourceName.contains("floating_action_button")
        && (packageName.toString().equals("com.google.android.calendar")
            || packageName.toString().equals("com.google.android.contacts"))) {
      return true;
    }

    // Gmail
    if (resourceName.contains("compose_button")
        && packageName.toString().equals("com.google.android.gm")) {
      return true;
    }

    return false;
  }

  /** Dumps the traversal order tree. */
  protected List<AccessibilityNodeInfoCompat> dumpTree() {
    List<AccessibilityNodeInfoCompat> result = new ArrayList<>();
    AccessibilityNodeInfoCompat node = findFirst();
    while (node != null) {
      result.add(node);
      node = findNext(node);
    }
    return result;
  }
}
