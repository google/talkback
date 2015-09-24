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
import com.android.utils.AccessibilityNodeInfoRef;

import java.util.Map;

public class SimpleTraversalStrategy implements TraversalStrategy {

    @Override
    public AccessibilityNodeInfoCompat findFocus(AccessibilityNodeInfoCompat startNode,
                                                 int direction) {
        if (startNode == null) {
            return null;
        }

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(startNode);
        boolean focusFound = direction == SEARCH_FOCUS_FORWARD ?
                ref.nextInOrder() : ref.previousInOrder();
        if (focusFound) {
            return ref.get();
        }

        ref.recycle();
        return null;
    }

    @Override
    public AccessibilityNodeInfoCompat focusFirst(AccessibilityNodeInfoCompat root) {
        if (root == null) {
            return null;
        }

        return AccessibilityNodeInfoCompat.obtain(root);
    }

    @Override
    public AccessibilityNodeInfoCompat focusLast(AccessibilityNodeInfoCompat root) {
        if (root == null) {
            return null;
        }

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(root);
        if (ref.lastDescendant()) {
            return ref.get();
        }

        ref.recycle();
        return null;
    }

    @Override
    public Map<AccessibilityNodeInfoCompat, Boolean> getSpeakingNodesCache() {
        return null;
    }

    @Override
    public void recycle() {
    }
}
