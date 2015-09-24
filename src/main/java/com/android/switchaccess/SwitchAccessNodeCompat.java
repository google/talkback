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

package com.android.switchaccess;

/*
 * Class that wraps A11yNodeInfoCompat, and supplies a new version of isVisibleToUser that takes
 * into account window overlap.
 *
 * This class does not extend A11yNodeInfoCompat because A11yNodeInfoCompat objects are obtained
 * by calling a static method.
 */
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class works around shortcomings of AccessibilityNodeInfo/Compat. One major issue is that
 * the visibility of Views that are covered by other Views or Windows is not handled completely
 * by the framework, but other issues may crop up over time.
 *
 * In order to support performing actions on the UI, we need to have access to the real Info. This
 * class can thus either wrap or extend AccessibilityNodeInfo or Compat. Because most of the
 * methods in Compat work fine, a wrapper will include huge amounts of boilerplate, so this is
 * an extension of the Compat class (Info is final).
 *
 * The biggest issue with this class is that it can't override the static {@code obtain} methods
 * in compat. That means that it is not compatible with utils methods built for Compat classes.
 * Arguably it thus shouldn't extend Compat, but the boilerplate savings seems worth dealing with.
 * We may eventually drop the extending and completely hide the Compat implementation if such
 * obtaining becomes an issue.
 */
public class SwitchAccessNodeCompat extends AccessibilityNodeInfoCompat {
    private final List<AccessibilityWindowInfo> mWindowsAbove;
    private boolean mVisibilityCalculated = false;
    private Rect mVisibleBoundsInScreen;

    /**
     * Find the largest sub-rectangle that doesn't intersect a specified one.
     *
     * @param rectToModify The rect that may be modified to avoid intersections
     * @param otherRect The rect that should be avoided
     */
    private static void adjustRectToAvoidIntersection(Rect rectToModify, Rect otherRect) {
        /*
         * Some rectangles are flipped around (left > right). Make sure we have two Rects free of
         * such pathologies.
         */
        rectToModify.sort();
        otherRect.sort();
        /*
         * Intersect rectToModify with four rects that represent cuts of the entire space along
         * lines defined by the otherRect's edges
         */
        Rect[] cuts = {
                new Rect(Integer.MIN_VALUE, Integer.MIN_VALUE, otherRect.left, Integer.MAX_VALUE),
                new Rect(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, otherRect.top),
                new Rect(otherRect.right, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                new Rect(Integer.MIN_VALUE, otherRect.bottom, Integer.MAX_VALUE, Integer.MAX_VALUE)
        };

        int maxIntersectingRectArea = 0;
        int indexOfLargestIntersection = -1;
        for (int i = 0; i < cuts.length; i++) {
            if (cuts[i].intersect(rectToModify)) {
                /* Reassign this cut to its intersection with rectToModify */
                int visibleRectArea = cuts[i].width() * cuts[i].height();
                if (visibleRectArea > maxIntersectingRectArea) {
                    maxIntersectingRectArea = visibleRectArea;
                    indexOfLargestIntersection = i;
                }
            }
        }
        if (maxIntersectingRectArea <= 0) {
            // The rectToModify isn't within any of our cuts, so it's entirely occuled by otherRect.
            rectToModify.setEmpty();
            return;
        }
        rectToModify.set(cuts[indexOfLargestIntersection]);
        return;
    }

    /**
     * @param info The info to wrap
     */
    public SwitchAccessNodeCompat(Object info) {
        this(info, null);
    }

    /**
     * @param info The info to wrap
     * @param windowsAbove The windows sitting on top of the current one. This
     * list is used to compute visibility.
     */
    public SwitchAccessNodeCompat(Object info, List<AccessibilityWindowInfo> windowsAbove) {
        super(info);
        if (info == null) {
            throw new NullPointerException();
        }
        if (windowsAbove == null) {
            mWindowsAbove = Collections.emptyList();
        } else {
            mWindowsAbove = new ArrayList<>(windowsAbove);
        }
    }

    @Override
    public SwitchAccessNodeCompat getParent() {
        AccessibilityNodeInfo info = (AccessibilityNodeInfo) getInfo();
        AccessibilityNodeInfo parent = info.getParent();
        return (parent == null) ? null : new SwitchAccessNodeCompat(parent, this.mWindowsAbove);
    }

    @Override
    public SwitchAccessNodeCompat getChild(int index) {
        AccessibilityNodeInfo info = (AccessibilityNodeInfo) getInfo();
        AccessibilityNodeInfo child = info.getChild(index);
        return (child == null) ? null : new SwitchAccessNodeCompat(child, this.mWindowsAbove);
    }

    /**
     * @return An immutable copy of the current window list
     */
    public List<AccessibilityWindowInfo> getWindowsAbove() {
        return Collections.unmodifiableList(mWindowsAbove);
    }

    /**
     * Get the largest rectangle in the bounds of the View that is not covered by another window.
     *
     * @param visibleBoundsInScreen The rect to return the visible bounds in
     */
    public void getVisibleBoundsInScreen(Rect visibleBoundsInScreen) {
        updateVisibility();
        visibleBoundsInScreen.set(mVisibleBoundsInScreen);
    }

    /**
     * Obtain a new copy of this object. The resulting node must be recycled for efficient use
     * of underlying resources.
     *
     * @return A new copy of the node
     */
    public SwitchAccessNodeCompat obtainCopy() {
        SwitchAccessNodeCompat obtainedInstance =
                new SwitchAccessNodeCompat(AccessibilityNodeInfo
                        .obtain((AccessibilityNodeInfo) getInfo()), mWindowsAbove);

        /* Preserve lazily-initialized value if we have it */
        if (mVisibilityCalculated) {
            obtainedInstance.mVisibilityCalculated = true;
            obtainedInstance.mVisibleBoundsInScreen = new Rect(mVisibleBoundsInScreen);
        }

        return obtainedInstance;
    }

    private void updateVisibility() {
        if (!mVisibilityCalculated) {
            mVisibleBoundsInScreen = new Rect();
            getBoundsInScreen(mVisibleBoundsInScreen);

            /* Deal with visibility implications for windows above */
            Rect windowBoundsInScreen = new Rect();
            for (int i = 0; i < mWindowsAbove.size(); ++i) {
                mWindowsAbove.get(i).getBoundsInScreen(windowBoundsInScreen);
                mVisibleBoundsInScreen.sort();
                windowBoundsInScreen.sort();
                if (Rect.intersects(mVisibleBoundsInScreen, windowBoundsInScreen)) {
                    adjustRectToAvoidIntersection(mVisibleBoundsInScreen, windowBoundsInScreen);
                }
            }

            mVisibilityCalculated = true;
        }
    }
}
