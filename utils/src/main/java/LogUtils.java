/*
 * Copyright (C) 2011 Google Inc.
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

import android.util.Log;
import java.util.IllegalFormatException;

/** Handles logging formatted strings. */
public class LogUtils {
  private static final String TAG = "LogUtils";

  /**
   * The minimum log level that will be printed to the console. Set this to {@link Log#ERROR} for
   * release or {@link Log#VERBOSE} for debugging.
   */
  private static int sLogLevel = Log.ERROR;

  /**
   * Logs a formatted string to the console using the source object's name as the log tag. If the
   * source object is null, the default tag (see {@link LogUtils#TAG} is used.
   *
   * <p>Example usage: <br>
   * <code>
   * LogUtils.log(this, Log.ERROR, "Invalid value: %d", value);
   * </code>
   *
   * @param source The object that generated the log event.
   * @param priority The log entry priority, see {@link Log#println(int, String, String)}.
   * @param format A format string, see {@link String#format(String, Object...)}.
   * @param args String formatter arguments.
   */
  public static void log(Object source, int priority, String format, Object... args) {
    if (priority < sLogLevel) {
      return;
    }

    final String sourceClass;

    if (source == null) {
      sourceClass = TAG;
    } else if (source instanceof Class<?>) {
      sourceClass = ((Class<?>) source).getSimpleName();
    } else {
      sourceClass = source.getClass().getSimpleName();
    }

    try {
      Log.println(priority, sourceClass, String.format(format, args));
    } catch (IllegalFormatException e) {
      Log.e(TAG, "Bad formatting string: \"" + format + "\"", e);
    }
  }

  /**
   * Logs a formatted string to the console using the default tag (see {@link LogUtils#TAG}.
   *
   * @param priority The log entry priority, see {@link Log#println(int, String, String)}.
   * @param format A format string, see {@link String#format(String, Object...)}.
   * @param args String formatter arguments.
   */
  public static void log(int priority, String format, Object... args) {
    log(null, priority, format, args);
  }

  /**
   * Logs a formatted string to the console using the default tag (see {@link LogUtils#TAG}. If the
   * index is greater than the limit, then the log entry is skipped.
   *
   * @param index The index of the log entry in the current log sequence.
   * @param limit The maximum number of log entries allowed in the current sequence.
   * @param priority The log entry priority, see {@link Log#println(int, String, String)}.
   * @param format A format string, see {@link String#format(String, Object...)}.
   * @param args String formatter arguments.
   */
  public static void logWithLimit(
      Object source, int priority, int index, int limit, String format, Object... args) {
    String formatWithIndex;
    if (index > limit) {
      return;
    } else if (index == limit) {
      formatWithIndex = String.format("%s (%d); further messages suppressed", format, index);
    } else {
      formatWithIndex = String.format("%s (%d)", format, index);
    }

    log(source, priority, formatWithIndex, args);
  }

  /**
   * Sets the log display level.
   *
   * @param logLevel The minimum log level that will be printed to the console.
   */
  public static void setLogLevel(int logLevel) {
    sLogLevel = logLevel;
  }

  /** Gets the log display level. */
  public static int getLogLevel() {
    return sLogLevel;
  }
}
