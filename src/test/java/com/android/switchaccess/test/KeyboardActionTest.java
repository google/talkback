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
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.KeyEvent;

import com.android.talkback.BuildConfig;
import com.android.talkback.R;
import com.android.switchaccess.ActionProcessor;
import com.android.switchaccess.KeyComboPreference;
import com.android.switchaccess.KeyboardAction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPreferenceManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for KeyboardAction
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {ShadowAccessibilityService.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class KeyboardActionTest {
    private Runnable mAction = new Runnable() {
        @Override
        public void run() {
        }
    };
    Runnable mCalledAction;
    ActionProcessor mActionProcessor = new ActionProcessor(null) {
        @Override
        public void process(Runnable action) {
            mCalledAction = action;
        }
    };
    private SharedPreferences mSharedPreferences;
    Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final KeyEvent mBackKeyDownEvent =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
    private final KeyEvent mBackKeyUpEvent =
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_0);
    private final KeyEvent mClickKeyDownEvent =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C);
    private final KeyEvent mClickKeyUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C);
    private KeyboardAction mClickKeyboardAction =
            new KeyboardAction(R.string.pref_key_mapped_to_click_key, mAction);

    @Before
    public void setUp() {
        mSharedPreferences = ShadowPreferenceManager.getDefaultSharedPreferences(mContext);
        setupPreferenceForAction(mBackKeyDownEvent, R.string.pref_key_mapped_to_back_key);
        setupPreferenceForAction(mClickKeyDownEvent, R.string.pref_key_mapped_to_click_key);
        mCalledAction = null;
        mClickKeyboardAction.refreshPreferences(mContext);
    }

    private void setupPreferenceForAction(KeyEvent event, int preferenceKeyId) {
        String preferenceKey = mContext.getString(preferenceKeyId);
        long extendedKeyCode = KeyComboPreference.keyEventToExtendedKeyCode(event);
        mSharedPreferences.edit().putLong(preferenceKey, extendedKeyCode).commit();
    }


    @Test
    public void testOnKeyPressingMyKeys() {
        assertTrue(mClickKeyboardAction.onKeyEvent(mClickKeyDownEvent, mActionProcessor, null));
        assertEquals("Action should trigger on first key down", mCalledAction, mAction);
        mCalledAction = null;

        assertTrue(mClickKeyboardAction.onKeyEvent(mClickKeyUpEvent, mActionProcessor, null));
        assertNull("Action should not trigger on key up", mCalledAction);

    }

    @Test
    public void testActionWithListener_shouldNotify() {
        KeyboardAction.KeyboardActionListener mockListener =
                mock(KeyboardAction.KeyboardActionListener.class);
        mClickKeyboardAction.onKeyEvent(mClickKeyDownEvent, mActionProcessor, mockListener);
        verify(mockListener, times(1)).onKeyboardAction(R.string.pref_key_mapped_to_click_key);
    }

        @Test
    public void testOnKeyPressingOtherKeys() {
        assertFalse(mClickKeyboardAction.onKeyEvent(mBackKeyDownEvent, mActionProcessor, null));
        assertFalse(mClickKeyboardAction.onKeyEvent(mBackKeyUpEvent, mActionProcessor, null));
        assertNull(mCalledAction);
    }
}