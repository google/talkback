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

package com.android.switchaccess.treebuilding;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import com.android.switchaccess.ClearFocusNode;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.ContextMenuNode;
import com.android.switchaccess.OptionScanActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.switchaccess.SwitchAccessWindowInfo;

import java.util.*;

/**
 * Build a binary tree of OptionScanNodes using linear scanning.
 */
public class LinearScanTreeBuilder extends BinaryTreeBuilder {

    public LinearScanTreeBuilder(Context context) {
        super(context);
    }

    @Override
    public OptionScanNode addViewHierarchyToTree(SwitchAccessNodeCompat root,
            OptionScanNode treeToBuildOn) {
        OptionScanNode tree = (treeToBuildOn != null) ? treeToBuildOn : new ClearFocusNode();
        LinkedList<SwitchAccessNodeCompat> talkBackOrderList = getNodesInTalkBackOrder(root);
        Iterator<SwitchAccessNodeCompat> reverseListIterator =
                talkBackOrderList.descendingIterator();
        while (reverseListIterator.hasNext()) {
            SwitchAccessNodeCompat next = reverseListIterator.next();
            tree = addCompatToTree(next, tree);
            next.recycle();
        }
        return tree;
    }

    @Override
    public OptionScanNode addWindowListToTree(List<SwitchAccessWindowInfo> windowList,
            OptionScanNode treeToBuildOn) {
        /* Not currently needed */
        return null;
    }
}
