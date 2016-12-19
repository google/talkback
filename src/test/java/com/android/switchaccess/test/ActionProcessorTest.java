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

package com.android.switchaccess.test;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.switchaccess.ActionProcessor;
import com.android.talkback.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;

/**
 * Robolectric tests for ActionProcessor
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21
)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class ActionProcessorTest implements ActionProcessor.UiChangedListener {
    private static final long TOO_SOON_TIME_MILLIS = 100;
    private static final long LONG_ENOUGH_TIME_MILLIS = 600;

    private ActionProcessor mActionProcessor;
    private int mNumCallsToOnUiChangedAndIsNowStable;
    private int mNumRunnableExecutions;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            mNumRunnableExecutions++;
        }
    };

    @Before
    public void setUp() {
        mNumCallsToOnUiChangedAndIsNowStable = 0;
        mNumRunnableExecutions = 0;
        mActionProcessor = new ActionProcessor(this);
        ShadowLooper.idleMainLooper(LONG_ENOUGH_TIME_MILLIS);
    }

    @Test
    public void testFirstAction_isProcessed() {
        mActionProcessor.process(runnable);
        assertEquals(1, mNumRunnableExecutions);
    }

    @Test
    public void testFirstAction_triggersUiRebuild() {
        mActionProcessor.process(runnable);
        assertEquals(1, mNumCallsToOnUiChangedAndIsNowStable);
    }

    @Test
    public void testActionAfterUIChange_notProcessedImmediately() {
        mActionProcessor.onPossibleChangeToUi();
        mActionProcessor.process(runnable);
        assertEquals(0, mNumRunnableExecutions);
        assertEquals(0, mNumCallsToOnUiChangedAndIsNowStable);
    }

    @Test
    public void testActionAfterUIChange_notProcessedAfterShortDelay() {
        mActionProcessor.onPossibleChangeToUi();
        mActionProcessor.process(runnable);
        ShadowLooper.idleMainLooper(TOO_SOON_TIME_MILLIS);
        assertEquals(0, mNumRunnableExecutions);
        assertEquals(0, mNumCallsToOnUiChangedAndIsNowStable);

    }

    @Test
    public void testActionAfterUIChange_processedAfterDelay() {
        mActionProcessor.onPossibleChangeToUi();
        mActionProcessor.process(runnable);
        ShadowLooper.idleMainLooper(LONG_ENOUGH_TIME_MILLIS);
        assertEquals(1, mNumRunnableExecutions);
        assertEquals(1, mNumCallsToOnUiChangedAndIsNowStable);
    }

    @Test
    public void testActionAfterUIChange_processedAfterShortThenLongDelay() {
        mActionProcessor.onPossibleChangeToUi();
        mActionProcessor.process(runnable);
        ShadowLooper.idleMainLooper(TOO_SOON_TIME_MILLIS);
        ShadowLooper.idleMainLooper(LONG_ENOUGH_TIME_MILLIS - TOO_SOON_TIME_MILLIS);
        assertEquals(1, mNumRunnableExecutions);
        assertEquals(1, mNumCallsToOnUiChangedAndIsNowStable);
    }

    @Override
    public void onUiChangedAndIsNowStable() {
        mNumCallsToOnUiChangedAndIsNowStable++;
    }
}
