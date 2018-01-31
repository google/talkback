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

package com.google.android.accessibility.switchaccess;

import android.graphics.Paint;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** Tree scanning node that holds other nodes for scanning. */
public class TreeScanSelectionNode implements TreeScanNode {

  protected TreeScanNode[] mChildren;
  private TreeScanSelectionNode mParent;

  /**
   * Selection nodes must be constructed with at least two things to select between
   *
   * @param child0 The first item to select
   * @param child1 The second item to select
   * @param otherChildren Any other items to select
   */
  public TreeScanSelectionNode(
      TreeScanNode child0, TreeScanNode child1, TreeScanNode... otherChildren) {
    mChildren = new TreeScanNode[otherChildren.length + 2];
    mChildren[0] = child0;
    mChildren[1] = child1;
    System.arraycopy(otherChildren, 0, mChildren, 2, otherChildren.length);
    for (TreeScanNode child : mChildren) {
      child.setParent(this);
    }
  }

  @Override
  public void recycle() {
    for (TreeScanNode child : mChildren) {
      child.recycle();
    }
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof TreeScanSelectionNode)) {
      return false;
    }
    TreeScanSelectionNode otherNode = (TreeScanSelectionNode) other;
    if (otherNode.getChildCount() != getChildCount()) {
      return false;
    }
    for (int i = 0; i < mChildren.length; i++) {
      TreeScanNode child = mChildren[i];
      if (!child.equals(otherNode.getChild(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    /*
     * Hashing function taken from an example in "Effective Java" page 38/39. The number 13 is
     * arbitrary, but choosing non-zero number to start decreases the number of collisions. 37
     * is used as it's an odd prime. If multiplication overflowed and the 37 was an even number,
     * it would be equivalent to bit shifting. The fact that 37 is prime is standard practice.
     */
    int hashCode = 13;
    for (TreeScanNode element : mChildren) {
      hashCode = 37 * hashCode + element.hashCode();
    }
    hashCode = 37 * hashCode + getClass().hashCode();
    return hashCode;
  }

  @Override
  public TreeScanSelectionNode getParent() {
    return mParent;
  }

  @Override
  public void setParent(TreeScanSelectionNode parent) {
    mParent = parent;
  }

  @Override
  public List<TreeScanLeafNode> getNodesList() {
    List<TreeScanLeafNode> nodes = new LinkedList<>();
    for (TreeScanNode child : mChildren) {
      nodes.addAll(child.getNodesList());
    }
    return nodes;
  }

  /**
   * Get the number of child nodes
   *
   * @return The number of child nodes.
   */
  public int getChildCount() {
    return mChildren.length;
  }

  /**
   * Get a specified child node.
   *
   * @param index The index of the desired child.
   * @return The child requested, or {@code null} if {@code ((index < 0) || (index >=
   *     getChildCount))}
   */
  @SuppressWarnings("JavaDoc")
  public TreeScanNode getChild(int index) {
    if ((index < 0) || (index >= getChildCount())) {
      return null;
    }
    return mChildren[index];
  }

  /**
   * Shows the selectable children reachable from this node.
   *
   * @param highlighter The {@code HighlightStrategy} used to indicate selectable views
   * @param paints The {@code Paint}s used to indicate selectable views
   */
  public void showSelections(HighlightStrategy highlighter, Paint[] paints) {
    /* Display the options for the children. */
    int childCount = getChildCount();
    for (int childIndex = 0; childIndex < childCount; ++childIndex) {
      Set<TreeScanLeafNode> nodes = new HashSet<>();
      nodes.addAll(getChild(childIndex).getNodesList());
      highlighter.highlight(nodes, paints[childIndex % paints.length], childIndex, childCount);
    }
  }
}
