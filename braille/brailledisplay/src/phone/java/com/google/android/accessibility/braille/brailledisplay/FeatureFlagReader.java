package com.google.android.accessibility.braille.brailledisplay;

import android.content.Context;

/** Reader class for accessing feature flags from Google experimentation framework. */
public final class FeatureFlagReader {

  /** Whether to use cut, copy and paste shortcut. */
  public static boolean useCutCopyPaste(Context context) {
    return true;
  }

  /** Whether to use select previous/next character/word/line shortcut. */
  public static boolean useSelectPreviousNextCharacterWordLine(Context context) {
    return true;
  }

  /** Whether to use select all. */
  public static boolean useSelectAll(Context context) {
    return true;
  }

  /** Whether to use play/pause media. */
  public static boolean usePlayPauseMedia(Context context) {
    return true;
  }

  /** Whether braille display hid protocol supported. */
  public static boolean isBdHidSupported(Context context) {
    return false;
  }

  /** Whether to enable select current from current cursor to start or end. */
  public static boolean useSelectCurrentToStartOrEnd(Context context) {
    return true;
  }

  private FeatureFlagReader() {}
}
