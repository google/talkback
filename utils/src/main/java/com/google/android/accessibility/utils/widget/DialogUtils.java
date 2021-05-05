package com.google.android.accessibility.utils.widget;

import android.view.Window;
import android.view.WindowManager;

/** Helper functions related dialog settings. */
public class DialogUtils {

  public static void setWindowTypeToDialog(Window window) {
    window.setType(getDialogType());
  }
  
  public static int getDialogType() {
    return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
  }
}
