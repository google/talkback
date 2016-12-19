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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import android.test.suitebuilder.annotation.MediumTest;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.talkback.R;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;
import com.android.utils.AccessibilityNodeInfoUtils;

public class CompoundButtonsTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;
    private NodeSpeechRuleProcessor mNodeProcessor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);

        NodeSpeechRuleProcessor.initialize(mTalkBack);
        mNodeProcessor = NodeSpeechRuleProcessor.getInstance();
    }

    @MediumTest
    public void testToggleButton() throws Throwable {
        setContentView(R.layout.toggle_button);

        assertDescriptionForIdContains(R.id.toggle_button, "off");
        assertDescriptionForIdContains(R.id.toggle_button_with_onoff, "custom_off");
        assertDescriptionForIdContains(R.id.toggle_button_with_desc, "contentDescription", "off");
        assertDescriptionForIdContains(
                R.id.toggle_button_with_desc_onoff, "contentDescription", "custom_off");
        assertDescriptionForIdContains(
                R.id.toggle_button_with_icon, "contentDescription", "checked");
    }

    @MediumTest
    public void testCompoundButtons() throws Throwable {
        setContentView(R.layout.compound_button);

        assertDescriptionForIdContains(R.id.check_box, "check box", "not checked");
        assertDescriptionForIdContains(R.id.check_box_text, "text", "not checked");
        assertDescriptionForIdContains(R.id.checked_text_view, "text", "not checked");
        assertDescriptionForIdContains(R.id.switch_basic, "switch", "off");
        assertDescriptionForIdContains(R.id.switch_text, "text", "off");
        assertDescriptionForIdContains(R.id.switch_text_onoff, "text", "state_off");
        assertDescriptionForIdContains(R.id.switch_desc, "content_description", "off");

        assertDescriptionForIdDoesNotContain(R.id.switch_text_onoff, "null");
    }

    private void assertDescriptionForIdContains(final int viewId,
                                                final CharSequence... partialText) throws Throwable {
        final AccessibilityNodeInfoCompat source = getNodeForId(viewId);
        final AccessibilityNodeInfoCompat announcedNode = AccessibilityNodeInfoUtils
                .findFocusFromHover(source);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                final CharSequence description = mNodeProcessor.getDescriptionForTree(
                        announcedNode, null, source);
                final CharSequenceFilter filter = new CharSequenceFilter().addContainsIgnoreCase(
                        partialText);

                assertTrue("Description \"" + description + "\" for view with id " + viewId
                        + " matches filter " + filter, filter.matches(description));
            }
        });
    }

    private void assertDescriptionForIdDoesNotContain(final int viewId,
                                                      final CharSequence partialText) throws Throwable {
        final AccessibilityNodeInfoCompat source = getNodeForId(viewId);
        final AccessibilityNodeInfoCompat announcedNode = AccessibilityNodeInfoUtils
                .findFocusFromHover(source);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {

                final CharSequence description = mNodeProcessor.getDescriptionForTree(
                        announcedNode, null, source);
                final CharSequenceFilter filter = new CharSequenceFilter().addContainsIgnoreCase(
                        partialText);

                assertFalse("Description \"" + description + "\" for view with id " + viewId
                        + " should not match filter " + filter, filter.matches(description));
            }
        });
    }
}