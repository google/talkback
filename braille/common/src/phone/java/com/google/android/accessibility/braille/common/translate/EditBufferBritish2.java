package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOT6;
import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS3456;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** An EditBuffer for British Braille Grade 2. */
public class EditBufferBritish2 extends EditBufferContracted {

  private final Set<String> forceInitialTranslationSet = new HashSet<>();
  private final Set<String> forceNonInitialTranslationSet = new HashSet<>();

  public EditBufferBritish2(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    super(context, translator, talkBack);
    fillForceDefaultTranslationSets();
  }

  private void fillForceDefaultTranslationSets() {
    // It announced Cap, but should be dot 6, ex: cation(14-6-1345).
    forceInitialTranslationSet.add("6");

    // It announced Number, but should be number(Initial) or ble(Medial or Terminal)
    forceNonInitialTranslationSet.add("3456");
    // It's first character of composite wordsigns, ex: word(45-2456).
    forceNonInitialTranslationSet.add("45");
    // It's first character of composite wordsigns, ex: cannot(456-14).
    forceNonInitialTranslationSet.add("456");
    // It's first character of composite wordsigns, ex: day(5-145).
    forceNonInitialTranslationSet.add("5");
    // It's first character of composite groupsigns, ex: ence(56-15).
    forceNonInitialTranslationSet.add("56");
    // It announced 1, but should be ea(Medial) or ,(Terminal)
    forceNonInitialTranslationSet.add("2");
    // It announced 2, but should be bb(Medial) or ;(Terminal)
    forceNonInitialTranslationSet.add("23");
    // It announced 3, but should be cc(Medial) or :(Terminal)
    forceNonInitialTranslationSet.add("25");
    // It announced .(Period), but should be dd(Medial) or .(Terminal)
    forceNonInitialTranslationSet.add("256");
    // It announced .(Period), but should be com(Medial) or -(Terminal)
    forceNonInitialTranslationSet.add("36");
    // It announced 6, but should be ff(Medial) or !(Terminal)
    forceNonInitialTranslationSet.add("235");
    // It announced )(Right paren), but should be gg(Medial) or )(Terminal)
    forceNonInitialTranslationSet.add("2356");
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
    initialCharacterTranslationMap.put("12345", "q");
    initialCharacterTranslationMap.put("1235", "r");
    initialCharacterTranslationMap.put("234", "s");
    initialCharacterTranslationMap.put("2345", "t");
    initialCharacterTranslationMap.put("136", "u");
    initialCharacterTranslationMap.put("1236", "v");
    initialCharacterTranslationMap.put("2456", "w");
    initialCharacterTranslationMap.put("1346", "x");
    initialCharacterTranslationMap.put("13456", "y");
    initialCharacterTranslationMap.put("1356", "z");

    initialCharacterTranslationMap.put("12346", "and");
    initialCharacterTranslationMap.put("123456", "for");
    initialCharacterTranslationMap.put("12356", "of");
    initialCharacterTranslationMap.put("2346", "the");
    initialCharacterTranslationMap.put("23456", "with");
    initialCharacterTranslationMap.put("16", "ch");
    initialCharacterTranslationMap.put("126", "gh");
    initialCharacterTranslationMap.put("146", "sh");
    initialCharacterTranslationMap.put("1456", "th");
    initialCharacterTranslationMap.put("156", "wh");
    initialCharacterTranslationMap.put("1246", "ed");
    initialCharacterTranslationMap.put("12456", "er");
    initialCharacterTranslationMap.put("1256", "ou");
    initialCharacterTranslationMap.put("246", "ow");
    initialCharacterTranslationMap.put("34", "st");
    initialCharacterTranslationMap.put("345", "ar");
    initialCharacterTranslationMap.put("26", "en");
    initialCharacterTranslationMap.put("35", "in");

    nonInitialCharacterTranslationMap.put("1", "a");
    nonInitialCharacterTranslationMap.put("12", "b");
    nonInitialCharacterTranslationMap.put("14", "c");
    nonInitialCharacterTranslationMap.put("145", "d");
    nonInitialCharacterTranslationMap.put("15", "e");
    nonInitialCharacterTranslationMap.put("124", "f");
    nonInitialCharacterTranslationMap.put("1245", "g");
    nonInitialCharacterTranslationMap.put("125", "h");
    nonInitialCharacterTranslationMap.put("24", "i");
    nonInitialCharacterTranslationMap.put("245", "j");
    nonInitialCharacterTranslationMap.put("13", "k");
    nonInitialCharacterTranslationMap.put("123", "l");
    nonInitialCharacterTranslationMap.put("134", "m");
    nonInitialCharacterTranslationMap.put("1345", "n");
    nonInitialCharacterTranslationMap.put("135", "o");
    nonInitialCharacterTranslationMap.put("1234", "p");
    nonInitialCharacterTranslationMap.put("12345", "q");
    nonInitialCharacterTranslationMap.put("1235", "r");
    nonInitialCharacterTranslationMap.put("234", "s");
    nonInitialCharacterTranslationMap.put("2345", "t");
    nonInitialCharacterTranslationMap.put("136", "u");
    nonInitialCharacterTranslationMap.put("1236", "v");
    nonInitialCharacterTranslationMap.put("2456", "w");
    nonInitialCharacterTranslationMap.put("1346", "x");
    nonInitialCharacterTranslationMap.put("13456", "y");
    nonInitialCharacterTranslationMap.put("1356", "z");

    nonInitialCharacterTranslationMap.put("12346", "and");
    nonInitialCharacterTranslationMap.put("123456", "for");
    nonInitialCharacterTranslationMap.put("12356", "of");
    nonInitialCharacterTranslationMap.put("2346", "the");
    nonInitialCharacterTranslationMap.put("23456", "with");
    nonInitialCharacterTranslationMap.put("16", "ch");
    nonInitialCharacterTranslationMap.put("126", "gh");
    nonInitialCharacterTranslationMap.put("146", "sh");
    nonInitialCharacterTranslationMap.put("1456", "th");
    nonInitialCharacterTranslationMap.put("156", "wh");
    nonInitialCharacterTranslationMap.put("1246", "ed");
    nonInitialCharacterTranslationMap.put("12456", "er");
    nonInitialCharacterTranslationMap.put("1256", "ou");
    nonInitialCharacterTranslationMap.put("246", "ow");
    nonInitialCharacterTranslationMap.put("34", "st");
    nonInitialCharacterTranslationMap.put("345", "ar");
    nonInitialCharacterTranslationMap.put("346", "ing");

    nonInitialCharacterTranslationMap.put("26", "en");
    nonInitialCharacterTranslationMap.put("35", "in");
  }

  @Override
  protected boolean isLetter(char character) {
    return false;
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return DOT6;
  }

  @Override
  protected BrailleCharacter getNumeric() {
    return DOTS3456;
  }

  @Override
  protected boolean forceInitialDefaultTranslation(String dotsNumber) {
    return forceInitialTranslationSet.contains(dotsNumber);
  }

  @Override
  protected boolean forceNonInitialDefaultTranslation(String dotsNumber) {
    return forceNonInitialTranslationSet.contains(dotsNumber);
  }
}
