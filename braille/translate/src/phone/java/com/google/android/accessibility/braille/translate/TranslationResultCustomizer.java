package com.google.android.accessibility.braille.translate;

/** Interface for customize the final {@link TranslationResult}. */
public interface TranslationResultCustomizer {

  /** Customizes the {@link TranslationResult}. */
  TranslationResult customize(TranslationResult translationResult, BrailleTranslator translator);
}
