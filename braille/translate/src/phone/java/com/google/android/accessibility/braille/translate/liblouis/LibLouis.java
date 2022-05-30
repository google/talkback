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

package com.google.android.accessibility.braille.translate.liblouis;

import android.content.Context;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslatorFactory;

/** Creates Translator instances that delegate to the LibLouis braille translation library. */
public class LibLouis implements TranslatorFactory {

  @Override
  public BrailleTranslator create(Context context, String codeName) {
    if (codeName.equals("FRENCH")) {
      return new LibLouisTranslatorFrench(context);
    }
    if (codeName.equals("POLISH")) {
      return new LibLouisTranslatorPolish(context);
    }
    if (codeName.equals("SPANISH")) {
      return new LibLouisTranslatorSpanish(context);
    }
    if (codeName.equals("UEB_1")) {
      return new LibLouisTranslatorUeb1(context);
    }
    if (codeName.equals("UEB_2")) {
      return new ExpandableContractedTranslator(
          new LibLouisTranslatorUeb1(context), new LibLouisTranslatorUeb2(context));
    }
    if (codeName.equals("ARABIC")) {
      return new LibLouisTranslatorArabic(context);
    }
    throw new IllegalArgumentException("unrecognized code " + codeName);
  }
}
