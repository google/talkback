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

import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.TARGET_SPAN_CLASS;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.LocaleSpan;
import android.text.style.URLSpan;
import com.google.android.accessibility.utils.FailoverTextToSpeech.SpeechParam;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.SpannableUtils;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utilities for generating {@link FeedbackItem}s populated with {@link FeedbackFragment}s created
 * according to processing rules.
 */
public class FeedbackProcessingUtils {

  /**
   * Utterances must be no longer than MAX_UTTERANCE_LENGTH for the TTS to be able to handle them
   * properly. Similar limitation imposed by {@link TextToSpeech#getMaxSpeechInputLength()}
   */
  public static final int MAX_UTTERANCE_LENGTH = TextToSpeech.getMaxSpeechInputLength();

  /** The pitch scale factor value to use when announcing hyperlinks. */
  private static final float PITCH_CHANGE_HYPERLINK = 0.95f;

  /**
   * Produces a populated {@link FeedbackItem} based on rules defined within this class. Currently
   * splits utterances into reasonable chunks and adds auditory and speech characteristics for
   * formatting changes in processed text.
   *
   * @param text The text to include
   * @param earcons The earcons to be played when this item is processed
   * @param haptics The haptic patterns to be produced when this item is processed
   * @param flags The Flags defining the treatment of this item
   * @param speechParams The {@link SpeechParam} parameters to attribute to the spoken feedback
   *     within each fragment in this item.
   * @param nonSpeechParams The {@link Utterance} parameters to attribute to non-speech feedback for
   *     this item.
   * @return a populated {@link FeedbackItem}
   */
  public static FeedbackItem generateFeedbackItemFromInput(
      Context context,
      CharSequence text,
      @Nullable Set<Integer> earcons,
      @Nullable Set<Integer> haptics,
      int flags,
      int utteranceGroup,
      @Nullable Bundle speechParams,
      @Nullable Bundle nonSpeechParams,
      @Nullable EventId eventId) {
    final FeedbackItem feedbackItem = new FeedbackItem(eventId);
    final FeedbackFragment initialFragment =
        new FeedbackFragment(text, earcons, haptics, speechParams, nonSpeechParams);
    feedbackItem.addFragment(initialFragment);
    feedbackItem.addFlag(flags);
    feedbackItem.setUtteranceGroup(utteranceGroup);

    // Process the FeedbackItem
    addFormattingCharacteristics(feedbackItem);
    splitLongText(feedbackItem);

    return feedbackItem;
  }

  /**
   * Splits text contained within the {@link FeedbackItem}'s {@link FeedbackFragment}s into
   * fragments containing less than {@link #MAX_UTTERANCE_LENGTH} characters.
   *
   * @param item The item containing fragments to split.
   */
  // Visible for testing
  public static void splitLongText(FeedbackItem item) {
    for (int i = 0; i < item.getFragments().size(); ++i) {
      final FeedbackFragment fragment = item.getFragments().get(i);
      final CharSequence fragmentText = fragment.getText();
      if (TextUtils.isEmpty(fragmentText)) {
        continue;
      }

      if (fragmentText.length() >= MAX_UTTERANCE_LENGTH) {
        // If the text from an original fragment exceeds the allowable
        // fragment text length, start by removing the original fragment
        // from the item.
        item.removeFragment(fragment);

        // Split the fragment's text into multiple fragments that don't
        // exceed the limit and add new fragments at the appropriate
        // position in the item.
        final int end = fragmentText.length();
        int start = 0;
        int splitFragments = 0;
        while (start < end) {
          final int fragmentEnd = start + MAX_UTTERANCE_LENGTH - 1;

          // TODO: We currently split only on spaces.
          // Find a better way to do this for languages that don't
          // use spaces.
          int splitLocation = TextUtils.lastIndexOf(fragmentText, ' ', start + 1, fragmentEnd);
          if (splitLocation < 0) {
            splitLocation = Math.min(fragmentEnd, end);
          }
          final CharSequence textSection = TextUtils.substring(fragmentText, start, splitLocation);
          final FeedbackFragment additionalFragment =
              new FeedbackFragment(textSection, fragment.getSpeechParams());
          item.addFragmentAtPosition(additionalFragment, i + splitFragments);
          splitFragments++;
          start = splitLocation;
        }

        // Always replace the metadata from the original fragment on the
        // first fragment resulting from the split
        copyFragmentMetadata(fragment, item.getFragments().get(i));
      }
    }
  }

  /**
   * Splits and adds feedback to {@link FeedbackItem}s for spannable text contained within this
   * {@link FeedbackItem}
   *
   * @param item The item to process for formatted text.
   */
  public static void addFormattingCharacteristics(FeedbackItem item) {
    for (int i = 0; i < item.getFragments().size(); ++i) {
      final FeedbackFragment fragment = item.getFragments().get(i);
      final CharSequence fragmentText = fragment.getText();
      if (TextUtils.isEmpty(fragmentText) || !(fragmentText instanceof Spannable)) {
        continue;
      }

      Spannable spannable = (Spannable) fragmentText;

      int len = spannable.length();
      int next;
      boolean isFirstFragment = true;
      for (int begin = 0; begin < len; begin = next) {
        next = nextSpanTransition(spannable, begin, len, LocaleSpan.class, TARGET_SPAN_CLASS);

        // CharacterStyle is a superclass of both ClickableSpan(including URLSpan) and LocaleSpan;
        // we want to split by only ClickableSpan and LocaleSpan, but it is OK if we request any
        // CharacterStyle in the list of spans since we ignore the ones that are not
        // ClickableSpan/LocaleSpan.
        CharacterStyle[] spans = spannable.getSpans(begin, next, CharacterStyle.class);
        CharacterStyle chosenSpan = null;
        for (CharacterStyle span : spans) {
          if (span instanceof LocaleSpan) {
            // Prioritize LocaleSpan, quit the loop when a LocaleSpan is detected. Note: If multiple
            // LocaleSpans are attached to the text, first LocaleSpan is given preference.
            chosenSpan = span;
            break;
          } else if ((span instanceof ClickableSpan) || (span instanceof URLSpan)) {
            chosenSpan = span;
          }
          // Ignore other CharacterStyle.
        }
        final FeedbackFragment newFragment;
        CharSequence subString = spannable.subSequence(begin, next);
        boolean isIdentifier =
            SpannableUtils.isWrappedWithTargetSpan(
                subString, SpannableUtils.IdentifierSpan.class, /* shouldTrim= */ true);
        if (isIdentifier) {
          continue;
        }
        if (isFirstFragment) {
          // This is the first new fragment, so we should reuse the old fragment.
          // That way, we'll keep the existing haptic/earcon feedback at the beginning!
          isFirstFragment = false;
          newFragment = fragment;
          newFragment.setText(subString);
        } else {
          // Otherwise, add after the last fragment processed/added.
          newFragment = new FeedbackFragment(subString, /* speechParams= */ null);
          ++i;
          newFragment.setStartIndexInFeedbackItem(begin);
          item.addFragmentAtPosition(newFragment, i);
        }
        if (chosenSpan instanceof LocaleSpan) { // LocaleSpan
          newFragment.setLocale(((LocaleSpan) chosenSpan).getLocale());
        } else if (chosenSpan != null) { // ClickableSpan (including UrlSpan)
          handleClickableSpan(newFragment);
        }
      }
    }
  }

  /**
   * Return the first offset greater than <code>start</code> where a markup object of any class of
   * <code>types</code> begins or ends, or <code>limit</code> if there are no starts or ends greater
   * than <code>start</code> but less than <code>limit</code>.
   */
  private static int nextSpanTransition(
      Spannable spannable, int start, int limit, Class<?>... types) {
    int next = limit;
    for (Class<?> type : types) {
      int currentNext = spannable.nextSpanTransition(start, limit, type);
      if (currentNext < next) {
        next = currentNext;
      }
    }

    return next;
  }

  /**
   * Handles {@link FeedbackFragment} with {@link ClickableSpan} (including {@link URLSpan]}). Adds
   * earcon and pitch information to the fragment.
   *
   * @param fragment The fragment containing {@link ClickableSpan}.
   */
  private static void handleClickableSpan(FeedbackFragment fragment) {
    final Bundle speechParams = new Bundle(Bundle.EMPTY);
    speechParams.putFloat(SpeechParam.PITCH, PITCH_CHANGE_HYPERLINK);
    fragment.setSpeechParams(speechParams);
    fragment.addEarcon(R.raw.hyperlink);
  }

  private static void copyFragmentMetadata(FeedbackFragment from, FeedbackFragment to) {
    to.setSpeechParams(from.getSpeechParams());
    to.setNonSpeechParams(from.getNonSpeechParams());
    for (int id : from.getEarcons()) {
      to.addEarcon(id);
    }

    for (int id : from.getHaptics()) {
      to.addHaptic(id);
    }
  }
}
