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

import static org.junit.Assert.assertNotEquals;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.LayoutDirection;
import android.view.KeyEvent;
import android.widget.TextView;

import com.android.talkback.keyboard.DefaultKeyComboModel;
import com.android.talkback.keyboard.KeyComboModel;
import com.android.talkback.keyboard.KeyComboModelApp;
import com.android.talkback.keyboard.KeyComboPersister;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

// TODO: Make this test not to run below this API level.
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class KeyboardShortcutDialogPreferenceTest extends TalkBackInstrumentationTestCase {

    private Activity mPreferencesActivity = null;

    private class KeyAcceptedTestData {
        public boolean escape;
        public boolean enter;
        public boolean backspace;
        public boolean A;
    }

    @Override
    protected void tearDown() throws Exception {
        if (mPreferencesActivity != null) {
            mPreferencesActivity.finish();
            mPreferencesActivity = null;
        }

        SharedPreferences pref = SharedPreferencesUtils.getSharedPreferences(getActivity());
        pref.edit().clear().commit();

        super.tearDown();
    }

    @MediumTest
    public void testKeyAccepted() throws Throwable {
        setContentView(R.layout.keyboard_shortcut_dialog);

        final KeyboardShortcutDialogPreference pref = new KeyboardShortcutDialogPreference(
                getService(), null);
        final KeyAcceptedTestData data = new KeyAcceptedTestData();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pref.onBindDialogView(getViewForId(android.R.id.content));

                KeyEvent escape = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE,
                        0);
                KeyEvent enter = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER,
                        0);
                KeyEvent backspace = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL,
                        0);
                KeyEvent A = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A,
                        0);

                synchronized (data) {
                    data.escape = pref.onKey(null, KeyEvent.KEYCODE_ESCAPE, escape);
                    data.enter = pref.onKey(null, KeyEvent.KEYCODE_ENTER, enter);
                    data.backspace = pref.onKey(null, KeyEvent.KEYCODE_DEL, backspace);
                    data.A = pref.onKey(null, KeyEvent.KEYCODE_A, A);
                }
            }
        });

        getInstrumentation().waitForIdleSync();

        synchronized (data) {
            assertFalse(data.escape); // Escape should be trapped and used to close the dialog.
            assertFalse(data.enter); // Enter should be trapped and used to close the dialog.
            assertTrue(data.backspace); // Backspace should be accepted but clears the shortcut.
            assertTrue(data.A); // A should be accepted.
        }
    }

    private class BackspaceUnassignsShortcutTestData {
        public String textAfterA;
        public String textAfterBackspace;
    }

    @MediumTest
    public void testBackspaceUnassignsShortcut() {
        setContentView(R.layout.keyboard_shortcut_dialog);

        final KeyboardShortcutDialogPreference pref = new KeyboardShortcutDialogPreference(
                getService(), null);
        final BackspaceUnassignsShortcutTestData data = new BackspaceUnassignsShortcutTestData();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pref.onBindDialogView(getViewForId(android.R.id.content));

                KeyEvent backspace = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL,
                        0);
                KeyEvent A = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A,
                        0);

                TextView assignedCombo = (TextView) getViewForId(R.id.assigned_combination);

                synchronized (data) {
                    pref.onKey(null, KeyEvent.KEYCODE_A, A);
                    data.textAfterA = assignedCombo.getText().toString();

                    pref.onKey(null, KeyEvent.KEYCODE_DEL, backspace);
                    data.textAfterBackspace = assignedCombo.getText().toString();
                }
            }
        });

        getInstrumentation().waitForIdleSync();

        final String unassigned = KeyComboManager.create(getService())
                .getKeyComboStringRepresentation(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);

        synchronized (data) {
            assertNotEquals(unassigned, data.textAfterA); // Label shouldn't be "unassigned".
            assertEquals(unassigned, data.textAfterBackspace); // Label should be "unassigned".
        }
    }

    public void testAssignKeyComboWithClassicKeymap() {
        setKeymapToClassicKeymap();
        assertAssignKeyCombo(
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_DPAD_RIGHT),
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_X),
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_X),
                new KeyComboPersister(getActivity(), null /* no prefix */));
    }

    public void testAssignKeyComboWithDefaultKeymap() {
        setKeymapToDefaultKeymap();
        assertAssignKeyCombo(
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_RIGHT),
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_A),
                KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_A),
                new KeyComboPersister(getActivity(), DefaultKeyComboModel.PREF_KEY_PREFIX));
    }

    public void testErrorKeyComboWithoutTriggerModifierWithDefaultKeymap() {
        setKeymapToDefaultKeymap();

        startTalkBackKeyboardShortcutPreferencesActivity();
        assertNotNull(mPreferencesActivity);

        KeyboardShortcutDialogPreference dialogPreference =
                openNavigateNextDialogPreference(mPreferencesActivity);

        KeyComboManager keyComboManager = TalkBackService.getInstance().getKeyComboManager();

        // Try to set x.
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_RIGHT,
                keyComboManager, dialogPreference);
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X, keyComboManager);
        assertAssignedKeyComboText(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X, keyComboManager,
                dialogPreference);

        // Try to set it and confirm that error message is announced.
        startRecordingUtterances();
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);
        stopRecordingAndAssertUtterance(
                keyComboManager.getKeyComboModel().getDescriptionOfEligibleKeyCombo());

        // Confirm that key combo is not changed.
        assertEquals(KeyComboManager.getKeyComboCode(
                        KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DPAD_RIGHT),
                keyComboManager.getKeyComboModel().getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_next)));
    }

    @MediumTest
    public void testUnassignKeyComboWithDefaultKeyMap() {
        setKeymapToDefaultKeymap();

        startTalkBackKeyboardShortcutPreferencesActivity();
        assertNotNull(mPreferencesActivity);

        KeyboardShortcutDialogPreference dialogPreference =
                openNavigateNextDialogPreference(mPreferencesActivity);

        KeyComboManager keyComboManager = TalkBackService.getInstance().getKeyComboManager();

        // Assert that current key combo is ALT + DPAD RIGHT.
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_RIGHT,
                keyComboManager, dialogPreference);

        // Press backspace.
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DEL, keyComboManager);

        // Assert that key combo has been changed to unassigned.
        int modifier = KeyComboManager.getModifier(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);
        int keyCode = KeyComboManager.getKeyCode(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);
        assertAssignedKeyComboText(modifier, keyCode, keyComboManager, dialogPreference);

        // Press enter.
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Confirm that new key combo has been made persistent.
        KeyComboPersister persister = new KeyComboPersister(getActivity(),
                DefaultKeyComboModel.PREF_KEY_PREFIX);
        assertEquals(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED,
                persister.getKeyComboCode(getActivity().getString(
                        R.string.keycombo_shortcut_navigate_next)).longValue());
    }

    public void testOverwriteKeyComboDialogWithDefaultKeymap() {
        setKeymapToDefaultKeymap();

        KeyComboManager keyComboManager = TalkBackService.getInstance().getKeyComboManager();

        // Confirm that DPAD LEFT is used for navigate previous.
        assertEquals(getActivity().getString(R.string.keycombo_shortcut_navigate_previous),
                keyComboManager.getKeyComboModel().getKeyForKeyComboCode(
                        KeyComboManager.getKeyComboCode(
                                KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DPAD_LEFT)));

        startTalkBackKeyboardShortcutPreferencesActivity();
        assertNotNull(mPreferencesActivity);

        KeyboardShortcutDialogPreference dialogPreference =
                openNavigateNextDialogPreference(mPreferencesActivity);

        // Set ALT + DPAD LEFT.
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_RIGHT,
                keyComboManager, dialogPreference);
        sendKeyEventDownAndUp(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_LEFT,
                keyComboManager);
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_LEFT,
                keyComboManager, dialogPreference);

        // Try to set it. Overwrite confirm dialog will come up.
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Select cancel with keyboard. Default focus is on cancel button.
        // Note: do this operation with keyboard to test the dialog can handle key events properly.
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Confirm that key combo is not changed.
        assertEquals(KeyComboManager.getKeyComboCode(
                        KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DPAD_RIGHT),
                keyComboManager.getKeyComboModel().getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_next)));
        assertEquals(KeyComboManager.getKeyComboCode(
                        KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DPAD_LEFT),
                keyComboManager.getKeyComboModel().getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_previous)));

        // Confirm that ALT + DPAD LEFT is still set in the dialog, and try again.
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_LEFT,
                keyComboManager, dialogPreference);
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Press OK. Since default focus is on cancel button, we need to move focus to OK button.
        int keyToSelectOK =
                getLayoutDirection(mPreferencesActivity.findViewById(android.R.id.content)) ==
                        LayoutDirection.RTL ?
                        KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, keyToSelectOK, keyComboManager);
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Confirm that key combos are changed.
        assertEquals(KeyComboManager.getKeyComboCode(
                        KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DPAD_LEFT),
                keyComboManager.getKeyComboModel().getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_next)));
        assertEquals(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED,
                keyComboManager.getKeyComboModel().getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_previous)));
    }

    public void testCancelKeyComboChangeWithDefaultKeymap() {
        setKeymapToDefaultKeymap();

        startTalkBackKeyboardShortcutPreferencesActivity();
        assertNotNull(mPreferencesActivity);

        KeyboardShortcutDialogPreference dialogPreference =
                openNavigateNextDialogPreference(mPreferencesActivity);

        // Change key combo to ALT + x.
        KeyComboManager keyComboManager = TalkBackService.getInstance().getKeyComboManager();
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_DPAD_RIGHT,
                keyComboManager, dialogPreference);
        sendKeyEventDownAndUp(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_X, keyComboManager);
        assertAssignedKeyComboText(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_X,
                keyComboManager, dialogPreference);

        // Press ESC to cancel.
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ESCAPE, keyComboManager);

        // Confirm that key combo is not changed.
        assertEquals(KeyComboManager.getKeyComboCode(
                        KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_DPAD_RIGHT),
                keyComboManager.getKeyComboModel().getKeyComboCodeForKey(
                        getActivity().getString(R.string.keycombo_shortcut_navigate_next)));
    }

    public void testKeyComboPreferenceIsDisabledIfTalkBackIsOffWithDefaultKeymap() {
        setKeymapToDefaultKeymap();

        startTalkBackKeyboardShortcutPreferencesActivity();
        assertNotNull(mPreferencesActivity);

        // Navigate next preference is enabled if TalkBack is on.
        assertEquals(TalkBackService.SERVICE_STATE_ACTIVE, TalkBackService.getServiceState());
        assertTrue(isNavigateNextPreferenceEnabled(mPreferencesActivity));

        // Navigate next preference is disabled if TalkBack is not on.
        disableAllServices();
        assertEquals(TalkBackService.SERVICE_STATE_INACTIVE, TalkBackService.getServiceState());
        assertFalse(isNavigateNextPreferenceEnabled(mPreferencesActivity));
    }

    /**
     * Asserts that key combo can be changed from keyComboCodeBefore to keyComboCodeAfter.
     */
    private void assertAssignKeyCombo(long keyComboCodeBeforeWithModifier,
                                      long keyComboCodeAfterWithModifier,
                                      long keyComboCodeAfterWithoutModifier,
                                      KeyComboPersister persister) {
        startTalkBackKeyboardShortcutPreferencesActivity();
        assertNotNull(mPreferencesActivity);

        KeyboardShortcutDialogPreference dialogPreference =
                openNavigateNextDialogPreference(mPreferencesActivity);

        KeyComboManager keyComboManager = TalkBackService.getInstance().getKeyComboManager();

        // Assert current key combo.
        assertAssignedKeyComboText(KeyComboManager.getModifier(keyComboCodeBeforeWithModifier),
                KeyComboManager.getKeyCode(keyComboCodeBeforeWithModifier),
                keyComboManager, dialogPreference);

        // Press new key combo.
        sendKeyEventDownAndUp(KeyComboManager.getModifier(keyComboCodeAfterWithModifier),
                KeyComboManager.getKeyCode(keyComboCodeAfterWithModifier), keyComboManager);

        // Assert that key combo has been changed to new key combo.
        assertAssignedKeyComboText(KeyComboManager.getModifier(keyComboCodeAfterWithModifier),
                KeyComboManager.getKeyCode(keyComboCodeAfterWithModifier),
                keyComboManager, dialogPreference);

        // Press enter.
        sendKeyEventDownAndUp(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_ENTER, keyComboManager);

        // Confirm that new key combo has been made persistent.
        assertEquals(KeyComboManager.getKeyComboCode(
                        KeyComboManager.getModifier(keyComboCodeAfterWithoutModifier),
                        KeyComboManager.getKeyCode(keyComboCodeAfterWithoutModifier)),
                persister.getKeyComboCode(getActivity().getString(
                        R.string.keycombo_shortcut_navigate_next)).longValue());
    }

    /**
     * Sets keymap to classic keymap.
     */
    private void setKeymapToClassicKeymap() {
        setKeymap(R.string.classic_keymap_entry_value, new KeyComboModelApp(getActivity()));
    }

    /**
     * Sets keymap to default keymap.
     */
    private void setKeymapToDefaultKeymap() {
        setKeymap(R.string.default_keymap_entry_value, new DefaultKeyComboModel(getActivity()));
    }

    /**
     * Sets keymap to specified one.
     */
    private void setKeymap(int keymapEntryValue, KeyComboModel keyComboModel) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        pref.edit().putString(
                getActivity().getString(R.string.pref_select_keymap_key),
                getActivity().getString(keymapEntryValue)).commit();

        TalkBackService.getInstance().getKeyComboManager().setKeyComboModel(keyComboModel);
    }

    /**
     * Starts TalkBackKeyboardShortcutPreferencesActivity. The activity will be set to
     * mPreferencesActivity. This test case automatically finish the activity in its tearDown.
     */
    private void startTalkBackKeyboardShortcutPreferencesActivity() {
        Intent intent = new Intent(getActivity(),
                TalkBackKeyboardShortcutPreferencesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPreferencesActivity = getInstrumentation().startActivitySync(intent);
    }

    /**
     * Opens dialog preference of navigate next shortcut.
     */
    private KeyboardShortcutDialogPreference openNavigateNextDialogPreference(
            Activity preferenceActivity) {
        OpenNavigateNextDialogPreferenceRunnable runnable =
                new OpenNavigateNextDialogPreferenceRunnable(preferenceActivity);
        getInstrumentation().runOnMainSync(runnable);
        getInstrumentation().waitForIdleSync();
        return runnable.dialog;
    }

    /**
     * Asserts current assigned key combo text in dialog.
     */
    private void assertAssignedKeyComboText(int modifier, int keyCode,
                                            KeyComboManager keyComboManager,
                                            KeyboardShortcutDialogPreference dialogPreference) {
        GetAssignedCombinationRunnable runnable =
                new GetAssignedCombinationRunnable(dialogPreference);
        getInstrumentation().runOnMainSync(runnable);
        getInstrumentation().waitForIdleSync();

        assertEquals(keyComboManager.getKeyComboStringRepresentation(
                        KeyComboManager.getKeyComboCode(modifier, keyCode)),
                runnable.assignedCombinationText);
    }

    /**
     * Gets whether navigate next preference is enabled or not.
     */
    private boolean isNavigateNextPreferenceEnabled(Activity activity) {
        GetNavigateNextPreferenceIsEnabledRunnable runnable =
                new GetNavigateNextPreferenceIsEnabledRunnable(activity);
        getInstrumentation().runOnMainSync(runnable);
        getInstrumentation().waitForIdleSync();

        return runnable.mEnabled;
    }

    private static class OpenNavigateNextDialogPreferenceRunnable implements Runnable {
        private final Activity mActivity;
        public KeyboardShortcutDialogPreference dialog;

        public OpenNavigateNextDialogPreferenceRunnable(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void run() {
            PreferenceFragment fragment =
                    (PreferenceFragment) mActivity.getFragmentManager().findFragmentById(
                            android.R.id.content);
            dialog = (KeyboardShortcutDialogPreference) fragment.findPreference(
                    mActivity.getString(R.string.keycombo_shortcut_navigate_next));
            dialog.showDialog(new Bundle());
        }
    }

    private static class GetAssignedCombinationRunnable implements Runnable {
        private final KeyboardShortcutDialogPreference mDialogPreference;
        public String assignedCombinationText;

        GetAssignedCombinationRunnable(KeyboardShortcutDialogPreference dialogPreference) {
            mDialogPreference = dialogPreference;
        }

        @Override
        public void run() {
            TextView assignedCombination = (TextView) mDialogPreference.getDialog().findViewById(
                    R.id.assigned_combination);
            assignedCombinationText = assignedCombination.getText().toString();
        }
    }



    private static class GetNavigateNextPreferenceIsEnabledRunnable implements Runnable {
        private final Activity mActivity;
        public boolean mEnabled;

        public GetNavigateNextPreferenceIsEnabledRunnable(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void run() {
            PreferenceFragment preferenceFragment =
                    (PreferenceFragment) mActivity.getFragmentManager().findFragmentById(
                            android.R.id.content);
            DialogPreference dialogPreference =
                    (DialogPreference) preferenceFragment.findPreference(
                            mActivity.getString(R.string.keycombo_shortcut_navigate_next));
            mEnabled = dialogPreference.isEnabled();
        }
    }

}
