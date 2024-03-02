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

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Calculates the utility bounds of the node. If node is not supposed to get accessibility focus the
 * utility bounds is calculated on the base of minimum rect that contains all accessibility
 * focusable nodes inside node hierarchy rooted by this node.
 */
public class NodeCachedBoundsCalculator {

  private static final String TAG = "NodeCachedBoundsCalculator";

  private static final Rect EMPTY_RECT = new Rect();

  private final Map<AccessibilityNodeInfoCompat, Rect> boundsMap = new HashMap<>();
  private final Set<AccessibilityNodeInfoCompat> calculatingNodes = new HashSet<>();
  private final Rect tempRect = new Rect();
  private Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache;

  public void setSpeakingNodesCache(Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache) {
    this.speakingNodesCache = speakingNodesCache;
  }

  public @Nullable Rect getBounds(AccessibilityNodeInfoCompat node) {
    Rect bounds = getBoundsInternal(node);
    if (bounds.equals(EMPTY_RECT)) {
      return null;
    }

    return bounds;
  }

  private Rect getBoundsInternal(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return EMPTY_RECT;
    }

    if (calculatingNodes.contains(node)) {
      LogUtils.w(TAG, "node tree loop detected while calculating node bounds");
      return EMPTY_RECT;
    }

    Rect bounds = boundsMap.get(node);
    if (bounds == null) {
      calculatingNodes.add(node);
      bounds = fetchBound(node);
      boundsMap.put(node, bounds);
      calculatingNodes.remove(node);
    }

    return bounds;
  }

  private Rect fetchBound(AccessibilityNodeInfoCompat node) {
    if (node == null || !AccessibilityNodeInfoUtils.isVisible(node)) {
      return EMPTY_RECT;
    }

    if (AccessibilityNodeInfoUtils.shouldFocusNode(node, speakingNodesCache)) {
      Rect bounds = new Rect();
      node.getBoundsInScreen(bounds);
      return bounds;
    }

    int childCount = node.getChildCount();
    int minTop = Integer.MAX_VALUE;
    int minLeft = Integer.MAX_VALUE;
    int maxBottom = Integer.MIN_VALUE;
    int maxRight = Integer.MIN_VALUE;
    AccessibilityNodeInfoCompat child = null;
    boolean hasChildBounds = false;
    for (int i = 0; i < childCount; i++) {
      try {
        child = node.getChild(i);
        Rect bounds = getBoundsInternal(child);
        if (!bounds.equals(EMPTY_RECT)) {
          hasChildBounds = true;
          if (bounds.top < minTop) {
            minTop = bounds.top;
          }

          if (bounds.left < minLeft) {
            minLeft = bounds.left;
          }

          if (bounds.right > maxRight) {
            maxRight = bounds.right;
          }

          if (bounds.bottom > maxBottom) {
            maxBottom = bounds.bottom;
          }
        }
      } finally {
      }
    }

    Rect bounds = new Rect();
    node.getBoundsInScreen(bounds);
    if (hasChildBounds) {
      bounds.top = Math.max(minTop, bounds.top);
      bounds.left = Math.max(minLeft, bounds.left);
      bounds.right = Math.min(maxRight, bounds.right);
      bounds.bottom = Math.min(maxBottom, bounds.bottom);
    }

    return bounds;
  }

  /**
   * If node is not supposed to be accessibility focused by TalkBack NodeBoundsCalculator calculates
   * useful bounds of focusable children. The method checks if the node uses its children useful
   * bounds or uses its own bounds
   */
  public boolean usesChildrenBounds(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    Rect bounds = getBounds(node);
    if (bounds == null) {
      return false;
    }

    node.getBoundsInScreen(tempRect);
    return !tempRect.equals(bounds);
  }
}
