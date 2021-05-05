/*
 * Copyright (C) 2013 Google Inc.
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

import android.text.SpannableStringBuilder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceRangeStartCallback;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceStartRunnable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Represents the feedback produced by a single {@link Utterance} */
public class FeedbackItem {

  /** Flag used to prevent this FeedbackItem from being included in utterance history. */
  public static final int FLAG_NO_HISTORY = 0x2;

  /**
   * Flag to force feedback from this item to be generated when audio playback is active.
   * REFERTO
   */
  public static final int FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE = 0x4;

  /** Flag to force feedback from this item to be generated when microphone is active. */
  public static final int FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE = 0x8;

  /** Flag to force feedback from this item to be generated for speech recognition/dictation. */
  public static final int FLAG_FORCED_FEEDBACK_SSB_ACTIVE = 0x10;

  /** Flag to force feedback from this item to be generated during phone call. */
  public static final int FLAG_FORCED_FEEDBACK_PHONE_CALL_ACTIVE = 0x20;

  // TODO: make a flag that combines all the forced feedback flags

  /**
   * Flag to inform the processor that completion of this item should advance continuous reading, if
   * active.
   */
  public static final int FLAG_ADVANCE_CONTINUOUS_READING = 0x40;

  /**
   * Flag to inform the processor that this feedback item should have its speech ignored and have no
   * impact on speech queues.
   */
  public static final int FLAG_NO_SPEECH = 0x80;

  /**
   * Flag to inform the processor that this feedback item should be skipped if duplicate utterance
   * is on queue or currently pronouncing
   */
  public static final int FLAG_SKIP_DUPLICATE = 0x100;

  /**
   * Flag to inform that all utterances with the same utterance group should be cleared from
   * utterance queue
   */
  public static final int FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP = 0x200;

  /** Flag to inform that utterance with the same utterance group should be interrupted */
  public static final int FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP = 0x400;

  /** Flag to inform that device should not sleep during spoken feedback */
  public static final int FLAG_NO_DEVICE_SLEEP = 0x800;

  /**
   * Flag to force feedback from this item to be generated, even while speech recognition is active
   * or other voice assist app is playing voice feedback.
   */
  public static final int FLAG_FORCED_FEEDBACK = 0x1000;

  /** Unique ID defining this generated feedback */
  private String mUtteranceId = "";

  /** Ordered fragments of the feedback to be produced from a single {@link Utterance}. */
  private List<FeedbackFragment> mFragments = new ArrayList<>();

  /** Flag indicating that this FeedbackItem should be uninterruptible. */
  private boolean mIsUninterruptible;

  /**
   * Flag indicating that this FeedbackItem will ignore interrupts when {@link
   * SpeechController#interrupt} is called with the parameter interruptItemsThatCanIgnoreInterrupts
   * set to false. Note, the interrupt will never be ignored if the parameter
   * interruptItemsThatCanIgnoreInterrupts is true or if the parameter is not provided.
   */
  private boolean mCanIgnoreInterrupts;

  /** Flags defining the treatment of this FeedbackItem. */
  private int mFlags;

  /** The time (in system uptime ms) that the FeedbackItem was created. */
  private final long mCreationTime;

  private int mUtteranceGroup = SpeechController.UTTERANCE_GROUP_DEFAULT;

  @Nullable private final EventId mEventId;

  /**
   * Returns the {@link UtteranceStartRunnable} to be fired before feedback from this item starts.
   */
  @Nullable private UtteranceStartRunnable mStartAction;

  /**
   * Returns the {@link UtteranceRangeStartCallback} to be invoked to update the range of utterance
   * being spoken.
   */
  @Nullable private UtteranceRangeStartCallback mRangeStartCallback;

  /**
   * Returns the {@link UtteranceCompleteRunnable} to be fired when feedback from this item is
   * complete.
   */
  @Nullable private UtteranceCompleteRunnable mCompletedAction;

  public FeedbackItem(@Nullable EventId eventId) {
    mCreationTime = System.currentTimeMillis();
    mEventId = eventId;
  }

  /** Creates a new FeedbackItem by deep copying the data from the specified FeedbackItem. */
  public FeedbackItem(FeedbackItem item) {
    mCreationTime = System.currentTimeMillis();
    mEventId = item.getEventId();
    mFlags = item.getFlags();
    mUtteranceGroup = item.getUtteranceGroup();
    mStartAction = item.getStartAction();
    mCompletedAction = item.getCompletedAction();
    mRangeStartCallback = item.getRangeStartCallback();
    for (FeedbackFragment fragment : item.getFragments()) {
      mFragments.add(new FeedbackFragment(fragment));
    }
  }

  @Nullable
  public EventId getEventId() {
    return mEventId;
  }



  /** @return The utterance ID for this item */
  public String getUtteranceId() {
    return mUtteranceId;
  }

  /**
   * Sets the utterance ID for this item.
   *
   * @param id The ID to set
   */
  public void setUtteranceId(String id) {
    mUtteranceId = id;
  }

  /**
   * Retrieves the fragments for this item.
   *
   * @return an unmodifiable ordered {@link List} of fragments for this item
   */
  public List<FeedbackFragment> getFragments() {
    return Collections.unmodifiableList(mFragments);
  }

  /**
   * Retrieves the aggregate text from all {@link FeedbackFragment}s.
   *
   * @return all text contained by this item, or {@code null} if no fragments exist.
   */
  public @Nullable CharSequence getAggregateText() {
    if (mFragments.size() == 0) {
      return null;
    } else if (mFragments.size() == 1) {
      return mFragments.get(0).getText();
    }

    final SpannableStringBuilder sb = new SpannableStringBuilder();
    for (FeedbackFragment fragment : mFragments) {
      StringBuilderUtils.appendWithSeparator(sb, fragment.getText());
    }

    return sb.toString();
  }

  /**
   * Adds a fragment to the end of the list of fragments for this item.
   *
   * @param fragment The fragment to add
   */
  public void addFragment(FeedbackFragment fragment) {
    mFragments.add(fragment);
  }

  public void addFragmentAtPosition(FeedbackFragment fragment, int position) {
    mFragments.add(position, fragment);
  }

  /**
   * Removes the indicated fragment.
   *
   * @param fragment The fragment to remove
   * @return {@code true} if removed.
   */
  public boolean removeFragment(FeedbackFragment fragment) {
    return mFragments.remove(fragment);
  }

  /** Removes all {@link FeedbackFragment}s associated with this item. */
  public void clearFragments() {
    mFragments.clear();
  }

  /** @return {@code true} if this item should be uninterruptible, {@code false} otherwise */
  public boolean isInterruptible() {
    return !mIsUninterruptible;
  }

  /**
   * Returns whether this item will ignore interrupts when {@link SpeechController#interrupt} is
   * called with the parameter interruptItemsThatCanIgnoreInterrupts set to false.
   *
   * <p>Note: Items that can ignore interrupts will never ignore them if {@link
   * SpeechController#interrupt} is called with the parameter interruptItemsThatCanIgnoreInterrupts
   * set to true or if the parameter is not provided.
   *
   * @return {@code true} if this item will ignore interrupts when {@link
   *     SpeechController#interrupt} is called with interruptItemsThatCanIgnoreInterrupts set to
   *     false, {@code false} if the interruptItemsThatCanIgnoreInterrupts parameter does not affect
   *     whether this item will ignore interrupts
   */
  public boolean canIgnoreInterrupts() {
    return mCanIgnoreInterrupts;
  }

  /**
   * Sets whether this item should be uninterruptible.
   *
   * @param isUninterruptible {@code true} if this item should be uninterruptible, {@code false}
   *     otherwise
   */
  public void setUninterruptible(boolean isUninterruptible) {
    mIsUninterruptible = isUninterruptible;
  }

  /**
   * Sets whether this item should ignore interrupts when when {@link SpeechController#interrupt} is
   * called with the parameter interruptItemsThatCanIgnoreInterrupts set to false. Even if this item
   * can ignore interrupts, it will never ignore interrupts based on this flag if {@link
   * SpeechController#interrupt} is called with the parameter interruptItemsThatCanIgnoreInterrupts
   * set to true or if the parameter is not provided.
   *
   * @param canIgnoreInterrupts {@code true} if this item should ignore interrupts when {@link
   *     SpeechController#interrupt} is called with interruptItemsThatCanIgnoreInterrupts set to
   *     false, {@code false} if the parameter interruptItemsThatCanIgnoreInterrupts should not
   *     affect whether this item will ignore interrupts
   */
  public void setCanIgnoreInterrupts(boolean canIgnoreInterrupts) {
    mCanIgnoreInterrupts = canIgnoreInterrupts;
  }

  /**
   * Determines if the FeedbackItem has the given flag.
   *
   * @param flag The flag to check
   * @return {@code true} if the FeedbackItem has the given flag, {@code false} otherwise
   */
  public boolean hasFlag(int flag) {
    return ((mFlags & flag) == flag);
  }

  /**
   * Adds the given flag.
   *
   * @param flag The flag to add
   */
  public void addFlag(int flag) {
    mFlags |= flag;
  }

  /** @return the {@link UtteranceStartRunnable} associated with this item */
  public @Nullable UtteranceStartRunnable getStartAction() {
    return mStartAction;
  }

  /** @return the {@link UtteranceRangeStartCallback} associated with this item */
  public @Nullable UtteranceRangeStartCallback getRangeStartCallback() {
    return mRangeStartCallback;
  }

  /** @return the {@link UtteranceCompleteRunnable} associated with this item */
  public @Nullable UtteranceCompleteRunnable getCompletedAction() {
    return mCompletedAction;
  }

  /**
   * Replaces the existing start action of this item.
   *
   * @param action The action to set
   */
  public void setStartAction(@Nullable UtteranceStartRunnable action) {
    mStartAction = action;
  }

  /**
   * Replaces the existing {@link UtteranceRangeStartCallback} of this item.
   *
   * @param callback The callback to set
   */
  public void setRangeStartCallback(@Nullable UtteranceRangeStartCallback callback) {
    mRangeStartCallback = callback;
  }

  /**
   * Replaces the existing completion action of this item.
   *
   * @param action The action to set
   */
  public void setCompletedAction(@Nullable UtteranceCompleteRunnable action) {
    mCompletedAction = action;
  }

  public void setUtteranceGroup(int utteranceGroup) {
    mUtteranceGroup = utteranceGroup;
  }

  public int getUtteranceGroup() {
    return mUtteranceGroup;
  }

  public long getCreationTime() {
    return mCreationTime;
  }

  @Override
  public String toString() {
    return "{utteranceId:\""
        + mUtteranceId
        + "\", fragments:"
        + mFragments
        + ", uninterruptible:"
        + mIsUninterruptible
        + ", flags:"
        + mFlags
        + ", creationTime:"
        + mCreationTime
        + "}";
  }

  public int getFlags() {
    return this.mFlags;
  }
}
