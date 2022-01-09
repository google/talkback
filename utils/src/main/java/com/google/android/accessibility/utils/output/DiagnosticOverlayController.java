package com.google.android.accessibility.utils.output;

import com.google.android.accessibility.utils.output.DiagnosticOverlayUtils.DiagnosticType;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Interface for appending logs to controller */
public interface DiagnosticOverlayController {

  /**
   * Receives and appends log to controller
   *
   * @param format The format of incoming log
   * @param args The log and other debugging objects
   */
  // TODO  - need to create new data structure for log records
  @FormatMethod
  void appendLog(@FormatString String format, Object... args);

  /**
   * Receives and appends the category of {@link DiagnosticType} and related debugging objects
   * {@code args}
   */
  void appendLog(@DiagnosticType Integer diagnosticInfo, Object... args);
}
