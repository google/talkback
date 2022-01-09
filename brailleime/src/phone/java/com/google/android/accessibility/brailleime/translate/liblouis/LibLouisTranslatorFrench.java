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

package com.google.android.accessibility.brailleime.translate.liblouis;

import android.content.Context;
import com.google.android.accessibility.brailleime.BrailleCharacter;

/** LibLouis Translator for French grade 1. */
public class LibLouisTranslatorFrench extends LibLouisTranslator {
  LibLouisTranslatorFrench(Context context) {
    super(context, "fr-bfu-comp6.utb");
    setupCommutativityReplacementMap();
  }

  private void setupCommutativityReplacementMap() {
    // @ and æ are overlapping in translation. @ is more likely to be used.
    getCommutativityMap().put(new BrailleCharacter(3, 4, 5), "@");
    // ", «, and » are overlapping in translation. " is more likely to be used.
    getCommutativityMap().put(new BrailleCharacter(2, 3, 5, 6), "\"");
  }
}
