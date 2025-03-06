/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.talkback.compositor;

import static com.google.common.base.Ascii.toLowerCase;
import static java.lang.Character.isUpperCase;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utils class that provides common methods for compositor to handle events and TTS output. */
public final class CompositorUtils {

  // Parameters used in join statement.
  public static final boolean PRUNE_EMPTY = true;
  private static final String SEPARATOR_COMMA = ", ";
  private static final String SEPARATOR_PERIOD = ". ";
  private static String separator = SEPARATOR_COMMA;

  private CompositorUtils() {}

  public static String getSeparator() {
    return separator;
  }

  public static void usePeriodAsSeparator() {
    separator = SEPARATOR_PERIOD;
  }

  public static CharSequence joinCharSequences(@Nullable CharSequence... list) {
    List<CharSequence> arrayList = new ArrayList<>(list.length);
    for (CharSequence charSequence : list) {
      if (charSequence != null) {
        arrayList.add(charSequence);
      }
    }
    return joinCharSequences(arrayList, separator, PRUNE_EMPTY);
  }

  public static CharSequence joinCharSequences(
      List<CharSequence> values, @Nullable CharSequence separator, boolean pruneEmpty) {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    boolean first = true;
    for (CharSequence value : values) {
      if (!pruneEmpty || !TextUtils.isEmpty(value)) {
        if (separator != null) {
          if (first) {
            first = false;
          } else {
            // We have to wrap each separator with a different span, because a single span object
            // can only be used once in a CharSequence.
            builder.append(SpannableUtils.wrapWithIdentifierSpan(separator));
          }
        }
        builder.append(value);
      }
    }
    return builder;
  }

  /** Joins the unique char sequence texts of the input values to one char sequence. */
  public static CharSequence dedupJoin(
      CharSequence value1, CharSequence value2, CharSequence value3) {
    CharSequence[] values = {value1, value2, value3};
    SpannableStringBuilder builder = new SpannableStringBuilder();
    HashSet<String> uniqueValues = new HashSet<>();
    boolean first = true;
    for (CharSequence value : values) {
      if (TextUtils.isEmpty(value)) {
        continue;
      }
      String lvalue = toLowerCase(value.toString());
      if (uniqueValues.contains(lvalue)) {
        continue;
      }
      uniqueValues.add(lvalue);
      if (first) {
        first = false;
      } else {
        // We have to wrap each separator with a different span, because a single span object
        // can only be used once in a CharSequence. An IdentifierSpan indicates the text is a
        // separator, and the text will not be announced.
        builder.append(SpannableUtils.wrapWithIdentifierSpan(separator));
      }
      builder.append(value);
    }
    return builder;
  }

  public static CharSequence conditionalPrepend(
      CharSequence prependText, CharSequence conditionalText, CharSequence separator) {
    if (TextUtils.isEmpty(conditionalText)) {
      return "";
    }
    if (TextUtils.isEmpty(prependText)) {
      return conditionalText;
    }
    SpannableStringBuilder result = new SpannableStringBuilder();
    result
        .append(prependText)
        .append(SpannableUtils.wrapWithIdentifierSpan(separator))
        .append(conditionalText);
    return result;
  }

  public static CharSequence conditionalAppend(
      CharSequence conditionalText, CharSequence appendText, CharSequence separator) {
    if (TextUtils.isEmpty(conditionalText)) {
      return "";
    }
    if (TextUtils.isEmpty(appendText)) {
      return conditionalText;
    }
    SpannableStringBuilder result = new SpannableStringBuilder();
    result
        .append(conditionalText)
        .append(SpannableUtils.wrapWithIdentifierSpan(separator))
        .append(appendText);
    return result;
  }

  /** Returns the text that is prepended capital if needed. */
  public static CharSequence prependCapital(CharSequence text, Context context) {
    if (TextUtils.isEmpty(text)) {
      return "";
    } else if (text.length() == 1 && isUpperCase(text.charAt(0))) {
      return context.getString(com.google.android.accessibility.utils.R.string.template_capital_letter, text.charAt(0));
    } else {
      return text;
    }
  }

  /** Returns the cleanup text that reduces the repeated characters and symbols. */
  public static CharSequence getCleanupString(CharSequence text, Context context) {
    if (TextUtils.isEmpty(text)) {
      return "";
    } else {
      return SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(context, text);
    }
  }
}
