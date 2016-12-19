/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;

import com.android.talkback.keyboard.DefaultKeyComboModel;
import com.android.talkback.keyboard.KeyComboModel;
import com.android.talkback.keyboard.KeyComboModelApp;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

/**
 * TalkBackKeyboardShortcutPreferencesActivity test.
 */
public class TalkBackKeyboardShortcutPreferencesActivityTest
        extends TalkBackInstrumentationTestCase {
    private TalkBackKeyboardShortcutPreferencesActivity mPreferencesActivity;

    @Override
    protected void tearDown() throws Exception {
        if (mPreferencesActivity != null) {
            mPreferencesActivity.finish();
            mPreferencesActivity = null;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        pref.edit().clear().commit();

        super.tearDown();
    }

    public void testResetKeymapWithClassicKeymap() {
        assertKeymapIsClassicKeymap();

        long customKeyComboCode = KeyComboManager.getKeyComboCode(
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_X);
        long defaultKeyComboCode = KeyComboManager.getKeyComboCode(
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertResetKeymap(customKeyComboCode, customKeyComboCode, defaultKeyComboCode,
                defaultKeyComboCode);
    }

    public void testResetKeymapWithDefaultKeymap() {
        setKeymapToDefaultKeymap();

        assertResetKeymap(
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_X),
                KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X),
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_RIGHT),
                KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER,
                        KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    // TODO: add test cases to reset keymap while TalkBack is off.

    public void testUnassignedKeyComboWithClassicKeymap() {
        assertKeymapIsClassicKeymap();

        assertSummaryOfUnassignedKeyCombo();
    }

    public void testUnassignedKeyComboWithDefaultKeymap() {
        setKeymapToDefaultKeymap();

        assertSummaryOfUnassignedKeyCombo();
    }

    private void assertKeymapIsClassicKeymap() {
        TalkBackService talkBackService = TalkBackService.getInstance();
        assertNotNull(talkBackService);

        KeyComboManager keyComboManager = talkBackService.getKeyComboManager();
        assertEquals(KeyComboModelApp.class, keyComboManager.getKeyComboModel().getClass());
    }

    private void setKeymapToDefaultKeymap() {
        TalkBackService talkbackService = TalkBackService.getInstance();
        assertNotNull(talkbackService);

        KeyComboManager keyComboManager = talkbackService.getKeyComboManager();
        DefaultKeyComboModel keyComboModel = new DefaultKeyComboModel(getActivity());
        keyComboManager.setKeyComboModel(keyComboModel);
    }

    private void startTalkBackKeyboardShortcutPreferencesActivity() {
        Intent intent = new Intent(getActivity(),
                TalkBackKeyboardShortcutPreferencesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPreferencesActivity = (TalkBackKeyboardShortcutPreferencesActivity) getInstrumentation()
                .startActivitySync(intent);
    }

    private void assertResetKeymap(long customKeyComboCodeWithTriggerModifier,
                                   long customKeyComboCodeWithoutTriggerModifier,
                                   long defaultKeyComboCodeWithTriggerModifier,
                                   long defaultKeyComboCodeWithoutTriggerModifier) {
        TalkBackService talkBackService = TalkBackService.getInstance();
        assertNotNull(talkBackService);
        KeyComboManager keyComboManager = talkBackService.getKeyComboManager();
        KeyComboModel keyComboModel = keyComboManager.getKeyComboModel();

        // Change key combo code to custom one.
        keyComboModel.saveKeyComboCode(
                getActivity().getString(R.string.keycombo_shortcut_navigate_next),
                customKeyComboCodeWithoutTriggerModifier);

        // Start keyboard shortcut preference activity.
        startTalkBackKeyboardShortcutPreferencesActivity();

        // Confirm UI before reset.
        String expectedBeforeReset = keyComboManager.getKeyComboStringRepresentation(
                customKeyComboCodeWithTriggerModifier);
        assertEquals(expectedBeforeReset, getDialogPreferenceSummary(
                getActivity().getString(R.string.keycombo_shortcut_navigate_next)));

        // Click reset keymap.
        ClickResetKeymapRunnable clickResetKeymapRunnable = new ClickResetKeymapRunnable();
        getInstrumentation().runOnMainSync(clickResetKeymapRunnable);
        getInstrumentation().waitForIdleSync();

        // Select reset in confirm dialog.
        startRecordingUtterances();
        int keyCodeToSelectReset =
                getLayoutDirection(mPreferencesActivity.findViewById(android.R.id.content)) ==
                        View.LAYOUT_DIRECTION_RTL ?
                        KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, keyCodeToSelectReset, keyComboManager);
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Confirm that feedback is announced for resetting the keymap.
        stopRecordingAndAssertUtterance(
                getActivity().getString(R.string.keycombo_menu_announce_reset_keymap));

        // Confirm that key combo is reset to default in the UI.
        String expectedAfterReset = keyComboManager.getKeyComboStringRepresentation(
                defaultKeyComboCodeWithTriggerModifier);
        assertEquals(expectedAfterReset, getDialogPreferenceSummary(
                getActivity().getString(R.string.keycombo_shortcut_navigate_next)));

        // Confirm that key combo code is reset to default in the model.
        assertEquals(defaultKeyComboCodeWithoutTriggerModifier,
                keyComboModel.getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_next)));
    }

    private void assertSummaryOfUnassignedKeyCombo() {
        TalkBackService talkBackService = TalkBackService.getInstance();
        assertNotNull(talkBackService);
        KeyComboModel keyComboModel = talkBackService.getKeyComboManager().getKeyComboModel();

        // Set key combo code for navigating to next item as unassigned.
        keyComboModel.saveKeyComboCode(
                getActivity().getString(R.string.keycombo_shortcut_navigate_next),
                KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);

        startTalkBackKeyboardShortcutPreferencesActivity();

        // Confirm that the summary is set to unassigned.
        assertEquals(getActivity().getString(R.string.keycombo_unassigned),
                getDialogPreferenceSummary(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_next)));
    }

    /**
     * Gets summary of specified key's dialog preference.
     */
    private String getDialogPreferenceSummary(String key) {
        GetDialogPreferenceSummaryRunnable runnable = new GetDialogPreferenceSummaryRunnable(key);
        getInstrumentation().runOnMainSync(runnable);
        getInstrumentation().waitForIdleSync();
        return runnable.mSummary;
    }

    private class GetDialogPreferenceSummaryRunnable implements Runnable {
        private final String mKey;
        public String mSummary;

        public GetDialogPreferenceSummaryRunnable(String key) {
            mKey = key;
        }

        @Override
        public void run() {
            PreferenceFragment preferenceFragment = (PreferenceFragment) mPreferencesActivity
                    .getFragmentManager().findFragmentById(android.R.id.content);
            DialogPreference dialogPreference = (DialogPreference) preferenceFragment
                    .findPreference(mKey);
            mSummary = dialogPreference.getSummary().toString();
        }
    }

    private class ClickResetKeymapRunnable implements Runnable {
        @Override
        public void run() {
            TalkBackKeyboardShortcutPreferencesActivity.TalkBackKeyboardShortcutPreferenceFragment
                    preferenceFragment =
                    (TalkBackKeyboardShortcutPreferencesActivity.
                            TalkBackKeyboardShortcutPreferenceFragment) mPreferencesActivity
                            .getFragmentManager().findFragmentById(android.R.id.content);
            preferenceFragment.performClickOnResetKeymapForTesting();
        }
    }
}
