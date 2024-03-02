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

import android.annotation.TargetApi;
import android.content.Context;
import android.icu.text.BreakIterator;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.LocaleSpan;
import android.text.style.TtsSpan;
import android.text.style.URLSpan;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.accessibility.utils.output.FailoverTextToSpeech.SpeechParam;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utilities for generating {@link FeedbackItem}s populated with {@link FeedbackFragment}s created
 * according to processing rules.
 */
public class FeedbackProcessingUtils {
  private static final String TAG = "FeedbackProcessingUtils";

  /**
   * Utterances must be no longer than MAX_UTTERANCE_LENGTH for the TTS to be able to handle them
   * properly. Similar limitation imposed by {@link TextToSpeech#getMaxSpeechInputLength()}
   */
  public static final int MAX_UTTERANCE_LENGTH = TextToSpeech.getMaxSpeechInputLength();

  /** The pitch scale factor value to use when announcing hyperlinks. */
  private static final float PITCH_CHANGE_HYPERLINK = 0.95f;

  // Which symbols are sentence delimiter? Only new-line symbol is considered as a delimiter now.
  private static final Pattern CHUNK_DELIMITER = Pattern.compile("\n");
  // The feedback item chunking is taking place only when the fragment size is greater
  // than this value.
  private static final int MIN_CHUNK_LENGTH = 35;

  private static boolean aggressiveChunking = false;

  public static void enableAggressiveChunking() {
    aggressiveChunking = true;
  }

  /**
   * Produces a populated {@link FeedbackItem} based on rules defined within this class. Currently
   * splits utterances into reasonable chunks and adds auditory and speech characteristics for
   * formatting changes in processed text.
   *
   * @param text The text to include
   * @param usePunctuation whether the feature speak-punctuation-symbol is activated
   * @param removeUnnecessarySpans whether remove unnecessary spans or not
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
      boolean usePunctuation,
      boolean removeUnnecessarySpans,
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
    if (!usePunctuation || !aggressiveChunking) {
      breakSentence(feedbackItem);
    }
    addFormattingCharacteristics(context, feedbackItem, usePunctuation, removeUnnecessarySpans);
    if (usePunctuation && aggressiveChunking) {
      aggressiveChunking(feedbackItem);
    }
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

  /** Collect the spans inside the SpannableString with span index and flag. */
  private static class SpanAndRange {
    final int spanStart;
    final int spanEnd;
    final int spanFlag;
    final Object span;

    SpanAndRange(Object span, int spanStart, int spanEnd, int spanFlag) {
      this.span = span;
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
      this.spanFlag = spanFlag;
    }
  }

  private static void splitSpans(
      SpannableString spannableString,
      List<SpanAndRange> spanAndRanges,
      int textStart,
      int textEnd) {
    for (SpanAndRange spanAndRange : spanAndRanges) {
      int spanStart = spanAndRange.spanStart;
      int spanEnd = spanAndRange.spanEnd;
      if (spanEnd <= textStart || spanStart >= textEnd) {
        continue;
      }
      int newStart = Math.max(spanStart, textStart) - textStart;
      int newEnd = Math.min(spanEnd, textEnd) - textStart;
      spannableString.setSpan(spanAndRange.span, newStart, newEnd, spanAndRange.spanFlag);
    }
  }

  private static Locale getPreferredLocale(FeedbackFragment fragment) {
    // Need to add new method from speech controller to access the default TTS locale.
    return (fragment.getLocale() != null) ? fragment.getLocale() : Locale.getDefault();
  }

  private static List<SpanAndRange> formInRangeSpans(CharSequence text, int length) {
    Object[] spans = ((Spanned) text).getSpans(0, length, Object.class);

    List<SpanAndRange> spanAndRanges = new ArrayList<>();
    for (Object span : spans) {
      SpanAndRange spanAndRange =
          new SpanAndRange(
              span,
              ((Spanned) text).getSpanStart(span),
              ((Spanned) text).getSpanEnd(span),
              ((Spanned) text).getSpanFlags(span));
      spanAndRanges.add(spanAndRange);
    }
    return spanAndRanges;
  }

  /**
   * This chunking occurs when the [Speaking punctuation symbols] feature is on.
   *
   * <p>Note In general, it breaks/splits long sentence into sub-sentences based on the {@link
   * BreakIterator#getSentenceInstance}
   *
   * <ul>
   *   Two exception cases are
   *   <li>1. The fragment size is smaller than the predefined value (MIN_CHUNK_LENGTH).
   *   <li>2. When the split position is contained in a spans.
   * </ul>
   */
  @TargetApi(Build.VERSION_CODES.Q)
  private static void aggressiveChunking(FeedbackItem item) {
    // Position to insert the split fragment is always [i+1].
    boolean chunked = false;
    for (int i = 0; i < item.getFragments().size(); ++i, chunked = false) {
      FeedbackFragment fragment = item.getFragments().get(i);
      final CharSequence fragmentText = fragment.getText();
      int fragmentLength = fragmentText.length();
      if (TextUtils.isEmpty(fragmentText) || fragmentLength < MIN_CHUNK_LENGTH) {
        continue;
      }

      List<SpanAndRange> spanAndRanges = formInRangeSpans(fragmentText, fragmentLength);

      BreakIterator boundary = BreakIterator.getSentenceInstance(getPreferredLocale(fragment));
      boundary.setText(fragmentText);
      int end = boundary.next();
      int startOfUnsplitText = 0;
      while (end != BreakIterator.DONE) {
        if (!splitFeasible(spanAndRanges, end)) {
          end = boundary.next();
          continue;
        }
        splitChunk(item, fragment, spanAndRanges, startOfUnsplitText, end, i + 1);
        startOfUnsplitText = end;
        chunked = true;
        i++;
        end = boundary.next();
      }
      if (chunked) {
        // The chunking really happens.
        if (startOfUnsplitText < fragmentLength) {
          // The remaining text after the last sentence break.
          splitChunk(item, fragment, spanAndRanges, startOfUnsplitText, fragmentLength, i + 1);
          i++;
        }
        item.removeFragment(fragment);
        i--;
        // Always replace the metadata from the original fragment on the
        // first fragment resulting from the split
        copyFragmentMetadata(fragment, item.getFragments().get(0));
      }
    }
  }

  /**
   * Splits text delimited by the pattern of punctuation into sentence. For now, if any spans are
   * found in the original text, do not split it.
   *
   * @param item The item containing fragments to split.
   */
  public static void breakSentence(FeedbackItem item) {
    List<FeedbackFragment> fragments = item.getFragments();
    if (fragments.size() != 1) {
      LogUtils.e(TAG, "It only supports to handle the feedback item with single fragment.");
      return;
    }

    FeedbackFragment fragment = item.getFragments().get(0);
    final CharSequence fragmentText = fragment.getText();
    if (TextUtils.isEmpty(fragmentText) || fragmentText.length() < MIN_CHUNK_LENGTH) {
      return;
    }

    List<SpanAndRange> spanAndRanges = formInRangeSpans(fragmentText, fragmentText.length());

    Matcher matcher = CHUNK_DELIMITER.matcher(fragmentText);
    int startOfUnsplitText = 0;
    int chunkIndex = 1;
    while (matcher.find()) {
      int end = matcher.end();
      if (!splitFeasible(spanAndRanges, end)) {
        continue;
      }
      splitChunk(item, fragment, spanAndRanges, startOfUnsplitText, end, chunkIndex);
      startOfUnsplitText = end;
      chunkIndex++;
    }
    if (chunkIndex > 1) {
      if (startOfUnsplitText < fragmentText.length()) {
        // The remaining text after the last sentence break.
        splitChunk(
            item, fragment, spanAndRanges, startOfUnsplitText, fragmentText.length(), chunkIndex);
      }
      item.removeFragment(fragment);
      // Always replace the metadata from the original fragment on the
      // first fragment resulting from the split
      copyFragmentMetadata(fragment, item.getFragments().get(0));
    }
  }

  private static void splitChunk(
      FeedbackItem item,
      FeedbackFragment fragment,
      List<SpanAndRange> spanAndRanges,
      int startOfUnsplitText,
      int chunkEnd,
      int chunkIndex) {
    final CharSequence fragmentText = fragment.getText();
    SpannableString spannableString =
        new SpannableString(fragmentText.subSequence(startOfUnsplitText, chunkEnd));
    splitSpans(spannableString, spanAndRanges, startOfUnsplitText, chunkEnd);
    final FeedbackFragment additionalFragment =
        new FeedbackFragment(spannableString, fragment.getSpeechParams());
    additionalFragment.setLocale(fragment.getLocale());
    item.addFragmentAtPosition(additionalFragment, chunkIndex);
  }

  /**
   * @param spanAndRanges: the collected spans in the original text.
   * @param textEnd: end index of the sentence.
   * @return true when it's recommended to break the sentence.
   */
  private static boolean splitFeasible(List<SpanAndRange> spanAndRanges, int textEnd) {
    for (SpanAndRange spanAndRange : spanAndRanges) {
      if (spanAndRange.spanStart < textEnd && spanAndRange.spanEnd > textEnd) {
        return false;
      }
    }
    return true;
  }

  /**
   * Splits and adds feedback to {@link FeedbackItem}s for spannable text contained within this
   * {@link FeedbackItem}
   *
   * @param context The caller's context.
   * @param item The item to process for formatted text.
   * @param usePunctuation Speak punctuation.
   */
  @VisibleForTesting
  static void addFormattingCharacteristics(
      Context context, FeedbackItem item, boolean usePunctuation, boolean removeUnnecessarySpans) {
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
        // TTS would not speak punctuation normally. However, when a punctuation appears alone, TTS
        // has to speak it in some form(b/233322397). Here we append the trailing punctuation to the
        // clickable span to avoid this.
        if (!usePunctuation
            && next < len
            && SpeechCleanupUtils.characterToName(context, fragmentText.charAt(next)) != null
            && !SpannableUtils.isWrappedWithTargetSpan(
                fragmentText.subSequence(next, next + 1),
                SpannableUtils.IdentifierSpan.class,
                false)) {
          next += 1;
        }
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
        if (removeUnnecessarySpans) {
          subString = removeUnnecessarySpansForTts(subString);
        }
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

  private static CharSequence removeUnnecessarySpansForTts(CharSequence fragmentText) {
    Spannable spannable = (Spannable) fragmentText;
    ParcelableSpan[] spans = spannable.getSpans(0, spannable.length(), ParcelableSpan.class);
    for (ParcelableSpan span : spans) {
      // LocaleSpan will also be removed, since it will be handled by
      // FeedbackFragment#setLocale(Locale).
      if (span instanceof TtsSpan) {
        // TtsSpan is necessary for TTS.
      } else {
        spannable.removeSpan(span);
      }
    }

    return spannable;
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
