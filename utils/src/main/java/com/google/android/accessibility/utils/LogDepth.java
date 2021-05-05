/*
 * Copyright (C) 2020 Google Inc.
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

import android.util.Log;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility class to arrange verbose log depths before calling {@link LogUtils}. */
public class LogDepth {

  private LogDepth() {}

  /**
   * Log verbose variables by depths.
   *
   * @param tag The tag that should be associated with the event
   * @param depth Add more indents before the log if depth increased
   * @param variableName The name of the variable
   * @param variableValue The value of the variable
   */
  public static void logVar(
      String tag, int depth, String variableName, @Nullable Object variableValue) {
    log(tag, depth, "%s=%s", variableName, variableValue);
  }

  /**
   * Log verbose function name by depths.
   *
   * @param tag The tag that should be associated with the event
   * @param depth Add more indents before the log if depth increased
   * @param functionName The name of the function
   */
  public static void logFunc(String tag, int depth, String functionName) {
    log(tag, depth, "%s()", functionName);
  }

  /**
   * Log verbose string by depths.
   *
   * @param tag The tag that should be associated with the event
   * @param depth Add two space indents before the log if depth increased
   * @param format A format string, see {@link String#format(String, Object...)}
   * @param args String formatter arguments
   */
  @FormatMethod
  public static void log(String tag, int depth, @FormatString String format, Object... args) {
    if (LogUtils.shouldLog(Log.VERBOSE) && depth >= 0) {
      String indent = StringBuilderUtils.repeatChar(' ', depth * 2);
      String messageStr = String.format(format, args);
      LogUtils.v(tag, "%s %s", indent, messageStr);
    }
  }
}
