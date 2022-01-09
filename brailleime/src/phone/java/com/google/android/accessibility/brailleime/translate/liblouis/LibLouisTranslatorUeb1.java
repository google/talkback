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
import com.google.android.accessibility.brailleime.BrailleWord;

class LibLouisTranslatorUeb1 extends LibLouisTranslator {

  LibLouisTranslatorUeb1(Context context) {
    super(context, "en-ueb-g1.ctb");
    fillBypassMap();
  }

  @Override
  protected String transformPostTranslation(String translation) {
    // When first type dot 3, it will produce ', but when it follows with another character, '
    // will become ’. TalkBack will make a replacement announcement instead of announcing the
    // typed character. To make apostrophe consistent, we use ' so TalkBack will announce
    // apostrophe then the character correctly.
    return translation.replace('\u2019', '\'');
  }

  private void fillBypassMap() {
    getBypassMap().put(BrailleWord.create("46-134"), "μ");
    getBypassMap().put(BrailleWord.create("6-46-134"), "Μ");
    getBypassMap().put(BrailleWord.create("6-46-234"), "Σ");
  }
}
