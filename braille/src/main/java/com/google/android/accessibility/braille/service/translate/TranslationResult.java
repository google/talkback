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

package com.google.android.accessibility.braille.service.translate;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.apps.common.proguard.UsedByNative;

/**
 * The result of translating text to braille, including character to cell mappings in both
 * directions.
 */
@UsedByNative("TranslationResult.java")
public class TranslationResult implements Parcelable {
  private final byte[] cells;
  private final int[] textToBraillePositions;
  private final int[] brailleToTextPositions;
  private final int cursorPosition;

  @UsedByNative("TranslationResult.java")
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
}
