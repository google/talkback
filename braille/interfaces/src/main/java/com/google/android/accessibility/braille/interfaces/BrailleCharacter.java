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

package com.google.android.accessibility.braille.interfaces;

import static com.google.common.base.Preconditions.checkArgument;

import android.icu.text.NumberFormat;
import android.util.Range;
import com.google.android.accessibility.utils.braille.BrailleUnicode;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * An immutable configuration of braille dots, in a single braille cell, some of which are on
 * (raised).
 *
 * <p>There are 256 distinguishable {@link BrailleCharacter} objects, because there are 8 dots -
 * each of which can be on or off (raised or unraised).
 */
public class BrailleCharacter {
  public static final BrailleCharacter EMPTY_CELL = new BrailleCharacter();
  public static final BrailleCharacter FULL_CELL = new BrailleCharacter("12345678");
  public static final BrailleCharacter DOT1 = new BrailleCharacter("1");
  public static final BrailleCharacter DOT2 = new BrailleCharacter("2");
  public static final BrailleCharacter DOT3 = new BrailleCharacter("3");
  public static final BrailleCharacter DOT4 = new BrailleCharacter("4");
  public static final BrailleCharacter DOT5 = new BrailleCharacter("5");
  public static final BrailleCharacter DOT6 = new BrailleCharacter("6");
  public static final BrailleCharacter DOT7 = new BrailleCharacter("7");
  public static final BrailleCharacter DOT8 = new BrailleCharacter("8");
  public static final BrailleCharacter DOT12 = new BrailleCharacter("12");

  public static final int DOT_COUNT = 8;
  private static final Range<Integer> DOT_RANGE = new Range<>(1, DOT_COUNT);
  private final BitSet dotNumbers;

  /** Creates an empty braille character. */
  public BrailleCharacter() {
    this.dotNumbers = new BitSet(DOT_COUNT);
  }

  /**
   * Creates a {@link BrailleCharacter} from a collection of dot numbers.
   *
   * <p>The dot numbers must be in the range 1 to 8, otherwise {@link IllegalArgumentException} is
   * thrown.
   */
  public BrailleCharacter(Collection<Integer> dotNumbers) {
    this();
    for (Integer dotNumber : dotNumbers) {
      checkArgument(DOT_RANGE.contains(dotNumber), "dot %s out of range %s", dotNumber, DOT_RANGE);
      // Maps dot number (1 to 8) to bit (0 to 7).
      this.dotNumbers.set(dotNumber - 1);
    }
  }

  /**
   * Creates a braille character from dot numbers string.
   *
   * <p>For example the input "12" creates a {@link BrailleCharacter} with dots 1 and 2.
   *
   * <p>Passing in the empty string returns an empty character.
   */
  public BrailleCharacter(String dots) {
    this();
    for (char c : dots.toCharArray()) {
      int digit = Character.digit(c, DOT_COUNT + 1);
      checkArgument(digit != -1, "dot %s out of range", c);
      this.dotNumbers.set(digit - 1);
    }
  }

  /**
   * Creates a {@link BrailleCharacter} from a BitSet.
   *
   * <p>The length must be less than 8, otherwise {@link IllegalArgumentException} is thrown.
   */
  public BrailleCharacter(BitSet bitSet) {
    checkArgument(bitSet.length() <= DOT_COUNT, "Bitset length %s out of range", bitSet.length());
    this.dotNumbers = bitSet;
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

  /** Intersects this {@link BrailleCharacter} with another, creating a new one. */
  public BrailleCharacter intersect(BrailleCharacter arg) {
    BitSet dotNumbersCopy = (BitSet) dotNumbers.clone();
    dotNumbersCopy.and(arg.dotNumbers);
    return new BrailleCharacter(dotNumbersCopy);
  }

  /** Intersects this {@link BrailleCharacter} with another, mutating this. */
  public void intersectMutate(BrailleCharacter arg) {
    dotNumbers.and(arg.dotNumbers);
  }

  /** Unions this {@link BrailleCharacter} with another, creating a new one. */
  public BrailleCharacter union(BrailleCharacter arg) {
    BitSet dotNumbersCopy = (BitSet) dotNumbers.clone();
    dotNumbersCopy.or(arg.dotNumbers);
    return new BrailleCharacter(dotNumbersCopy);
  }

  /** Unions this {@link BrailleCharacter} with another, mutating this. */
  public void unionMutate(BrailleCharacter arg) {
    dotNumbers.or(arg.dotNumbers);
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
    return BrailleUnicode.toDotNumbers(dotNumbers);
  }

  /** Returns whether the given dot number is on. */
  public boolean isDotNumberOn(int dotNumber) {
    return dotNumbers.get(dotNumber - 1);
  }

  /** Swaps the dot values of 1<->4, 2<->5, 3<->6, 7<->8. */
  public BrailleCharacter toMirror() {
    byte allDots = toByte();
    byte dot123To456 = (byte) ((allDots << 3) & 0b00111000);
    byte dot456To123 = (byte) ((allDots >> 3) & 0b00000111);
    byte dot7To8 = (byte) ((allDots << 1) & 0b10000000);
    byte dot8To7 = (byte) ((allDots >> 1) & 0b01000000);
    return new BrailleCharacter((byte) (dot123To456 | dot456To123 | dot7To8 | dot8To7));
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

  /** Returns the number string using the current locale. */
  public String toLocaleString() {
    StringBuilder sb = new StringBuilder();
    NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
    for (int bitIndex = 0; bitIndex < dotNumbers.length(); bitIndex++) {
      if (dotNumbers.get(bitIndex)) {
        // Maps bit 0 to dot number 1.
        sb.append(numberFormat.format(bitIndex + 1));
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
