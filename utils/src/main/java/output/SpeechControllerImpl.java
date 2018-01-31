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

package com.google.android.accessibility.utils.output;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech.Engine;
import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FailoverTextToSpeech;
import com.google.android.accessibility.utils.FailoverTextToSpeech.SpeechParam;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;

/** Handles text-to-speech. */
public class SpeechControllerImpl implements SpeechController {
  /** Prefix for utterance IDs. */
  private static final String UTTERANCE_ID_PREFIX = "talkback_";

  /** The number of recently-spoken items to keep in history. */
  private static final int MAX_HISTORY_ITEMS = 10;

  /**
   * The delay, in ms, after which a recently-spoken item will be considered for duplicate removal
   * in the event that a new feedback item has the flag {@link FeedbackItem#FLAG_SKIP_DUPLICATE}.
   * (The delay does not apply to queued items that haven't been spoken yet or to the currently
   * speaking item; these items will always be considered.)
   */
  private static final int SKIP_DUPLICATES_DELAY = 1000;

  /** Reusable map used for passing parameters to the TextToSpeech. */
  private final HashMap<String, String> mSpeechParametersMap = new HashMap<>();

  /**
   * Priority queue of actions to perform before utterances start, ordered by ascending utterance
   * index.
   */
  private final PriorityQueue<UtteranceStartAction> mUtteranceStartActions = new PriorityQueue<>();

  /**
   * Priority queue of actions to perform when utterances are completed, ordered by ascending
   * utterance index.
   */
  private final PriorityQueue<UtteranceCompleteAction> mUtteranceCompleteActions =
      new PriorityQueue<>();

  /** Maps from utterance index to UtteranceRangeStartCallback. */
  private final HashMap<Integer, UtteranceRangeStartCallback> mUtteranceRangeStartCallbacks =
      new HashMap<>();

  /** The list of items to be spoken. */
  private final LinkedList<FeedbackItem> mFeedbackQueue = new LinkedList<>();

  /** The list of recently-spoken items. */
  private final LinkedList<FeedbackItem> mFeedbackHistory = new LinkedList<>();

  private final Context mContext;

  /** The SpeechController delegate, used to provide callbacks. */
  private final Delegate mDelegate;

  /** The audio manager, used to query ringer volume. */
  private final AudioManager mAudioManager;

  /** The feedback controller, used for playing auditory icons and vibration */
  private final FeedbackController mFeedbackController;

  /** The text-to-speech service, used for speaking. */
  private final FailoverTextToSpeech mFailoverTts;

  private boolean mShouldHandleTtsCallBackInMainThread = true;

  /** Listener used for testing. */
  private SpeechControllerListener mSpeechListener;

  private final Set<SpeechController.Observer> mObservers = new HashSet<>();

  /** An iterator at the fragment currently being processed */
  private Iterator<FeedbackFragment> mCurrentFragmentIterator = null;

  /** The item current being spoken, or {@code null} if the TTS is idle. */
  private FeedbackItem mCurrentFeedbackItem;

  /** Whether we should request audio focus during speech. */
  private boolean mUseAudioFocus = false;

  /** The text-to-speech screen overlay. */
  private TextToSpeechOverlay mTtsOverlay;

  /** Whether the speech controller should add utterance callbacks to FullScreenReadController */
  private boolean mInjectFullScreenReadCallbacks;

  /** The utterance completed callback for FullScreenReadController */
  private UtteranceCompleteRunnable mFullScreenReadNextCallback;

  /**
   * The next utterance index; each utterance value will be constructed from this ever-increasing
   * index.
   */
  private int mNextUtteranceIndex = 0;

  /** Whether rate and pitch can change. */
  private boolean mUseIntonation = true;

  /** The speech rate adjustment (default is 1.0). */
  private float mSpeechRate = 1.0f;

  /** The speech pitch adjustment (default is 1.0). */
  private float mSpeechPitch = 1.0f;

  /** The speech volume adjustment (default is 1.0). */
  private float mSpeechVolume = 1.0f;

  /**
   * Whether the controller is currently speaking utterances. Used to check consistency of internal
   * speaking state.
   */
  private boolean mIsSpeaking;

  /** Indicates that we want to switch TTS silently, i.e. don't say "Using XYZ engine". */
  private boolean mSkipNextTTSChangeAnnouncement = false;

  public SpeechControllerImpl(
      Context context, Delegate delegate, FeedbackController feedbackController) {
    mContext = context;
    mDelegate = delegate;

    mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

    mFailoverTts = new FailoverTextToSpeech(mContext);
    mFailoverTts.addListener(
        new FailoverTextToSpeech.FailoverTtsListener() {
          @Override
          public void onTtsInitialized(boolean wasSwitchingEngines) {
            SpeechControllerImpl.this.onTtsInitialized(wasSwitchingEngines);
          }

          @Override
          public void onUtteranceStarted(String utteranceId) {
            SpeechControllerImpl.this.onFragmentStarted(utteranceId);
          }

          @Override
          public void onUtteranceRangeStarted(String utteranceId, int start, int end) {
            SpeechControllerImpl.this.onFragmentRangeStarted(utteranceId, start, end);
          }

          @Override
          public void onUtteranceCompleted(String utteranceId, boolean success) {
            // Utterances from FailoverTts are considered fragments in SpeechControllerImpl
            SpeechControllerImpl.this.onFragmentCompleted(utteranceId, success, true /* advance */);
          }
        });

    mFeedbackController = feedbackController;
    mInjectFullScreenReadCallbacks = false;
  }

  /** @return {@code true} if the speech controller is currently speaking. */
  @Override
  public boolean isSpeaking() {
    return mIsSpeaking;
  }

  @Override
  public void addObserver(SpeechController.Observer observer) {
    mObservers.add(observer);
  }

  @Override
  public void removeObserver(SpeechController.Observer observer) {
    mObservers.remove(observer);
  }

  public void setUseAudioFocus(boolean useAudioFocus) {
    mUseAudioFocus = useAudioFocus;
    if (!mUseAudioFocus) {
      mAudioManager.abandonAudioFocus(mAudioFocusListener);
    }
  }

  public void setUseIntonation(boolean useIntonation) {
    mUseIntonation = useIntonation;
  }

  public void setSpeechPitch(float speechPitch) {
    mSpeechPitch = speechPitch;
  }

  public void setSpeechRate(float speechRate) {
    mSpeechRate = speechRate;
  }

  public void setSpeechVolume(float speechVolume) {
    mSpeechVolume = speechVolume;
  }

  /** @return {@code true} if the speech controller has feedback queued up to speak */
  private boolean isSpeechQueued() {
    return !mFeedbackQueue.isEmpty();
  }

  @Override
  public boolean isSpeakingOrSpeechQueued() {
    return isSpeaking() || isSpeechQueued();
  }

  @Override
  public void setSpeechListener(SpeechControllerListener speechListener) {
    mSpeechListener = speechListener;
  }

  @Override
  public void setHandleTtsCallbackInMainThread(boolean shouldHandleInMainThread) {
    mShouldHandleTtsCallBackInMainThread = shouldHandleInMainThread;
    mFailoverTts.setHandleTtsCallbackInMainThread(shouldHandleInMainThread);
  }

  /**
   * Sets whether the SpeechControllerImpl should inject utterance completed callbacks for advancing
   * continuous reading.
   */
  @Override
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
   *
   * @param quiet suppresses the "Using XYZ engine" message if the TTS engine changes
   */
  public void updateTtsEngine(boolean quiet) {
    mSkipNextTTSChangeAnnouncement = quiet;
    mFailoverTts.updateDefaultEngine();
  }

  /**
   * Gets the {@link FailoverTextToSpeech} instance that is serving as a text-to-speech service.
   *
   * @return The text-to-speech service.
   */
  @Override
  public FailoverTextToSpeech getFailoverTts() {
    return mFailoverTts;
  }

  /** Repeats the last spoken utterance. */
  @Override
  public boolean repeatLastUtterance() {
    return repeatUtterance(getLastUtterance());
  }

  /** Copies the last phrase spoken by TalkBack to clipboard */
  @Override
  public boolean copyLastUtteranceToClipboard(FeedbackItem item, EventId eventId) {
    if (item == null) {
      return false;
    }

    final ClipboardManager clipboard =
        (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText(null, item.getAggregateText());
    clipboard.setPrimaryClip(clip);

    // Verify that we actually have the utterance on the clipboard
    clip = clipboard.getPrimaryClip();
    if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
      speak(
          mContext.getString(
              R.string.template_text_copied, clip.getItemAt(0).getText().toString()) /* text */,
          QUEUE_MODE_INTERRUPT /* queue mode */,
          0 /* flags */,
          null /* speech params */,
          eventId);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public FeedbackItem getLastUtterance() {
    if (mFeedbackHistory.isEmpty()) {
      return null;
    }
    return mFeedbackHistory.getLast();
  }

  @Override
  public boolean repeatUtterance(FeedbackItem item) {
    if (item == null) {
      return false;
    }

    item.addFlag(FeedbackItem.FLAG_NO_HISTORY);
    speak(
        item, /* feedbackItem */
        QUEUE_MODE_FLUSH_ALL, /* queueMode */
        null, /* startAction */
        null, /* rangeStartCallback */
        null); /* completeAction */
    return true;
  }

  /** Spells the last spoken utterance. */
  @Override
  public boolean spellLastUtterance() {
    if (getLastUtterance() == null) {
      return false;
    }

    CharSequence aggregateText = getLastUtterance().getAggregateText();
    return spellUtterance(aggregateText);
  }

  /** Spells the text. */
  @Override
  public boolean spellUtterance(CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return false;
    }

    final SpannableStringBuilder builder = new SpannableStringBuilder();

    for (int i = 0; i < text.length(); i++) {
      final String cleanedChar = SpeechCleanupUtils.getCleanValueFor(mContext, text.charAt(i));

      StringBuilderUtils.appendWithSeparator(builder, cleanedChar);
    }

    speak(
        builder,
        null,
        null,
        QUEUE_MODE_FLUSH_ALL,
        UTTERANCE_GROUP_DEFAULT,
        FeedbackItem.FLAG_NO_HISTORY,
        null,
        null,
        null);
    return true;
  }

  /** Speaks the name of the currently active TTS engine. */
  private void speakCurrentEngine() {
    final CharSequence engineLabel = mFailoverTts.getEngineLabel();
    if (TextUtils.isEmpty(engineLabel)) {
      return;
    }

    final String text = mContext.getString(R.string.template_current_tts_engine, engineLabel);

    EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for TTS initialization.
    speak(
        text,
        null,
        null,
        QUEUE_MODE_QUEUE,
        FeedbackItem.FLAG_NO_HISTORY,
        UTTERANCE_GROUP_DEFAULT,
        null,
        null,
        eventId);
  }

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  @Override
  public void speak(
      CharSequence text, int queueMode, int flags, Bundle speechParams, EventId eventId) {
    speak(text, null, null, queueMode, flags, UTTERANCE_GROUP_DEFAULT, speechParams, null, eventId);
  }

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  @Override
  public void speak(
      CharSequence text,
      int queueMode,
      int flags,
      Bundle speechParams,
      UtteranceStartRunnable startingAction,
      UtteranceRangeStartCallback rangeStartCallback,
      UtteranceCompleteRunnable completedAction,
      EventId eventId) {
    speak(
        text,
        null,
        null,
        queueMode,
        flags,
        UTTERANCE_GROUP_DEFAULT,
        speechParams,
        null,
        startingAction,
        rangeStartCallback,
        completedAction,
        eventId);
  }

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  @Override
  public void speak(
      CharSequence text,
      Set<Integer> earcons,
      Set<Integer> haptics,
      int queueMode,
      int flags,
      int uttranceGroup,
      Bundle speechParams,
      Bundle nonSpeechParams,
      EventId eventId) {
    speak(
        text,
        earcons,
        haptics,
        queueMode,
        flags,
        uttranceGroup,
        speechParams,
        nonSpeechParams,
        null,
        null,
        null,
        eventId);
  }

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  @Override
  public void speak(CharSequence text, EventId eventId, SpeakOptions options) {
    if (options == null) {
      options = SpeakOptions.create();
    }
    speak(
        text,
        options.mEarcons,
        options.mHaptics,
        options.mQueueMode,
        options.mFlags,
        options.mUtteranceGroup,
        options.mSpeechParams,
        options.mNonSpeechParams,
        options.mStartingAction,
        options.mRangeStartCallback,
        options.mCompletedAction,
        eventId);
  }

  /**
   * Cleans up and speaks an <code>utterance</code>. The <code>queueMode</code> determines whether
   * the speech will interrupt or wait on queued speech events.
   *
   * <p>This method does nothing if the text to speak is empty. See {@link
   * TextUtils#isEmpty(CharSequence)} for implementation.
   *
   * <p>See {@link SpeechCleanupUtils#cleanUp} for text clean-up implementation.
   *
   * @param text The text to speak.
   * @param earcons The set of earcon IDs to play.
   * @param haptics The set of vibration patterns to play.
   * @param queueMode The queue mode to use for speaking. One of:
   *     <ul>
   *       <li>{@link #QUEUE_MODE_INTERRUPT}
   *       <li>{@link #QUEUE_MODE_QUEUE}
   *       <li>{@link #QUEUE_MODE_UNINTERRUPTIBLE}
   *     </ul>
   *
   * @param flags Bit mask of speaking flags. Use {@code 0} for no flags, or a combination of the
   *     flags defined in {@link FeedbackItem}
   * @param speechParams Speaking parameters. Not all parameters are supported by all engines. One
   *     of:
   *     <ul>
   *       <li>{@link SpeechParam#PITCH}
   *       <li>{@link SpeechParam#RATE}
   *       <li>{@link SpeechParam#VOLUME}
   *     </ul>
   *
   * @param nonSpeechParams Non-Speech parameters. Optional, but can include {@link
   *     Utterance#KEY_METADATA_EARCON_RATE} and {@link Utterance#KEY_METADATA_EARCON_VOLUME}
   * @param startAction The action to run before this utterance starts.
   * @param rangeStartCallback The callback to update the range of utterance being spoken.
   * @param completedAction The action to run after this utterance has been spoken.
   */
  @Override
  public void speak(
      CharSequence text,
      Set<Integer> earcons,
      Set<Integer> haptics,
      int queueMode,
      int flags,
      int utteranceGroup,
      Bundle speechParams,
      Bundle nonSpeechParams,
      UtteranceStartRunnable startAction,
      UtteranceRangeStartCallback rangeStartCallback,
      UtteranceCompleteRunnable completedAction,
      EventId eventId) {

    if (TextUtils.isEmpty(text)
        && (earcons == null || earcons.isEmpty())
        && (haptics == null || haptics.isEmpty())) {
      // don't process request with empty feedback
      if ((flags & FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING) != 0) {
        tryNotifyFullScreenReaderCallback();
      }
      return;
    }

    final FeedbackItem pendingItem =
        FeedbackProcessingUtils.generateFeedbackItemFromInput(
            mContext,
            text,
            earcons,
            haptics,
            flags,
            utteranceGroup,
            speechParams,
            nonSpeechParams,
            eventId);

    speak(pendingItem, queueMode, startAction, rangeStartCallback, completedAction);
  }

  private void speak(
      FeedbackItem item,
      int queueMode,
      UtteranceStartRunnable startAction,
      UtteranceRangeStartCallback rangeStartCallback,
      UtteranceCompleteRunnable completedAction) {

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
      if (item.hasFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING)) {
        tryNotifyFullScreenReaderCallback();
      }
      return;
    }

    if (item.hasFlag(FeedbackItem.FLAG_SKIP_DUPLICATE) && hasItemOnQueueOrSpeaking(item)) {
      return;
    }

    item.setUninterruptible(queueMode == QUEUE_MODE_UNINTERRUPTIBLE);
    item.setStartAction(startAction);
    item.setRangeStartCallback(rangeStartCallback);
    item.setCompletedAction(completedAction);

    boolean currentFeedbackInterrupted = false;
    if (shouldClearQueue(item, queueMode)) {
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
      LogUtils.log(
          this,
          Log.VERBOSE,
          "Queued speech item, waiting for \"%s\"",
          mCurrentFeedbackItem.getUtteranceId());
    }
  }

  private void tryNotifyFullScreenReaderCallback() {
    if (mInjectFullScreenReadCallbacks && mFullScreenReadNextCallback != null) {
      if (mShouldHandleTtsCallBackInMainThread) {
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                mFullScreenReadNextCallback.run(SpeechController.STATUS_NOT_SPOKEN);
              }
            });
      } else {
        mFullScreenReadNextCallback.run(SpeechController.STATUS_NOT_SPOKEN);
      }
    }
  }

  private boolean shouldClearQueue(FeedbackItem item, int queueMode) {
    // QUEUE_MODE_INTERRUPT and QUEUE_MODE_FLUSH_ALL will clear the queue.
    if (queueMode != QUEUE_MODE_QUEUE && queueMode != QUEUE_MODE_UNINTERRUPTIBLE) {
      return true;
    }

    // If there is utterance group different from SpeechControllerImpl.UTTERANCE_GROUP_DEFAULT
    // and flag FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP items
    // from same UTTERANCE_GRPOUP would be cleared from queue
    if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT
        && item.hasFlag(FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP)) {
      return true;
    }

    // If there is utterance group different from SpeechControllerImpl.UTTERANCE_GROUP_DEFAULT
    // and flag FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP
    // currently speaking item would be interrupted if it has the same utterance group
    if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT
        && item.hasFlag(FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP)) {
      return true;
    }

    return false;
  }

  private FeedbackItemFilter getFeedbackItemFilter(FeedbackItem item, int queueMode) {
    FeedbackItemFilter filter = new FeedbackItemFilter();
    if (queueMode != QUEUE_MODE_QUEUE && queueMode != QUEUE_MODE_UNINTERRUPTIBLE) {
      filter.addFeedbackItemPredicate(new FeedbackItemInterruptiblePredicate());
    }

    if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT
        && item.hasFlag(FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP)) {
      FeedbackItemPredicate notCurrentItemPredicate =
          new FeedbackItemEqualSamplePredicate(mCurrentFeedbackItem, false);
      FeedbackItemPredicate sameUtteranceGroupPredicate =
          new FeedbackItemUtteranceGroupPredicate(item.getUtteranceGroup());
      FeedbackItemPredicate clearQueuePredicate =
          new FeedbackItemConjunctionPredicateSet(
              notCurrentItemPredicate, sameUtteranceGroupPredicate);
      filter.addFeedbackItemPredicate(clearQueuePredicate);
    }

    if (item.getUtteranceGroup() != UTTERANCE_GROUP_DEFAULT
        && item.hasFlag(FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP)) {
      FeedbackItemPredicate currentItemPredicate =
          new FeedbackItemEqualSamplePredicate(mCurrentFeedbackItem, true);
      FeedbackItemPredicate sameUtteranceGroupPredicate =
          new FeedbackItemUtteranceGroupPredicate(item.getUtteranceGroup());
      FeedbackItemPredicate clearQueuePredicate =
          new FeedbackItemConjunctionPredicateSet(
              currentItemPredicate, sameUtteranceGroupPredicate);
      filter.addFeedbackItemPredicate(clearQueuePredicate);
    }

    return filter;
  }

  private void notifyItemInterrupted(FeedbackItem item) {
    final UtteranceCompleteRunnable queuedItemCompletedAction = item.getCompletedAction();
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
   * Compares feedback fragments based on their text only. Ignores other parameters such as earcons
   * and interruptibility.
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

      if (fragment1 != null
          && fragment2 != null
          && !TextUtils.equals(fragment1.getText(), fragment2.getText())) {
        return false;
      }

      if ((fragment1 == null && fragment2 != null) || (fragment1 != null && fragment2 == null)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Add a new action that will be run before the given utterance index starts.
   *
   * @param index The index of the utterance that should starts after this action is executed.
   * @param runnable The code to execute.
   */
  @Override
  public void addUtteranceStartAction(int index, UtteranceStartRunnable runnable) {
    final UtteranceStartAction action = new UtteranceStartAction(index, runnable);
    mUtteranceStartActions.add(action);
  }

  @Override
  public void setUtteranceRangeStartCallback(
      int utteranceId, UtteranceRangeStartCallback callback) {
    mUtteranceRangeStartCallbacks.put(utteranceId, callback);
  }

  /**
   * Add a new action that will be run when the given utterance index completes.
   *
   * @param index The index of the utterance that should finish before this action is executed.
   * @param runnable The code to execute.
   */
  @Override
  public void addUtteranceCompleteAction(int index, UtteranceCompleteRunnable runnable) {
    final UtteranceCompleteAction action = new UtteranceCompleteAction(index, runnable);
    mUtteranceCompleteActions.add(action);
  }

  /**
   * Removes all instances of the specified runnable from the utterance complete action list.
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

  @Override
  public void interrupt(boolean stopTtsSpeechCompletely) {
    // Clear all current and queued utterances.
    clearCurrentAndQueuedUtterances();

    clearUtteranceRangeStartCallbacks();
    // Clear and post all remaining completion actions.
    clearUtteranceCompletionActions(true);

    if (stopTtsSpeechCompletely) {
      // Stop all TTS audios.
      mFailoverTts.stopAll();
    } else {
      // Stop TTS audio from TalkBack.
      mFailoverTts.stopFromTalkBack();
    }
  }

  /** Stops speech and shuts down this controller. */
  public void shutdown() {
    interrupt(false /* stopTtsSpeechCompletely */);

    mFailoverTts.shutdown();

    setOverlayEnabled(false);
  }

  /** Returns the next utterance identifier. */
  @Override
  public int peekNextUtteranceId() {
    return mNextUtteranceIndex;
  }

  /** Returns the next utterance identifier and increments the utterance value. */
  private int getNextUtteranceId() {
    return mNextUtteranceIndex++;
  }

  public void setOverlayEnabled(boolean enabled) {
    if (enabled && mTtsOverlay == null) {
      mTtsOverlay = new TextToSpeechOverlay(mContext);
    } else if (!enabled && mTtsOverlay != null) {
      mTtsOverlay.hide();
      mTtsOverlay = null;
    }
  }

  /**
   * Returns {@code true} if speech should be silenced. Does not prevent haptic or auditory feedback
   * from occurring. The controller will run utterance completion actions immediately for silenced
   * utterances.
   *
   * <p>Silences speech in the following cases:
   *
   * <ul>
   *   <li>Speech recognition is active and the user is not using a headset
   * </ul>
   */
  @SuppressWarnings("deprecation")
  private boolean shouldSilenceSpeech(FeedbackItem item) {
    if (item == null) {
      return false;
    }
    // Unless otherwise flagged, don't speak during speech recognition.
    return !item.hasFlag(FeedbackItem.FLAG_FORCED_FEEDBACK)
        && mDelegate.shouldSuppressPassiveFeedback();
  }

  /**
   * Sends the specified item to the text-to-speech engine. Manages internal speech controller
   * state.
   *
   * <p>This method should only be called by {@link #speakNextItem()}.
   *
   * @param item The item to speak.
   */
  @SuppressLint("InlinedApi")
  private void speakNextItemInternal(FeedbackItem item) {
    final int utteranceIndex = getNextUtteranceId();
    final String utteranceId = UTTERANCE_ID_PREFIX + utteranceIndex;
    item.setUtteranceId(utteranceId);

    // Track latency from event received to feedback queued.
    EventId eventId = item.getEventId();
    if (eventId != null && utteranceId != null) {
      Performance.getInstance().onFeedbackQueued(eventId, utteranceId);
    }

    final UtteranceStartRunnable startAction = item.getStartAction();
    if (startAction != null) {
      addUtteranceStartAction(utteranceIndex, startAction);
    }

    final UtteranceRangeStartCallback rangeStartCallback = item.getRangeStartCallback();
    if (rangeStartCallback != null) {
      setUtteranceRangeStartCallback(utteranceIndex, rangeStartCallback);
    }

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

    final boolean shouldSilenceFragment = shouldSilenceSpeech(mCurrentFeedbackItem);
    if (shouldSilenceFragment || TextUtils.isEmpty(fragment.getText())) {
      text = null;
    } else {
      text = fragment.getText();
    }
    final Locale locale = fragment.getLocale();

    final boolean preventDeviceSleep =
        mCurrentFeedbackItem.hasFlag(FeedbackItem.FLAG_NO_DEVICE_SLEEP);

    EventId eventId = mCurrentFeedbackItem.getEventId();
    final String logText = (text == null) ? null : String.format("\"%s\"", text.toString());
    LogUtils.log(this, Log.VERBOSE, "Speaking fragment text %s for event %s", logText, eventId);

    if (text != null && mCurrentFeedbackItem.hasFlag(FeedbackItem.FLAG_FORCED_FEEDBACK)) {
      mDelegate.onSpeakingForcedFeedback();
    }

    // It's okay if the utterance is empty, the fail-over TTS will
    // immediately call the fragment completion listener. This process is
    // important for things like continuous reading.
    mFailoverTts.speak(
        text, locale, pitch, rate, params, DEFAULT_STREAM, mSpeechVolume, preventDeviceSleep);

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
    final float earconVolume = nonSpeechParams.getFloat(Utterance.KEY_METADATA_EARCON_VOLUME, 1.0f);

    if (mFeedbackController != null) {
      for (int keyResId : fragment.getEarcons()) {
        mFeedbackController.playAuditory(keyResId, earconRate, earconVolume);
      }
    }
  }

  /**
   * Produces all haptic feedback stored in a {@link FeedbackFragment}.
   *
   * @param fragment The fragment to process
   */
  private void playHapticsFromFragment(FeedbackFragment fragment) {
    if (mFeedbackController != null) {
      for (int keyResId : fragment.getHaptics()) {
        mFeedbackController.playHaptic(keyResId);
      }
    }
  }

  /** @return The utterance ID, or -1 if the ID is invalid. */
  private static int parseUtteranceId(String utteranceId) {
    // Check for bad utterance ID. This should never happen.
    if (!utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
      LogUtils.log(SpeechControllerImpl.class, Log.ERROR, "Bad utterance ID: %s", utteranceId);
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
   * Called when transitioning from an idle state to a speaking state, e.g. the queue was empty,
   * there was no current speech, and a speech item was added to the queue.
   *
   * @see #handleSpeechCompleted()
   */
  @TargetApi(Build.VERSION_CODES.N)
  private void handleSpeechStarting() {
    for (SpeechController.Observer observer : mObservers) {
      observer.onSpeechStarting();
    }

    boolean useAudioFocus = mUseAudioFocus;
    if (BuildVersionUtils.isAtLeastN()) {
      List<AudioRecordingConfiguration> recordConfigurations =
          mAudioManager.getActiveRecordingConfigurations();
      if (recordConfigurations.size() != 0) {
        useAudioFocus = false;
      }
    }

    if (useAudioFocus) {
      if (BuildVersionUtils.isAtLeastO()) {
        mAudioManager.requestAudioFocus(mAudioFocusRequest);
      } else {
        mAudioManager.requestAudioFocus(
            mAudioFocusListener, DEFAULT_STREAM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
      }
    }

    if (mIsSpeaking) {
      LogUtils.log(this, Log.ERROR, "Started speech while already speaking!");
    }

    mIsSpeaking = true;
  }

  /**
   * Called when transitioning from a speaking state to an idle state, e.g. all queued utterances
   * have been spoken and the last utterance has completed.
   *
   * @see #handleSpeechStarting()
   */
  private void handleSpeechCompleted() {
    for (SpeechController.Observer observer : mObservers) {
      observer.onSpeechCompleted();
    }

    if (mUseAudioFocus) {
      if (BuildVersionUtils.isAtLeastO()) {
        mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
      } else {
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
      }
    }

    if (!mIsSpeaking) {
      LogUtils.log(this, Log.ERROR, "Completed speech while already completed!");
    }

    mIsSpeaking = false;
  }

  /** Clears the speech queue and completes the current speech item, if any. */
  private void clearCurrentAndQueuedUtterances() {
    mFeedbackQueue.clear();
    mCurrentFragmentIterator = null;

    if (mCurrentFeedbackItem != null) {
      final String utteranceId = mCurrentFeedbackItem.getUtteranceId();
      onFragmentCompleted(utteranceId, false /* success */, true /* advance */);
      mCurrentFeedbackItem = null;
    }
  }

  private void clearUtteranceRangeStartCallbacks() {
    mUtteranceRangeStartCallbacks.clear();
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
        runUtteranceCompleteRunnable(runnable, STATUS_INTERRUPTED);
      }
    }

    // Don't call handleSpeechCompleted(), it will be called by the TTS when
    // it stops the current current utterance.
  }

  private void onFragmentStarted(String utteranceId) {
    final int utteranceIndex = SpeechControllerImpl.parseUtteranceId(utteranceId);
    onUtteranceStarted(utteranceIndex);
  }

  private void onFragmentRangeStarted(String utteranceId, int start, int end) {
    final int utteranceIndex = SpeechControllerImpl.parseUtteranceId(utteranceId);
    onUtteranceRangeStarted(utteranceIndex, start, end);
  }

  /**
   * Handles completion of a {@link FeedbackFragment}.
   *
   * <p>
   *
   * @param utteranceId The ID of the {@link FeedbackItem} the fragment belongs to.
   * @param success Whether the fragment was spoken successfully.
   * @param advance Whether to advance to the next queue item.
   */
  private void onFragmentCompleted(String utteranceId, boolean success, boolean advance) {
    final int utteranceIndex = SpeechControllerImpl.parseUtteranceId(utteranceId);
    final boolean interrupted =
        (mCurrentFeedbackItem != null)
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
   * Handles the start of an {@link Utterance}/{@link FeedbackItem}.
   *
   * @param utteranceIndex The ID of the utterance that starts.
   */
  private void onUtteranceStarted(int utteranceIndex) {
    while (!mUtteranceStartActions.isEmpty()
        && (mUtteranceStartActions.peek().utteranceIndex <= utteranceIndex)) {
      final UtteranceStartRunnable runnable = mUtteranceStartActions.poll().runnable;
      if (runnable != null) {
        if (mShouldHandleTtsCallBackInMainThread) {
          mHandler.post(
              new Runnable() {
                @Override
                public void run() {
                  runnable.run();
                }
              });
        } else {
          runnable.run();
        }
      }
    }
  }

  private void onUtteranceRangeStarted(int utteranceIndex, final int start, final int end) {
    final UtteranceRangeStartCallback callback = mUtteranceRangeStartCallbacks.get(utteranceIndex);
    if (callback != null) {
      if (mShouldHandleTtsCallBackInMainThread) {
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                callback.onUtteranceRangeStarted(start, end);
              }
            });
      } else {
        callback.onUtteranceRangeStarted(start, end);
      }
    }
  }

  /**
   * Handles the completion of an {@link Utterance}/{@link FeedbackItem}.
   *
   * @param utteranceIndex The ID of the utterance that has completed.
   * @param status One of {@link SpeechControllerImpl#STATUS_ERROR}, {@link
   *     SpeechControllerImpl#STATUS_INTERRUPTED}, or {@link SpeechControllerImpl#STATUS_SPOKEN}
   * @param interrupted {@code true} if the utterance was interrupted, {@code false} otherwise
   * @param advance Whether to advance to the next queue item.
   */
  private void onUtteranceCompleted(
      int utteranceIndex, int status, boolean interrupted, boolean advance) {
    while (!mUtteranceCompleteActions.isEmpty()
        && (mUtteranceCompleteActions.peek().utteranceIndex <= utteranceIndex)) {
      final UtteranceCompleteRunnable runnable = mUtteranceCompleteActions.poll().runnable;
      if (runnable != null) {
        runUtteranceCompleteRunnable(runnable, status);
      }
    }

    mUtteranceRangeStartCallbacks.remove(utteranceIndex);

    if (mSpeechListener != null) {
      mSpeechListener.onUtteranceCompleted(utteranceIndex, status);
    }

    if (interrupted) {
      // We finished an utterance, but we weren't expecting to see a
      // completion. This means we interrupted a previous utterance and
      // can safely ignore this callback.
      LogUtils.log(
          this,
          Log.VERBOSE,
          "Interrupted %d with %s",
          utteranceIndex,
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
      onFragmentCompleted(
          mCurrentFeedbackItem.getUtteranceId(), false /* success */, false /* advance */);
      mCurrentFeedbackItem = null;
    }

    if (wasSwitchingEngines && !mSkipNextTTSChangeAnnouncement) {
      speakCurrentEngine();
    } else if (!mFeedbackQueue.isEmpty()) {
      speakNextItem();
    }
    mSkipNextTTSChangeAnnouncement = false;
  }

  private void runUtteranceCompleteRunnable(
      @NonNull UtteranceCompleteRunnable runnable, int status) {
    CompletionRunner runner = new CompletionRunner(runnable, status);
    if (mShouldHandleTtsCallBackInMainThread) {
      mHandler.post(runner);
    } else {
      runner.run();
    }
  }

  /**
   * Removes and speaks the next {@link FeedbackItem} in the queue, interrupting the current
   * utterance if necessary.
   *
   * @return {@code false} if there are no more queued speech items.
   */
  private boolean speakNextItem() {
    final FeedbackItem previousItem = mCurrentFeedbackItem;
    final FeedbackItem nextItem = (mFeedbackQueue.isEmpty() ? null : mFeedbackQueue.removeFirst());

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

  @Override
  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {
    mDelegate.interruptAllFeedback(stopTtsSpeechCompletely);
  }

  private final Handler mHandler = new Handler();

  private final AudioManager.OnAudioFocusChangeListener mAudioFocusListener =
      new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
          LogUtils.log(
              SpeechControllerImpl.this, Log.DEBUG, "Saw audio focus change: %d", focusChange);
        }
      };

  private final AudioFocusRequest mAudioFocusRequest =
      BuildVersionUtils.isAtLeastO()
          ? new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
              .setOnAudioFocusChangeListener(mAudioFocusListener, mHandler)
              .setAudioAttributes(
                  new AudioAttributes.Builder()
                      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                      .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                      .build())
              .build()
          : null;

  /** An action that should be performed before a particular utterance index starts. */
  private static class UtteranceStartAction implements Comparable<UtteranceStartAction> {
    public UtteranceStartAction(int utteranceIndex, UtteranceStartRunnable runnable) {
      this.utteranceIndex = utteranceIndex;
      this.runnable = runnable;
    }

    /** The maximum utterance index that can be spoken before this action should be performed. */
    public int utteranceIndex;

    /** The action to execute. */
    public UtteranceStartRunnable runnable;

    @Override
    public int compareTo(@NonNull UtteranceStartAction another) {
      return (utteranceIndex - another.utteranceIndex);
    }
  }

  /** An action that should be performed after a particular utterance index completes. */
  private static class UtteranceCompleteAction implements Comparable<UtteranceCompleteAction> {
    public UtteranceCompleteAction(int utteranceIndex, UtteranceCompleteRunnable runnable) {
      this.utteranceIndex = utteranceIndex;
      this.runnable = runnable;
    }

    /** The minimum utterance index that must complete before this action should be performed. */
    public int utteranceIndex;

    /** The action to execute. */
    public UtteranceCompleteRunnable runnable;

    @Override
    public int compareTo(@NonNull UtteranceCompleteAction another) {
      return (utteranceIndex - another.utteranceIndex);
    }
  }

  private interface FeedbackItemPredicate {
    public boolean accept(FeedbackItem item);
  }

  private static class FeedbackItemDisjunctionPredicateSet implements FeedbackItemPredicate {
    private FeedbackItemPredicate mPredicate1;
    private FeedbackItemPredicate mPredicate2;

    public FeedbackItemDisjunctionPredicateSet(
        FeedbackItemPredicate predicate1, FeedbackItemPredicate predicate2) {
      mPredicate1 = predicate1;
      mPredicate2 = predicate2;
    }

    @Override
    public boolean accept(FeedbackItem item) {
      return mPredicate1.accept(item) || mPredicate2.accept(item);
    }
  }

  private static class FeedbackItemConjunctionPredicateSet implements FeedbackItemPredicate {
    private FeedbackItemPredicate mPredicate1;
    private FeedbackItemPredicate mPredicate2;

    public FeedbackItemConjunctionPredicateSet(
        FeedbackItemPredicate predicate1, FeedbackItemPredicate predicate2) {
      mPredicate1 = predicate1;
      mPredicate2 = predicate2;
    }

    @Override
    public boolean accept(FeedbackItem item) {
      return mPredicate1.accept(item) && mPredicate2.accept(item);
    }
  }

  private static class FeedbackItemInterruptiblePredicate implements FeedbackItemPredicate {
    @Override
    public boolean accept(FeedbackItem item) {
      if (item == null) {
        return false;
      }

      return item.isInterruptible();
    }
  }

  private static class FeedbackItemEqualSamplePredicate implements FeedbackItemPredicate {

    private FeedbackItem mSample;
    private boolean mEqual;

    public FeedbackItemEqualSamplePredicate(FeedbackItem sample, boolean equal) {
      mSample = sample;
      mEqual = equal;
    }

    @Override
    public boolean accept(FeedbackItem item) {
      if (mEqual) {
        return mSample == item;
      }

      return mSample != item;
    }
  }

  private static class FeedbackItemUtteranceGroupPredicate implements FeedbackItemPredicate {

    private int mUtteranceGroup;

    public FeedbackItemUtteranceGroupPredicate(int utteranceGroup) {
      mUtteranceGroup = utteranceGroup;
    }

    @Override
    public boolean accept(FeedbackItem item) {
      if (item == null) {
        return false;
      }

      return item.getUtteranceGroup() == mUtteranceGroup;
    }
  }

  private static class FeedbackItemFilter {

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
