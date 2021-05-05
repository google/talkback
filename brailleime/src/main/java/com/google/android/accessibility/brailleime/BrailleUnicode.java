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

import static com.google.common.base.Preconditions.checkArgument;

import android.util.Range;

/** Utilities for Braille unicode characters, which are the 256 characters beginning at U+2800. */
public class BrailleUnicode {

  private BrailleUnicode() {}

  private static final Range<Character> BRAILLE_CHAR_RANGE = new Range<>('\u2800', '\u28FF');
  private static final Range<Integer> OFFSET_RANGE =
      new Range<>(0, BRAILLE_CHAR_RANGE.getUpper() - BRAILLE_CHAR_RANGE.getLower());

  /**
   * Converts an offset into the braille unicode region into a specific Braille character.
   *
   * <p>For example, 15 in decimal (0b1111 in binary) converts to '⠏'.
   */
  public static char fromOffset(int offset) {
    checkArgument(OFFSET_RANGE.contains(offset), "offset %s out of range %s", offset, OFFSET_RANGE);
    return (char) (BRAILLE_CHAR_RANGE.getLower() + offset);
  }

  /** Returns {@code true} if {@param c} is in the range of Braille unicode symbols. */
  public static boolean isBraille(char c) {
    return BRAILLE_CHAR_RANGE.contains(c);
  }
}
