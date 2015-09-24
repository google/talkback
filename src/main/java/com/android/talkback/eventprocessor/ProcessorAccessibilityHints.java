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
import android.os.Build;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.controller.CursorControllerApp;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.talkback.tutorial.AccessibilityTutorialActivity;
import com.android.talkback.controller.GestureActionMonitor;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.WeakReferenceHandler;

/**
 * Manages accessibility hints. If a HOVER_ENTER event passes through this
 * processor or a control receives focus via linear exploration, the relevant
 * hint will be spoken unless an event is received before the timeout completes.
 */
public class ProcessorAccessibilityHints implements AccessibilityEventListener,
        GestureActionMonitor.GestureActionListener {
    private final GestureActionMonitor mGestureActionMonitor = new GestureActionMonitor();

    private final SharedPreferences mPrefs;
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final CursorControllerApp mCursorController;
    private final NodeSpeechRuleProcessor mRuleProcessor;
    private final A11yHintHandler mHandler;

    private AccessibilityNodeInfoCompat mWaitingForExit;
    private boolean mIsTouchExploring;

    public ProcessorAccessibilityHints(Context context, SpeechController speechController,
                                       CursorControllerApp cursorController) {
        if (speechController == null) throw new IllegalStateException();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;
        mSpeechController = speechController;
        mCursorController = cursorController;
        mRuleProcessor = NodeSpeechRuleProcessor.getInstance();
        mHandler = new A11yHintHandler(this);

        mGestureActionMonitor.setListener(this);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mGestureActionMonitor,
            GestureActionMonitor.FILTER);
    }

    @Override
    public void onGestureAction(String action) {
        cancelA11yHint();

        if ((action.equals(mContext.getString(R.string.shortcut_value_next)) ||
             action.equals(mContext.getString(R.string.shortcut_value_previous))) &&
             areHintsEnabled()) {
            final AccessibilityNodeInfoCompat source = mCursorController.getCursor();
            if (source == null) {
                return;
            }

            if (mCursorController.isLinearNavigationLocked(source)) {
                return;
            }

            final CharSequence text = mRuleProcessor.getHintForNode(source);
            if (!TextUtils.isEmpty(text)) {
                postA11yHintRunnable(text.toString());
            }
            source.recycle();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER ||
            eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            mIsTouchExploring = true;
        }

        if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            mIsTouchExploring = false;
            cacheEnteredNode(null);
            cancelA11yHint();
            return;
        }

        if (!mIsTouchExploring ||
            ((eventType != AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                && (eventType != AccessibilityEvent.TYPE_VIEW_HOVER_EXIT))) {
            return;
        }

        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return;
        }

        if (eventType == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            cacheEnteredNode(source);
            String hint = getHintFromEvent(event);
            if (hint != null) {
                postA11yHintRunnable(hint);
            }
        } else if (eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT) {
            if (source.equals(mWaitingForExit)) {
                cancelA11yHint();
            }
        }

        source.recycle();
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
     * @param event The source event.
     */
    private String getHintFromEvent(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return null;
        }

        // If this was a HOVER_ENTER event, we need to compute the focused node.
        // TODO: We're already doing this in the touch exploration formatter --
        // maybe this belongs there instead?
        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            source = AccessibilityNodeInfoUtils.findFocusFromHover(source);
            if (source == null) {
                return null;
            }
        }

        final CharSequence text = mRuleProcessor.getHintForNode(source);
        source.recycle();

        if (TextUtils.isEmpty(text)) {
            return null;
        }

        return text.toString();
    }

    private void speakHint(String text) {
        // Never speak hint text if the tutorial is active
        if (AccessibilityTutorialActivity.isTutorialActive()) {
            LogUtils.log(this, Log.VERBOSE, "Dropping hint speech because tutorial is active.");
            return;
        }

        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.speak(
                text, SpeechController.QUEUE_MODE_QUEUE, FeedbackItem.FLAG_NO_HISTORY, null);
    }

    private void cacheEnteredNode(AccessibilityNodeInfoCompat node) {
        if (mWaitingForExit != null) {
            mWaitingForExit.recycle();
            mWaitingForExit = null;
        }

        if (node != null) {
            mWaitingForExit = AccessibilityNodeInfoCompat.obtain(node);
        }
    }

    /**
     * The hint that will be read by the utterance complete action.
     */
    private String mPendingHint;

    /**
     * Starts the hint timeout. Call this for every event that triggers a hint.
     */
    private void postA11yHintRunnable(String hint) {
        cancelA11yHint();

        mPendingHint = hint;

        // The timeout starts after the current text is spoken.
        mSpeechController.addUtteranceCompleteAction(
                mSpeechController.peekNextUtteranceId(), mA11yHintRunnable);
    }

    /**
     * Removes the hint timeout and completion action. Call this for every event.
     */
    private void cancelA11yHint() {
        mHandler.cancelA11yHintTimeout();

        mPendingHint = null;
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

                    if (mPendingHint == null) {
                        return;
                    }

                    mHandler.startA11yHintTimeout(mPendingHint);
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
                    final String hint = (String) msg.obj;
                    parent.speakHint(hint);
                    break;
                }
            }
        }

        public void startA11yHintTimeout(String hint) {
            final Message msg = obtainMessage(LONG_HOVER_TIMEOUT, hint);

            sendMessageDelayed(msg, DELAY_LONG_HOVER_TIMEOUT);
        }

        public void cancelA11yHintTimeout() {
            removeMessages(LONG_HOVER_TIMEOUT);
        }
    }
}
