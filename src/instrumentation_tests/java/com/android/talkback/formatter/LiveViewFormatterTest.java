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
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import com.android.talkback.Utterance;
import com.android.talkback.R;

import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;
import com.googlecode.eyesfree.testing.UtteranceFilter;

public class LiveViewFormatterTest extends TalkBackInstrumentationTestCase {

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
    public void testView_pronounceLiveRegionWithNonFocusableChildren() throws Exception {
        setContentView(R.layout.live_region);
        getViewForId(R.id.root).setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);

        startRecordingUtterances();
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                "text_one, text_two");
        final UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);

        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testView_pronounceRootViewContentDescription() throws Exception {
        setContentView(R.layout.live_region);
        final View root = getViewForId(R.id.root);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                root.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
            }
        });
        waitForAccessibilityIdleSync();

        startRecordingUtterances();
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                root.setContentDescription("Content description");
            }
        });
        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                "Content description");
        final UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);

        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testView_notIncludeLiveNonFocusableChildren() throws Exception {
        setContentView(R.layout.live_region);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getViewForId(R.id.text_one).setFocusable(true);
            }
        });
        mInstrumentation.waitForIdleSync();
        startRecordingUtterances();
        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter = new CharSequenceFilter().addContainsIgnoreCase(
                "text_one");
        final UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);

        assertNull(utterance);
    }
}
