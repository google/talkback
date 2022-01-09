package com.google.android.accessibility.utils.output;

import androidx.annotation.IntDef;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
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
    SEARCH_FOCUS_FAIL
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
  public static void appendLog(@DiagnosticType Integer diagnosticInfo, Object... args) {
    if (diagnosticOverlayController != null) {
      diagnosticOverlayController.appendLog(diagnosticInfo, args);
    }
  }
}
