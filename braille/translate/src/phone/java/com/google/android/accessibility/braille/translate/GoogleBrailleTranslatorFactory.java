/*
 * Copyright (C) 2023 Google Inc.
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

import android.content.Context;

/** Creates Translator instances that delegate to the Google braille translation library. */
public class GoogleBrailleTranslatorFactory implements TranslatorFactory {
  private final TranslatorFactory translatorFactory;
  private final TranslationResultCustomizer customizer;

  public GoogleBrailleTranslatorFactory(
      TranslatorFactory translatorFactory, TranslationResultCustomizer customizer) {
    this.translatorFactory = translatorFactory;
    this.customizer = customizer;
  }

  @Override
  public BrailleTranslator create(Context context, String codeName, boolean contractedMode) {
    return new GoogleBrailleTranslator(
        translatorFactory.create(context, codeName, contractedMode), customizer);
  }

  @Override
  public String getLibraryName() {
    return translatorFactory.getLibraryName();
  }
}
