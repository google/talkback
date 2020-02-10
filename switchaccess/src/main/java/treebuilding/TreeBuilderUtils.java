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

import android.graphics.Rect;
import com.google.android.accessibility.switchaccess.treenodes.ShowActionsMenuNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import java.io.PrintStream;
import java.util.Iterator;

/** Useful methods for debugging tree builders */
public class TreeBuilderUtils {
  /**
   * Print a tree to a specified stream. This method is intended for debugging.
   *
   * @param tree The tree to print
   * @param printStream The stream to which to print
   * @param prefix Any prefix that should be prepended to each line.
   */
  public static void printTree(TreeScanNode tree, PrintStream printStream, String prefix) {
    String treeClassName = tree.getClass().getSimpleName();
    if (tree instanceof ShowActionsMenuNode) {
      Iterator<TreeScanLeafNode> nodes = tree.getNodesList().iterator();
      if (nodes.hasNext()) {
        Rect rect = nodes.next().getRectForNodeHighlight();
        printStream.println(prefix + treeClassName + " with rect: " + rect);
      }
      return;
    }
    printStream.println(prefix + treeClassName);
    if (tree instanceof TreeScanSelectionNode) {
      TreeScanSelectionNode selectionNode = (TreeScanSelectionNode) tree;
      for (int i = 0; i < selectionNode.getChildCount(); ++i) {
        printTree(selectionNode.getChild(i), printStream, prefix + "-");
      }
    }
  }
}
