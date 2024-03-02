package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS3456;
import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS46;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.Map;

/** An EditBuffer for Hungarian Braille Grade 2. */
public class EditBufferHungarian2 extends EditBufferContracted {

  public EditBufferHungarian2(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {
    initialCharacterTranslationMap.put("1", "a");
    initialCharacterTranslationMap.put("12", "b");
    initialCharacterTranslationMap.put("14", "c");
    initialCharacterTranslationMap.put("145", "d");
    initialCharacterTranslationMap.put("15", "e");
    initialCharacterTranslationMap.put("124", "f");
    initialCharacterTranslationMap.put("1245", "g");
    initialCharacterTranslationMap.put("125", "h");
    initialCharacterTranslationMap.put("24", "i");
    initialCharacterTranslationMap.put("245", "j");
    initialCharacterTranslationMap.put("13", "k");
    initialCharacterTranslationMap.put("123", "l");
    initialCharacterTranslationMap.put("134", "m");
    initialCharacterTranslationMap.put("1345", "n");
    initialCharacterTranslationMap.put("135", "o");
    initialCharacterTranslationMap.put("1234", "p");
    initialCharacterTranslationMap.put("12346", "q");
    initialCharacterTranslationMap.put("1235", "r");
    initialCharacterTranslationMap.put("234", "s");
    initialCharacterTranslationMap.put("2345", "t");
    initialCharacterTranslationMap.put("136", "u");
    initialCharacterTranslationMap.put("1236", "v");
    initialCharacterTranslationMap.put("2456", "w");
    initialCharacterTranslationMap.put("1346", "x");
    initialCharacterTranslationMap.put("13456", "y");
    initialCharacterTranslationMap.put("1356", "z");

    initialCharacterTranslationMap.put("45", "ä");
    initialCharacterTranslationMap.put("4", "á");
    initialCharacterTranslationMap.put("16", "é");
    initialCharacterTranslationMap.put("246", "ó");
    initialCharacterTranslationMap.put("12456", "ő");
    initialCharacterTranslationMap.put("12345", "ö");
    initialCharacterTranslationMap.put("346", "ú");
    initialCharacterTranslationMap.put("23456", "ű");
    initialCharacterTranslationMap.put("12356", "ü");
    initialCharacterTranslationMap.put("34", "í");

    initialCharacterTranslationMap.put("146", "cs");
    initialCharacterTranslationMap.put("1456", "gy");
    initialCharacterTranslationMap.put("456", "ly");
    initialCharacterTranslationMap.put("1246", "ny");
    initialCharacterTranslationMap.put("1256", "ty");
    initialCharacterTranslationMap.put("156", "sz");
    initialCharacterTranslationMap.put("345", "zs");
  }

  @Override
  protected boolean isLetter(char character) {
    return false;
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return DOTS46;
  }

  @Override
  protected BrailleCharacter getNumeric() {
    return DOTS3456;
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
