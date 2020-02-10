package com.google.android.accessibility.utils.widget;

import androidx.annotation.IntDef;
import android.view.Window;
import android.view.WindowManager;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Helper functions related dialog settings. */
public class DialogUtils {

  /** Enumeration of window layout types for dialogs. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
  })
  public @interface WindowType {}

  public static void setWindowTypeToDialog(Window window) {
    window.setType(getDialogType());
  }

  @WindowType
  public static int getDialogType() {
    return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
  }
}
