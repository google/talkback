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
import com.google.android.libraries.accessibility.utils.log.LogUtils

/** Tree that represents Accessibility node hierarchy. It lets reorder the structure of the tree. */
class WorkingTree(
  private val node: AccessibilityNodeInfoCompat,
  private var parent: WorkingTree?,
) {

  private val children: MutableList<WorkingTree> = ArrayList()

  fun getNode(): AccessibilityNodeInfoCompat = node

  fun getParent(): WorkingTree? = parent

  fun setParent(parent: WorkingTree?) {
    this.parent = parent
  }

  fun addChild(node: WorkingTree) {
    children.add(node)
  }

  fun removeChild(child: WorkingTree): Boolean = children.remove(child)

  /** Checks whether subTree is a descendant of this WorkingTree node. */
  fun hasDescendant(tree: WorkingTree?): Boolean {
    if (ancestorsHaveLoop()) {
      LogUtils.w(TAG, "Looped ancestors line")
      return false
    }

    // For each ancestor of target descendant node...
    var subTree = tree
    while (subTree != null) {
      val node = subTree.getNode()

      // If ancestor is this working tree node... target is descendant of this node.
      if (this.node == node) {
        return true
      }

      subTree = subTree.getParent()
    }

    return false
  }

  /** Checks whether subTree is a descendant of this WorkingTree node. */
  fun ancestorsHaveLoop(): Boolean {
    val visitedNodes = HashSet<AccessibilityNodeInfoCompat>()

    // For each ancestor node...
    var workNode: WorkingTree? = this
    while (workNode != null) {
      val accessNode = workNode.getNode()
      if (visitedNodes.contains(accessNode)) {
        return true
      }
      visitedNodes.add(accessNode)
      workNode = workNode.getParent()
    }
    return false
  }

  fun swapChild(swappedChild: WorkingTree, newChild: WorkingTree) {
    val position = children.indexOf(swappedChild)
    if (position < 0) {
      LogUtils.e(TAG, "WorkingTree IllegalStateException: swap child not found")
      return
    }

    children[position] = newChild
  }

  val next: WorkingTree?
    get() {
      if (children.isNotEmpty()) {
        return children[0]
      }

      var startNode: WorkingTree? = this
      while (startNode != null) {
        val nextSibling = startNode.nextSibling
        if (nextSibling != null) {
          return nextSibling
        }

        startNode = startNode.getParent()
      }

      return null
    }

  val nextSibling: WorkingTree?
    get() {
      val parent = getParent() ?: return null

      var currentIndex = parent.children.indexOf(this)
      if (currentIndex < 0) {
        LogUtils.e(TAG, "WorkingTree IllegalStateException: swap child not found")
        return null
      }

      currentIndex++

      if (currentIndex >= parent.children.size) {
        // it was last child
        return null
      }

      return parent.children[currentIndex]
    }

  val previous: WorkingTree?
    get() {
      val previousSibling = previousSibling
      if (previousSibling != null) {
        return previousSibling.lastNode
      }

      return getParent()
    }

  val previousSibling: WorkingTree?
    get() {
      val parent = getParent() ?: return null

      var currentIndex = parent.children.indexOf(this)
      if (currentIndex < 0) {
        LogUtils.e(TAG, "WorkingTree IllegalStateException: swap child not found")
        return null
      }

      currentIndex--

      if (currentIndex < 0) {
        // it was first child
        return null
      }

      return parent.children[currentIndex]
    }

  val lastNode: WorkingTree
    get() {
      var node = this
      while (node.children.isNotEmpty()) {
        node = node.children.last()
      }

      return node
    }

  val root: WorkingTree
    get() {
      var root = this
      var parent: WorkingTree?
      while (true) {
        parent = root.getParent()
        if (parent == null) {
          break
        }
        root = parent
      }

      return root
    }

  private companion object {
    const val TAG = "WorkingTree"
  }
}
