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
import android.test.suitebuilder.annotation.MediumTest;
import android.view.KeyEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.SpeechController;

import com.google.android.marvin.talkback.TalkBackService;

import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import java.util.ArrayList;
import java.util.List;

public class SpeechControllerTest extends TalkBackInstrumentationTestCase {

    private TalkBackService mTalkBack;
    private Instrumentation mInstrumentation;
    private SpeechController mSpeechController;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);

        mSpeechController = getService().getSpeechController();
    }

    @Override
    protected void tearDown() throws Exception {
        mSpeechController.setSpeechListener(null);
        super.tearDown();
    }

    @MediumTest
    public void testInterrupt_interruptsTts() {
        speak("Peter Piper picked a peck of pickled peppers.");

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mSpeechController.isSpeakingOrSpeechQueued());

        mTalkBack.getSpeechController().interrupt();

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertFalse(mSpeechController.isSpeakingOrSpeechQueued());
    }

    @MediumTest
    public void testLeftCtrlKey_interruptsTts() {
        speak("Peter Piper picked a peck of pickled peppers.");

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mSpeechController.isSpeakingOrSpeechQueued());

        sendCtrlCombo(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_UNKNOWN);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertFalse(mSpeechController.isSpeakingOrSpeechQueued());
    }

    @MediumTest
    public void testRightCtrlKey_interruptsTts() {
        speak("Peter Piper picked a peck of pickled peppers.");

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mSpeechController.isSpeakingOrSpeechQueued());

        sendCtrlCombo(KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_UNKNOWN);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertFalse(mSpeechController.isSpeakingOrSpeechQueued());
    }

    @MediumTest
    public void testCtrlZCombo_doesNotInterruptTts() {
        speak("Peter Piper picked a peck of pickled peppers.");

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mSpeechController.isSpeakingOrSpeechQueued());

        sendCtrlCombo(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_Z);

        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mSpeechController.isSpeakingOrSpeechQueued());
    }

    @MediumTest
    public void testDuplicates_skipDuplicates() {
        startRecordingRawSpeech();
        speakQueued("Duplicate", FeedbackItem.FLAG_SKIP_DUPLICATE);
        speakQueued("Duplicate", FeedbackItem.FLAG_SKIP_DUPLICATE);
        speakQueued("Unique", FeedbackItem.FLAG_SKIP_DUPLICATE);
        speakQueued("Duplicate", FeedbackItem.FLAG_SKIP_DUPLICATE);
        speakQueued("Unique", FeedbackItem.FLAG_SKIP_DUPLICATE);

        assertEquals(2, getRawSpeechHistory().size());
    }

    @MediumTest
    public void testDuplicates_keepDuplicates() {
        startRecordingRawSpeech();
        speakQueued("Duplicate", 0);
        speakQueued("Duplicate", 0);
        speakQueued("Unique", 0);
        speakQueued("Duplicate", 0);
        speakQueued("Unique", 0);

        assertEquals(5, getRawSpeechHistory().size());
    }

    public void testLastUtterance() {
        // Wait for "TalkBack on".
        mInstrumentation.waitForIdleSync();

        // Clear the speech queue.
        mSpeechController.interrupt();
        mInstrumentation.waitForIdleSync();

        // The text "Google" should speak and appear in history.
        startRecordingRawSpeech();
        speakWithHistory("Google");
        stopRecordingAndAssertRawSpeech("Google");
    }

    public void testSpellLastUtterance() {
        // Wait for "TalkBack on".
        mInstrumentation.waitForIdleSync();

        // Clear the speech queue.
        mSpeechController.interrupt();
        mInstrumentation.waitForIdleSync();

        // The text "Google" should speak and appear in history.
        startRecordingRawSpeech(RECORD_STARTED);
        speakWithHistory("Google");

        // Need to wait for "Google" to actually start speaking before spelling.
        stopRecordingAndAssertRawSpeech("Google");

        // Spell and check.
        startRecordingRawSpeech(RECORD_QUEUED);
        mSpeechController.spellLastUtterance();
        stopRecordingAndAssertRawSpeech("capital G, o, o, g, l, e");
    }

    private void speak(String speech) {
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        mSpeechController.speak(speech, SpeechController.QUEUE_MODE_INTERRUPT,
                FeedbackItem.FLAG_NO_HISTORY, null);
    }

    private void speakWithHistory(String speech) {
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        mSpeechController.speak(speech, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
    }

    private void speakQueued(String speech, int flags) {
        mSpeechController.speak(speech, SpeechController.QUEUE_MODE_QUEUE, flags, null);
    }

    /**
     * Emulates the behavior of a user entering a keycombo involving the Ctrl key by first pressing
     * down the Ctrl key, then pressing down another key, then lifting the second key, then lifting
     * the Ctrl key.
     * @param ctrlKeyCode should be one of KeyEvent.KEYCODE_CTRL_LEFT or KeyEvent.KEYCODE_CTRL_RIGHT
     * @param secondKeyCode a second key to form the combo, or KeyEvent.KEYCODE_UNKNOWN to only
     *                      simulate pressing the Ctrl key
     */
    private void sendCtrlCombo(int ctrlKeyCode, int secondKeyCode) {
        KeyEvent ctrlKeyDown = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, ctrlKeyCode, 0,
                KeyEvent.META_CTRL_ON);
        KeyEvent secondKeyDown = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, secondKeyCode, 0,
                KeyEvent.META_CTRL_ON);
        KeyEvent secondKeyUp = new KeyEvent(0, 0, KeyEvent.ACTION_UP, secondKeyCode, 0,
                KeyEvent.META_CTRL_ON);
        KeyEvent ctrlKeyUp = new KeyEvent(0, 0, KeyEvent.ACTION_UP, ctrlKeyCode, 0,
                KeyEvent.META_CTRL_ON);

        mTalkBack.onKeyEventShared(ctrlKeyDown);
        if (secondKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            mTalkBack.onKeyEventShared(secondKeyDown);
            mTalkBack.onKeyEventShared(secondKeyUp);
        }
        mTalkBack.onKeyEventShared(ctrlKeyUp);
    }

}