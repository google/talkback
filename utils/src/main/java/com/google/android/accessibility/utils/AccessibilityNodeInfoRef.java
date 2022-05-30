/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.utils;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
public class AccessibilityNodeInfoRef {
  private AccessibilityNodeInfoCompat mNode;
  private boolean mOwned;

  /** Returns the current node. */
  public AccessibilityNodeInfoCompat get() {
    return mNode;
  }

  /** Clears this object. */
  public void clear() {
    reset((AccessibilityNodeInfoCompat) null);
  }

  /** Resets this object to contain a new node, taking ownership of the new node. */
  public void reset(AccessibilityNodeInfoCompat newNode) {
    mNode = newNode;
    mOwned = true;
  }

  /**
   * Resets this object with the node held by {@code newNode}. if {@code newNode} was owning the
   * node, ownership is transfered to this object.
   */
  public void reset(AccessibilityNodeInfoRef newNode) {
    reset(newNode.get());
    mOwned = newNode.mOwned;
    newNode.mOwned = false;
  }

  /** Creates a new instance of this class containing a new copy of {@code node}. */
  public static AccessibilityNodeInfoRef obtain(AccessibilityNodeInfoCompat node) {
    return new AccessibilityNodeInfoRef(AccessibilityNodeInfoCompat.obtain(node), true);
  }

  /** Creates a new instance of this class without assuming ownership of {@code node}. */
  @Nullable
  public static AccessibilityNodeInfoRef unOwned(AccessibilityNodeInfoCompat node) {
    return node != null ? new AccessibilityNodeInfoRef(node, false) : null;
  }

  /** Creates a new instance of this class taking ownership of {@code node}. */
  @Nullable
  public static AccessibilityNodeInfoRef owned(AccessibilityNodeInfoCompat node) {
    return node != null ? new AccessibilityNodeInfoRef(node, true) : null;
  }

  /**
   * Creates an {@link AccessibilityNodeInfoRef} with a refreshed copy of {@code node}, taking
   * ownership of the copy. If {@code node} is {@code null}, {@code null} is returned.
   */
  public static AccessibilityNodeInfoRef refreshed(AccessibilityNodeInfoCompat node) {
    return owned(AccessibilityNodeInfoUtils.refreshNode(node));
  }

  /**
   * Makes sure that this object owns its own copy of the node it holds by creating a new copy of
   * the node if not already owned or doing nothing otherwise.
   */
  public AccessibilityNodeInfoRef makeOwned() {
    if (mNode != null && !mOwned) {
      reset(AccessibilityNodeInfoCompat.obtain(mNode));
    }
    return this;
  }

  public AccessibilityNodeInfoRef() {}

  public static boolean isNull(AccessibilityNodeInfoRef ref) {
    return ref == null || ref.get() == null;
  }

  private AccessibilityNodeInfoRef(AccessibilityNodeInfoCompat node, boolean owned) {
    mNode = node;
    mOwned = owned;
  }

  /**
   * Releases the ownership of the underlying node if it was owned, returning the underlying node.
   * This is typically chained with {@link #makeOwned} to have a copy that can be put in another
   * container or {@link AccessibilityNodeInfoRef}. After this call, this object still refers to the
   * underlying node so that any of the traversal methods can be used afterwards.
   */
  public AccessibilityNodeInfoCompat release() {
    mOwned = false;
    return mNode;
  }

  /** Traverses to the last child of this node, returning {@code true} on success. */
  boolean lastChild() {
    if (mNode == null || mNode.getChildCount() < 1) {
      return false;
    }

    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createDescendingIterator(mNode);
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat newNode = iterator.next();
      if (newNode == null) {
        return false;
      }

      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode);
        return true;
      }
    }
    return false;
  }

  /**
   * Traverses to the previous sibling of this node within its parent, returning {@code true} on
   * success.
   */
  public boolean previousSibling() {
    if (mNode == null) {
      return false;
    }
    AccessibilityNodeInfoCompat parent = mNode.getParent();
    if (parent == null) {
      return false;
    }
    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createDescendingIterator(parent);
    if (!moveIteratorAfterNode(iterator, mNode)) {
      return false;
    }

    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat newNode = iterator.next();
      if (newNode == null) {
        return false;
      }
      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode);
        return true;
      }
    }
    return false;
  }

  /** Traverses to the first child of this node if any, returning {@code true} on success. */
  boolean firstChild() {
    if (mNode == null) {
      return false;
    }

    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createAscendingIterator(mNode);
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat newNode = iterator.next();
      if (newNode == null) {
        return false;
      }
      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode);
        return true;
      }
    }
    return false;
  }

  /**
   * Traverses to the next sibling of this node within its parent, returning {@code true} on
   * success.
   */
  public boolean nextSibling() {
    if (mNode == null) {
      return false;
    }
    AccessibilityNodeInfoCompat parent = mNode.getParent();
    if (parent == null) {
      return false;
    }
    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createAscendingIterator(parent);
    if (!moveIteratorAfterNode(iterator, mNode)) {
      return false;
    }

    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat newNode = iterator.next();
      if (newNode == null) {
        return false;
      }
      if (AccessibilityNodeInfoUtils.isVisible(newNode)) {
        reset(newNode);
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean moveIteratorAfterNode(
      Iterator<AccessibilityNodeInfoCompat> iterator, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat nextNode = iterator.next();
      if (node.equals(nextNode)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Traverses to the parent of this node, returning {@code true} on success. On failure, returns
   * {@code false} and does not move.
   */
  public boolean parent() {
    if (mNode == null) {
      return false;
    }
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    visitedNodes.add(AccessibilityNodeInfoCompat.obtain(mNode));
    AccessibilityNodeInfoCompat parentNode = mNode.getParent();
    while (parentNode != null) {
      if (visitedNodes.contains(parentNode)) {
        return false;
      }

      if (AccessibilityNodeInfoUtils.isVisible(parentNode)) {
        reset(parentNode);
        return true;
      }
      visitedNodes.add(parentNode);
      parentNode = parentNode.getParent();
    }
    return false;
  }

  /** Traverses to the next node in depth-first order, returning {@code true} on success. */
  public boolean nextInOrder() {
    if (mNode == null) {
      return false;
    }
    if (firstChild()) {
      return true;
    }
    if (nextSibling()) {
      return true;
    }
    AccessibilityNodeInfoRef tmp = unOwned(mNode);
    while (tmp.parent()) {
      if (tmp.nextSibling()) {
        reset(tmp);
        return true;
      }
    }
    tmp.clear();
    return false;
  }

  /** Traverses to the previous node in depth-first order, returning {@code true} on success. */
  public boolean previousInOrder() {
    if (mNode == null) {
      return false;
    }
    if (previousSibling()) {
      lastDescendant();
      return true;
    }
    return parent();
  }

  /** Traverses to the last descendant of this node, returning {@code true} on success. */
  public boolean lastDescendant() {
    if (!lastChild()) {
      return false;
    }
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    while (lastChild()) {
      if (visitedNodes.contains(mNode)) {
        return false;
      }
      visitedNodes.add(AccessibilityNodeInfoCompat.obtain(mNode));
    }
    return true;
  }
}
