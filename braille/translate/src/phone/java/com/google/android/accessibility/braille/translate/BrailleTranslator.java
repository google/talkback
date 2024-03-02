/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.braille.translate;

import com.google.android.accessibility.braille.interfaces.BrailleWord;

/**
 * Translates from text to braille and the other way according to a particular translation table.
 */
public interface BrailleTranslator {
  /** Translates a {@link BrailleWord} into a String (print). */
  String translateToPrint(BrailleWord brailleWord);

  /** Translates a partially-constructed {@link BrailleWord} into a String (print). */
  String translateToPrintPartial(BrailleWord brailleWord);

  /**
   * Translates a string into the corresponding dot patterns and returns the resulting byte array.
   * Returns {@code null} on error. {@code cursorPosition}, if positive, will be mapped to the
   * corresponding position in the output. This is sometimes more accurate than the position maps in
   * the {@link TranslationResult}.
   */
  TranslationResult translate(CharSequence text, int cursorPosition);
}
