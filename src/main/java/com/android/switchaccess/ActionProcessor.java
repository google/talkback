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

package com.android.switchaccess;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Processor for accessibility actions. Because we aren't synchronized with the UI, actions
 * are sometimes delayed slightly to allow our view of the UI to settle.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ActionProcessor implements UiChangeDetector.PossibleUiChangeListener {
    /* Empirically determined magic time that we delay after possible changes to the UI */
    private static final long FRAMEWORK_SETTLING_TIME_MILLIS = 500;

    private final List<Runnable> mListOfHeldOffActions = new ArrayList<>();
    private final Handler mHandler = new Handler();
    private final UiChangedListener mUiChangedListener;

    private final Runnable mRunnableToProcessHeldOffActions = new Runnable() {
        @Override
        public void run() {
            long timeToWait = getMillisUntilSafeToProcessActions();

            if (timeToWait > 0) {
                /* Not safe to process now; reschedule for later */
                mHandler.removeCallbacks(mRunnableToProcessHeldOffActions);
                mHandler.postDelayed(mRunnableToProcessHeldOffActions, timeToWait);
                return;
            }

            /*
             * As with any workaround of a race condition, this isn't watertight, but the view
             * hierarchy is most likely stable.
             */
            for (Runnable action : mListOfHeldOffActions) {
                processGuaranteedSafeAction(action);
            }

            mListOfHeldOffActions.clear();
        }
    };

    private long mLastWindowChangeTime = 0;
    private boolean mUiMayHaveChanged = true;

    /**
     *
     * @param uiChangedListener A listener to be notified when the UI updates (typically an
     * OptionManager)
     */
    public ActionProcessor(UiChangedListener uiChangedListener) {
        mUiChangedListener = uiChangedListener;
    }

    /**
     * Process a Runnable when appropriate
     * @param action The runnable to process
     */
    public void process(Runnable action) {
        mListOfHeldOffActions.add(action);
        mHandler.postDelayed(
                mRunnableToProcessHeldOffActions, getMillisUntilSafeToProcessActions());
    }

    /**
     * If the UI may have changed, this method should be called so we know to wait for it to
     * settle.
     */
    @Override
    public void onPossibleChangeToUi() {
        mUiMayHaveChanged = true;
        mLastWindowChangeTime = SystemClock.elapsedRealtime();
        /*
         * Process an empty runnable so we'll check if we need to clear the overlay once the
         * UI is stable.
         */
        process(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    private void processGuaranteedSafeAction(Runnable action) {
        if (mUiMayHaveChanged) {
            mUiChangedListener.onUiChangedAndIsNowStable();
            mUiMayHaveChanged = false;
        }
        action.run();
    }

    private long getMillisUntilSafeToProcessActions() {
        long timeToWait = mLastWindowChangeTime + FRAMEWORK_SETTLING_TIME_MILLIS
                - SystemClock.elapsedRealtime();
        timeToWait = (timeToWait > 0) ? timeToWait : 0;
        return timeToWait;
    }

    public interface UiChangedListener {
        void onUiChangedAndIsNowStable();
    }
}