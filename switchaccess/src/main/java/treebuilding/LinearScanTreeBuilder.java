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

package com.google.android.accessibility.switchaccess.treebuilding;

import android.content.Context;
import com.google.android.accessibility.switchaccess.ClearFocusNode;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.TreeScanNode;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/** Builds a binary tree of TreeScanNodes using linear scanning. */
public class LinearScanTreeBuilder extends BinaryTreeBuilder {

  public LinearScanTreeBuilder(Context context) {
    super(context);
  }

  @Override
  public TreeScanNode addViewHierarchyToTree(
      SwitchAccessNodeCompat root, TreeScanNode treeToBuildOn, boolean includeNonActionableItems) {
    TreeScanNode tree = (treeToBuildOn != null) ? treeToBuildOn : new ClearFocusNode();
    LinkedList<SwitchAccessNodeCompat> talkBackOrderList = getNodesInTalkBackOrder(root);
    Iterator<SwitchAccessNodeCompat> reverseListIterator = talkBackOrderList.descendingIterator();
    while (reverseListIterator.hasNext()) {
      SwitchAccessNodeCompat next = reverseListIterator.next();
      tree = addCompatToTree(next, tree, includeNonActionableItems);
      next.recycle();
    }
    return tree;
  }

  @Override
  public TreeScanNode addWindowListToTree(
      List<SwitchAccessWindowInfo> windowList,
      TreeScanNode treeToBuildOn,
      boolean shouldPlaceTreeFirst,
      boolean includeNonActionableItems) {
    /* Not currently needed */
    return null;
  }
}
