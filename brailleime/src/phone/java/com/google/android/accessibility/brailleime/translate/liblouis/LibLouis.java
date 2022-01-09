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

package com.google.android.accessibility.brailleime.translate.liblouis;

import android.content.Context;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.android.accessibility.brailleime.translate.Translator;
import com.google.android.accessibility.brailleime.translate.TranslatorFactory;
import com.google.android.accessibility.brailleime.translate.stub.Stub;

/** Creates Translator instances that delegate to the LibLouis braille translation library. */
public class LibLouis implements TranslatorFactory {

  @Override
  public Translator create(Context context, Code code, boolean contractedMode) {
    if (code.equals(Code.FRENCH)) {
      return new LibLouisTranslatorFrench(context);
    }
    if (code.equals(Code.SPANISH)) {
      return new LibLouisTranslatorSpanish(context);
    }
    if (code.equals(Code.UEB)) {
      if (contractedMode) {
        return new LibLouisTranslatorUeb2(context);
      }
      return new LibLouisTranslatorUeb1(context);
    }
    if (code.equals(Code.ARABIC)) {
      return new LibLouisTranslatorArabic(context);
    }
    if (code.equals(Code.STUB)) {
      return new Stub().create(context, code, contractedMode);
    }
    throw new IllegalArgumentException("unrecognized code " + code);
  }

}
