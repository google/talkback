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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Tree that represents Accessibility node hierarchy. It lets reorder the structure of the tree. */
public class WorkingTree {

  private static final String TAG = "WorkingTree";

  private AccessibilityNodeInfoCompat mNode;
  @Nullable private WorkingTree mParent;
  private final List<WorkingTree> mChildren = new ArrayList<>();

  public WorkingTree(AccessibilityNodeInfoCompat node, @Nullable WorkingTree parent) {
    mNode = node;
    mParent = parent;
  }

  public AccessibilityNodeInfoCompat getNode() {
    return mNode;
  }

  public @Nullable WorkingTree getParent() {
    return mParent;
  }

  public void setParent(@Nullable WorkingTree parent) {
    mParent = parent;
  }

  public void addChild(WorkingTree node) {
    mChildren.add(node);
  }

  public boolean removeChild(WorkingTree child) {
    return mChildren.remove(child);
  }

  /** Checks whether subTree is a descendant of this WorkingTree node. */
  public boolean hasDescendant(@Nullable WorkingTree tree) {

    if (ancestorsHaveLoop()) {
      LogUtils.w(TAG, "Looped ancestors line");
      return false;
    }

    // For each ancestor of target descendant node...
    WorkingTree subTree = tree;
    while (subTree != null) {
      AccessibilityNodeInfoCompat node = subTree.getNode();

      // If ancestor is this working tree node... target is descendant of this node.
      if (mNode.equals(node)) {
        return true;
      }

      subTree = subTree.getParent();
    }

    return false;
  }

  /** Checks whether subTree is a descendant of this WorkingTree node. */
  public boolean ancestorsHaveLoop() {

    // Not owner of contained nodes, do not call recycle().
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();

    // For each ancestor node...
    for (WorkingTree workNode = this; workNode != null; workNode = workNode.getParent()) {
      AccessibilityNodeInfoCompat accessNode = workNode.getNode();
      if (visitedNodes.contains(accessNode)) {
        return true;
      }
      visitedNodes.add(accessNode);
    }
    return false;
  }

  public void swapChild(WorkingTree swappedChild, WorkingTree newChild) {
    int position = mChildren.indexOf(swappedChild);
    if (position < 0) {
      LogUtils.e(TAG, "WorkingTree IllegalStateException: swap child not found");
      return;
    }

    mChildren.set(position, newChild);
  }

  public @Nullable WorkingTree getNext() {
    if (!mChildren.isEmpty()) {
      return mChildren.get(0);
    }

    WorkingTree startNode = this;
    while (startNode != null) {
      WorkingTree nextSibling = startNode.getNextSibling();
      if (nextSibling != null) {
        return nextSibling;
      }

      startNode = startNode.getParent();
    }

    return null;
  }

  public @Nullable WorkingTree getNextSibling() {
    WorkingTree parent = getParent();
    if (parent == null) {
      return null;
    }

    int currentIndex = parent.mChildren.indexOf(this);
    if (currentIndex < 0) {
      LogUtils.e(TAG, "WorkingTree IllegalStateException: swap child not found");
      return null;
    }

    currentIndex++;

    if (currentIndex >= parent.mChildren.size()) {
      // it was last child
      return null;
    }

    return parent.mChildren.get(currentIndex);
  }

  public @Nullable WorkingTree getPrevious() {
    WorkingTree previousSibling = getPreviousSibling();
    if (previousSibling != null) {
      return previousSibling.getLastNode();
    }

    return getParent();
  }

  public @Nullable WorkingTree getPreviousSibling() {
    WorkingTree parent = getParent();
    if (parent == null) {
      return null;
    }

    int currentIndex = parent.mChildren.indexOf(this);
    if (currentIndex < 0) {
      LogUtils.e(TAG, "WorkingTree IllegalStateException: swap child not found");
      return null;
    }

    currentIndex--;

    if (currentIndex < 0) {
      // it was first child
      return null;
    }

    return parent.mChildren.get(currentIndex);
  }

  public WorkingTree getLastNode() {
    WorkingTree node = this;
    while (!node.mChildren.isEmpty()) {
      node = node.mChildren.get(node.mChildren.size() - 1);
    }

    return node;
  }

  public WorkingTree getRoot() {
    WorkingTree root = this;
    WorkingTree parent;
    while ((parent = root.getParent()) != null) {
      root = parent;
    }

    return root;
  }
}
