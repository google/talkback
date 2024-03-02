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

package com.google.android.accessibility.braille.brailledisplay;

import com.google.android.accessibility.utils.BuildConfig;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Log handler which unifies logging under a single, BrailleDisplay-specific tag. */
public class BrailleDisplayLog {
  private static final String LOG_TAG = "BrailleDisplay";
  public static final boolean DEBUG = BuildConfig.DEBUG;

  private BrailleDisplayLog() {}

  public static int getLogLevel() {
    return LogUtils.getLogLevel();
  }

  public static void v(String tag, String message) {
    LogUtils.v(LOG_TAG, "%s: %s", tag, message);
  }

  public static void d(String tag, String message) {
    LogUtils.d(LOG_TAG, "%s: %s", tag, message);
  }

  public static void i(String tag, String message) {
    LogUtils.i(LOG_TAG, "%s: %s", tag, message);
  }

  public static void w(String tag, String message) {
    LogUtils.w(LOG_TAG, "%s: %s", tag, message);
  }

  public static void w(String tag, String message, Throwable throwable) {
    LogUtils.w(LOG_TAG, throwable, "%s: %s", tag, message);
  }

  public static void e(String tag, String message, Throwable throwable) {
    LogUtils.e(LOG_TAG, throwable, "%s: %s", tag, message);
  }

  public static void e(String tag, String message) {
    LogUtils.e(LOG_TAG, (Throwable) null, "%s: %s", tag, message);
  }
}
