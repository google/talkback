/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.utils;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

public class StringBuilderUtils {
    /**
     * Breaking separator inserted between text, intended to make TTS pause an
     * appropriate amount. Using a period breaks pronunciation of street
     * abbreviations, and using a new line doesn't work in eSpeak.
     */
    public static final String DEFAULT_BREAKING_SEPARATOR = ", ";

    /**
     * Non-breaking separator inserted between text. Used when text already ends
     * with some sort of breaking separator or non-alphanumeric character.
     */
    public static final String DEFAULT_SEPARATOR = " ";

    /**
     * The hex alphabet.
     */
    private static final char[] HEX_ALPHABET = "0123456789abcdef".toCharArray();

    /**
     * Generates the aggregate text from a list of {@link CharSequence}s,
     * separating as necessary.
     *
     * @param textList The list of text to process.
     * @return The separated aggregate text, or null if no text was appended.
     */
    public static CharSequence getAggregateText(List<CharSequence> textList) {
        final CharSequence aggregateText;
        if (textList == null || textList.isEmpty()) {
            aggregateText = null;
        } else {
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            for (CharSequence text : textList) {
                appendWithSeparator(builder, text);
            }

            aggregateText = builder;
        }

        return aggregateText;
    }

    /**
     * Appends CharSequence representations of the specified arguments to a
     * {@link SpannableStringBuilder}, creating one if the supplied builder is
     * {@code null}.
     *
     * @param builder An existing {@link SpannableStringBuilder}, or {@code null} to create one.
     * @param args    The objects to append to the builder.
     * @return A builder with the specified objects appended.
     */
    public static SpannableStringBuilder appendWithSeparator(
            SpannableStringBuilder builder, CharSequence... args) {
        if (builder == null) {
            builder = new SpannableStringBuilder();
        }

        for (CharSequence arg : args) {
            if (arg == null) {
                continue;
            }

            if (arg.toString().length() == 0) {
                continue;
            }

            if (builder.length() > 0) {
                if (needsBreakingSeparator(builder)) {
                    builder.append(DEFAULT_BREAKING_SEPARATOR);
                } else {
                    builder.append(DEFAULT_SEPARATOR);
                }
            }

            builder.append(arg);
        }

        return builder;
    }

    /**
     * Create spannable from text that includes some CharSequence. If the CharSequence has any spans
     * they would be copied to result spannable
     * @param text - some text that potentially contains CharSequence template
     * @param innerTemplate - CharSequence that is supposed but not necessary to be Spanned.
     *                      If it is Spanned the spans are copied to result Spannable
     * @return Spannable object that contains incoming text and spans from innerTemplate
     */
    public static Spannable createSpannableFromTextWithTemplate(String text,
                                                                CharSequence innerTemplate) {
        SpannableString result = new SpannableString(text);
        if(innerTemplate instanceof Spanned) {
            int index = text.indexOf(innerTemplate.toString());
            if(index >= 0) {
                copySpans(result, (Spanned) innerTemplate, index);
            }
        }

        return result;
    }

    /**
     * Utility that copies spans from fromSpan to toSpan
     * @param toSpan - Spannable that is supposed to contain fromSpan.
     * @param fromSpan - Spannable that could contain spans that would be copied to toSpan
     * @param startIndex - Starting index of occurrence fromSpan in toSpan
     */
    private static void copySpans(Spannable toSpan, Spanned fromSpan, int startIndex) {
        if (startIndex < 0 || startIndex >= toSpan.length()) {
            LogUtils.log(StringBuilderUtils.class, Log.ERROR, "startIndex parameter (" +
                    startIndex + ") is out of toSpan length " + toSpan.length());
            return;
        }

        Object[] spans = fromSpan.getSpans(0, fromSpan.length(), Object.class);
        if (spans != null && spans.length > 0) {
            for (Object span : spans) {
                int spanStartIndex = fromSpan.getSpanStart(span);
                int spanEndIndex = fromSpan.getSpanEnd(span);
                if(spanStartIndex >= spanEndIndex) {
                    continue;
                }

                int spanFlags = fromSpan.getSpanFlags(span);
                toSpan.setSpan(span, startIndex + spanStartIndex, startIndex + spanEndIndex, spanFlags);
            }
        }
    }

    /**
     * Returns whether the text needs a breaking separator (e.g. a period
     * followed by a space) appended before more text is appended.
     * <p>
     * If text ends with a letter or digit (according to the current locale)
     * then this method will return {@code true}.
     */
    private static boolean needsBreakingSeparator(CharSequence text) {
        return !TextUtils.isEmpty(text)
                && Character.isLetterOrDigit(text.charAt(text.length() - 1));
    }

    /**
     * Convert a byte array to a hex-encoded string.
     *
     * @param bytes The byte array of data to convert
     * @return The hex encoding of {@code bytes}, or null if {@code bytes} was null
     */
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        final StringBuilder hex = new StringBuilder(bytes.length * 2);
        int nibble1, nibble2;
        for (byte b : bytes) {
            nibble1 = (b >>> 4) & 0xf;
            nibble2 = b & 0xf;
            hex.append(HEX_ALPHABET[nibble1]);
            hex.append(HEX_ALPHABET[nibble2]);
        }

        return hex.toString();
    }
}
