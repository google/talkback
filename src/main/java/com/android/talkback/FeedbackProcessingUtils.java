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

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import java.util.Set;

/**
 * Utilities for generating {@link FeedbackItem}s populated with
 * {@link FeedbackFragment}s created according to processing rules.
 */
class FeedbackProcessingUtils {

    /**
     * Utterances must be no longer than maxUtteranceLength for the TTS to be
     * able to handle them properly. Similar limitation imposed by
     * {@link TextToSpeech#getMaxSpeechInputLength()}
     */
    static int maxUtteranceLength;
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            maxUtteranceLength = TextToSpeech.getMaxSpeechInputLength();
        } else {
            maxUtteranceLength = 4000;
        }
    }

    /** The pitch scale factor value to use when announcing hyperlinks. */
    private static final float PITCH_CHANGE_HYPERLINK = 0.95f;

    /** The pitch scale factor value to use when announcing bold text. */
    private static final float PITCH_CHANGE_BOLD = 0.95f;

    /** The pitch scale factor value to use when announcing italic text. */
    private static final float PITCH_CHANGE_ITALIC = 1.05f;

    /**
     * Produces a populated {@link FeedbackItem} based on rules defined within
     * this class. Currently splits utterances into reasonable chunks and adds
     * auditory and speech characteristics for formatting changes in processed
     * text.
     *
     * @param text The text to include
     * @param earcons The earcons to be played when this item is processed
     * @param haptics The haptic patterns to be produced when this item is
     *            processed
     * @param flags The Flags defining the treatment of this item
     * @param speechParams The {@link SpeechController.SpeechParam} parameters to attribute to
     *            the spoken feedback within each fragment in this item.
     * @param nonSpeechParams The {@link Utterance} parameters to attribute to
     *            non-speech feedback for this item.
     * @return a populated {@link FeedbackItem}
     */
    public static FeedbackItem generateFeedbackItemFromInput(Context context, CharSequence text,
            Set<Integer> earcons, Set<Integer> haptics, int flags, int utteranceGroup,
            Bundle speechParams, Bundle nonSpeechParams) {
        final FeedbackItem feedbackItem = new FeedbackItem();
        final FeedbackFragment initialFragment = new FeedbackFragment(
                text, earcons, haptics, speechParams, nonSpeechParams);
        feedbackItem.addFragment(initialFragment);
        feedbackItem.addFlag(flags);
        feedbackItem.setUtteranceGroup(utteranceGroup);

        // Process the FeedbackItem
        addFormattingCharacteristics(feedbackItem);
        cleanupItemText(context, feedbackItem);
        splitLongText(feedbackItem);

        return feedbackItem;
    }

    /**
     * Splits text contained within the {@link FeedbackItem}'s
     * {@link FeedbackFragment}s into fragments containing less than
     * {@link #maxUtteranceLength} characters.
     *
     * @param item The item containing fragments to split.
     */
    // Visible for testing
    static void splitLongText(FeedbackItem item) {
        for (int i = 0; i < item.getFragments().size(); ++i) {
            final FeedbackFragment fragment = item.getFragments().get(i);
            final CharSequence fragmentText = fragment.getText();
            if (TextUtils.isEmpty(fragmentText)) {
                continue;
            }

            if (fragmentText.length() >= maxUtteranceLength) {
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
                    final int fragmentEnd = start + maxUtteranceLength - 1;

                    // TODO(CB): We currently split only on spaces.
                    // Find a better way to do this for languages that don't
                    // use spaces.
                    int splitLocation = TextUtils.lastIndexOf(
                            fragmentText, ' ', start + 1, fragmentEnd);
                    if (splitLocation < 0) {
                        splitLocation = Math.min(fragmentEnd, end);
                    }
                    final CharSequence textSection = TextUtils.substring(
                            fragmentText, start, splitLocation);
                    final FeedbackFragment additionalFragment = new FeedbackFragment(
                            textSection, fragment.getSpeechParams());
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
     * Splits and adds feedback to {@link FeedbackItem}s for spannable text
     * contained within this {@link FeedbackItem}
     *
     * @param item The item to process for formatted text.
     */
    private static void addFormattingCharacteristics(FeedbackItem item) {
        for (int i = 0; i < item.getFragments().size(); ++i) {
            final FeedbackFragment fragment = item.getFragments().get(i);
            final CharSequence fragmentText = fragment.getText();
            if (TextUtils.isEmpty(fragmentText) || !(fragmentText instanceof Spannable)) {
                continue;
            }

            Spannable spannable = (Spannable) fragmentText;
            final Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
            for (Object span : spans) {
                boolean spanHandled = false;
                if (span instanceof URLSpan) {
                    final URLSpan urlSpan = (URLSpan) span;
                    spanHandled = handleUrlSpan(item, fragment, i, spannable, urlSpan);
                } else if (span instanceof StyleSpan) {
                    final StyleSpan styleSpan = (StyleSpan) span;
                    spanHandled = handleStyleSpan(item, fragment, i, spannable, styleSpan);
                }

                if (spanHandled) {
                    // If the span was handled, truncate the handled section
                    // from the spannable source
                    spannable = (Spannable) spannable.subSequence(
                            spannable.getSpanEnd(span), spannable.toString().length());

                    // Adjust iterative position by the number of fragments
                    // added to the item
                    i += 2;
                }
            }
        }
    }

    private static void cleanupItemText(Context context, FeedbackItem item) {
        for (FeedbackFragment fragment : item.getFragments()) {
            if (!TextUtils.isEmpty(fragment.getText())) {
                CharSequence processedText = SpeechCleanupUtils.collapseRepeatedCharacters(
                        context, fragment.getText());
                processedText = SpeechCleanupUtils.cleanUp(context, processedText);
                fragment.setText(processedText);
            }
        }
    }

    /**
     * Handles the splitting of {@link StyleSpan}s into multiple
     * {@link FeedbackFragment}s.
     * <p>
     * NOTE: in the case that this method returns {@code true}, two new
     * {@link FeedbackFragment}s will always be added to the given
     * {@link FeedbackItem}.
     *
     * @param item The item to which new fragments should be added.
     * @param fragment The fragment containing the spannable text to process.
     * @param spannable The spannable text containing the span to process
     * @param span The individual {@link StyleSpan} that represents the span
     * @return {@code true} if processed, {@code false} otherwise
     */
    private static boolean handleUrlSpan(FeedbackItem item, FeedbackFragment fragment,
            int fragmentPosition, Spannable spannable, URLSpan span) {
        final int spanStart = spannable.getSpanStart(span);
        final int spanEnd = spannable.getSpanEnd(span);

        if (spanStart < 0 || spanEnd < 0) {
            return false;
        }

        // Add a fragment for the text and metadata from before the span.
        // Copying this metadata preserves earcons and other speech and
        // non-speech parameters that were originally associated with the
        // initial section of the fragment.
        final FeedbackFragment beforeSpanFragment = new FeedbackFragment(
                spannable.subSequence(0, spanStart), null);
        copyFragmentMetadata(fragment, beforeSpanFragment);
        item.addFragmentAtPosition(beforeSpanFragment, fragmentPosition);

        // Add a fragment for the span and add appropriate feedback and metadata
        // specific to the span.
        FeedbackFragment spanFragment = new FeedbackFragment(
                spannable.subSequence(spanStart, spanEnd), null);
        final Bundle speechParams = new Bundle(Bundle.EMPTY);
        speechParams.putFloat(SpeechController.SpeechParam.PITCH, PITCH_CHANGE_HYPERLINK);
        spanFragment.setSpeechParams(speechParams);
        spanFragment.addEarcon(R.raw.hyperlink);
        item.addFragmentAtPosition(spanFragment, fragmentPosition + 1);

        // Use the existing fragment to hold any remaining text after the span.
        // We clear the metadata associated with this fragment as it's now
        // included in its proper location within beforeSpanFragment
        fragment.setText(spannable.subSequence(spanEnd, spannable.length()));
        clearFragmentMetadata(fragment);

        return true;
    }

    /**
     * Handles the splitting of {@link URLSpan}s into multiple
     * {@link FeedbackFragment}s.
     * <p>
     * NOTE: in the case that this method returns {@code true}, two new
     * {@link FeedbackFragment}s will always be added to the given
     * {@link FeedbackItem}.
     *
     * @param item The item to which new fragments should be added.
     * @param fragment The fragment containing the spannable text to process.
     * @param spannable The spannable text containing the span to process
     * @param span The individual {@link StyleSpan} that represents the span
     * @return {@code true} if processed, {@code false} otherwise
     */
    private static boolean handleStyleSpan(FeedbackItem item, FeedbackFragment fragment,
            int fragmentPosition, Spannable spannable, StyleSpan span) {
        final int spanStart = spannable.getSpanStart(span);
        final int spanEnd = spannable.getSpanEnd(span);
        final int style = span.getStyle();

        if (spanStart < 0 || spanEnd < 0) {
            return false;
        }

        final int earconId;
        final float voicePitch;
        switch (style) {
            case Typeface.BOLD:
                voicePitch = PITCH_CHANGE_BOLD;
                earconId = R.raw.bold;
                break;
            case Typeface.ITALIC:
                voicePitch = PITCH_CHANGE_ITALIC;
                earconId = R.raw.italic;
                break;
            default:
                return false;
        }

        // Add a fragment for the text and metadata from before the span.
        // Copying this metadata preserves earcons and other speech and
        // non-speech parameters that were originally associated with the
        // initial section of the fragment.
        final FeedbackFragment beforeSpanFragment = new FeedbackFragment(
                spannable.subSequence(0, spanStart), null);
        copyFragmentMetadata(fragment, beforeSpanFragment);
        item.addFragmentAtPosition(beforeSpanFragment, fragmentPosition);

        // Add a fragment for the span and add appropriate feedback and metadata
        // specific to the span.
        FeedbackFragment spanFragment = new FeedbackFragment(
                spannable.subSequence(spanStart, spanEnd), null);
        final Bundle speechParams = new Bundle(Bundle.EMPTY);
        speechParams.putFloat(SpeechController.SpeechParam.PITCH, voicePitch);
        spanFragment.setSpeechParams(speechParams);
        spanFragment.addEarcon(earconId);
        item.addFragmentAtPosition(spanFragment, fragmentPosition + 1);

        // Use the existing fragment to hold any remaining text after the span.
        // We clear the metadata associated with this fragment as it's now
        // included in its proper location within beforeSpanFragment
        fragment.setText(spannable.subSequence(spanEnd, spannable.length()));
        clearFragmentMetadata(fragment);

        return true;
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

    private static void clearFragmentMetadata(FeedbackFragment fragment) {
        fragment.setSpeechParams(new Bundle());
        fragment.setNonSpeechParams(new Bundle());
        fragment.clearAllEarcons();
        fragment.clearAllHaptics();
    }
}
