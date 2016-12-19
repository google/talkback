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
import android.text.style.CharacterStyle;
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

                    // TODO: We currently split only on spaces.
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
    static void addFormattingCharacteristics(FeedbackItem item) {
        for (int i = 0; i < item.getFragments().size(); ++i) {
            final FeedbackFragment fragment = item.getFragments().get(i);
            final CharSequence fragmentText = fragment.getText();
            if (TextUtils.isEmpty(fragmentText) || !(fragmentText instanceof Spannable)) {
                continue;
            }

            Spannable spannable = (Spannable) fragmentText;

            int len = spannable.length();
            int next;
            for (int begin = 0; begin < len; begin = next) {
                // CharacterStyle is a superclass of both URLSpan and StyleSpan; we want to split by
                // only URLSpan/StyleSpan, but it is OK if we request any CharacterStyle in the list
                // of spans since we ignore the ones that are not URLSpan/StyleSpan.
                next = nextSpanTransition(spannable, begin, len, URLSpan.class, StyleSpan.class);
                CharacterStyle[] spans = spannable.getSpans(begin, next, CharacterStyle.class);

                // Since we add earcons and change pitch for URLs and styling, we can only handle
                // one type of span per block. URLs seem more important, so they get priority.
                CharacterStyle chosenSpan = null;
                for (CharacterStyle span : spans) {
                    if (span instanceof URLSpan) {
                        chosenSpan = span;
                    } else if (span instanceof StyleSpan && !(chosenSpan instanceof URLSpan)) {
                        chosenSpan = span;
                    }
                }

                final FeedbackFragment newFragment;
                if (begin == 0) {
                    // This is the first new fragment, so we should reuse the old fragment.
                    // That way, we'll keep the existing haptic/earcon feedback at the beginning!
                    newFragment = fragment;
                    newFragment.setText(spannable.subSequence(0, next));
                } else {
                    // Otherwise, add after the last fragment processed/added.
                    newFragment = new FeedbackFragment(spannable.subSequence(begin, next), null);

                    ++i;
                    item.addFragmentAtPosition(newFragment, i);
                }

                if (chosenSpan instanceof URLSpan) {
                    handleUrlSpan(newFragment);
                } else if (chosenSpan instanceof StyleSpan) {
                    handleStyleSpan(newFragment, (StyleSpan) chosenSpan);
                }
            }
        }
    }

    private static int nextSpanTransition(Spannable spannable, int start, int limit,
            Class... types) {
        int next = limit;
        for (Class type : types) {
            int currentNext = spannable.nextSpanTransition(start, limit, type);
            if (currentNext < next) {
                next = currentNext;
            }
        }

        return next;
    }

    /**
     * Handles the splitting of {@link StyleSpan}s into multiple
     * {@link FeedbackFragment}s.
     *
     * @param fragment The fragment containing the spannable text to process.
     */
    private static void handleUrlSpan(FeedbackFragment fragment) {
        final Bundle speechParams = new Bundle(Bundle.EMPTY);
        speechParams.putFloat(SpeechController.SpeechParam.PITCH, PITCH_CHANGE_HYPERLINK);
        fragment.setSpeechParams(speechParams);
        fragment.addEarcon(R.raw.hyperlink);
    }

    /**
     * Handles the splitting of {@link URLSpan}s into multiple
     * {@link FeedbackFragment}s.
     *
     * @param fragment The fragment containing the spannable text to process.
     * @param span The individual {@link StyleSpan} that represents the span
     */
    private static void handleStyleSpan(FeedbackFragment fragment, StyleSpan span) {
        final int style = span.getStyle();

        final int earconId;
        final float voicePitch;
        switch (style) {
            case Typeface.BOLD:
            case Typeface.BOLD_ITALIC:
                voicePitch = PITCH_CHANGE_BOLD;
                earconId = R.raw.bold;
                break;
            case Typeface.ITALIC:
                voicePitch = PITCH_CHANGE_ITALIC;
                earconId = R.raw.italic;
                break;
            default:
                return;
        }

        final Bundle speechParams = new Bundle(Bundle.EMPTY);
        speechParams.putFloat(SpeechController.SpeechParam.PITCH, voicePitch);
        fragment.setSpeechParams(speechParams);
        fragment.addEarcon(earconId);
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
