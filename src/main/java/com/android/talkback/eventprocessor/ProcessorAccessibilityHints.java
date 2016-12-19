/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.WeakReferenceHandler;

/**
 * Manages accessibility hints. When a node is accessibility-focused and hints are enabled,
 * the hint will be queued after a short delay.
 */
public class ProcessorAccessibilityHints implements AccessibilityEventListener {
    private final SharedPreferences mPrefs;
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final NodeSpeechRuleProcessor mRuleProcessor;
    private final A11yHintHandler mHandler;

    public ProcessorAccessibilityHints(Context context, SpeechController speechController) {
        if (speechController == null) throw new IllegalStateException();
        mPrefs = SharedPreferencesUtils.getSharedPreferences(context);
        mContext = context;
        mSpeechController = speechController;
        mRuleProcessor = NodeSpeechRuleProcessor.getInstance();
        mHandler = new A11yHintHandler(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!areHintsEnabled()) {
            return;
        }

        // Clear hints that were generated before a click or in an old window configuration.
        final int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            cancelA11yHint();
            return;
        }

        if (eventType == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            EventState eventState = EventState.getInstance();
            if (eventState.checkAndClearRecentEvent(
                    EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE)) {
                return;
            }
            if (eventState.checkAndClearRecentEvent(
                    EventState.EVENT_SKIP_HINT_AFTER_CURSOR_CONTROL)) {
                return;
            }

            AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
            AccessibilityNodeInfoCompat source = record.getSource();
            if (source != null) {
                postA11yHintRunnable(source);
                // DO NOT RECYCLE. postA11yHintRunnable will save the node.
            }
        }
    }

    private boolean areHintsEnabled() {
        return SharedPreferencesUtils.getBooleanPref(
                mPrefs, mContext.getResources(),
                R.string.pref_a11y_hints_key,
                R.bool.pref_a11y_hints_default);
    }

    /**
     * Given an {@link AccessibilityEvent}, obtains the long hover utterance.
     *
     * @param source The source node.
     */
    private String getHintFromNode(AccessibilityNodeInfoCompat source) {
        if (source == null) {
            return null;
        }

        final CharSequence text = mRuleProcessor.getHintForNode(source);
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        return text.toString();
    }

    private void speakHint(String text) {
        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.speak(
                text, SpeechController.QUEUE_MODE_QUEUE, FeedbackItem.FLAG_NO_HISTORY, null);
    }

    /**
     * The source node whose hint will be read by the utterance complete action.
     */
    private AccessibilityNodeInfoCompat mPendingHintSource;

    /**
     * Starts the hint timeout. Call this for every event that triggers a hint.
     */
    private void postA11yHintRunnable(AccessibilityNodeInfoCompat node) {
        cancelA11yHint();

        if (mPendingHintSource != null) {
            mPendingHintSource.recycle();
        }
        mPendingHintSource = node;

        // The timeout starts after the current text is spoken.
        mSpeechController.addUtteranceCompleteAction(
                mSpeechController.peekNextUtteranceId(), mA11yHintRunnable);
    }

    /**
     * Removes the hint timeout and completion action. Call this for every event.
     */
    private void cancelA11yHint() {
        mHandler.cancelA11yHintTimeout();

        if (mPendingHintSource != null) {
            mPendingHintSource.recycle();
        }
        mPendingHintSource = null;
    }

    /**
     * Posts a delayed hint action.
     */
    private final SpeechController.UtteranceCompleteRunnable mA11yHintRunnable =
            new SpeechController.UtteranceCompleteRunnable() {
                @Override
                public void run(int status) {
                    // The utterance must have been spoken successfully.
                    if (status != SpeechController.STATUS_SPOKEN) {
                        return;
                    }

                    if (mPendingHintSource == null) {
                        return;
                    }

                    mHandler.startA11yHintTimeout(mPendingHintSource);
                }
            };

    private static class A11yHintHandler extends WeakReferenceHandler<ProcessorAccessibilityHints> {
        /**
         * Message identifier for a verbose (long-hover) notification.
         */
        private static final int LONG_HOVER_TIMEOUT = 1;

        /**
         * Timeout before reading a verbose (long-hover) notification.
         */
        private static final long DELAY_LONG_HOVER_TIMEOUT = 1000;

        public A11yHintHandler(ProcessorAccessibilityHints parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorAccessibilityHints parent) {
            switch (msg.what) {
                case LONG_HOVER_TIMEOUT: {
                    AccessibilityNodeInfoCompat source = (AccessibilityNodeInfoCompat) msg.obj;
                    AccessibilityNodeInfoCompat refreshed =
                            AccessibilityNodeInfoUtils.refreshNode(source);
                    if (refreshed != null) {
                        if (refreshed.isAccessibilityFocused()) {
                            String hint = parent.getHintFromNode(source);
                            parent.speakHint(hint);
                            LogUtils.log(this, Log.VERBOSE, "Speaking hint for node: %s",
                                    refreshed);
                        } else {
                            LogUtils.log(this, Log.VERBOSE, "Skipping hint for node: %s",
                                    refreshed);
                        }
                        refreshed.recycle();
                    }
                    source.recycle();
                    break;
                }
            }
        }

        public void startA11yHintTimeout(AccessibilityNodeInfoCompat source) {
            if (source != null) {
                final Message msg = obtainMessage(LONG_HOVER_TIMEOUT,
                        AccessibilityNodeInfoCompat.obtain(source));
                sendMessageDelayed(msg, DELAY_LONG_HOVER_TIMEOUT);
                LogUtils.log(this, Log.VERBOSE, "Queuing hint for node: %s", source);
            }
        }

        public void cancelA11yHintTimeout() {
            removeMessages(LONG_HOVER_TIMEOUT);
        }
    }
}
