/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.content.Context;
import android.content.pm.PackageManager;

/** Utility class containing methods to query hardware states. */
public final class HardwareUtils {
  private HardwareUtils() {}

  /** Return whether fingerprint feature is supported on this device. */
  public static boolean isFingerprintSupported(Context context) {
    // PackageManager.FEATURE_FINGERPRINT is supported since M.
    if (!BuildVersionUtils.isAtLeastM() || context == null) {
      return false;
    }

    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
  }
}
