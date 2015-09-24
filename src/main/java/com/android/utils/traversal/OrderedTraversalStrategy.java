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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.utils.AccessibilityNodeInfoUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Window could have its content views hierarchy. Views in that hierarchy could be traversed one
 * after another. Every view inside that hierarchy could change its natural traverse order by
 * setting traversal before/after view. See
 * {@link android.view.View.getTraversalBefore()}, {@link android.view.View.getTraversalAfter()}.
 *
 * This strategy considers changes in the traverse order according to after/before view movements
 */
@SuppressWarnings("JavadocReference")
public class OrderedTraversalStrategy implements TraversalStrategy {

    private AccessibilityNodeInfoCompat mRootNode;
    private final OrderedTraversalController mController;
    private final Map<AccessibilityNodeInfoCompat, Boolean> mSpeakingNodesCache;

    public OrderedTraversalStrategy(AccessibilityNodeInfoCompat rootNode) {
        if (rootNode != null) {
            mRootNode = AccessibilityNodeInfoCompat.obtain(rootNode);
        }

        mSpeakingNodesCache = new HashMap<>();
        mController = new OrderedTraversalController();
        mController.setSpeakNodesCache(mSpeakingNodesCache);
        mController.initOrder(mRootNode);
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
    public AccessibilityNodeInfoCompat findFocus(AccessibilityNodeInfoCompat startNode,
                                                 int direction) {
        switch (direction) {
            case SEARCH_FOCUS_FORWARD:
                return focusNext(startNode);
            case SEARCH_FOCUS_BACKWARD:
                return focusPrevious(startNode);
        }

        return null;
    }

    private AccessibilityNodeInfoCompat focusNext(AccessibilityNodeInfoCompat node) {
        AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoCompat.obtain(node);
        AccessibilityNodeInfoCompat targetNode;
        try {
            targetNode = mController.findNext(rootNode);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(rootNode);
        }

        return targetNode;
    }

    private AccessibilityNodeInfoCompat focusPrevious(AccessibilityNodeInfoCompat node) {
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
    public AccessibilityNodeInfoCompat focusFirst(AccessibilityNodeInfoCompat root) {
        return mController.findFirst(root);
    }

    @Override
    public AccessibilityNodeInfoCompat focusLast(AccessibilityNodeInfoCompat root) {
        return mController.findLast(root);
    }
}
