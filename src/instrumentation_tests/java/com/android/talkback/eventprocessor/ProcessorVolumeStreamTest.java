/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.talkback.eventprocessor;

import android.content.SharedPreferences;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.talkback.R;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.DimScreenController;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.volumebutton.VolumeButtonPatternDetector;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class ProcessorVolumeStreamTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;
    private CursorController mCursorController;
    private ProcessorVolumeStream mProcessorVolumeStream;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        mCursorController = mTalkBack.getCursorController();
        FeedbackController feedbackController = mTalkBack.getFeedbackController();
        CursorController cursorController = mTalkBack.getCursorController();

        DimScreenController dimScreenController = mTalkBack.getDimScreenController();

        mProcessorVolumeStream = new ProcessorVolumeStream(feedbackController, cursorController,
                dimScreenController, mTalkBack);

        // Disable dim confirmation so it doesn't interfere with testing.
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getActivity());
        SharedPreferencesUtils.putBooleanPref(prefs, getActivity().getResources(),
                R.string.pref_show_dim_screen_confirmation_dialog, false);

        assertNotNull("Obtained TalkBack instance", mTalkBack);
        assertNotNull("Obtained CursorController instance", mCursorController);
        assertNotNull("Obtained ProcessorVolumeStream instance", mProcessorVolumeStream);
    }

    @MediumTest
    public void testScrollSeekBarWithVolumeButton() {
        setContentView(R.layout.scroll_seek_bar_with_volume_key_test);

        AccessibilityNodeInfoCompat seekBar = getNodeForId(R.id.seek_bar);
        mCursorController.setCursor(seekBar);
        waitForAccessibilityIdleSync();

        // The progress of the SeekBar has already been set to 99%.
        // Scroll the SeekBar from 99% to 100%.
        mProcessorVolumeStream.onPatternMatched(VolumeButtonPatternDetector.SHORT_PRESS_PATTERN,
                VolumeButtonPatternDetector.VOLUME_UP);
        waitForAccessibilityIdleSync();

        // Scroll again the SeekBar.
        // It should not navigate to another node.
        mProcessorVolumeStream.onPatternMatched(VolumeButtonPatternDetector.SHORT_PRESS_PATTERN,
                VolumeButtonPatternDetector.VOLUME_UP);
        waitForAccessibilityIdleSync();
        assertEquals(seekBar, mCursorController.getCursor());
    }
}