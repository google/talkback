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

package com.android.talkback.contextmenu;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.EditText;

import com.android.talkback.R;

import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class ListMenuPreparerTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);

        prepareTestLayout();
    }

    @MediumTest
    public void testAccessibilityFocusedOnly_shouldShowLocalMenu() {
        requestAccessibilityFocus(R.id.username);

        ListMenu menu = new ListMenu(mTalkBack);
        ListMenuPreparer listPreparer = new ListMenuPreparer(mTalkBack);
        listPreparer.prepareMenu(menu, R.menu.local_context_menu);

        assertTrue(menu.size() > 0);
    }

    @MediumTest
    public void testInputFocusedOnly_shouldShowLocalMenu() {
        requestInputFocus(R.id.username);

        ListMenu menu = new ListMenu(mTalkBack);
        ListMenuPreparer listPreparer = new ListMenuPreparer(mTalkBack);
        listPreparer.prepareMenu(menu, R.menu.local_context_menu);

        assertTrue(menu.size() > 0);
    }

    @MediumTest
    public void testBothAccessibilityAndInputFocus_shouldShowLocalMenu() {
        requestInputFocus(R.id.username);
        requestAccessibilityFocus(R.id.username);

        ListMenu menu = new ListMenu(mTalkBack);
        ListMenuPreparer listPreparer = new ListMenuPreparer(mTalkBack);
        listPreparer.prepareMenu(menu, R.menu.local_context_menu);

        assertTrue(menu.size() > 0);
    }

    @MediumTest
    public void testNoFocus_shouldNotShowLocalMenu() {
        clearInputFocus();

        ListMenu menu = new ListMenu(mTalkBack);
        ListMenuPreparer listPreparer = new ListMenuPreparer(mTalkBack);
        listPreparer.prepareMenu(menu, R.menu.local_context_menu);

        assertTrue(menu.size() == 0);
    }

    private void prepareTestLayout() {
        setContentView(R.layout.text_activity);
        final EditText usernameEditText = (EditText) getViewForId(R.id.username);

        // Scroll the list view down to display the last item before beginning the test.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                usernameEditText.setText("Sample text");
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();
    }

    private void requestAccessibilityFocus(int viewId) {
        final AccessibilityNodeInfoCompat node = getNodeForId(viewId);
        mTalkBack.getCursorController().setCursor(node);
        waitForAccessibilityIdleSync();
    }

    private void requestInputFocus(int viewId) {
        final View view = getViewForId(viewId);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                view.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();
    }

    private void clearInputFocus() {
        // Android always wants to keep the focus somewhere if possible.
        // So we need to redirect the focus to a dummy view with no content.
        // Furthermore, TalkBack will try to make the accessibility focus follow the input focus,
        // so we will need to clear the accessibility focus afterwards.
        final View view = getViewForId(R.id.dummy);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                view.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        mTalkBack.getCursorController().clearCursor();
        waitForAccessibilityIdleSync();
    }

}