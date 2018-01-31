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

import android.media.AudioManager;
import android.os.Bundle;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FailoverTextToSpeech;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.Set;

public interface SpeechController {
  /** Default stream for speech output. */
  int DEFAULT_STREAM =
      BuildVersionUtils.isAtLeastO()
          ? AudioManager.STREAM_ACCESSIBILITY
          : AudioManager.STREAM_MUSIC;

  // Queue modes.
  int QUEUE_MODE_INTERRUPT = 0;
  int QUEUE_MODE_QUEUE = 1;
  /**
   * Similiar to QUEUE_MODE_QUEUE. The only difference is FeedbackItem in this mode cannot be
   * interrupted by another while it is speaking. This includes not being removed from the queue
   * unless shutdown is called.
   */
  int QUEUE_MODE_UNINTERRUPTIBLE = 2;

  int QUEUE_MODE_FLUSH_ALL = 3;

  // Speech item status codes.
  int STATUS_ERROR = 1;
  int STATUS_SPEAKING = 2;
  int STATUS_INTERRUPTED = 3;
  int STATUS_SPOKEN = 4;
  int STATUS_NOT_SPOKEN = 5;

  int UTTERANCE_GROUP_DEFAULT = 0;
  int UTTERANCE_GROUP_TEXT_SELECTION = 1;
  int UTTERANCE_GROUP_SEEK_PROGRESS = 2;
  int UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS = 3;
  int UTTERANCE_GROUP_SCREEN_MAGNIFICATION = 4;

  /**
   * Delegate that is registered in {@link SpeechController} to provide callbacks when processing
   * {@link FeedbackItem}/{@link Utterance}.
   */
  interface Delegate {
    boolean shouldSuppressPassiveFeedback();

    void onSpeakingForcedFeedback();

    void interruptAllFeedback(boolean stopTtsSpeechCompletely);
  }

  /**
   * Listener for speech started and completed. TODO: This is only used for tests. Evaluate if
   * it's still appropriate.
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
    /** @param status The status supplied. */
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
    public Set<Integer> mEarcons = null;
    public Set<Integer> mHaptics = null;
    public int mQueueMode = QUEUE_MODE_QUEUE;
    public int mFlags = 0;
    public int mUtteranceGroup = UTTERANCE_GROUP_DEFAULT;
    public Bundle mSpeechParams = null;
    public Bundle mNonSpeechParams = null;
    public UtteranceStartRunnable mStartingAction = null;
    public UtteranceRangeStartCallback mRangeStartCallback = null;
    public UtteranceCompleteRunnable mCompletedAction = null;

    private SpeakOptions() {} // To instantiate, use create().

    public static SpeakOptions create() {
      return new SpeakOptions();
    }

    public SpeakOptions setEarcons(Set<Integer> earcons) {
      mEarcons = earcons;
      return this;
    }

    public SpeakOptions setHaptics(Set<Integer> haptics) {
      mHaptics = haptics;
      return this;
    }

    public SpeakOptions setQueueMode(int queueMode) {
      mQueueMode = queueMode;
      return this;
    }

    public SpeakOptions setFlags(int flags) {
      mFlags = flags;
      return this;
    }

    public SpeakOptions setUtteranceGroup(int utteranceGroup) {
      mUtteranceGroup = utteranceGroup;
      return this;
    }

    public SpeakOptions setSpeechParams(Bundle speechParams) {
      mSpeechParams = speechParams;
      return this;
    }

    public SpeakOptions setNonSpeechParams(Bundle nonSpeechParams) {
      mNonSpeechParams = nonSpeechParams;
      return this;
    }

    public SpeakOptions setStartingAction(UtteranceStartRunnable runnable) {
      mStartingAction = runnable;
      return this;
    }

    public SpeakOptions setRangeStartCallback(UtteranceRangeStartCallback callback) {
      mRangeStartCallback = callback;
      return this;
    }

    public SpeakOptions setCompletedAction(UtteranceCompleteRunnable runnable) {
      mCompletedAction = runnable;
      return this;
    }
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
   * @param eventId The identity of an event which originated this speech action.
   */
  void speak(
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
      EventId eventId);

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  void speak(CharSequence text, int queueMode, int flags, Bundle speechParams, EventId eventId);

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
      Bundle speechParams,
      Bundle nonSpeechParams,
      EventId eventId);

  /**
   * @see #speak(CharSequence, Set, Set, int, int, int, Bundle, Bundle, UtteranceStartRunnable,
   *     UtteranceRangeStartCallback, UtteranceCompleteRunnable, EventId)
   */
  void speak(CharSequence text, EventId eventId, SpeakOptions options);

  boolean isSpeaking();

  boolean isSpeakingOrSpeechQueued();

  void addObserver(Observer observer);

  void removeObserver(Observer observer);

  /** Stops all Talkback speech. Stops speech from other apps if stopTtsSpeechCompletely is true */
  void interrupt(boolean stopTtsSpeechCompletely);

  /** Sends an interrupt to the delegate without modifying internal state. */
  void interruptAllFeedback(boolean stopTtsSpeechCompletely);

  /** Returns the last spoken utterance. */
  FeedbackItem getLastUtterance();

  /** Repeats the last spoken utterance. */
  boolean repeatLastUtterance();

  /** Repeats the provided utterance. */
  boolean repeatUtterance(FeedbackItem item);

  /** Spells the last spoken utterance. */
  boolean spellLastUtterance();

  /** Spells the text. */
  boolean spellUtterance(CharSequence text);

  /** Copies the last phrase spoken by TalkBack to clipboard */
  boolean copyLastUtteranceToClipboard(FeedbackItem item, EventId eventId);

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

  // TODO: Check if it can be defined as a private method.
  void addUtteranceCompleteAction(int index, UtteranceCompleteRunnable runnable);

  /**
   * Sets whether the SpeechControllerImpl should inject utterance completed callbacks for advancing
   * continuous reading.
   */
  void setShouldInjectAutoReadingCallbacks(
      boolean shouldInject, UtteranceCompleteRunnable nextItemCallback);

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
}
