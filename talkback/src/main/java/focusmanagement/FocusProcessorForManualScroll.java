/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;

/** Handles the use case when a node is scrolled by dragging two fingers on screen. */
public class FocusProcessorForManualScroll extends FocusProcessor {

  private final FocusManagerInternal mFocusManagerInternal;

  public FocusProcessorForManualScroll(FocusManagerInternal focusManagerInternal) {
    mFocusManagerInternal = focusManagerInternal;
  }

  @Override
  public void onNodeManuallyScrolled(
      AccessibilityNodeInfoCompat scrolledNode,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {
    AccessibilityNodeInfoCompat currentA11yFocusedNode = null;
    AccessibilityNodeInfoCompat lastA11yFocusedNode = null;
    AccessibilityNodeInfoCompat nodeToFocus = null;
    try {
      currentA11yFocusedNode = mFocusManagerInternal.getAccessibilityFocus();
      if (AccessibilityNodeInfoUtils.shouldFocusNode(currentA11yFocusedNode)) {
        return;
      }

      lastA11yFocusedNode = mFocusManagerInternal.getLastAccessibilityFocus();
      if (lastA11yFocusedNode == null) {
        return;
      }

      // When a child node is hidden from a scrollable parent, the parent-child relationship
      // sometimes is not cleared completely. We should check on both directions to determine if the
      // last focused node is a descendant of the scrolled node.
      if (!AccessibilityNodeInfoUtils.hasAncestor(lastA11yFocusedNode, scrolledNode)
          && !AccessibilityNodeInfoUtils.hasDescendant(scrolledNode, lastA11yFocusedNode)) {
        return;
      }

      // Try to focus on the next/previous focusable node.
      // TODO: Shall we use lastA11yFocusedNode as the pivot to traverse through the tree?
      nodeToFocus = findChildFromNode(scrolledNode, direction);
      if (nodeToFocus == null) {
        return;
      }

      FocusActionInfo focusActionInfo =
          new FocusActionInfo.Builder().setSourceAction(FocusActionInfo.MANUAL_SCROLL).build();

      mFocusManagerInternal.setAccessibilityFocus(
          nodeToFocus, /* forceRefocusIfAlreadyFocused= */ false, focusActionInfo, eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(
          currentA11yFocusedNode, lastA11yFocusedNode, nodeToFocus);
    }
  }

  // TODO: Remove the legacy implementation of AccessibilityEventListener;
  @Override
  public int getEventTypes() {
    return 0;
  }

  // TODO: Remove the legacy implementation of AccessibilityEventListener;
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    // Do nothing.
  }

  /**
   * Returns the first focusable child found while traversing the child of the specified node in a
   * specific direction. Only traverses direct children.
   *
   * <p><strong>Note: </strong> Caller is responsible for recycling the root node.
   *
   * @param root The node to search within.
   * @param direction The direction to search, one of the {@link TraversalStrategy.SearchDirection}
   *     constants.
   * @return The first focusable child encountered in the specified direction.
   */
  private static AccessibilityNodeInfoCompat findChildFromNode(
      AccessibilityNodeInfoCompat root, @TraversalStrategy.SearchDirection int direction) {
    if (root == null || root.getChildCount() == 0) {
      return null;
    }

    final TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(root, direction);

    AccessibilityNodeInfoCompat pivotNode = traversalStrategy.focusInitial(root, direction);

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null
                && AccessibilityNodeInfoUtils.shouldFocusNode(
                    node, traversalStrategy.getSpeakingNodesCache());
          }
        };

    if (filter.accept(pivotNode)) {
      return pivotNode;
    }
    try {
      return TraversalStrategyUtils.searchFocus(traversalStrategy, pivotNode, direction, filter);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(pivotNode);
    }
  }
}
