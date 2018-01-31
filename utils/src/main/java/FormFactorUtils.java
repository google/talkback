/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.utils;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Utilities that differentiate between different device types: Android Wear, Android TV, Android
 * Auto, Android VR, etc..
 */
public final class FormFactorUtils {
  private static final int FORM_FACTOR_PHONE_OR_TABLET = 0;
  private static final int FORM_FACTOR_WATCH = 1;
  private static final int FORM_FACTOR_TV = 2;
  private static final int FORM_FACTOR_ARC = 3;

  private static final String ARC_DEVICE_PATTERN = ".+_cheets|cheets_.+";

  private static FormFactorUtils sInstance;

  private final int mFormFactor;
  private final boolean mHasAccessibilityShortcut;

  private FormFactorUtils(final Context context) {
    // Find device type.
    if (context
        .getApplicationContext()
        .getPackageManager()
        .hasSystemFeature(PackageManager.FEATURE_WATCH)) {
      mFormFactor = FORM_FACTOR_WATCH;
    } else if (isContextTelevision(context)) {
      mFormFactor = FORM_FACTOR_TV;
    } else if (Build.DEVICE != null && Build.DEVICE.matches(ARC_DEVICE_PATTERN)) {
      mFormFactor = FORM_FACTOR_ARC;
    } else {
      mFormFactor = FORM_FACTOR_PHONE_OR_TABLET;
    }

    // Find whether device supports accessibility shortcut.
    mHasAccessibilityShortcut =
        (BuildVersionUtils.isAtLeastO() && mFormFactor == FORM_FACTOR_PHONE_OR_TABLET);
  }

  /** @return an instance of this Singleton. */
  public static synchronized FormFactorUtils getInstance(final Context context) {
    if (sInstance == null) {
      sInstance = new FormFactorUtils(context);
    }
    return sInstance;
  }

  /** Return the cached version of the isWatch. */
  public boolean isWatch() {
    return mFormFactor == FORM_FACTOR_WATCH;
  }

  public boolean isArc() {
    return mFormFactor == FORM_FACTOR_ARC;
  }

  public boolean isTv() {
    return mFormFactor == FORM_FACTOR_TV;
  }

  public boolean isPhoneOrTablet() {
    return mFormFactor == FORM_FACTOR_PHONE_OR_TABLET;
  }

  public boolean hasAccessibilityShortcut() {
    return mHasAccessibilityShortcut;
  }

  public static boolean useSpeakPasswordsServicePref() {
    return BuildVersionUtils.isAtLeastO();
  }

  public static boolean isContextTelevision(Context context) {
    if (context == null) {
      return false;
    }

    UiModeManager modeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    return modeManager != null
        && modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
  }

  // Returns true for devices which have separate audio a11y stream
  public static boolean hasAcessibilityAudioStream(Context context) {
    return BuildVersionUtils.isAtLeastO() && !FormFactorUtils.getInstance(context).isTv();
  }
}
