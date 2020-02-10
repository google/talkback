/*
 * Copyright (C) 2018 The Android Open Source Project
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

/** Methods to check hardware and software support for operating system features. */
public final class FeatureSupport {

  public static boolean isWatch(Context context) {
    return context
        .getApplicationContext()
        .getPackageManager()
        .hasSystemFeature(PackageManager.FEATURE_WATCH);
  }

  public static boolean isArc() {
    return (Build.DEVICE != null && Build.DEVICE.matches(".+_cheets|cheets_.+"));
  }

  public static boolean isTv(Context context) {
    if (context == null) {
      return false;
    }

    UiModeManager modeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    return ((modeManager != null)
        && (modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION));
  }

  public static boolean isPhoneOrTablet(Context context) {
    return (!isWatch(context) && !isArc() && !isTv(context));
  }

  // Find whether device supports accessibility shortcut.
  public static boolean hasAccessibilityShortcut(Context context) {
    return isPhoneOrTablet(context) && BuildVersionUtils.isAtLeastO();
  }

  public static boolean useSpeakPasswordsServicePref() {
    return BuildVersionUtils.isAtLeastO();
  }

  // Returns true for devices which have separate audio a11y stream.
  public static boolean hasAcessibilityAudioStream(Context context) {
    return BuildVersionUtils.isAtLeastO() && !isTv(context);
  }

  /** Return whether fingerprint feature is supported on this device. */
  public static boolean isFingerprintSupported(Context context) {
    // PackageManager.FEATURE_FINGERPRINT is supported since M.
    if (context == null) {
      return false;
    }

    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
  }

  public static boolean supportsVolumeKeyShortcuts() {
    return !BuildVersionUtils.isAtLeastO();
  }

  public static boolean disableAnimation() {
    return BuildVersionUtils.isAtLeastP();
  }

  public static boolean supportPaneTitles() {
    return BuildVersionUtils.isAtLeastP();
  }

  public static boolean supportReadClipboard() {
    return !BuildVersionUtils.isAtLeastQ();
  }

  public static boolean supportNotificationChannel() {
    return BuildVersionUtils.isAtLeastO();
  }

  public static boolean isHeadingWorks() {
    return BuildVersionUtils.isAtLeastN();
  }
}
