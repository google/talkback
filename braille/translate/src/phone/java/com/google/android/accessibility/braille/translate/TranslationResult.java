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

package com.google.android.accessibility.braille.translate;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.accessibility.braille.interfaces.BrailleDots;
import com.google.android.apps.common.proguard.UsedByNative;
import java.util.Arrays;
import java.util.Objects;

/**
 * The result of translating text to braille, including character to cell mappings in both
 * directions.
 */
@UsedByNative("TranslationResult.java")
public class TranslationResult implements Parcelable {

  /** Braille characters in byte array. */
  private final byte[] cells;
  /**
   * The mapping of each character of print to its position in braille. an(1-1345) is [0, 1]. a is
   * translated to 1, which is at the position 0 of all braille.
   */
  private final int[] textToBraillePositions;
  /**
   * The mapping of each braille character to its position in print. an(1-1345) is [0, 1]. 1 is is
   * the translation of a, which is at the position 0 of "an".
   */
  private final int[] brailleToTextPositions;
  /** The text cursor position in braille. */
  private final int cursorPosition;

  @UsedByNative("TranslationResult.java")
  /**
   * Creates a translation result of print to braille, the mapping of each character of print to
   * braille, the mapping of each braille to print and the cursor position in braille.
   */
  public TranslationResult(
      byte[] cells,
      int[] textToBraillePositions,
      int[] brailleToTextPositions,
      int cursorPosition) {
    this.cells = cells;
    this.textToBraillePositions = textToBraillePositions;
    this.brailleToTextPositions = brailleToTextPositions;
    this.cursorPosition = cursorPosition;
  }

  private TranslationResult(Parcel in) {
    cells = in.createByteArray();
    textToBraillePositions = in.createIntArray();
    brailleToTextPositions = in.createIntArray();
    cursorPosition = in.readInt();
  }

  /** Returns the braille cells corresponding to the original text. */
  public byte[] getCells() {
    return cells;
  }

  /** Maps a position in the original text to the corresponding position in the braille cells. */
  public int[] getTextToBraillePositions() {
    return textToBraillePositions;
  }

  /** Maps a position in the braille cells to the corresponding position in the original text. */
  public int[] getBrailleToTextPositions() {
    return brailleToTextPositions;
  }

  /**
   * Returns the cursor position corresponding to the cursor position specified when translating the
   * text, or -1, if there was no cursor position specified.
   */
  public int getCursorPosition() {
    return cursorPosition;
  }

  // For Parcelable support.

  public static final Creator<TranslationResult> CREATOR =
      new Creator<TranslationResult>() {
        @Override
        public TranslationResult createFromParcel(Parcel in) {
          return new TranslationResult(in);
        }

        @Override
        public TranslationResult[] newArray(int size) {
          return new TranslationResult[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeByteArray(cells);
    out.writeIntArray(textToBraillePositions);
    out.writeIntArray(brailleToTextPositions);
    out.writeInt(cursorPosition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        Arrays.hashCode(cells),
        Arrays.hashCode(textToBraillePositions),
        Arrays.hashCode(brailleToTextPositions),
        cursorPosition);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TranslationResult)) {
      return false;
    }
    TranslationResult translationResult = (TranslationResult) o;
    return Arrays.equals(cells, translationResult.cells)
        && Arrays.equals(textToBraillePositions, translationResult.textToBraillePositions)
        && Arrays.equals(brailleToTextPositions, translationResult.brailleToTextPositions)
        && cursorPosition == translationResult.cursorPosition;
  }

  /** Creates a result where all cells contain the special unknown, not-sure-what-to-render cell. */
  public static TranslationResult createUnknown(String text, int cursorPosition) {
    int[] map = new int[text.length()];
    byte[] translation = new byte[text.length()];
    for (int i = 0; i < text.length(); i++) {
      map[i] = i;
      translation[i] = (byte) BrailleDots.FULL_CELL;
    }
    return new TranslationResult(translation, map, map, cursorPosition);
  }
}
