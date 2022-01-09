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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * An immutable configuration of braille dots, in a single braille cell, some of which are on
 * (raised).
 *
 * <p>There are 256 distinguishable {@link BrailleCharacter} objects, because there are 8 dots -
 * each of which can be on or off (raised or unraised).
 */
public class BrailleCharacter {

  private static final Range<Integer> DOT_RANGE = new Range<>(1, 8);

  private final BitSet dotNumbers;

  /**
   * Creates a {@link BrailleCharacter} from a collection of dot numbers.
   *
   * <p>The dot numbers must be in the range 1 to 8, otherwise {@link IllegalArgumentException} is
   * thrown.
   */
  public BrailleCharacter(Collection<Integer> dotNumbers) {
    this.dotNumbers = new BitSet();
    for (Integer dotNumber : dotNumbers) {
      checkArgument(DOT_RANGE.contains(dotNumber), "dot %s out of range %s", dotNumber, DOT_RANGE);
      // Maps dot number (1 to 8) to bit (0 to 7).
      this.dotNumbers.set(dotNumber - 1);
    }
  }

  /**
   * Creates a {@link BrailleCharacter} from a collection of dot numbers.
   *
   * <p>The dot numbers must be in the range 1 to 8, otherwise {@link IllegalArgumentException} is
   * thrown.
   */
  public BrailleCharacter(Integer... dotNumbers) {
    this(Arrays.asList(dotNumbers));
  }

  /**
   * Creates a {@link BrailleCharacter} from a collection of dot numbers.
   *
   * <p>The bits map to the dot numbers.
   */
  public BrailleCharacter(byte b) {
    this.dotNumbers = BitSet.valueOf(new byte[] {b});
  }

  /** Returns the number of on dots. */
  public int getOnCount() {
    return dotNumbers.cardinality();
  }

  /** Returns {@code true} if the character is empty (has zero on dots). */
  public boolean isEmpty() {
    return dotNumbers.isEmpty();
  }

  /**
   * Returns a byte that represents this {@link BrailleCharacter} via binary mapping.
   *
   * <p>For example, the character with dots 1 and 3 raised results in {@code 0b101}, or {@code 5}.
   */
  public byte toByte() {
    if (isEmpty()) {
      return 0;
    }
    return dotNumbers.toByteArray()[0];
  }

  /**
   * Returns an int that represents this {@link BrailleCharacter} via binary mapping.
   *
   * <p>For example, the character with dots 1 and 3 raised results in {@code 0b101}, or {@code 5}.
   */
  public int toInt() {
    return toByte() & 0xFF;
  }

  /**
   * Returns a list of the dot numbers that represents this {@link BrailleCharacter}.
   *
   * <p>The dot numbers are in the range 1 to 8.
   */
  public List<Integer> toDotNumbers() {
    List<Integer> dotNumberList = new ArrayList<>();
    for (int index = dotNumbers.nextSetBit(0);
        index != -1;
        index = dotNumbers.nextSetBit(index + 1)) {
      dotNumberList.add(index + 1);
    }
    return dotNumberList;
  }

  /** Returns whether the given dot number is on. */
  public boolean isDotNumberOn(int dotNumber) {
    return dotNumbers.get(dotNumber - 1);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int bitIndex = 0; bitIndex < dotNumbers.length(); bitIndex++) {
      if (dotNumbers.get(bitIndex)) {
        // Maps bit 0 to dot number 1.
        sb.append(bitIndex + 1);
      }
    }
    return sb.toString();
  }

  /** Returns the unicode represenation of this {@link BrailleCharacter}. */
  public char asUnicodeChar() {
    return BrailleUnicode.fromOffset(toInt());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BrailleCharacter)) {
      return false;
    }
    BrailleCharacter that = (BrailleCharacter) o;
    return dotNumbers.equals(that.dotNumbers);
  }

  @Override
  public int hashCode() {
    return dotNumbers.hashCode();
  }
}
