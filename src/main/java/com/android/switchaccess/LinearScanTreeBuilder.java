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

package com.android.switchaccess;

import android.annotation.TargetApi;
import android.os.Build;

import java.util.*;

/**
 * Build a binary tree of OptionScanNodes using linear scanning.
 * TODO(PW) Generalize this class so it's easy to add new ways to build a tree and to
 * mix-and-match them. For example, the original Switch Access service uses row-column for IMEs and
 * linear scanning elsewhere.
 */
public class LinearScanTreeBuilder implements TreeBuilder {
    /**
     * Build a linearly-scanned context menu to choose from a list of actions
     * @param actions The actions to choose from, in the order they should be scanned.
     * @return A tree containing context menu nodes to linearly scan the options. If no options
     * are selected, the tree falls through to a {@code ClearFocusNode}
     */
    public static OptionScanNode buildContextMenuTree(
            List<? extends OptionScanActionNode> actions) {
        ContextMenuItem tree = new ClearFocusNode();
        if (actions != null) {
            for (int i = actions.size() - 1; i >= 0; --i) {
                tree = new ContextMenuNode(actions.get(i), tree);
            }
        }
        return tree;
    }

    /**
     * Build a tree with all clickable nodes in the tree anchored at root.
     * @param root The root of the tree of SwitchAccessNodeCompat
     * @param treeToBuildOn The tree that should be traversed if the user doesn't select
     * any options found under {@code root}. If this option is {@code null}, a
     * {@code ClearActionNode} terminates the tree.
     * @return A tree of OptionScanNode objects to be scanned. The tree terminates with a
     * ClearFocusNode. If no clickable nodes are found, a single ClearFocusNode is returned.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public OptionScanNode buildTreeFromNodeTree(SwitchAccessNodeCompat root,
            OptionScanNode treeToBuildOn) {
        OptionScanNode tree = (treeToBuildOn != null) ? treeToBuildOn : new ClearFocusNode();
        LinkedList<SwitchAccessNodeCompat> talkBackOrderList =
                TreeBuilderUtils.getNodesInTalkBackOrder(root);
        Iterator<SwitchAccessNodeCompat> reverseListIterator =
                talkBackOrderList.descendingIterator();
        while (reverseListIterator.hasNext()) {
            SwitchAccessNodeCompat next = reverseListIterator.next();
            tree = TreeBuilderUtils.addCompatToTree(next, tree);
            next.recycle();
        }
        return tree;
    }
}
