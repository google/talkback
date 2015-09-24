/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.utils.AccessibilityEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SavedNode implements AccessibilityEventListener {
    private AccessibilityNodeInfoCompat mNode;
    private Selection mSelection;
    private CursorGranularity mGranularity;
    private Map<AccessibilityNodeInfoCompat, Selection> mSelectionCache = new HashMap<>();

    public void saveNodeState(AccessibilityNodeInfoCompat node, CursorGranularity granularity) {
        if (node == null) {
            return;
        }
        mNode = AccessibilityNodeInfoCompat.obtain(node);
        mGranularity = granularity;
        mSelection = findSelectionForNode(node);
    }

    public AccessibilityNodeInfoCompat getNode() {
        return mNode;
    }

    public CursorGranularity getGranularity() {
        return mGranularity;
    }

    public Selection getSelection() {
        return mSelection;
    }

    private void clear() {
        mNode = null;
        mSelection = null;
        mGranularity = null;
    }

    private void clearCache() {
        List<AccessibilityNodeInfoCompat> toRemove = new ArrayList<>();
        for (AccessibilityNodeInfoCompat node : mSelectionCache.keySet()) {
            boolean refreshed = refreshNode(node);
            if (!refreshed || !node.isVisibleToUser()) {
                toRemove.add(node);
            }
        }

        for (AccessibilityNodeInfoCompat node : toRemove) {
            mSelectionCache.remove(node);
            node.recycle();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean refreshNode(AccessibilityNodeInfoCompat node) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                ((AccessibilityNodeInfo) node.getInfo()).refresh();

    }

    private Selection findSelectionForNode(AccessibilityNodeInfoCompat targetNode) {
        if (targetNode == null) {
            return null;
        }

        return mSelectionCache.get(targetNode);
    }

    public void recycle() {
        if (mNode != null) {
            mNode.recycle();
            mNode = null;
        }
        clear();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                clearCache();
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    AccessibilityNodeInfoCompat copyNode = new AccessibilityNodeInfoCompat(
                            AccessibilityNodeInfo.obtain(source));
                    Selection selection = new Selection(event.getFromIndex(), event.getToIndex());
                    mSelectionCache.put(copyNode, selection);
                }
                break;
        }
    }

    public static class Selection {
        public final int start;
        public final int end;

        public Selection(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
