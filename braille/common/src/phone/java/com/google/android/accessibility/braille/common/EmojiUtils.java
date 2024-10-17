/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utils class handles Emoji representations. */
public class EmojiUtils {
  // TODO: Extends Emoji regex to support more Emojis.
  private static final Pattern EMOJI_REGEX_PATTERN = Pattern.compile("[\uD83D\uDE00-\uD83D\uDE4F]");

  /** Finds Emoji unicode. */
  public static Matcher findEmoji(CharSequence text) {
    return EMOJI_REGEX_PATTERN.matcher(text);
  }

  /**
   * Gets emoji's short code which is a human readable description.
   *
   * @param emoji an emoji String composed by 2 unicode.
   * @return Emoji short code.
   */
  public static String getEmojiShortCode(String emoji) {
    // TODO: Makes emoji has localization.
    return Character.getName(emoji.codePointAt(0));
  }

  private EmojiUtils() {}
}
