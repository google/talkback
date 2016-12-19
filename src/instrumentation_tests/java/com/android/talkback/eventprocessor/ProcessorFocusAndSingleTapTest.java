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

package com.android.talkback.eventprocessor;

import com.google.android.marvin.talkback.TalkBackService;

import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;

import com.android.talkback.R;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class ProcessorFocusAndSingleTapTest extends TalkBackInstrumentationTestCase {

    TalkBackService mService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mService = getService();
        setContentView(R.layout.text_activity);
    }

    @MediumTest
    public void testOnAccessibilityEvent_viewHoverEnter_shouldFocus() {
        AccessibilityNodeInfoCompat buttonNode = getNodeForId(R.id.sign_in);
        final View buttonView = getViewForId(R.id.sign_in);

        sendAccessibilityEvent(buttonView, AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START);
        sendAccessibilityEvent(buttonView, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
        sendAccessibilityEvent(buttonView, AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END);

        AccessibilityNodeInfoCompat cursor = mService.getCursorController().getCursor();
        assertEquals(buttonNode, cursor);
    }

    @MediumTest
    public void testOnAccessibilityEvent_viewHoverEnter_shouldRefocus() {
        AccessibilityNodeInfoCompat buttonNode = getNodeForId(R.id.sign_in);
        final View buttonView = getViewForId(R.id.sign_in);

        mService.getCursorController().setCursor(buttonNode);
        waitForAccessibilityIdleSync();

        // Refocus only occurs if TTS is silent, so let's make sure nothing's speaking!
        mService.getSpeechController().interrupt();
        waitForAccessibilityIdleSync();

        sendAccessibilityEvent(buttonView, AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START);
        sendAccessibilityEvent(buttonView, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
        sendAccessibilityEvent(buttonView, AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END);

        AccessibilityNodeInfoCompat cursor = mService.getCursorController().getCursor();
        assertEquals(buttonNode, cursor);
    }

    private void sendAccessibilityEvent(final View view, final int eventId) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                view.sendAccessibilityEvent(eventId);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();
    }

}
