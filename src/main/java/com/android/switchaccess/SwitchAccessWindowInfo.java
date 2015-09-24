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

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension of AccessibilityWindowInfo that returns {@code ExtendedNodeCompat} for its root
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SwitchAccessWindowInfo {
    final AccessibilityWindowInfo mAccessibilityWindowInfo;
    final List<AccessibilityWindowInfo> mListOfWindowsAbove;

    /**
     * Convert a list of standard {@code AccessibilityWindowInfo} objects into a list of
     * {@code ExtendedWindowInfo} objects.
     *
     * @param originalList The original list in Z order (as the framework returns it)
     * @return The new list in the same order as the original
     */
    static public List<SwitchAccessWindowInfo>
            convertZOrderWindowList(List<AccessibilityWindowInfo> originalList) {
        List<SwitchAccessWindowInfo> newList = new ArrayList<>(originalList.size());
        for (int i = 0; i < originalList.size(); i++) {
            newList.add(new SwitchAccessWindowInfo(
                    originalList.get(i), originalList.subList(0, i)));
        }
        return newList;
    }

    /**
     * @param accessibilityWindowInfo The windowInfo to wrap
     * @param listOfWindowsAbove A list of all windows above this one
     */
    public SwitchAccessWindowInfo(AccessibilityWindowInfo accessibilityWindowInfo,
            List<AccessibilityWindowInfo> listOfWindowsAbove) {
        if (accessibilityWindowInfo == null) {
            throw new NullPointerException();
        }
        mAccessibilityWindowInfo = accessibilityWindowInfo;
        mListOfWindowsAbove = listOfWindowsAbove;
    }

    /**
     * @return The root of the window
     */
    public SwitchAccessNodeCompat getRoot() {
        AccessibilityNodeInfo root = mAccessibilityWindowInfo.getRoot();
        return (root == null)
                ? null : new SwitchAccessNodeCompat((Object) root, mListOfWindowsAbove);
    }

    /**
     * @return The type of the window. See {@link AccessibilityWindowInfo}
     */
    public int getType() {
        return mAccessibilityWindowInfo.getType();
    }

    public void getBoundsInScreen(Rect outBounds) {
        mAccessibilityWindowInfo.getBoundsInScreen(outBounds);
    }
}
