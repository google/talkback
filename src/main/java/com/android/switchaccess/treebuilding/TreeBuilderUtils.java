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

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;

import com.android.switchaccess.AccessibilityNodeActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.OptionScanSelectionNode;
import com.android.utils.traversal.OrderedTraversalController;

import java.io.PrintStream;
import java.util.*;

/**
 * Useful methods for debugging tree builders
 */
public class TreeBuilderUtils {
    /**
     * Print a tree to a specified stream. This method is intended for debugging.
     *
     * @param tree The tree to print
     * @param printStream The stream to which to print
     * @param prefix Any prefix that should be prepended to each line.
     */
    @SuppressWarnings("unused")
    public static void printTree(OptionScanNode tree, PrintStream printStream, String prefix) {
        String treeClassName = tree.getClass().getSimpleName();
        if (tree instanceof AccessibilityNodeActionNode) {
            Iterator<Rect> rects = tree.getRectsForNodeHighlight().iterator();
            if (rects.hasNext()) {
                Rect rect = rects.next();
                printStream.println(prefix + treeClassName + " with rect: " + rect.toString());
            }
            return;
        }
        printStream.println(prefix + treeClassName);
        if (tree instanceof OptionScanSelectionNode) {
            OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) tree;
            for (int i = 0; i < selectionNode.getChildCount(); ++i) {
                printTree(selectionNode.getChild(i), printStream, prefix + "-");
            }
        }
    }
}
