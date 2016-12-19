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

import android.annotation.TargetApi;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import com.android.switchaccess.UiChangeDetector;
import com.android.talkback.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Robolectric tests for AccessibilityEventProcessor
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21
)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class UiChangeDetectorTest {
    UiChangeDetector mUiChangeDetector;
    UiChangeDetector.PossibleUiChangeListener mListener =
            mock(UiChangeDetector.PossibleUiChangeListener.class);

    @Before
    public void setUp() {
        mUiChangeDetector = new UiChangeDetector(mListener);
    }

    @Test
    public void testNullEvent_shouldNotCrash() {
        mUiChangeDetector.onAccessibilityEvent(null);
    }

    @Test
    public void testWindowChangedEvent_shouldCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, times(1)).onPossibleChangeToUi();
    }

    @Test
    public void testScrollEvent_shouldCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_VIEW_SCROLLED);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, times(1)).onPossibleChangeToUi();
    }

    @Test
    public void testWindowsChangedEvent_shouldCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOWS_CHANGED);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, times(1)).onPossibleChangeToUi();
    }

    @Test
    public void testWindowStateChangedEvent_shouldCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, times(1)).onPossibleChangeToUi();
    }

    @Test
    public void testWindowContentChangedEvent_subtreeChanged_shouldCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, times(1)).onPossibleChangeToUi();
    }

    @Test
    public void testWindowContentChangedEvent_undefined_shouldCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, never()).onPossibleChangeToUi();
    }

    @Test
    public void testWindowContentChangedEvent_text_shouldNotCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, never()).onPossibleChangeToUi();
    }

    @Test
    public void testIrrelevantEvents_shouldNotCallListener() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_GESTURE_DETECTION_END);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_GESTURE_DETECTION_START);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_TOUCH_INTERACTION_START);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_SELECTED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
        mUiChangeDetector.onAccessibilityEvent(event);
        event.setEventType(AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
        mUiChangeDetector.onAccessibilityEvent(event);
        verify(mListener, never()).onPossibleChangeToUi();
    }
}