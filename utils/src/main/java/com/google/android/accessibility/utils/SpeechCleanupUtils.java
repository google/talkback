/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.utils;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.SparseIntArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

/** Utilities for cleaning up speech text. */
public class SpeechCleanupUtils {
  /** The regular expression used to match consecutive identical characters */
  // Double escaping of regex characters is required. "\\1" refers to the
  // first capturing group between the outer nesting of "[]"s and "{2,}"
  // refers to two or more additional repetitions thereof.
  private static final String CONSECUTIVE_CHARACTER_REGEX =
      "([\\-\\\\/|!@#$%^&*\\(\\)=_+\\[\\]\\{\\}.?;'\":<>\\u2022])\\1{2,}";

  /** The Pattern used to match consecutive identical characters */
  private static final Pattern CONSECUTIVE_CHARACTER_PATTERN =
      Pattern.compile(CONSECUTIVE_CHARACTER_REGEX);

  /** Map containing string to speech conversions. */
  private static final SparseIntArray UNICODE_MAP = new SparseIntArray();

  static {
    UNICODE_MAP.put('&', R.string.symbol_ampersand);
    UNICODE_MAP.put('<', R.string.symbol_angle_bracket_left);
    UNICODE_MAP.put('>', R.string.symbol_angle_bracket_right);
    UNICODE_MAP.put('\'', R.string.symbol_apostrophe);
    UNICODE_MAP.put('*', R.string.symbol_asterisk);
    UNICODE_MAP.put('@', R.string.symbol_at_sign);
    UNICODE_MAP.put('\\', R.string.symbol_backslash);
    UNICODE_MAP.put('\u2022', R.string.symbol_bullet);
    UNICODE_MAP.put('^', R.string.symbol_caret);
    UNICODE_MAP.put('¢', R.string.symbol_cent);
    UNICODE_MAP.put(':', R.string.symbol_colon);
    UNICODE_MAP.put(',', R.string.symbol_comma);
    UNICODE_MAP.put('©', R.string.symbol_copyright);
    UNICODE_MAP.put('{', R.string.symbol_curly_bracket_left);
    UNICODE_MAP.put('}', R.string.symbol_curly_bracket_right);
    UNICODE_MAP.put('°', R.string.symbol_degree);
    UNICODE_MAP.put('\u00F7', R.string.symbol_division);
    UNICODE_MAP.put('$', R.string.symbol_dollar_sign);
    UNICODE_MAP.put('…', R.string.symbol_ellipsis);
    UNICODE_MAP.put('\u2014', R.string.symbol_em_dash);
    UNICODE_MAP.put('\u2013', R.string.symbol_en_dash);
    UNICODE_MAP.put('€', R.string.symbol_euro);
    UNICODE_MAP.put('!', R.string.symbol_exclamation_mark);
    UNICODE_MAP.put('`', R.string.symbol_grave_accent);
    UNICODE_MAP.put('-', R.string.symbol_hyphen_minus);
    UNICODE_MAP.put('„', R.string.symbol_low_double_quote);
    UNICODE_MAP.put('\u00D7', R.string.symbol_multiplication);
    UNICODE_MAP.put('\n', R.string.symbol_new_line);
    UNICODE_MAP.put('¶', R.string.symbol_paragraph_mark);
    UNICODE_MAP.put('(', R.string.symbol_parenthesis_left);
    UNICODE_MAP.put(')', R.string.symbol_parenthesis_right);
    UNICODE_MAP.put('%', R.string.symbol_percent);
    UNICODE_MAP.put('.', R.string.symbol_period);
    UNICODE_MAP.put('π', R.string.symbol_pi);
    UNICODE_MAP.put('#', R.string.symbol_pound);
    UNICODE_MAP.put('£', R.string.symbol_pound_sterling);
    UNICODE_MAP.put('?', R.string.symbol_question_mark);
    UNICODE_MAP.put('"', R.string.symbol_quotation_mark);
    UNICODE_MAP.put('®', R.string.symbol_registered_trademark);
    UNICODE_MAP.put(';', R.string.symbol_semicolon);
    UNICODE_MAP.put('/', R.string.symbol_slash);
    UNICODE_MAP.put(' ', R.string.symbol_space);
    UNICODE_MAP.put('[', R.string.symbol_square_bracket_left);
    UNICODE_MAP.put(']', R.string.symbol_square_bracket_right);
    UNICODE_MAP.put('√', R.string.symbol_square_root);
    UNICODE_MAP.put('™', R.string.symbol_trademark);
    UNICODE_MAP.put('_', R.string.symbol_underscore);
    UNICODE_MAP.put('|', R.string.symbol_vertical_bar);
    UNICODE_MAP.put('\u00a5', R.string.symbol_yen);
    UNICODE_MAP.put('\u00ac', R.string.symbol_not_sign);
    UNICODE_MAP.put('\u00a6', R.string.symbol_broken_bar);
    UNICODE_MAP.put('\u00b5', R.string.symbol_micro_sign);
    UNICODE_MAP.put('\u2248', R.string.symbol_almost_equals);
    UNICODE_MAP.put('\u2260', R.string.symbol_not_equals);
    UNICODE_MAP.put('\u00a4', R.string.symbol_currency_sign);
    UNICODE_MAP.put('\u00a7', R.string.symbol_section_sign);
    UNICODE_MAP.put('\u2191', R.string.symbol_upwards_arrow);
    UNICODE_MAP.put('\u2190', R.string.symbol_leftwards_arrow);
    UNICODE_MAP.put('\u20B9', R.string.symbol_rupee);
    UNICODE_MAP.put('\u2665', R.string.symbol_black_heart);
    UNICODE_MAP.put('\u007e', R.string.symbol_tilde);
    UNICODE_MAP.put('\u003d', R.string.symbol_equal);
    UNICODE_MAP.put('\uffe6', R.string.symbol_won);
    UNICODE_MAP.put('\u203b', R.string.symbol_reference);
    UNICODE_MAP.put('\u2606', R.string.symbol_white_star);
    UNICODE_MAP.put('\u2605', R.string.symbol_black_star);
    UNICODE_MAP.put('\u2661', R.string.symbol_white_heart);
    UNICODE_MAP.put('\u25cb', R.string.symbol_white_circle);
    UNICODE_MAP.put('\u25cf', R.string.symbol_black_circle);
    UNICODE_MAP.put('\u2299', R.string.symbol_solar);
    UNICODE_MAP.put('\u25ce', R.string.symbol_bullseye);
    UNICODE_MAP.put('\u2667', R.string.symbol_white_club_suit);
    UNICODE_MAP.put('\u2664', R.string.symbol_white_spade_suit);
    UNICODE_MAP.put('\u261c', R.string.symbol_white_left_pointing_index);
    UNICODE_MAP.put('\u261e', R.string.symbol_white_right_pointing_index);
    UNICODE_MAP.put('\u25d0', R.string.symbol_circle_left_half_black);
    UNICODE_MAP.put('\u25d1', R.string.symbol_circle_right_half_black);
    UNICODE_MAP.put('\u25a1', R.string.symbol_white_square);
    UNICODE_MAP.put('\u25a0', R.string.symbol_black_square);
    UNICODE_MAP.put('\u25b3', R.string.symbol_white_up_pointing_triangle);
    UNICODE_MAP.put('\u25bd', R.string.symbol_white_down_pointing_triangle);
    UNICODE_MAP.put('\u25c1', R.string.symbol_white_left_pointing_triangle);
    UNICODE_MAP.put('\u25b7', R.string.symbol_white_right_pointing_triangle);
    UNICODE_MAP.put('\u25c7', R.string.symbol_white_diamond);
    UNICODE_MAP.put('\u2669', R.string.symbol_quarter_note);
    UNICODE_MAP.put('\u266a', R.string.symbol_eighth_note);
    UNICODE_MAP.put('\u266c', R.string.symbol_beamed_sixteenth_note);
    UNICODE_MAP.put('\u2640', R.string.symbol_female);
    UNICODE_MAP.put('\u2642', R.string.symbol_male);
    UNICODE_MAP.put('\u3010', R.string.symbol_left_black_lenticular_bracket);
    UNICODE_MAP.put('\u3011', R.string.symbol_right_black_lenticular_bracket);
    UNICODE_MAP.put('\u300c', R.string.symbol_left_corner_bracket);
    UNICODE_MAP.put('\u300d', R.string.symbol_right_corner_bracket);
    UNICODE_MAP.put('\u2192', R.string.symbol_rightwards_arrow);
    UNICODE_MAP.put('\u2193', R.string.symbol_downwards_arrow);
    UNICODE_MAP.put('\u00b1', R.string.symbol_plus_minus_sign);
    UNICODE_MAP.put('\u2113', R.string.symbol_liter);
    UNICODE_MAP.put('\u2103', R.string.symbol_celsius_degree);
    UNICODE_MAP.put('\u2109', R.string.symbol_fahrenheit_degree);
    UNICODE_MAP.put('\u00a2', R.string.symbol_cent);
    UNICODE_MAP.put('\u2252', R.string.symbol_approximately_equals);
    UNICODE_MAP.put('\u222b', R.string.symbol_integral);
    UNICODE_MAP.put('\u27e8', R.string.symbol_mathematical_left_angle_bracket);
    UNICODE_MAP.put('\u27e9', R.string.symbol_mathematical_right_angle_bracket);
    UNICODE_MAP.put('\u3012', R.string.symbol_postal_mark);
    UNICODE_MAP.put('\u25b2', R.string.symbol_black_triangle_pointing_up);
    UNICODE_MAP.put('\u25bC', R.string.symbol_black_triangle_pointing_down);
    UNICODE_MAP.put('\u25c6', R.string.symbol_black_suit_of_diamonds);
    UNICODE_MAP.put('\uff65', R.string.symbol_halfwidth_katakana_middle_dot);
    UNICODE_MAP.put('\u25aa', R.string.symbol_black_smallsquare);
    UNICODE_MAP.put('\u300a', R.string.symbol_left_angle_bracket);
    UNICODE_MAP.put('\u300b', R.string.symbol_right_angle_bracket);
    UNICODE_MAP.put('\u00a1', R.string.symbol_inverted_exclamation_mark);
    UNICODE_MAP.put('\u00bf', R.string.symbol_inverted_question_mark);
    UNICODE_MAP.put('\u20a9', R.string.symbol_won_sign);
    UNICODE_MAP.put('\uff0c', R.string.symbol_full_width_comma);
    UNICODE_MAP.put('\uff01', R.string.symbol_full_width_exclamation_mark);
    UNICODE_MAP.put('\u3002', R.string.symbol_full_width_ideographic_full_stop);
    UNICODE_MAP.put('\uff1f', R.string.symbol_full_width_question_mark);
    UNICODE_MAP.put('\u00b7', R.string.symbol_middle_dot);
    UNICODE_MAP.put('\u201d', R.string.symbol_right_double_quotation_mark);
    UNICODE_MAP.put('\u3001', R.string.symbol_ideographic_comma);
    UNICODE_MAP.put('\uff1a', R.string.symbol_full_width_colon);
    UNICODE_MAP.put('\uff1b', R.string.symbol_full_width_semicolon);
    UNICODE_MAP.put('\uff06', R.string.symbol_full_width_ampersand);
    UNICODE_MAP.put('\uff3e', R.string.symbol_full_width_circumflex);
    UNICODE_MAP.put('\uff5e', R.string.symbol_full_width_tilde);
    UNICODE_MAP.put('\u201c', R.string.symbol_left_double_quotation_mark);
    UNICODE_MAP.put('\uff08', R.string.symbol_full_width_left_parenthesis);
    UNICODE_MAP.put('\uff09', R.string.symbol_full_width_right_parenthesis);
    UNICODE_MAP.put('\uff0a', R.string.symbol_full_width_asterisk);
    UNICODE_MAP.put('\uff3f', R.string.symbol_full_width_underscore);
    UNICODE_MAP.put('\u2019', R.string.symbol_right_single_quotation_mark);
    UNICODE_MAP.put('\uff5b', R.string.symbol_full_width_left_curly_bracket);
    UNICODE_MAP.put('\uff5d', R.string.symbol_full_width_right_curly_bracket);
    UNICODE_MAP.put('\uff1c', R.string.symbol_full_width_less_than_sign);
    UNICODE_MAP.put('\uff1e', R.string.symbol_full_width_greater_than_sign);
    UNICODE_MAP.put('\u2018', R.string.symbol_left_single_quotation_mark);
    // Arabic diacritical marks.
    UNICODE_MAP.put('\u064e', R.string.symbol_fatha);
    UNICODE_MAP.put('\u0650', R.string.symbol_kasra);
    UNICODE_MAP.put('\u064f', R.string.symbol_damma);
    UNICODE_MAP.put('\u064b', R.string.symbol_fathatan);
    UNICODE_MAP.put('\u064d', R.string.symbol_kasratan);
    UNICODE_MAP.put('\u064c', R.string.symbol_dammatan);
    UNICODE_MAP.put('\u0651', R.string.symbol_shadda);
    UNICODE_MAP.put('\u0652', R.string.symbol_sukun);
  }

  /**
   * Cleans up text for speech. Converts symbols to their spoken equivalents.
   *
   * @param context The context used to resolve string resources.
   * @param text The text to clean up.
   * @return Cleaned up text, or null if text is null.
   */
  public static @PolyNull CharSequence cleanUp(Context context, @PolyNull CharSequence text) {
    if (text != null) {
      CharSequence textAfterTrim = trimText(text);
      int trimmedLength = textAfterTrim.length();
      if (trimmedLength == 1) {
        CharSequence textAfterCleanUp = getCleanValueFor(context, textAfterTrim.charAt(0));

        // Return the text as it is if it remains the same after clean up so
        // that any Span information is not lost
        if (TextUtils.equals(textAfterCleanUp, textAfterTrim)) {
          return textAfterTrim;
        }

        // Retaining Spans that might have got stripped during cleanUp
        CharSequence formattedText = retainSpans(text, textAfterCleanUp);
        return formattedText;

      } else if (trimmedLength == 0 && text.length() > 0) {
        // For example, just spaces.
        return getCleanValueFor(context, text.toString().charAt(0));
      }
    }
    return text;
  }

  // Tries to detect spans in the original text
  // and wraps the cleaned up text with those spans.
  private static CharSequence retainSpans(CharSequence text, CharSequence textAfterCleanUp) {
    if (text instanceof Spannable) {
      Spannable spannable = (Spannable) text;
      Object[] spans = spannable.getSpans(0, text.length(), Object.class);
      if (spans.length != 0) {
        SpannableString ss = new SpannableString(textAfterCleanUp);
        for (Object span : spans) {
          ss.setSpan(span, 0, ss.length(), 0);
        }
        return ss;
      }
    }
    return textAfterCleanUp;
  }

  // Avoid using String.trim() so that Span info is not lost. Use this method for CharSequence trim.
  public static CharSequence trimText(CharSequence text) {
    CharSequence trimmedText;
    int start = 0;
    int last = text.length() - 1;
    while ((start <= last) && Character.isWhitespace(text.charAt(start))) {
      start++;
    }

    while ((last > start) && Character.isWhitespace(text.charAt(last))) {
      last--;
    }
    trimmedText = text.subSequence(start, (last + 1));
    return trimmedText;
  }

  /**
   * Collapses repeated consecutive characters in a CharSequence by matching against {@link
   * #CONSECUTIVE_CHARACTER_REGEX}.
   *
   * @param context Context for retrieving resources
   * @param text The text to process
   * @return The text with consecutive identical characters collapsed
   */
  public static @Nullable CharSequence collapseRepeatedCharacters(
      Context context, @Nullable CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return null;
    }

    // TODO: Add tests
    Matcher matcher = CONSECUTIVE_CHARACTER_PATTERN.matcher(text);
    while (matcher.find()) {
      final String replacement =
          context.getString(
              R.string.character_collapse_template,
              matcher.group().length(),
              getCleanValueFor(context, matcher.group().charAt(0)));
      final int matchFromIndex = matcher.end() - matcher.group().length() + replacement.length();
      text = matcher.replaceFirst(replacement);
      matcher = CONSECUTIVE_CHARACTER_PATTERN.matcher(text);
      matcher.region(matchFromIndex, text.length());
    }

    return text;
  }

  /**
   * Convenience method that feeds the given text through {@link #collapseRepeatedCharacters} and
   * then {@link #cleanUp}.
   */
  public static @Nullable CharSequence collapseRepeatedCharactersAndCleanUp(
      Context context, @Nullable CharSequence text) {
    CharSequence collapsed = collapseRepeatedCharacters(context, text);
    CharSequence cleanedUp = cleanUp(context, collapsed);
    return cleanedUp;
  }

  /** Returns the "clean" value for the specified character. */
  public static String getCleanValueFor(Context context, char key) {
    final int resId = UNICODE_MAP.get(key);

    if (resId != 0) {
      return context.getString(resId);
    }

    return Character.toString(key);
  }

  /** Returns the "clean" value for the specified character as punctuation. */
  public static @Nullable String characterToName(Context context, char key) {
    final int resId = UNICODE_MAP.get(key);

    if (resId != 0 && key != ' ') {
      return context.getString(resId);
    }
    return null;
  }
}
