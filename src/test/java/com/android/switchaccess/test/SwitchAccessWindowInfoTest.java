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

package com.android.switchaccess.test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.switchaccess.SwitchAccessWindowInfo;
import com.android.talkback.BuildConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

/**
 * Robolectric tests of ExtendedWindowInfo
 */
@Config(
        constants = BuildConfig.class,
        manifest = Config.NONE,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityWindowInfo.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class SwitchAccessWindowInfoTest {
    @Test
    public void testGetRootWithNullInfo_shouldReturnNull() {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        SwitchAccessWindowInfo windowInfo = new SwitchAccessWindowInfo(window, null);
        assertNull(windowInfo.getRoot());
    }

    @Test
    public void testGetRootWithNonNullInfo_shouldReturnInfo() {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ShadowAccessibilityWindowInfo shadowWindow =
                (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(window);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        shadowWindow.setRoot(info);
        SwitchAccessWindowInfo windowInfo = new SwitchAccessWindowInfo(window, null);
        assertEquals(info, windowInfo.getRoot().getInfo());
    }

    @Test
    public void testGetType_shouldReturnWrappedType() {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ShadowAccessibilityWindowInfo shadowWindow =
                (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(window);
        SwitchAccessWindowInfo windowInfo = new SwitchAccessWindowInfo(window, null);
        shadowWindow.setType(1);
        assertEquals(1, windowInfo.getType());
        shadowWindow.setType(2);
        assertEquals(2, windowInfo.getType());
    }

    @Test
    public void testGetBounds_shouldReturnWrappedBounds() {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ShadowAccessibilityWindowInfo shadowWindow =
                (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(window);
        Rect rectIn = new Rect(100, 200, 300, 400);
        shadowWindow.setBoundsInScreen(rectIn);
        SwitchAccessWindowInfo windowInfo = new SwitchAccessWindowInfo(window, null);
        Rect rectOut = new Rect();
        windowInfo.getBoundsInScreen(rectOut);
        assertEquals(rectIn, rectOut);
    }

    @Test
    public void testConvertList_shouldReturnProperNewList() {
        Rect bounds0 = new Rect(100, 200, 300, 400);
        Rect bounds1 = new Rect(200, 300, 400, 500);
        List<AccessibilityWindowInfo> windowInfos = new ArrayList<>(2);
        windowInfos.add(AccessibilityWindowInfo.obtain());
        windowInfos.add(AccessibilityWindowInfo.obtain());
        ShadowAccessibilityWindowInfo shadowWindow0 =
                (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(windowInfos.get(0));
        shadowWindow0.setBoundsInScreen(bounds0);
        shadowWindow0.setRoot(AccessibilityNodeInfo.obtain());
        ShadowAccessibilityWindowInfo shadowWindow1 =
                (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(windowInfos.get(1));
        shadowWindow1.setBoundsInScreen(bounds1);
        shadowWindow1.setRoot(AccessibilityNodeInfo.obtain());

        List<SwitchAccessWindowInfo> convertedList =
                SwitchAccessWindowInfo.convertZOrderWindowList(windowInfos);
        Rect outBounds0 = new Rect();
        convertedList.get(0).getBoundsInScreen(outBounds0);
        assertEquals(bounds0, outBounds0);
        Rect outBounds1 = new Rect();
        convertedList.get(1).getBoundsInScreen(outBounds1);
        assertEquals(bounds1, outBounds1);

        assertEquals(0, convertedList.get(0).getRoot().getWindowsAbove().size());
        assertEquals(1, convertedList.get(1).getRoot().getWindowsAbove().size());
        convertedList.get(1).getRoot().getWindowsAbove().get(0).getBoundsInScreen(outBounds0);
        assertEquals(bounds0, outBounds0);
    }
}
