/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;

import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.R;
import com.android.talkback.SpeechCleanupUtils;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.formatter.EventSpeechRuleProcessor;
import com.android.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.LogUtils;
import com.android.utils.StringBuilderUtils;
import com.android.utils.WeakReferenceHandler;

/**
 * Manages the event feedback queue. Queued events are run through the
 * {@link EventSpeechRuleProcessor} to generate spoken, haptic, and audible
 * feedback.
 */
public class ProcessorEventQueue implements AccessibilityEventListener {
    /** Manages pending speech events. */
    private final ProcessorEventHandler mHandler = new ProcessorEventHandler(this);

    /**
     * We keep the accessibility events to be processed. If a received event is
     * the same type as the previous one it replaces the latter, otherwise it is
     * added to the queue. All events in this queue are processed while we speak
     * and this occurs after a certain timeout since the last received event.
     */
    private final EventQueue mEventQueue = new EventQueue();

    private final SpeechController mSpeechController;

    /**
     * Processor for {@link AccessibilityEvent}s that populates
     * {@link Utterance}s.
     */
    private EventSpeechRuleProcessor mEventSpeechRuleProcessor;

    /** TalkBack-specific listener used for testing. */
    private TalkBackListener mTestingListener;

    /** Event type for the most recently processed event. */
    private int mLastEventType;

    /** Event time for the most recent window state changed event. */
    private long mLastWindowStateChanged = 0;

    /** Context for accessing resources. */
    private Context mContext;

    public ProcessorEventQueue(SpeechController speechController, TalkBackService context) {
        if (speechController == null) throw new IllegalStateException();

        mSpeechController = speechController;
        mEventSpeechRuleProcessor = new EventSpeechRuleProcessor(context);
        mContext = context;

        loadDefaultRules();
    }

    public void setTestingListener(TalkBackListener testingListener) {
        mTestingListener = testingListener;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            mLastWindowStateChanged = SystemClock.uptimeMillis();
        }

        synchronized (mEventQueue) {
            mEventQueue.enqueue(event);
            mHandler.postSpeak();
        }
    }


    /**
     * Loads default speech strategies based on the current SDK version.
     */
    private void loadDefaultRules() {
        // Add version-specific speech strategies for semi-bundled apps.
        mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_apps);
        mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_googletv);

        // Add platform-specific speech strategies for bundled apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_kitkat);
        }

        // Add generic speech strategy. This should always be added last so that
        // the app-specific rules above can override the generic rules.
        mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy);
    }

    /**
     * Processes an <code>event</code> by asking the
     * {@link EventSpeechRuleProcessor} to match it against its rules and in
     * case an utterance is generated it is spoken. This method is responsible
     * for recycling of the processed event.
     *
     * @param event The event to process.
     */
    private void processAndRecycleEvent(AccessibilityEvent event) {
        if (event == null) return;
        LogUtils.log(this, Log.DEBUG, "Processing event: %s", event);

        final Utterance utterance = new Utterance();

        if (mEventSpeechRuleProcessor.processEvent(event, utterance)) {
            if (mTestingListener != null) {
                mTestingListener.onUtteranceQueued(utterance);
            }

            provideFeedbackForUtterance(computeQueuingMode(utterance, event), utterance);
        } else {
            // Failed to match event to a rule, so the utterance is empty.
            LogUtils.log(this, Log.WARN, "Failed to process event");
        }

        event.recycle();
    }

    /**
     * Provides feedback for the specified utterance.
     *
     * @param queueMode The queueMode of the Utterance.
     * @param utterance The utterance to provide feedback for.
     */
    private void provideFeedbackForUtterance(int queueMode, Utterance utterance) {
        final Bundle metadata = utterance.getMetadata();
        final float earconRate = metadata.getFloat(Utterance.KEY_METADATA_EARCON_RATE, 1.0f);
        final float earconVolume = metadata.getFloat(Utterance.KEY_METADATA_EARCON_VOLUME, 1.0f);
        final Bundle nonSpeechMetadata = new Bundle();
        nonSpeechMetadata.putFloat(Utterance.KEY_METADATA_EARCON_RATE, earconRate);
        nonSpeechMetadata.putFloat(Utterance.KEY_METADATA_EARCON_VOLUME, earconVolume);

        // Perform cleanup of spoken text for each separate part of the utterance, e.g. we do not
        // want to combine repeated characters if they span different parts, and we still want to
        // expand single-character symbols if a certain part is a single character.
        final SpannableStringBuilder textToSpeak = new SpannableStringBuilder();
        for (CharSequence text : utterance.getSpoken()) {
            if (!TextUtils.isEmpty(text)) {
                CharSequence processedText =
                        SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(mContext, text);
                StringBuilderUtils.appendWithSeparator(textToSpeak, processedText);
            }
        }

        // Get speech settings from utterance.
        final int flags = metadata.getInt(Utterance.KEY_METADATA_SPEECH_FLAGS, 0);
        final Bundle speechMetadata = metadata.getBundle(Utterance.KEY_METADATA_SPEECH_PARAMS);

        final int utteranceGroup = utterance.getMetadata().getInt(Utterance.KEY_UTTERANCE_GROUP,
                SpeechController.UTTERANCE_GROUP_DEFAULT);

        mSpeechController.speak(textToSpeak, utterance.getAuditory(), utterance.getHaptic(),
                queueMode, flags, utteranceGroup, speechMetadata, nonSpeechMetadata);
    }

    /**
     * Computes the queuing mode for the current utterance.
     *
     * @param utterance to compute queuing from
     * @return A queuing mode, one of:
     *         <ul>
     *         <li>{@link SpeechController#QUEUE_MODE_INTERRUPT}
     *         <li>{@link SpeechController#QUEUE_MODE_QUEUE}
     *         <li>{@link SpeechController#QUEUE_MODE_UNINTERRUPTIBLE}
     *         </ul>
     */
    private int computeQueuingMode(Utterance utterance, AccessibilityEvent event) {
        final Bundle metadata = utterance.getMetadata();
        final int eventType = event.getEventType();

        // Queue events that occur automatically after window state changes.
        if (((event.getEventType() & AccessibilityEventProcessor.AUTOMATIC_AFTER_STATE_CHANGE) != 0)
                && ((event.getEventTime() - mLastWindowStateChanged)
                        < AccessibilityEventProcessor.DELAY_AUTO_AFTER_STATE)) {
            return SpeechController.QUEUE_MODE_QUEUE;
        }

        if(eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
            AccessibilityNodeInfoCompat node = record.getSource();
            if (node != null) {
                int liveRegionMode = node.getLiveRegion();
                if (liveRegionMode == View.ACCESSIBILITY_LIVE_REGION_POLITE) {
                    return SpeechController.QUEUE_MODE_QUEUE;
                }
            }
        }

        int queueMode = metadata.getInt(Utterance.KEY_METADATA_QUEUING,
                SpeechController.QUEUE_MODE_INTERRUPT);

        // Always collapse events of the same type.
        if (mLastEventType == eventType &&
                queueMode != SpeechController.QUEUE_MODE_UNINTERRUPTIBLE) {
            return SpeechController.QUEUE_MODE_INTERRUPT;
        }

        mLastEventType = eventType;

        return queueMode;
    }

    private static class ProcessorEventHandler extends WeakReferenceHandler<ProcessorEventQueue> {
        /** Speak action. */
        private static final int WHAT_SPEAK = 1;

        public ProcessorEventHandler(ProcessorEventQueue parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message message, ProcessorEventQueue parent) {
            switch (message.what) {
                case WHAT_SPEAK:
                    processAllEvents(parent);
                    break;
            }
        }

        /**
         * Attempts to process all events in the queue.
         */
        private void processAllEvents(ProcessorEventQueue parent) {
            while (true) {
                final AccessibilityEvent event;

                synchronized (parent.mEventQueue) {
                    if (parent.mEventQueue.isEmpty()) {
                        return;
                    }

                    event = parent.mEventQueue.dequeue();
                }

                parent.processAndRecycleEvent(event);
            }
        }

        /**
         * Sends {@link #WHAT_SPEAK} to the speech handler. This method cancels
         * the old message (if such exists) since it is no longer relevant.
         */
        public void postSpeak() {
            if (!hasMessages(WHAT_SPEAK)) {
                sendEmptyMessage(WHAT_SPEAK);
            }
        }
    }
}
