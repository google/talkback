package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS3456;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** Korean Braille Grade 2 EditBuffer. */
public class EditBufferKorean2 extends EditBufferContracted {

  public EditBufferKorean2(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {
    ImmutableMap<String, String> vowels =
        ImmutableMap.<String, String>builder()
            .put("126", "ㅏ")
            .put("345", "ㅑ")
            .put("234", "ㅓ")
            .put("156", "ㅕ")
            .put("136", "ㅗ")
            .put("346", "ㅛ")
            .put("134", "ㅜ")
            .put("146", "ㅠ")
            .put("246", "ㅡ")
            .put("135", "ㅣ")
            .put("1345", "ㅔ")
            .put("1235", "ㅐ")
            .put("34", "ㅖ")
            .put("2456", "ㅢ")
            .put("1236", "ㅘ")
            .put("1234", "ㅝ")
            .put("13456", "ㅚ")
            .put("345-1235", "ㅒ")
            .put("1236-1235", "ㅙ")
            .put("1234-1235", "ㅞ")
            .put("134-1235", "ㅟ")
            .buildOrThrow();
    ImmutableMap<String, String> consonants =
        ImmutableMap.<String, String>builder()
            .put("4", "ㄱ")
            .put("14", "ㄴ")
            .put("24", "ㄷ")
            .put("5", "ㄹ")
            .put("15", "ㅁ")
            .put("45", "ㅂ")
            .put("6", "ㅅ")
            .put("46", "ㅈ")
            .put("56", "ㅊ")
            .put("124", "ㅋ")
            .put("125", "ㅌ")
            .put("145", "ㅍ")
            .put("245", "ㅎ")
            .put("1", "ㄱ")
            .put("25", "ㄴ")
            .put("35", "ㄷ")
            .put("2", "ㄹ")
            .put("26", "ㅁ")
            .put("12", "ㅂ")
            .put("3", "ㅅ")
            .put("13", "ㅈ")
            .put("23", "ㅊ")
            .put("235", "ㅋ")
            .put("236", "ㅌ")
            .put("256", "ㅍ")
            .put("356", "ㅎ")
            .put("1256", "ㅇ")
            .buildOrThrow();
    initialCharacterTranslationMap.putAll(consonants);
    initialCharacterTranslationMap.putAll(vowels);
    nonInitialCharacterTranslationMap.putAll(initialCharacterTranslationMap);
  }

  @Override
  protected boolean isLetter(char character) {
    return false;
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    // TODO: May distinguish between this because cap indicator only appear after roman
    // indicator.
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
