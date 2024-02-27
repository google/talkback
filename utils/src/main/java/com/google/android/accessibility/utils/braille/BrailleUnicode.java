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

package com.google.android.accessibility.utils.braille;

import static com.google.common.base.Preconditions.checkArgument;

import android.util.Range;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Utilities for Braille unicode characters, which are the 256 characters beginning at U+2800. */
public class BrailleUnicode {

  private BrailleUnicode() {}

  private static final int DOT_COUNT = 8;
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

  /** Returns {@code true} if {@code c} is in the range of Braille unicode symbols. */
  public static boolean isBraille(char c) {
    return BRAILLE_CHAR_RANGE.contains(c);
  }

  /**
   * Returns a list of the dot numbers.
   *
   * <p>The dot numbers are in the range 1 to 8.
   */
  public static List<Integer> toDotNumbers(char c) {
    int offset = (int) c - BRAILLE_CHAR_RANGE.getLower();
    checkArgument(OFFSET_RANGE.contains(offset), "offset %s out of range %s", offset, OFFSET_RANGE);
    return toDotNumbers(BitSet.valueOf(new byte[] {(byte) offset}));
  }

  /**
   * Returns a list of the dot numbers.
   *
   * <p>The dot numbers are in the range 1 to 8.
   */
  public static List<Integer> toDotNumbers(BitSet dotNumbers) {
    checkArgument(
        dotNumbers.length() <= DOT_COUNT, "Bitset length %s out of range", dotNumbers.length());
    List<Integer> dotNumberList = new ArrayList<>();
    for (int index = dotNumbers.nextSetBit(0);
        index != -1;
        index = dotNumbers.nextSetBit(index + 1)) {
      dotNumberList.add(index + 1);
    }
    return dotNumberList;
  }

  /** Converts braille symbol to dot numbers string. */
  public static String toDotNumbersString(char c) {
    List<Integer> dotNumberList = toDotNumbers(c);
    StringBuilder sb = new StringBuilder();
    for (int index = 0; index < dotNumberList.size(); index++) {
      sb.append(dotNumberList.get(index));
    }
    return sb.toString();
  }
}
