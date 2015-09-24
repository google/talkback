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

package com.android.talkback;

import android.text.SpannableStringBuilder;
import com.android.utils.StringBuilderUtils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the feedback produced by a single {@link Utterance}
 */
public class FeedbackItem {

    /**
     * Flag used to prevent this FeedbackItem from being included in utterance
     * history.
     */
    public static final int FLAG_NO_HISTORY = 0x2;

    /**
     * Flag to force feedback from this item to be generated, even while speech
     * recognition is active.
     */
    public static final int FLAG_DURING_RECO = 0x4;

    /**
     * Flag to inform the processor that completion of this item should advance
     * continuous reading, if active.
     */
    public static final int FLAG_ADVANCE_CONTINUOUS_READING = 0x8;

    /**
     * Flag to inform the processor that this feedback item should have its
     * speech ignored and have no impact on speech queues.
     */
    public static final int FLAG_NO_SPEECH = 0x10;

    /**
     * Flag to inform the processor that this feedback item should be skipped
     * if duplicate utterance is on queue or currently pronouncing
     */
    public static final int FLAG_SKIP_DUPLICATE = 0x20;

    /**
     * Flag to inform that all utterances with the same utterance group should be cleared from
     * utterance queue
     */
    public static final int FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP = 0x40;

    /**
     * Flag to inform that utterance with the same utterance group should be interrupted
     */
    public static final int FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP = 0x80;


    /** Unique ID defining this generated feedback */
    private String mUtteranceId = "";

    /**
     * Ordered fragments of the feedback to be produced from a single
     * {@link Utterance}.
     */
    private List<FeedbackFragment> mFragments = new LinkedList<>();

    /** Flag indicating that this FeedbackItem should be uninterruptible. */
    private boolean mIsUninterruptible;

    /** Flags defining the treatment of this FeedbackItem. */
    private int mFlags;

    private int mUtteranceGroup = SpeechController.UTTERANCE_GROUP_DEFAULT;

    /**
     * {@link SpeechController.UtteranceCompleteRunnable} to be fired when feedback from this
     * item is complete.
     */
    private SpeechController.UtteranceCompleteRunnable mCompletedAction;

    /**
     * @return The utterance ID for this item
     */
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
     * @return all text contained by this item, or {@code null} if no fragments
     *         exist.
     */
    public CharSequence getAggregateText() {
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

    /**
     * Removes all {@link FeedbackFragment}s associated with this item.
     */
    public void clearFragments() {
        mFragments.clear();
    }

    /**
     * @return {@code true} if this item should be uninterruptible,
     *         {@code false} otherwise
     */
    public boolean isInterruptible() {
        return !mIsUninterruptible;
    }

    /**
     * Sets whether this item should be uninterruptible.
     *
     * @param isUninterruptible {@code true} if this item should be
     *            uninterruptible, {@code false} otherwise
     */
    public void setUninterruptible(boolean isUninterruptible) {
        mIsUninterruptible = isUninterruptible;
    }

    /**
     * Determines if the FeedbackItem has the given flag.
     *
     * @param flag The flag to check
     * @return {@code true} if the FeedbackItem has the given flag,
     *         {@code false} otherwise
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

    /**
     * @return the {@link SpeechController.UtteranceCompleteRunnable} associated with this item
     */
    public SpeechController.UtteranceCompleteRunnable getCompletedAction() {
        return mCompletedAction;
    }

    /**
     * Replaces the existing completion action of this item.
     *
     * @param action The action to set
     */
    public void setCompletedAction(SpeechController.UtteranceCompleteRunnable action) {
        mCompletedAction = action;
    }

    public void setUtteranceGroup(int utteranceGroup) {
        mUtteranceGroup = utteranceGroup;
    }

    public int getUtteranceGroup() {
        return mUtteranceGroup;
    }

    @Override
    public String toString() {
        return "{utteranceId:\"" + mUtteranceId + "\", fragments:" + mFragments
                + ", uninterruptible:" + mIsUninterruptible + ", flags:" + mFlags + "}";
    }
}
