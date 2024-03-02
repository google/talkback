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
