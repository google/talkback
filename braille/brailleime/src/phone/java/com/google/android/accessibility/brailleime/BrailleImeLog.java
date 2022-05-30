/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime;

import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Log handler which unifies logging under a single, BrailleIme-specific tag. */
public class BrailleImeLog {
  private static final String LOG_TAG = "BrailleIme";

  private BrailleImeLog() {}

  public static void logD(String tag, String message) {
    LogUtils.d(LOG_TAG, "%s: %s", tag, message);
  }

  public static void logE(String tag, String message, Throwable throwable) {
    LogUtils.e(LOG_TAG, throwable, "%s: %s", tag, message);
  }

  public static void logE(String tag, String message) {
    LogUtils.e(LOG_TAG, (Throwable) null, "%s: %s", tag, message);
  }
}
