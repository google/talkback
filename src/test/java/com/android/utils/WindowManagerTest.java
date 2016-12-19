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

package com.android.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.switchaccess.test.ShadowAccessibilityWindowInfo;

import com.android.talkback.BuildConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {ShadowAccessibilityWindowInfo.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class WindowManagerTest {

    @Test
    public void testNullWindowsGetCurrent_returnNull() {
        WindowManager manager = new WindowManager(false /* isInRTL */);
        assertNull(manager.getCurrentWindow(false /* useInputFocus */));
    }

    @Test
    public void testNullWindowsGetPrevious_returnNull() {
        WindowManager manager = new WindowManager(false /* isInRTL */);
        assertNull(manager.getPreviousWindow(manager.getCurrentWindow(false /* useInputFocus */)));
    }

    @Test
    public void testNullWindowsGetNext_returnNull() {
        WindowManager manager = new WindowManager(false /* isInRTL */);
        assertNull(manager.getNextWindow(manager.getCurrentWindow(false /* useInputFocus */)));
    }

    @Test
    public void testEmptyWindowsGetCurrent_returnNull() {
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(new ArrayList<AccessibilityWindowInfo>());
        assertNull(manager.getCurrentWindow(false /* useInputFocus */));
    }

    @Test
    public void testEmptyWindowsGetPrevious_returnNull() {
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(new ArrayList<AccessibilityWindowInfo>());
        assertNull(manager.getPreviousWindow(manager.getCurrentWindow(false /* useInputFocus */)));
    }

    @Test
    public void testEmptyWindowsGetNext_returnNull() {
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(new ArrayList<AccessibilityWindowInfo>());
        assertNull(manager.getNextWindow(manager.getCurrentWindow(false /* useInputFocus */)));
    }

    @Test
    public void testNoFocusedWindowGetCurrent_returnNull() {
        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            windows.add(AccessibilityWindowInfo.obtain());
            ShadowAccessibilityWindowInfo shadowWindow =
                    (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(windows.get(i));
            shadowWindow.setId(i);
            shadowWindow.setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        }

        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(windows);
        assertNull(manager.getCurrentWindow(false /* useInputFocus */));
    }

    @Test
    public void testFocusedWindowLastGetNext_returnFirstWindow() {
        List<AccessibilityWindowInfo> windows = initList(3, 2);
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(windows);
        AccessibilityWindowInfo currentWindow = manager.getCurrentWindow(false /* useInputFocus */);
        assertEquals(windows.get(0).getId(), manager.getNextWindow(currentWindow).getId());
    }

    @Test
    public void testFocusedWindowNotLastGetNext_returnsNextWindow() {
        List<AccessibilityWindowInfo> windows = initList(3, 1);
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(windows);
        AccessibilityWindowInfo currentWindow = manager.getCurrentWindow(false /* useInputFocus */);
        assertEquals(windows.get(2).getId(), manager.getNextWindow(currentWindow).getId());
    }

    @Test
    public void testFocusedWindowFirstGetPrevious_returnsLastWindow() {
        List<AccessibilityWindowInfo> windows = initList(3, 0);
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(windows);
        AccessibilityWindowInfo currentWindow = manager.getCurrentWindow(false /* useInputFocus */);
        assertEquals(windows.get(2).getId(), manager.getPreviousWindow(currentWindow).getId());
    }

    @Test
    public void testFocusedWindowNotFirstGetPrevious_returnsPrevious() {
        List<AccessibilityWindowInfo> windows = initList(3, 1);
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(windows);
        AccessibilityWindowInfo currentWindow = manager.getCurrentWindow(false /* useInputFocus */);
        assertEquals(windows.get(0).getId(), manager.getPreviousWindow(currentWindow).getId());
    }

    @Test
    public void testGetCurrentWindow_returnsFocusedWindow() {
        List<AccessibilityWindowInfo> windows = initList(3, 1);
        WindowManager manager = new WindowManager(false /* isInRTL */);
        manager.setWindows(windows);
        assertEquals(windows.get(1).getId(),
                manager.getCurrentWindow(false /* useInputFocus */).getId());
    }

    private List<AccessibilityWindowInfo> initList(int listSize,
                                                   int accessibilityFocusedWindowIndex) {
        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            windows.add(AccessibilityWindowInfo.obtain());
            ShadowAccessibilityWindowInfo shadowWindow =
                    (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(windows.get(i));
            shadowWindow.setId(i);
            shadowWindow.setAccessibilityFocused(i == accessibilityFocusedWindowIndex);
        }

        return windows;
    }
}
