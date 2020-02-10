package com.google.android.libraries.accessibility.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

/**
 * Library for string manipulations. Contains regular expressions and common operation helper
 * methods.
 */
public final class StringUtils {

  /**
   * A regex pattern to group a single section of a string based on camel case. This will need to be
   * used in a loop to extract all sections of the string. This will work for Unicode character
   * blocks. Here are some examples:
   *
   * <ul>
   * <li>"thisIsCamelCase" => ["this", "Is", "Camel", "Case"]
   * <li>"30daysInJune" => ["30", "days", "In", "June"]
   * <li>"25DaysSinceJune10!!!" => ["25", "Days", "Since", "June", "10", "!!!"]
   * </ul>
   *
   * <p>
   * <br>
   * This regex has four categories of characters: upper case letters, lower case letters, numbers,
   * and symbols (including punctuation). All of these categories are grouped separately from one
   * another with the exception of upper case letters. A group cannot have more than one upper case
   * letter and can contain any number of lower case letters.
   *
   * <p>
   * <br>
   * Here are the sections of the regex:
   *
   * <br>
   * 1. "\\p{Ll}+" -> One or more lower case letters
   *
   * <br>
   * 2. "\\p{N}+" -> One or more numbers
   *
   * <br>
   * 3. "[\\p{S}\\p{P}]+" -> One or more symbols/punctuations
   *
   * <br>
   * 4. "\\p{Lu}\\p{Ll}*" -> Exactly one upper case letter followed by zero or more lower case
   * letters
   */
  public static final Pattern CAMEL_CASE_PATTERN =
      Pattern.compile("(\\p{Ll}+|\\p{N}+|[\\p{S}\\p{P}]+|\\p{Lu}\\p{Ll}*)");

  /**
   * A regex pattern to match a single whitespace or invisible separator. Supports Unicode character
   * blocks.
   */
  private static final Pattern WHITESPACE_BLOCK_PATTERN = Pattern.compile("\\p{Z}+");

  /**
   * A regex pattern to match a group of non-whitespace characters. Supports Unicode blocks.
   */
  private static final Pattern NOT_WHITESPACE_GROUP_PATTERN = Pattern.compile("(\\P{Z}+)");

  private static final String SPACE_STRING = " ";

  private StringUtils() {}

  /**
   * Splits the input {@link String} based on whitespace and camel case. The resulting split
   * substrings will not contain any whitespace and will also maintain the original capitalization.
   * See {@value #CAMEL_CASE_PATTERN} for specific rules on camel case splitting and
   * {@link #splitOnSpace} for specific rules on whitespace splitting.
   *
   * @param str The {@link String} to be split
   * @return The substrings based off camel case and whitespace. If the input is empty (no
   *         non-whitespace characters) or {@code null} then the result will be an immutable empty
   *         list
   */
  public static List<String> splitOnCamelCase(String str) {
    if (isEmpty(str)) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>();
    for (final String word : splitOnSpace(str)) {
      final Matcher camelCaseMatcher = CAMEL_CASE_PATTERN.matcher(word);
      if (camelCaseMatcher.find()) {
        do {
          result.add(camelCaseMatcher.group());
        } while (camelCaseMatcher.find());
      } else {
        result.add(word);
      }
    }
    return result;
  }

  /**
   * Equivalent to {@code splitOnSpace(str, 0)}
   */
  public static List<String> splitOnSpace(String str) {
    return splitOnSpace(str, 0);
  }

  /**
   * Splits the input {@link String} based on whitespace. The resulting split substrings will not
   * contain any whitespace and will also maintain the original capitalization.
   *
   * @param str The {@link String} to be split
   * @param limit Determines the maximum number of entries in the resulting array, and the treatment
   *        of trailing empty strings
   *        <ul>
   *        <li>For n > 0, the resulting {@link List} contains at most n entries. If this is fewer
   *        than the number of matches, the final entry will contain all remaining input
   *        <li>For n <= 0, the length of the resulting {@link List} is exactly the number of words
   *        in the input
   *        </ul>
   * @return The substrings based off whitespace. If the input is empty (no non-whitespace
   *         characters) or {@code null} then the result will be an immutable empty list
   */
  public static List<String> splitOnSpace(String str, int limit) {
    if (str == null) {
      return Collections.emptyList();
    }
    List<String> words = new ArrayList<>();
    Matcher matcher = NOT_WHITESPACE_GROUP_PATTERN.matcher(str);
    int count = 0;
    while (matcher.find()) {
      words.add(matcher.group());
      if (++count == limit) {
        break;
      }
    }
    return words;
  }

  /**
   * Checks the input {@link String} to see if it contains any non-whitespace characters.
   *
   * @param str The {@link String} to be checked
   * @return {@code true} if the input is null or consists of zero or more whitespace characters;
   *     {@code false} otherwise
   */
  @EnsuresNonNullIf(expression = "#1", result = false)
  public static boolean isEmpty(@Nullable CharSequence cs) {
    if ((cs == null) || (cs.length() == 0)) {
      return true;
    }
    for (int i = 0; i < cs.length(); i++) {
      if (!Character.isWhitespace(cs.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Collapses multiple spaces into a single space and removes leading/trailing whitespace.
   *
   * @param str The input {@link String} to normalize spaces
   * @return The input {@link String} with unnecessary whitespace removed or {@code null} if the
   *     input is {@code null}
   */
  @PolyNull
  public static String normalizeSpaces(@PolyNull CharSequence str) {
    return (str == null)
        ? null
        : WHITESPACE_BLOCK_PATTERN.matcher(str).replaceAll(SPACE_STRING).trim();
  }

  /**
   * Capitalize the first letter of a string, in the specified locale. Supports Unicode.
   *
   * @param str The input {@link String} for which to capitalize the first letter
   * @param locale The {@link Locale} to use when capitalizing
   * @return The input {@link String} with the first letter capitalized
   */
  public static String capitalizeFirstLetter(String str, Locale locale) {
    if (isEmpty(str)) {
      return str;
    }
    return Character.isUpperCase(str.charAt(0))
        ? str
        : str.substring(0, 1).toUpperCase(locale) + str.substring(1);
  }

  /**
   * Capitalize the first letter of a string, using the default locale. Supports Unicode.
   *
   * @param str The input {@link String} for which to capitalize the first letter
   * @return The input {@link String} with the first letter capitalized
   */
  public static String capitalizeFirstLetter(String str) {
    return capitalizeFirstLetter(str, Locale.getDefault());
  }

  /**
   * Lowercase the first letter of a string, in the specified locale. Supports Unicode.
   *
   * @param str The input {@link String} for which to lowercase the first letter
   * @param locale The {@link Locale} to use when lowercasing
   * @return The input {@link String} with the first letter lowercased
   */
  public static String lowercaseFirstLetter(String str, Locale locale) {
    if (isEmpty(str)) {
      return str;
    }
    return Character.isLowerCase(str.charAt(0))
        ? str
        : str.substring(0, 1).toLowerCase(locale) + str.substring(1);
  }

  /**
   * Lowercase the first letter of a string, using the default locale. Supports Unicode.
   *
   * @param str The input {@link String} for which to lowercase the first letter
   * @return The input {@link String} with the first letter lowercased
   */
  public static String lowercaseFirstLetter(String str) {
    return lowercaseFirstLetter(str, Locale.getDefault());
  }

  /**
   * Checks to see if a string is only consisting of punctuation, but is not empty.
   *
   * @return {@code true} if the above condition is true
   */
  public static boolean isPunctuationOnly(String str) {
    return Pattern.matches("\\p{P}+", str);
  }
}
