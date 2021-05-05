/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.utils.traversal;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Window could have its content views hierarchy. Views in that hierarchy could be traversed one
 * after another. Every view inside that hierarchy could change its natural traverse order by
 * setting traversal before/after view. See {@link android.view.View.getTraversalBefore()}, {@link
 * android.view.View.getTraversalAfter()}.
 *
 * <p>This strategy considers changes in the traverse order according to after/before view movements
 */
@SuppressWarnings("JavadocReference")
public class OrderedTraversalStrategy implements TraversalStrategy {

  @Nullable private AccessibilityNodeInfoCompat mRootNode;
  private final OrderedTraversalController mController;
  private final Map<AccessibilityNodeInfoCompat, Boolean> mSpeakingNodesCache;

  public OrderedTraversalStrategy(AccessibilityNodeInfoCompat rootNode) {
    if (rootNode != null) {
      mRootNode = AccessibilityNodeInfoCompat.obtain(rootNode);
    }

    mSpeakingNodesCache = new HashMap<>();
    mController = new OrderedTraversalController();
    mController.setSpeakNodesCache(mSpeakingNodesCache);
    mController.initOrder(mRootNode, false);
  }

  @Override
  public void recycle() {
    if (mRootNode != null) {
      mRootNode.recycle();
    }

    mController.recycle();
  }

  @Override
  public Map<AccessibilityNodeInfoCompat, Boolean> getSpeakingNodesCache() {
    return mSpeakingNodesCache;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat findFocus(
      AccessibilityNodeInfoCompat startNode, @SearchDirection int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        return focusNext(startNode);
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        return focusPrevious(startNode);
      default: // fall out
    }

    return null;
  }

  private @Nullable AccessibilityNodeInfoCompat focusNext(AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoCompat.obtain(node);
    AccessibilityNodeInfoCompat targetNode;
    try {
      targetNode = mController.findNext(rootNode);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(rootNode);
    }

    return targetNode;
  }

  private @Nullable AccessibilityNodeInfoCompat focusPrevious(AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoCompat.obtain(node);
    AccessibilityNodeInfoCompat targetNode;
    try {
      targetNode = mController.findPrevious(rootNode);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(rootNode);
    }

    return targetNode;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat focusInitial(
      AccessibilityNodeInfoCompat root, @SearchDirection int direction) {
    if (direction == SEARCH_FOCUS_FORWARD) {
      return mController.findFirst(root);
    } else if (direction == SEARCH_FOCUS_BACKWARD) {
      return mController.findLast(root);
    } else {
      return null;
    }
  }

  /** Dumps the traversal order tree. */
  public void dumpTree() {
    mController.dumpTree();
  }
}
