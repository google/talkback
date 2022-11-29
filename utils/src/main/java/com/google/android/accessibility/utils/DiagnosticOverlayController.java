package com.google.android.accessibility.utils;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.DiagnosticType;

/** Interface for appending logs to controller */
public interface DiagnosticOverlayController {

  /**
   * Receives and appends the category of {@link DiagnosticType} and related debugging objects
   * {@code args}
   */
  void appendLog(@DiagnosticType Integer diagnosticInfo, AccessibilityNodeInfoCompat node);

  void appendLog(@DiagnosticType Integer diagnosticInfo, AccessibilityNode node);
}
