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

package com.android.switchaccess.test;

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.internal.ShadowExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Shadow of AccessibilityNodeInfoCompat that wraps ShadowAccessibilityNodeInfo.
 */
@Implements(AccessibilityNodeInfoCompat.class)
public class ShadowAccessibilityNodeInfoCompat {

    @RealObject AccessibilityNodeInfoCompat mRealObject;

    @Implementation
    public static AccessibilityNodeInfoCompat obtain(AccessibilityNodeInfoCompat compat) {
        final AccessibilityNodeInfo newInfo =
                AccessibilityNodeInfo.obtain((AccessibilityNodeInfo) compat.getInfo());
        return new AccessibilityNodeInfoCompat(newInfo);
    }

    @Implementation
    public static AccessibilityNodeInfoCompat obtain() {
        return new AccessibilityNodeInfoCompat(AccessibilityNodeInfo.obtain());
    }

    /**
     * Check for leaked objects that were {@code obtain}ed but never {@code recycle}d.
     * @param printUnrecycledNodesToSystemErr - if true, stack traces of calls to {@code obtain}
     * that lack matching calls to {@code recycle} are dumped to System.err.
     *
     * @return {@code true} if there are unrecycled nodes
     */
    public static boolean areThereUnrecycledNodes(boolean printUnrecycledNodesToSystemErr) {
        return ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(printUnrecycledNodesToSystemErr);
    }

    /**
     * Clear list of obtained instance objects. {@code areThereUnrecycledNodes} will always
     * return false if called immediately afterwards.
     */
    public static void resetObtainedInstances() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Implementation
    public void recycle() {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).recycle();
    }

    @Implementation
    public int getChildCount() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).getChildCount();
    }

    @Implementation
    public AccessibilityNodeInfoCompat getChild(int index) {
        final AccessibilityNodeInfo childInfo =
                ((AccessibilityNodeInfo) mRealObject.getInfo()).getChild(index);
        if (childInfo == null) {
            return null;
        }

        return new AccessibilityNodeInfoCompat(childInfo);
    }

    @Implementation
    public AccessibilityNodeInfoCompat getParent() {
        final AccessibilityNodeInfo parentInfo =
                ((AccessibilityNodeInfo) mRealObject.getInfo()).getParent();
        if (parentInfo == null) {
            return null;
        }

        return new AccessibilityNodeInfoCompat(parentInfo);
    }

    @Implementation
    public boolean isClickable() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isClickable();
    }

    @Implementation
    public boolean isLongClickable() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isLongClickable();
    }

    @Implementation
    public boolean isAccessibilityFocused() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isAccessibilityFocused();
    }

    @Implementation
    public boolean isFocusable() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isFocusable();
    }

    @Implementation
    public boolean isFocused() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isFocused();
    }

    @Implementation
    public boolean isScrollable() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isScrollable();
    }

    public boolean isPasteable() {
        ShadowAccessibilityNodeInfo info =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mRealObject.getInfo());
        return info.isPasteable();
    }

    @Implementation
    public boolean isVisibleToUser() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).isVisibleToUser();
    }

    @Implementation
    public void setVisibleToUser(boolean visible) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setVisibleToUser(visible);
    }

    @Implementation
    public void setClickable(boolean isClickable) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setClickable(isClickable);
    }

    @Implementation
    public void setLongClickable(boolean isLongClickable) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setLongClickable(isLongClickable);
    }

    @Implementation
    public void setAccessibilityFocused(boolean isAccessibilityFocused) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setAccessibilityFocused(
                isAccessibilityFocused);
    }

    @Implementation
    public void setFocusable(boolean isFocusable) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setFocusable(isFocusable);
    }

    @Implementation
    public void setFocused(boolean isFocused) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setFocused(isFocused);
    }

    @Implementation
    public void setScrollable(boolean isScrollable) {
        ((AccessibilityNodeInfo) mRealObject.getInfo()).setScrollable(isScrollable);
    }

    public void setPasteable(boolean isPasteable) {
        ShadowAccessibilityNodeInfo info =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mRealObject.getInfo());
        info.setPasteable(isPasteable);
    }

    @Implementation
    public void setText(String text) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        info.setText(text);
    }

    @Implementation
    public String getText() {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        return (String) info.getText();
    }

    @Implementation
    public void setContentDescription(String contentDescription) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        info.setContentDescription(contentDescription);
    }

    @Implementation
    public String getContentDescription() {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        return (String) info.getContentDescription();
    }

    @Implementation
    public void setBoundsInScreen(Rect bounds) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        info.setBoundsInScreen(bounds);
    }

    @Implementation
    public void getBoundsInScreen(Rect outBounds) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        info.getBoundsInScreen(outBounds);
    }

    @Implementation
    public void setClassName(CharSequence className) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        info.setClassName(className);
    }

    @Implementation
    public int getMovementGranularities() {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        return info.getMovementGranularities();
    }

    @Implementation
    public void setMovementGranularities(int granularities) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        info.setMovementGranularities(granularities);
    }


    @Implementation
    public CharSequence getClassName() {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        return info.getClassName();
    }

    @Implementation
    public int getActions() {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        return info.getActions();
    }

    @Implementation
    public List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> getActionList() {
        /* Robolectric doesn't handle the AccessibilityNodeInfo.AccessibilityAction. Make do. */
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> result = new ArrayList<>();
        int actionsInt = getActions();
        int mask = 1;
        while (actionsInt != 0) {
            if ((actionsInt & mask) != 0) {
                actionsInt &= ~mask;
                result.add(new AccessibilityNodeInfoCompat
                        .AccessibilityActionCompat(mask, String.format("Action_%d", mask)));
            }
            mask = mask << 1;
        }
        return result;
    }

    @Implementation
    public boolean performAction(int action) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        return info.performAction(action);
    }

    @Implementation
    @Override
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        final AccessibilityNodeInfoCompat compat = (AccessibilityNodeInfoCompat) object;
        final AccessibilityNodeInfo thisInfo = ((AccessibilityNodeInfo) mRealObject.getInfo());
        final AccessibilityNodeInfo otherInfo = ((AccessibilityNodeInfo) compat.getInfo());
        return thisInfo.equals(otherInfo);
    }

    @Implementation
    @Override
    public int hashCode() {
        return ((AccessibilityNodeInfo) mRealObject.getInfo()).hashCode();
    }

    /**
     * Add a child node to this one. Also initializes the parent field of the child.
     *
     * @param child The node to be added as a child.
     */
    public void addChild(AccessibilityNodeInfoCompat child) {
        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) mRealObject.getInfo();
        final ShadowAccessibilityNodeInfo shadowInfo =
                ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(info));
        shadowInfo.addChild((AccessibilityNodeInfo) child.getInfo());
    }

    @Implements(AccessibilityNodeInfoCompat.AccessibilityActionCompat.class)
    public static final class ShadowAccessibilityActionCompat {
        private int id;
        private CharSequence label;

        public void __constructor__(int id, CharSequence label) {
            this.id = id;
            this.label = label;
        }

        @Implementation
        public int getId() {
            return id;
        }

        @Implementation
        public CharSequence getLabel() {
            return label;
        }
    }
}
