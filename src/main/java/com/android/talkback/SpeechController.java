/*
 * Copyright (C) 2011 Google Inc.
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

package com.android.talkback;

import android.os.Message;
import android.support.annotation.NonNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech.Engine;
import android.support.v4.os.BuildCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.eventprocessor.EventState;
import com.android.utils.FailoverTextToSpeech;
import com.android.utils.LogUtils;
import com.android.utils.ProximitySensor;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.StringBuilderUtils;
import com.android.utils.compat.media.AudioSystemCompatUtils;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Handles text-to-speech.
 */
public class SpeechController implements TalkBackService.KeyEventListener {
    /** Prefix for utterance IDs. */
    private static final String UTTERANCE_ID_PREFIX = "talkback_";

    /** Default stream for speech output. */
    public static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    // Queue modes.
    public static final int QUEUE_MODE_INTERRUPT = 0;
    public static final int QUEUE_MODE_QUEUE = 1;
    /**
     * Similiar to QUEUE_MODE_QUEUE. The only difference is FeedbackItem in this mode cannot be
     * interrupted by another while it is speaking. This includes not being removed from the queue
     * unless shutdown is called.
     */
    public static final int QUEUE_MODE_UNINTERRUPTIBLE = 2;
    public static final int QUEUE_MODE_FLUSH_ALL = 3;

    // Speech item status codes.
    private static final int STATUS_ERROR = 1;
    public static final int STATUS_SPEAKING = 2;
    public static final int STATUS_INTERRUPTED = 3;
    public static final int STATUS_SPOKEN = 4;

    public static final int UTTERANCE_GROUP_DEFAULT = 0;
    public static final int UTTERANCE_GROUP_TEXT_SELECTION = 1;
    public static final int UTTERANCE_GROUP_SEEK_PROGRESS = 2;

    /**
     * The number of recently-spoken items to keep in history.
     */
    private static final int MAX_HISTORY_ITEMS = 10;

    /**
     * The delay, in ms, after which a recently-spoken item will be considered for duplicate removal
     * in the event that a new feedback item has the flag {@link FeedbackItem#FLAG_SKIP_DUPLICATE}.
     * (The delay does not apply to queued items that haven't been spoken yet or to the currently
     * speaking item; these items will always be considered.)
     */
    private static final int SKIP_DUPLICATES_DELAY = 1000;

    /**
     * Class defining constants used for describing speech parameters.
     */
    public static class SpeechParam {
        /** Float parameter for controlling speech volume. Range is {0 ... 2}. */
        public static final String VOLUME = Engine.KEY_PARAM_VOLUME;

        /** Float parameter for controlling speech rate. Range is {0 ... 2}. */
        public static final String RATE = "rate";

        /** Float parameter for controlling speech pitch. Range is {0 ... 2}. */
        public static final String PITCH = "pitch";
    }

    /**
     * Reusable map used for passing parameters to the TextToSpeech.
     */
    private final HashMap<String, String> mSpeechParametersMap = new HashMap<>();

    /**
     * Priority queue of actions to perform when utterances are completed,
     * ordered by ascending utterance index.
     */
    private final PriorityQueue<UtteranceCompleteAction> mUtteranceCompleteActions =
            new PriorityQueue<>();

    /** The list of items to be spoken. */
    private final LinkedList<FeedbackItem> mFeedbackQueue = new LinkedList<>();

    /** The list of recently-spoken items. */
    private final LinkedList<FeedbackItem> mFeedbackHistory = new LinkedList<>();

    /** The parent service. */
    private final TalkBackService mService;

    /** The audio manager, used to query ringer volume. */
    private final AudioManager mAudioManager;

    /** The feedback controller, used for playing auditory icons and vibration */
    private final FeedbackController mFeedbackController;

    /** The text-to-speech service, used for speaking. */
    private final FailoverTextToSpeech mFailoverTts;

    /** Proximity sensor for implementing "shut up" functionality. */
    private ProximitySensor mProximitySensor;

    /** Listener used for testing. */
    private SpeechControllerListener mSpeechListener;

    /** An iterator at the fragment currently being processed */
    private Iterator<FeedbackFragment> mCurrentFragmentIterator = null;

    /** The item current being spoken, or {@code null} if the TTS is idle. */
    private FeedbackItem mCurrentFeedbackItem;

    /** Whether to use the proximity sensor to silence speech. */
    private boolean mSilenceOnProximity;

    /** Whether we should request audio focus during speech. */
    private boolean mUseAudioFocus;

    /** Whether or not the screen is on. */
    // This is set by RingerModeAndScreenMonitor and used by SpeechController
    // to determine if the ProximitySensor should be on or off.
    private boolean mScreenIsOn;

    /** The text-to-speech screen overlay. */
    private TextToSpeechOverlay mTtsOverlay;

    /**
     * Whether the speech controller should add utterance callbacks to
     * FullScreenReadController
     */
    private boolean mInjectFullScreenReadCallbacks;

    /** The utterance completed callback for FullScreenReadController */
    private UtteranceCompleteRunnable mFullScreenReadNextCallback;

    /**
     * The next utterance index; each utterance value will be constructed from this
     * ever-increasing index.
     */
    private int mNextUtteranceIndex = 0;

    /** Whether rate and pitch can change. */
    private boolean mUseIntonation;

    /** The speech rate adjustment (default is 1.0). */
    private float mSpeechRate;

    /** The speech pitch adjustment (default is 1.0). */
    private float mSpeechPitch;

    /** The speech volume adjustment (default is 1.0). */
    private float mSpeechVolume;

    /**
     * Whether the controller is currently speaking utterances. Used to check
     * consistency of internal speaking state.
     */
    private boolean mIsSpeaking;

    private VoiceRecognitionChecker mVoiceRecognitionChecker;

    /**
     * Used to keep track of whether the interrupt TTS key (Ctrl key) is currently pressed.
     */
    private boolean mInterruptKeyDown = false;

    @SuppressWarnings("FieldCanBeLocal")
    private final OnSharedPreferenceChangeListener prefListener;

    public SpeechController(TalkBackService context,
                            FeedbackController feedbackController) {
        if (feedbackController == null) throw new IllegalStateException();

        mService = context;
        mService.addServiceStateListener(new TalkBackService.ServiceStateListener() {
            @Override
            public void onServiceStateChanged(int newState) {
                if (newState == TalkBackService.SERVICE_STATE_ACTIVE) {
                    setProximitySensorState(true);
                } else if (newState == TalkBackService.SERVICE_STATE_SUSPENDED) {
                    setProximitySensorState(false);
                }
            }
        });

        mAudioManager = (AudioManager) mService.getSystemService(Context.AUDIO_SERVICE);

        mFailoverTts = new FailoverTextToSpeech(context);
        mFailoverTts.addListener(new FailoverTextToSpeech.FailoverTtsListener() {
            @Override
            public void onTtsInitialized(boolean wasSwitchingEngines) {
                SpeechController.this.onTtsInitialized(wasSwitchingEngines);
            }

            @Override
            public void onUtteranceCompleted(String utteranceId, boolean success) {
                // Utterances from FailoverTts are considered fragments in SpeechController
                SpeechController.this.onFragmentCompleted(utteranceId, success, true /* advance */);
            }
        });

        mFeedbackController = feedbackController;
        mInjectFullScreenReadCallbacks = false;

        prefListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if ((key == null) || (prefs == null)) {
                    return;
                }

                reloadPreferences(prefs);
            }
        };
        final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
        // Handles preference changes that affect speech.
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        reloadPreferences(prefs);

        mScreenIsOn = true;
        mVoiceRecognitionChecker = new VoiceRecognitionChecker();
    }

    /**
     * @return {@code true} if the speech controller is currently speaking.
     */
    public boolean isSpeaking() {
        return mIsSpeaking;
    }

    /**
     * @return {@code true} if the speech controller has feedback queued up to speak
     */
    private boolean isSpeechQueued() {
        return !mFeedbackQueue.isEmpty();
    }

    /* package */ boolean isSpeakingOrSpeechQueued() {
        return isSpeaking() || isSpeechQueued();
    }

    public void setSpeechListener(SpeechControllerListener speechListener) {
        mSpeechListener = speechListener;
    }

    /**
     * Sets whether or not the proximity sensor should be used to silence
     * speech.
     * <p>
     * This should be called when the user changes the state of the "silence on
     * proximity" preference.
     */
    public void setSilenceOnProximity(boolean silenceOnProximity) {
        mSilenceOnProximity = silenceOnProximity;

        // Propagate the proximity sensor change.
        setProximitySensorState(mSilenceOnProximity);
    }

    /**
     * Lets the SpeechController know whether the screen is on.
     */
    public void setScreenIsOn(boolean screenIsOn) {
        mScreenIsOn = screenIsOn;

        // The proximity sensor should always be on when the screen is on so
        // that the proximity gesture can be used to silence all apps.
        if (mScreenIsOn) {
            setProximitySensorState(true);
        }
    }

    /**
     * Sets whether the SpeechController should inject utterance completed
     * callbacks for advancing continuous reading.
     */
    public void setShouldInjectAutoReadingCallbacks(
            boolean shouldInject, UtteranceCompleteRunnable nextItemCallback) {
        mFullScreenReadNextCallback = (shouldInject) ? nextItemCallback : null;
        mInjectFullScreenReadCallbacks = shouldInject;

        if (!shouldInject) {
            removeUtteranceCompleteAction(nextItemCallback);
        }
    }

    /**
     * Forces a reload of the user's preferred TTS engine, if it is available and the current TTS
     * engine is not the preferred engine.
     * @param quiet suppresses the "Using XYZ engine" message if the TTS engine changes
     */
    public void updateTtsEngine(boolean quiet) {
        if (quiet) {
            EventState.getInstance().addEvent(
                    EventState.EVENT_SKIP_FEEDBACK_AFTER_QUIET_TTS_CHANGE);
        }
        mFailoverTts.updateDefaultEngine();
    }

    /**
     * Gets the {@link FailoverTextToSpeech} instance that is serving as a text-to-speech service.
     *
     * @return The text-to-speech service.
     */
    /* package */ FailoverTextToSpeech getFailoverTts() {
        return mFailoverTts;
    }

    /**
     * Repeats the last spoken utterance.
     */
    public boolean repeatLastUtterance() {
        return repeatUtterance(getLastUtterance());
    }

    /**
     * Copies the last phrase spoken by TalkBack to clipboard
     */
    public boolean copyLastUtteranceToClipboard(FeedbackItem item) {
        if (item == null) {
            return false;
        }

        final ClipboardManager clipboard = (ClipboardManager) mService.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(null, item.getAggregateText());
        clipboard.setPrimaryClip(clip);

        // Verify that we actually have the utterance on the clipboard
        clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
            speak(mService.getString(R.string.template_text_copied,
                    clip.getItemAt(0).getText().toString()) /* text */,
                    QUEUE_MODE_INTERRUPT /* queue mode */,
                    0 /* flags */,
                    null /* speech params */);
            return true;
        } else {
            return false;
        }
    }

    public FeedbackItem getLastUtterance() {
        if (mFeedbackHistory.isEmpty()) {
            return null;
        }
        return mFeedbackHistory.getLast();
    }

    public boolean repeatUtterance(FeedbackItem item) {
        if (item == null) {
            return false;
        }

        item.addFlag(FeedbackItem.FLAG_NO_HISTORY);
        speak(item, QUEUE_MODE_FLUSH_ALL, null);
        return true;
    }

    /**
     * Spells the last spoken utterance.
     */
    public boolean spellLastUtterance() {
        if (getLastUtterance() == null) {
            return false;
        }

        CharSequence aggregateText = getLastUtterance().getAggregateText();
        return spellUtterance(aggregateText);
    }

    /**
     * Spells the text.
     */
    public boolean spellUtterance(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < text.length(); i++) {
            final String cleanedChar = SpeechCleanupUtils.getCleanValueFor(
                    mService, text.charAt(i));

            StringBuilderUtils.appendWithSeparator(builder, cleanedChar);
        }

        speak(builder, null, null, QUEUE_MODE_FLUSH_ALL, UTTERANCE_GROUP_DEFAULT,
                FeedbackItem.FLAG_NO_HISTORY, null, null, null);
        return true;
    }

    /**
     * Speaks the name of the currently active TTS engine.
     */
    private void speakCurrentEngine() {
        final CharSequence engineLabel = mFailoverTts.getEngineLabel();
        if (TextUtils.isEmpty(engineLabel)) {
            return;
        }

        final String text = mService.getString(R.string.template_current_tts_engine, engineLabel);

        speak(text, null, null, QUEUE_MODE_QUEUE, FeedbackItem.FLAG_NO_HISTORY,
                UTTERANCE_GROUP_DEFAULT, null, null);
    }

    /**
     * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceCompleteRunnable)
     */
    public void speak(CharSequence text, int queueMode, int flags, Bundle speechParams) {
        speak(text, null, null, queueMode, flags, UTTERANCE_GROUP_DEFAULT, speechParams, null);
    }

    /**
     * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceCompleteRunnable)
     */
    public void speak(CharSequence text, Set<Integer> earcons, Set<Integer> haptics, int queueMode,
            int flags, int uttranceGroup, Bundle speechParams, Bundle nonSpeechParams) {
        speak(text, earcons, haptics, queueMode, flags, uttranceGroup,
                speechParams, nonSpeechParams, null);
    }

    /**
     * Cleans up and speaks an <code>utterance</code>. The <code>queueMode</code> determines
     * whether the speech will interrupt or wait on queued speech events.
     * <p>
     * This method does nothing if the text to speak is empty. See
     * {@link TextUtils#isEmpty(CharSequence)} for implementation.
     * <p>
     * See {@link SpeechCleanupUtils#cleanUp} for text clean-up implementation.
     *
     * @param text The text to speak.
     * @param earcons The set of earcon IDs to play.
     * @param haptics The set of vibration patterns to play.
     * @param queueMode The queue mode to use for speaking. One of:
     *            <ul>
     *            <li>{@link #QUEUE_MODE_INTERRUPT} <li>
     *            {@link #QUEUE_MODE_QUEUE} <li>
     *            {@link #QUEUE_MODE_UNINTERRUPTIBLE}
     *            </ul>
     * @param flags Bit mask of speaking flags. Use {@code 0} for no flags, or a
     *            combination of the flags defined in {@link FeedbackItem}
     * @param speechParams Speaking parameters. Not all parameters are supported by
     *            all engines. One of:
     *            <ul>
     *              <li>{@link SpeechParam#PITCH}</li>
     *              <li>{@link SpeechParam#RATE}</li>
     *              <li>{@link SpeechParam#VOLUME}</li>
     *            </ul>
     * @param nonSpeechParams Non-Speech parameters. Optional, but can include
     *            {@link Utterance#KEY_METADATA_EARCON_RATE} and
     *            {@link Utterance#KEY_METADATA_EARCON_VOLUME}
     * @param completedAction The action to run after this utterance has been
     *            spoken.
     */
    public void speak(CharSequence text, Set<Integer> earcons, Set<Integer> haptics, int queueMode,
            int flags, int utteranceGroup, Bundle speechParams, Bundle nonSpeechParams,
            UtteranceCompleteRunnable completedAction) {

        if (TextUtils.isEmpty(text) && (earcons == null || earcons.isEmpty()) &&
                (haptics == null || haptics.isEmpty())) {
            // don't process request with empty feedback
            return;
        }

        final FeedbackItem pendingItem = FeedbackProcessingUtils.generateFeedbackItemFromInput(
                mService, text, earcons, haptics, flags, utteranceGroup,
                speechParams, nonSpeechParams);

        speak(pendingItem, queueMode, completedAction);
    }

    private void speak(
            FeedbackItem item, int queueMode, UtteranceCompleteRunnable completedAction) {

        // If this FeedbackItem is flagged as NO_SPEECH, ignore speech and
        // immediately process earcons and haptics without disrupting the speech
        // queue.
        // TODO: Consider refactoring non-speech feedback out of
        // this class entirely.
        if (item.hasFlag(FeedbackItem.FLAG_NO_SPEECH)) {
            for (FeedbackFragment fragment : item.getFragments()) {
                playEarconsFromFragment(fragment);
                playHapticsFromFragment(fragment);
            }

            return;
        }

        if (item.hasFlag(FeedbackItem.FLAG_SKIP_DUPLICATE) && hasItemOnQueueOrSpeaking(item)) {
            return;
        }

        item.setUninterruptible(queueMode == QUEUE_MODE_UNINTERRUPTIBLE);
        item.setCompletedAction(completedAction);

        boolean currentFeedbackInterrupted = false;
        if(shouldClearQueue(item, queueMode)) {
            FeedbackItemFilter filter = getFeedbackItemFilter(item, queueMode);
            // Call onUtteranceComplete on each queue item to be cleared.
            ListIterator<FeedbackItem> iterator = mFeedbackQueue.listIterator(0);
            while (iterator.hasNext()) {
                FeedbackItem currentItem = iterator.next();
                if (filter.accept(currentItem)) {
                    iterator.remove();
                    notifyItemInterrupted(currentItem);
                }
            }

            if (mCurrentFeedbackItem != null && filter.accept(mCurrentFeedbackItem)) {
                notifyItemInterrupted(mCurrentFeedbackItem);
                currentFeedbackInterrupted = true;
            }
        }

        mFeedbackQueue.add(item);
        if (mSpeechListener != null) {
            mSpeechListener.onUtteranceQueued(item);
        }

        // If TTS isn't ready, this should be the only item in the queue.
        if (!mFailoverTts.isReady()) {
            LogUtils.log(this, Log.ERROR, "Attempted to speak before TTS was initialized.");
            return;
        }

        if ((mCurrentFeedbackItem == null) || currentFeedbackInterrupted) {
            mCurrentFragmentIterator = null;
            speakNextItem();
        } else {
            LogUtils.log(this, Log.VERBOSE, "Queued speech item, waiting for \"%s\"",
                    mCurrentFeedbackItem.getUtteranceId());
        }
    }

    private boolean shouldClearQueue(FeedbackItem item, int queueMode) {
        // QUEUE_MODE_INTERRUPT and QUEUE_MODE_FLUSH_ALL will clear the queue.
        if (queueMode != QUEUE_MODE_QUEUE && queueMode != QUEUE_MODE_UNINTERRUPTIBLE) {
            return true;
        }

        // If there is utterance group different from SpeechController.UTTERANCE_GROUP_DEFAULT
        // and flag FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP items
        // from same UTTERANCE_GRPOUP would be cleared from queue
        if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT &&
                item.hasFlag(FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP)) {
            return true;
        }

        // If there is utterance group different from SpeechController.UTTERANCE_GROUP_DEFAULT
        // and flag FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP
        // currently speaking item would be interrupted if it has the same utterance group
        if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT &&
                item.hasFlag(
                        FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP)) {
            return true;
        }

        return false;
    }

    private FeedbackItemFilter getFeedbackItemFilter(FeedbackItem item, int queueMode) {
        FeedbackItemFilter filter = new FeedbackItemFilter();
        if (queueMode != QUEUE_MODE_QUEUE && queueMode != QUEUE_MODE_UNINTERRUPTIBLE) {
            filter.addFeedbackItemPredicate(new FeedbackItemInterruptiblePredicate());
        }

        if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT &&
                item.hasFlag(FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP)) {
            FeedbackItemPredicate notCurrentItemPredicate = new FeedbackItemEqualSamplePredicate(
                    mCurrentFeedbackItem, false);
            FeedbackItemPredicate sameUtteranceGroupPredicate =
                    new FeedbackItemUtteranceGroupPredicate(item.getUtteranceGroup());
            FeedbackItemPredicate clearQueuePredicate = new FeedbackItemConjunctionPredicateSet(
                    notCurrentItemPredicate, sameUtteranceGroupPredicate);
            filter.addFeedbackItemPredicate(clearQueuePredicate);
        }

        if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT &&
                item.hasFlag(
                        FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP)) {
            FeedbackItemPredicate currentItemPredicate = new FeedbackItemEqualSamplePredicate(
                    mCurrentFeedbackItem, true);
            FeedbackItemPredicate sameUtteranceGroupPredicate =
                    new FeedbackItemUtteranceGroupPredicate(item.getUtteranceGroup());
            FeedbackItemPredicate clearQueuePredicate = new FeedbackItemConjunctionPredicateSet(
                    currentItemPredicate, sameUtteranceGroupPredicate);
            filter.addFeedbackItemPredicate(clearQueuePredicate);
        }

        return filter;
    }

    private void notifyItemInterrupted(FeedbackItem item) {
        final UtteranceCompleteRunnable queuedItemCompletedAction
                = item.getCompletedAction();
        if (queuedItemCompletedAction != null) {
            queuedItemCompletedAction.run(STATUS_INTERRUPTED);
        }
    }

    private boolean hasItemOnQueueOrSpeaking(FeedbackItem item) {
        if (item == null) {
            return false;
        }

        if (feedbackTextEquals(item, mCurrentFeedbackItem)) {
            return true;
        }

        for (FeedbackItem queuedItem : mFeedbackQueue) {
            if (feedbackTextEquals(item, queuedItem)) {
                return true;
            }
        }

        long currentTime = item.getCreationTime();
        for (FeedbackItem recentItem : mFeedbackHistory) {
            if (currentTime - recentItem.getCreationTime() < SKIP_DUPLICATES_DELAY) {
                if (feedbackTextEquals(item, recentItem)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Compares feedback fragments based on their text only. Ignores other parameters such as
     * earcons and interruptibility.
     */
    private boolean feedbackTextEquals(FeedbackItem item1, FeedbackItem item2) {
        if (item1 == null || item2 == null) {
            return false;
        }

        List<FeedbackFragment> fragments1 = item1.getFragments();
        List<FeedbackFragment> fragments2 = item2.getFragments();

        if (fragments1.size() != fragments2.size()) {
            return false;
        }

        int size = fragments1.size();
        for (int i = 0; i < size; i++) {
            FeedbackFragment fragment1 = fragments1.get(i);
            FeedbackFragment fragment2 = fragments2.get(i);

            if (fragment1 != null && fragment2 != null
                    && !TextUtils.equals(fragment1.getText(), fragment2.getText())) {
                return false;
            }

            if ((fragment1 == null && fragment2 != null)
                    || (fragment1 != null && fragment2 == null)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Add a new action that will be run when the given utterance index
     * completes.
     *
     * @param index The index of the utterance that should finish before this
     *            action is executed.
     * @param runnable The code to execute.
     */
    public void addUtteranceCompleteAction(int index, UtteranceCompleteRunnable runnable) {
        final UtteranceCompleteAction action = new UtteranceCompleteAction(index, runnable);
        mUtteranceCompleteActions.add(action);
    }

    /**
     * Removes all instances of the specified runnable from the utterance
     * complete action list.
     *
     * @param runnable The runnable to remove.
     */
    public void removeUtteranceCompleteAction(UtteranceCompleteRunnable runnable) {
        final Iterator<UtteranceCompleteAction> i = mUtteranceCompleteActions.iterator();

        while (i.hasNext()) {
            final UtteranceCompleteAction action = i.next();
            if (action.runnable == runnable) {
                i.remove();
            }
        }
    }

    /**
     * Stops all speech.
     */
    public void interrupt() {
        // Clear all current and queued utterances.
        clearCurrentAndQueuedUtterances();

        // Clear and post all remaining completion actions.
        clearUtteranceCompletionActions(true);

        // Make sure TTS actually stops talking.
        mFailoverTts.stopAll();
    }

    /**
     * Stops speech and shuts down this controller.
     */
    public void shutdown() {
        interrupt();

        mFailoverTts.shutdown();

        setOverlayEnabled(false);
        setProximitySensorState(false);
    }

    /**
     * Returns the next utterance identifier.
     */
    public int peekNextUtteranceId() {
        return mNextUtteranceIndex;
    }

    /**
     * Returns the next utterance identifier and increments the utterance value.
     */
    private int getNextUtteranceId() {
        return mNextUtteranceIndex++;
    }

    /**
     * Reloads preferences for this controller.
     *
     * @param prefs The shared preferences for this service. Pass {@code null}
     *            to disable the overlay.
     */
    private void reloadPreferences(SharedPreferences prefs) {
        final Resources res = mService.getResources();

        final boolean ttsOverlayEnabled = SharedPreferencesUtils.getBooleanPref(prefs, res,
                R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default);

        setOverlayEnabled(ttsOverlayEnabled);

        mUseIntonation = SharedPreferencesUtils.getBooleanPref(prefs, res,
                R.string.pref_intonation_key, R.bool.pref_intonation_default);
        mSpeechPitch = SharedPreferencesUtils.getFloatFromStringPref(prefs, res,
                R.string.pref_speech_pitch_key, R.string.pref_speech_pitch_default);
        mSpeechRate = SharedPreferencesUtils.getFloatFromStringPref(prefs, res,
                R.string.pref_speech_rate_key, R.string.pref_speech_rate_default);
        mUseAudioFocus = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);

        // Speech volume is stored as int [0,100] and scaled to float [0,1].
        mSpeechVolume = (SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                R.string.pref_speech_volume_key, R.string.pref_speech_volume_default) / 100.0f);

        if (!mUseAudioFocus) {
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
        }
    }

    private void setOverlayEnabled(boolean enabled) {
        if (enabled && mTtsOverlay == null) {
            mTtsOverlay = new TextToSpeechOverlay(mService);
        } else if (!enabled && mTtsOverlay != null) {
            mTtsOverlay.hide();
            mTtsOverlay = null;
        }
    }

    /**
     * Returns {@code true} if speech should be silenced. Does not prevent
     * haptic or auditory feedback from occurring. The controller will run
     * utterance completion actions immediately for silenced utterances.
     * <p>
     * Silences speech in the following cases:
     * <ul>
     * <li>Speech recognition is active and the user is not using a headset
     * </ul>
     */
    @SuppressWarnings("deprecation")
    private boolean shouldSilenceSpeech(FeedbackItem item) {
        if (item == null) {
            return false;
        }
        // Unless otherwise flagged, don't speak during speech recognition.
        return !item.hasFlag(FeedbackItem.FLAG_DURING_RECO)
                && AudioSystemCompatUtils.isSourceActive(AudioSource.VOICE_RECOGNITION)
                && !mAudioManager.isBluetoothA2dpOn() && !mAudioManager.isWiredHeadsetOn();
    }

    /**
     * Sends the specified item to the text-to-speech engine. Manages internal
     * speech controller state.
     * <p>
     * This method should only be called by {@link #speakNextItem()}.
     *
     * @param item The item to speak.
     */
    @SuppressLint("InlinedApi")
    private void speakNextItemInternal(FeedbackItem item) {
        final int utteranceIndex = getNextUtteranceId();
        final String utteranceId = UTTERANCE_ID_PREFIX + utteranceIndex;
        item.setUtteranceId(utteranceId);

        final UtteranceCompleteRunnable completedAction = item.getCompletedAction();
        if (completedAction != null) {
            addUtteranceCompleteAction(utteranceIndex, completedAction);
        }

        if (mInjectFullScreenReadCallbacks
                && item.hasFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING)) {
            addUtteranceCompleteAction(utteranceIndex, mFullScreenReadNextCallback);
        }

        if ((item != null) && !item.hasFlag(FeedbackItem.FLAG_NO_HISTORY)) {
            while (mFeedbackHistory.size() >= MAX_HISTORY_ITEMS) {
                mFeedbackHistory.removeFirst();
            }
            mFeedbackHistory.addLast(item);
        }

        if (mSpeechListener != null) {
            mSpeechListener.onUtteranceStarted(item);
        }

        processNextFragmentInternal();
    }

    private boolean processNextFragmentInternal() {
        if (mCurrentFragmentIterator == null || !mCurrentFragmentIterator.hasNext()) {
            return false;
        }

        FeedbackFragment fragment = mCurrentFragmentIterator.next();
        playEarconsFromFragment(fragment);
        playHapticsFromFragment(fragment);

        // Reuse the global instance of speech parameters.
        final HashMap<String, String> params = mSpeechParametersMap;
        params.clear();

        // Add all custom speech parameters.
        final Bundle speechParams = fragment.getSpeechParams();
        for (String key : speechParams.keySet()) {
            params.put(key, String.valueOf(speechParams.get(key)));
        }

        // Utterance ID, stream, and volume override item params.
        params.put(Engine.KEY_PARAM_UTTERANCE_ID, mCurrentFeedbackItem.getUtteranceId());
        params.put(Engine.KEY_PARAM_STREAM, String.valueOf(DEFAULT_STREAM));
        params.put(Engine.KEY_PARAM_VOLUME, String.valueOf(mSpeechVolume));

        final float pitch =
                mSpeechPitch * (mUseIntonation ? parseFloatParam(params, SpeechParam.PITCH, 1) : 1);
        final float rate =
                mSpeechRate * (mUseIntonation ? parseFloatParam(params, SpeechParam.RATE, 1) : 1);
        final CharSequence text;
        if (shouldSilenceSpeech(mCurrentFeedbackItem) || TextUtils.isEmpty(fragment.getText())) {
            text = null;
        } else {
            text = fragment.getText();
        }

        String logText = text == null ? null : text.toString();
        LogUtils.log(this, Log.VERBOSE, "Speaking fragment text \"%s\"", logText);

        mVoiceRecognitionChecker.onUtteranceStart();

        // It's okay if the utterance is empty, the fail-over TTS will
        // immediately call the fragment completion listener. This process is
        // important for things like continuous reading.
        mFailoverTts.speak(text, pitch, rate, params, DEFAULT_STREAM, mSpeechVolume);

        if (mTtsOverlay != null) {
            mTtsOverlay.speak(text);
        }

        return true;
    }

    /**
     * Plays all earcons stored in a {@link FeedbackFragment}.
     *
     * @param fragment The fragment to process
     */
    private void playEarconsFromFragment(FeedbackFragment fragment) {
        final Bundle nonSpeechParams = fragment.getNonSpeechParams();
        final float earconRate = nonSpeechParams.getFloat(Utterance.KEY_METADATA_EARCON_RATE, 1.0f);
        final float earconVolume = nonSpeechParams.getFloat(
                Utterance.KEY_METADATA_EARCON_VOLUME, 1.0f);

        for (int keyResId : fragment.getEarcons()) {
            mFeedbackController.playAuditory(keyResId, earconRate, earconVolume);
        }
    }

    /**
     * Produces all haptic feedback stored in a {@link FeedbackFragment}.
     *
     * @param fragment The fragment to process
     */
    private void playHapticsFromFragment(FeedbackFragment fragment) {
        for (int keyResId : fragment.getHaptics()) {
            mFeedbackController.playHaptic(keyResId);
        }
    }

    /**
     * @return The utterance ID, or -1 if the ID is invalid.
     */
    private static int parseUtteranceId(String utteranceId) {
        // Check for bad utterance ID. This should never happen.
        if (!utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
            LogUtils.log(SpeechController.class, Log.ERROR, "Bad utterance ID: %s", utteranceId);
            return -1;
        }

        try {
            return Integer.parseInt(utteranceId.substring(UTTERANCE_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Called when transitioning from an idle state to a speaking state, e.g.
     * the queue was empty, there was no current speech, and a speech item was
     * added to the queue.
     *
     * @see #handleSpeechCompleted()
     */
    private void handleSpeechStarting() {
        // Always enable the proximity sensor when speaking.
        setProximitySensorState(true);

        boolean useAudioFocus = mUseAudioFocus;
        if (BuildCompat.isAtLeastN()) {
            List<AudioRecordingConfiguration> recordConfigurations =
                    mAudioManager.getActiveRecordingConfigurations();
            if (recordConfigurations.size() != 0)
                useAudioFocus = false;
        }

        if (useAudioFocus) {
            mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }

        if (mIsSpeaking) {
            LogUtils.log(this, Log.ERROR, "Started speech while already speaking!");
        }

        mIsSpeaking = true;
    }

    /**
     * Called when transitioning from a speaking state to an idle state, e.g.
     * all queued utterances have been spoken and the last utterance has
     * completed.
     *
     * @see #handleSpeechStarting()
     */
    private void handleSpeechCompleted() {
        // If the screen is on, keep the proximity sensor on.
        setProximitySensorState(mScreenIsOn);

        if (mUseAudioFocus) {
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
        }

        if (!mIsSpeaking) {
            LogUtils.log(this, Log.ERROR, "Completed speech while already completed!");
        }

        mIsSpeaking = false;
    }

    /**
     * Clears the speech queue and completes the current speech item, if any.
     */
    private void clearCurrentAndQueuedUtterances() {
        mFeedbackQueue.clear();
        mCurrentFragmentIterator = null;

        if (mCurrentFeedbackItem != null) {
            final String utteranceId = mCurrentFeedbackItem.getUtteranceId();
            onFragmentCompleted(utteranceId, false /* success */, true /* advance */);
            mCurrentFeedbackItem = null;
        }
    }

    /**
     * Clears (and optionally posts) all pending completion actions.
     *
     * @param execute {@code true} to post actions to the handler.
     */
    private void clearUtteranceCompletionActions(boolean execute) {
        if (!execute) {
            mUtteranceCompleteActions.clear();
            return;
        }

        while (!mUtteranceCompleteActions.isEmpty()) {
            final UtteranceCompleteRunnable runnable = mUtteranceCompleteActions.poll().runnable;
            if (runnable != null) {
                mHandler.post(new CompletionRunner(runnable, STATUS_INTERRUPTED));
            }
        }

        // Don't call handleSpeechCompleted(), it will be called by the TTS when
        // it stops the current current utterance.
    }

    /**
     * Handles completion of a {@link FeedbackFragment}.
     * <p>
     *
     * @param utteranceId The ID of the {@link FeedbackItem} the fragment belongs to.
     * @param success Whether the fragment was spoken successfully.
     * @param advance Whether to advance to the next queue item.
     */
    private void onFragmentCompleted(String utteranceId, boolean success, boolean advance) {
        final int utteranceIndex = SpeechController.parseUtteranceId(utteranceId);
        final boolean interrupted = (mCurrentFeedbackItem != null)
                && (!mCurrentFeedbackItem.getUtteranceId().equals(utteranceId));

        final int status;

        if (interrupted) {
            status = STATUS_INTERRUPTED;
        } else if (success) {
            status = STATUS_SPOKEN;
        } else {
            status = STATUS_ERROR;
        }

        // Process the next fragment for this FeedbackItem if applicable.
        if ((status != STATUS_SPOKEN) || !processNextFragmentInternal()) {
            // If speaking resulted in an error, was ultimately interrupted, or
            // there are no additional fragments to speak as part of the current
            // FeedbackItem, finish processing of this utterance.
            onUtteranceCompleted(utteranceIndex, status, interrupted, advance);
        }
    }

    /**
     * Handles the completion of an {@link Utterance}/{@link FeedbackItem}.
     *
     * @param utteranceIndex The ID of the utterance that has completed.
     * @param status One of {@link SpeechController#STATUS_ERROR},
     *            {@link SpeechController#STATUS_INTERRUPTED}, or
     *            {@link SpeechController#STATUS_SPOKEN}
     * @param interrupted {@code true} if the utterance was interrupted, {@code false} otherwise
     * @param advance Whether to advance to the next queue item.
     */
    private void onUtteranceCompleted(
            int utteranceIndex, int status, boolean interrupted, boolean advance) {
        while (!mUtteranceCompleteActions.isEmpty()
                && (mUtteranceCompleteActions.peek().utteranceIndex <= utteranceIndex)) {
            final UtteranceCompleteRunnable runnable = mUtteranceCompleteActions.poll().runnable;
            if (runnable != null) {
                mHandler.post(new CompletionRunner(runnable, status));
            }
        }

        if (mSpeechListener != null) {
            mSpeechListener.onUtteranceCompleted(utteranceIndex, status);
        }

        if (interrupted) {
            // We finished an utterance, but we weren't expecting to see a
            // completion. This means we interrupted a previous utterance and
            // can safely ignore this callback.
            LogUtils.log(this, Log.VERBOSE, "Interrupted %d with %s", utteranceIndex,
                    mCurrentFeedbackItem.getUtteranceId());
            return;
        }

        if (advance && !speakNextItem()) {
            handleSpeechCompleted();
        }
    }

    private void onTtsInitialized(boolean wasSwitchingEngines) {
        // The previous engine may not have shut down correctly, so make sure to
        // clear the "current" speech item.
        if (mCurrentFeedbackItem != null) {
            onFragmentCompleted(mCurrentFeedbackItem.getUtteranceId(),
                    false /* success */, false /* advance */);
            mCurrentFeedbackItem = null;
        }

        if (wasSwitchingEngines && !EventState.getInstance().checkAndClearRecentEvent(
                EventState.EVENT_SKIP_FEEDBACK_AFTER_QUIET_TTS_CHANGE)) {
            speakCurrentEngine();
        } else if (!mFeedbackQueue.isEmpty()) {
            speakNextItem();
        }
    }

    /**
     * Removes and speaks the next {@link FeedbackItem} in the queue,
     * interrupting the current utterance if necessary.
     *
     * @return {@code false} if there are no more queued speech items.
     */
    private boolean speakNextItem() {
        final FeedbackItem previousItem = mCurrentFeedbackItem;
        final FeedbackItem nextItem = (mFeedbackQueue.isEmpty() ? null
                : mFeedbackQueue.removeFirst());

        mCurrentFeedbackItem = nextItem;

        if (nextItem == null) {
            LogUtils.log(this, Log.VERBOSE, "No next item, stopping speech queue");
            return false;
        }

        if (previousItem == null) {
            handleSpeechStarting();
        }

        mCurrentFragmentIterator = nextItem.getFragments().iterator();
        speakNextItemInternal(nextItem);
        return true;
    }

    /**
     * Attempts to parse a float value from a {@link HashMap} of strings.
     *
     * @param params The map to obtain the value from.
     * @param key The key that the value is assigned to.
     * @param defaultValue The default value.
     * @return The parsed float value, or the default value on failure.
     */
    private static float parseFloatParam(
            HashMap<String, String> params, String key, float defaultValue) {
        final String value = params.get(key);

        if (value == null) {
            return defaultValue;
        }

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return defaultValue;
    }

    /**
     * Enables/disables the proximity sensor. The proximity sensor should be
     * disabled when not in use to save battery.
     * <p>
     * This is a no-op if the user has turned off the "silence on proximity"
     * preference.
     *
     * @param enabled {@code true} if the proximity sensor should be enabled,
     *            {@code false} otherwise.
     */
    // TODO: Rewrite for readability.
    private void setProximitySensorState(boolean enabled) {
        if (mProximitySensor != null) {
            // Should we be using the proximity sensor at all?
            if (!mSilenceOnProximity) {
                mProximitySensor.stop();
                mProximitySensor = null;
                return;
            }

            if (!TalkBackService.isServiceActive()) {
                mProximitySensor.stop();
                return;
            }
        } else {
            // Do we need to initialize the proximity sensor?
            if (enabled && mSilenceOnProximity) {
                mProximitySensor = new ProximitySensor(mService);
                mProximitySensor.setProximityChangeListener(mProximityChangeListener);
            } else {
                return;
            }
        }

        // Manage the proximity sensor state.
        if (enabled) {
            mProximitySensor.start();
        } else {
            mProximitySensor.stop();
        }
    }

    /**
     * Stops the TTS engine when the Ctrl key is tapped without any other keys.
     */
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mInterruptKeyDown = (keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                    keyCode == KeyEvent.KEYCODE_CTRL_RIGHT);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (mInterruptKeyDown && (keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                    keyCode == KeyEvent.KEYCODE_CTRL_RIGHT)) {
                mInterruptKeyDown = false;
                mService.interruptAllFeedback();
            }
        }

        return false;
    }

    @Override
    public boolean processWhenServiceSuspended() {
        return false;
    }

    /**
     * Stops the TTS engine when the proximity sensor is close.
     */
    private final ProximitySensor.ProximityChangeListener mProximityChangeListener =
            new ProximitySensor.ProximityChangeListener() {
        @Override
        public void onProximityChanged(boolean isClose) {
            // Stop feedback if the user is close to the sensor.
            if (isClose) {
                mService.interruptAllFeedback();
            }
        }
    };

    private final Handler mHandler = new Handler();

    private final AudioManager.OnAudioFocusChangeListener
            mAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    LogUtils.log(SpeechController.this, Log.DEBUG, "Saw audio focus change: %d",
                            focusChange);
                }
            };

    /**
     * Listener for speech started and completed.
     */
    public interface SpeechControllerListener {
        public void onUtteranceQueued(FeedbackItem utterance);
        public void onUtteranceStarted(FeedbackItem utterance);
        public void onUtteranceCompleted(int utteranceIndex, int status);
    }

    /**
     * An action that should be performed after a particular utterance index
     * completes.
     */
    private static class UtteranceCompleteAction implements Comparable<UtteranceCompleteAction> {
        public UtteranceCompleteAction(int utteranceIndex, UtteranceCompleteRunnable runnable) {
            this.utteranceIndex = utteranceIndex;
            this.runnable = runnable;
        }

        /**
         * The minimum utterance index that must complete before this action
         * should be performed.
         */
        public int utteranceIndex;

        /** The action to execute. */
        public UtteranceCompleteRunnable runnable;

        @Override
        public int compareTo(@NonNull UtteranceCompleteAction another) {
            return (utteranceIndex - another.utteranceIndex);
        }
    }

    /**
     * Utility class run an UtteranceCompleteRunnable.
     */
    public static class CompletionRunner implements Runnable {
        private final UtteranceCompleteRunnable mRunnable;
        private final int mStatus;

        public CompletionRunner(UtteranceCompleteRunnable runnable, int status) {
            mRunnable = runnable;
            mStatus = status;
        }

        @Override
        public void run() {
            mRunnable.run(mStatus);
        }
    }

    /**
     * Interface for a run method with a status.
     */
    public interface UtteranceCompleteRunnable {
        /**
         * @param status The status supplied.
         */
        public void run(int status);
    }


    /**
     * Class that responsible for checking whether voice recognition is enabled and interrupt
     * utterances that should be silenced when mic is on
     */
    private class VoiceRecognitionChecker extends Handler {

        private int MESSAGE_ID = 0;
        private int NEXT_CHECK_DELAY = 100;

        public void onUtteranceStart() {
            removeMessages(MESSAGE_ID);
            sendEmptyMessageDelayed(MESSAGE_ID, NEXT_CHECK_DELAY);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mCurrentFeedbackItem == null || shouldSilenceSpeech(mCurrentFeedbackItem)) {
                mFailoverTts.stopFromTalkBack();
                removeMessages(MESSAGE_ID);
            } else {
                sendEmptyMessageDelayed(MESSAGE_ID, NEXT_CHECK_DELAY);
            }
        }
    }

    private interface FeedbackItemPredicate {
        public boolean accept(FeedbackItem item);
    }

    private class FeedbackItemDisjunctionPredicateSet implements FeedbackItemPredicate {
        private FeedbackItemPredicate mPredicate1;
        private FeedbackItemPredicate mPredicate2;

        public FeedbackItemDisjunctionPredicateSet(FeedbackItemPredicate predicate1,
                                                   FeedbackItemPredicate predicate2) {
            mPredicate1 = predicate1;
            mPredicate2 = predicate2;
        }

        @Override
        public boolean accept(FeedbackItem item) {
            return mPredicate1.accept(item) || mPredicate2.accept(item);
        }
    }

    private class FeedbackItemConjunctionPredicateSet implements FeedbackItemPredicate {
        private FeedbackItemPredicate mPredicate1;
        private FeedbackItemPredicate mPredicate2;

        public FeedbackItemConjunctionPredicateSet(FeedbackItemPredicate predicate1,
                                                   FeedbackItemPredicate predicate2) {
            mPredicate1 = predicate1;
            mPredicate2 = predicate2;
        }

        @Override
        public boolean accept(FeedbackItem item) {
            return mPredicate1.accept(item) && mPredicate2.accept(item);
        }
    }

    private class FeedbackItemInterruptiblePredicate implements FeedbackItemPredicate {
        public boolean accept(FeedbackItem item) {
            if (item == null) {
                return false;
            }

            return item.isInterruptible();
        }
    }

    private class FeedbackItemEqualSamplePredicate implements FeedbackItemPredicate {

        private FeedbackItem mSample;
        private boolean mEqual;

        public FeedbackItemEqualSamplePredicate(FeedbackItem sample, boolean equal) {
            mSample = sample;
            mEqual = equal;
        }

        public boolean accept(FeedbackItem item) {
            if (mEqual) {
                return mSample == item;
            }

            return mSample != item;
        }
    }

    private class FeedbackItemUtteranceGroupPredicate implements FeedbackItemPredicate {

        private int mUtteranceGroup;

        public FeedbackItemUtteranceGroupPredicate(int utteranceGroup) {
            mUtteranceGroup = utteranceGroup;
        }

        public boolean accept(FeedbackItem item) {
            if (item == null) {
                return false;
            }

            return item.getUtteranceGroup() == mUtteranceGroup;
        }
    }

    private class FeedbackItemFilter {

        private FeedbackItemPredicate mPredicate;

        public void addFeedbackItemPredicate(FeedbackItemPredicate predicate) {
            if (predicate == null) {
                return;
            }

            if (mPredicate == null) {
                mPredicate = predicate;
            } else {
                mPredicate = new FeedbackItemDisjunctionPredicateSet(mPredicate, predicate);
            }
        }

        public boolean accept(FeedbackItem item) {
            return mPredicate != null && mPredicate.accept(item);
        }
    }
}
