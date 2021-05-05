/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils.traversal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.SpannableUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utility methods for traversing a tree with spannable objects. */
public class SpannableTraversalUtils {

  /**
   * Return whether the tree description of node contains target spans. Caller should recycle {@code
   * node}.
   */
  public static boolean hasTargetSpanInNodeTreeDescription(
      AccessibilityNodeInfoCompat node, Class<?> targetSpanClass) {
    if (node == null) {
      return false;
    }
    Set<AccessibilityNodeInfoCompat> visitedNode = new HashSet<>();
    try {
      return searchSpannableStringsInNodeTree(
          AccessibilityNodeInfoUtils.obtain(node), // Root node. Will be recycled in visitedNodes.
          visitedNode, // Visited nodes. Should be recycled.
          null, // Result list. No need to collect result here.
          targetSpanClass // Target span class
          );
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(visitedNode);
    }
  }

  /**
   * Collects SpannableStrings with target span within the node tree description. Caller should
   * recycle {@code node}.
   */
  public static void collectSpannableStringsWithTargetSpanInNodeDescriptionTree(
      AccessibilityNodeInfoCompat node,
      Class<?> targetSpanClass,
      @NonNull List<SpannableString> result) {
    if (node == null) {
      return;
    }
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    searchSpannableStringsInNodeTree(
        AccessibilityNodeInfoCompat.obtain(node), // Root node. Will be recycled in visitedNodes.
        visitedNodes, // Visited nodes. Should be recycled.
        result, // List of SpannableStrings collected.
        targetSpanClass // Target span class
        );
    AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
  }

  /**
   * Search for SpannableStrings under <strong>node description tree</strong> of {@code root}.
   * <strong>Note:</strong> {@code root} will be added to {@code visitedNodes} if it's not null.
   * Caller should recycle {@root visitedNodes}.
   *
   * @param root Root of node tree. Caller does not need to recycle this node.
   * @param visitedNodes Set of {@link AccessibilityNodeInfoCompat} to record visited nodes, used to
   *     avoid loops. Caller should recycle this node set.
   * @param result List of SpannableStrings collected.
   * @param targetSpanClass Class of target span.
   * @return true if any SpannableString is found in the description tree.
   */
  private static boolean searchSpannableStringsInNodeTree(
      AccessibilityNodeInfoCompat root,
      @NonNull Set<AccessibilityNodeInfoCompat> visitedNodes,
      @Nullable List<SpannableString> result,
      Class<?> targetSpanClass) {
    if (root == null) {
      return false;
    }
    if (!visitedNodes.add(root)) {
      // Root already visited. Recycle root node and stop searching.
      root.recycle();
      return false;
    }
    SpannableString string = SpannableUtils.getStringWithTargetSpan(root, targetSpanClass);
    boolean hasSpannableString = !TextUtils.isEmpty(string);
    if (hasSpannableString) {
      if (result == null) {
        // If we don't need to collect result and we found a Spannable String, return true.
        return true;
      } else {
        result.add(string);
      }
    }

    // TODO: Check if we should search descendents of web content node.
    if (!TextUtils.isEmpty(root.getContentDescription())) {
      // If root has content description, do not search the children nodes.
      return hasSpannableString;
    }
    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createAscendingIterator(root);
    boolean containsSpannableDescendents = false;
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat child = iterator.next();
      if (AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(child)) {
        containsSpannableDescendents |=
            searchSpannableStringsInNodeTree(child, visitedNodes, result, targetSpanClass);
      } else {
        AccessibilityNodeInfoUtils.recycleNodes(child);
      }
      if (containsSpannableDescendents && result == null) {
        iterator.recycle();
        return true;
      }
    }
    iterator.recycle();
    return hasSpannableString || containsSpannableDescendents;
  }
}
