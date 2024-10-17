/*
 * Copyright 2023 Google Inc.
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

package com.google.android.accessibility.braille.common;

import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A logging handler for braille. */
public final class BrailleLog {

  /** Logs at Android "verbose" level. */
  public static void v(String logTag, String tag, String message) {
    LogUtils.v(logTag, "%s: %s", tag, message);
  }

  /** Logs at Android "debug" level. Logs a */
  public static void d(String logTag, String tag, String message) {
    LogUtils.d(logTag, "%s: %s", tag, message);
  }

  /** Logs at Android "information" level. */
  public static void i(String logTag, String tag, String message) {
    LogUtils.i(logTag, "%s: %s", tag, message);
  }

  /** Logs at Android "warning" level. */
  public static void w(String logTag, String tag, String message) {
    LogUtils.w(logTag, "%s: %s", tag, message);
  }

  /** Logs at Android "warning" level with {@code Throwable}. */
  public static void w(String logTag, String tag, String message, Throwable throwable) {
    LogUtils.w(logTag, throwable, "%s: %s", tag, message);
  }

  /** Logs at Android "error" level with {@code Throwable}. */
  public static void e(String logTag, String tag, String message, Throwable throwable) {
    LogUtils.e(logTag, throwable, "%s: %s", tag, message);
  }

  /** Logs at Android "error" level. */
  public static void e(String logTag, String tag, String message) {
    LogUtils.e(logTag, (Throwable) null, "%s: %s", tag, message);
  }

  private BrailleLog() {}
}
