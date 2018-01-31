package com.google.android.accessibility.switchaccess;

import com.google.common.annotations.VisibleForTesting;

/** Guards features that are under development, but not yet ready for launch. */
public class FeatureFlags {

  private static boolean mNomonClocks = false;
  private static boolean mScanNonActionableItems = false;
  private static boolean mScrollArrows = false;

  /**
   * Guards Nomon Clock feature. In the Nomon Clock scanning mode, animated clocks are displayed
   * next to each item. Each item is "selectable" for a given period of time, during which the clock
   * is shown in an active state. Items are narrowed down until only one item is left.
   *
   * @return {@code true} if Nomon Clocks features are enabled, {@code false} otherwise
   */
  public static boolean nomonClocks() {
    return mNomonClocks;
  }

  /**
   * Guards the Scanning Non-actionable Items feature. If the feature is enabled, when scanning
   * items on the screen, non-actionable items can also be scanned and be spoken aloud.
   *
   * @return {@code true} if Scanning Non-actionable Items feature is enabled, {@code false}
   *     otherwise
   */
  public static boolean scanNonActionableItems() {
    return mScanNonActionableItems;
  }

  /**
   * Guards the Scroll Arrows feature. When enabled, arrows will appear along the top, bottom,
   * right, and left edges of highlighted scrollable views to indicate to the user that the views
   * are scrollable.
   *
   * @return {@code true} if the Scroll Arrows feature is enabled, {@code false} otherwise
   */
  public static boolean scrollArrows() {
    return mScrollArrows;
  }

  @VisibleForTesting
  public static void enableAllFeatureFlags() {
    mNomonClocks = true;
    mScanNonActionableItems = true;
    mScrollArrows = true;
  }
}
