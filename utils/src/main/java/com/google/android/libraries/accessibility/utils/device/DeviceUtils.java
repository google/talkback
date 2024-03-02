package com.google.android.libraries.accessibility.utils.device;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utilities for checking hardware support. */
public final class DeviceUtils {

  private DeviceUtils() {}

  /** Returns {@code true} if this device is a TV. */
  public static boolean isTv(@Nullable Context context) {
    if (context == null) {
      return false;
    }

    UiModeManager modeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    return (modeManager != null)
        && (modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
  }
}
