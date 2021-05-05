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

package com.google.android.accessibility.brailleime.translate.liblouis;

import android.content.Context;
import com.google.android.accessibility.brailleime.translate.BrailleTranslateUtilsSpanish;
import java.util.LinkedHashMap;
import java.util.Map;

class LibLouisTranslatorSpanish extends LibLouisTranslator {

  LibLouisTranslatorSpanish(Context context) {
    super(context, "es-g1.ctb");
    setupCommutativityReplacementMap();
  }

  private void setupCommutativityReplacementMap() {
    // LibLouis's Spanish translator translates "3" as apostrophe, but in almost all cases, "3"
    // should be translated as a period.
    getCommutativityMap().put(BrailleTranslateUtilsSpanish.PERIOD, ".");

    // LibLouis's Spanish translator translates "23" as 2. It should be ;.
    getCommutativityMap().put(BrailleTranslateUtilsSpanish.SEMICOLON, ";");

    // LibLouis's Spanish translator translates "25" as 3. It should be :.
    getCommutativityMap().put(BrailleTranslateUtilsSpanish.COLON, ":");

    // LibLouis's Spanish translator translates "26" as 5. It should be ?.
    getCommutativityMap().put(BrailleTranslateUtilsSpanish.QUESTION_MARK, "?");
  }

  private static final String[] INVERTIBLE_PUNCTUATION_SEQUENCES = {
    "?", "!", "?!", "!?",
  };

  private static final Map<Character, Character> PUNCTUATION_INVERSE_MAP = new LinkedHashMap<>();

  static {
    PUNCTUATION_INVERSE_MAP.put('?', '¿');
    PUNCTUATION_INVERSE_MAP.put('!', '¡');
  }

  @Override
  protected String transformPostTranslation(String translation) {
    if (translation.length() > 0) {
      char char0 = translation.charAt(0);
      // If the translation begins with an invertible punctuation character, invert that character
      // in accordance with Spanish orthographic custom.
      if (PUNCTUATION_INVERSE_MAP.containsKey(char0)) {
        for (String invertiblePunctuationSequence : INVERTIBLE_PUNCTUATION_SEQUENCES) {
          if (translation.startsWith(invertiblePunctuationSequence)) {
            translation =
                invertPunctuation(invertiblePunctuationSequence)
                    + translation.substring(invertiblePunctuationSequence.length());
          }
        }
      }
    }
    return translation;
  }

  private static String invertPunctuation(String string) {
    return string.replace('?', '¿').replace('!', '¡');
  }
}
