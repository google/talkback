package com.google.android.accessibility.braille.common.translate;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.Map;

/** A general contracted braille edit buffer. */
public class EditBufferCommonContracted extends EditBufferContracted {

  public EditBufferCommonContracted(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {}

  @Override
  protected boolean isLetter(char character) {
    return false;
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return null;
  }

  @Override
  protected BrailleCharacter getNumeric() {
    return null;
  }

  @Override
  protected boolean forceInitialDefaultTranslation(String dotsNumber) {
    return false;
  }

  @Override
  protected boolean forceNonInitialDefaultTranslation(String dotsNumber) {
    return false;
  }
}
