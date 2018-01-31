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

import java.util.List;

/** Interface for classes that represent nodes in a scanning tree. */
public interface TreeScanNode {

  /**
   * Recycle the tree based at this node. Some nodes may hold resources such as {@code
   * AccessibilityNodeInfo}s that require explicit recycling.
   */
  public void recycle();

  /**
   * Get parent node.
   *
   * @return The parent node
   */
  public TreeScanSelectionNode getParent();

  /**
   * Set the parent node.
   *
   * @param parent The parent of this node in the tree
   */
  public void setParent(TreeScanSelectionNode parent);

  /**
   * Return an ordered list of all leaves in this tree. Order in the list corresponds to traversal
   * order, with items traversed earlier appearing earlier in the list. If this is a leaf node, the
   * list should contain only one item.
   */
  public List<TreeScanLeafNode> getNodesList();
}
