/*
 * Copyright (C) 2023 Google Inc.
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
import androidx.annotation.VisibleForTesting;

/** Methods to return the form factor which TalkBack is running on. */
public class FormFactorUtils {

  private static FormFactorUtils instance;

  public static void initialize(Context context) {
    instance = new FormFactorUtils(context);
  }

  public static FormFactorUtils getInstance() {
    if (instance == null) {
      throw new IllegalStateException(
          "We should initialize FormFactorUtils before getting instance.");
    }
    return instance;
  }

  private final boolean isAndroidAuto;
  private final boolean isAndroidWear;
  private final boolean isAndroidTv;

  @VisibleForTesting
  FormFactorUtils(Context context) {
    isAndroidAuto = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    isAndroidWear = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    isAndroidTv = initIsAndroidTv(context);
  }

  public boolean isAndroidAuto() {
    return isAndroidAuto;
  }

  public boolean isAndroidWear() {
    return isAndroidWear;
  }

  public boolean isAndroidTv() {
    return isAndroidTv;
  }

  /** Returns whether TB is running on Android Tv. */
  private static boolean initIsAndroidTv(Context context) {
    UiModeManager modeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    return ((modeManager != null)
        && (modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION));
  }
}
