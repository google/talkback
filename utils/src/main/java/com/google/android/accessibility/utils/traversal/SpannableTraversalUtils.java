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
import android.text.style.ClickableSpan;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utility methods for traversing a tree with spannable objects. */
public class SpannableTraversalUtils {

  private static final String TAG = "SpannableTraversalUtils";

  /**
   * Returns whether the node hierarchy contains target {@link ClickableSpan}.
   *
   * <p><b>Note: {@code targetClickableSpanClass} should be able to be parcelable and transmitted by
   * IPC which depends on the implementation of {@link AccessibilityNodeInfo#setText(CharSequence)}
   * in the framework side.</b>
   */
  public static boolean hasTargetClickableSpanInNodeTree(
      AccessibilityNodeInfoCompat node, Class<? extends ClickableSpan> targetClickableSpanClass) {
    if (node == null) {
      return false;
    }
    Set<AccessibilityNodeInfoCompat> visitedNode = new HashSet<>();
    return searchSpannableStringsForClickableSpanInNodeTree(
        node, visitedNode, /* result= */ null, targetClickableSpanClass);
  }

  /**
   * Gets {@link SpannableString} with target {@link ClickableSpan} within the node tree.
   *
   * <p><b>Note: {@code targetClickableSpanClass} should be able to be parcelable and transmitted by
   * IPC which depends on the implementation of {@link AccessibilityNodeInfo#setText(CharSequence)}
   * in the framework side.</b>
   */
  public static void getSpannableStringsWithTargetClickableSpanInNodeTree(
      AccessibilityNodeInfoCompat node,
      Class<? extends ClickableSpan> targetClickableSpanClass,
      @NonNull List<SpannableString> result) {
    if (node == null) {
      return;
    }
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    searchSpannableStringsForClickableSpanInNodeTree(
        node, visitedNodes, result, targetClickableSpanClass);
  }

  /**
   * Search for {@link SpannableString} under <strong>node tree</strong> from {@code root}.
   *
   * <p><b>Note: {@code root} will be added to {@code visitedNodes} if it's not null.</b>
   *
   * <p><b>Note: {@code targetClickableSpanClass} should be able to be parcelable and transmitted by
   * IPC which depends on the implementation of {@link AccessibilityNodeInfo#setText(CharSequence)}
   * in the framework side.</b>
   *
   * @param root root of node tree
   * @param visitedNodes a set of {@link AccessibilityNodeInfoCompat} to record visited nodes, used
   *     to avoid loops.
   * @param result a list of {@link SpannableString} collected from node tree
   * @param targetClickableSpanClass the class of target ClickableSpan.
   * @return {@code true} if any SpannableString is found in the node tree
   */
  @CanIgnoreReturnValue
  private static boolean searchSpannableStringsForClickableSpanInNodeTree(
      AccessibilityNodeInfoCompat root,
      @NonNull Set<AccessibilityNodeInfoCompat> visitedNodes,
      @Nullable List<SpannableString> result,
      Class<? extends ClickableSpan> targetClickableSpanClass) {
    if (root == null) {
      return false;
    }
    if (!visitedNodes.add(root)) {
      // Root already visited. Stop searching.
      return false;
    }
    SpannableString string =
        SpannableUtils.getSpannableStringWithTargetClickableSpan(root, targetClickableSpanClass);
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
      LogUtils.v(TAG, "Root has content description, skipping searching the children nodes.");
      return hasSpannableString;
    }
    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createAscendingIterator(root);
    boolean containsSpannableDescendents = false;
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat child = iterator.next();
      if (AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(child)) {
        containsSpannableDescendents |=
            searchSpannableStringsForClickableSpanInNodeTree(
                child, visitedNodes, result, targetClickableSpanClass);
      }
      if (containsSpannableDescendents && result == null) {
        return true;
      }
    }
    return hasSpannableString || containsSpannableDescendents;
  }
}
