package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS3456;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.Map;

/** An EditBuffer for Arabic Braille Grade 2. */
public class EditBufferArabic2 extends EditBufferContracted {

  public EditBufferArabic2(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {
    initialCharacterTranslationMap.put("1", "ا");
    initialCharacterTranslationMap.put("12", "ب");
    initialCharacterTranslationMap.put("2345", "ت");
    initialCharacterTranslationMap.put("1456", "ث");
    initialCharacterTranslationMap.put("245", "ج");
    initialCharacterTranslationMap.put("156", "ح");
    initialCharacterTranslationMap.put("1346", "خ");
    initialCharacterTranslationMap.put("145", "د");
    initialCharacterTranslationMap.put("2346", "ذ");
    initialCharacterTranslationMap.put("1235", "ر");
    initialCharacterTranslationMap.put("1356", "ز");
    initialCharacterTranslationMap.put("234", "س");
    initialCharacterTranslationMap.put("146", "ش");
    initialCharacterTranslationMap.put("12346", "ص");
    initialCharacterTranslationMap.put("1246", "ض");
    initialCharacterTranslationMap.put("23456", "ط");
    initialCharacterTranslationMap.put("123456", "ظ");
    initialCharacterTranslationMap.put("12356", "ع");
    initialCharacterTranslationMap.put("126", "غ");
    initialCharacterTranslationMap.put("124", "ف");
    initialCharacterTranslationMap.put("12345", "ق");
    initialCharacterTranslationMap.put("13", "ك");
    initialCharacterTranslationMap.put("123", "ل");
    initialCharacterTranslationMap.put("134", "م");
    initialCharacterTranslationMap.put("1345", "ن");
    initialCharacterTranslationMap.put("125", "ه");
    initialCharacterTranslationMap.put("2456", "و");
    initialCharacterTranslationMap.put("24", "ي");
  }

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
