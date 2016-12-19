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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.os.Build;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.talkback.BuildConfig;
import com.android.talkback.R;
import com.android.switchaccess.KeyComboPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.res.Attribute;
import org.robolectric.shadows.RoboAttributeSet;
import org.robolectric.shadows.ShadowToast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Robolectric tests for KeyComboPreference
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class KeyComboPreferenceTest {

    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();

    private final ListView mKeysListView = new ListView(mContext);

    private final Button mResetButton = new Button(mContext);

    private final View mMockView = mock(View.class);

    private SharedPreferences mSharedPrefs;
    private PreferenceManager mMockPrefMgr;

    @Before
    public void setUp() {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        when(mMockView.findViewById(R.id.key_combo_preference_key_list)).
                thenReturn(mKeysListView);
        when(mMockView.findViewById(R.id.key_combo_preference_reset_button)).
                thenReturn(mResetButton);

        mMockPrefMgr = mock(PreferenceManager.class);
        when(mMockPrefMgr.getSharedPreferences()).thenReturn(mSharedPrefs);
    }

    @Test
    public void testGetExtendedKeyCode_basic() {
        KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0);
        long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(event);
        assertEquals(event.getKeyCode(), (int) keyCode);
    }

    @Test
    public void testGetExtendedKeyCode_withMetaKeys() {
        /* Confirm that extra bits are set when meta keys are pressed */
        final int baseKeyCode = KeyEvent.KEYCODE_A;
        KeyEvent eventWithAlt = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, baseKeyCode, 0,
                KeyEvent.META_ALT_ON);
        KeyEvent eventWithSftAlt = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, baseKeyCode, 0,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        KeyEvent eventWithCtrlSftAlt = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, baseKeyCode, 0,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON);
        long keyCodeWithAlt = KeyComboPreference.keyEventToExtendedKeyCode(eventWithAlt);
        long keyCodeWithSftAlt = KeyComboPreference.keyEventToExtendedKeyCode(eventWithSftAlt);
        long keyCodeWithCtrlSftAlt =
                KeyComboPreference.keyEventToExtendedKeyCode(eventWithCtrlSftAlt);

        assertTrue(keyCodeWithAlt > baseKeyCode);
        assertTrue(keyCodeWithSftAlt > keyCodeWithAlt);
        assertTrue(keyCodeWithCtrlSftAlt > keyCodeWithSftAlt);
    }

    @Test
    public void testGetKeyCodesForPreference_withLegacyLongPreference() {
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(keyEvent);
        mSharedPrefs.edit().putLong(
                mContext.getString(R.string.pref_key_mapped_to_click_key), keyCode).apply();
        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        long firstKeycode = keyCodesForPreference.iterator().next();
        assertEquals(keyCode, firstKeycode);
    }

    @Test
    public void testGetKeyCodesForPreference_withStringSetPreference() {
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(keyEvent);
        Set<String> stringSet = new HashSet<>(Arrays.asList((new Long(keyCode)).toString()));
        mSharedPrefs.edit().putStringSet(
                mContext.getString(R.string.pref_key_mapped_to_click_key), stringSet).apply();
        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        assertEquals(1, keyCodesForPreference.size());
        assertEquals(keyCode, (long) keyCodesForPreference.iterator().next());
    }

    @Test
    public void testNoKeysConfigured_showsNoKeysInSummary() {
        TestableKeyComboPreference keyComboPreference =
                buildPreference(R.string.pref_key_mapped_to_click_key, R.string.action_name_click);
        String summary = (String) keyComboPreference.getSummary();
        assertTrue(summary.contains("0"));
    }

    @Test
    public void testMultipleKeysConfigured_showsNumberOfKeysInSummary() {
        KeyEvent keyEventZ = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z);
        KeyEvent keyEventY = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y);
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click,
                keyEventZ, keyEventY);

        String summary = (String) keyComboPreference.getSummary();
        assertFalse(summary.contains("Z"));
        assertFalse(summary.contains("Y"));
        assertTrue(summary.contains("2"));
    }

    @Test
    public void testDialogClosedWithOK_internalListenerNotified() {
        TestableKeyComboPreference keyComboPreference =
                buildPreference(R.string.pref_key_mapped_to_click_key, R.string.action_name_click);

        keyComboPreference.mNotifyChangedCalls = 0;
        keyComboPreference.callOnDialogClosed(true);
        assertEquals(1, keyComboPreference.mNotifyChangedCalls);
    }

    @Test
    public void testPreferenceReset_clearsPreferences() {
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        KeyEvent keyEventB = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click,
                keyEventA, keyEventB);

        mResetButton.performClick();
        keyComboPreference.callOnDialogClosed(true);

        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        assertEquals(0, keyCodesForPreference.size());
    }

    @Test
    public void testKeysConfiguredInDialog_savedInPreference() {
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click);
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        KeyEvent keyEventB = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        Long keyCodeA = KeyComboPreference.keyEventToExtendedKeyCode(keyEventA);
        Long keyCodeB = KeyComboPreference.keyEventToExtendedKeyCode(keyEventB);

        keyComboPreference.onKey((DialogInterface) null, keyCodeA.intValue(), keyEventA);
        keyComboPreference.onKey((DialogInterface) null, keyCodeB.intValue(), keyEventB);
        keyComboPreference.callOnDialogClosed(true);

        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        assertEquals(2, keyCodesForPreference.size());
        assertTrue(keyCodesForPreference.contains(keyCodeA));
        assertTrue(keyCodesForPreference.contains(keyCodeB));
    }

    @Test
    public void testKeysConfiguredInDialog_withLegacyConfigured_savedInPreference() {
        /* Configured legacy sharedpreference for 'A' */
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        Long keyCodeA = KeyComboPreference.keyEventToExtendedKeyCode(keyEventA);
        String prefKey = mContext.getString(R.string.pref_key_mapped_to_click_key);
        mSharedPrefs.edit().putLong(prefKey, keyCodeA).apply();

        /* Build the preference */
        TestableKeyComboPreference keyComboPreference =
                new TestableKeyComboPreference(mContext, null);
        keyComboPreference.setKey(prefKey);
        keyComboPreference.attachToHierarchy();
        keyComboPreference.onBindDialogViewPublic(mMockView);
        keyComboPreference.setTitle(R.string.action_name_click);

        /* Send a 'B' key to the preference to add to the legacy A */
        KeyEvent keyEventB = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        Long keyCodeB = KeyComboPreference.keyEventToExtendedKeyCode(keyEventB);
        keyComboPreference.onKey((DialogInterface) null, keyCodeB.intValue(), keyEventB);
        keyComboPreference.callOnDialogClosed(true);

        /* Both A and B should be configured */
        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        assertEquals(2, keyCodesForPreference.size());
        assertTrue(keyCodesForPreference.contains(keyCodeA));
        assertTrue(keyCodesForPreference.contains(keyCodeB));
    }

    @Test
    public void testKeysInPreference_areRemoved() {
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click, keyEventA);

        keyComboPreference.onKey((DialogInterface) null, keyEventA.getKeyCode(), keyEventA);
        keyComboPreference.callOnDialogClosed(true);

        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        assertEquals(0, keyCodesForPreference.size());
    }

    @Test
    public void testKeysInPreference_canBeReAdded() {
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click, keyEventA);

        keyComboPreference.onKey((DialogInterface) null, keyEventA.getKeyCode(), keyEventA);
        keyComboPreference.onKey((DialogInterface) null, keyEventA.getKeyCode(), keyEventA);
        keyComboPreference.callOnDialogClosed(true);

        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_click_key);
        assertEquals(1, keyCodesForPreference.size());
        assertTrue(keyCodesForPreference
                .contains(KeyComboPreference.keyEventToExtendedKeyCode(keyEventA)));
    }

    @Test
    public void testKeysAssignedToOtherActions_shouldNotBeAddedToPreference() {
        TestableKeyComboPreference keyComboPreference =
                buildPreference(R.string.pref_key_mapped_to_next_key, R.string.action_name_next);

        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        TestableKeyComboPreference otherKeyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click, keyEventA);

        keyComboPreference.onKey((DialogInterface) null, keyEventA.getKeyCode(), keyEventA);
        keyComboPreference.callOnDialogClosed(true);

        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, R.string.pref_key_mapped_to_next_key);
        assertEquals(0, keyCodesForPreference.size());
    }

    @Test
    public void testKeysAssignedToOtherActions_shouldShowToast() {
        TestableKeyComboPreference keyComboPreference =
                buildPreference(R.string.pref_key_mapped_to_next_key, R.string.action_name_next);

        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        TestableKeyComboPreference otherKeyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click, keyEventA);

        ShadowToast.reset();
        keyComboPreference.onKey((DialogInterface) null, keyEventA.getKeyCode(), keyEventA);

        assertEquals(1, ShadowToast.shownToastCount());
    }

    @Test
    public void testDpadKeys_shouldBeIgnored() {
        TestableKeyComboPreference keyComboPreference =
                buildPreference(R.string.pref_key_mapped_to_click_key, R.string.action_name_click);
        KeyEvent dpadCenterEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
        KeyEvent dpadDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        KeyEvent dpadUpEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP);
        KeyEvent dpadRightEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
        KeyEvent dpadLeftEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT);
        KeyEvent backEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);

        assertFalse(keyComboPreference
                .onKey((DialogInterface) null, dpadCenterEvent.getKeyCode(), dpadCenterEvent));
        assertFalse(keyComboPreference
                .onKey((DialogInterface) null, dpadDownEvent.getKeyCode(), dpadDownEvent));
        assertFalse(keyComboPreference
                .onKey((DialogInterface) null, dpadUpEvent.getKeyCode(), dpadUpEvent));
        assertFalse(keyComboPreference
                .onKey((DialogInterface) null, dpadRightEvent.getKeyCode(), dpadRightEvent));
        assertFalse(keyComboPreference
                .onKey((DialogInterface) null, dpadLeftEvent.getKeyCode(), dpadLeftEvent));
        assertFalse(keyComboPreference
                .onKey((DialogInterface) null, backEvent.getKeyCode(), backEvent));
    }

    @Test
    public void testListView_isEmptyWhenNothingIsConfigured() {
        buildPreference(R.string.pref_key_mapped_to_click_key, R.string.action_name_click);

        assertEquals(0, mKeysListView.getAdapter().getCount());
    }

    @Test
    public void testListView_containsPreConfiguredKeys() {
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        KeyEvent keyEventB = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        buildPreference(R.string.pref_key_mapped_to_click_key, R.string.action_name_click,
                keyEventA, keyEventB);

        assertEquals(2, mKeysListView.getAdapter().getCount());
    }

    @Test
    public void testListView_addsNewKeysWhenPressed() {
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        KeyEvent keyEventB = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click,
                keyEventA, keyEventB);

        KeyEvent keyEventC = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C);
        keyComboPreference.onKey((DialogInterface) null, keyEventC.getKeyCode(), keyEventC);

        assertEquals(3, mKeysListView.getAdapter().getCount());
    }

    @Test
    public void testListView_removesExistingKeysWhenPressed() {
        KeyEvent keyEventA = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        KeyEvent keyEventB = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);
        TestableKeyComboPreference keyComboPreference = buildPreference(
                R.string.pref_key_mapped_to_click_key, R.string.action_name_click,
                keyEventA, keyEventB);

        keyComboPreference.onKey((DialogInterface) null, keyEventB.getKeyCode(), keyEventB);

        assertEquals(1, mKeysListView.getAdapter().getCount());
    }

    private TestableKeyComboPreference buildPreference(
            int prefKeyId, int prefTitleId, KeyEvent... keyEvents) {
        String prefKey = mContext.getString(prefKeyId);

        Set<String> stringSet = new HashSet<>();
        for (KeyEvent keyEvent : keyEvents) {
            long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(keyEvent);
            stringSet.add((new Long(keyCode)).toString());
        }
        mSharedPrefs.edit().putStringSet(prefKey, stringSet).apply();

        // Sanity check
        Set<Long> keyCodesForPreference = KeyComboPreference.getKeyCodesForPreference(
                mContext, prefKeyId);
        assertEquals(keyEvents.length, keyCodesForPreference.size());

        TestableKeyComboPreference keyComboPreference =
                new TestableKeyComboPreference(mContext, null);
        keyComboPreference.setKey(prefKey);
        keyComboPreference.attachToHierarchy();
        keyComboPreference.onBindDialogViewPublic(mMockView);
        keyComboPreference.setTitle(mContext.getString(prefTitleId));
        return keyComboPreference;
    }

    class TestableKeyComboPreference extends KeyComboPreference {

        public int mNotifyChangedCalls = 0;

        public TestableKeyComboPreference(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public void callOnDialogClosed(boolean result) {
            onDialogClosed(result);
        }

        public void onBindDialogViewPublic(View view) {
            onBindDialogView(view);
        }

        public void attachToHierarchy() {
            onAttachedToHierarchy(mMockPrefMgr);
            when(mMockPrefMgr.findPreference(getKey())).thenReturn(this);
        }

        @Override
        protected void notifyChanged() {
            super.notifyChanged();
            mNotifyChangedCalls++;
        }
    }
}
