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

import static android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_SOURCE_IS_VOLUME_CONTROL;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.Voice;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.text.style.TtsSpan;
import android.util.Range;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.braille.BrailleUnicode;
import com.google.android.accessibility.utils.output.FailoverTextToSpeech.SpeechParam;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Handles text-to-speech. */
public class SpeechControllerImpl implements SpeechController {
  /** Feedback type of speaking capital letter. */
  @IntDef({
    CAPITAL_LETTERS_TYPE_SPEAK_CAP,
    CAPITAL_LETTERS_TYPE_PITCH,
    CAPITAL_LETTERS_TYPE_SOUND_FEEDBACK,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface CapitalLetterHandlingMethod {}

  public static final int CAPITAL_LETTERS_TYPE_SPEAK_CAP = 1;
  public static final int CAPITAL_LETTERS_TYPE_PITCH = 2;
  public static final int CAPITAL_LETTERS_TYPE_SOUND_FEEDBACK = 3;

  private static final String TAG = "SpeechControllerImpl";

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
  /**
   * Assume the time between the Touch explore interaction start event & Pause gesture event is no
   * greater than this value (milli-second).
   */
  private static final long SAVED_FEEDBACK_FOR_PAUSE_TIME = 800;

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
  private ArrayList<FeedbackItem> feedbackQueue = new ArrayList<>();
  /** The list for stopping or resuming voice feedback. */
  private ArrayList<FeedbackItem> savedFeedbackQueue;
  /** Keep the feedbackSavedTime to correlate it to the Pause gesture */
  private long feedbackSavedTime;

  /** The list of recently-spoken items. */
  private final LinkedList<FeedbackItem> mFeedbackHistory = new LinkedList<>();

  /** Talkback speech deliberately saved by a caller of saveLastUtterance() */
  private @Nullable FeedbackItem savedUtterance;

  private final Context mContext;

  /** The SpeechController delegate, used to provide callbacks. */
  private final Delegate mDelegate;

  /** The audio manager, used to query ringer volume. */
  private final AudioManager mAudioManager;

  /** The feedback controller, used for playing auditory icons and vibration */
  private final FeedbackController mFeedbackController;

  /** The text-to-speech service, used for speaking. */
  private final FailoverTextToSpeech mFailoverTts;

  private final boolean removeUnnecessarySpans;

  private boolean mShouldHandleTtsCallBackInMainThread = true;

  /** Listener used for testing. */
  private SpeechControllerListener mSpeechListener;

  private final Set<SpeechController.Observer> mObservers = new HashSet<>();

  /** An iterator of fragments currently being processed */
  private @Nullable FeedbackFragmentsIterator currentFragmentIterator = null;
  /** An iterator for stopping or resuming voice feedback. */
  private @Nullable FeedbackFragmentsIterator savedFragmentIterator = null;

  /** The item current being spoken, or {@code null} if the TTS is idle. */
  private @Nullable FeedbackItem mCurrentFeedbackItem;

  /** The saved item before initializing the item current being spoken */
  private @Nullable FeedbackItem savedFeedbackItem;

  /** Whether we should request audio focus during speech. */
  private boolean mUseAudioFocus = false;

  /** The text-to-speech screen overlay. */
  private TextToSpeechOverlay mTtsOverlay;

  /** Whether the speech controller should add utterance callbacks to FullScreenReadActor */
  private boolean mInjectFullScreenReadCallbacks;

  /** The utterance completed callback for FullScreenReadActor */
  private UtteranceCompleteRunnable mFullScreenReadNextCallback;

  /**
   * The next utterance index; each utterance value will be constructed from this ever-increasing
   * index.
   */
  private int mNextUtteranceIndex = 0;

  /** Whether rate and pitch can change. */
  private boolean mUseIntonation = true;

  /** Whether reading punctuation can change. */
  private boolean mUsePunctuation = false;

  /** The feedback of capital letter (default is "Cap") */
  @CapitalLetterHandlingMethod private int capLetterFeedback = CAPITAL_LETTERS_TYPE_SPEAK_CAP;

  /** The speech pitch adjustment for capital letters. */
  private static final float CAPITAL_LETTER_PITCH_RATE = 1.8f;

  private static final float CAPITAL_LETTER_PITCH_RATE_UPPER_BOUND = 2.0f;

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

  private boolean ttsChangeAnnouncementEnabled = true;

  /** Records pause request */
  private boolean requestPause = false;

  /** Whether voice feedback is mute. */
  private boolean isMuteSpeech = false;

  /** Records whether should silence speech */
  private boolean shouldSilentSpeech = false;

  /** Records whether should silence speech */
  private boolean sourceIsVolumeControl = false;

  public SpeechControllerImpl(
      Context context, Delegate delegate, FeedbackController feedbackController) {
    this(
        context,
        delegate,
        feedbackController,
        new FailoverTextToSpeech(context),
        /* removeUnnecessarySpans= */ false);
  }

  public SpeechControllerImpl(
      Context context,
      Delegate delegate,
      FeedbackController feedbackController,
      boolean removeUnnecessarySpans) {
    this(
        context,
        delegate,
        feedbackController,
        new FailoverTextToSpeech(context),
        removeUnnecessarySpans);
  }

  @VisibleForTesting
  public SpeechControllerImpl(
      Context context,
      Delegate delegate,
      FeedbackController feedbackController,
      FailoverTextToSpeech failOverTts,
      boolean removeUnnecessarySpans) {
    mContext = context;
    mDelegate = delegate;

    mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

    mFailoverTts = failOverTts;
    mFailoverTts.addListener(
        new FailoverTextToSpeech.FailoverTtsListener() {
          @Override
          public void onBeforeUtteranceRequested(
              String utteranceId, CharSequence text, @Nullable Locale locale) {
            // Do nothing.
          }

          @Override
          public void onTtsInitialized(boolean wasSwitchingEngines, String enginePackageName) {
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
            SpeechControllerImpl.this.onFragmentCompleted(
                utteranceId, success, true /* advance */, true /* notifyObserver */);
          }
        });

    mFeedbackController = feedbackController;
    mInjectFullScreenReadCallbacks = false;
    this.removeUnnecessarySpans = removeUnnecessarySpans;
  }

  @Override
  public void setTTSChangeAnnouncementEnabled(boolean enabled) {
    ttsChangeAnnouncementEnabled = enabled;
  }

  /**
   * @return {@code true} if the speech controller is currently speaking.
   */
  @Override
  public boolean isSpeaking() {
    return mIsSpeaking;
  }

  @Override
  public void addObserver(@UnknownInitialization(Observer.class) Observer observer) {
    mObservers.add(observer);
  }

  @Override
  public void removeObserver(SpeechController.Observer observer) {
    mObservers.remove(observer);
  }

  public void setUseAudioFocus(boolean useAudioFocus) {
    mUseAudioFocus = useAudioFocus;
    if (!mUseAudioFocus) {
      LogUtils.v(TAG, "Abandon Audio Focus.");
      if (BuildVersionUtils.isAtLeastO()) {
        mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
      } else {
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
      }
    }
  }

  public void setUseIntonation(boolean useIntonation) {
    mUseIntonation = useIntonation;
  }

  public void setUsePunctuation(boolean usePunctuation) {
    mUsePunctuation = usePunctuation;
  }

  public void setCapLetterFeedback(@CapitalLetterHandlingMethod int capLetterFeedback) {
    this.capLetterFeedback = capLetterFeedback;
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

  /**
   * @return {@code true} if the speech controller has feedback queued up to speak
   */
  private boolean isSpeechQueued() {
    return !feedbackQueue.isEmpty();
  }

  @Override
  public boolean isSpeakingOrSpeechQueued() {
    return isSpeaking() || isSpeechQueued();
  }

  /** Read-only limited interface for reading speech state from parent SpeechController. */
  public class State {
    public boolean isSpeaking() {
      return SpeechControllerImpl.this.isSpeaking();
    }

    /**
     * In addition to check any feedback items are queued or spoken by {@link
     * #isSpeakingOrSpeechQueued()}, this method excludes the feedback items which are tagged with
     * {@code FeedbackItem#FLAG_SOURCE_IS_VOLUME_CONTROL}. That is used to identify the items
     * generated by Volume control UI.
     */
    public boolean isSpeakingOrQueuedAndNotSourceIsVolumeAnnouncment() {
      return !sourceIsVolumeControl && SpeechControllerImpl.this.isSpeakingOrSpeechQueued();
    }

    public @Nullable Set<Voice> getVoices() {
      return SpeechControllerImpl.this.getVoices();
    }
  }

  /** Read-only interface for reading speech state. */
  public final State state = new State();

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

  public @Nullable Set<Voice> getVoices() {
    try {
      if (mFailoverTts.isReady()) {
        return mFailoverTts.getEngineInstance().getVoices();
      }

      LogUtils.w(TAG, "Attempted to get voices before TTS was initialized.");
      return null;
    } catch (Exception e) {
      LogUtils.e(TAG, "TTS client crashed while generating language menu items");
      e.printStackTrace();
      return null;
    }
  }

  /** Repeats the last spoken utterance. */
  public boolean repeatLastUtterance() {
    return repeatUtterance(getLastUtterance());
  }

  /** Copies saved talkback speech to clipboard. */
  public boolean copySavedUtteranceToClipboard(EventId eventId) {
    return copyUtteranceToClipboard(savedUtterance, eventId);
  }

  /** Copies the last phrase spoken by TalkBack to clipboard */
  public boolean copyLastUtteranceToClipboard(EventId eventId) {
    return copyUtteranceToClipboard(getLastUtterance(), eventId);
  }

  /** Copies a phrase spoken by TalkBack to clipboard */
  public boolean copyUtteranceToClipboard(FeedbackItem item, EventId eventId) {
    if (item == null) {
      return false;
    }

    CharSequence copyableText = SpannableUtils.getCopyableText(item.getAggregateText());

    if (TextUtils.isEmpty(copyableText)) {
      return false;
    }

    final ClipboardManager clipboard =
        (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText(null, copyableText);
    clipboard.setPrimaryClip(clip);

    // Verify that we actually have the utterance on the clipboard
    if (FeatureSupport.supportReadClipboard()) {
      clip = clipboard.getPrimaryClip();
    }
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

  /** Saves most recent talkback speech. */
  public void saveLastUtterance() {
    savedUtterance = getLastUtterance();
  }

  /** Returns {@code true} if SpeechControllerImpl should interrupt speech */
  public boolean getShouldSilentSpeech() {
    return shouldSilentSpeech;
  }

  public void setSilenceSpeech(boolean shouldSlientSpeech) {
    this.shouldSilentSpeech = shouldSlientSpeech;
  }

  /** Returns the last spoken utterance. */
  public @Nullable FeedbackItem getLastUtterance() {
    if (mFeedbackHistory.isEmpty()) {
      return null;
    }
    return mFeedbackHistory.getLast();
  }

  /** Re-speaks saved talkback speech. */
  public boolean repeatSavedUtterance() {
    return repeatUtterance(savedUtterance);
  }

  /** Repeats the provided utterance. */
  public boolean repeatUtterance(@Nullable FeedbackItem item) {
    if (item == null) {
      return false;
    }
    /*
     * We copy and speak the last item with history enabled.
     * This guarantees that it is consistently the last item in the history.
     * Otherwise the window title will be spoken when the context menu closes
     * and if the user invokes this action again, it will repeat the window title.
     */
    final FeedbackItem newItem = new FeedbackItem(item);
    newItem.addFlag(
        FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);
    speak(
        /* item= */ newItem,
        /* queueMode= */ QUEUE_MODE_BIT_FLUSH_ALL,
        /* startAction= */ null,
        /* rangeStartCallback= */ null,
        /* completedAction= */ null);
    return true;
  }

  /** Spells the last spoken utterance. */
  public boolean spellLastUtterance() {
    FeedbackItem last = getLastUtterance();
    return (last == null) ? false : spellUtterance(last);
  }

  /** Announces the spelling of saved talkback speech. */
  public boolean spellSavedUtterance() {
    return (savedUtterance == null) ? false : spellUtterance(savedUtterance);
  }

  /** Spells the given utterance. */
  public boolean spellUtterance(FeedbackItem utterance) {
    CharSequence text = utterance.getAggregateText();
    /*
     * We spell the utterance then append a copy of the original utterance to the history.
     * This guarantees that it is consistently the last item in the history.
     * Otherwise the window title will be spoken when the context menu closes
     * and if the user invokes this action again, it will spell the window title.
     */
    boolean result = spellUtterance(text);
    final FeedbackItem newUtterance = new FeedbackItem(utterance);
    mFeedbackHistory.addLast(newUtterance);
    return result;
  }

  /** Spells the text. */
  public boolean spellUtterance(CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return false;
    }

    final SpannableStringBuilder builder = new SpannableStringBuilder();
    for (int i = 0; i < text.length(); i++) {
      final String cleanedChar = SpeechCleanupUtils.getCleanValueFor(mContext, text.charAt(i));

      StringBuilderUtils.appendWithSeparator(builder, cleanedChar);
    }
    SpeakOptions options = SpeakOptions.create();
    options.mQueueMode = QUEUE_MODE_BIT_INTERRUPT | QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH;
    options.mFlags =
        FeedbackItem.FLAG_NO_HISTORY
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE;
    options.mUtteranceGroup = UTTERANCE_GROUP_DEFAULT;
    speak(builder, /* eventId= */ null, options);
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
        0,
        FeedbackItem.FLAG_NO_HISTORY
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE,
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
      CharSequence text,
      int queueMode,
      int flags,
      @Nullable Bundle speechParams,
      @Nullable EventId eventId) {
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
      @Nullable Bundle speechParams,
      @Nullable UtteranceStartRunnable startingAction,
      @Nullable UtteranceRangeStartCallback rangeStartCallback,
      @Nullable UtteranceCompleteRunnable completedAction,
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
      @Nullable Set<Integer> earcons,
      @Nullable Set<Integer> haptics,
      int queueMode,
      int flags,
      int uttranceGroup,
      @Nullable Bundle speechParams,
      @Nullable Bundle nonSpeechParams,
      @Nullable EventId eventId) {
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
  public void speak(CharSequence text, @Nullable EventId eventId, @Nullable SpeakOptions options) {
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

  /** Toggle the voice feedback flag with additional state change notification. */
  @Override
  public void toggleVoiceFeedback() {
    if (isMuteSpeech) {
      // Un-mute the speech, then speak the state.
      isMuteSpeech = false;
      speak(
          mContext.getString(R.string.function_on),
          QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH,
          /* flags= */ 0,
          /* speechParams= */ null,
          EVENT_ID_UNTRACKED);
    } else {
      // Speak the state before mute the speech feedback.
      speak(
          mContext.getString(R.string.function_off),
          QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH,
          /* flags= */ 0,
          /* speechParams= */ null,
          EVENT_ID_UNTRACKED);
      isMuteSpeech = true;
    }
  }

  @Override
  public boolean isMute() {
    return isMuteSpeech;
  }

  @Override
  public void setMute(boolean mute) {
    isMuteSpeech = mute;
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
   *       <li>{@link #QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH}
   *       <li>{@link #QUEUE_MODE_CAN_IGNORE_INTERRUPTS}
   *       <li>{@link #QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS}
   *       <li>{@link #QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH}
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
      @Nullable Set<Integer> earcons,
      @Nullable Set<Integer> haptics,
      int queueMode,
      int flags,
      int utteranceGroup,
      @Nullable Bundle speechParams,
      @Nullable Bundle nonSpeechParams,
      @Nullable UtteranceStartRunnable startAction,
      @Nullable UtteranceRangeStartCallback rangeStartCallback,
      @Nullable UtteranceCompleteRunnable completedAction,
      @Nullable EventId eventId) {

    if (isMuteSpeech) {
      LogUtils.v(TAG, "Voice feedback is off.");
      return;
    }

    if (TextUtils.isEmpty(text)
        && (earcons == null || earcons.isEmpty())
        && (haptics == null || haptics.isEmpty())) {
      // don't process request with empty feedback
      if ((flags & FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING) != 0) {
        tryNotifyFullScreenReaderCallback();
      }
      return;
    }

    text = replaceSpanByContentDescription(text);
    text = replaceBrailleSymbolByDescription(mContext, text);
    final FeedbackItem pendingItem =
        FeedbackProcessingUtils.generateFeedbackItemFromInput(
            mContext,
            text,
            mUsePunctuation,
            removeUnnecessarySpans,
            earcons,
            haptics,
            flags,
            utteranceGroup,
            speechParams,
            nonSpeechParams,
            eventId);

    makeSpeakablePunctuation(pendingItem);
    speak(pendingItem, queueMode, startAction, rangeStartCallback, completedAction);
  }

  private void makeSpeakablePunctuation(FeedbackItem item) {
    if (!mUsePunctuation) {
      return;
    }
    for (FeedbackFragment originalFragment : item.getFragments()) {
      CharSequence sourceText = originalFragment.getText();
      if (TextUtils.isEmpty(sourceText)) {
        continue;
      }
      SpannableStringBuilder builder = new SpannableStringBuilder(sourceText);
      // An exception list to keep the range of each span within the source text. It could help to
      // check any conflict spans for the scanned punctuation.
      List<Range<Integer>> rangeList = new ArrayList<>();
      // Adding the exception cases of reading punctuation.
      // 1. Identifier Span
      // 2. TtsSpan
      // 3. Other span which would conflict to the punctuation should be added into the list
      /* Don't use java.util.stream.Stream in accessibility/utils until it's supported.
      Stream.of(
              ((Spanned) sourceText)
                  .getSpans(0, sourceText.length(), SpannableUtils.IdentifierSpan.class),
              ((Spanned) sourceText).getSpans(0, sourceText.length(), TtsSpan.class))
          .flatMap(Stream::of)
          .forEach(
              s ->
                  rangeList.add(
                      new Range<Integer>(builder.getSpanStart(s), builder.getSpanEnd(s))));
       */
      List<Object> spans = new ArrayList<>();
      Collections.addAll(
          spans,
          ((Spanned) sourceText)
              .getSpans(0, sourceText.length(), SpannableUtils.IdentifierSpan.class));
      Collections.addAll(
          spans, ((Spanned) sourceText).getSpans(0, sourceText.length(), TtsSpan.class));
      for (Object span : spans) {
        rangeList.add(new Range<Integer>(builder.getSpanStart(span), builder.getSpanEnd(span)));
      }

      boolean fragmentChanged = false;
      Locale locale = originalFragment.getLocale();
      Context configurationContext = mContext;
      if (locale != null) {
        // When the fragment's locale is specified, we need to get the punctuation symbol according
        // the this locale, instead of the system's one.
        Configuration configuration = new Configuration(mContext.getResources().getConfiguration());
        configuration.setLocale(locale);
        configurationContext = mContext.createConfigurationContext(configuration);
      }
      // Traverse the entire text and locate for each symbol, which has a punctuation name and is
      // not conflict with existing exception list, then add a tts span with the punctuation name.
      for (int i = 0; i < sourceText.length(); i++) {
        char ch = sourceText.charAt(i);
        @Nullable String cleanValue = SpeechCleanupUtils.characterToName(configurationContext, ch);
        if (cleanValue != null) {
          /* Don't use java.util.stream.Stream in accessibility/utils until it's supported.
          int x = i;
          Optional<Range<Integer>> match =
              rangeList.stream().filter(range -> range.contains(x)).findFirst();
          if (!match.isPresent()) {
            fragmentChanged = true;
            builder.setSpan(
                new TtsSpan.TextBuilder(cleanValue).build(), i, i + 1, SPAN_INCLUSIVE_EXCLUSIVE);
          }
           */
          fragmentChanged |= checkAndInsertSpanForPunctuation(builder, rangeList, i, cleanValue);
        }
      }
      if (fragmentChanged) {
        originalFragment.setText(builder);
      }
    }
  }

  /**
   * Verify if the location of punctuation has conflict to any exception spans.
   *
   * @return true if no conflict and adds a tts span for the mapped term.
   */
  private boolean checkAndInsertSpanForPunctuation(
      SpannableStringBuilder builder,
      List<Range<Integer>> rangeList,
      int index,
      @Nullable String cleanValue) {
    if (cleanValue == null) {
      return false;
    }
    for (Range<Integer> range : rangeList) {
      if (range.contains(index)) {
        return false;
      }
    }
    builder.setSpan(
        new TtsSpan.TextBuilder(cleanValue).build(), index, index + 1, SPAN_INCLUSIVE_EXCLUSIVE);
    return true;
  }

  /** Find and replace any ReplacementSpan, whose content description is not null, with TTS span. */
  private static CharSequence replaceSpanByContentDescription(CharSequence text) {
    if (!FeatureSupport.supportContentDescriptionInReplacementSpan()
        || TextUtils.isEmpty(text)
        || !(text instanceof Spanned)) {
      return text;
    }
    ReplacementSpan[] replacementSpans =
        ((Spanned) text).getSpans(0, text.length(), ReplacementSpan.class);
    if (replacementSpans == null || replacementSpans.length == 0) {
      return text;
    }
    SpannableStringBuilder spannable = new SpannableStringBuilder(text);
    for (ReplacementSpan span : replacementSpans) {
      CharSequence replacementText = span.getContentDescription();
      if (replacementText == null) {
        continue;
      }
      LogUtils.v(TAG, "Replace ReplacementSpan by content description: %s", replacementText);
      int spanToReplaceStart = spannable.getSpanStart(span);
      int spanToReplaceEnd = spannable.getSpanEnd(span);
      int spanToReplaceFlags = spannable.getSpanFlags(span);
      spannable.removeSpan(span);
      spannable.setSpan(
          new TtsSpan.TextBuilder(replacementText.toString()).build(),
          spanToReplaceStart,
          spanToReplaceEnd,
          spanToReplaceFlags);
    }
    return spannable;
  }

  private static CharSequence replaceBrailleSymbolByDescription(
      Context context, CharSequence text) {
    SpannableStringBuilder spannable = new SpannableStringBuilder(text);
    for (int index = 0; index < spannable.length(); index++) {
      char ch = spannable.charAt(index);
      if (BrailleUnicode.isBraille(ch)) {
        String dots = BrailleUnicode.toDotNumbersString(ch);
        String dotsPattern =
            context.getResources().getQuantityString(R.plurals.dots, dots.length(), dots);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String braillePattern = context.getString(R.string.symbol_braille, dotsPattern);
        sb.append(braillePattern);
        sb.append(" ");
        sb.setSpan(
            new TtsSpan.VerbatimBuilder().setVerbatim(dots).build(),
            braillePattern.indexOf(dots),
            braillePattern.indexOf(dots) + dots.length(),
            SPAN_INCLUSIVE_EXCLUSIVE);
        spannable.replace(index, index + 1, sb);
      }
    }
    return spannable;
  }

  private void speak(
      FeedbackItem item,
      int queueMode,
      @Nullable UtteranceStartRunnable startAction,
      @Nullable UtteranceRangeStartCallback rangeStartCallback,
      @Nullable UtteranceCompleteRunnable completedAction) {

    // If this FeedbackItem is flagged as NO_SPEECH, ignore speech and
    // immediately process earcons and haptics without disrupting the speech
    // queue.
    // TODO: Consider refactoring non-speech feedback out of
    // this class entirely.
    if (item.hasFlag(FeedbackItem.FLAG_NO_SPEECH)) {
      for (FeedbackFragment fragment : item.getFragments()) {
        playEarconsFromFragment(fragment, item.getEventId());
        playHapticsFromFragment(fragment, item.getEventId());
      }
      if (item.hasFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING)) {
        tryNotifyFullScreenReaderCallback();
      }
      return;
    }

    int[] expectedUtteranceId = new int[1];
    expectedUtteranceId[0] = -1;
    if (item.hasFlag(FeedbackItem.FLAG_SKIP_DUPLICATE)
        && hasItemOnQueueOrSpeaking(item, expectedUtteranceId)) {
      Iterator<UtteranceCompleteAction> iterator = mUtteranceCompleteActions.iterator();
      while (iterator.hasNext()) {
        UtteranceCompleteAction utteranceCompleteAction = iterator.next();
        if (utteranceCompleteAction.utteranceIndex == peekNextUtteranceId()) {
          if (expectedUtteranceId[0] != -1) {
            utteranceCompleteAction.utteranceIndex = expectedUtteranceId[0];
          } else {
            iterator.remove();
          }
        }
      }
      return;
    }

    item.setFlushGlobalTtsQueue(
        SpeechController.hasQueueModeFlagSet(queueMode, QUEUE_MODE_BIT_INTERRUPT));
    item.setUninterruptible(
        SpeechController.hasQueueModeFlagSet(
            queueMode, QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH));
    item.setCanIgnoreInterrupts(
        SpeechController.hasQueueModeFlagSet(queueMode, QUEUE_MODE_BIT_CAN_IGNORE_INTERRUPTS));
    item.setStartAction(startAction);
    item.setRangeStartCallback(rangeStartCallback);
    item.setCompletedAction(completedAction);

    boolean currentFeedbackInterrupted = false;
    if (shouldClearQueue(item, queueMode)) {
      FeedbackItemFilter filter = getFeedbackItemFilter(item, queueMode);
      // Call onUtteranceComplete on each queue item to be cleared.
      ListIterator<FeedbackItem> iterator = feedbackQueue.listIterator(0);
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

    Performance.getInstance().onFeedbackComposed(item.getEventId());
    feedbackQueue.add(item);
    if (mSpeechListener != null) {
      mSpeechListener.onUtteranceQueued(item);
    }

    // If TTS isn't ready, this should be the only item in the queue.
    if (!mFailoverTts.isReady()) {
      LogUtils.e(
          TAG, "TTS is not ready. Attempted to speak before TTS was initialized. Item: " + item);
      return;
    }

    if ((mCurrentFeedbackItem == null) || currentFeedbackInterrupted) {
      currentFragmentIterator = null;
      speakNextItem();
    } else {
      LogUtils.v(
          TAG, "Queued speech item, waiting for \"%s\"", mCurrentFeedbackItem.getUtteranceId());
    }
  }

  private void tryNotifyFullScreenReaderCallback() {
    if (mInjectFullScreenReadCallbacks && mFullScreenReadNextCallback != null) {
      if (mShouldHandleTtsCallBackInMainThread) {
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                if (mFullScreenReadNextCallback != null) {
                  mFullScreenReadNextCallback.run(SpeechController.STATUS_NOT_SPOKEN);
                }
              }
            });
      } else {
        mFullScreenReadNextCallback.run(SpeechController.STATUS_NOT_SPOKEN);
      }
    }
  }

  private boolean shouldClearQueue(FeedbackItem item, int queueMode) {
    // QUEUE_MODE_BIT_INTERRUPT, QUEUE_MODE_BIT_FLUSH_ALL, QUEUE_MODE_BIT_CAN_IGNORE_INTERRUPTS and
    // QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH will clear the queue.
    if (SpeechController.hasQueueModeFlagSet(queueMode, QUEUE_MODE_BIT_INTERRUPT)) {
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
    if (SpeechController.hasQueueModeFlagSet(queueMode, QUEUE_MODE_BIT_INTERRUPT)) {
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

  /**
   * @param item : The item which is about to read.
   * @param expectedUtteranceId : When duplicate happens, this will return the expected utterance
   *     index, or keep unchanged when the associated hint should be dropped.
   * @return whether there is any speech item reading or pending to ready which is duplicate to the
   *     new item.
   */
  private boolean hasItemOnQueueOrSpeaking(FeedbackItem item, int[] expectedUtteranceId) {
    int accumulatedUtterance;
    if (mCurrentFeedbackItem == null) {
      accumulatedUtterance = peekNextUtteranceId();
    } else {
      String utteranceId = mCurrentFeedbackItem.getUtteranceId();
      if (utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
        accumulatedUtterance =
            Integer.parseInt(utteranceId.substring(UTTERANCE_ID_PREFIX.length()));
      } else {
        LogUtils.e(TAG, "Bad utterance ID: %s", utteranceId);
        return false;
      }
    }
    if (item == null) {
      return false;
    }

    if (feedbackTextEquals(item, mCurrentFeedbackItem)) {
      expectedUtteranceId[0] = accumulatedUtterance;
      return true;
    }

    for (FeedbackItem queuedItem : feedbackQueue) {
      accumulatedUtterance++;
      if (feedbackTextEquals(item, queuedItem)) {
        expectedUtteranceId[0] = accumulatedUtterance;
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
  private boolean feedbackTextEquals(@Nullable FeedbackItem item1, @Nullable FeedbackItem item2) {
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

  @Override
  public void addUtteranceCompleteAction(
      int index, @UtteranceGroup int utteranceGroup, UtteranceCompleteRunnable runnable) {
    final UtteranceCompleteAction action =
        new UtteranceCompleteAction(index, utteranceGroup, runnable);
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
  public void clearHintUtteranceCompleteAction() {
    final Iterator<UtteranceCompleteAction> iterator = mUtteranceCompleteActions.iterator();

    while (iterator.hasNext()) {
      final UtteranceCompleteAction action = iterator.next();
      if (action.utteranceGroup == UTTERANCE_GROUP_CONTENT_HINTS) {
        iterator.remove();
      }
    }
  }

  @Override
  public void interrupt(boolean stopTtsSpeechCompletely) {
    interrupt(stopTtsSpeechCompletely, true /* notifyObserver */);
  }

  @Override
  public void interrupt(boolean stopTtsSpeechCompletely, boolean notifyObserver) {
    interrupt(
        stopTtsSpeechCompletely, notifyObserver, true /* interruptItemsThatCanIgnoreInterrupts */);
  }

  @Override
  public void interrupt(
      boolean stopTtsSpeechCompletely,
      boolean notifyObserver,
      boolean interruptItemsThatCanIgnoreInterrupts) {
    if (!interruptItemsThatCanIgnoreInterrupts) {
      if (!clearCurrentAndQueuedUtterancesThatDontIgnoreInterrupts(notifyObserver)) {
        // If there are speech items that are not removed from the speech queue, or the currently
        // speaking speech should not be interrupted, then TTS should not be stopped.
        return;
      }
    } else {
      // Clear all current and queued utterances.
      clearCurrentAndQueuedUtterances(notifyObserver);
    }

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

  /** Check the last request status and then stop or resume utterance. */
  public void pauseOrResumeUtterance() {
    if (requestPause) {
      resume();
    } else {
      pause();
    }
  }

  @Override
  public void pause() {
    long delta = SystemClock.uptimeMillis() - feedbackSavedTime;
    // The savedFeedbackQueue was copied for the last interrupted speech content. If the delta
    // time between this pause method invoked and the last speech interruption is too long, we can
    // determine the saved feedback is outdated.
    if (savedFeedbackQueue != null && !requestPause && delta <= SAVED_FEEDBACK_FOR_PAUSE_TIME) {
      mCurrentFeedbackItem = null;
      requestPause = true;
      mFailoverTts.stopFromTalkBack();
    }
  }

  @Override
  public void resume() {
    if (savedFeedbackQueue != null && requestPause) {
      loadSavedFeedbackInfo();
      resetSavedFeedbackInfo();
      handleSpeechStarting();
      processNextFragmentInternal();
    }
    requestPause = false;
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
   * <p>Silences speech in the following cases if not forced feedback:
   *
   * <ul>
   *   <li>Speech recognition is active and the user is not using a headset
   *   <li>Mic is recording and the user is not using a headset
   *   <li>Audio is playing
   *   <li>Phone call is active
   * </ul>
   */
  @SuppressWarnings("deprecation")
  private boolean shouldSilenceSpeech(FeedbackItem item) {
    // TODO: remove the legacy flag when all items are updated with the new flags
    if (item.hasFlag(FeedbackItem.FLAG_FORCE_FEEDBACK)) {
      return false;
    }
    if (shouldSilentSpeech) {
      LogUtils.v(TAG, "SilenceSpeechByFlag");
      return true;
    }
    if (shouldSilenceSpeechWhenSsbActive(item)) {
      LogUtils.v(TAG, "SilenceSpeechWhenSsbActive");
      return true;
    }
    if (shouldSilenceSpeechWhenMicrophoneActive(item)) {
      LogUtils.v(TAG, "SilenceSpeechWhenMicrophoneActive");
      return true;
    }
    if (shouldSilenceSpeechWhenAudioPlaybackActive(item)) {
      LogUtils.v(TAG, "SilenceSpeechWhenAudioPlaybackActive");
      return true;
    }
    if (shouldSilenceSpeechWhenPhoneCallActive(item)) {
      LogUtils.v(TAG, "SilenceSpeechWhenPhoneCallActive");
      return true;
    }
    return false;
  }

  /** Returns {@code true} if speech should be silenced if audio is playing. */
  @SuppressWarnings("deprecation")
  private boolean shouldSilenceSpeechWhenAudioPlaybackActive(FeedbackItem item) {
    return !item.hasFlag(FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE)
        && mDelegate.isAudioPlaybackActive();
  }

  /** Returns {@code true} if speech should be silenced if microphone is recording. */
  @SuppressWarnings("deprecation")
  private boolean shouldSilenceSpeechWhenMicrophoneActive(FeedbackItem item) {
    return !item.hasFlag(FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE)
        && mDelegate.isMicrophoneActiveAndHeadphoneOff();
  }

  /** Returns {@code true} if speech should be silenced during speech recognition/dictation. */
  @SuppressWarnings("deprecation")
  private boolean shouldSilenceSpeechWhenSsbActive(FeedbackItem item) {
    return !item.hasFlag(FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE)
        && mDelegate.isSsbActiveAndHeadphoneOff();
  }

  /** Returns {@code true} if speech should be silenced during phone call. */
  @SuppressWarnings("deprecation")
  private boolean shouldSilenceSpeechWhenPhoneCallActive(FeedbackItem item) {
    return !item.hasFlag(FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE)
        && mDelegate.isPhoneCallActive();
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
    currentFragmentIterator.setFeedBackItemUtteranceId(utteranceId);

    final UtteranceStartRunnable startAction = item.getStartAction();
    if (startAction != null) {
      addUtteranceStartAction(utteranceIndex, startAction);
    }

    final UtteranceRangeStartCallback rangeStartCallback = item.getRangeStartCallback();
    if (rangeStartCallback != null) {
      setUtteranceRangeStartCallback(utteranceIndex, rangeStartCallback);
    }

    final UtteranceCompleteRunnable completedAction = item.getCompletedAction();
    final int utteranceGroup = item.getUtteranceGroup();
    if (completedAction != null) {
      addUtteranceCompleteAction(utteranceIndex, utteranceGroup, completedAction);
    }

    if (mInjectFullScreenReadCallbacks
        && item.hasFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING)) {
      addUtteranceCompleteAction(utteranceIndex, utteranceGroup, mFullScreenReadNextCallback);
    }

    if ((item != null) && !item.hasFlag(FeedbackItem.FLAG_NO_HISTORY)) {
      while (mFeedbackHistory.size() >= MAX_HISTORY_ITEMS) {
        mFeedbackHistory.remove(mFeedbackHistory.peek());
      }
      mFeedbackHistory.addLast(item);
    }

    if (mSpeechListener != null) {
      mSpeechListener.onUtteranceStarted(item);
    }

    processNextFragmentInternal();
  }

  private boolean processNextFragmentInternal() {
    if (currentFragmentIterator == null || !currentFragmentIterator.hasNext()) {
      return false;
    }
    // Use local variable because mCurrentFeedbackItem could be null by race condition.
    // See b/307482287 for more details.
    FeedbackItem feedbackItem = mCurrentFeedbackItem;
    if (feedbackItem == null) {
      // TODO: Probably due to asynchronous overlap of onFragmentCompleted() calling
      // processNextFragmentInternal(), and clearCurrentAndQueuedUtterances() setting
      // mCurrentFeedbackItem to null.
      return false;
    }

    FeedbackFragment fragment = currentFragmentIterator.next();
    EventId eventId = feedbackItem.getEventId();
    playEarconsFromFragment(fragment, eventId);
    playHapticsFromFragment(fragment, eventId);

    // Reuse the global instance of speech parameters.
    final HashMap<String, String> params = mSpeechParametersMap;
    params.clear();

    // Add all custom speech parameters.
    final Bundle speechParams = fragment.getSpeechParams();
    for (String key : speechParams.keySet()) {
      params.put(key, String.valueOf(speechParams.get(key)));
    }

    // Utterance ID, stream, and volume override item params.
    params.put(Engine.KEY_PARAM_UTTERANCE_ID, feedbackItem.getUtteranceId());
    params.put(Engine.KEY_PARAM_STREAM, String.valueOf(DEFAULT_STREAM));
    params.put(Engine.KEY_PARAM_VOLUME, String.valueOf(mSpeechVolume));

    float pitch =
        mSpeechPitch * (mUseIntonation ? parseFloatParam(params, SpeechParam.PITCH, 1) : 1);
    final float rate =
        mSpeechRate * (mUseIntonation ? parseFloatParam(params, SpeechParam.RATE, 1) : 1);
    CharSequence text;

    final boolean shouldSilenceFragment = shouldSilenceSpeech(feedbackItem);
    if (shouldSilenceFragment || TextUtils.isEmpty(fragment.getText())) {
      text = null;
    } else {
      text = fragment.getText();
    }
    final Locale locale = fragment.getLocale();

    final boolean preventDeviceSleep = feedbackItem.hasFlag(FeedbackItem.FLAG_NO_DEVICE_SLEEP);

    // for capital letter
    if (text != null && text.length() == 1 && Character.isUpperCase(text.charAt(0))) {
      switch (capLetterFeedback) {
        case CAPITAL_LETTERS_TYPE_SPEAK_CAP:
          // To resolve the limitation of [length == 1]. "Say capital" is handled by compositor
          // variable.
          break;
        case CAPITAL_LETTERS_TYPE_PITCH:
          pitch = min(pitch * CAPITAL_LETTER_PITCH_RATE, CAPITAL_LETTER_PITCH_RATE_UPPER_BOUND);
          break;
        case CAPITAL_LETTERS_TYPE_SOUND_FEEDBACK:
          // TODO: The raw resource of sound feedback is required for capital letter.
          mFeedbackController.playAuditory(R.raw.window_state, eventId);
          break;
        default: // fall out
      }
    }

    final String logText = (text == null) ? null : String.format("\"%s\"", text.toString());
    LogUtils.v(
        TAG,
        "Speaking fragment text %s with spans %s for event %s",
        logText,
        SpannableUtils.spansToStringForLogging(text),
        eventId);

    if (text != null && feedbackItem.hasFlag(FeedbackItem.FLAG_FORCE_FEEDBACK)) {
      mDelegate.onSpeakingForcedFeedback();
    }

    sourceIsVolumeControl = feedbackItem.hasFlag(FLAG_SOURCE_IS_VOLUME_CONTROL);
    // It's okay if the utterance is empty, the fail-over TTS will
    // immediately call the fragment completion listener. This process is
    // important for things like continuous reading.
    mFailoverTts.speak(
        text,
        locale,
        pitch,
        rate,
        params,
        DEFAULT_STREAM,
        mSpeechVolume,
        preventDeviceSleep,
        feedbackItem.shouldFlushGlobalTtsQueue(),
        eventId);

    if (mTtsOverlay != null) {
      mTtsOverlay.displayText(text);
    }

    return true;
  }

  /**
   * Plays all earcons stored in a {@link FeedbackFragment}.
   *
   * @param fragment The fragment to process
   */
  private void playEarconsFromFragment(FeedbackFragment fragment, @Nullable EventId eventId) {
    final Bundle nonSpeechParams = fragment.getNonSpeechParams();
    final float earconRate = nonSpeechParams.getFloat(Utterance.KEY_METADATA_EARCON_RATE, 1.0f);
    final float earconVolume = nonSpeechParams.getFloat(Utterance.KEY_METADATA_EARCON_VOLUME, 1.0f);

    if (mFeedbackController != null) {
      for (int keyResId : fragment.getEarcons()) {
        mFeedbackController.playAuditory(keyResId, earconRate, earconVolume, eventId);
      }
    }
  }

  /**
   * Produces all haptic feedback stored in a {@link FeedbackFragment}.
   *
   * @param fragment The fragment to process
   */
  private void playHapticsFromFragment(FeedbackFragment fragment, @Nullable EventId eventId) {
    if (mFeedbackController != null) {
      for (int keyResId : fragment.getHaptics()) {
        mFeedbackController.playHaptic(keyResId, eventId);
      }
    }
  }

  /**
   * @return The utterance ID, or -1 if the ID is invalid.
   */
  private static int parseUtteranceId(String utteranceId) {
    // Check for bad utterance ID. This should never happen.
    if (!utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
      LogUtils.e(TAG, "Bad utterance ID: %s", utteranceId);
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
   * there was no current speech, a speech item was added to the queue, and {@link #resume()} is
   * called when status is {@link #STATUS_PAUSE} .
   *
   * @see #handleSpeechCompleted(int status)
   */
  private void handleSpeechStarting() {
    for (SpeechController.Observer observer : mObservers) {
      observer.onSpeechStarting();
    }

    boolean useAudioFocus = mUseAudioFocus;
    List<AudioRecordingConfiguration> recordConfigurations =
        mAudioManager.getActiveRecordingConfigurations();
    if (!recordConfigurations.isEmpty()) {
      useAudioFocus = false;
    }

    if (useAudioFocus) {
      LogUtils.v(TAG, "Request Audio Focus.");
      if (BuildVersionUtils.isAtLeastO()) {
        mAudioManager.requestAudioFocus(mAudioFocusRequest);
      } else {
        mAudioManager.requestAudioFocus(
            mAudioFocusListener, DEFAULT_STREAM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
      }
    }

    if (mIsSpeaking) {
      LogUtils.e(TAG, "Started speech while already speaking!");
    }

    mIsSpeaking = true;
  }

  /**
   * Called when transitioning from a speaking state to an idle/pause state, e.g. all queued
   * utterances have been spoken, the last utterance has completed and {@link #pause()} is called
   * when {@link #isSpeaking()} is {@code true}
   *
   * @see #handleSpeechStarting()
   */
  private void handleSpeechCompleted(int status) {
    for (SpeechController.Observer observer : mObservers) {
      if (status == STATUS_PAUSE) {
        observer.onSpeechPaused();

      } else if (status != STATUS_ERROR_DONT_NOTIFY_OBSERVER) {
        observer.onSpeechCompleted();
      }
    }

    if (mUseAudioFocus) {
      if (BuildVersionUtils.isAtLeastO()) {
        mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
      } else {
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
      }
    }

    if (!mIsSpeaking) {
      LogUtils.e(TAG, "Completed speech while already completed!");
    }

    mIsSpeaking = false;
  }

  /**
   * Clears the speech queue and completes the current speech item, if any.
   *
   * @param notifyObserver Whether to notify the observer about the completion of the current {@link
   *     FeedbackItem}
   */
  private void clearCurrentAndQueuedUtterances(boolean notifyObserver) {
    // We should save some of the feedback information to provide Pause / Resume feedback.
    // Because clearCurrentAndQueuedUtterances() clears all feedback
    // before recognizing the single tap with two fingers gesture.
    saveCurrentFeedbackInfo();

    feedbackQueue.clear();
    currentFragmentIterator = null;

    if (mCurrentFeedbackItem != null) {
      final String utteranceId = mCurrentFeedbackItem.getUtteranceId();
      onFragmentCompleted(utteranceId, false /* success */, true /* advance */, notifyObserver);
      mCurrentFeedbackItem = null;
    }
  }

  /**
   * Removes speech items from the speech queue and completes the current speech item if the speech
   * items are in queue modes other than QUEUE_MODE_CAN_IGNORE_INTERRUPTS or
   * QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS.
   *
   * @param notifyObserver Whether to notify the observer about the completion of the current {@link
   *     FeedbackItem}
   * @return {@code true} if speech has been cleared from the speech queue and the currently
   *     speaking speech should be interrupted
   */
  private boolean clearCurrentAndQueuedUtterancesThatDontIgnoreInterrupts(boolean notifyObserver) {
    ListIterator<FeedbackItem> iterator = feedbackQueue.listIterator(0);
    while (iterator.hasNext()) {
      FeedbackItem currentItem = iterator.next();
      if (!currentItem.canIgnoreInterrupts()) {
        iterator.remove();
        notifyItemInterrupted(currentItem);
      }
    }

    boolean currentFeedbackInterrupted = false;
    if (mCurrentFeedbackItem != null) {
      if (!mCurrentFeedbackItem.canIgnoreInterrupts()) {
        notifyItemInterrupted(mCurrentFeedbackItem);
        currentFeedbackInterrupted = true;
      }
    }

    if ((mCurrentFeedbackItem == null) || currentFeedbackInterrupted) {
      currentFragmentIterator = null;
      if (!feedbackQueue.isEmpty()) {
        speakNextItem();
      } else if (mCurrentFeedbackItem != null) {
        final String utteranceId = mCurrentFeedbackItem.getUtteranceId();
        onFragmentCompleted(utteranceId, false /* success */, true /* advance */, notifyObserver);
        mCurrentFeedbackItem = null;
        return true;
      }
    } else {
      LogUtils.v(
          TAG, "Queued speech item, waiting for \"%s\"", mCurrentFeedbackItem.getUtteranceId());
    }
    return false;
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

    UtteranceCompleteAction action;
    while ((action = mUtteranceCompleteActions.poll()) != null) {
      UtteranceCompleteRunnable runnable = action.runnable;
      if (runnable != null) {
        runUtteranceCompleteRunnable(runnable, STATUS_INTERRUPTED);
      }
    }

    // Don't call handleSpeechCompleted(int status), it will be called by the TTS when
    // it stops the current current utterance.
  }

  /** Save the current feedback and the contents of the feedback queue. */
  @SuppressWarnings({"unchecked"})
  private void saveCurrentFeedbackInfo() {
    if (!requestPause) {
      feedbackSavedTime = SystemClock.uptimeMillis();
      savedFeedbackQueue = (ArrayList<FeedbackItem>) feedbackQueue.clone();
      savedFeedbackItem = mCurrentFeedbackItem;
      savedFragmentIterator =
          currentFragmentIterator != null ? currentFragmentIterator.deepCopy() : null;
    }
  }

  /** Load the saved feedback and the contents of the feedback queue. */
  private void loadSavedFeedbackInfo() {
    if (savedFeedbackQueue == null || savedFragmentIterator == null) {
      return;
    }
    feedbackQueue = savedFeedbackQueue;
    savedFeedbackQueue = null;
    mCurrentFeedbackItem = savedFeedbackItem;
    currentFragmentIterator = savedFragmentIterator;
    savedFragmentIterator = null;
  }

  /** Reset the saved feedback and the contents of the feedback queue. */
  private void resetSavedFeedbackInfo() {
    requestPause = false;
    savedFragmentIterator = null;
    savedFeedbackItem = null;
    if (savedFeedbackQueue != null) {
      savedFeedbackQueue.clear();
    }
  }

  private void onFragmentStarted(String utteranceId) {
    final int utteranceIndex = SpeechControllerImpl.parseUtteranceId(utteranceId);
    onUtteranceStarted(utteranceIndex);
  }

  private void onFragmentRangeStarted(String utteranceId, int start, int end) {
    int offset = 0;
    if (currentFragmentIterator != null) {
      currentFragmentIterator.onFragmentRangeStarted(utteranceId, start, end);
      offset = currentFragmentIterator.getFeedbackItemOffset();
    }
    final int utteranceIndex = SpeechControllerImpl.parseUtteranceId(utteranceId);
    onUtteranceRangeStarted(utteranceIndex, start + offset, end + offset);
  }

  /**
   * Handles completion of a {@link FeedbackFragment}.
   *
   * <p>
   *
   * @param utteranceId The ID of the {@link FeedbackItem} the fragment belongs to.
   * @param success Whether the fragment was spoken successfully.
   * @param advance Whether to advance to the next queue item.
   * @param notifyObserver Whether to notify the Observer about the completion of the {@link
   *     FeedbackFragment}. This parameter is ignored if the corresponding {@link FeedbackFragment}
   *     completed successfully, or it was interrupted by another {@link FeedbackFragment}.
   */
  private void onFragmentCompleted(
      String utteranceId, boolean success, boolean advance, boolean notifyObserver) {
    if (currentFragmentIterator != null) {
      currentFragmentIterator.onFragmentCompleted(utteranceId, success);
    }

    final int utteranceIndex = SpeechControllerImpl.parseUtteranceId(utteranceId);
    final boolean interrupted =
        (mCurrentFeedbackItem != null)
            && (!mCurrentFeedbackItem.getUtteranceId().equals(utteranceId));

    final int status;
    if (interrupted) {
      status = STATUS_INTERRUPTED;
    } else if (requestPause
        && savedFeedbackItem != null
        && utteranceId.equals(savedFeedbackItem.getUtteranceId())) {
      status = STATUS_PAUSE;
    } else if (success) {
      status = STATUS_SPOKEN;
    } else if (!notifyObserver) {
      status = STATUS_ERROR_DONT_NOTIFY_OBSERVER;
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
    UtteranceStartAction action;
    while (((action = mUtteranceStartActions.peek()) != null)
        && (action.utteranceIndex <= utteranceIndex)) {
      mUtteranceStartActions.remove(action);
      final UtteranceStartRunnable runnable = action.runnable;
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
    sourceIsVolumeControl = false;
    UtteranceCompleteAction action;
    while (((action = mUtteranceCompleteActions.peek()) != null)
        && (action.utteranceIndex <= utteranceIndex)) {
      mUtteranceCompleteActions.remove(action);
      UtteranceCompleteRunnable runnable = action.runnable;
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
      LogUtils.v(
          TAG,
          "Interrupted %d with %s",
          utteranceIndex,
          (mCurrentFeedbackItem == null) ? null : mCurrentFeedbackItem.getUtteranceId());
      return;
    }

    if (advance && !speakNextItem()) {
      handleSpeechCompleted(status);
    }
  }

  private void onTtsInitialized(boolean wasSwitchingEngines) {
    mDelegate.onTtsReady();
    // The previous engine may not have shut down correctly, so make sure to
    // clear the "current" speech item.
    if (mCurrentFeedbackItem != null) {
      onFragmentCompleted(
          mCurrentFeedbackItem.getUtteranceId(),
          false /* success */,
          false /* advance */,
          true /* notifyObserver */);
      mCurrentFeedbackItem = null;
    }

    if (wasSwitchingEngines && ttsChangeAnnouncementEnabled && !mSkipNextTTSChangeAnnouncement) {
      speakCurrentEngine();
    } else if (!feedbackQueue.isEmpty()) {
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
    final FeedbackItem nextItem = (feedbackQueue.isEmpty() ? null : feedbackQueue.remove(0));

    mCurrentFeedbackItem = nextItem;

    if (nextItem == null) {
      LogUtils.v(TAG, "No next item, stopping speech queue");
      return false;
    }

    if (previousItem == null) {
      handleSpeechStarting();
    }

    currentFragmentIterator = new FeedbackFragmentsIterator(nextItem.getFragments().iterator());
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
      LogUtils.e(TAG, "value '%s' is not a string", value);
    }

    return defaultValue;
  }

  private final Handler mHandler = new Handler();

  private final AudioManager.OnAudioFocusChangeListener mAudioFocusListener =
      new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
          LogUtils.d(TAG, "Saw audio focus change: %d", focusChange);
        }
      };

  private final @Nullable AudioFocusRequest mAudioFocusRequest =
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
    public UtteranceCompleteAction(
        int utteranceIndex,
        @UtteranceGroup int utteranceGroup,
        UtteranceCompleteRunnable runnable) {
      this.utteranceIndex = utteranceIndex;
      this.runnable = runnable;
      this.utteranceGroup = utteranceGroup;
    }

    /** The minimum utterance index that must complete before this action should be performed. */
    public int utteranceIndex;

    /** The action to execute. */
    public UtteranceCompleteRunnable runnable;

    /** The group of the utterance. */
    @UtteranceGroup public int utteranceGroup = UTTERANCE_GROUP_DEFAULT;

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

    private final @Nullable FeedbackItem mSample;
    private final boolean mEqual;

    public FeedbackItemEqualSamplePredicate(@Nullable FeedbackItem sample, boolean equal) {
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
