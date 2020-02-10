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
import com.google.android.accessibility.utils.WebInterfaceUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Children nodes iterator that iterates its children according the order of AccessibilityNodeInfo
 * hierarchy. But for nodes that are not considered to be focused according to
 * AccessibilityNodeInfoUtils.shouldFocusNode() rules we calculate new bounds that is minimum
 * rectangle that contains all focusable children nodes. If that rectangle differs from real node
 * bounds that node is reordered according needSwapNodeOrder() logic and could be traversed later.
 *
 * <p>This class obtains new instances of AccessibilityNodeCompat. Call recycle to recycle those
 * instances. Do not use the iterator once it's been recycled.
 */
public class ReorderedChildrenIterator implements Iterator<AccessibilityNodeInfoCompat> {

  public static ReorderedChildrenIterator createAscendingIterator(
      AccessibilityNodeInfoCompat parent) {
    return createAscendingIterator(parent, null);
  }

  public static ReorderedChildrenIterator createDescendingIterator(
      AccessibilityNodeInfoCompat parent) {
    return createDescendingIterator(parent, null);
  }

  public static ReorderedChildrenIterator createAscendingIterator(
      AccessibilityNodeInfoCompat parent, @Nullable NodeCachedBoundsCalculator boundsCalculator) {
    if (parent == null) {
      return null;
    }

    return new ReorderedChildrenIterator(parent, true, boundsCalculator);
  }

  public static ReorderedChildrenIterator createDescendingIterator(
      AccessibilityNodeInfoCompat parent, @Nullable NodeCachedBoundsCalculator boundsCalculator) {
    if (parent == null) {
      return null;
    }

    return new ReorderedChildrenIterator(parent, false, boundsCalculator);
  }

  private final AccessibilityNodeInfoCompat mParent;
  private int mCurrentIndex;
  private final List<AccessibilityNodeInfoCompat> mNodes;
  private final boolean mIsAscending;
  private final boolean mRightToLeft = false; // TODO: Refactor to get RTL state.
  private final NodeCachedBoundsCalculator mBoundsCalculator;

  // Avoid constantly creating and discarding Rects.
  private final Rect mTempLeftBounds = new Rect();
  private final Rect mTempRightBounds = new Rect();

  private ReorderedChildrenIterator(
      AccessibilityNodeInfoCompat parent,
      boolean isAscending,
      @Nullable NodeCachedBoundsCalculator boundsCalculator) {
    mParent = parent;
    mIsAscending = isAscending;
    mBoundsCalculator =
        (boundsCalculator == null) ? new NodeCachedBoundsCalculator() : boundsCalculator;

    mNodes = new ArrayList<>(mParent.getChildCount());
    init(mParent);
    mCurrentIndex = mIsAscending ? 0 : mNodes.size() - 1;
  }

  private void init(AccessibilityNodeInfoCompat node) {
    fillNodesFromParent();
    if (!WebInterfaceUtils.isWebContainer(node) && needReordering(mNodes)) {
      reorder(mNodes);
    }
  }

  private boolean needReordering(List<AccessibilityNodeInfoCompat> nodes) {
    if (nodes == null || nodes.size() == 1) {
      return false;
    }

    for (AccessibilityNodeInfoCompat node : nodes) {
      if (mBoundsCalculator.usesChildrenBounds(node)) {
        return true;
      }
    }

    return false;
  }

  private void reorder(List<AccessibilityNodeInfoCompat> nodes) {
    if (nodes == null || nodes.size() == 1) {
      return;
    }

    int size = nodes.size();
    AccessibilityNodeInfoCompat[] nodeArray = new AccessibilityNodeInfoCompat[size];
    nodes.toArray(nodeArray);

    int currentIndex = size - 2;
    while (currentIndex >= 0) {
      AccessibilityNodeInfoCompat currentNode = nodeArray[currentIndex];
      if (mBoundsCalculator.usesChildrenBounds(currentNode)) {
        moveNodeIfNecessary(nodeArray, currentIndex);
      }

      currentIndex--;
    }

    nodes.clear();
    nodes.addAll(Arrays.asList(nodeArray));
  }

  private void moveNodeIfNecessary(AccessibilityNodeInfoCompat[] nodeArray, int index) {
    int size = nodeArray.length;
    int nextIndex = index + 1;
    AccessibilityNodeInfoCompat currentNode = nodeArray[index];
    while (nextIndex < size && needSwapNodeOrder(currentNode, nodeArray[nextIndex])) {
      nodeArray[nextIndex - 1] = nodeArray[nextIndex];
      nodeArray[nextIndex] = currentNode;
      nextIndex++;
    }
  }

  private boolean needSwapNodeOrder(
      AccessibilityNodeInfoCompat leftNode, AccessibilityNodeInfoCompat rightNode) {
    if (leftNode == null || rightNode == null) {
      return false;
    }

    Rect leftBounds = mBoundsCalculator.getBounds(leftNode);
    Rect rightBounds = mBoundsCalculator.getBounds(rightNode);

    // Sometimes the bounds compare() is overzealous, so swap the items only if the adjusted
    // (mBoundsCalculator) leftBounds > rightBounds but the original leftBounds < rightBounds,
    // i.e. the compare() method returns the existing ordering for the original bounds but
    // wants a swap for the adjusted bounds.
    // Simply, if compare() says that the original system ordering is wrong, then we cannot
    // trust its judgment in the adjusted bounds case.
    //
    // Example:
    // (1) Page scrolled to top  (2) Page scrolled to bottom.
    // +----------+              +----------+
    // | App bar  |              | App bar  |
    // +----------+              +----------+
    // | Item 1   |              | Item 2   |
    // | Item 2   |              | Item 3   |
    // | Item 3   |              | (spacer) |
    // +----------+              +----------+
    // Note: App bar overlays the top part of the list; the top, left, and right edges of the
    // list line up with the app bar. Assume that the spacer is not important for accessibility.
    // In this example, the traversal order for (1) is Item 1 -> Item 2 -> Item 3 -> App bar
    // but the traversal order for (2) gets reordered to App bar -> Item 2 -> Item 3.
    // So during auto-scrolling the app bar is actually excluded from the traversal order until
    // after the wrap-around.
    if (compare(leftBounds, rightBounds) > 0) {
      leftNode.getBoundsInScreen(mTempLeftBounds);
      rightNode.getBoundsInScreen(mTempRightBounds);
      return compare(mTempLeftBounds, mTempRightBounds) < 0;
    }

    return false;
  }

  /**
   * Returns a negative value if the inputs are ordered {@code {leftBounds, rightBounds}} and a
   * positive value if the inputs are ordered {@code {rightBounds, leftBounds}}. Guaranteed to not
   * return 0.
   *
   * <p>The ordering is determined via an algorithm similar to the {@link
   * android.view.ViewGroup.ViewLocationHolder#COMPARISON_STRATEGY_STRIPE} strategy used by the
   * framework to sort children of ViewGroups. This is essentially copied from {@link
   * android.view.ViewGroup.ViewLocationHolder#compareTo} with minor modifications.
   */
  private int compare(@Nullable Rect leftBounds, @Nullable Rect rightBounds) {
    if (leftBounds == null || rightBounds == null) {
      return -1;
    }

    // First is above second.
    if (leftBounds.bottom - rightBounds.top <= 0) {
      return -1;
    }
    // First is below second.
    if (leftBounds.top - rightBounds.bottom >= 0) {
      return 1;
    }

    // We are ordering left-to-right, top-to-bottom.
    if (mRightToLeft) {
      final int rightDifference = leftBounds.right - rightBounds.right;
      if (rightDifference != 0) {
        return -rightDifference;
      }
    } else { // LTR
      final int leftDifference = leftBounds.left - rightBounds.left;
      if (leftDifference != 0) {
        return leftDifference;
      }
    }
    // We are ordering left-to-right, top-to-bottom.
    final int topDifference = leftBounds.top - rightBounds.top;
    if (topDifference != 0) {
      return topDifference;
    }
    // Break tie by height.
    final int heightDifference = leftBounds.height() - rightBounds.height();
    if (heightDifference != 0) {
      return -heightDifference;
    }
    // Break tie by width.
    final int widthDifference = leftBounds.width() - rightBounds.width();
    if (widthDifference != 0) {
      return -widthDifference;
    }
    // Break tie somehow.
    return -1;
  }

  public void recycle() {
    AccessibilityNodeInfoUtils.recycleNodes(mNodes);
    mNodes.clear();
  }

  private void fillNodesFromParent() {
    int count = mParent.getChildCount();
    for (int i = 0; i < count; i++) {
      AccessibilityNodeInfoCompat node = mParent.getChild(i);
      if (node != null) {
        mNodes.add(node);
      }
    }
  }

  @Override
  public boolean hasNext() {
    return mIsAscending ? mCurrentIndex < mNodes.size() : mCurrentIndex >= 0;
  }

  /** Caller must recycle returned AccessibilityNodeInfoCompat. */
  @Override
  public AccessibilityNodeInfoCompat next() {
    AccessibilityNodeInfoCompat nextNode = mNodes.get(mCurrentIndex);
    if (mIsAscending) {
      mCurrentIndex++;
    } else {
      mCurrentIndex--;
    }

    return nextNode != null ? AccessibilityNodeInfoCompat.obtain(nextNode) : null;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException(
        "ReorderedChildrenIterator does not support remove operation");
  }
}
