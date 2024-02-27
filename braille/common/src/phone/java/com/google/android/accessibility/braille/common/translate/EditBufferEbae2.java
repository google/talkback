package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS3456;
import static com.google.android.accessibility.braille.interfaces.BrailleCharacter.DOT6;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * English Braille American Edition (EBAE) Grade 2 EditBuffer.
 *
 * <p>This class is similar to {@link EditBufferEbae2} but differs in that it uses {@link
 * BrailleTranslator#translateToPrintPartial}.
 */
public class EditBufferEbae2 extends EditBufferContracted {

  /**
   * Set for non-initial braille characters. Since the single braille character translation with
   * multiple meanings, it should be forced to announce the dot number.
   */
  private final Set<String> forceNonInitialTranslationSet = new HashSet<>();

  /**
   * Set for initial braille characters. Since the single braille character translation with
   * multiple meanings, it should be forced to announce the dot number.
   */
  private final Set<String> forceInitialTranslationSet = new HashSet<>();

  public EditBufferEbae2(Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
    fillForceDefaultTranslationSets();
  }

  private void fillForceDefaultTranslationSets() {
    forceInitialTranslationSet.add("6");
    forceInitialTranslationSet.add("23"); // be-, ;

    forceNonInitialTranslationSet.add("2"); // -ea-, ,
    forceNonInitialTranslationSet.add("23"); // -bb-, ;
    forceNonInitialTranslationSet.add("235"); // -ff-, !
    forceNonInitialTranslationSet.add("2356"); // -gg-, )
    forceNonInitialTranslationSet.add("25"); // -cc-, :
    forceNonInitialTranslationSet.add("256"); // -dd-, .
    forceNonInitialTranslationSet.add("6");
    forceNonInitialTranslationSet.add("3456");
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {
    initialCharacterTranslationMap.put("1", "a");
    initialCharacterTranslationMap.put("2", ",");
    initialCharacterTranslationMap.put("12", "b");
    initialCharacterTranslationMap.put("3", "'");
    initialCharacterTranslationMap.put("13", "k");
    initialCharacterTranslationMap.put("123", "l");
    initialCharacterTranslationMap.put("14", "c");
    initialCharacterTranslationMap.put("24", "i");
    initialCharacterTranslationMap.put("124", "f");
    initialCharacterTranslationMap.put("34", "st");
    initialCharacterTranslationMap.put("134", "m");
    initialCharacterTranslationMap.put("234", "s");
    initialCharacterTranslationMap.put("1234", "p");
    initialCharacterTranslationMap.put("15", "e");
    initialCharacterTranslationMap.put("125", "h");
    initialCharacterTranslationMap.put("35", "in");
    initialCharacterTranslationMap.put("135", "o");
    initialCharacterTranslationMap.put("1235", "r");
    initialCharacterTranslationMap.put("145", "d");
    initialCharacterTranslationMap.put("245", "j");
    initialCharacterTranslationMap.put("1245", "g");
    initialCharacterTranslationMap.put("345", "ar");
    initialCharacterTranslationMap.put("1345", "n");
    initialCharacterTranslationMap.put("2345", "t");
    initialCharacterTranslationMap.put("12345", "q");
    initialCharacterTranslationMap.put("16", "ch");
    initialCharacterTranslationMap.put("26", "en");
    initialCharacterTranslationMap.put("126", "gh");
    initialCharacterTranslationMap.put("136", "u");
    initialCharacterTranslationMap.put("1236", "v");
    initialCharacterTranslationMap.put("146", "sh");
    initialCharacterTranslationMap.put("246", "ow");
    initialCharacterTranslationMap.put("1246", "ed");
    initialCharacterTranslationMap.put("1346", "x");
    initialCharacterTranslationMap.put("2346", "the");
    initialCharacterTranslationMap.put("12346", "and");
    initialCharacterTranslationMap.put("156", "wh");
    initialCharacterTranslationMap.put("1256", "ou");
    initialCharacterTranslationMap.put("1356", "z");
    initialCharacterTranslationMap.put("12356", "of");
    initialCharacterTranslationMap.put("1456", "th");
    initialCharacterTranslationMap.put("2456", "w");
    initialCharacterTranslationMap.put("12456", "er");
    initialCharacterTranslationMap.put("13456", "y");
    initialCharacterTranslationMap.put("23456", "with");
    initialCharacterTranslationMap.put("123456", "for");

    // Since the translation issue from liblouis table, added this item to avoid the announcement
    // switch between "com" and "dash" if the user continuously input dots36.
    nonInitialCharacterTranslationMap.put("36", "-");
    nonInitialCharacterTranslationMap.put("346", "ing");
  }

  @Override
  protected boolean forceInitialDefaultTranslation(String dotsNumber) {
    return forceInitialTranslationSet.contains(dotsNumber);
  }

  @Override
  protected boolean forceNonInitialDefaultTranslation(String dotsNumber) {
    return forceNonInitialTranslationSet.contains(dotsNumber);
  }

  @Override
  protected boolean isLetter(char character) {
    return ('a' <= character && character <= 'z') || ('A' <= character && character <= 'Z');
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return DOT6;
  }

  @Override
  protected BrailleCharacter getNumeric() {
    return DOTS3456;
  }
}
