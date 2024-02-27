/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.utils;

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_LEFT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_RIGHT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.getSymbolicName;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.utils.traversal.DirectionalTraversalStrategy;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Wrapper around a scrollable {@link AccessibilityNodeInfoCompat}.
 *
 * <p>Facilitates fallbacks for search directions not natively supported by widgets.
 */
public class ScrollableNodeInfo {
  private final AccessibilityNodeInfoCompat node;
  private final boolean isRtl;
  private boolean supportsUpDownScrolling;
  private boolean supportsLeftRightScrolling;

  private static final String TAG = "ScrollableNodeInfo";

  /**
   * Creates a wrapper with the given scrollable {@code node}.
   *
   * @param node scrollable {@link AccessibilityNodeInfoCompat}
   * @param isRtl {@code true} if the ui locale is right-to-left. It is Required for mapping logical
   *     to spatial directions.
   */
  public ScrollableNodeInfo(@NonNull AccessibilityNodeInfoCompat node, boolean isRtl) {
    this.node = node;
    this.isRtl = isRtl;
    initSupportedDirections();
  }

  /** Returns the single node which is wrapped by the instance. */
  public @NonNull AccessibilityNodeInfoCompat getNode() {
    return node;
  }

  /**
   * Returns a supported {@code TraversalStrategy} for the specified {@code direction} if one
   * exists, and empty otherwise.
   *
   * @param direction {@link SearchDirection} for the node. The actual returned strategy may use a
   *     different, fallback direction.
   * @param focusFinder Reference to a {@link FocusFinder} that is needed to create a {@link
   *     DirectionalTraversalStrategy}.
   */
  public @Nullable TraversalStrategy getSupportedTraversalStrategy(
      @SearchDirection int direction, @NonNull FocusFinder focusFinder) {
    Integer supportedDirection = getSupportedScrollDirection(direction);
    if (supportedDirection == null) {
      return null;
    }
    if (TraversalStrategyUtils.isLogicalDirection(supportedDirection)) {
      return getLogicalTraversalStrategy();
    }
    if (TraversalStrategyUtils.isSpatialDirection(supportedDirection)) {
      return getSpatialTraversalStrategy(focusFinder);
    }
    return null;
  }

  /**
   * Returns a supported {@link SearchDirection} that is equivalent to the specified {@code
   * searchDirection} if one exists, and {@code null} otherwise.
   */
  public @Nullable Integer getSupportedScrollDirection(@SearchDirection int searchDirection) {
    Integer nativeDirection = getDirectionIfNativelySupported(searchDirection);
    if (nativeDirection != null) {
      return nativeDirection;
    }
    // fallback
    if (TraversalStrategyUtils.isLogicalDirection(searchDirection)) {
      if (supportsUpDownScrolling && supportsLeftRightScrolling) {
        // If two axes can be scrolled, we would not know which one to scroll.
        return null;
      }
      if (supportsUpDownScrolling) {
        return getDirectionIfNativelySupported(
            searchDirection == SEARCH_FOCUS_FORWARD ? SEARCH_FOCUS_DOWN : SEARCH_FOCUS_UP);
      }
      if (supportsLeftRightScrolling) {
        @SearchDirection int forward = isRtl ? SEARCH_FOCUS_LEFT : SEARCH_FOCUS_RIGHT;
        @SearchDirection int backward = isRtl ? SEARCH_FOCUS_RIGHT : SEARCH_FOCUS_LEFT;
        return getDirectionIfNativelySupported(
            searchDirection == SEARCH_FOCUS_FORWARD ? forward : backward);
      }
    }
    if (TraversalStrategyUtils.isSpatialDirection(searchDirection)) {
      return getDirectionIfNativelySupported(
          TraversalStrategyUtils.getLogicalDirection(searchDirection, isRtl));
    }

    return null;
  }

  private @Nullable Integer getDirectionIfNativelySupported(@SearchDirection int searchDirection) {
    int desiredAction =
        TraversalStrategyUtils.convertSearchDirectionToScrollAction(searchDirection);
    for (AccessibilityActionCompat action : node.getActionList()) {
      if (action.getId() == desiredAction) {
        return searchDirection;
      }
    }
    return null;
  }

  private void initSupportedDirections() {
    supportsUpDownScrolling = false;
    supportsLeftRightScrolling = false;
    for (AccessibilityActionCompat action : node.getActionList()) {
      if (action.equals(ACTION_SCROLL_UP) || action.equals(ACTION_SCROLL_DOWN)) {
        supportsUpDownScrolling = true;
      }
      if (action.equals(ACTION_SCROLL_LEFT) || action.equals(ACTION_SCROLL_RIGHT)) {
        supportsLeftRightScrolling = true;
      }
    }
  }

  private @NonNull TraversalStrategy getLogicalTraversalStrategy() {
    return new OrderedTraversalStrategy(AccessibilityNodeInfoUtils.getRoot(node));
  }

  private @NonNull TraversalStrategy getSpatialTraversalStrategy(@NonNull FocusFinder focusFinder) {
    return new DirectionalTraversalStrategy(AccessibilityNodeInfoUtils.getRoot(node), focusFinder);
  }

  /**
   * Returns a {@link ScrollableNodeInfo} for a node that is an ancestor of {@code pivot} and can be
   * scrolled in the specified {@code direction} or an equivalent fallback direction if one exists,
   * and {@code null} otherwise.
   *
   * @param direction The direction in which a scroll is requested.
   * @param pivot The node for which an ancestor is searched.
   * @param includeSelf Whether the {@code pivot} is allowed to be the ancestor.
   * @param isRtl Whether the window has RTL direction. This is required to map logical to spatial
   *     direction and vice-versa.
   */
  public static @Nullable ScrollableNodeInfo findScrollableNodeForDirection(
      @SearchDirectionOrUnknown int direction,
      @NonNull AccessibilityNodeInfoCompat pivot,
      boolean includeSelf,
      boolean isRtl) {
    if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return null;
    }
    if (includeSelf) {
      ScrollableNodeInfo match = findMatchingScrollable(direction, pivot, isRtl);
      if (match != null) {
        return match;
      }
    }
    if (pivot.getParent() != null) {
      return findScrollableNodeForDirectionRecursive(direction, pivot.getParent(), isRtl);
    }
    return null;
  }

  private static @Nullable ScrollableNodeInfo findScrollableNodeForDirectionRecursive(
      @SearchDirection int direction, @NonNull AccessibilityNodeInfoCompat node, boolean isRtl) {
    ScrollableNodeInfo match = findMatchingScrollable(direction, node, isRtl);
    if (match != null) {
      return match;
    }
    if (node.getParent() != null) {
      return findScrollableNodeForDirectionRecursive(direction, node.getParent(), isRtl);
    }
    return null;
  }

  private static @Nullable ScrollableNodeInfo findMatchingScrollable(
      @SearchDirection int direction, @NonNull AccessibilityNodeInfoCompat node, boolean isRtl) {
    if (!FILTER_AUTO_SCROLL.accept(node)) {
      return null;
    }

    ScrollableNodeInfo scrollableNodeInfo = new ScrollableNodeInfo(node, isRtl);
    Integer supportedDirection = scrollableNodeInfo.getSupportedScrollDirection(direction);
    if (supportedDirection != null) {
      NodeActionFilter scrollableFilter =
          new NodeActionFilter(
              TraversalStrategyUtils.convertSearchDirectionToScrollAction(supportedDirection));
      if (scrollableFilter.accept(node)) {
        return scrollableNodeInfo;
      }
    } else {
      LogUtils.d(
          TAG,
          "findMatchingScrollable - supportedDirection is null, direction = %s",
          getSymbolicName(direction));
    }
    return null;
  }
}
