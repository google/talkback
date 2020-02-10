/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback.screensearch;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class that is responsible for the string matching to fulfill the screen search needs. */
public class StringMatcher {
  private StringMatcher() {}

  // TODO: [Screen Search] Enables AutoValue for screen search when AOSP verified to
  // allow using AutoValue
  static final class MatchResult {
    private int start;
    private int end;

    MatchResult(int start, int end) {
      this.start = start;
      this.end = end;
    }

    public int start() {
      return start;
    }

    public int end() {
      return end;
    }
  }

  /**
   * Finds the {@code keyword} matches in the {@code target}. The matching is performed
   * case-insensitive and multiple continuous spaces in either {@code target} or {@code keyword}
   * will be treated as only one while matching.
   *
   * @param target, the target where the matching will be performed
   * @param keyword, the keyword used to perform the matching
   */
  static List<MatchResult> findMatches(String target, String keyword) {
    if (TextUtils.isEmpty(target) || TextUtils.isEmpty(keyword)) {
      return Collections.emptyList();
    }

    Pattern keywordPattern = convertKeywordToPattern(keyword);
    Matcher matchResult = keywordPattern.matcher(target);

    List<MatchResult> result = new ArrayList<>();

    while (matchResult.find()) {
      MatchResult matching = new MatchResult(matchResult.start(), matchResult.end());
      result.add(matching);
    }

    return result;
  }

  /**
   * Converts the user input {@code keyword} to a pattern for the regular expression matching usage.
   * The resulting pattern allows the spaces in keyword to be able to match different lengths spaces
   * in the matching target and allows case-insensitive matching.
   */
  private static Pattern convertKeywordToPattern(String keyword) {
    String spacePattern = "\\s+";
    String spaceString = " ";

    String[] spaceSplitKeyword =
        keyword
            .replaceAll(/* regex= */ spacePattern, /* replacement= */ spaceString)
            .split(spaceString);

    StringBuilder quotedKeywordPattern = new StringBuilder();

    // Quote each segment to ensure the keyword content if containing pattern-preserved keyword
    // won't alter the resulting pattern.
    for (String keywordSegment : spaceSplitKeyword) {
      if (quotedKeywordPattern.length() == 0) {
        quotedKeywordPattern.append(Pattern.quote(keywordSegment));
      } else {
        quotedKeywordPattern.append(spacePattern).append(Pattern.quote(keywordSegment));
      }
    }

    return Pattern.compile(
        quotedKeywordPattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }
}
