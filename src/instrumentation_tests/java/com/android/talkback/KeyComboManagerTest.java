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
import android.content.SharedPreferences;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.KeyEvent;

import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import java.lang.Override;
import java.lang.Throwable;
import java.util.ArrayList;

public class KeyComboManagerTest extends TalkBackInstrumentationTestCase {

    private TalkBackService mTalkBack;
    private Instrumentation mInstrumentation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertEquals(TalkBackService.SERVICE_STATE_ACTIVE, TalkBackService.getServiceState());

        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        SharedPreferencesUtils.putBooleanPref(prefs, mTalkBack.getResources(),
                R.string.pref_show_suspension_confirmation_dialog, false);
    }

    @Override
    protected void tearDown() throws Exception {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        SharedPreferencesUtils.putBooleanPref(prefs, mTalkBack.getResources(),
                R.string.pref_suspended, false);
        SharedPreferencesUtils.putBooleanPref(prefs, mTalkBack.getResources(),
                R.string.pref_show_suspension_confirmation_dialog, true);

        super.tearDown();
    }

    @MediumTest
    public void testDefaultKeyCombo_pauseTalkBack() {
        sendAltShiftZ();
        waitForAccessibilityIdleSync();
        assertEquals(TalkBackService.SERVICE_STATE_SUSPENDED, TalkBackService.getServiceState());
    }

    @MediumTest
    public void testDefaultKeyCombo_resumeTalkback() {
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mTalkBack.suspendTalkBack();
            }
        });
        mInstrumentation.waitForIdleSync();

        sendAltShiftZ();
        waitForAccessibilityIdleSync();
        assertEquals(TalkBackService.SERVICE_STATE_ACTIVE, TalkBackService.getServiceState());
    }

    private void sendAltShiftZ() {
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyEvent down = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0,
                        KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

                mTalkBack.onKeyEventShared(down);
            }
        });
        mInstrumentation.waitForIdleSync();
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyEvent up = new KeyEvent(0, 200, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0,
                        KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
                mTalkBack.onKeyEventShared(up);
            }
        });
        mInstrumentation.waitForIdleSync();
    }
}
