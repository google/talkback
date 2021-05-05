/*
 * Copyright 2020 Google Inc.
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

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A sequence of {@link BrailleCharacter}. */
public class BrailleWord {

  private final List<BrailleCharacter> list;

  /** Creates an empty word. */
  public BrailleWord() {
    list = new ArrayList<>();
  }

  /** Creates a word from a collection of {@link BrailleCharacter}. */
  public BrailleWord(Collection<BrailleCharacter> characters) {
    this();
    list.addAll(characters);
  }

  /**
   * Creates a word from a dash-delimited list of dot numbers.
   *
   * <p>For example the input "6-12" creates a word of two {@link BrailleCharacter} - the first is
   * the character corresponding with dot 6 and the second is the character corresponding with dots
   * 1 and 2.
   *
   * <p>Passing in the empty string returns an empty word.
   */
  public BrailleWord(String dashDelimited) {
    this();
    for (String token : Splitter.on('-').omitEmptyStrings().split(dashDelimited)) {
      List<Integer> dotNumbers = new ArrayList<>();
      for (int i = 0; i < token.length(); i++) {
        dotNumbers.add(Character.getNumericValue(token.charAt(i)));
      }
      list.add(new BrailleCharacter(dotNumbers));
    }
  }

  /** Creates a word from a var-args list of {@link BrailleCharacter}. */
  public BrailleWord(BrailleCharacter... brailleCharacters) {
    this(Arrays.asList(brailleCharacters));
  }

  public static BrailleWord create(String dashDelimited) {
    return new BrailleWord(dashDelimited);
  }

  /** Append a {@link BrailleCharacter} to the end of the word. */
  public void add(BrailleCharacter brailleCharacter) {
    list.add(brailleCharacter);
  }

  /**
   * Removes and returns the {@link BrailleCharacter} found at the specified index or throws {@link
   * IndexOutOfBoundsException} if {@code index < 0} or {@code index >= size()}.
   */
  public BrailleCharacter remove(int index) {
    return list.remove(index);
  }

  /**
   * Gets the {@link BrailleCharacter} at the specified {@code index}, or throws {@link
   * IndexOutOfBoundsException} if {@code index < 0} or {@code index >= size()}.
   */
  public BrailleCharacter get(int index) {
    return list.get(index);
  }

  /** Returns the size of the word, which is the number of {@link BrailleCharacter} it contains. */
  public int size() {
    return list.size();
  }

  /** Returns {@code true} if the word is empty. */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /** Clears the contents of word, making it empty. */
  public void clear() {
    list.clear();
  }

  /**
   * Returns an array of bytes representing this list, where each byte value comes from {@link
   * BrailleCharacter#toByte()}.
   */
  public byte[] toByteArray() {
    byte[] array = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) {
      BrailleCharacter brailleCharacter = list.get(i);
      array[i] = brailleCharacter.toByte();
    }
    return array;
  }

  /**
   * Returns a String representation of a list of {@link BrailleCharacter}.
   *
   * <p>The result is a dash-delimited concatenation of the result of invoking {@code toString} on
   * the individual {@link BrailleCharacter} elements.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      BrailleCharacter brailleCharacter = list.get(i);
      sb.append(brailleCharacter);
      if (i < list.size() - 1) {
        sb.append('-');
      }
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BrailleWord)) {
      return false;
    }
    BrailleWord that = (BrailleWord) o;
    return list.equals(that.list);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(list);
  }

  public List<BrailleCharacter> toList() {
    return new ArrayList<>(list);
  }

  /**
   * Returns a subword from the range [start, end), or throws {@link IndexOutOfBoundsException} if
   * the passed-in range is out of bounds.
   */
  public BrailleWord subword(int start, int end) {
    return new BrailleWord(list.subList(start, end));
  }

  /** Returns {@code true} if the word contains the passed-in {@link BrailleCharacter}. */
  public boolean contains(BrailleCharacter brailleCharacter) {
    return list.contains(brailleCharacter);
  }

  /**
   * Returns {@code true} if the word contains any of the {@link BrailleCharacter} contained in the
   * passed-in collection.
   */
  public boolean containsAny(Collection<BrailleCharacter> collection) {
    return !Collections.disjoint(list, collection);
  }

  /**
   * Tokenizes a word into subwords, where subword boundaries are defined by the {@code delimiters}.
   *
   * <p>The resulting list of words includes delimeters, as well as non-delimeters, and no non-empty
   * subwords are discarded by the tokenization.
   */
  public List<BrailleWord> tokenize(Collection<BrailleCharacter> delimiters) {
    List<BrailleWord> fragments = new ArrayList<>();
    int nonPoleFragmentAnchor = -1;
    for (int i = 0; i < size(); i++) {
      BrailleCharacter iCharacter = get(i);
      if (delimiters.contains(iCharacter)) {
        if (nonPoleFragmentAnchor >= 0) {
          fragments.add(subword(nonPoleFragmentAnchor, i));
          nonPoleFragmentAnchor = -1;
        }
        fragments.add(new BrailleWord(iCharacter));
      } else {
        if (nonPoleFragmentAnchor < 0) {
          nonPoleFragmentAnchor = i;
        }
      }
    }
    if (nonPoleFragmentAnchor >= 0) {
      fragments.add(subword(nonPoleFragmentAnchor, size()));
    }
    return fragments;
  }
}
