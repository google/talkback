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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.Role
import com.google.android.accessibility.utils.WebInterfaceUtils
import com.google.android.libraries.accessibility.utils.log.LogUtils

class OrderedTraversalController @JvmOverloads constructor(private val makeFabFirst: Boolean = false) {

  private var speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>? = null
  private var tree: WorkingTree? = null
  private val nodeTreeMap: MutableMap<AccessibilityNodeInfoCompat, WorkingTree> = LinkedHashMap()
  private var initialFocusNode: AccessibilityNodeInfoCompat? = null

  fun setSpeakingNodesCache(speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean>?) {
    this.speakingNodesCache = speakingNodesCache
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
  fun initOrder(
    compatRoot: AccessibilityNodeInfoCompat?,
    includeChildrenOfNodesWithWebActions: Boolean,
  ) {
    if (compatRoot == null) {
      return
    }

    val boundsCalculator = NodeCachedBoundsCalculator()
    boundsCalculator.setSpeakingNodesCache(speakingNodesCache)
    tree =
      createWorkingTree(compatRoot, null, boundsCalculator, includeChildrenOfNodesWithWebActions)
    reorderTree(compatRoot)
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
  private fun createWorkingTree(
    rootNode: AccessibilityNodeInfoCompat,
    parent: WorkingTree?,
    boundsCalculator: NodeCachedBoundsCalculator,
    includeChildrenOfNodesWithWebActions: Boolean,
  ): WorkingTree? {
    if (nodeTreeMap.containsKey(rootNode)) {
      LogUtils.w(TAG, "creating node tree with looped nodes - break the loop edge")
      return null
    }

    val tree = WorkingTree(rootNode, parent)
    nodeTreeMap[rootNode] = tree

    // When we reach a node that supports web navigation, we traverse using the web navigation
    // actions, so we should not try to determine the ordering of its descendants.
    if (!includeChildrenOfNodesWithWebActions && WebInterfaceUtils.supportsWebActions(rootNode)) {
      return tree
    }

    val iterator = ReorderedChildrenIterator.createAscendingIterator(rootNode, boundsCalculator)
    while (iterator != null && iterator.hasNext()) {
      val child = iterator.next()
      val childSubTree =
        createWorkingTree(child, tree, boundsCalculator, includeChildrenOfNodesWithWebActions)
      if (childSubTree != null) {
        tree.addChild(childSubTree)
      }
    }
    return tree
  }

  /**
   * reorder previously created tree according to after/before view traversal order on separate
   * nodes
   */
  private fun reorderTree(compatRoot: AccessibilityNodeInfoCompat) {
    for (subtree in nodeTreeMap.values) {
      val node = subtree.getNode()
      if (makeFabFirst && isFab(node)) {
        val targetTree = nodeTreeMap[compatRoot]?.next
        moveNodeBefore(subtree, targetTree)
      }
      if (AccessibilityNodeInfoUtils.hasRequestInitialAccessibilityFocus(node)) {
        // TODO: Add test case after Roboletric in Google3 supports API 34.
        initialFocusNode = node
      }
      val beforeNode = node.traversalBefore
      if (beforeNode != null) {
        val targetTree = nodeTreeMap[beforeNode]
        moveNodeBefore(subtree, targetTree)
      } else {
        val afterNode = node.traversalAfter
        if (afterNode != null) {
          val targetTree = nodeTreeMap[afterNode]
          moveNodeAfter(subtree, targetTree)
        }
      }
    }
  }

  /** Moves movingTree before targetTree. */
  private fun moveNodeBefore(movingTree: WorkingTree?, targetTree: WorkingTree?) {
    if (movingTree == null || targetTree == null) {
      return
    }

    if (movingTree.hasDescendant(targetTree)) {
      // no operation if move child before parent
      return
    }

    // Find subtree to move.
    val movingTreeRoot = getParentsThatAreMovedBeforeOrSameNode(movingTree)

    // Find destination for movingTreeRoot.
    val parent = targetTree.getParent()
    if (movingTreeRoot.hasDescendant(parent)) {
      return // Moving movingTreeRoot under its own descendant would create a loop.
    }

    // Unlink moving subtree from tree.
    detachSubtreeFromItsParent(movingTreeRoot)

    // swap target node with moving node on targets node parent children list
    parent?.swapChild(targetTree, movingTreeRoot)

    movingTreeRoot.setParent(parent)

    // add target node as last child of moving node
    movingTree.addChild(targetTree)
    targetTree.setParent(movingTree)
  }

  /**
   * This method is called before moving subtree. It checks if parent of that node was moved on its
   * place because it has before property to that node. In that case parent node should be moved
   * with movingTree node.
   *
   * @return top node that should be moved with movingTree node.
   */
  private fun getParentsThatAreMovedBeforeOrSameNode(movingTree: WorkingTree): WorkingTree {
    val parent = movingTree.getParent() ?: return movingTree

    val parentNode = parent.getNode()
    val parentNodeBefore = parentNode.traversalBefore ?: return movingTree

    if (parentNodeBefore == movingTree.getNode()) {
      return getParentsThatAreMovedBeforeOrSameNode(parent)
    }

    return movingTree
  }

  private fun detachSubtreeFromItsParent(subtree: WorkingTree) {
    val movingTreeParent = subtree.getParent()
    movingTreeParent?.removeChild(subtree)
    subtree.setParent(null)
  }

  private fun moveNodeAfter(movingTree: WorkingTree?, targetTree: WorkingTree?) {
    var movingTree = movingTree
    if (movingTree == null || targetTree == null) {
      return
    }

    if (movingTree.hasDescendant(targetTree)) {
      return // Moving movingTree under its own descendant would create a loop.
    }
    movingTree = getParentsThatAreMovedBeforeOrSameNode(movingTree)
    if (movingTree.hasDescendant(targetTree)) {
      return // Moving movingTree under its own descendant would create a loop.
    }
    detachSubtreeFromItsParent(movingTree)
    targetTree.addChild(movingTree)
    movingTree.setParent(targetTree)
  }

  fun findNext(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    val tree = nodeTreeMap[node]
    if (tree == null) {
      LogUtils.w(TAG, "findNext(), can't find WorkingTree for AccessibilityNodeInfo")
      return null
    }

    return tree.next?.getNode()
  }

  fun findPrevious(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    val tree = nodeTreeMap[node]
    if (tree == null) {
      LogUtils.w(TAG, "findPrevious(), can't find WorkingTree for AccessibilityNodeInfo")
      return null
    }

    return tree.previous?.getNode()
  }

  /** Searches first node to be focused */
  fun findFirst(): AccessibilityNodeInfoCompat? = tree?.root?.getNode()

  fun findFirst(rootNode: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (rootNode == null) {
      return null
    }

    return nodeTreeMap[rootNode]?.getNode()
  }

  fun findInitial(rootNode: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (rootNode == null) {
      return null
    }

    val tree = nodeTreeMap[rootNode] ?: return null

    if (tree.hasDescendant(nodeTreeMap[initialFocusNode])) {
      return initialFocusNode
    }

    return null
  }

  /** Searches last node to be focused */
  fun findLast(): AccessibilityNodeInfoCompat? = tree?.root?.lastNode?.getNode()

  fun findLast(rootNode: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
    if (rootNode == null) {
      return null
    }

    return nodeTreeMap[rootNode]?.lastNode?.getNode()
  }

  /**
   * Checks for the resource id and package name of specific apps. We do not check for calendar or
   * dialer since they both force accessibility focus and disrupt reordering for FABs.
   */
  private fun isFab(node: AccessibilityNodeInfoCompat): Boolean {
    if (Role.getRole(node) == Role.ROLE_FLOATING_ACTION_BUTTON) {
      return true
    }
    if (node.viewIdResourceName == null || node.packageName == null) {
      return false
    }

    val resourceName = node.viewIdResourceName
    val packageName = node.packageName
    // Clock and Drive
    if (resourceName.contains("fab") &&
      (packageName.toString() == "com.google.android.deskclock" ||
        packageName.toString() == "com.google.android.apps.docs")
    ) {
      return true
    }
    // Calendar and Contacts
    if (resourceName.contains("floating_action_button") &&
      (packageName.toString() == "com.google.android.calendar" ||
        packageName.toString() == "com.google.android.contacts")
    ) {
      return true
    }

    // Gmail
    if (resourceName.contains("compose_button") && packageName.toString() == "com.google.android.gm") {
      return true
    }

    return false
  }

  /** Dumps the traversal order tree. */
  internal fun dumpTree(): List<AccessibilityNodeInfoCompat> {
    val result = ArrayList<AccessibilityNodeInfoCompat>()
    var node = findFirst()
    while (node != null) {
      result.add(node)
      node = findNext(node)
    }
    return result
  }

  private companion object {
    const val TAG = "OrderedTraversalCont"
  }
}
