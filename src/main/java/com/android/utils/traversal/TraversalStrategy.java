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

import java.util.Map;

/**
 * Strategy the is defined an order of traversing through the nodes of AccessibilityNodeInfo
 * hierarchy
 */
public interface TraversalStrategy {

    public static final int SEARCH_FOCUS_FORWARD = 1;
    public static final int SEARCH_FOCUS_BACKWARD = -1;

    /**
     * The method searches next node to be focused
     * @param startNode - pivot node the search is start from
     * @param direction - direction to find focus
     * @return {@link android.support.v4.view.accessibility.AccessibilityNodeInfoCompat} node
     * that has next focus
     */
    public AccessibilityNodeInfoCompat findFocus(AccessibilityNodeInfoCompat startNode,
                                                 int direction);

    /**
     * Finds the first focusable accessibility node in hierarchy started from root node
     * @param root - root node
     * @return returns the first node that could be focused
     */
    public AccessibilityNodeInfoCompat focusFirst(AccessibilityNodeInfoCompat root);

    /**
     * Finds the last focusable accessibility node in hierarchy started from root node
     * @param root - root node
     * @return returns the last node that could be focused
     */
    public AccessibilityNodeInfoCompat focusLast(AccessibilityNodeInfoCompat root);

    /**
     * Calculating if node is speaking node according to AccessibilityNodeInfoUtils.isSpeakingNode()
     * method is time consuming. Traversal strategy may use cache for already calculated values.
     * If traversal strategy does not need in such cache use it could return null.
     * @return speaking node cache map. Could be null if cache is not used by traversal strategy
     */
    public Map<AccessibilityNodeInfoCompat, Boolean> getSpeakingNodesCache();

    /**
     * When there is no need in traversal strategy object it must be recycled before
     * garbage collected
     */
    public void recycle();
}
