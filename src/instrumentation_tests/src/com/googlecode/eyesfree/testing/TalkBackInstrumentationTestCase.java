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

package com.googlecode.eyesfree.testing;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.BuildConfig;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class TalkBackInstrumentationTestCase extends BaseAccessibilityInstrumentationTestCase {
    private static final String TARGET_PACKAGE = BuildConfig.APPLICATION_ID;
    private static final String TARGET_CLASS = "com.google.android.marvin.talkback.TalkBackService";

    /** Maximum time to wait for a specific utterance. */
    private static final long OBTAIN_UTTERANCE_TIMEOUT = 5000;

    /** List of recorded utterances. */
    private final ArrayList<Utterance> mUtteranceCache = new ArrayList<>();

    /** Whether we're currently recording utterances. */
    private boolean mRecordingUtterances;

    private TalkBackService mService;

    @Override
    protected TalkBackService getService() {
        mService = TalkBackService.getInstance();

        return mService;
    }

    @Override
    protected void enableTargetService() {
        assertServiceIsInstalled(TARGET_PACKAGE, TARGET_CLASS);

        // Prevent TalkBack from automatically opening the tutorial.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mInsCtx);
        final Editor editor = prefs.edit();
        editor.putBoolean("first_time_user", false);
        editor.commit();

        enableService(TARGET_PACKAGE, TARGET_CLASS, true /* usesExploreByTouch */);
    }

    @Override
    protected void connectServiceListener() {
        mService.setTestingListener(mTestingListener);
    }

    @Override
    protected void disconnectServiceListener() {
        mService.setTestingListener(null);
    }

    protected void startRecordingUtterances() {
        synchronized (mUtteranceCache) {
            mUtteranceCache.clear();
            mRecordingUtterances = true;
        }
    }

    protected Utterance stopRecordingUtterancesAfterMatch(UtteranceFilter filter) {
        final long startTime = SystemClock.uptimeMillis();

        synchronized (mUtteranceCache) {
            try {
                int currentIndex = 0;

                while (true) {
                    // Check all events starting from the current index.
                    for (; currentIndex < mUtteranceCache.size(); currentIndex++) {
                        final Utterance utterance = mUtteranceCache.get(currentIndex);
                        if (filter.matches(utterance)) {
                            mRecordingUtterances = false;
                            return utterance;
                        }
                    }

                    final long elapsed = (SystemClock.uptimeMillis() - startTime);
                    final long timeLeft = (OBTAIN_UTTERANCE_TIMEOUT - elapsed);
                    if (timeLeft <= 0) {
                        break;
                    }

                    mUtteranceCache.wait(timeLeft);
                }

                mRecordingUtterances = false;
            } catch (InterruptedException e) {
                // Do nothing.
            }
        }

        return null;
    }

    protected List<Utterance> getUtteranceHistory() {
        synchronized (mUtteranceCache) {
            return new ArrayList<Utterance>(mUtteranceCache);
        }
    }

    private final TalkBackListener mTestingListener = new TalkBackListener() {
        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            onEventReceived(event);
        }

        // Note: This method is called on the TalkBack service thread.
        @Override
        public void onUtteranceQueued(Utterance utterance) {
            synchronized (mUtteranceCache) {
                if (mRecordingUtterances) {
                    mUtteranceCache.add(utterance);
                    mUtteranceCache.notifyAll();
                }
            }
        }
    };
}