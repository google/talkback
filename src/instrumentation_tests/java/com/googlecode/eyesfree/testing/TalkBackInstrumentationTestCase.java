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

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.BuildConfig;
import com.android.talkback.FeedbackItem;
import com.android.talkback.KeyComboManager;
import com.android.talkback.SpeechController.SpeechControllerListener;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class TalkBackInstrumentationTestCase extends BaseAccessibilityInstrumentationTestCase {
    @IntDef({RECORD_NONE, RECORD_QUEUED, RECORD_STARTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RecordingMode {}
    public static final int RECORD_NONE = 0;
    public static final int RECORD_QUEUED = 1;
    public static final int RECORD_STARTED = 2;

    private static final long KEY_EVENT_DOWN_TIME = 0;
    private static final long KEY_EVENT_EVENT_TIME = 0;
    private static final String TARGET_PACKAGE = BuildConfig.APPLICATION_ID;
    private static final String TARGET_CLASS = "com.google.android.marvin.talkback.TalkBackService";

    /** Maximum time to wait for a specific utterance. */
    private static final long OBTAIN_UTTERANCE_TIMEOUT = 5000;

    /** List of recorded utterances. */
    private final ArrayList<Utterance> mUtteranceCache = new ArrayList<>();

    /** List of recorded raw speech feedback items. */
    private final ArrayList<FeedbackItem> mRawSpeechCache = new ArrayList<>();

    /** Whether we're currently recording utterances. */
    private boolean mRecordingUtterances;

    /** Whether we're currently recording raw speech feedback items, and at what time. */
    private @RecordingMode int mRecordingRawSpeech;

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
        final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mAppCtx);
        final Editor editor = prefs.edit();
        editor.putBoolean("first_time_user", false);
        editor.commit();

        enableService(TARGET_PACKAGE, TARGET_CLASS, true /* usesExploreByTouch */);
    }

    @Override
    protected void connectServiceListener() {
        mService.setTestingListener(mTestingListener);
        mService.getSpeechController().setSpeechListener(mTestingSpeechListener);
    }

    @Override
    protected void disconnectServiceListener() {
        mService.setTestingListener(null);
        mService.getSpeechController().setSpeechListener(null);
    }

    protected void startRecordingUtterances() {
        synchronized (mUtteranceCache) {
            mUtteranceCache.clear();
            mRecordingUtterances = true;
        }
    }

    protected void startRecordingRawSpeech() {
        startRecordingRawSpeech(RECORD_QUEUED);
    }

    protected void startRecordingRawSpeech(@RecordingMode int mode) {
        synchronized (mRawSpeechCache) {
            mRawSpeechCache.clear();
            mRecordingRawSpeech = mode;
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

    protected FeedbackItem stopRecordingRawSpeechAfterMatch(FeedbackItemFilter filter) {
        final long startTime = SystemClock.uptimeMillis();

        synchronized (mRawSpeechCache) {
            try {
                int currentIndex = 0;

                while (true) {
                    // Check all events starting from the current index.
                    for (; currentIndex < mRawSpeechCache.size(); currentIndex++) {
                        final FeedbackItem feedbackItem = mRawSpeechCache.get(currentIndex);
                        if (filter.matches(feedbackItem)) {
                            mRecordingRawSpeech = RECORD_NONE;
                            return feedbackItem;
                        }
                    }

                    final long elapsed = (SystemClock.uptimeMillis() - startTime);
                    final long timeLeft = (OBTAIN_UTTERANCE_TIMEOUT - elapsed);
                    if (timeLeft <= 0) {
                        break;
                    }

                    mRawSpeechCache.wait(timeLeft);
                }

                mRecordingRawSpeech = RECORD_NONE;
            } catch (InterruptedException e) {
                // Do nothing.
            }
        }

        return null;
    }

    protected void stopRecordingAndAssertUtterance(String utterance) {
        CharSequenceFilter textFilter = new CharSequenceFilter().addMatchesPattern(utterance, 0);
        UtteranceFilter utteranceFilter = new UtteranceFilter().addTextFilter(textFilter);
        Utterance result = stopRecordingUtterancesAfterMatch(utteranceFilter);
        assertNotNull(result);
    }

    protected void stopRecordingAndAssertRawSpeech(String feedback) {
        CharSequenceFilter textFilter = new CharSequenceFilter().addMatchesPattern(feedback, 0);
        FeedbackItemFilter feedbackFilter = new FeedbackItemFilter().addTextFilter(textFilter);
        FeedbackItem result = stopRecordingRawSpeechAfterMatch(feedbackFilter);
        assertNotNull(result);
    }

    protected List<Utterance> getUtteranceHistory() {
        synchronized (mUtteranceCache) {
            return new ArrayList<Utterance>(mUtteranceCache);
        }
    }

    protected List<FeedbackItem> getRawSpeechHistory() {
        synchronized (mRawSpeechCache) {
            return new ArrayList<>(mRawSpeechCache);
        }
    }

    protected void afterEventReceived(AccessibilityEvent event) {}

    private final TalkBackListener mTestingListener = new TalkBackListener() {
        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            onEventReceived(event);
        }

        @Override
        public void afterAccessibilityEvent(AccessibilityEvent event) {
            afterEventReceived(event);
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

    private final SpeechControllerListener mTestingSpeechListener = new SpeechControllerListener() {
        @Override
        public void onUtteranceQueued(FeedbackItem utterance) {
            synchronized (mRawSpeechCache) {
                if (mRecordingRawSpeech == RECORD_QUEUED) {
                    mRawSpeechCache.add(utterance);
                    mRawSpeechCache.notifyAll();
                }
            }
        }

        @Override
        public void onUtteranceStarted(FeedbackItem utterance) {
            synchronized (mRawSpeechCache) {
                if (mRecordingRawSpeech == RECORD_STARTED) {
                    mRawSpeechCache.add(utterance);
                    mRawSpeechCache.notifyAll();
                }
            }
        }

        @Override
        public void onUtteranceCompleted(int utteranceIndex, int status) {}
    };

    /**
     * Sends down and up key event.
     */
    protected void sendKeyEventDownAndUp(int modifier, int keyCode, KeyComboManager keyComboManager) {
        sendKeyEvent(KeyEvent.ACTION_DOWN, modifier, keyCode, keyComboManager);
        sendKeyEvent(KeyEvent.ACTION_UP, modifier, keyCode, keyComboManager);
    }

    /**
     * Sends key event. This method first tries to send key event to KeyComboManager to simulate the
     * situation where AccessibilityService can listen and consume key events before an activity
     * gets them.
     */
    private void sendKeyEvent(int action, int modifier, int keyCode,
                              KeyComboManager keyComboManager) {
        KeyEvent keyEvent = new KeyEvent(KEY_EVENT_DOWN_TIME, KEY_EVENT_EVENT_TIME,
                action, keyCode, 0, modifier);
        SendKeyEventToKeyComboManagerRunnable runnable =
                new SendKeyEventToKeyComboManagerRunnable(keyComboManager, keyEvent);
        getInstrumentation().runOnMainSync(runnable);
        getInstrumentation().waitForIdleSync();

        if (runnable.consumed) {
            return;
        }

        getInstrumentation().sendKeySync(keyEvent);
        getInstrumentation().waitForIdleSync();
    }

    private static class SendKeyEventToKeyComboManagerRunnable implements Runnable {
        private final KeyComboManager mKeyComboManager;
        private final KeyEvent mKeyEvent;
        public boolean consumed;

        public SendKeyEventToKeyComboManagerRunnable(KeyComboManager keyComboManager,
                                                     KeyEvent keyEvent) {
            mKeyComboManager = keyComboManager;
            mKeyEvent = keyEvent;
        }

        @Override
        public void run() {
            consumed = mKeyComboManager.onKeyEvent(mKeyEvent);
        }
    }

    /**
     * Gets layout direction of dialog preference.
     */
    protected int getLayoutDirection(View view) {
        GetLayoutDirectionRunnable runnable = new GetLayoutDirectionRunnable(view);
        getInstrumentation().runOnMainSync(runnable);
        getInstrumentation().waitForIdleSync();

        return runnable.mLayoutDirection;
    }

    private static class GetLayoutDirectionRunnable implements Runnable {
        private final View mView;
        public int mLayoutDirection;

        public GetLayoutDirectionRunnable(View view) {
            mView = view;
        }

        @Override
        public void run() {
            mLayoutDirection = mView.findViewById(android.R.id.content).getLayoutDirection();
        }
    }
}
