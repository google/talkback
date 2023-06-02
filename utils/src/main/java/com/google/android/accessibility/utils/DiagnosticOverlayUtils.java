package com.google.android.accessibility.utils;

import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Forwards diagnostic information to the set DiagnosticOverlayController */
public class DiagnosticOverlayUtils {

  public static final int NONE = -1;
  public static final int FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS = 0;
  public static final int FOCUS_FAIL_NOT_SPEAKABLE = 1;
  public static final int FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN = 2;
  public static final int FOCUS_FAIL_NOT_VISIBLE = 3;
  public static final int SEARCH_FOCUS_FAIL = 4;
  public static final int REFOCUS_PATH = 5;

  /**
   * Types defining what category of debugging controller needs to handle based on information sent
   * from {@link AccessibilityNodeInfoUtils#shouldFocusNode}
   */
  @IntDef({
    NONE,
    FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS,
    FOCUS_FAIL_NOT_SPEAKABLE,
    FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN,
    FOCUS_FAIL_NOT_VISIBLE,
    SEARCH_FOCUS_FAIL,
    REFOCUS_PATH
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface DiagnosticType {};

  private DiagnosticOverlayUtils() {}

  private static @Nullable DiagnosticOverlayController diagnosticOverlayController = null;

  /** Sets controller for shared utils class */
  public static void setDiagnosticOverlayController(
      @Nullable DiagnosticOverlayController controller) {
    diagnosticOverlayController = controller;
  }

  /**
   * Receives and forwards the category of {@link DiagnosticType} and related debugging objects
   * {@code args} to the controller.
   */
  public static void appendLog(
      @DiagnosticType Integer diagnosticInfo, AccessibilityNodeInfoCompat node) {
    if (diagnosticOverlayController != null) {
      diagnosticOverlayController.appendLog(diagnosticInfo, node);
    }
  }

  public static void appendLog(@DiagnosticType Integer diagnosticInfo, AccessibilityNode node) {
    if (diagnosticOverlayController != null) {
      diagnosticOverlayController.appendLog(diagnosticInfo, node);
    }
  }
}
