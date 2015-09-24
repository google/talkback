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

package com.android.talkback.controller;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.R;
import com.android.talkback.contextmenu.MenuManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.*;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
@TargetApi(16)
public class GestureControllerAppTest {
    // TODO(KM): AccessibilityService.performGlobalAction not tested since it is final

    SharedPreferences prefs;

    AccessibilityService mockAccessibilityService = new MockAccessibilityService();
    CursorController mockCursorController;
    FeedbackController mockFeedbackController;
    FullScreenReadController mockFullScreenReadController;
    MenuManager mockMenuManager;

    GestureControllerApp gestureController;

    @Before
    public void beforeTests() {
        // Clear prefs
        prefs = PreferenceManager.getDefaultSharedPreferences(mockAccessibilityService);
        prefs.edit().clear().apply();

        // Set a new mock for each test
        mockCursorController = mock(CursorController.class);
        mockFeedbackController = mock(FeedbackController.class);
        mockFullScreenReadController = mock(FullScreenReadController.class);
        mockMenuManager = mock(MenuManager.class);

        // new controller with mocks for each test
        gestureController = new GestureControllerApp(
                        mockAccessibilityService,
                        mockCursorController,
                        mockFeedbackController,
                        mockFullScreenReadController,
                        mockMenuManager);
    }

    @Test
    public void testOverridingPrefs() {
        prefs.edit().putString(mockAccessibilityService.getString(R.string.pref_shortcut_up_key),
                mockAccessibilityService.getString(R.string.shortcut_value_next)).apply();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP);
        verify(mockCursorController).next(true, true, true);
    }

    @Test
    public void testLegacyPrefs() {
        prefs.edit().putString(
                mockAccessibilityService.getString(R.string.pref_two_part_vertical_gestures_key),
                mockAccessibilityService.getString(R.string.value_two_part_vertical_gestures_jump))
                .apply();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN);
        verify(mockCursorController).jumpToTop();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP);
        verify(mockCursorController).jumpToBottom();
        prefs.edit().putString(
                mockAccessibilityService.getString(R.string.pref_two_part_vertical_gestures_key),
                mockAccessibilityService.getString(R.string.value_two_part_vertical_gestures_cycle))
                .apply();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN);
        verify(mockCursorController).previousGranularity();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP);
        verify(mockCursorController).nextGranularity();
    }

    @Test
    public void testDefaults() {
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP);
        verify(mockCursorController).previous(true, true, true);
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_LEFT);
        verify(mockCursorController, times(2)).previous(true, true, true);
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_DOWN);
        verify(mockCursorController).next(true, true, true);
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_RIGHT);
        verify(mockCursorController, times(2)).next(true, true, true);

        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP);
        verify(mockCursorController).jumpToBottom();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN);
        verify(mockCursorController).jumpToTop();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT);
        verify(mockCursorController).less();
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT);
        verify(mockCursorController).more();

        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT);
        // Global home
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT);
        verify(mockMenuManager).showMenu(R.menu.local_context_menu);
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT);
        // Global back
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT);
        verify(mockMenuManager).showMenu(R.menu.global_context_menu);

        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP);
        // Global overview
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN);
        // unassigned
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP);
        // unassigned
        gestureController.onGesture(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN);
        // Global notifications
    }

    private class MockAccessibilityService extends AccessibilityService {
        @Override
        public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {}

        @Override
        public void onInterrupt() {}
    }
}