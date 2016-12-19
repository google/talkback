package com.android.switchaccess.treebuilding;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.android.switchaccess.AccessibilityNodeActionNode;
import com.android.switchaccess.ClearFocusNode;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.ContextMenuNode;
import com.android.switchaccess.OptionScanActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.OptionScanSelectionNode;
import com.android.switchaccess.SwitchAccessNodeCompat;

import java.util.List;

/**
 * Extension of tree builder that assumes binary trees
 */
public abstract class BinaryTreeBuilder extends TreeBuilder {
    public BinaryTreeBuilder(Context context) {
        super(context);
    }

    @Override
    public OptionScanNode buildContextMenu(List<? extends ContextMenuItem> actionList) {
        ContextMenuItem tree = new ClearFocusNode();
        if (actionList != null) {
            for (int i = actionList.size() - 1; i >= 0; --i) {
                tree = new ContextMenuNode(actionList.get(i), tree);
            }
        }
        return tree;
    }

    /**
     * Add the specified node to the tree. If the node has more than one action, a context menu
     * is added to select from them.
     *
     * @param compat The node whose actions should be added
     * @param tree The tree to add them to
     * @return The new tree with the added actions for the specified node. If no actions are
     * associated with the node, the original tree is returned.
     */
    public OptionScanNode addCompatToTree(final SwitchAccessNodeCompat compat,
                                             OptionScanNode tree) {
        List<AccessibilityNodeActionNode> actionNodes = getCompatActionNodes(compat);
        if (actionNodes.size() == 1) {
            tree = new OptionScanSelectionNode(actionNodes.get(0), tree);
        } else if (actionNodes.size() > 1) {
            tree = new OptionScanSelectionNode(buildContextMenu(actionNodes), tree);
        }
        return tree;
    }
}
