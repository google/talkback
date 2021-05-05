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

package com.google.android.accessibility.utils;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Frequently used functions for concatenating text. */
public class StringBuilderUtils {

  private static final String TAG = "StringBuildingUtils";

  ////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /**
   * Breaking separator inserted between text, intended to make TTS pause an appropriate amount.
   * Using a period breaks pronunciation of street abbreviations, and using a new line doesn't work
   * in eSpeak.
   */
  public static final String DEFAULT_BREAKING_SEPARATOR = ", ";

  /**
   * Non-breaking separator inserted between text. Used when text already ends with some sort of
   * breaking separator or non-alphanumeric character.
   */
  public static final String DEFAULT_SEPARATOR = " ";

  /** The hex alphabet. */
  private static final char[] HEX_ALPHABET = "0123456789abcdef".toCharArray();

  ////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public static String repeatChar(char c, int times) {
    char[] chars = new char[times];
    Arrays.fill(chars, c);
    return new String(chars);
  }

  /** Return labeled field-value, only if field-value is not null. */
  public static String optionalField(String fieldName, @Nullable Object fieldValue) {
    return (fieldValue == null) ? "" : String.format("%s=%s", fieldName, fieldValue.toString());
  }

  /** Return labeled delimited field-value, only if field-value is not null. */
  public static String optionalSubObj(String fieldName, @Nullable Object fieldValue) {
    return (fieldValue == null) ? "" : String.format("%s= %s", fieldName, fieldValue.toString());
  }

  /** Return labeled quoted field-value, only if field-value is not null. */
  public static String optionalText(String fieldName, @Nullable CharSequence fieldValue) {
    return (fieldValue == null) ? "" : String.format("%s=\"%s\"", fieldName, fieldValue);
  }

  /** Return labeled field-value, only if field-value is not default. */
  public static String optionalInt(String fieldName, int fieldValue, int defaultValue) {
    return (fieldValue == defaultValue) ? "" : String.format("%s=%s", fieldName, fieldValue);
  }

  /** Return labeled field-value, only if field-value is not default. */
  public static String optionalInt(String fieldName, long fieldValue, long defaultValue) {
    return (fieldValue == defaultValue) ? "" : String.format("%s=%s", fieldName, fieldValue);
  }

  /** Return field-tag, only if field-value is true. */
  public static String optionalTag(String tagName, boolean tagValue) {
    return tagValue ? tagName : "";
  }

  public static String joinFields(String... strings) {
    StringBuilder builder = new StringBuilder();
    for (String s : strings) {
      if (s != null && !s.equals("")) {
        builder.append(s);
        builder.append(" ");
      }
    }
    return builder.toString();
  }

  /**
   * Generates the aggregate text from a list of {@link CharSequence}s, separating as necessary.
   *
   * @param textList The list of text to process.
   * @return The separated aggregate text, or null if no text was appended.
   */
  public static @Nullable CharSequence getAggregateText(List<CharSequence> textList) {
    if (textList == null || textList.isEmpty()) {
      return null;
    } else {
      SpannableStringBuilder builder = new SpannableStringBuilder();
      for (CharSequence text : textList) {
        appendWithSeparator(builder, text);
      }

      return builder;
    }
  }

  /**
   * Appends CharSequence representations of the specified arguments to a {@link
   * SpannableStringBuilder}, creating one if the supplied builder is {@code null}. A separator will
   * be inserted between each of the arguments.
   *
   * @param builder An existing {@link SpannableStringBuilder}, or {@code null} to create one.
   * @param args The objects to append to the builder.
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
   * Appends CharSequence representations of the specified arguments to a {@link
   * SpannableStringBuilder}, creating one if the supplied builder is {@code null}. A separator will
   * be inserted before the first non-{@code null} argument, but additional separators will not be
   * inserted between the following elements.
   *
   * @param builder An existing {@link SpannableStringBuilder}, or {@code null} to create one.
   * @param args The objects to append to the builder.
   * @return A builder with the specified objects appended.
   */
  public static SpannableStringBuilder append(
      SpannableStringBuilder builder, CharSequence... args) {
    if (builder == null) {
      builder = new SpannableStringBuilder();
    }

    boolean didAppend = false;
    for (CharSequence arg : args) {
      if (arg == null) {
        continue;
      }

      if (arg.toString().length() == 0) {
        continue;
      }

      if (builder.length() > 0) {
        if (!didAppend && needsBreakingSeparator(builder)) {
          builder.append(DEFAULT_BREAKING_SEPARATOR);
        } else {
          builder.append(DEFAULT_SEPARATOR);
        }
      }

      builder.append(arg);
      didAppend = true;
    }

    return builder;
  }

  /**
   * Returns whether the text needs a breaking separator (e.g. a period followed by a space)
   * appended before more text is appended.
   *
   * <p>If text ends with a letter or digit (according to the current locale) then this method will
   * return {@code true}.
   */
  private static boolean needsBreakingSeparator(CharSequence text) {
    return !TextUtils.isEmpty(text) && Character.isLetterOrDigit(text.charAt(text.length() - 1));
  }

  /**
   * Convert a byte array to a hex-encoded string.
   *
   * @param bytes The byte array of data to convert
   * @return The hex encoding of {@code bytes}, or null if {@code bytes} was null
   */
  public static @Nullable String bytesToHexString(byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    final StringBuilder hex = new StringBuilder(bytes.length * 2);
    int nibble1;
    int nibble2;
    for (byte b : bytes) {
      nibble1 = (b >>> 4) & 0xf;
      nibble2 = b & 0xf;
      hex.append(HEX_ALPHABET[nibble1]);
      hex.append(HEX_ALPHABET[nibble2]);
    }

    return hex.toString();
  }
}
