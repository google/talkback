package com.google.android.accessibility.switchaccess.treebuilding;

import android.content.Context;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.TreeScanNode;
import com.google.android.accessibility.switchaccess.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.TreeScanSystemProvidedNode;

/** Extension of tree builder that assumes binary trees */
public abstract class BinaryTreeBuilder extends TreeBuilder {
  public BinaryTreeBuilder(Context context) {
    super(context);
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
