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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.android.talkback.FeedbackItem;
import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.controller.CursorController;
import com.googlecode.eyesfree.testing.CharSequenceFilter;
import com.googlecode.eyesfree.testing.FeedbackItemFilter;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import java.util.ArrayList;

public class ProcessorScrollPositionTest extends TalkBackInstrumentationTestCase {

    CursorController mCursorController;

    private static final String[] ITEMS = {"Breakfast", "Second Breakfast", "Lunch", "Dinner",
            "Dessert"};

    public class TestFragment extends Fragment {
        private String mText = "";

        public TestFragment(String text) {
            mText = text;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstance) {
            View view = inflater.inflate(R.layout.view_pager_fragment, container, false);
            FrameLayout page = (FrameLayout) view.findViewById(R.id.page);
            page.setContentDescription(mText);

            return view;
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setContentView(R.layout.cursor_test);
        mCursorController = getService().getCursorController();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @MediumTest
    public void testAutoScroll_doesNotGiveFeedback() {
        ListView listView = (ListView) getViewForId(R.id.teams_list);
        AccessibilityNodeInfoCompat listNode = getNodeForView(listView);

        getService().interruptAllFeedback(true);
        startRecordingRawSpeech();

        int numItems = listView.getCount(); // Need total items, including invisible items!
        for (int i = 0; i < numItems; ++i) {
            if (i == 0) {
                AccessibilityNodeInfoCompat child = listNode.getChild(i);
                mCursorController.setCursor(child);
                child.recycle();
            } else {
                mCursorController.next(false, true, false, InputModeManager.INPUT_MODE_TOUCH);
            }
            waitForAccessibilityIdleSync();
        }

        final CharSequenceFilter textFilter =
                new CharSequenceFilter().addContainsIgnoreCase("Showing items");
        FeedbackItemFilter filter = new FeedbackItemFilter().addTextFilter(textFilter);
        FeedbackItem feedbackItem = stopRecordingRawSpeechAfterMatch(filter);

        assertNull(feedbackItem);
    }

    @MediumTest
    public void testManualScroll_doesGiveFeedback() {
        AccessibilityNodeInfoCompat list = getNodeForId(R.id.teams_list);

        int numItems = list.getChildCount();
        assertTrue(numItems > 0);

        startRecordingRawSpeech();

        AccessibilityNodeInfoCompat child = list.getChild(0);
        mCursorController.setCursor(child);
        child.recycle();
        waitForAccessibilityIdleSync();

        mCursorController.more();
        waitForAccessibilityIdleSync();

        mCursorController.more();
        waitForAccessibilityIdleSync();

        final CharSequenceFilter textFilter =
                new CharSequenceFilter().addContainsIgnoreCase("Showing items");
        FeedbackItemFilter filter = new FeedbackItemFilter().addTextFilter(textFilter);
        FeedbackItem feedbackItem = stopRecordingRawSpeechAfterMatch(filter);

        assertNotNull(feedbackItem);
    }

    @MediumTest
    public void testViewPager_scrollNext() {
        setUpViewPager();

        startRecordingRawSpeech();

        final ViewPager pager = (ViewPager) getViewForId(R.id.test_pager);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pager.setCurrentItem(1, false);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        stopRecordingAndAssertRawSpeech("Second Breakfast, 2 of 5");
    }

    @MediumTest
    public void testViewPager_scrollBack() {
        setUpViewPager();

        startRecordingRawSpeech();

        final ViewPager pager = (ViewPager) getViewForId(R.id.test_pager);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pager.setCurrentItem(3, false);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pager.setCurrentItem(2, false);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        stopRecordingAndAssertRawSpeech("Lunch, 3 of 5");
    }

    @MediumTest
    public void testViewPager_scrollSamePage() {
        setUpViewPager();

        getService().interruptAllFeedback(true);
        startRecordingRawSpeech();

        final ViewPager pager = (ViewPager) getViewForId(R.id.test_pager);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pager.setCurrentItem(0, false);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        // No feedback when page doesn't change.
        FeedbackItemFilter anyItemFilter = new FeedbackItemFilter();
        FeedbackItem feedbackItem = stopRecordingRawSpeechAfterMatch(anyItemFilter);

        assertNull(feedbackItem);
    }

    private void setUpViewPager() {
        setContentView(R.layout.view_pager);
        final ViewPager pager = (ViewPager) getViewForId(R.id.test_pager);
        final FragmentManager fragmentManager = getActivity().getSupportFragmentManager();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                pager.setAdapter(new FragmentPagerAdapter(fragmentManager) {
                    @Override
                    public Fragment getItem(int position) {
                        return new TestFragment(ITEMS[position]);
                    }

                    @Override
                    public int getCount() {
                        return ITEMS.length;
                    }
                });
            }
        });
        getInstrumentation().waitForIdleSync();
    }

}
