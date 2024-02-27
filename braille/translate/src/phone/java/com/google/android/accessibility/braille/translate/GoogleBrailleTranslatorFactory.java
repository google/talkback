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
