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

package com.google.android.accessibility.brailleime.translate.stub;

import android.content.Context;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.android.accessibility.brailleime.BrailleWord;
import com.google.android.accessibility.brailleime.translate.Translator;
import com.google.android.accessibility.brailleime.translate.TranslatorFactory;
import java.util.Collections;

/** Creates Translator instances that return stub (dummy) translations. */
public class Stub implements TranslatorFactory {
  private static final BrailleCharacter BRAILLE_CHARACTER_DOT_1 = new BrailleCharacter(1);

  @Override
  public Translator create(Context context, Code code, boolean isContractedOn) {
    return new Translator() {
      @Override
      public String translateToPrint(BrailleWord brailleWord) {
        return translateToPrintCommon(brailleWord);
      }

      @Override
      public String translateToPrintPartial(BrailleWord brailleWord) {
        return translateToPrintCommon(brailleWord);
      }

      private String translateToPrintCommon(BrailleWord brailleWord) {
        if (brailleWord.isEmpty()) {
          return "";
        }
        return String.format("%0" + brailleWord.size() + "d", 0).replace('0', 'a');
      }

      @Override
      public BrailleWord translateToBraille(String text) {
        return new BrailleWord(Collections.nCopies(text.length(), BRAILLE_CHARACTER_DOT_1));
      }
    };
  }
}
