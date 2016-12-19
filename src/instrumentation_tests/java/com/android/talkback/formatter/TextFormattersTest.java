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

package com.android.talkback.formatter;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.KeyEvent;
import android.widget.EditText;

import com.android.talkback.R;
import com.android.talkback.SpeechCleanupUtils;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;
import com.googlecode.eyesfree.testing.UtteranceFilter;

import java.lang.CharSequence;
import java.util.List;

public class TextFormattersTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;
    private Instrumentation mInstrumentation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        mInstrumentation = getInstrumentation();

        assertNotNull("Obtained TalkBack instance", mTalkBack);
    }

    @MediumTest
    public void testChangedTextFormatter_deleteCharacter() throws Exception {
        internalTestChangedTextFormatter_deleteCharacter("hello");
        internalTestChangedTextFormatter_deleteCharacter("hello ");
    }

    private void internalTestChangedTextFormatter_deleteCharacter(String text) throws Exception {
        if (text.length() < 1) {
            throw new IllegalArgumentException("This test case can only handle non-empty strings");
        }

        // Set up the test.
        setContentView(R.layout.text_activity);
        getViewForId(R.id.username).requestFocus();
        mInstrumentation.waitForIdleSync();
        mInstrumentation.sendStringSync(text);
        waitForAccessibilityIdleSync();

        // Delete the last character.
        startRecordingUtterances();
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);
        waitForAccessibilityIdleSync();

        final CharSequence lastChar = text.substring(text.length() - 1);
        final CharSequence expectedSpeech = SpeechCleanupUtils.cleanUp(mAppCtx, lastChar) + " "
                + mAppCtx.getString(R.string.value_text_removed);
        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                expectedSpeech);
        final UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);

        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testSelectedTextFormatter_moveCursorWithKeyboard() throws Exception {
        setContentView(R.layout.text_activity);
        getViewForId(R.id.username).requestFocus();
        mInstrumentation.waitForIdleSync();
        mInstrumentation.sendStringSync("abc");
        waitForAccessibilityIdleSync();

        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_LEFT, "c");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_LEFT, "b");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_RIGHT, "b");
    }

    private void checkKeyEventProcessing(int keyCode, String expectedUtterance) {
        startRecordingUtterances();
        mInstrumentation.sendKeyDownUpSync(keyCode);
        waitForAccessibilityIdleSync();

        CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                expectedUtterance);
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);

        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testSelectedTextFormatter_moveCursorWithVolumeButtons() {
        setContentView(R.layout.text_activity);
        getViewForId(R.id.username).requestFocus();
        mInstrumentation.waitForIdleSync();
        mInstrumentation.sendStringSync("abc");
        waitForAccessibilityIdleSync();

        mTalkBack.getCursorController().setCursor(getNodeForId(R.id.username));

        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "c");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "b");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "b");
    }

    /**
     * Volume button simulation needs to be handled separately from key event simulation because the
     * normal Android system instrumentation will just change the system volume instead of
     * delivering the volume key events to TalkBack.
     */
    private void sendVolumeKey(int keyCode) {
        assertTrue(keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN);

        long down = SystemClock.uptimeMillis();
        KeyEvent keyDown = new KeyEvent(down, down, KeyEvent.ACTION_DOWN, keyCode, 0, 0);
        mTalkBack.onKeyEventShared(keyDown);

        long up = SystemClock.uptimeMillis();
        KeyEvent keyUp = new KeyEvent(down, up, KeyEvent.ACTION_UP, keyCode, 0, 0);
        mTalkBack.onKeyEventShared(keyUp);
    }

    private void checkVolumeButtonProcessing(int keyCode, String expectedUtterance) {
        startRecordingUtterances();

        sendVolumeKey(keyCode);
        waitForAccessibilityIdleSync();

        CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                expectedUtterance);
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);

        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testSelectedTextFormatter_volumeButtonFeedbackNotDuplicated() {
        setContentView(R.layout.text_activity);
        getViewForId(R.id.username).requestFocus();
        mInstrumentation.waitForIdleSync();
        mInstrumentation.sendStringSync("abc");
        waitForAccessibilityIdleSync();

        mTalkBack.getCursorController().setCursor(getNodeForId(R.id.username));
        waitForAccessibilityIdleSync(); // Wait after the ["Edit text", "abc"] feedback.

        startRecordingUtterances();
        sendVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN);
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        sendVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN);
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        sendVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN);
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase("a");
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        stopRecordingUtterancesAfterMatch(utteranceFilter);

        List<Utterance> utteranceHistory = getUtteranceHistory();
        int countB = 0;
        int countC = 0;
        for (Utterance utterance : utteranceHistory) {
            for (CharSequence spoken : utterance.getSpoken()) {
                if (spoken.toString().contains("b")) {
                    countB++;
                }
                if (spoken.toString().contains("c")) {
                    countC++;
                }
            }
        }

        assertEquals(1, countB);
        assertEquals(1, countC);
    }

    @MediumTest
    public void testInputShortText_uninterruptibleUtterance() throws Exception {
        setContentView(R.layout.text_activity);
        startRecordingUtterances();
        getViewForId(R.id.username).requestFocus();
        mInstrumentation.waitForIdleSync();
        mInstrumentation.sendStringSync("a");
        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase("a");
        final UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);
        assertNotNull("Saw matching utterance", utterance);
        assertEquals(SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, utterance.getMetadata().
                getInt(Utterance.KEY_METADATA_QUEUING));
    }
    @MediumTest
    public void testPasswordField_moveCursor() {
        setContentView(R.layout.text_activity);
        waitForAccessibilityIdleSync();

        final EditText passwordTextView = (EditText) getViewForId(R.id.password);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                passwordTextView.setText("abcdefg");
                passwordTextView.requestFocus();
            }
        });
        waitForAccessibilityIdleSync();

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                passwordTextView.setSelection(1, 1);
            }
        });
        waitForAccessibilityIdleSync();

        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "Character 2");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "Character 3");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "Character 4");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "Character 4");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "Character 3");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "Character 2");
    }

    @MediumTest
    public void testPasswordField_moveCursorWithKeyboard() {
        setContentView(R.layout.text_activity);
        waitForAccessibilityIdleSync();

        final EditText passwordTextView = (EditText) getViewForId(R.id.password);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                passwordTextView.setText("abcdefg");
                passwordTextView.requestFocus();
            }
        });
        waitForAccessibilityIdleSync();

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                passwordTextView.setSelection(1, 1);
            }
        });
        waitForAccessibilityIdleSync();

        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_RIGHT, "Character 2");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_RIGHT, "Character 3");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_RIGHT, "Character 4");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_LEFT, "Character 4");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_LEFT, "Character 3");
        checkKeyEventProcessing(KeyEvent.KEYCODE_DPAD_LEFT, "Character 2");
    }

    @MediumTest
    public void testPassword_select() {
        setContentView(R.layout.text_activity);
        waitForAccessibilityIdleSync();

        final EditText passwordTextView = (EditText) getViewForId(R.id.password);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                passwordTextView.setText("abcdefg");
                passwordTextView.requestFocus();
            }
        });
        waitForAccessibilityIdleSync();

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                passwordTextView.setSelection(1, 1);
            }
        });
        waitForAccessibilityIdleSync();

        mTalkBack.getCursorController().setSelectionModeActive(getNodeForView(passwordTextView),
                true);
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "Character 2, selected");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "Character 3, selected");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_UP, "Character 4, selected");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "Character 4, unselected");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "Character 3, unselected");
        checkVolumeButtonProcessing(KeyEvent.KEYCODE_VOLUME_DOWN, "Character 2, unselected");
    }
}
