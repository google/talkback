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

package com.android.utils.traversal;

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.WebInterfaceUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Children nodes iterator that iterates its children according the order of AccessibilityNodeInfo
 * hierarchy. But for nodes that are not considered to be focused according to
 * AccessibilityNodeInfoUtils.shouldFocusNode() rules we calculate new bounds that is minimum
 * rectangle that contains all focusable children nodes. If that rectangle differs from
 * real node bounds that node is reordered according needSwipeNodes() logic and could be
 * traversed later.
 *
 * This class obtains new instances of AccessibilityNodeCompat. Call recycle to recycle those
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
            AccessibilityNodeInfoCompat parent, NodeCachedBoundsCalculator boundsCalculator) {
        if (parent == null) {
            return null;
        }

        return new ReorderedChildrenIterator(parent, true, boundsCalculator);
    }

    public static ReorderedChildrenIterator createDescendingIterator(
            AccessibilityNodeInfoCompat parent, NodeCachedBoundsCalculator boundsCalculator) {
        if (parent == null) {
            return null;
        }

        return new ReorderedChildrenIterator(parent, false, boundsCalculator);
    }

    private AccessibilityNodeInfoCompat mParent;
    private int mCurrentIndex;
    private List<AccessibilityNodeInfoCompat> mNodes;
    private boolean mIsAscending;
    private NodeCachedBoundsCalculator mBoundsCalculator;

    private ReorderedChildrenIterator(AccessibilityNodeInfoCompat parent, boolean isAscending,
                                      NodeCachedBoundsCalculator boundsCalculator) {
        mParent = parent;
        mIsAscending = isAscending;
        mBoundsCalculator = boundsCalculator;
        if (boundsCalculator == null) {
            mBoundsCalculator = new NodeCachedBoundsCalculator();
        }

        mNodes = new ArrayList<>(mParent.getChildCount());
        init(mParent);
        mCurrentIndex = mIsAscending ? 0 : mNodes.size() - 1;
    }

    private void init(AccessibilityNodeInfoCompat node) {
        fillNodesFromParent();
        if(!WebInterfaceUtils.isWebContainer(node) && needReordering(mNodes)) {
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
        while (nextIndex < size && needSwipeNodes(currentNode, nodeArray[nextIndex])) {
            nodeArray[nextIndex - 1] = nodeArray[nextIndex];
            nodeArray[nextIndex] = currentNode;
            nextIndex++;
        }
    }

    private boolean needSwipeNodes(AccessibilityNodeInfoCompat leftNode,
                                   AccessibilityNodeInfoCompat rightNode) {
        if (leftNode == null || rightNode == null) {
            return false;
        }

        Rect leftBounds = mBoundsCalculator.getBounds(leftNode);
        Rect rightBounds = mBoundsCalculator.getBounds(rightNode);
        if (leftBounds == null || rightBounds == null) {
            return true;
        }

        if (leftBounds.top != rightBounds.top) {
            return leftBounds.top > rightBounds.top;
        }

        if (leftBounds.left != rightBounds.left) {
            return leftBounds.left > rightBounds.top;
        }

        if (leftBounds.right != rightBounds.right) {
            return leftBounds.right > rightBounds.right;
        }

        return leftBounds.bottom > rightBounds.bottom;
    }

    public void recycle() {
        AccessibilityNodeInfoUtils.recycleNodes(mNodes);
        mNodes = null;
    }

    private void fillNodesFromParent() {
        int count = mParent.getChildCount();
        for(int i = 0; i < count; i++) {
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
