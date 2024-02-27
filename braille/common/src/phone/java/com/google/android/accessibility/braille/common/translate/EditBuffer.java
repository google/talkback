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

package com.google.android.accessibility.braille.common.translate;

import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo;

/**
 * Receives and accumulates inputted {@link BrailleCharacter}, and commits the print translation of
 * that accumulation to the IME Editor appropriately.
 *
 * <p>Methods include append and delete mutators, which modify an active list of {@link
 * BrailleCharacter} contents. Some of these mutation invocations will result in a commission, which
 * means the translation of braille into print, and the transfer of that print to the IME Editor via
 * the InputConnection. The recognition of when to commit the contents is a function of the braille
 * language associated with this buffer, so an implementation will generally use a {@link
 * BrailleTranslator}, which converts braille into print in a language-specific way.
 *
 * <p>The invocation of any of the methods defined here will often involve signalling to the IME
 * Editor across the InputConnection (to effect the change of contents in that Editor).
 */
public interface EditBuffer {

  /**
   * Appends a {@link BrailleCharacter} to the end of the buffer, possibly mutating the IME Editor.
   */
  String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter);

  /** Appends a space (' ') to the end of the buffer, possibly mutating the IME Editor. */
  void appendSpace(ImeConnection imeConnection);

  /** Appends a newline to the end of the buffer, possibly mutating the IME Editor. */
  void appendNewline(ImeConnection imeConnection);

  /**
   * Deletes a single character backward from the end of the buffer, possibly mutating the IME
   * Editor.
   */
  void deleteCharacterBackward(ImeConnection imeConnection);

  /** Deletes a single character from the end of the buffer, possibly mutating the IME Editor. */
  void deleteCharacterForward(ImeConnection imeConnection);

  /** Deletes a word from the end of the buffer, possibly mutating the IME Editor. */
  void deleteWord(ImeConnection imeConnection);

  /** Commits all {@link BrailleCharacter} in the buffer, possibly mutating the IME Editor. */
  void commit(ImeConnection imeConnection);

  /** Moves the cursor of the buffer forward. */
  boolean moveCursorForward(ImeConnection imeConnection);

  /** Moves the cursor of the buffer backward. */
  boolean moveCursorBackward(ImeConnection imeConnection);

  /** Moves the cursor of the holdings. */
  default boolean moveHoldingsCursor(ImeConnection imeConnection, int index) {
    return false;
  }

  /** Moves the cursor in the text field. */
  boolean moveTextFieldCursor(ImeConnection imeConnection, int index);

  /** Moves the cursor to the beginning of text field. */
  boolean moveCursorToBeginning(ImeConnection imeConnection);

  /** Moves the cursor to the end of text field. */
  boolean moveCursorToEnd(ImeConnection imeConnection);

  /** Selects All text from the text field. */
  boolean selectAllText(ImeConnection imeConnection);

  /** Gets the holdings info for braille display. */
  HoldingsInfo getHoldingsInfo(ImeConnection imeConnection);
}
