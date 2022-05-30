package com.google.android.accessibility.utils.screenunderstanding;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.Locale;

/** An interface for detecting icon annotations from a specific screenshot. */
public interface IconAnnotationsDetector extends ScreenAnnotationsDetector {

  /**
   * If icons identified by screen understanding matches the specified {@code node}, returns the
   * localized label of the matched icons. Returns {@code null} if no detected icon matches the
   * specified {@code node}.
   */
  @Nullable
  CharSequence getIconLabel(Locale locale, AccessibilityNodeInfoCompat node);
}
