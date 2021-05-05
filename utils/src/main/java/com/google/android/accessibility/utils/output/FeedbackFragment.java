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

import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import com.google.android.accessibility.utils.FailoverTextToSpeech;
import com.google.android.accessibility.utils.FailoverTextToSpeech.SpeechParam;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a fragment of feedback included within a {@link FeedbackItem}. It must contain speech
 * with optional earcons and haptic feedback.
 */
public class FeedbackFragment {

  private static final String TAG = "FeedbackFragment";

  /** Text to be spoken when processing this fragment */
  private CharSequence mText;

  /** Locale information of this fragment for TTS. */
  private Locale mLocale;

  /**
   * Set of resource IDs indicating the auditory icons to be played when this fragment is processed
   */
  private Set<Integer> mEarcons;

  /**
   * Set of resource IDs indicating the haptic patterns to be generated when this fragment is
   * processed
   */
  private Set<Integer> mHaptics;

  /**
   * {@link SpeechParam} fields used for altering various properties on the speech feedback for this
   * fragment.
   *
   * @see SpeechParam#PITCH
   * @see SpeechParam#RATE
   * @see SpeechParam#VOLUME
   */
  private Bundle mSpeechParams;

  /**
   * {@link Utterance} metadata parameters used for altering various properties on the non-speech
   * feedback for this fragment.
   *
   * @see Utterance#KEY_METADATA_EARCON_RATE
   * @see Utterance#KEY_METADATA_EARCON_VOLUME
   */
  private Bundle mNonSpeechParams;
  /**
   * The start index of {@link #mText}, which is a substring in {@link FeedbackItem}.
   *
   * <p>Assume the speech of {@link FeedbackItem} is "first Chinese.second English.". "second
   * Chinese" is saved in {@link #mText}, and we also save the index of 's' in this field.
   */
  private int startIndexInFeedbackItem = 0;

  /**
   * The start index that {@link #mText} is going to be spoken from.
   *
   * <p>When {@link #mText} is spoken, we know where is going to be started by {@link
   * FailoverTextToSpeech.FailoverTtsListener#onUtteranceRangeStarted(String, int, int)} we save the
   * start index here.
   */
  public int fragmentStartIndex = 0;

  public FeedbackFragment(CharSequence text, @Nullable Bundle speechParams) {
    this(text, null, null, speechParams, null);
  }

  public FeedbackFragment(
      CharSequence text,
      @Nullable Set<Integer> earcons,
      @Nullable Set<Integer> haptics,
      @Nullable Bundle speechParams,
      @Nullable Bundle nonSpeechParams) {
    mText = new SpannableString(text);

    mEarcons = new HashSet<>();
    if (earcons != null) {
      mEarcons.addAll(earcons);
    }

    mHaptics = new HashSet<>();
    if (haptics != null) {
      mHaptics.addAll(haptics);
    }

    mSpeechParams = new Bundle(Bundle.EMPTY);
    if (speechParams != null) {
      mSpeechParams.putAll(speechParams);
    }

    mNonSpeechParams = new Bundle(Bundle.EMPTY);
    if (nonSpeechParams != null) {
      mNonSpeechParams.putAll(nonSpeechParams);
    }
  }

  /** Creates a new fragment by deep copying the data from the specified fragment. */
  public FeedbackFragment(FeedbackFragment fragment) {
    this(
        fragment.getText(),
        fragment.getEarcons(),
        fragment.getHaptics(),
        fragment.getSpeechParams(),
        fragment.getNonSpeechParams());
  }

  /** @return The text of this fragment */
  public CharSequence getText() {
    return mText;
  }

  /** @param text The text to set for this fragment */
  public void setText(CharSequence text) {
    mText = text;
  }

  /** @return The locale information of this fragment. */
  public Locale getLocale() {
    return mLocale;
  }

  /** Sets locale information of this fragment. */
  public void setLocale(Locale locale) {
    mLocale = locale;
  }

  /** @return An unmodifiable set of IDs of the earcons to play along with this fragment */
  public Set<Integer> getEarcons() {
    return Collections.unmodifiableSet(mEarcons);
  }

  /**
   * @param earconId The ID of the earcon to add to the set of earcons to play when this fragment is
   *     processed
   */
  public void addEarcon(int earconId) {
    mEarcons.add(earconId);
  }

  /** Clears all earcons associated with this fragment */
  public void clearAllEarcons() {
    mEarcons.clear();
  }

  /**
   * @return an unmodifiable set of IDs of the haptic patterns to produce along with this fragment
   */
  public Set<Integer> getHaptics() {
    return Collections.unmodifiableSet(mHaptics);
  }

  /**
   * @param hapticId The ID of the haptic pattern to add to the set of haptic patterns to play when
   *     this fragment is processed
   */
  public void addHaptic(int hapticId) {
    mHaptics.add(hapticId);
  }

  /** Clears all haptic patterns associated with this fragment. */
  public void clearAllHaptics() {
    mHaptics.clear();
  }

  /** @return the {@link SpeechParam} parameters to use when processing this fragment */
  public Bundle getSpeechParams() {
    return mSpeechParams;
  }

  /** @param speechParams the {@link SpeechParam} parameters to use when processing this fragment */
  public void setSpeechParams(Bundle speechParams) {
    mSpeechParams = speechParams;
  }

  /** @return the {@link Utterance} non-speech parameters to use when processing this fragment */
  public Bundle getNonSpeechParams() {
    return mNonSpeechParams;
  }

  /**
   * @param nonSpeechParams the {@link SpeechParam} parameters to use when processing this fragment
   */
  public void setNonSpeechParams(Bundle nonSpeechParams) {
    mNonSpeechParams = nonSpeechParams;
  }

  /**
   * Records the beginning of {@link #mText} in the original text of {@link FeedbackItem}.
   *
   * @param startIndexInFeedbackItem the start index of {@link #mText} in {@link FeedbackItem}.
   */
  void setStartIndexInFeedbackItem(int startIndexInFeedbackItem) {
    if (startIndexInFeedbackItem >= 0) {
      this.startIndexInFeedbackItem = startIndexInFeedbackItem;
    }
  }

  /** @return the start index (see {@link #startIndexInFeedbackItem} for more details) * */
  int getStartIndexInFeedbackItem() {
    return startIndexInFeedbackItem;
  }

  /** @param utteranceStartIndex the index of utterance that TextToSpeech is going to read from. */
  void recordFragmentStartIndex(int utteranceStartIndex) {
    this.fragmentStartIndex = utteranceStartIndex;
  }

  /** Update {@link #mText} and {@link #startIndexInFeedbackItem} by {@link #fragmentStartIndex}. */
  void updateContentByFragmentStartIndex() {
    if (fragmentStartIndex < mText.length()) {
      CharSequence remainingSequence = mText.subSequence(fragmentStartIndex, mText.length());
      mText = remainingSequence;
      startIndexInFeedbackItem += fragmentStartIndex;
      fragmentStartIndex = 0;
    } else {
      LogUtils.w(
          TAG, "updateContentByFragmentStartIndex, fragmentStartIndex is out of mText bound", "");
    }
    LogUtils.v(TAG, "updateContentByFragmentStartIndex ,  remaining utterance = %s", mText);
  }

  @Override
  public String toString() {
    return "{text:"
        + mText
        + ", earcons:"
        + mEarcons
        + ", haptics:"
        + mHaptics
        + ", speechParams:"
        + mSpeechParams
        + "nonSpeechParams:"
        + mNonSpeechParams
        + "fragmentStartIndex:"
        + fragmentStartIndex
        + "}";
  }

  @Override
  public int hashCode() {
    int hashCode = 17;
    hashCode = 31 * hashCode + (mText == null ? 0 : mText.hashCode());
    hashCode = 31 * hashCode + (mEarcons == null ? 0 : mEarcons.hashCode());
    hashCode = 31 * hashCode + (mHaptics == null ? 0 : mHaptics.hashCode());
    hashCode = 31 * hashCode + getBundleHashCode(mSpeechParams);
    hashCode = 31 * hashCode + getBundleHashCode(mNonSpeechParams);
    return hashCode;
  }

  private int getBundleHashCode(Bundle bundle) {
    if (bundle == null) {
      return 0;
    }

    int hashCode = 0;
    for (String key : bundle.keySet()) {
      Object value = bundle.get(key);
      hashCode += value == null ? 0 : value.hashCode();
    }

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FeedbackFragment)) {
      return false;
    }

    FeedbackFragment fragment = (FeedbackFragment) obj;

    if (!TextUtils.equals(mText, fragment.mText)) {
      return false;
    }

    //noinspection SimplifiableIfStatement
    if (objectsNotEqual(mEarcons, fragment.mEarcons)
        || objectsNotEqual(mHaptics, fragment.mHaptics)) {
      return false;
    }

    return !(bundleNotEqual(mSpeechParams, fragment.mSpeechParams)
        || bundleNotEqual(mNonSpeechParams, fragment.mNonSpeechParams));
  }

  private boolean objectsNotEqual(Object obj1, Object obj2) {
    return (obj1 != null || obj2 != null) && (obj1 == null || obj2 == null || !obj1.equals(obj2));
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean bundleNotEqual(Bundle bundle1, Bundle bundle2) {
    if (bundle1 == null && bundle2 == null) {
      return false;
    }

    if (bundle1 != null && bundle2 != null) {
      int size = bundle1.size();
      if (bundle2.size() != size) {
        return true;
      }

      for (String key : bundle1.keySet()) {
        if (objectsNotEqual(bundle1.get(key), bundle2.get(key))) {
          return true;
        }
      }

      return false;
    }

    return true;
  }
}
