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

package com.google.android.accessibility.switchaccess.treenodes;

import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.google.android.accessibility.switchaccess.ui.HighlightStrategy;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** Tree scanning node that holds other nodes for scanning. */
public class TreeScanSelectionNode extends TreeScanNode {

  private final TreeScanNode[] children;
  private TreeScanSelectionNode parent;

  /**
   * Selection nodes must be constructed with at least two things to select between
   *
   * @param child0 The first item to select
   * @param child1 The second item to select
   * @param otherChildren Any other items to select
   */
  // #setParent throws the suppressed warning because the checker only allows assigning an
  // uninitialized object (this) to a field (parent) in a specific way. See
  // https://checkerframework.org/manual/#initialization-checker section 3.8.5. Doing it this way
  // would require significant overhaul of our tree structure, so we suppress the warning instead.
  @SuppressWarnings("initialization:argument.type.incompatible")
  public TreeScanSelectionNode(
      TreeScanNode child0, TreeScanNode child1, TreeScanNode... otherChildren) {
    children = new TreeScanNode[otherChildren.length + 2];
    children[0] = child0;
    children[1] = child1;
    System.arraycopy(otherChildren, 0, children, 2, otherChildren.length);
    for (TreeScanNode child : children) {
      child.setParent(this);
    }
  }

  @Override
  public void recycle() {
    for (TreeScanNode child : children) {
      child.recycle();
    }
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (!(other instanceof TreeScanSelectionNode)) {
      return false;
    }
    TreeScanSelectionNode otherNode = (TreeScanSelectionNode) other;
    if (otherNode.getChildCount() != getChildCount()) {
      return false;
    }
    for (int i = 0; i < children.length; i++) {
      TreeScanNode child = children[i];
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
    for (TreeScanNode element : children) {
      hashCode = 37 * hashCode + element.hashCode();
    }
    hashCode = 37 * hashCode + getClass().hashCode();
    return hashCode;
  }

  @Override
  public TreeScanSelectionNode getParent() {
    return parent;
  }

  @Override
  public void setParent(TreeScanSelectionNode parent) {
    this.parent = parent;
  }

  @Override
  public List<TreeScanLeafNode> getNodesList() {
    List<TreeScanLeafNode> nodes = new LinkedList<>();
    for (TreeScanNode child : children) {
      nodes.addAll(child.getNodesList());
    }
    return nodes;
  }

  @Override
  public TreeScanLeafNode getFirstLeafNode() {
    return children[0].getFirstLeafNode();
  }

  /**
   * Get the number of child nodes
   *
   * @return The number of child nodes.
   */
  public int getChildCount() {
    return children.length;
  }

  /**
   * Get a specified child node. Calling this method with an invalid index will cause an out of
   * bounds exception.
   *
   * @param index The valid index of the desired child
   * @return The child requested
   */
  public TreeScanNode getChild(int index) {
    return children[index];
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
      Set<TreeScanLeafNode> nodes = new HashSet<>(getChild(childIndex).getNodesList());
      highlighter.highlight(nodes, paints[childIndex % paints.length], childIndex, childCount);
    }
  }
}
