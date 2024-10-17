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

package com.google.android.accessibility.utils.output;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.core.util.Pair;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

/** Utilities for cleaning up speech text. */
public class SpeechCleanupUtils {
  /** The regular expression used to match consecutive identical characters */
  // Double escaping of regex characters is required. "\\1" refers to the
  // first capturing group between the outer nesting of "[]"s.
  // When the text contains any repeat of special characters, the collapse function applies.
  private static final String CONSECUTIVE_CHARACTER_REGEX =
      "([\\-\\\\/|!@#$%^&*\\(\\)=_+\\[\\]\\{\\}.?;'\":<>\\u2022])\\1+";

  /** The Pattern used to match consecutive identical characters */
  private static final Pattern CONSECUTIVE_CHARACTER_PATTERN =
      Pattern.compile(CONSECUTIVE_CHARACTER_REGEX);

  /** Speak punctuation verbosity levels. */
  @IntDef({ALL, MOST, SOME})
  @Retention(RetentionPolicy.SOURCE)
  public @interface PunctuationVerbosity {}

  /**
   * Verbosity level of the punctuation and symbol that the user always hear. If the level is lower
   * than TalkBack verbosity preference, the punctuation and symbol will not be announced.
   */
  public static final int ALL = 0;

  /**
   * Verbosity level of the punctuation and symbol that user usually hears. If the level is lower
   * than TalkBack verbosity preference, the punctuation and symbol will not be announced.
   */
  public static final int MOST = 1;

  /**
   * Verbosity level of the punctuation and symbol that user sometimes hears. If the level is lower
   * than TalkBack verbosity preference, the punctuation and symbol will not be announced.
   *
   * <p>Note: TalkBack speak punctuation verbosity preference in Some level will let TTS engine
   * handle speaking punctuation in SOME level.
   */
  public static final int SOME = 2;

  /**
   * Map containing string to speech conversions and the verbosity level of symbol and punctuation .
   */
  private static final ImmutableMap<Character, Pair<Integer, Integer>>
      TALKBACK_PUNCTUATION_AND_SYMBOL =
          ImmutableMap.<Character, Pair<Integer, Integer>>builder()
              .put('&', new Pair<>(R.string.symbol_ampersand, MOST))
              .put('<', new Pair<>(R.string.symbol_angle_bracket_left, MOST))
              .put('>', new Pair<>(R.string.symbol_angle_bracket_right, MOST))
              .put('\'', new Pair<>(R.string.symbol_apostrophe, ALL))
              .put('*', new Pair<>(R.string.symbol_asterisk, MOST))
              .put('@', new Pair<>(R.string.symbol_at_sign, MOST))
              .put('\\', new Pair<>(R.string.symbol_backslash, MOST))
              .put('•', new Pair<>(R.string.symbol_bullet, MOST))
              .put('^', new Pair<>(R.string.symbol_caret, MOST))
              .put('¢', new Pair<>(R.string.symbol_cent, ALL))
              .put(':', new Pair<>(R.string.symbol_colon, MOST))
              .put(',', new Pair<>(R.string.symbol_comma, ALL))
              .put('©', new Pair<>(R.string.symbol_copyright, MOST))
              .put('{', new Pair<>(R.string.symbol_curly_bracket_left, MOST))
              .put('}', new Pair<>(R.string.symbol_curly_bracket_right, MOST))
              .put('°', new Pair<>(R.string.symbol_degree, MOST))
              .put('÷', new Pair<>(R.string.symbol_division, MOST))
              .put('…', new Pair<>(R.string.symbol_ellipsis, ALL))
              .put('—', new Pair<>(R.string.symbol_em_dash, MOST))
              .put('–', new Pair<>(R.string.symbol_en_dash, MOST))
              .put('!', new Pair<>(R.string.symbol_exclamation_mark, ALL))
              .put('`', new Pair<>(R.string.symbol_grave_accent, MOST))
              .put('-', new Pair<>(R.string.symbol_hyphen_minus, ALL))
              .put('„', new Pair<>(R.string.symbol_low_double_quote, MOST))
              .put('×', new Pair<>(R.string.symbol_multiplication, MOST))
              .put('\n', new Pair<>(R.string.symbol_new_line, ALL))
              .put('¶', new Pair<>(R.string.symbol_paragraph_mark, MOST))
              .put('(', new Pair<>(R.string.symbol_parenthesis_left, MOST))
              .put(')', new Pair<>(R.string.symbol_parenthesis_right, MOST))
              .put('%', new Pair<>(R.string.symbol_percent, MOST))
              .put('.', new Pair<>(R.string.symbol_period, ALL))
              .put('π', new Pair<>(R.string.symbol_pi, MOST))
              .put('#', new Pair<>(R.string.symbol_pound, ALL))
              // Currency
              .put('$', new Pair<>(R.string.symbol_dollar_sign, ALL))
              .put('€', new Pair<>(R.string.symbol_euro, ALL))
              .put('£', new Pair<>(R.string.symbol_pound_sterling, ALL))
              .put('¥', new Pair<>(R.string.symbol_yen, ALL))
              .put('₱', new Pair<>(R.string.symbol_currency_peso, ALL))
              .put('₹', new Pair<>(R.string.symbol_rupee, MOST))
              .put('₫', new Pair<>(R.string.symbol_currency_dong, ALL))
              .put('¤', new Pair<>(R.string.symbol_currency_sign, ALL))
              .put('?', new Pair<>(R.string.symbol_question_mark, ALL))
              .put('"', new Pair<>(R.string.symbol_quotation_mark, MOST))
              .put('®', new Pair<>(R.string.symbol_registered_trademark, MOST))
              .put(';', new Pair<>(R.string.symbol_semicolon, MOST))
              .put('/', new Pair<>(R.string.symbol_slash, MOST))
              .put(' ', new Pair<>(R.string.symbol_space, ALL))
              .put('\u00a0', new Pair<>(R.string.symbol_space, ALL))
              .put('[', new Pair<>(R.string.symbol_square_bracket_left, MOST))
              .put(']', new Pair<>(R.string.symbol_square_bracket_right, MOST))
              .put('√', new Pair<>(R.string.symbol_square_root, MOST))
              .put('™', new Pair<>(R.string.symbol_trademark, MOST))
              .put('_', new Pair<>(R.string.symbol_underscore, MOST))
              .put('|', new Pair<>(R.string.symbol_vertical_bar, MOST))
              .put('¬', new Pair<>(R.string.symbol_not_sign, MOST))
              .put('¦', new Pair<>(R.string.symbol_broken_bar, MOST))
              .put('µ', new Pair<>(R.string.symbol_micro_sign, MOST))
              .put('≈', new Pair<>(R.string.symbol_almost_equals, MOST))
              .put('≠', new Pair<>(R.string.symbol_not_equals, MOST))
              .put('§', new Pair<>(R.string.symbol_section_sign, MOST))
              .put('↑', new Pair<>(R.string.symbol_upwards_arrow, MOST))
              .put('←', new Pair<>(R.string.symbol_leftwards_arrow, MOST))
              .put('→', new Pair<>(R.string.symbol_rightwards_arrow, MOST))
              .put('↓', new Pair<>(R.string.symbol_downwards_arrow, MOST))
              .put('♥', new Pair<>(R.string.symbol_black_heart, MOST))
              .put('~', new Pair<>(R.string.symbol_tilde, MOST))
              .put('=', new Pair<>(R.string.symbol_equal, MOST))
              .put('￦', new Pair<>(R.string.symbol_won, MOST))
              .put('₩', new Pair<>(R.string.symbol_won_sign, MOST))
              .put('※', new Pair<>(R.string.symbol_reference, MOST))
              .put('☆', new Pair<>(R.string.symbol_white_star, MOST))
              .put('★', new Pair<>(R.string.symbol_black_star, MOST))
              .put('♡', new Pair<>(R.string.symbol_white_heart, MOST))
              .put('○', new Pair<>(R.string.symbol_white_circle, MOST))
              .put('●', new Pair<>(R.string.symbol_black_circle, MOST))
              .put('⊙', new Pair<>(R.string.symbol_solar, MOST))
              .put('◎', new Pair<>(R.string.symbol_bullseye, MOST))
              .put('♧', new Pair<>(R.string.symbol_white_club_suit, MOST))
              .put('♤', new Pair<>(R.string.symbol_white_spade_suit, MOST))
              .put('☜', new Pair<>(R.string.symbol_white_left_pointing_index, MOST))
              .put('☞', new Pair<>(R.string.symbol_white_right_pointing_index, MOST))
              .put('◐', new Pair<>(R.string.symbol_circle_left_half_black, MOST))
              .put('◑', new Pair<>(R.string.symbol_circle_right_half_black, MOST))
              .put('□', new Pair<>(R.string.symbol_white_square, MOST))
              .put('■', new Pair<>(R.string.symbol_black_square, MOST))
              .put('△', new Pair<>(R.string.symbol_white_up_pointing_triangle, MOST))
              .put('▽', new Pair<>(R.string.symbol_white_down_pointing_triangle, MOST))
              .put('◁', new Pair<>(R.string.symbol_white_left_pointing_triangle, MOST))
              .put('▷', new Pair<>(R.string.symbol_white_right_pointing_triangle, MOST))
              .put('◇', new Pair<>(R.string.symbol_white_diamond, MOST))
              .put('♩', new Pair<>(R.string.symbol_quarter_note, MOST))
              .put('♪', new Pair<>(R.string.symbol_eighth_note, MOST))
              .put('♬', new Pair<>(R.string.symbol_beamed_sixteenth_note, MOST))
              .put('♀', new Pair<>(R.string.symbol_female, MOST))
              .put('♂', new Pair<>(R.string.symbol_male, MOST))
              .put('【', new Pair<>(R.string.symbol_left_black_lenticular_bracket, MOST))
              .put('】', new Pair<>(R.string.symbol_right_black_lenticular_bracket, MOST))
              .put('「', new Pair<>(R.string.symbol_left_corner_bracket, MOST))
              .put('」', new Pair<>(R.string.symbol_right_corner_bracket, MOST))
              .put('±', new Pair<>(R.string.symbol_plus_minus_sign, MOST))
              .put('ℓ', new Pair<>(R.string.symbol_liter, MOST))
              .put('℃', new Pair<>(R.string.symbol_celsius_degree, MOST))
              .put('℉', new Pair<>(R.string.symbol_fahrenheit_degree, MOST))
              .put('≒', new Pair<>(R.string.symbol_approximately_equals, MOST))
              .put('∫', new Pair<>(R.string.symbol_integral, MOST))
              .put('⟨', new Pair<>(R.string.symbol_mathematical_left_angle_bracket, MOST))
              .put('⟩', new Pair<>(R.string.symbol_mathematical_right_angle_bracket, MOST))
              .put('〒', new Pair<>(R.string.symbol_postal_mark, MOST))
              .put('▲', new Pair<>(R.string.symbol_black_triangle_pointing_up, MOST))
              .put('▼', new Pair<>(R.string.symbol_black_triangle_pointing_down, MOST))
              .put('♦', new Pair<>(R.string.symbol_black_suit_of_diamonds, MOST))
              .put('◆', new Pair<>(R.string.symbol_black_suit_of_diamonds, MOST))
              .put('･', new Pair<>(R.string.symbol_halfwidth_katakana_middle_dot, MOST))
              .put('▪', new Pair<>(R.string.symbol_black_smallsquare, MOST))
              .put('《', new Pair<>(R.string.symbol_left_angle_bracket, MOST))
              .put('》', new Pair<>(R.string.symbol_right_angle_bracket, MOST))
              .put('¡', new Pair<>(R.string.symbol_inverted_exclamation_mark, MOST))
              .put('¿', new Pair<>(R.string.symbol_inverted_question_mark, ALL))
              // Full-width
              .put('，', new Pair<>(R.string.symbol_full_width_comma, ALL))
              .put('！', new Pair<>(R.string.symbol_full_width_exclamation_mark, ALL))
              .put('。', new Pair<>(R.string.symbol_full_width_ideographic_full_stop, MOST))
              .put('？', new Pair<>(R.string.symbol_full_width_question_mark, ALL))
              .put('·', new Pair<>(R.string.symbol_middle_dot, MOST))
              .put('”', new Pair<>(R.string.symbol_right_double_quotation_mark, MOST))
              .put('、', new Pair<>(R.string.symbol_ideographic_comma, ALL))
              .put('：', new Pair<>(R.string.symbol_full_width_colon, MOST))
              .put('；', new Pair<>(R.string.symbol_full_width_semicolon, MOST))
              .put('＆', new Pair<>(R.string.symbol_full_width_ampersand, MOST))
              .put('＾', new Pair<>(R.string.symbol_full_width_circumflex, MOST))
              .put('～', new Pair<>(R.string.symbol_full_width_tilde, MOST))
              .put('“', new Pair<>(R.string.symbol_left_double_quotation_mark, MOST))
              .put('（', new Pair<>(R.string.symbol_full_width_left_parenthesis, MOST))
              .put('）', new Pair<>(R.string.symbol_full_width_right_parenthesis, MOST))
              .put('＊', new Pair<>(R.string.symbol_full_width_asterisk, MOST))
              .put('＿', new Pair<>(R.string.symbol_full_width_underscore, MOST))
              .put('’', new Pair<>(R.string.symbol_right_single_quotation_mark, MOST))
              .put('｛', new Pair<>(R.string.symbol_full_width_left_curly_bracket, MOST))
              .put('｝', new Pair<>(R.string.symbol_full_width_right_curly_bracket, MOST))
              .put('＜', new Pair<>(R.string.symbol_full_width_less_than_sign, MOST))
              .put('＞', new Pair<>(R.string.symbol_full_width_greater_than_sign, MOST))
              .put('‘', new Pair<>(R.string.symbol_left_single_quotation_mark, MOST))
              // Arabic diacritical marks.
              .put('\u064e', new Pair<>(R.string.symbol_fatha, MOST))
              .put('\u0650', new Pair<>(R.string.symbol_kasra, MOST))
              .put('\u064f', new Pair<>(R.string.symbol_damma, MOST))
              .put('\u064b', new Pair<>(R.string.symbol_fathatan, MOST))
              .put('\u064d', new Pair<>(R.string.symbol_kasratan, MOST))
              .put('\u064c', new Pair<>(R.string.symbol_dammatan, MOST))
              .put('\u0651', new Pair<>(R.string.symbol_shadda, MOST))
              .put('\u0652', new Pair<>(R.string.symbol_sukun, MOST))
              .buildOrThrow();

  /**
   * Cleans up text for speech. Converts symbols to their spoken equivalents.
   *
   * @param context The context used to resolve string resources.
   * @param text The text to clean up.
   * @return Cleaned up text, or null if text is null.
   */
  public static @PolyNull CharSequence cleanUp(Context context, @PolyNull CharSequence text) {
    if (text != null) {
      CharSequence textAfterTrim = SpannableUtils.trimText(text);
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
    Pair<Integer, Integer> pair = TALKBACK_PUNCTUATION_AND_SYMBOL.get(key);
    if (pair != null) {
      final int resId = pair.first;

      if (resId != 0) {
        return context.getString(resId);
      }
    }
    return Character.toString(key);
  }

  /** Returns the "clean" value for the specified character as punctuation. */
  public static @Nullable String characterToName(Context context, char key) {
    return characterToName(context, key, ALL);
  }

  /**
   * Returns the "clean" value for the specified character as punctuation.
   *
   * @param context the parent context
   * @param key the character to be transformed
   * @param punctuationVerbosity the verbosity of user preference
   * @return the character name defined in this utils
   */
  public static @Nullable String characterToName(
      Context context, char key, int punctuationVerbosity) {
    Pair<Integer, Integer> pair = TALKBACK_PUNCTUATION_AND_SYMBOL.get(key);
    if (pair == null) {
      return null;
    } else if (key != ' ') {
      if (pair.second < punctuationVerbosity) {
        return null;
      }
    }
    final int resId = pair.first;

    if (resId != 0 && key != ' ') {
      return context.getString(resId);
    }
    return null;
  }
}
