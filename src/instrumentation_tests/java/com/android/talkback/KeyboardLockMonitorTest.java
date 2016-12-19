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

package com.android.talkback;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;
import com.googlecode.eyesfree.testing.UtteranceFilter;

import java.lang.Override;
import java.lang.Throwable;
import java.util.ArrayList;

public class KeyboardLockMonitorTest extends TalkBackInstrumentationTestCase {

    private TalkBackService mTalkBack;
    private Instrumentation mInstrumentation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @MediumTest
    public void testEnableCapsLock_announceOn() throws Throwable {
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        startRecordingRawSpeech();

        long down = SystemClock.uptimeMillis();
        KeyEvent capsLockDown = new KeyEvent(down, down, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_CAPS_LOCK, 0, 0);
        mTalkBack.onKeyEventShared(capsLockDown);

        long up = SystemClock.uptimeMillis();
        KeyEvent capsLockUp = new KeyEvent(down, up, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_CAPS_LOCK, 0, KeyEvent.META_CAPS_LOCK_ON);
        mTalkBack.onKeyEventShared(capsLockUp);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        stopRecordingAndAssertRawSpeech("Caps Lock on.");
        assertFalse(feedbackMatches("Caps Lock off."));
    }

    @MediumTest
    public void testToggleCapsLock_announceOnOff() throws Throwable {
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        startRecordingRawSpeech();

        // First Caps Lock keypress.
        final long down = SystemClock.uptimeMillis();
        final KeyEvent capsLockDown = new KeyEvent(down, down, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_CAPS_LOCK, 0, 0);
        mTalkBack.onKeyEventShared(capsLockDown);

        final long up = SystemClock.uptimeMillis();
        final KeyEvent capsLockUp = new KeyEvent(down, up, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_CAPS_LOCK, 0, KeyEvent.META_CAPS_LOCK_ON);
        mTalkBack.onKeyEventShared(capsLockUp);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        // Second Caps Lock keypress.
        final long down2 = SystemClock.uptimeMillis();
        final KeyEvent capsLockDown2 = new KeyEvent(down2, down2, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_CAPS_LOCK, 0, KeyEvent.META_CAPS_LOCK_ON);
        mTalkBack.onKeyEventShared(capsLockDown2);

        final long up2 = SystemClock.uptimeMillis();
        final KeyEvent capsLockUp2 = new KeyEvent(down2, up2, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_CAPS_LOCK, 0, 0);
        mTalkBack.onKeyEventShared(capsLockUp2);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        stopRecordingAndAssertRawSpeech("Caps Lock off.");
        assertTrue(feedbackMatches("Caps Lock on."));
    }

    private boolean feedbackMatches(String text) {
        for (FeedbackItem it : getRawSpeechHistory()) {
            if (it.getAggregateText().equals(text)) {
                return true;
            }
        }
        return false;
    }

}
