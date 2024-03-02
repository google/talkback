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

import static com.google.android.accessibility.utils.StringBuilderUtils.optionalField;

import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import androidx.annotation.IntDef;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface SpeechController {
  /** Default stream for speech output. */
  int DEFAULT_STREAM =
      BuildVersionUtils.isAtLeastO()
          ? AudioManager.STREAM_ACCESSIBILITY
          : AudioManager.STREAM_MUSIC;

  // Queue modes bit masks.
  int QUEUE_MODE_BIT_DEFAULT = 0; // default is queued.
  int QUEUE_MODE_BIT_INTERRUPT = 1 << 0;
  int QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH = 1 << 1;
  int QUEUE_MODE_BIT_CAN_IGNORE_INTERRUPTS = 1 << 2;
  int QUEUE_MODE_BIT_FLUSH_ALL = 1 << 3;

  // Queue modes.
  int QUEUE_MODE_INTERRUPT = QUEUE_MODE_BIT_INTERRUPT;

  /**
   * The queue mode will be add the new feedback entry at the end of the playback queue.
   *
   * <p>And it acts like TTS queue mode {@link TextToSpeech#QUEUE_ADD} internally that would not
   * flush the global TTS queue.
   */
  int QUEUE_MODE_QUEUE = 0;

  /**
   * Similar to QUEUE_MODE_QUEUE. The only difference is FeedbackItem in this mode cannot be
   * interrupted by another while it is speaking. This includes not being removed from the queue
   * unless shutdown is called. FeedbackItem in this mode will still be interrupted and removed from
   * the queue when {@link SpeechController#interrupt} is called.
   */
  int QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH = QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH;

  int QUEUE_MODE_FLUSH_ALL = QUEUE_MODE_BIT_FLUSH_ALL | QUEUE_MODE_INTERRUPT;

  /**
   * FeedbackItem in this mode cannot be interrupted or removed from the queue when {@link
   * SpeechController#interrupt(boolean, boolean, boolean)} is called and the
   * interruptItemsThatCanIgnoreInterrupts parameter is true.
   */
  int QUEUE_MODE_CAN_IGNORE_INTERRUPTS = QUEUE_MODE_BIT_CAN_IGNORE_INTERRUPTS;

  /**
   * FeedbackItems in this mode have the properties of both QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH
   * and QUEUE_MODE_CAN_IGNORE_INTERRUPTS.
   */
  int QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS =
      QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH | QUEUE_MODE_BIT_CAN_IGNORE_INTERRUPTS;

  /**
   * FeedbackItems in this mode have the properties of both QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH
   * and QUEUE_MODE_INTERRUPT.
   */
  int QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH =
      QUEUE_MODE_BIT_INTERRUPT | QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH;

  /** Enumeration of queue-mode values. */
  @IntDef({
    QUEUE_MODE_INTERRUPT,
    QUEUE_MODE_QUEUE,
    QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH,
    QUEUE_MODE_FLUSH_ALL,
    QUEUE_MODE_CAN_IGNORE_INTERRUPTS,
    QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS,
    QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface QueueMode {}

  // Speech item status codes.
  int STATUS_ERROR = 1;
  int STATUS_INTERRUPTED = 3;
  int STATUS_SPOKEN = 4;
  int STATUS_NOT_SPOKEN = 5;
  // A status indicates that the speech was interrupted by the client, and the Observer should not
  // be notified when speech stops as a result of the interruption.
  int STATUS_ERROR_DONT_NOTIFY_OBSERVER = 6;
  int STATUS_PAUSE = 7;

  // Speech groups, for interrupting specific groups only, when
  // FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP is set.
  int UTTERANCE_GROUP_DEFAULT = 0;
  int UTTERANCE_GROUP_TEXT_SELECTION = 1;
  int UTTERANCE_GROUP_SEEK_PROGRESS = 2;
  int UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS = 3;
  int UTTERANCE_GROUP_SCREEN_MAGNIFICATION = 4;
  int UTTERANCE_GROUP_CONTENT_CHANGE = 5;
  int UTTERANCE_GROUP_CONTENT_HINTS = 6;

  /** Enumeration of utterance-group values. */
  @IntDef({
    UTTERANCE_GROUP_DEFAULT,
    UTTERANCE_GROUP_TEXT_SELECTION,
    UTTERANCE_GROUP_SEEK_PROGRESS,
    UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS,
    UTTERANCE_GROUP_SCREEN_MAGNIFICATION,
    UTTERANCE_GROUP_CONTENT_CHANGE,
    UTTERANCE_GROUP_CONTENT_HINTS
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface UtteranceGroup {}

  /**
   * Delegate that is registered in {@link SpeechController} to provide callbacks when processing
   * {@link FeedbackItem}/{@link Utterance}.
   */
  interface Delegate {
    boolean isAudioPlaybackActive();

    boolean isMicrophoneActiveAndHeadphoneOff();

    boolean isSsbActiveAndHeadphoneOff();

    boolean isPhoneCallActive();

    void onSpeakingForcedFeedback();

    default void onTtsReady() {}
    ;
  }

  /**
   * Listener for speech started and completed. TODO: This is only used for tests. Evaluate
   * if it's still appropriate.
   */
  interface SpeechControllerListener {
    void onUtteranceQueued(FeedbackItem utterance);

    void onUtteranceStarted(FeedbackItem utterance);

    void onUtteranceCompleted(int utteranceIndex, int status);
  }

  /** Receives events when speech starts and stops. */
  interface Observer {
    void onSpeechStarting();

    void onSpeechCompleted();

    void onSpeechPaused();
  }

  /** Interface for a run method, used to perform action when an utterance starts. */
  interface UtteranceStartRunnable {
    void run();
  }

  /** Interface for a callback method, used to update the range of utterance being spoken */
  interface UtteranceRangeStartCallback {
    /**
     * Callback to be invoked when it is about to speak the specific range of the utterance.
     *
     * @param start The start index of the range in the utterance text.
     * @param end The end index of the range (exclusive) in the utterance text.
     */
    void onUtteranceRangeStarted(int start, int end);
  }

  /** Interface for a run method with a status, used to perform post-utterance action. */
  interface UtteranceCompleteRunnable {
    /**
     * @param status The status supplied.
     */
    void run(int status);
  }

  /** Utility class run an UtteranceCompleteRunnable. */
  class CompletionRunner implements Runnable {
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

  /** Builder class for input parameters to {@code speak()}. */
  class SpeakOptions {
    public @Nullable Set<Integer> mEarcons = null;
    public @Nullable Set<Integer> mHaptics = null;
    public int mQueueMode = QUEUE_MODE_BIT_DEFAULT;
    public int mFlags = 0;
    public @UtteranceGroup int mUtteranceGroup = UTTERANCE_GROUP_DEFAULT;
    public @Nullable Bundle mSpeechParams = null;
    public @Nullable Bundle mNonSpeechParams = null;
    public @Nullable UtteranceStartRunnable mStartingAction = null;
    public @Nullable UtteranceRangeStartCallback mRangeStartCallback = null;
    public @Nullable UtteranceCompleteRunnable mCompletedAction = null;

    private SpeakOptions() {} // To instantiate, use create().

    public static SpeakOptions create() {
      return new SpeakOptions();
    }

    @CanIgnoreReturnValue
    public SpeakOptions setEarcons(Set<Integer> earcons) {
      mEarcons = earcons;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setHaptics(Set<Integer> haptics) {
      mHaptics = haptics;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setQueueMode(@QueueMode int queueMode) {
      mQueueMode = queueMode;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setFlags(int flags) {
      mFlags = flags;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setUtteranceGroup(@UtteranceGroup int utteranceGroup) {
      mUtteranceGroup = utteranceGroup;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setSpeechParams(Bundle speechParams) {
      mSpeechParams = speechParams;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setNonSpeechParams(Bundle nonSpeechParams) {
      mNonSpeechParams = nonSpeechParams;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setStartingAction(UtteranceStartRunnable runnable) {
      mStartingAction = runnable;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setRangeStartCallback(UtteranceRangeStartCallback callback) {
      mRangeStartCallback = callback;
      return this;
    }

    @CanIgnoreReturnValue
    public SpeakOptions setCompletedAction(@Nullable UtteranceCompleteRunnable runnable) {
      mCompletedAction = runnable;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SpeakOptions)) {
        return false;
      }
      SpeakOptions that = (SpeakOptions) o;
      return mQueueMode == that.mQueueMode
          && mFlags == that.mFlags
          && mUtteranceGroup == that.mUtteranceGroup
          && Objects.equals(mEarcons, that.mEarcons)
          && Objects.equals(mHaptics, that.mHaptics)
          && Objects.equals(mSpeechParams, that.mSpeechParams)
          && Objects.equals(mNonSpeechParams, that.mNonSpeechParams)
          && Objects.equals(mStartingAction, that.mStartingAction)
          && Objects.equals(mRangeStartCallback, that.mRangeStartCallback)
          && Objects.equals(mCompletedAction, that.mCompletedAction);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          mEarcons,
          mHaptics,
          mQueueMode,
          mFlags,
          mUtteranceGroup,
          mSpeechParams,
          mNonSpeechParams,
          mStartingAction,
          mRangeStartCallback,
          mCompletedAction);
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          optionalField("earcons", mEarcons),
          optionalField("haptics", mHaptics),
          optionalField("queueMode", queueModeBitToString(mQueueMode)),
          optionalField("flags", FeedbackItem.flagsToString(mFlags)),
          optionalField(
              "utteranceGroup",
              (mUtteranceGroup == UTTERANCE_GROUP_DEFAULT)
                  ? null
                  : utteranceGroupToString(mUtteranceGroup)),
          optionalField("speechParams", mSpeechParams),
          optionalField("nonSpeechParams", mNonSpeechParams));
    }
  }

  public static boolean hasQueueModeFlagSet(int queueMode, int flag) {
    if (flag == 0) {
      return false;
    }
    return (queueMode & flag) == flag;
  }

  public static @NonNull String queueModeBitToString(int mode) {

    StringBuilder buffer = new StringBuilder(128);
    buffer.append(hasQueueModeFlagSet(mode, QUEUE_MODE_BIT_INTERRUPT) ? "INTERRUPT" : "QUEUE");
    if (hasQueueModeFlagSet(mode, QUEUE_MODE_BIT_UNINTERRUPTIBLE_BY_NEW_SPEECH)) {
      buffer.append("/UNINTERRUPTIBLE");
    }
    if (hasQueueModeFlagSet(mode, QUEUE_MODE_BIT_CAN_IGNORE_INTERRUPTS)) {
      buffer.append("/CAN_IGNORE_INTERRUPTS");
    }
    if (hasQueueModeFlagSet(mode, QUEUE_MODE_BIT_FLUSH_ALL)) {
      buffer.append("/FLUSH_ALL");
    }
    return buffer.toString();
  }

  public static @NonNull String utteranceGroupToString(@UtteranceGroup int group) {
    switch (group) {
      case UTTERANCE_GROUP_DEFAULT:
        return "UTTERANCE_GROUP_DEFAULT";
      case UTTERANCE_GROUP_TEXT_SELECTION:
        return "UTTERANCE_GROUP_TEXT_SELECTION";
      case UTTERANCE_GROUP_SEEK_PROGRESS:
        return "UTTERANCE_GROUP_SEEK_PROGRESS";
      case UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS:
        return "UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS";
      case UTTERANCE_GROUP_SCREEN_MAGNIFICATION:
        return "UTTERANCE_GROUP_SCREEN_MAGNIFICATION";
      case UTTERANCE_GROUP_CONTENT_CHANGE:
        return "UTTERANCE_GROUP_CONTENT_CHANGE";
      case UTTERANCE_GROUP_CONTENT_HINTS:
        return "UTTERANCE_GROUP_CONTENT_HINTS";
      default:
        return "(unknown utterance group)";
    }
  }

  void toggleVoiceFeedback();

  boolean isMute();

  void setMute(boolean mute);

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
   * @param eventId The identity of an event which originated this speech action.
   */
  void speak(
      CharSequence text,
      Set<Integer> earcons,
      Set<Integer> haptics,
      int queueMode,
      int flags,
      int utteranceGroup,
      @Nullable Bundle speechParams,
      Bundle nonSpeechParams,
      UtteranceStartRunnable startAction,
      UtteranceRangeStartCallback rangeStartCallback,
      UtteranceCompleteRunnable completedAction,
      @Nullable EventId eventId);

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  void speak(
      CharSequence text,
      int queueMode,
      int flags,
      @Nullable Bundle speechParams,
      @Nullable EventId eventId);

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  void speak(
      CharSequence text,
      int queueMode,
      int flags,
      Bundle speechParams,
      UtteranceStartRunnable startingAction,
      UtteranceRangeStartCallback rangeStartCallback,
      UtteranceCompleteRunnable completedAction,
      EventId eventId);

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  void speak(
      CharSequence text,
      Set<Integer> earcons,
      Set<Integer> haptics,
      int queueMode,
      int flags,
      int uttranceGroup,
      @Nullable Bundle speechParams,
      Bundle nonSpeechParams,
      @Nullable EventId eventId);

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  void speak(CharSequence text, @Nullable EventId eventId, @Nullable SpeakOptions options);

  boolean isSpeaking();

  boolean isSpeakingOrSpeechQueued();

  void addObserver(Observer observer);

  void removeObserver(Observer observer);

  void setTTSChangeAnnouncementEnabled(boolean enabled);

  /**
   * Stops all speech from the calling app. Stops speech from other apps if stopTtsSpeechCompletely
   * is true.
   */
  void interrupt(boolean stopTtsSpeechCompletely);

  /**
   * Stops all speech from the calling app.
   *
   * @param stopTtsSpeechCompletely Whether to also stop speech from other apps
   * @param callObserver Whether to notify the Observer once speech is stopped
   */
  void interrupt(boolean stopTtsSpeechCompletely, boolean callObserver);

  /**
   * Stops all speech from the calling app.
   *
   * @param stopTtsSpeechCompletely Whether to also stop speech from other apps
   * @param callObserver Whether to notify the Observer once speech is stopped
   * @param interruptItemsThatCanIgnoreInterrupts Whether to interrupt and remove FeedbackItems that
   *     are in the QUEUE_MODE_CAN_IGNORE_INTERRUPTS or
   *     QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS mode when this method is
   *     called
   */
  void interrupt(
      boolean stopTtsSpeechCompletely,
      boolean callObserver,
      boolean interruptItemsThatCanIgnoreInterrupts);

  int peekNextUtteranceId();

  // TODO: Check if it can be defined as a private method.
  void addUtteranceStartAction(int index, UtteranceStartRunnable runnable);

  // TODO: Check if it can be defined as a private method.
  /**
   * Set the callback to update with the range of utterance being spoken.
   *
   * @param utteranceId The id of the utterance.
   * @param callback The callback to be invoked.
   */
  void setUtteranceRangeStartCallback(int utteranceId, UtteranceRangeStartCallback callback);

  /**
   * Adds a new group-labeled action that will be run when the given utterance index completes.
   *
   * @param index The index of the utterance that should finish before this action is executed.
   * @param utteranceGroup The group of the utterance.
   * @param runnable The code to execute.
   */
  void addUtteranceCompleteAction(
      int index, @UtteranceGroup int utteranceGroup, UtteranceCompleteRunnable runnable);

  /**
   * Sets whether the SpeechControllerImpl should inject utterance completed callbacks for advancing
   * continuous reading.
   */
  void setShouldInjectAutoReadingCallbacks(
      boolean shouldInject, UtteranceCompleteRunnable nextItemCallback);

  /** Clears {@link UtteranceCompleteRunnable}s which is belong to the hint group. */
  void clearHintUtteranceCompleteAction();

  /**
   * Gets the {@link FailoverTextToSpeech} instance that is serving as a text-to-speech service.
   *
   * @return The text-to-speech service.
   */
  FailoverTextToSpeech getFailoverTts();

  /** Sets the listener for starting, stopping and queuing speech. */
  void setSpeechListener(SpeechControllerListener speechListener);

  /**
   * Sets whether to handle TTS callback in main thread. If {@code false}, the callback will be
   * handled in TTS thread.
   */
  void setHandleTtsCallbackInMainThread(boolean shouldHandleInMainThread);

  /**
   * Stops current feedbackFragment but don't callback UtteranceCompleteAction since it's an
   * suspended state. {@link Observer#onSpeechPaused()} will be called if it works successfully.
   */
  void pause();

  /**
   * Speaks remaining sentence of suspended feedbackFragment. It works properly when {@link
   * FailoverTextToSpeech.FailoverTtsListener#onUtteranceRangeStarted(String, int, int)} could be
   * called by TTS engine(ex Samsung TTS engine doesn't support it ) and Android version should be
   * above Oreo, otherwise it would speak whole text of the feedbackFragment. {@link
   * Observer#onSpeechStarting()} will be called if it works successfully.
   */
  void resume();
}
