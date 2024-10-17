package com.google.android.accessibility.talkback.utils;

/** Reader class for build-time experimental flags. */
public final class ExperimentalUtils {

  public static boolean isExperimental() {
    return false;
  }

  public static int getAdditionalTalkBackServiceFlags() {
    // No flags in stable.
    return 0;
  }
}
