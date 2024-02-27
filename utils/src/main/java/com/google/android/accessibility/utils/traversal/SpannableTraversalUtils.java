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

import android.text.SpannableString;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.SpannableUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utility methods for traversing a tree with spannable objects. */
public class SpannableTraversalUtils {

  /** Return whether the tree description of node contains target spans. */
  public static boolean hasTargetSpanInNodeTreeDescription(
      AccessibilityNodeInfoCompat node, Class<?> targetSpanClass) {
    if (node == null) {
      return false;
    }
    Set<AccessibilityNodeInfoCompat> visitedNode = new HashSet<>();
    try {
      return searchSpannableStringsInNodeTree(
          node, // Root node.
          visitedNode, // Visited nodes.
          null, // Result list. No need to collect result here.
          targetSpanClass // Target span class
          );
    } finally {
    }
  }

  /** Collects SpannableStrings with target span within the node tree description. */
  public static void collectSpannableStringsWithTargetSpanInNodeDescriptionTree(
      AccessibilityNodeInfoCompat node,
      Class<?> targetSpanClass,
      @NonNull List<SpannableString> result) {
    if (node == null) {
      return;
    }
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    searchSpannableStringsInNodeTree(
        node, // Root node.
        visitedNodes, // Visited nodes.
        result, // List of SpannableStrings collected.
        targetSpanClass // Target span class
        );
  }

  /**
   * Search for SpannableStrings under <strong>node description tree</strong> of {@code root}.
   * <strong>Note:</strong> {@code root} will be added to {@code visitedNodes} if it's not null.
   *
   * @param root Root of node tree.
   * @param visitedNodes Set of {@link AccessibilityNodeInfoCompat} to record visited nodes, used to
   *     avoid loops.
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
      // Root already visited. Stop searching.
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
      }
      if (containsSpannableDescendents && result == null) {
        return true;
      }
    }
    return hasSpannableString || containsSpannableDescendents;
  }
}
