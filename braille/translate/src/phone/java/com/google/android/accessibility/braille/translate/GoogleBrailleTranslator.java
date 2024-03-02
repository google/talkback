package com.google.android.accessibility.braille.translate;

import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.interfaces.BrailleWord;

/** Translates from text to braille with custom translation. */
public class GoogleBrailleTranslator implements BrailleTranslator {
  private final BrailleTranslator translator;
  private final TranslationResultCustomizer customizer;

  public GoogleBrailleTranslator(
      BrailleTranslator translator, @Nullable TranslationResultCustomizer customizer) {
    this.translator = translator;
    this.customizer = customizer;
  }

  @Override
  public String translateToPrint(BrailleWord brailleWord) {
    return translator.translateToPrint(brailleWord);
  }

  @Override
  public String translateToPrintPartial(BrailleWord brailleWord) {
    return translator.translateToPrintPartial(brailleWord);
  }

  @Override
  public TranslationResult translate(CharSequence text, int cursorPosition) {
    TranslationResult result = translator.translate(text, cursorPosition);
    return customizer == null ? result : customizer.customize(result, this);
  }
}
