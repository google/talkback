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

package com.google.android.accessibility.brailleime.translate;

import android.view.inputmethod.CursorAnchorInfo;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleIme.ImeConnection;

/**
 * Receives and accumulates inputted {@link BrailleCharacter}, and commits the print translation of
 * that accumulation to the IME Editor appropriately.
 *
 * <p>Methods include append and delete mutators, which modify an active list of {@link
 * BrailleCharacter} contents. Some of these mutation invocations will result in a commission, which
 * means the translation of braille into print, and the transfer of that print to the IME Editor via
 * the InputConnection. The recognition of when to commit the contents is a function of the braille
 * language associated with this buffer, so an implementation will generally use a {@link
 * Translator}, which converts braille into print in a language-specific way.
 *
 * <p>The invocation of any of the methods defined here will often involve signalling to the IME
 * Editor across the InputConnection (to effect the change of contents in that Editor).
 */
public interface EditBuffer {

  /**
   * Append a {@link BrailleCharacter} to the end of the buffer, possibly mutating the IME Editor.
   */
  String appendBraille(ImeConnection inputConnection, BrailleCharacter brailleCharacter);

  /** Append a space (' ') to the end of the buffer, possibly mutating the IME Editor. */
  void appendSpace(ImeConnection inputConnection);

  /** Append a newline to the end of the buffer, possibly mutating the IME Editor. */
  void appendNewline(ImeConnection inputConnection);

  /** Delete a single character from the end of the buffer, possibly mutating the IME Editor. */
  void deleteCharacter(ImeConnection inputConnection);

  /** Delete a word from the end of the buffer, possibly mutating the IME Editor. */
  void deleteWord(ImeConnection inputConnection);

  /** Commits all {@link BrailleCharacter} in the buffer, possibly mutating the IME Editor. */
  void commit(ImeConnection inputConnection);

  /**
   * Notify that the IME Editor's cursor moved.
   *
   * <p>In this case, the EditBuffer may want to commit its contents to the IME Editor, especially
   * if it uses composing text.
   */
  void onUpdateCursorAnchorInfo(ImeConnection imeConnection, CursorAnchorInfo cursorAnchorInfo);
}
