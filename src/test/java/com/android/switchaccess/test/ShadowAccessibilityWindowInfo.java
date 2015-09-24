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

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.robolectric.Robolectric;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
/**
 * Shadow of AccessibilityNodeInfoCompat that allows a test to set properties that are
 * locked in the original class. It also keeps track of calls to {@code obtain()} and
 * {@code recycle()} to look for bugs that mismatches.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@Implements(AccessibilityWindowInfo.class)
public class ShadowAccessibilityWindowInfo {

    private AccessibilityNodeInfo mRootNode;

    private int mType = AccessibilityWindowInfo.TYPE_APPLICATION;

    private Rect mBoundsInScreen = new Rect();

    private boolean mIsAccessibilityFocused;

    private boolean mIsActive;

    private int mId;

    public void __constructor__() {

    }

    @Implementation
    public static AccessibilityWindowInfo obtain() {
        return ReflectionHelpers.callConstructor(AccessibilityWindowInfo.class);
    }

    @Implementation
    public AccessibilityNodeInfo getRoot() {
        return (mRootNode == null) ? null : AccessibilityNodeInfo.obtain(mRootNode);
    }

    @Implementation
    public int getType() {
        return mType;
    }

    @Implementation
    public void getBoundsInScreen(Rect outBounds) {
        outBounds.set(mBoundsInScreen);
    }

    @Implementation
    public boolean isAccessibilityFocused() {
        return mIsAccessibilityFocused;
    }

    @Implementation
    public boolean isActive() {
        return mIsActive;
    }

    @Implementation
    public int getId() {
        return mId;
    }

    @Implementation
    public boolean equals(Object obj) {
        if (!(obj instanceof AccessibilityWindowInfo)) {
            return false;
        }

        return mId == ((AccessibilityWindowInfo) obj).getId();
    }

    public void setRoot(AccessibilityNodeInfo root) {
        mRootNode = root;
    }

    public void setType(int type) {
        mType = type;
    }

    public void setBoundsInScreen(Rect boundsInScreen) {
        mBoundsInScreen.set(boundsInScreen);
    }

    public void setAccessibilityFocused(boolean isAccessibilityFocused) {
        mIsAccessibilityFocused = isAccessibilityFocused;
    }

    public void setActive(boolean isActive) {
        mIsActive = isActive;
    }

    public void setId(int id) {
        mId = id;
    }

}