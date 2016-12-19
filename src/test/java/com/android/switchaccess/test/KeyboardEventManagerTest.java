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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.KeyEvent;
import com.android.talkback.BuildConfig;
import com.android.talkback.R;
import com.android.switchaccess.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowPreferenceManager;

import java.util.List;

/**
 * Tests for KeyboardEventManager.
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {ShadowAccessibilityService.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class KeyboardEventManagerTest {
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final SharedPreferences mSharedPreferences =
            ShadowPreferenceManager.getDefaultSharedPreferences(mContext);
    private final AccessibilityService mAccessibilityService = new SwitchAccessService();
    private final ShadowAccessibilityService mShadowService =
            (ShadowAccessibilityService) ShadowExtractor.extract(mAccessibilityService);
    private final OptionManager mMockOptionManger = mock(OptionManager.class);
    private final AutoScanController mMockAutoScanController = mock(AutoScanController.class);
    private KeyboardEventManager mKeyboardEventManager;
    ActionProcessor mRunAction = new ActionProcessor(null) {
        @Override
        public void process(Runnable action) {
            action.run();
        }
    };
    @Before
    public void setUp() {
        mSharedPreferences.edit().clear().commit();
        mKeyboardEventManager = new KeyboardEventManager(
                mAccessibilityService, mMockOptionManger, mMockAutoScanController);

    }
    @After
    public void tearDown() {
        mSharedPreferences.edit().clear().commit();
    }

    @Test
    public void testUnassignedKey_notHandled() {
        KeyEvent unassignedKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        mSharedPreferences.edit().clear().commit();
        assertFalse(mKeyboardEventManager.onKeyEvent(unassignedKeyEvent, mRunAction, null));
        List<Integer> globalActionList = mShadowService.getGlobalActionsPerformed();
        assertEquals(0, globalActionList.size());
    }

    @Test
    public void testBackKey_performGlobalActionCalledOnService() {
        KeyEvent backKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        setupPreferenceForAction(backKeyEvent, R.string.pref_key_mapped_to_back_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(backKeyEvent, mRunAction, null));
        List<Integer> globalActionList = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActionList.size());
        assertEquals(
                (long) AccessibilityService.GLOBAL_ACTION_BACK, (long) globalActionList.get(0));
    }

    @Test
    public void testNextKey_selectsOption1() {
        KeyEvent nextKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        setupPreferenceForAction(nextKeyEvent, R.string.pref_key_mapped_to_next_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(nextKeyEvent, mRunAction, null));
        verify(mMockOptionManger, times(1)).selectOption(1);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testClickKey_selectsOption0() {
        KeyEvent clickKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        setupPreferenceForAction(clickKeyEvent, R.string.pref_key_mapped_to_click_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(clickKeyEvent, mRunAction, null));
        verify(mMockOptionManger, times(1)).selectOption(0);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testOptionScanKey3_selectsOption2() {
        KeyEvent clickKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        setupPreferenceForAction(clickKeyEvent, R.string.pref_key_mapped_to_switch_3_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(clickKeyEvent, mRunAction, null));
        verify(mMockOptionManger, times(1)).selectOption(2);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testOptionScanKey4_selectsOption3() {
        KeyEvent clickKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        setupPreferenceForAction(clickKeyEvent, R.string.pref_key_mapped_to_switch_4_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(clickKeyEvent, mRunAction, null));
        verify(mMockOptionManger, times(1)).selectOption(3);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testOptionScanKey5_selectsOption4() {
        KeyEvent clickKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        setupPreferenceForAction(clickKeyEvent, R.string.pref_key_mapped_to_switch_5_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(clickKeyEvent, mRunAction, null));
        verify(mMockOptionManger, times(1)).selectOption(4);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testClickKey_shouldNotifyListener() {
        KeyboardAction.KeyboardActionListener mockListener =
                mock(KeyboardAction.KeyboardActionListener.class);
        KeyEvent clickKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        setupPreferenceForAction(clickKeyEvent, R.string.pref_key_mapped_to_click_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(clickKeyEvent, mRunAction, mockListener));
        verify(mockListener, times(1)).onKeyboardAction(R.string.pref_key_mapped_to_click_key);
    }

    @Test
    public void testPreviousKey_selectsParent() {
        KeyEvent previousKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D);
        setupPreferenceForAction(previousKeyEvent, R.string.pref_key_mapped_to_previous_key);
        assertTrue(mKeyboardEventManager.onKeyEvent(previousKeyEvent, mRunAction, null));
        verify(mMockOptionManger, times(1)).moveToParent(true);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testAutoScanKeyNoEnable_doesNotActivateAutoScan() {
        KeyEvent autoScanKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C);
        setupPreferenceForAction(autoScanKeyEvent, R.string.pref_key_mapped_to_auto_scan_key);
        setupBooleanPreference(false, R.string.pref_key_auto_scan_enabled);
        assertFalse(mKeyboardEventManager.onKeyEvent(autoScanKeyEvent, mRunAction, null));
        verify(mMockAutoScanController, times(0)).autoScanActivated(false);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }
    @Test
    public void testAutoScanKeyEnable_activatesAutoScan() {
        KeyEvent autoScanKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C);
        setupPreferenceForAction(autoScanKeyEvent, R.string.pref_key_mapped_to_auto_scan_key);
        setupBooleanPreference(true, R.string.pref_key_auto_scan_enabled);
        assertTrue(mKeyboardEventManager.onKeyEvent(autoScanKeyEvent, mRunAction, null));
        verify(mMockAutoScanController, times(1)).autoScanActivated(false);
        verifyNoMoreInteractions(mMockOptionManger, mMockAutoScanController);
    }

    @Test
    public void testAutoScanKeyUp_returnsTrueWithoutDoingAnything() {
        KeyEvent autoScanKeyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C);
        setupPreferenceForAction(autoScanKeyEvent, R.string.pref_key_mapped_to_auto_scan_key);
        setupBooleanPreference(true, R.string.pref_key_auto_scan_enabled);
        assertTrue(mKeyboardEventManager.onKeyEvent(autoScanKeyEvent, mRunAction, null));
        verifyZeroInteractions(mMockOptionManger, mMockAutoScanController);
    }

    private void setupPreferenceForAction(KeyEvent event, int preferenceKeyId) {
        String preferenceKey = mContext.getString(preferenceKeyId);
        long extendedKeyCode = KeyComboPreference.keyEventToExtendedKeyCode(event);
        mSharedPreferences.edit().putLong(preferenceKey, extendedKeyCode).commit();
        mKeyboardEventManager.reloadPreferences(mContext);
    }
    private void setupBooleanPreference(boolean value, int preferenceId) {
        String preferenceKey = mContext.getString(preferenceId);
        mSharedPreferences.edit().putBoolean(preferenceKey, value).commit();
        mKeyboardEventManager.reloadPreferences(mContext);
    }
}
