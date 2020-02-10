/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.switchaccess.treebuilding;

import android.accessibilityservice.AccessibilityService;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSystemProvidedNode;

/** Extension of tree builder that assumes binary trees */
public abstract class BinaryTreeBuilder extends TreeBuilder {
  public BinaryTreeBuilder(AccessibilityService service) {
    super(service);
  }

  /**
   * Adds the specified node to the tree. If the node has more than one action, a context menu is
   * added to select from them.
   *
   * @param compat The node whose actions should be added
   * @param tree The tree to add them to
   * @param includeNonActionableItems Whether non-actionable items should be added when building the
   *     tree
   * @return The new tree with the added actions for the specified node. If non-actionable items are
   *     not being scanned and no actions are associated with the node, the original tree is
   *     returned. If non-actionable items are being scanned but no text is associated with the
   *     node, also return the original tree.
   */
  public TreeScanNode addCompatToTree(
      final SwitchAccessNodeCompat compat, TreeScanNode tree, boolean includeNonActionableItems) {
    TreeScanSystemProvidedNode node = createNodeIfImportant(compat, includeNonActionableItems);

    if (node != null) {
      tree = new TreeScanSelectionNode(node, tree);
    }

    return tree;
  }
}
