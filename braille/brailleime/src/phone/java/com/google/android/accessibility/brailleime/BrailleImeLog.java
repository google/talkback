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

import com.google.android.accessibility.braille.common.BrailleLog;

/** Log handler which unifies logging under a single, BrailleIme-specific tag. */
public class BrailleImeLog {
  private static final String TAG = "BrailleIme";

  /** Logs at Android "verbose" level. */
  public static void v(String tag, String message) {
    BrailleLog.v(TAG, tag, message);
  }

  /** Logs at Android "debug" level. Logs a */
  public static void d(String tag, String message) {
    BrailleLog.d(TAG, tag, message);
  }

  /** Logs at Android "information" level. */
  public static void i(String tag, String message) {
    BrailleLog.i(TAG, tag, message);
  }

  /** Logs at Android "warning" level. */
  public static void w(String tag, String message) {
    BrailleLog.w(TAG, tag, message);
  }

  /** Logs at Android "warning" level with {@code Throwable}. */
  public static void w(String tag, String message, Throwable throwable) {
    BrailleLog.w(TAG, tag, message, throwable);
  }

  /** Logs at Android "error" level with {@code Throwable}. */
  public static void e(String tag, String message, Throwable throwable) {
    BrailleLog.e(TAG, tag, message, throwable);
  }

  /** Logs at Android "error" level. */
  public static void e(String tag, String message) {
    BrailleLog.e(TAG, tag, message);
  }
}
