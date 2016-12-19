/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkbacktests;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkbacktests.testsession.TestSession;
import com.android.talkbacktests.testsession.TestSessionFragment;

/**
 * Entry activity of Test App.
 */
public class MainActivity extends AppCompatActivity implements NavigationCallback,
        FragmentManager.OnBackStackChangedListener {

    // A String used to identify main fragment in fragment back stack.
    private static final String MAIN_FRAGMENT_NAME = "MAIN_FRAGMENT_NAME";

    // A TestController instance containing all the test information.
    private final TestController mController = TestController.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        try {
            mController.init(this);
        } catch (Exception e) {
            Log.println(Log.ERROR, this.getLocalClassName(), "Failed to initialize tests.");
            finish();
            return;
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        final MainFragment mainFragment = new MainFragment();
        mainFragment.setOnSessionSelectedCallback(this);
        mainFragment.setTestController(mController);
        switchFragment(mainFragment, MAIN_FRAGMENT_NAME);
    }

    @Override
    public void onBackStackChanged() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            finish();
        }
    }

    @Override
    public void onTestSessionSelected(int sessionId) {
        showTestContent(sessionId, 0);
    }

    @Override
    public void onNextContentClicked(int sessionId, int contentIndex) {
        final int nextContentIndex = contentIndex + 1;
        if (mController.getSessionById(sessionId).getContentCount() > nextContentIndex) {
            showTestContent(sessionId, nextContentIndex);
        } else {
            final TestSession nextSession = mController.getNextSessionById(sessionId);
            if (nextSession == null) {
                returnToMainFragment();
            } else {
                showTestContent(nextSession.getId(), 0);
            }
        }
    }

    @Override
    public void onPreviousContentClicked(int sessionId, int contentIndex) {
        final int previousContent = contentIndex - 1;
        if (previousContent >= 0) {
            showTestContent(sessionId, previousContent);
        } else {
            final TestSession previousSession = mController.getPreviousSessionById(sessionId);
            if (previousSession == null) {
                returnToMainFragment();
            } else {
                showTestContent(previousSession.getId(), previousSession.getContentCount() - 1);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        returnToMainFragment();
        return true;
    }

    @Override
    public void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        // Fire a TYPE_WINDOW_STATE_CHANGED event so that the accessibility service will be notified
        // of window title change.
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    /**
     * Pops back to the main fragment.
     */
    private void returnToMainFragment() {
        getSupportFragmentManager().popBackStack(MAIN_FRAGMENT_NAME, 0);
    }

    /**
     * Show specified content in a test session.
     *
     * @param sessionId    session ID
     * @param contentIndex page index of the content in the session
     */
    private void showTestContent(int sessionId, int contentIndex) {
        final TestSessionFragment testSessionFragment = new TestSessionFragment();
        testSessionFragment.setSession(sessionId, contentIndex);
        testSessionFragment.setNavigationCallback(this);
        switchFragment(testSessionFragment, null);
        mController.recordTestSessionAccessed(getApplicationContext(), sessionId);
    }

    /**
     * Replaces the fragment holder with a new fragment, adds the fragment transaction to the
     * back stack.
     *
     * @param fragment The fragment to show.
     * @param name     Name of the fragment, used to identify the fragment when popping up the back
     *                 stack.
     */
    private void switchFragment(Fragment fragment, String name) {
        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_holder, fragment);
        transaction.addToBackStack(name);
        transaction.commit();
    }
}
