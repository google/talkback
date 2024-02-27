package com.google.android.accessibility.brailleime;

import android.content.Context;

/** Reader class for flags of experimental feature. */
public final class FeatureFlagReader {

  /** Whether to use hold and swipe gesture. */
  public static boolean useHoldAndSwipeGesture(Context context) {
    return true;
  }

  private FeatureFlagReader() {}
}
