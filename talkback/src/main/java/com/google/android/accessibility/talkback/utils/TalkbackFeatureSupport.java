/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.accessibility.talkback.utils;

import android.os.Build;
import com.google.android.accessibility.utils.FormFactorUtils;

/** Methods to check whether to support talkback features. */
public class TalkbackFeatureSupport {
  private TalkbackFeatureSupport() {}

  /** Returns {@code true} if devices support text suggestion feature. */
  public static boolean supportTextSuggestion() {
    return !FormFactorUtils.getInstance().isAndroidWear();
  }

  /** Returns {@code true} if devices support speech recognize feature. */
  public static boolean supportSpeechRecognize() {
    return !FormFactorUtils.getInstance().isAndroidWear();
  }

  /**
   * Returns {@code true} if the device supports dynamic features.
   *
   * <p>Note: TalkBack dynamic features are icon description and image description that need the
   * device downloads libraries dynamically.
   *
   * <p>Note: TalkBack would not support dynamic features on x86, ATV, auto and wear platform.
   */
  public static boolean supportDynamicFeatures() {
    if (FormFactorUtils.getInstance().isAndroidTv()
        || FormFactorUtils.getInstance().isAndroidAuto()
        || FormFactorUtils.getInstance().isAndroidWear()) {
      return false;
    }
    for (String abs : Build.SUPPORTED_32_BIT_ABIS) {
      if (abs.contains("x86")) {
        return false;
      }
    }
    for (String abs : Build.SUPPORTED_64_BIT_ABIS) {
      if (abs.contains("x86_64")) {
        return false;
      }
    }
    return true;
  }

  public static boolean supportMultipleAutoScroll() {
    return FormFactorUtils.getInstance().isAndroidWear();
  }
}
