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

package com.google.android.accessibility.utils

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * A class that simplifies traversal of node trees.
 *
 * <p>This class keeps track of an {@link AccessibilityNodeInfoCompat} object and can traverse to
 * other nodes in the tree, or be reset to other nodes. The node can be owned.
 *
 * <p>Any node can be assigned to objects of this class, including nodes that are not visible to the
 * user. The traversal methods, however, will only traverse to visible nodes.
 *
 * @see AccessibilityNodeInfoUtils#isVisible(AccessibilityNodeInfoCompat)
 */
class AccessibilityNodeInfoRef {
  private var mNode: AccessibilityNodeInfoCompat? = null

  /** Returns the current node. */
  fun get(): AccessibilityNodeInfoCompat? = mNode

  /** Clears this object. */
  fun clear() {
    reset(null as AccessibilityNodeInfoCompat?)
  }

  /** Resets this object to contain a new node, taking ownership of the new node. */
  fun reset(newNode: AccessibilityNodeInfoCompat?) {
    mNode = newNode
  }

  /**
   * Resets this object with the node held by {@code newNode}. if {@code newNode} was owning the
   * node, ownership is transfered to this object.
   */
  fun reset(newNode: AccessibilityNodeInfoRef) {
    reset(newNode.get())
  }

  /**
   * Makes sure that this object owns its own copy of the node it holds by creating a new copy of
   * the node if not already owned or doing nothing otherwise.
   */
  @CanIgnoreReturnValue
  fun makeOwned(): AccessibilityNodeInfoRef {
    reset(mNode)
    return this
  }

  constructor()

  private constructor(node: AccessibilityNodeInfoCompat?) {
    mNode = node
  }

  /**
   * Releases the ownership of the underlying node if it was owned, returning the underlying node.
   * This is typically chained with {@link #makeOwned} to have a copy that can be put in another
   * container or {@link AccessibilityNodeInfoRef}. After this call, this object still refers to the
   * underlying node so that any of the traversal methods can be used afterwards.
   */
  fun release(): AccessibilityNodeInfoCompat? = mNode

  /** Traverses to the last child of this node, returning {@code true} on success. */
  internal fun lastChild(): Boolean {
    val node = mNode
    if (node == null || node.childCount < 1) {
      return false
    }

    val iterator = ReorderedChildrenIterator.createDescendingIterator(node)
    while (iterator.hasNext()) {
      val newNode = iterator.next() ?: return false

      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode)
        return true
      }
    }
    return false
  }

  /**
   * Traverses to the previous sibling of this node within its parent, returning {@code true} on
   * success.
   */
  fun previousSibling(): Boolean {
    val node = mNode ?: return false
    val parent = node.parent ?: return false
    val iterator = ReorderedChildrenIterator.createDescendingIterator(parent)
    if (!moveIteratorAfterNode(iterator, node)) {
      return false
    }

    while (iterator.hasNext()) {
      val newNode = iterator.next() ?: return false
      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode)
        return true
      }
    }
    return false
  }

  /** Traverses to the first child of this node if any, returning {@code true} on success. */
  internal fun firstChild(): Boolean {
    val node = mNode ?: return false

    val iterator = ReorderedChildrenIterator.createAscendingIterator(node)
    while (iterator.hasNext()) {
      val newNode = iterator.next() ?: return false
      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode)
        return true
      }
    }
    return false
  }

  /**
   * Traverses to the next sibling of this node within its parent, returning {@code true} on
   * success.
   */
  fun nextSibling(): Boolean {
    val node = mNode ?: return false
    val parent = node.parent ?: return false
    val iterator = ReorderedChildrenIterator.createAscendingIterator(parent)
    if (!moveIteratorAfterNode(iterator, node)) {
      return false
    }

    while (iterator.hasNext()) {
      val newNode = iterator.next() ?: return false
      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode)
        return true
      }
    }
    return false
  }

  private fun moveIteratorAfterNode(
    iterator: Iterator<AccessibilityNodeInfoCompat>,
    node: AccessibilityNodeInfoCompat?,
  ): Boolean {
    if (node == null) {
      return false
    }
    while (iterator.hasNext()) {
      val nextNode = iterator.next()
      if (node == nextNode) {
        return true
      }
    }

    return false
  }

  /**
   * Traverses to the parent of this node, returning {@code true} on success. On failure, returns
   * {@code false} and does not move.
   */
  fun parent(): Boolean {
    val node = mNode ?: return false
    val visitedNodes = HashSet<AccessibilityNodeInfoCompat>()
    visitedNodes.add(node)
    var parentNode = node.parent
    while (parentNode != null) {
      if (visitedNodes.contains(parentNode)) {
        return false
      }

      if (AccessibilityNodeInfoUtils.isVisible(parentNode)) {
        reset(parentNode)
        return true
      }
      visitedNodes.add(parentNode)
      parentNode = parentNode.parent
    }
    return false
  }

  /** Traverses to the next node in depth-first order, returning {@code true} on success. */
  fun nextInOrder(): Boolean {
    if (mNode == null) {
      return false
    }
    if (firstChild()) {
      return true
    }
    if (nextSibling()) {
      return true
    }
    val tmp = unOwned(mNode) ?: return false
    while (tmp.parent()) {
      if (tmp.nextSibling()) {
        reset(tmp)
        return true
      }
    }
    tmp.clear()
    return false
  }

  /** Traverses to the previous node in depth-first order, returning {@code true} on success. */
  fun previousInOrder(): Boolean {
    if (mNode == null) {
      return false
    }
    if (previousSibling()) {
      lastDescendant()
      return true
    }
    return parent()
  }

  /** Traverses to the last descendant of this node, returning {@code true} on success. */
  fun lastDescendant(): Boolean {
    if (!lastChild()) {
      return false
    }
    val visitedNodes = HashSet<AccessibilityNodeInfoCompat>()
    while (lastChild()) {
      val node = mNode ?: return false
      if (visitedNodes.contains(node)) {
        return false
      }
      visitedNodes.add(node)
    }
    return true
  }

  companion object {
    /** Creates a new instance of this class. */
    @JvmStatic
    fun obtain(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoRef =
      AccessibilityNodeInfoRef(node)

    /** Creates a new instance of this class without assuming ownership of {@code node}. */
    @JvmStatic
    fun unOwned(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoRef? =
      if (node != null) AccessibilityNodeInfoRef(node) else null

    /** Creates a new instance of this class taking ownership of {@code node}. */
    @JvmStatic
    fun owned(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoRef? =
      if (node != null) AccessibilityNodeInfoRef(node) else null

    /**
     * Creates an {@link AccessibilityNodeInfoRef} with a refreshed copy of {@code node}, taking
     * ownership of the copy. If {@code node} is {@code null}, {@code null} is returned.
     */
    @JvmStatic
    fun refreshed(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoRef? =
      owned(AccessibilityNodeInfoUtils.refreshNode(node))

    @JvmStatic
    fun isNull(ref: AccessibilityNodeInfoRef?): Boolean = ref == null || ref.get() == null
  }
}
