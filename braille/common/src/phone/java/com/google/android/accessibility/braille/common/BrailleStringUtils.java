package com.google.android.accessibility.braille.common;

import android.text.TextUtils;
import java.util.Locale;

/** Braille string utils. */
public class BrailleStringUtils {

  /** Converts string to character title case. */
  public static CharSequence toCharacterTitleCase(CharSequence input) {
    if (TextUtils.isEmpty(input)) {
      return input;
    }
    return String.valueOf(input.charAt(0)).toUpperCase(Locale.getDefault())
        + input.subSequence(1, input.length());
  }

  private BrailleStringUtils() {}
}
