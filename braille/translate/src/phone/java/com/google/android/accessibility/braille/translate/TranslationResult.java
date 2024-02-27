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

import static com.google.android.accessibility.braille.interfaces.BrailleCharacter.EMPTY_CELL;

import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.apps.common.proguard.UsedByNative;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of translating text to braille, including character to cell mappings in both
 * directions.
 *
 * <ul>
 *   For example, "phone|", the translation result in UEB2 is:
 *   <ul>
 *     <li>text: "phone"
 *     <li>cells: 1234-125-5-135
 *     <li>
 *         <ul>
 *           text to braille positions: [0,1,2,2,2]
 *           <li>with p mapping to 1234 at the index 0
 *           <li>with h mapping to 125 at the index 1
 *           <li>with o mapping to 5-135 starting at the index 2
 *           <li>with n mapping to 5-135 starting at the index 2
 *           <li>with e mapping to 5-135 starting at the index 2
 *         </ul>
 *         <ul>
 *           braille to text positions: [0,1,2,2]
 *           <li>with 123 mapping to p at the index 0
 *           <li>with 125 mapping to h at the index 1
 *           <li>with 5 mapping to one starting at the index 2
 *           <li>with 135 mapping to one starting at the index 2
 *         </ul>
 *     <li>cursor position in braille: 4
 */
@AutoValue
@UsedByNative("louis_translation.cc")
public abstract class TranslationResult {

  /** Texts to be translated in braille. */
  public abstract CharSequence text();

  /** Braille characters in byte array. */
  public abstract BrailleWord cells();
  /**
   * The mapping of each character of print to its position in braille. an(1-1345) is [0, 1]. a is
   * translated to 1, which is at the position 0 of all braille.
   */
  public abstract ImmutableList<Integer> textToBraillePositions();
  /**
   * The mapping of each braille character to its position in print. an(1-1345) is [0, 1]. 1 is is
   * the translation of a, which is at the position 0 of "an".
   */
  public abstract ImmutableList<Integer> brailleToTextPositions();

  /** The text cursor position in braille. */
  public abstract Integer cursorBytePosition();

  public static Builder builder() {
    return new AutoValue_TranslationResult.Builder();
  }

  /** Builder for translation result. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Returns text to be translated. */
    public abstract Builder setText(CharSequence text);

    /** Returns the braille cells corresponding to the original text. */
    public abstract Builder setCells(BrailleWord cells);

    /** Maps a position in the original text to the corresponding position in the braille cells. */
    public abstract Builder setTextToBraillePositions(List<Integer> textToBraillePositions);

    /** Maps a position in the braille cells to the corresponding position in the original text. */
    public abstract Builder setBrailleToTextPositions(List<Integer> brailleToTextPositions);

    /**
     * Returns the cursor position corresponding to the cursor position specified when translating
     * the text, or -1, if there was no cursor position specified.
     */
    public abstract Builder setCursorBytePosition(int cursorBytePosition);

    public abstract TranslationResult build();
  }

  /** Creates a result where all cells contain the special unknown, not-sure-what-to-render cell. */
  public static TranslationResult createUnknown(CharSequence text, int cursorPosition) {
    List<Integer> map = new ArrayList<>(text.length());
    BrailleWord translation = new BrailleWord();
    for (int i = 0; i < text.length(); i++) {
      map.add(i);
      translation.append(BrailleCharacter.FULL_CELL);
    }
    return TranslationResult.builder()
        .setText(text)
        .setCells(translation)
        .setTextToBraillePositions(map)
        .setBrailleToTextPositions(map)
        .setCursorBytePosition(cursorPosition)
        .build();
  }

  /** Corrects translation of text starts from {@code textStart} to {@code textEnd}. */
  public static TranslationResult correctTranslation(
      TranslationResult incorrectResult,
      BrailleWord correctBrailleWord,
      int textStart,
      int textEnd) {
    final BrailleWord brailleWord = new BrailleWord();
    final List<Integer> textToBraillePositions = new ArrayList<>();
    final List<Integer> brailleToTextPositions = new ArrayList<>();

    if (textStart == 0) {
      brailleToTextPositions.addAll(Collections.nCopies(correctBrailleWord.size(), 0));
      textToBraillePositions.addAll(Collections.nCopies(textEnd - textStart, 0));
    } else {
      int byteStart = incorrectResult.textToBraillePositions().get(textStart);
      brailleWord.append(incorrectResult.cells().subword(0, byteStart));
      textToBraillePositions.addAll(incorrectResult.textToBraillePositions().subList(0, textStart));
      brailleToTextPositions.addAll(incorrectResult.brailleToTextPositions().subList(0, byteStart));

      textToBraillePositions.addAll(Collections.nCopies(textEnd - textStart, brailleWord.size()));
      for (int i = 0; i < correctBrailleWord.size(); i++) {
        brailleToTextPositions.add(incorrectResult.text().subSequence(0, textStart).length());
      }
    }
    brailleWord.append(correctBrailleWord);

    if (textEnd != incorrectResult.text().length()) {
      int byteEnd = incorrectResult.textToBraillePositions().get(textEnd);
      int byteStart = incorrectResult.textToBraillePositions().get(textStart);
      brailleWord.append(incorrectResult.cells().subword(byteEnd, incorrectResult.cells().size()));
      for (int i :
          incorrectResult
              .textToBraillePositions()
              .subList(textEnd, incorrectResult.text().length())) {
        textToBraillePositions.add(byteStart + (i - byteEnd) + correctBrailleWord.size());
      }
      brailleToTextPositions.addAll(
          incorrectResult
              .brailleToTextPositions()
              .subList(byteEnd, incorrectResult.brailleToTextPositions().size()));
    }

    return TranslationResult.builder()
        .setText(incorrectResult.text())
        .setCells(brailleWord)
        .setTextToBraillePositions(textToBraillePositions)
        .setBrailleToTextPositions(brailleToTextPositions)
        .setCursorBytePosition(incorrectResult.cursorBytePosition())
        .build();
  }

  /** Extends translation result by one empty cell. */
  public static TranslationResult appendOneEmptyCell(TranslationResult result) {
    List<Integer> brailleToText = new ArrayList<>(result.textToBraillePositions());
    brailleToText.add(result.cells().size());
    List<Integer> textToBraille = new ArrayList<>(result.brailleToTextPositions());
    textToBraille.add(result.text().length());
    result.cells().append(EMPTY_CELL);
    return TranslationResult.builder()
        .setCells(result.cells())
        .setText(result.text() + " ")
        .setTextToBraillePositions(brailleToText)
        .setBrailleToTextPositions(textToBraille)
        .setCursorBytePosition(result.cursorBytePosition())
        .build();
  }
}
