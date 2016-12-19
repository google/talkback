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

package com.android.talkback.menurules;

import com.google.android.marvin.talkback.TalkBackService;

import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.talkback.R;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ListMenu;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import java.util.List;

/**
 * Tests that the RuleSeekBar and its value/percentage conversion works.
 */
public class RuleSeekBarTest extends TalkBackInstrumentationTestCase {

    private TalkBackService mTalkBack;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTalkBack = getService();
    }

    @MediumTest
    public void testMenuAcceptedOrRejected() {
        setContentView(R.layout.seek_bar_test);

        AccessibilityNodeInfoCompat seekBar = getNodeForId(R.id.seek_bar_40);
        RuleSeekBar ruleSeekBar = new RuleSeekBar();
        boolean accepted = ruleSeekBar.accept(mTalkBack, seekBar);

        // Accept if and only if at least N.
        assertEquals(BuildCompat.isAtLeastN(), accepted);
    }

    @MediumTest
    public void testMenuPopulated_atLeastN() {
        if (!BuildCompat.isAtLeastN()) {
            return;
        }

        setContentView(R.layout.seek_bar_test);

        AccessibilityNodeInfoCompat seekBar = getNodeForId(R.id.seek_bar_40);
        RuleSeekBar ruleSeekBar = new RuleSeekBar();
        ListMenu listMenu = new ListMenu(mTalkBack);
        List<ContextMenuItem> items = ruleSeekBar.getMenuItemsForNode(mTalkBack,
                listMenu.getMenuItemBuilder(), seekBar);

        // There should be one item (set seek control level).
        assertEquals(1, items.size());
    }

    @MediumTest
    public void testSetValue_atLeastN() {
        if (!BuildCompat.isAtLeastN()) {
            return;
        }

        setContentView(R.layout.seek_bar_test);

        AccessibilityNodeInfoCompat seekBar = getNodeForId(R.id.seek_bar_40);

        // 25% of 40 = 10.
        RuleSeekBar.setProgress(seekBar, 25);
        waitForAccessibilityIdleSync();
        seekBar.refresh();
        assertEquals(10.0f, seekBar.getRangeInfo().getCurrent());

        // 100% of 40 = 50.
        RuleSeekBar.setProgress(seekBar, 100);
        waitForAccessibilityIdleSync();
        seekBar.refresh();
        assertEquals(40.0f, seekBar.getRangeInfo().getCurrent());

        // Should keep current value for > 100%.
        RuleSeekBar.setProgress(seekBar, 0); // Set to 0 first.
        waitForAccessibilityIdleSync();

        RuleSeekBar.setProgress(seekBar, 120); // Set to > 100 but it should stay at 0.
        waitForAccessibilityIdleSync();

        seekBar.refresh();
        assertEquals(0.0f, seekBar.getRangeInfo().getCurrent());
    }
}
