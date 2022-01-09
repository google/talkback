/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Build;
import androidx.core.os.BuildCompat;

/**
 * This file provides a wrapper for the Build versions. Everytime an android version number gets
 * fixed, this file should be updated. Generally, BuildCompat.isAtLeast*() works before android
 * release is finalized, Build.VERSION_CODES.* works after.
 */
public class BuildVersionUtils {

  // TODO: Use Build.VERSION_CODES.S when available.
  public static final int API_S = 31;

  public static boolean isM() {
    return Build.VERSION.SDK_INT == Build.VERSION_CODES.M;
  }

  public static boolean isAtLeastN() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
  }

  public static boolean isAtLeastNMR1() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
  }

  public static boolean isAtLeastO() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
  }

  public static boolean isAtLeastOMR1() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;
  }

  public static boolean isAtLeastP() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
  }

  public static boolean isAtLeastQ() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
  }

  public static boolean isAtLeastR() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
  }

  public static boolean isAtLeastS() {
    return Build.VERSION.SDK_INT >= API_S || BuildCompat.isAtLeastS();
  }
}
