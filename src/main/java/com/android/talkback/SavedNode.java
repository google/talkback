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

import com.google.android.marvin.talkback.TalkBackService;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.PerformActionUtils;
import com.android.utils.WindowManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SavedNode implements AccessibilityEventListener {
    private static final int NODE_REGULAR = 0;
    private static final int NODE_ANCHOR = 1;
    private static final int NODE_ANCHORED = 2;

    @IntDef({NODE_REGULAR, NODE_ANCHOR, NODE_ANCHORED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SavedNodeType {}

    /** The node that previously had accessibility focus. */
    private AccessibilityNodeInfoCompat mNode;
    /** The anchor node of the window of mNode. */
    private AccessibilityNodeInfoCompat mAnchor;
    private Selection mSelection;
    private CursorGranularity mGranularity;
    private @SavedNodeType int mSavedNodeType;
    private Map<AccessibilityNodeInfoCompat, Selection> mSelectionCache = new HashMap<>();

    public void saveNodeState(AccessibilityNodeInfoCompat node,
            CursorGranularity granularity,
            TalkBackService service) {
        if (node == null) {
            return;
        }

        // Anchors and anchored nodes can only exist on >= N.
        if (BuildCompat.isAtLeastN()) {
            // Does the current node have a window anchored to it?
            WindowManager windowManager = new WindowManager(service.isScreenLayoutRTL());
            windowManager.setWindows(service.getWindows());
            if (windowManager.getAnchoredWindow(node) != null) {
                mSavedNodeType = NODE_ANCHOR;
                mNode = AccessibilityNodeInfoCompat.obtain(node);
                mAnchor = null;
                mGranularity = granularity;
                mSelection = findSelectionForNode(node);
                return;
            }

            // Is the current node in a window anchored to another node?
            AccessibilityNodeInfoCompat anchor = AccessibilityNodeInfoUtils.getAnchor(node);
            if (anchor != null) {
                mSavedNodeType = NODE_ANCHORED;
                mNode = AccessibilityNodeInfoCompat.obtain(node);
                mAnchor = anchor;
                mGranularity = granularity;
                mSelection = findSelectionForNode(anchor);
                return;
            }
        }

        // The node is neither anchored nor an anchor.
        mSavedNodeType = NODE_REGULAR;
        mNode = AccessibilityNodeInfoCompat.obtain(node);
        mAnchor = null;
        mGranularity = granularity;
        mSelection = findSelectionForNode(node);
    }

    public AccessibilityNodeInfoCompat getNode() {
        return mNode;
    }

    public AccessibilityNodeInfoCompat getAnchor() {
        return mAnchor;
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

    public void restoreTextAndSelection() {
        switch (mSavedNodeType) {
            case NODE_REGULAR: {
                if (mNode != null) {
                    restoreSelection(mNode);
                }
            } break;
            case NODE_ANCHOR: {
                if (mNode != null) {
                    // Restore text on the current node so that its popup window appears again.
                    restoreText(mNode);
                    restoreSelection(mNode);
                }
            } break;
            case NODE_ANCHORED:{
                if (mAnchor != null) {
                    // Restore text on anchor so that its popup (containing current node) appears.
                    restoreText(mAnchor);
                    restoreSelection(mAnchor);
                }
            } break;
        }
    }

    private void restoreText(AccessibilityNodeInfoCompat node) {
        if (node.getText() != null) {
            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    node.getText());
            PerformActionUtils.performAction(node,
                    AccessibilityNodeInfoCompat.ACTION_SET_TEXT, args);
        }
    }

    private void restoreSelection(AccessibilityNodeInfoCompat node) {
        if (mSelection != null) {
            Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT,
                    mSelection.start);
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
                    mSelection.end);
            PerformActionUtils.performAction(node,
                    AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args);
        }
    }

    public void recycle() {
        if (mNode != null) {
            mNode.recycle();
            mNode = null;
        }
        if (mAnchor != null) {
            mAnchor.recycle();
            mAnchor = null;
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
