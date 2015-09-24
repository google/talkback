/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.content.Context;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.talkback.R;
import com.android.utils.PerformActionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Option Scanning node to perform an action on an {@code AccessibilityNodeInfoCompat}
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AccessibilityNodeActionNode extends OptionScanActionNode {
    private final SwitchAccessNodeCompat mNodeCompat;
    private final AccessibilityNodeInfoCompat.AccessibilityActionCompat mAction;

    /**
     * Note that this object must be recycled to prevent nodeCompat from leaking.
     * @param nodeCompat The node on which to perform the action
     * @param action The action the node should perform.
     */
    public AccessibilityNodeActionNode(SwitchAccessNodeCompat nodeCompat,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat action) {
        mNodeCompat = nodeCompat.obtainCopy();
        mAction = action;
    }

    /**
     * Get the underlying {@code SwitchAccessNodeCompat}
     * @return The {@code SwitchAccessNodeCompat} on which actions are performed.
     */
    public SwitchAccessNodeCompat getNodeInfoCompat() {
        return mNodeCompat.obtainCopy();
    }

    /**
     * This method must be called to free the copy of the AccessibilityNodeInfoCompat made
     * when the object was created
     */
    @Override
    public void recycle() {
        mNodeCompat.recycle();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AccessibilityNodeActionNode)) {
            return false;

        }
        AccessibilityNodeActionNode otherNode = (AccessibilityNodeActionNode) other;
        if (otherNode.mAction.getId() != mAction.getId()) {
            return false;
        }
        if (!otherNode.mNodeCompat.equals(mNodeCompat)) {
            return false;
        }

        if (!otherNode.getRectsForNodeHighlight().equals(
                getRectsForNodeHighlight())) {
            return false;
        }
        return true;
    }

    @Override
    public void performAction() {
        PerformActionUtils.performAction(mNodeCompat, mAction.getId());
    }

    @Override
    public Set<Rect> getRectsForNodeHighlight() {
        Rect boundsInScreen = new Rect();
        mNodeCompat.getVisibleBoundsInScreen(boundsInScreen);
        Set<Rect> rects = new HashSet<>();
        rects.add(boundsInScreen);
        return Collections.unmodifiableSet(rects);
    }

    @Override
    public CharSequence getActionLabel(Context context) {
        CharSequence label = mAction.getLabel();
        if (label != null) {
            return label;
        }

        int id = mAction.getId();
        switch (id) {
            case AccessibilityNodeInfoCompat.ACTION_CLICK:
                return context.getResources().getString(R.string.action_name_click);
            case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
                return context.getResources().getString(R.string.action_name_long_click);
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                return context.getResources().getString(R.string.action_name_scroll_backward);
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                return context.getResources().getString(R.string.action_name_scroll_forward);
            default:
                return null;
        }
    }
}
