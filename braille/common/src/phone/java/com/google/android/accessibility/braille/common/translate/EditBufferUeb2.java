/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * Unified English Braille (UEB) Grade 2 EditBuffer.
 *
 * <p>This class is similar to {@link EditBufferUeb2} but differs in that it uses {@link
 * BrailleTranslator#translateToPrintPartial}.
 */
public class EditBufferUeb2 extends EditBufferContracted {
  private static final String TAG = "EditBufferUeb2";

  /**
   * Set for non-initial braille characters. Since the single braille character translation with
   * multiple meanings, it should be forced to announce the dot number.
   */
  private final Set<String> forceNonInitialTranslationSet = new HashSet<>();

  public EditBufferUeb2(
      Context context, BrailleTranslator ueb2Translator, TalkBackSpeaker talkBack) {
    super(context, ueb2Translator, talkBack);
    fillForceDefaultTranslationSets();
  }

  private void fillForceDefaultTranslationSets() {
    forceNonInitialTranslationSet.add("2"); // -ea-, ,
    forceNonInitialTranslationSet.add("23"); // -bb-, ;
    forceNonInitialTranslationSet.add("25"); // -cc-, :
    forceNonInitialTranslationSet.add("235"); // -ff-, !
    forceNonInitialTranslationSet.add("256"); // -dd-, .
    forceNonInitialTranslationSet.add("2356"); // -gg-, '
  }

  @Override
  protected void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap) {
    // See: https://en.wikipedia.org/wiki/English_Braille.
    initialCharacterTranslationMap.put("1", "a");
    initialCharacterTranslationMap.put("2", ",");
    initialCharacterTranslationMap.put("12", "b");
    initialCharacterTranslationMap.put("3", "'");
    initialCharacterTranslationMap.put("13", "k");
    initialCharacterTranslationMap.put("23", "be");
    initialCharacterTranslationMap.put("123", "l");
    initialCharacterTranslationMap.put("14", "c");
    initialCharacterTranslationMap.put("24", "i");
    initialCharacterTranslationMap.put("124", "f");
    initialCharacterTranslationMap.put("34", "st");
    initialCharacterTranslationMap.put("134", "m");
    initialCharacterTranslationMap.put("234", "s");
    initialCharacterTranslationMap.put("1234", "p");
    initialCharacterTranslationMap.put("15", "e");
    initialCharacterTranslationMap.put("25", "con");
    initialCharacterTranslationMap.put("125", "h");
    initialCharacterTranslationMap.put("35", "in");
    initialCharacterTranslationMap.put("135", "o");
    initialCharacterTranslationMap.put("235", "!");
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
    initialCharacterTranslationMap.put("36", "-");
    initialCharacterTranslationMap.put("136", "u");
    initialCharacterTranslationMap.put("1236", "v");
    initialCharacterTranslationMap.put("146", "sh");
    initialCharacterTranslationMap.put("246", "ow");
    initialCharacterTranslationMap.put("1246", "ed");
    initialCharacterTranslationMap.put("1346", "x");
    initialCharacterTranslationMap.put("2346", "the");
    initialCharacterTranslationMap.put("12346", "and");
    initialCharacterTranslationMap.put("156", "wh");
    initialCharacterTranslationMap.put("256", "dis");
    initialCharacterTranslationMap.put("1256", "ou");
    initialCharacterTranslationMap.put("1356", "z");
    initialCharacterTranslationMap.put("12356", "of");
    initialCharacterTranslationMap.put("1456", "th");
    initialCharacterTranslationMap.put("2456", "w");
    initialCharacterTranslationMap.put("12456", "er");
    initialCharacterTranslationMap.put("13456", "y");
    initialCharacterTranslationMap.put("23456", "with");
    initialCharacterTranslationMap.put("123456", "for");

    nonInitialCharacterTranslationMap.put("346", "ing");
    nonInitialCharacterTranslationMap.put("356", "\"");
  }

  @Override
  protected boolean forceInitialDefaultTranslation(String dotsNumber) {
    return false;
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
