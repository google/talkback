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

package com.android.talkback.controller;

import static org.junit.Assert.assertNotEquals;

import com.google.android.marvin.talkback.TalkBackService;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.talkback.CursorGranularity;
import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.Utterance;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.traversal.TraversalStrategy;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;
import com.googlecode.eyesfree.testing.UtteranceFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CursorControllerAppTest extends TalkBackInstrumentationTestCase {

    private TalkBackService mTalkBack;
    private CursorController mCursorController;
    private List <AccessibilityNodeInfoCompat> mObtainedNodes;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTalkBack = getService();
        mCursorController = mTalkBack.getCursorController();
        mObtainedNodes = new ArrayList<>();

        // We don't want to include the action bar in our traversals.
        final Activity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.getActionBar().hide();
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        AccessibilityNodeInfoUtils.recycleNodes(mObtainedNodes);
    }

    @MediumTest
    public void testGetSetCursor() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat squareButton = getNodeForId(R.id.button_square);
        mCursorController.setCursor(squareButton);
        waitForAccessibilityIdleSync();
        assertEquals(squareButton, mCursorController.getCursor());
    }

    @MediumTest
    public void testGetCursorOrInputCursor() {
        setContentView(R.layout.text_activity);

        AccessibilityNodeInfoCompat usernameEditText = getNodeForId(R.id.username);
        mCursorController.setCursor(usernameEditText);
        waitForAccessibilityIdleSync();

        // Click to open the keyboard.
        mCursorController.clickCurrent();
        waitForAccessibilityIdleSync();

        // Remove accessibility focus from the text field. Input focus should stay, however.
        mCursorController.clearCursor();
        waitForAccessibilityIdleSync();

        assertEquals(usernameEditText, mCursorController.getCursorOrInputCursor());
    }

    @MediumTest
    public void testClearCursor() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat squareButton = getNodeForId(R.id.button_square);
        mCursorController.setCursor(squareButton);
        waitForAccessibilityIdleSync();

        mCursorController.clearCursor();
        waitForAccessibilityIdleSync();
        assertFalse(squareButton.equals(mCursorController.getCursor()));
    }

    @MediumTest
    public void testRefocus() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat squareButton = getNodeForId(R.id.button_square);
        mCursorController.setCursor(squareButton);
        waitForAccessibilityIdleSync();

        mCursorController.refocus();
        waitForAccessibilityIdleSync();
        assertEquals(squareButton, mCursorController.getCursor());
    }

    @MediumTest
    public void testClickCurrent() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        assertFalse(checkBox.isChecked());

        mCursorController.setCursor(checkBox);
        waitForAccessibilityIdleSync();

        mCursorController.clickCurrent();
        waitForAccessibilityIdleSync();

        checkBox.refresh();
        assertTrue(checkBox.isChecked());
    }

    @MediumTest
    public void testMore() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);

        AccessibilityNodeInfoCompat oldTeam = teamsList.getChild(0);
        CollectionItemInfoCompat oldItemInfo = oldTeam.getCollectionItemInfo();
        assertEquals(0, oldItemInfo.getRowIndex());

        mCursorController.setCursor(oldTeam); // Put cursor in the list view so that it will scroll.
        waitForAccessibilityIdleSync();

        mCursorController.more();
        waitForAccessibilityIdleSync();

        // The first item in the list should have scrolled out of view. The first visible item in
        // the list should now be something different.
        AccessibilityNodeInfoCompat newTeam = teamsList.getChild(0);
        CollectionItemInfoCompat newItemInfo = newTeam.getCollectionItemInfo();
        assertNotEquals(0, newItemInfo.getRowIndex());
    }

    @MediumTest
    public void testLess() {
        setContentView(R.layout.cursor_test);

        final ListView teamsListView = (ListView) getViewForId(R.id.teams_list);
        final int lastTeamIndex = teamsListView.getCount() - 1;

        // Scroll the list view down to display the last item before beginning the test.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                teamsListView.setSelection(lastTeamIndex);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);

        AccessibilityNodeInfoCompat oldTeam = teamsList.getChild(teamsList.getChildCount() - 1);
        CollectionItemInfoCompat oldItemInfo = oldTeam.getCollectionItemInfo();
        assertEquals(lastTeamIndex, oldItemInfo.getRowIndex());

        mCursorController.setCursor(oldTeam); // Put cursor in the list view so that it will scroll.
        waitForAccessibilityIdleSync();

        mCursorController.less();
        waitForAccessibilityIdleSync();

        // The last item in the list should have scrolled out of view. The last visible item
        // should be something different.
        teamsList.refresh(); // We need to update the list view's child count.
        AccessibilityNodeInfoCompat newTeam = teamsList.getChild(teamsList.getChildCount() - 1);
        CollectionItemInfoCompat newItemInfo = newTeam.getCollectionItemInfo();
        assertNotEquals(lastTeamIndex, newItemInfo.getRowIndex());
    }

    @MediumTest
    public void testJumpToTop() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);

        mCursorController.jumpToTop(InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat cursor = mCursorController.getCursor();
        assertEquals(firstLabel, cursor);
    }

    @MediumTest
    public void testJumpToBottom() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);

        mCursorController.jumpToBottom(InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat cursor = mCursorController.getCursor();
        assertEquals(lastLabel, cursor);
    }

    @MediumTest
    public void testNext_noWrap_noScroll_noUseInputFocus() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);
        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat squareButton = getNodeForId(R.id.button_square);
        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);

        mCursorController.setCursor(firstLabel);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) text_first_item
        // 1. button_square
        // 2. check_me
        // 3-N. teams_list - visible items only
        // N. text_last_item
        final int teamsVisibleCount = teamsList.getChildCount();
        final int traversals = 3 + teamsVisibleCount; // 3 non-list items + N visible list items.
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_FORWARD,
                traversals, false, false, false);

        assertEquals(squareButton, nodes.get(0));
        assertEquals(checkBox, nodes.get(1));
        assertHasParent(teamsList, nodes.subList(2, traversals - 1));
        assertEquals(lastLabel, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testNext_noWrap_doScroll_noUseInputFocus() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);
        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);

        mCursorController.setCursor(checkBox);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) check_me
        // 1-9. teams_list - all items
        // 10. text_last_item
        final int traversals = 10;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_FORWARD,
                traversals, false, true, false);

        assertHasParent(teamsList, nodes.subList(0, traversals - 1));
        assertEquals(lastLabel, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testNext_doWrap_noScroll_noUseInputFocus() {
        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);
        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);

        mCursorController.setCursor(lastLabel);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) text_last_item
        // 1. text_last_item - first next() pauses on same node
        // 2. text_first_item - second next() should wrap around
        final int traversals = 2;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_FORWARD,
                traversals, true, false, false);

        assertEquals(lastLabel, nodes.get(0));
        assertEquals(firstLabel, nodes.get(1));
    }

    @MediumTest
    public void testNavigateWithGranularity() {
        setContentView(R.layout.cursor_test);

        // Focus to the beginning of page.
        mCursorController.setCursor(getNodeForId(R.id.text_first_item));
        waitForAccessibilityIdleSync();

        // Navigate forward by word.
        startRecordingUtterances();
        assertTrue(mCursorController.nextWithSpecifiedGranularity(CursorGranularity.WORD,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("Beginning");

        startRecordingUtterances();
        assertTrue(mCursorController.nextWithSpecifiedGranularity(CursorGranularity.WORD,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("of");

        startRecordingUtterances();
        assertTrue(mCursorController.nextWithSpecifiedGranularity(CursorGranularity.WORD,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("page");

        // Returns false if user tries to go out from the current focused item.
        assertFalse(mCursorController.nextWithSpecifiedGranularity(CursorGranularity.WORD,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));

        // Navigate backward by word.
        startRecordingUtterances();
        assertTrue(mCursorController.previousWithSpecifiedGranularity(CursorGranularity.WORD,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("page");

        startRecordingUtterances();
        assertTrue(mCursorController.previousWithSpecifiedGranularity(CursorGranularity.WORD,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("of");

        // Navigate forward and backward by character.
        startRecordingUtterances();
        assertTrue(mCursorController.nextWithSpecifiedGranularity(CursorGranularity.CHARACTER,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("o");

        startRecordingUtterances();
        assertTrue(mCursorController.nextWithSpecifiedGranularity(CursorGranularity.CHARACTER,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("f");

        startRecordingUtterances();
        assertTrue(mCursorController.previousWithSpecifiedGranularity(CursorGranularity.CHARACTER,
                    false /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertUtterance("f");

        // Try to go to next item and confirm that above operation doesn't affect to this.
        assertTrue(mCursorController.next(true /* shouldWrap */, true /* shouldScroll */,
                    true /* useInputFocusAsPivotIfEmpty */, InputModeManager.INPUT_MODE_TOUCH));
        waitForAccessibilityIdleSync();
        assertEquals(getNodeForId(R.id.button_square), mCursorController.getCursor());
    }

    @MediumTest
    public void testNext_noWrap_noScroll_doUseInputFocus() {
        setContentView(R.layout.cursor_test);

        // Assign input focus to the button, but don't touch accessibility focus.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View squareButton = getViewForId(R.id.button_square);
                squareButton.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) button_square - has input focus
        // 1. check_me
        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        mCursorController.next(false, false, true, InputModeManager.INPUT_MODE_TOUCH);
        assertEquals(checkBox, mCursorController.getCursor());
    }

    @MediumTest
    public void testNext_horizontal() {
        setContentView(R.layout.cursor_horizontal_test);

        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);
        AccessibilityNodeInfoCompat horizScroller = getNodeForId(R.id.horiz_scroller);

        mCursorController.setCursor(firstLabel);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) text_first_item
        // 1-7. horiz_scroller - all items
        // 8. text_last_item
        final int traversals = 8;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_FORWARD,
                traversals, true, true, true);

        assertHasParent(horizScroller, nodes.subList(0, traversals - 1));
        assertEquals(lastLabel, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testPrevious_doWrap_doScroll_doUseInputFocus() {
        setContentView(R.layout.cursor_test);

        // Scroll teams list to the last item.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ListView teamsListView = (ListView) getViewForId(R.id.teams_list);
                teamsListView.setSelection(teamsListView.getCount() - 1);
            }
        });
        getInstrumentation().waitForIdleSync();

        // Put input focus on button AFTER the teams list is done updating.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View squareButton = getViewForId(R.id.button_square);
                squareButton.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);
        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);

        // This is the expected traversal:
        // (Initial focus) button_square - has input focus
        // 1. text_first_item
        // 2. text_first_item - first previous() pauses on node
        // 3. text_last_item - second previous() wraps around
        // 4-12: teams_list items
        // 13. check_me
        final int traversals = 13;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_BACKWARD,
                traversals, true, true, true);

        assertEquals(firstLabel, nodes.get(0));
        assertEquals(firstLabel, nodes.get(1));
        assertEquals(lastLabel, nodes.get(2));
        assertHasParent(teamsList, nodes.subList(3, traversals - 1));
        assertEquals(checkBox, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testPrevious_horizontal() {
        setContentView(R.layout.cursor_horizontal_test);

        // Scroll to the very right to reach the last items before beginning test.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                HorizontalScrollView scroller =
                        (HorizontalScrollView) getViewForId(R.id.horiz_scroller);
                scroller.fullScroll(View.FOCUS_RIGHT);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);
        AccessibilityNodeInfoCompat horizScroller = getNodeForId(R.id.horiz_scroller);

        mCursorController.setCursor(lastLabel);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) text_last_item
        // 1-7. horiz_scroller - all items
        // 8. text_first_item
        final int traversals = 8;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_BACKWARD,
                traversals, true, true, true);

        assertHasParent(horizScroller, nodes.subList(0, traversals - 1));
        assertEquals(firstLabel, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testDown_noWrap_doScroll_noUseInputFocus() {
        if (!checkApiLevelSupportsDirectional()) {
            return;
        }

        setContentView(R.layout.cursor_test);

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);
        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat squareButton = getNodeForId(R.id.button_square);
        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);

        mCursorController.setCursor(firstLabel);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) text_first_item
        // 1. button_square
        // 2. check_me
        // 3-N. teams_list - visible items only
        // N. text_last_item
        final int teamsVisibleCount = teamsList.getChildCount();
        final int traversals = 3 + teamsVisibleCount; // 3 non-list items + N visible list items.
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_DOWN,
                traversals, false, false, false);

        assertEquals(squareButton, nodes.get(0));
        assertEquals(checkBox, nodes.get(1));
        assertHasParent(teamsList, nodes.subList(2, traversals - 1));
        assertEquals(lastLabel, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testDown_horizontal() {
        if (!checkApiLevelSupportsDirectional()) {
            return;
        }

        setContentView(R.layout.cursor_horizontal_test);

        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);
        AccessibilityNodeInfoCompat horizScroller = getNodeForId(R.id.horiz_scroller);

        mCursorController.setCursor(firstLabel);
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) text_first_item
        // 1. horiz_scroller - a single item (doesn't really matter which specifically)
        // 2. text_last_item
        // 3. text_last_item - first down() should pause on node
        // 4. text_first_item - second down() should wrap around
        final int traversals = 4;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_DOWN,
                traversals, true, true, true);

        assertHasParent(horizScroller, nodes.subList(0, 1));
        assertEquals(lastLabel, nodes.get(1));
        assertEquals(lastLabel, nodes.get(2));
        assertEquals(firstLabel, nodes.get(3));
    }

    @MediumTest
    public void testUp_doWrap_doScroll_doUseInputFocus() {
        if (!checkApiLevelSupportsDirectional()) {
            return;
        }

        setContentView(R.layout.cursor_test);

        // Scroll teams list to the last item.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ListView teamsListView = (ListView) getViewForId(R.id.teams_list);
                teamsListView.setSelection(teamsListView.getCount() - 1);
            }
        });
        getInstrumentation().waitForIdleSync();

        // Put input focus on button AFTER the teams list is done updating.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View squareButton = getViewForId(R.id.button_square);
                squareButton.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);
        AccessibilityNodeInfoCompat checkBox = getNodeForId(R.id.check_me);
        AccessibilityNodeInfoCompat firstLabel = getNodeForId(R.id.text_first_item);
        AccessibilityNodeInfoCompat lastLabel = getNodeForId(R.id.text_last_item);

        // This is the expected traversal:
        // (Initial focus) button_square - has input focus
        // 1. text_first_item
        // 2. text_first_item - first previous() pauses on node
        // 3. text_last_item - second previous() wraps around
        // 4-12: teams_list items
        // 13. check_me
        final int traversals = 13;
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_UP,
                traversals, true, true, true);

        assertEquals(firstLabel, nodes.get(0));
        assertEquals(firstLabel, nodes.get(1));
        assertEquals(lastLabel, nodes.get(2));
        assertHasParent(teamsList, nodes.subList(3, traversals - 1));
        assertEquals(checkBox, nodes.get(traversals - 1));
    }

    @MediumTest
    public void testRight_horizontal() {
        if (!checkApiLevelSupportsDirectional()) {
            return;
        }

        setContentView(R.layout.cursor_horizontal_test);

        AccessibilityNodeInfoCompat horizScroller = getNodeForId(R.id.horiz_scroller);
        mCursorController.setCursor(horizScroller.getChild(0));
        waitForAccessibilityIdleSync();

        // This is the expected traversal:
        // (Initial focus) horiz_scroller - first item.
        // 1-20. horiz_scroller - we should wrap around twice but keep hitting horiz_scroller
        final int traversals = 20; // Arbitrary big number. Enough to wrap around a few times.
        List<AccessibilityNodeInfoCompat> nodes = navigate(TraversalStrategy.SEARCH_FOCUS_RIGHT,
                traversals, true, true, true);
        assertHasParent(horizScroller, nodes);
        assertNotEquals(nodes.get(0), nodes.get(1)); // We should get different nodes!
    }

    @MediumTest
    public void testSetGranularity() {
        setContentView(R.layout.text_activity);

        AccessibilityNodeInfoCompat usernameLabel = getNodeForId(R.id.username_label);
        mCursorController.setCursor(usernameLabel);
        mCursorController.setGranularity(CursorGranularity.CHARACTER, false);
        waitForAccessibilityIdleSync();

        startRecordingUtterances();

        // It should read [username] as "u", "s", "e", "r", etc.
        // Read up until the seventh letter ("m").
        for (int i = 0; i < 7; ++i) {
            mCursorController.next(false, false, false, InputModeManager.INPUT_MODE_TOUCH);
            waitForAccessibilityIdleSync();
        }

        // Need to match "m" exactly (we want "m" by itself and not "username" which contains "m").
        final CharSequenceFilter textFilter = new CharSequenceFilter().addMatchesPattern("m", 0);
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);
        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testSetGranularity_thenNavigateAway() {
        setContentView(R.layout.text_activity);

        final TextView usernameView = (TextView) getViewForId(R.id.username);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                usernameView.setText("potatoes");
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat usernameLabel = getNodeForId(R.id.username_label);
        mCursorController.setCursor(usernameLabel);
        mCursorController.setGranularity(CursorGranularity.CHARACTER, false);
        waitForAccessibilityIdleSync();

        startRecordingUtterances();

        // First read [username] at character granularity then begin reading [potatoes].
        // It should read [username] as "u", "s", "e", "r", etc.
        // 1-8: "u", "s", "e", "r"...
        // 9. (indication that we've reached the end)
        // 10. "p"
        for (int i = 0; i < 10; ++i) {
            mCursorController.next(false, false, false, InputModeManager.INPUT_MODE_TOUCH);
            waitForAccessibilityIdleSync();
        }

        // We need to match an utterance with exactly the string "p".
        final CharSequenceFilter textFilter = new CharSequenceFilter().addMatchesPattern("p", 0);
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        final Utterance utterance = stopRecordingUtterancesAfterMatch(utteranceFilter);
        assertNotNull("Saw matching utterance", utterance);
    }

    @MediumTest
    public void testSetSelectionModeActive() {
        setContentView(R.layout.text_activity);

        final TextView usernameView = (TextView) getViewForId(R.id.username);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                usernameView.setText("abcdefghijklmnop");
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat username = getNodeForId(R.id.username);
        mCursorController.setCursor(username);
        mCursorController.setGranularity(CursorGranularity.CHARACTER, false);
        waitForAccessibilityIdleSync();

        // Move cursor between "c" and "d".
        for (int i = 0; i < 3; ++i) {
            mCursorController.next(false, false, false, InputModeManager.INPUT_MODE_TOUCH);
            waitForAccessibilityIdleSync();
        }

        mCursorController.setSelectionModeActive(username, true);
        waitForAccessibilityIdleSync();

        // Select five characters "defgh".
        for (int i = 0; i < 5; ++i) {
            mCursorController.next(false, false, false, InputModeManager.INPUT_MODE_TOUCH);
            waitForAccessibilityIdleSync();
        }

        assertEquals(3, usernameView.getSelectionStart());
        assertEquals(8, usernameView.getSelectionEnd()); // 8-3 = 5 char selection.
    }

    @MediumTest
    public void testAddGranularityListener() {
        setContentView(R.layout.text_activity);

        GranularityChangeListener listener = new GranularityChangeListener();
        mCursorController.addGranularityListener(listener);

        AccessibilityNodeInfoCompat usernameLabel = getNodeForId(R.id.username_label);
        mCursorController.setCursor(usernameLabel);

        mCursorController.setGranularity(CursorGranularity.CHARACTER, false);
        waitForAccessibilityIdleSync();
        assertEquals(1, listener.count);

        mCursorController.setGranularity(CursorGranularity.WORD, false);
        waitForAccessibilityIdleSync();
        assertEquals(2, listener.count);
    }

    @MediumTest
    public void testAddScrollListener() {
        setContentView(R.layout.cursor_test);

        ScrollListener listener = new ScrollListener();
        mCursorController.addScrollListener(listener);

        AccessibilityNodeInfoCompat teamsList = getNodeForId(R.id.teams_list);
        AccessibilityNodeInfoCompat teamItem = teamsList.getChild(0);

        mCursorController.setCursor(teamItem);
        waitForAccessibilityIdleSync();

        mCursorController.more(); // Causes a downwards scroll.
        waitForAccessibilityIdleSync();
        assertEquals(1, listener.count);
    }

    @MediumTest
    public void testNextPrevious_web() {
        setContentView(R.layout.cursor_web_test);

        WebAccessibilityDelegate delegate1 = new WebAccessibilityDelegate(true /* next */,
                false /* previous */);
        View webElement1 = getViewForId(R.id.web_element_1);
        webElement1.setAccessibilityDelegate(delegate1);
        AccessibilityNodeInfoCompat webNode1 = getNodeForView(webElement1);

        WebAccessibilityDelegate delegate2 = new WebAccessibilityDelegate(false /* next */,
                true /* previous */);
        View webElement2 = getViewForId(R.id.web_element_2);
        webElement2.setAccessibilityDelegate(delegate2);
        AccessibilityNodeInfoCompat webNode2 = getNodeForView(webElement2);

        AccessibilityNodeInfoCompat nativeNode = getNodeForId(R.id.native_element);

        // Start at web element 1.
        mCursorController.setCursor(webNode1);
        waitForAccessibilityIdleSync();

        // Navigate to next web element. We verify that CursorController doesn't move cursor.
        mCursorController.next(false /* wrap */, false /* scroll */, false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        assertTrue(delegate1.didPerformNextHtmlAction());
        assertEquals(webNode1, mCursorController.getCursor());

        // Set a11y focus to web element 2 manually using the CursorController.
        mCursorController.setCursor(webNode2);
        waitForAccessibilityIdleSync();

        // Navigate to previous web element. We verify that CursorController doesn't move cursor.
        mCursorController.previous(false /* wrap */, false /* scroll */, false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        assertTrue(delegate2.didPerformPreviousHtmlAction());
        assertEquals(webNode2, mCursorController.getCursor());

        // We're still at web element 2. Try to move to the native element.
        // We verify that the CursorController should move the cursor in this case.
        mCursorController.next(false /* wrap */, false /* scroll */, false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        assertEquals(nativeNode, mCursorController.getCursor());
    }

    @MediumTest
    public void testHitEdgeAnnouncement_web() {
        setContentView(R.layout.hit_edge_web_test);

        final WebView webView = (WebView) getViewForId(R.id.web_view);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                loadWebViewFromResource(webView, R.raw.simple_page_webview);
            }
        });

        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat wholeWeb = getNodeForId(R.id.web_view);
        mCursorController.setCursor(wholeWeb);
        waitForAccessibilityIdleSync();

        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        mCursorController.setGranularity(CursorGranularity.WEB_SECTION, true);
        waitForAccessibilityIdleSync();

        //Move cursor to Heading 1
        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        //Try to move to the next element.
        //It should notify the user no next heading or landmark.
        startRecordingRawSpeech();
        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();
        stopRecordingAndAssertRawSpeech("No next heading or landmark");
    }

    @MediumTest
    public void testNotPastLastHeading_web() {
        setContentView(R.layout.hit_edge_web_test);

        final WebView webView = (WebView) getViewForId(R.id.web_view);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                loadWebViewFromResource(webView, R.raw.simple_page_webview);
            }
        });

        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat wholeWeb = getNodeForId(R.id.web_view);
        mCursorController.setCursor(wholeWeb);
        waitForAccessibilityIdleSync();

        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();

        mCursorController.setGranularity(CursorGranularity.WEB_SECTION, true);
        waitForAccessibilityIdleSync();

        //Move cursor to Heading 1
        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();
        AccessibilityNodeInfoCompat heading = mCursorController.getCursor();

        //Try to move to the next element.
        //It should stay on the last Heading.
        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();
        assertEquals(heading, mCursorController.getCursor());

        //Try again to move to the next element.
        //It should stay on the last Heading.
        mCursorController.next(false /* wrap */,
                false /* scroll */,
                false /* useInput */,
                InputModeManager.INPUT_MODE_TOUCH);
        waitForAccessibilityIdleSync();
        assertEquals(heading, mCursorController.getCursor());
    }

    private void loadWebViewFromResource(WebView webView, int resourceId) {
        InputStream inputStream = getActivity().getResources()
                .openRawResource(resourceId);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

        String webContent = "";
        String tmp = "";

        try {
            while (tmp != null) {
                webContent += tmp;
                tmp = br.readLine();
            }
        } catch (IOException e) {
            Log.e(CursorControllerAppTest.this.getName(), "Cannot read from file.");
        }

        webView.loadData(webContent, "text/html", null);
    }

    private List<AccessibilityNodeInfoCompat> navigate(
            @TraversalStrategy.SearchDirection int direction,
            int numberTraversals,
            boolean wrap, boolean scroll, boolean useInputFocus) {
        List<AccessibilityNodeInfoCompat> nodesTraversed = new ArrayList<>();
        for (int i = 0; i < numberTraversals; ++i) {
            switch (direction) {
                case TraversalStrategy.SEARCH_FOCUS_FORWARD:
                    mCursorController.next(wrap, scroll, useInputFocus,
                            InputModeManager.INPUT_MODE_TOUCH);
                    break;
                case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
                    mCursorController.previous(wrap, scroll, useInputFocus,
                            InputModeManager.INPUT_MODE_TOUCH);
                    break;
                case TraversalStrategy.SEARCH_FOCUS_LEFT:
                    mCursorController.left(wrap, scroll, useInputFocus,
                            InputModeManager.INPUT_MODE_TOUCH);
                    break;
                case TraversalStrategy.SEARCH_FOCUS_RIGHT:
                    mCursorController.right(wrap, scroll, useInputFocus,
                            InputModeManager.INPUT_MODE_TOUCH);
                    break;
                case TraversalStrategy.SEARCH_FOCUS_UP:
                    mCursorController.up(wrap, scroll, useInputFocus,
                            InputModeManager.INPUT_MODE_TOUCH);
                    break;
                case TraversalStrategy.SEARCH_FOCUS_DOWN:
                    mCursorController.down(wrap, scroll, useInputFocus,
                            InputModeManager.INPUT_MODE_TOUCH);
                    break;
                default:
                    throw new IllegalArgumentException("direction must be a SearchDirection");
            }

            waitForAccessibilityIdleSync();

            AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.obtain(
                    mCursorController.getCursor());
            nodesTraversed.add(node);
            mObtainedNodes.add(node);
        }
        return nodesTraversed;
    }

    private void assertHasParent(AccessibilityNodeInfoCompat parent,
            List<AccessibilityNodeInfoCompat> items) {
        for (AccessibilityNodeInfoCompat node : items) {
            assertEquals(parent, node.getParent());
        }
    }

    private boolean checkApiLevelSupportsDirectional() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    private class GranularityChangeListener implements CursorController.GranularityChangeListener {
        public int count = 0;

        @Override
        public void onGranularityChanged(CursorGranularity granularity) {
            count++;
        }
    }

    private class ScrollListener implements CursorController.ScrollListener {
        public int count = 0;

        @Override
        public void onScroll(AccessibilityNodeInfoCompat scrolledNode, int action, boolean auto) {
            count++;
        }
    }

    private class WebAccessibilityDelegate extends View.AccessibilityDelegate {
        private final boolean mHasNext;
        private final boolean mHasPrevious;
        private int mActionsPerformed = 0;

        public WebAccessibilityDelegate(boolean hasNext, boolean hasPrevious) {
            mHasNext = hasNext;
            mHasPrevious = hasPrevious;
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            mActionsPerformed = mActionsPerformed | action;
            if (action == AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT && mHasNext) {
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT && mHasPrevious) {
                return true;
            }
            return super.performAccessibilityAction(host, action, args);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            if (mHasNext) {
                info.addAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT);
            }
            if (mHasPrevious) {
                info.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT);
            }
        }

        public boolean didPerformNextHtmlAction() {
            return (mActionsPerformed & AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT) != 0;
        }

        public boolean didPerformPreviousHtmlAction() {
            return (mActionsPerformed & AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT) != 0;
        }
    }

}
