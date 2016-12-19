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

import android.app.AlertDialog;
import android.test.suitebuilder.annotation.MediumTest;
import com.android.talkback.Utterance;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;
import com.googlecode.eyesfree.testing.UtteranceFilter;

public class DialogFormatterTest extends TalkBackInstrumentationTestCase {

    @MediumTest
    public void testOpenDialogWithTitle_pronounceTitle() throws Throwable {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startRecordingUtterances();

                new AlertDialog.Builder(getActivity())
                        .setTitle("Title")
                        .setMessage("Message")
                        .show();
            }
        });
        getInstrumentation().waitForIdleSync();

        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                "Title");
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);
        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testOpenDialogNoTitle_pronounceMessage() throws Throwable {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startRecordingUtterances();

                new AlertDialog.Builder(getActivity())
                        .setMessage("Message")
                        .show();
            }
        });
        getInstrumentation().waitForIdleSync();

        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                "Message");
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);
        assertNotNull("Saw matching utterance", utterance);
    }
}
