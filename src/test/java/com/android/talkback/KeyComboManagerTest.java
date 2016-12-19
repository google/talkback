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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;

import com.android.talkback.KeyComboManager;
import com.android.talkback.KeyboardShortcutDialogPreference;
import com.android.talkback.keyboard.DefaultKeyComboModel;
import com.android.talkback.keyboard.KeyComboModel;
import com.android.talkback.keyboard.KeyComboModelApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.Override;

@Config(
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
public class KeyComboManagerTest {

    private static long KEY_EVENT_DOWN_TIME = 0;
    private static long KEY_EVENT_EVENT_TIME = 0;

    private KeyComboManager mKeyComboManager;

    @Before
    public void setUp() {
        mKeyComboManager = KeyComboManager.create(
                RuntimeEnvironment.application.getApplicationContext());
    }

    @Test
    public void isEligibleKeyCombo_allowAltOrCtrl() {
        long altQ = makeKeyDown(true, false, false, KeyEvent.KEYCODE_Q);
        assertTrue(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(altQ));

        long altEsc = makeKeyDown(true, false, false, KeyEvent.KEYCODE_ESCAPE);
        assertTrue(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(altEsc));

        long ctrlAltEnter = makeKeyDown(true, true, false, KeyEvent.KEYCODE_ENTER);
        assertTrue(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(ctrlAltEnter));

        long ctrlBackspace = makeKeyDown(false, true, false, KeyEvent.KEYCODE_DEL);
        assertTrue(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(ctrlBackspace));
    }

    @Test
    public void isEligibleKeyCombo_rejectIfNeitherAltNorCtrl() {
        long q = makeKeyDown(false, false, false, KeyEvent.KEYCODE_Q);
        assertFalse(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(q));

        long esc = makeKeyDown(false, false, false, KeyEvent.KEYCODE_ESCAPE);
        assertFalse(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(esc));

        long eight = makeKeyDown(false, false, false, KeyEvent.KEYCODE_8);
        assertFalse(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(eight));

        long shiftNine = makeKeyDown(false, false, true, KeyEvent.KEYCODE_9);
        assertFalse(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(shiftNine));
    }

    @Test
    public void isEligibleKeyCombo_rejectSearch() {
        long search = makeKeyDown(false, false, false, KeyEvent.KEYCODE_SEARCH);
        assertFalse(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(search));
    }

    @Test
    public void isEligibleKeyCombo_allowUnassigned() {
        long unassigned = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
        assertTrue(mKeyComboManager.getKeyComboModel().isEligibleKeyComboCode(unassigned));
    }

    @Test
    public void onKeyEvent_withoutModifier() {
        addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), KeyComboModelApp.class);

        // Search key is not consumed.
        KeyEvent keyEventMeta = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMeta));
    }

    @Test
    public void onKeyEvent_modifier() {
        setTestKeyComboModel();
        FakeKeyComboListener fakeKeyComboListener = addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        // Key down event is not consumed if modifier condition is not met.
        KeyEvent keyEventDpadRightDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyComboModel.NO_MODIFIER);
        assertFalse(mKeyComboManager.onKeyEvent(keyEventDpadRightDown));
        KeyEvent keyEventDpadRightUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyComboModel.NO_MODIFIER);
        assertFalse(mKeyComboManager.onKeyEvent(keyEventDpadRightUp));

        // Key down event will be consumed if modifier condition is met.
        KeyEvent keyEventDpadRightDownWithMeta = new KeyEvent(KEY_EVENT_DOWN_TIME,
                KEY_EVENT_EVENT_TIME, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0,
                KeyEvent.META_META_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventDpadRightDownWithMeta));
        KeyEvent keyEventDpadRightUpWithMeta = new KeyEvent(KEY_EVENT_DOWN_TIME,
                KEY_EVENT_EVENT_TIME, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0,
                KeyEvent.META_META_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventDpadRightUpWithMeta));
        assertEquals(KeyComboManager.ACTION_NAVIGATE_NEXT,
                fakeKeyComboListener.mLastPerformedActionId);

        // Press meta first and dpad right later.
        fakeKeyComboListener.mLastPerformedActionId = KeyComboManager.ACTION_UNKNOWN;
        KeyEvent keyEventMetaDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);
        KeyEvent keyEventMetaUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0, KeyComboModel.NO_MODIFIER);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDown));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventDpadRightDownWithMeta));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventDpadRightUpWithMeta));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaUp));
        assertEquals(KeyComboManager.ACTION_NAVIGATE_NEXT,
                fakeKeyComboListener.mLastPerformedActionId);
    }

    @Test
    public void onKeyEvent_consumeKeyEventWithModifier() {
        setTestKeyComboModel();
        addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        KeyEvent keyEventMetaDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);
        KeyEvent keyEventMetaUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0, KeyComboModel.NO_MODIFIER);
        KeyEvent keyEventXDownWithMeta = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0, KeyEvent.META_META_ON);
        KeyEvent keyEventXUpWithMeta = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_X, 0, KeyEvent.META_META_ON);

        // User presses meta first and x later. All key event will be consumed when modifier
        // condition is met.
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDown));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventXDownWithMeta));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventXUpWithMeta));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaUp));
    }

    @Test
    public void onKeyEvent_pressSearchTwice() {
        setTestKeyComboModel();
        addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        KeyEvent keyEventMetaDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);
        KeyEvent keyEventMetaUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);

        // First key event is consumed.
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDown));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaUp));

        // Second key event is not consumed.
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaDown));
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaUp));

        // Third key event is consumed.
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDown));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaUp));
    }

    @Test
    public void onKeyEvent_pressSearchTwiceSlowly() {
        int delay = 1000; // ms
        int short_delay = 10; // ms

        setTestKeyComboModel();
        addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        KeyEvent keyEventMetaDownFirst = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);
        KeyEvent keyEventMetaUpFirst = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);

        // First key event is consumed.
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDownFirst));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaUpFirst));

        KeyEvent keyEventMetaDownSecond = new KeyEvent(KEY_EVENT_DOWN_TIME + delay,
                KEY_EVENT_EVENT_TIME, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0,
                KeyEvent.META_META_ON);
        KeyEvent keyEventMetaUpSecond = new KeyEvent(KEY_EVENT_DOWN_TIME + delay,
                KEY_EVENT_EVENT_TIME, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0,
                KeyEvent.META_META_ON);

        // Second key event is also consumed.
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDownSecond));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaUpSecond));

        KeyEvent keyEventMetaDownThird = new KeyEvent(KEY_EVENT_DOWN_TIME + delay + short_delay,
                KEY_EVENT_EVENT_TIME, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0,
                KeyEvent.META_META_ON);
        KeyEvent keyEventMetaUpThird = new KeyEvent(KEY_EVENT_DOWN_TIME + delay + short_delay,
                KEY_EVENT_EVENT_TIME, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0,
                KeyEvent.META_META_ON);

        // When user has pressed again immediately after the second key event, detect it as double
        // tap
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaDownThird));
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaUpThird));
    }

    @Test
    public void onKeyEvent_specialKeyHandlingForHomeAndBack() {
        setTestKeyComboModel();
        FakeKeyComboListener fakeKeyComboListener = addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        KeyEvent keyEventHomeDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 0, KeyEvent.META_META_LEFT_ON);
        KeyEvent keyEventHomeUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 0, KeyEvent.META_META_LEFT_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventHomeDown));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventHomeUp));
        assertEquals(KeyComboManager.ACTION_PERFORM_CLICK,
                fakeKeyComboListener.mLastPerformedActionId);

        fakeKeyComboListener.mLastPerformedActionId = KeyComboManager.ACTION_UNKNOWN;

        KeyEvent keyEventBackDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, KeyEvent.META_META_LEFT_ON);
        KeyEvent keyEventBackUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, KeyEvent.META_META_LEFT_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventBackDown));
        assertTrue(mKeyComboManager.onKeyEvent(keyEventBackUp));
        assertEquals(KeyComboManager.ACTION_BACK, fakeKeyComboListener.mLastPerformedActionId);
    }

    @Test
    public void onKeyEvent_doNotConsumeKeyEventsIfNoKeyComboMatches() {
        setTestKeyComboModel();
        FakeKeyComboListener fakeKeyComboListener = addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        // Key down: Search
        KeyEvent keyEventMetaDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0, KeyEvent.META_META_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventMetaDown));

        // Key down: A with Search.
        KeyEvent keyEventMetaADown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_META_ON);
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaADown));

        // No action is performed.
        assertEquals(KeyComboManager.ACTION_UNKNOWN, fakeKeyComboListener.mLastPerformedActionId);

        // Key up: A with Search.
        KeyEvent keyEventMetaAUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_META_ON);
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaAUp));

        // Key up: Search.
        KeyEvent keyEventMetaUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0, 0);
        assertFalse(mKeyComboManager.onKeyEvent(keyEventMetaUp));
    }

    @Test
    public void onKeyEvent_handleCorrespondingKeyEvents_performedCombo() {
        mKeyComboManager.setTalkBackServiceStateHelperForTesting(
                new TalkBackServiceStateHelperTest());
        FakeKeyComboListener fakeKeyComboListener = addFakeListener();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), KeyComboModelApp.class);

        // Key down: Alt + Shift + Enter.
        KeyEvent keyEventAltShiftEnterDown = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventAltShiftEnterDown));

        // Confirm that click action is performed.
        assertEquals(KeyComboManager.ACTION_PERFORM_CLICK,
                fakeKeyComboListener.mLastPerformedActionId);

        // Set match key combo to false.
        mKeyComboManager.setMatchKeyCombo(false);

        // Key up: Alt + Shift + Enter. This must be consumed since corresponding down event is
        // consumed.
        KeyEvent keyEventAltShiftEnterUp = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        assertTrue(mKeyComboManager.onKeyEvent(keyEventAltShiftEnterUp));
    }

    @Test
    public void getKeyComboStringRepresentation_withoutTriggerModifier() {
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), KeyComboModelApp.class);

        // Unassigned.
        Context context = RuntimeEnvironment.application.getApplicationContext();
        String resultUnassigned = mKeyComboManager.getKeyComboStringRepresentation(
                KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);
        String expectedUnassigned = context.getString(R.string.keycombo_unassigned);
        assertEquals(expectedUnassigned, resultUnassigned);

        // A.
        long keyComboCodeA = KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER,
                KeyEvent.KEYCODE_A);
        String resultA = mKeyComboManager.getKeyComboStringRepresentation(keyComboCodeA);
        String expectedA = KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A);
        assertEquals(expectedA, resultA);

        // Alt + Shift.
        long keyComboCodeAltShift = KeyComboManager.getKeyComboCode(
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SHIFT_LEFT);
        String resultAltShift =
                mKeyComboManager.getKeyComboStringRepresentation(keyComboCodeAltShift);
        String expectedAltShift = context.getString(R.string.keycombo_key_modifier_alt) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_modifier_shift);
        assertEquals(expectedAltShift, resultAltShift);

        // Alt + Shift + A.
        long keyComboCodeAltShiftA = KeyComboManager.getKeyComboCode(
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_A);
        String resultAltShiftA = mKeyComboManager.getKeyComboStringRepresentation(
                keyComboCodeAltShiftA);
        String expectedAltShiftA = context.getString(R.string.keycombo_key_modifier_alt) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_modifier_shift) +
                KeyComboManager.CONCATINATION_STR +
                KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A);
        assertEquals(expectedAltShiftA, resultAltShiftA);

        // Alt + Shift + Arrow Right.
        long keyComboCodeAltShiftArrowRight = KeyComboManager.getKeyComboCode(
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
        String resultAltShiftArrowRight =
                mKeyComboManager.getKeyComboStringRepresentation(keyComboCodeAltShiftArrowRight);
        String expectedAltShiftArrowRight = context.getString(R.string.keycombo_key_modifier_alt) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_modifier_shift) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_arrow_right);
        assertEquals(expectedAltShiftArrowRight, resultAltShiftArrowRight);
    }

    @Test
    public void getKeyComboStringRepresentation_withTriggerModifier() {
        setTestKeyComboModel();
        assertEquals(mKeyComboManager.getKeyComboModel().getClass(), TestKeyComboModel.class);

        // Unassigned.
        Context context = RuntimeEnvironment.application.getApplicationContext();
        String resultUnassigned = mKeyComboManager.getKeyComboStringRepresentation(
                KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);
        String expectedUnassigned = context.getString(R.string.keycombo_unassigned);
        assertEquals(expectedUnassigned, resultUnassigned);

        // A.
        long keyComboCodeA = KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER,
                KeyEvent.KEYCODE_A);
        String resultA = mKeyComboManager.getKeyComboStringRepresentation(keyComboCodeA);
        String expectedA = KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A);
        assertEquals(expectedA, resultA);

        // Alt + Ctrl.
        long keyComboCodeAltCtrl = KeyComboManager.getKeyComboCode(
                KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_CTRL_LEFT);
        String resultAltCtrl = mKeyComboManager.getKeyComboStringRepresentation(
                keyComboCodeAltCtrl);
        String expectedAltCtrl = context.getString(R.string.keycombo_key_modifier_alt) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_modifier_ctrl);
        assertEquals(expectedAltCtrl, resultAltCtrl);

        // Search + Ctrl.
        long keyComboCodeSearchCtrl = KeyComboManager.getKeyComboCode(
                KeyEvent.META_META_ON | KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_CTRL_LEFT);
        String resultSearchCtrl = mKeyComboManager.getKeyComboStringRepresentation(
                keyComboCodeSearchCtrl);
        String expectedSearchCtrl = context.getString(R.string.keycombo_key_modifier_meta) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_modifier_ctrl);
        assertEquals(expectedSearchCtrl, resultSearchCtrl);

        // Search + A.
        long keyComboCodeSearchA = KeyComboManager.getKeyComboCode(
                KeyEvent.META_META_ON, KeyEvent.KEYCODE_A);
        String resultSearchA = mKeyComboManager.getKeyComboStringRepresentation(
                keyComboCodeSearchA);
        String expectedSearchA = context.getString(R.string.keycombo_key_modifier_meta) +
                KeyComboManager.CONCATINATION_STR +
                KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A);
        assertEquals(expectedSearchA, resultSearchA);

        // Search + Ctrl + A.
        long keyComboCodeSearchCtrlA = KeyComboManager.getKeyComboCode(
                KeyEvent.META_META_ON | KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_A);
        String resultSearchCtrlA = mKeyComboManager.getKeyComboStringRepresentation(
                keyComboCodeSearchCtrlA);
        String expectedSearchCtrlA = context.getString(R.string.keycombo_key_modifier_meta) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_modifier_ctrl) +
                KeyComboManager.CONCATINATION_STR +
                KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A);
        assertEquals(expectedSearchCtrlA, resultSearchCtrlA);

        // Search + Arrow Right.
        long keyComboCodeSearchArrowRight = KeyComboManager.getKeyComboCode(
                KeyEvent.META_META_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
        String resultSearchArrowRight =
                mKeyComboManager.getKeyComboStringRepresentation(keyComboCodeSearchArrowRight);
        String expectedSearchArrowRight = context.getString(R.string.keycombo_key_modifier_meta) +
                KeyComboManager.CONCATINATION_STR +
                context.getString(R.string.keycombo_key_arrow_right);
        assertEquals(expectedSearchArrowRight, resultSearchArrowRight);
    }

    private long makeKeyDown(boolean alt, boolean ctrl, boolean shift, int keyCode) {
        int meta = 0;
        if (alt) {
            meta |= KeyEvent.META_ALT_ON;
        }
        if (ctrl) {
            meta |= KeyEvent.META_CTRL_ON;
        }
        if (shift) {
            meta |= KeyEvent.META_SHIFT_ON;
        }
        return KeyComboManager.getKeyComboCode(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode,
                0, meta));
    }

    private void setTestKeyComboModel() {
        mKeyComboManager.setKeyComboModel(new TestKeyComboModel(
                RuntimeEnvironment.application.getApplicationContext()));
        mKeyComboManager.setTalkBackServiceStateHelperForTesting(
                new TalkBackServiceStateHelperTest());
    }

    private FakeKeyComboListener addFakeListener() {
        FakeKeyComboListener fakeKeyComboListener = new FakeKeyComboListener();
        mKeyComboManager.addListener(fakeKeyComboListener);
        return fakeKeyComboListener;
    }

    private class FakeKeyComboListener implements KeyComboManager.KeyComboListener {
        public int mLastPerformedActionId = KeyComboManager.ACTION_UNKNOWN;

        @Override
        public boolean onComboPerformed(int id) {
            mLastPerformedActionId = id;
            return true;
        }
    }

    /**
     * TestKeyComboModel is a DefaultKeyComboModel whose trigger modifier key is set to Search key.
     *
     * TODO: remove this class once trigger modifier key of DefaultKeyComboModel becomes
     *               configurable.
     */
    private class TestKeyComboModel extends DefaultKeyComboModel {
        public TestKeyComboModel(Context context) {
            super(context);
        }

        @Override
        public int getTriggerModifier() {
            return KeyEvent.META_META_ON;
        }
    }

    private class TalkBackServiceStateHelperTest implements
            KeyComboManager.TalkBackServiceStateHelper {
        @Override
        public boolean isServiceActive() {
            return true;
        }
    }

}
