/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller.utils;

import android.text.Editable;
import android.text.TextUtils;

/** Utilities for {@link String}s and other {@link CharSequence}s. */
public class StringUtils {
  private static final char SPACE = ' ';

  public static Editable appendWithSpaces(Editable editable, Object... args) {
    for (Object arg : args) {
      if (arg == null) {
        continue;
      }
      if (editable.length() > 0
          && !endsWithSpace(editable)
          && !TextUtils.isEmpty(String.valueOf(arg))) {
        editable.append(SPACE);
      }
      if (arg instanceof CharSequence) {
        editable.append((CharSequence) arg);
      } else {
        editable.append(String.valueOf(arg));
      }
    }
    return editable;
  }

  public static StringBuilder appendWithSpaces(StringBuilder builder, Object... args) {
    for (Object arg : args) {
      if (arg == null) {
        continue;
      }
      if (builder.length() > 0 && !endsWithSpace(builder)) {
        builder.append(SPACE);
      }
      builder.append(arg);
    }
    return builder;
  }

  /** Inserts text into {@link Editable} with a space in front of it. */
  public static Editable insertWithSpaces(Editable editable, int index, Object arg) {
    if (arg == null) {
      return editable;
    }
    String space = String.valueOf(SPACE);
    if (editable.length() > 0 && !TextUtils.isEmpty(String.valueOf(arg))) {
      editable.insert(index, String.valueOf(SPACE));
    }
    if (arg instanceof CharSequence) {
      editable.insert(index + space.length(), (CharSequence) arg);
    } else {
      editable.insert(index + space.length(), String.valueOf(arg));
    }
    return editable;
  }

  /** Returns [@code true} if the last code point of {@code seq} is a space character. */
  public static boolean endsWithSpace(CharSequence seq) {
    if (seq.length() == 0) {
      return false;
    }
    return Character.isSpaceChar(Character.codePointBefore(seq, seq.length()));
  }

  private StringUtils() {}
}
