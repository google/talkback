package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS3456;
import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS46;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** An EditBuffer for Danish Braille Grade 2. */
public class EditBufferDanish2 extends EditBufferContracted {

  private static final ImmutableMap<String, String> INITIAL_MAP =
      ImmutableMap.<String, String>builder()
          .put("1", "a")
          .put("12", "b")
          .put("14", "c")
          .put("145", "d")
          .put("15", "e")
          .put("124", "f")
          .put("1245", "g")
          .put("125", "h")
          .put("24", "i")
          .put("245", "j")
          .put("13", "k")
          .put("123", "l")
          .put("134", "m")
          .put("1345", "n")
          .put("135", "o")
          .put("1234", "p")
          .put("12345", "q")
          .put("1235", "r")
          .put("234", "s")
          .put("2345", "t")
          .put("136", "u")
          .put("1236", "v")
          .put("2456", "w")
          .put("1346", "x")
          .put("13456", "y")
          .put("1356", "z")
          .put("345", "æ")
          .put("246", "ø")
          .put("16", "å")
          .buildOrThrow();

  public EditBufferDanish2(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {
    INITIAL_MAP.forEach(initialCharacterTranslationMap::put);
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
