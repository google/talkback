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

package com.google.android.accessibility.brailleime.translate;

import static com.google.android.accessibility.brailleime.translate.BrailleTranslateUtilsUeb.getTextToSpeak;
import static com.google.common.base.Strings.nullToEmpty;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.InputConnection;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleIme.ImeConnection;
import com.google.android.accessibility.brailleime.BrailleWord;
import com.google.android.accessibility.brailleime.Constants;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.TalkBackForBrailleImeInternal;
import com.google.android.accessibility.brailleime.UserPreferences.TypingEchoMode;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified English Braille (UEB) Grade 2 EditBuffer.
 *
 * <p>This class is similar to {@link EditBufferUeb1} but differs in that it only sends the print
 * translation of its holdings to the IME Editor once a print word has finished being constructed.
 * As a consequence, the real-time reading out of input is handled directly by this class (instead
 * of by the framework).
 */
public class EditBufferUeb2 implements EditBuffer {
  private static final String TAG = "EditBufferUeb2";

  private final Context context;
  private final Translator translator;
  private final BrailleWord holdings = new BrailleWord();
  private final TalkBackForBrailleImeInternal talkBack;

  // TODO: Rename these maps and cleanup appendBraille method.
  /** Mapping for single braille characters. See: https://en.wikipedia.org/wiki/English_Braille */
  private static final Map<String, String> singleBrailleTranslationMap = new HashMap<>();
  /** Mapping for braille digraphs. See: https://en.wikipedia.org/wiki/English_Braille */
  private static final Map<String, String> digraphBrailleTranslationMap = new HashMap<>();

  private static final int DELETE_WORD_MAX = 50;
  private int cursorPosition = -1;

  public EditBufferUeb2(
      Context context, Translator ueb2Translator, TalkBackForBrailleImeInternal talkBack) {
    this.context = context;
    this.translator = ueb2Translator;
    this.talkBack = talkBack;
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    holdings.add(brailleCharacter);
    String result = getDigraphBrailleTranslation(brailleCharacter);
    if (holdings.size() == 1) {
      result = getSingleBrailleTranslation(brailleCharacter);
    }
    if (result.isEmpty()) {
      result = getHoldingsTranslateDifference();
      if (result.isEmpty() || isAlphabet(result.charAt(0))) {
        result = getDynamicTranslation(brailleCharacter, holdings.size() > 1);
      }
    }
    cursorPosition = getCursorPosition(imeConnection.inputConnection);
    boolean emitPerCharacterFeedback =
        imeConnection.typingEchoMode == TypingEchoMode.CHARACTERS
            || !Utils.isTextField(imeConnection.editorInfo);
    if (emitPerCharacterFeedback) {
      speak(result);
      return result;
    } else {
      return "";
    }
  }

  /**
   * Gets the proper translation for single braille. For example, dot 16 can be "child" or "ch",
   * it's better to get "ch" with more use experience.
   */
  private String getDynamicTranslation(
      BrailleCharacter brailleCharacter, boolean hasOtherHoldings) {
    return hasOtherHoldings
        ? getSingleBrailleTranslation(brailleCharacter)
        : getDigraphBrailleTranslation(brailleCharacter);
  }

  private String getHoldingsTranslateDifference() {
    String longerString = translator.translateToPrint(holdings.subword(0, holdings.size()));
    String shorterString = translator.translateToPrint(holdings.subword(0, holdings.size() - 1));
    if (longerString.startsWith(shorterString)) {
      return longerString.substring(shorterString.length());
    }
    return "";
  }

  @Override
  public void appendSpace(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection);
    imeConnection.inputConnection.commitText(" ", 1);
  }

  @Override
  public void appendNewline(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection);
    imeConnection.inputConnection.commitText("\n", 1);
  }

  @Override
  public void deleteCharacter(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      return;
    }
    String holdingsTranslateDifference = getHoldingsTranslateDifference();
    BrailleCharacter deletedBrailleCharacter = holdings.remove(holdings.size() - 1);
    if (holdings.isEmpty()) {
      speakDelete(getSingleBrailleTranslation(deletedBrailleCharacter));
      return;
    }
    String deletedString = getDigraphBrailleTranslation(deletedBrailleCharacter);
    if (!deletedString.isEmpty()) {
      speakDelete(deletedString);
      return;
    }
    deletedString = holdingsTranslateDifference;
    if (deletedString.isEmpty() || isAlphabet(deletedString.charAt(0))) {
      deletedString = getDynamicTranslation(deletedBrailleCharacter, !holdings.isEmpty());
    }
    speakDelete(deletedString);
  }

  @Override
  public void deleteWord(ImeConnection imeConnection) {
    // If there is any holdings left, clear it out; otherwise delete at the Editor level.
    if (!holdings.isEmpty()) {
      String deletedWord = translator.translateToPrint(holdings);
      speakDelete(deletedWord);
      // speak(context.getString(R.string.read_out_deleted));
      holdings.clear();
      imeConnection.inputConnection.setComposingText("", 0);
    } else {
      CharSequence hunkBeforeCursor =
          imeConnection.inputConnection.getTextBeforeCursor(DELETE_WORD_MAX, 0);
      int charactersToDeleteCount = Utils.getLastWordLengthForDeletion(hunkBeforeCursor);
      if (charactersToDeleteCount > 0) {
        imeConnection.inputConnection.deleteSurroundingText(charactersToDeleteCount, 0);
      }
    }
  }

  @Override
  public void commit(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection);
  }

  @Override
  public void onUpdateCursorAnchorInfo(
      ImeConnection imeConnection, CursorAnchorInfo cursorAnchorInfo) {
    if (!holdings.isEmpty() && cursorPosition >= 0) {
      int newPosition = getCursorPosition(imeConnection.inputConnection);
      imeConnection.inputConnection.setSelection(cursorPosition, cursorPosition);
      commit(imeConnection);
      if (newPosition <= cursorPosition) {
        // If cursor moved to backward, set cursor position to backward after commit.
        imeConnection.inputConnection.setSelection(newPosition, newPosition);
      }
      cursorPosition = -1;
    }
  }

  @Override
  public String toString() {
    return holdings.toString();
  }

  private void clearHoldingsAndSendToEditor(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return;
    }
    String currentTranslation = translator.translateToPrint(holdings);
    // TODO: remove this condition.
    if (Constants.KEEP_NOTES_PACKAGE_NAME.equals(imeConnection.editorInfo.packageName)) {
      // Sometimes EditText would lose cursor which causes commitText nowhere to insert text.
      // setComposingText will call onUpdateSelection to bring cursor back so we use it here.
      imeConnection.inputConnection.setComposingText(currentTranslation, 1);
      imeConnection.inputConnection.finishComposingText();
    } else {
      imeConnection.inputConnection.commitText(currentTranslation, 1);
    }
    holdings.clear();
  }

  private static int getCursorPosition(InputConnection inputConnection) {
    CharSequence textBeforeCursor =
        inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, /* flags= */ 0);
    if (TextUtils.isEmpty(textBeforeCursor)) {
      return 0;
    }
    return textBeforeCursor.length();
  }

  private void speak(String text) {
    String speakText = SpeechCleanupUtils.cleanUp(context, text).toString();
    talkBack.speakInterrupt(speakText);
  }

  private void speakDelete(String text) {
    String speakText = SpeechCleanupUtils.cleanUp(context, text).toString();
    talkBack.speakInterrupt(context.getString(R.string.read_out_deleted, speakText));
  }

  private static boolean isAlphabet(char character) {
    return ('a' <= character && character <= 'z') || ('A' <= character && character <= 'Z');
  }

  private String getSingleBrailleTranslation(BrailleCharacter brailleCharacter) {
    String translation = getTextToSpeak(context.getResources(), brailleCharacter);
    if (TextUtils.isEmpty(translation)) {
      translation = nullToEmpty(singleBrailleTranslationMap.get(brailleCharacter.toString()));
      if (TextUtils.isEmpty(translation)) {
        translation = BrailleTranslateUtils.getDotsText(context.getResources(), brailleCharacter);
      }
    }
    return translation;
  }

  private String getDigraphBrailleTranslation(BrailleCharacter brailleCharacter) {
    String translation = getTextToSpeak(context.getResources(), brailleCharacter);
    if (TextUtils.isEmpty(translation)) {
      translation = nullToEmpty(digraphBrailleTranslationMap.get(brailleCharacter.toString()));
    }
    return translation;
  }

  static {
    singleBrailleTranslationMap.put("1", "a");
    singleBrailleTranslationMap.put("2", ",");
    singleBrailleTranslationMap.put("12", "b");
    singleBrailleTranslationMap.put("3", "'");
    singleBrailleTranslationMap.put("13", "k");
    singleBrailleTranslationMap.put("23", "be");
    singleBrailleTranslationMap.put("123", "l");
    singleBrailleTranslationMap.put("14", "c");
    singleBrailleTranslationMap.put("24", "i");
    singleBrailleTranslationMap.put("124", "f");
    singleBrailleTranslationMap.put("34", "st");
    singleBrailleTranslationMap.put("134", "m");
    singleBrailleTranslationMap.put("234", "s");
    singleBrailleTranslationMap.put("1234", "p");
    singleBrailleTranslationMap.put("15", "e");
    singleBrailleTranslationMap.put("25", "con");
    singleBrailleTranslationMap.put("125", "h");
    singleBrailleTranslationMap.put("35", "in");
    singleBrailleTranslationMap.put("135", "o");
    singleBrailleTranslationMap.put("235", "!");
    singleBrailleTranslationMap.put("1235", "r");
    singleBrailleTranslationMap.put("45", "^");
    singleBrailleTranslationMap.put("145", "d");
    singleBrailleTranslationMap.put("245", "j");
    singleBrailleTranslationMap.put("1245", "g");
    singleBrailleTranslationMap.put("345", "ar");
    singleBrailleTranslationMap.put("1345", "n");
    singleBrailleTranslationMap.put("2345", "t");
    singleBrailleTranslationMap.put("12345", "q");
    singleBrailleTranslationMap.put("16", "ch");
    singleBrailleTranslationMap.put("26", "en");
    singleBrailleTranslationMap.put("126", "gh");
    singleBrailleTranslationMap.put("36", "-");
    singleBrailleTranslationMap.put("136", "u");
    singleBrailleTranslationMap.put("236", "\"");
    singleBrailleTranslationMap.put("1236", "v");
    singleBrailleTranslationMap.put("146", "sh");
    singleBrailleTranslationMap.put("246", "ow");
    singleBrailleTranslationMap.put("1246", "ed");
    singleBrailleTranslationMap.put("346", "ing");
    singleBrailleTranslationMap.put("1346", "x");
    singleBrailleTranslationMap.put("2346", "the");
    singleBrailleTranslationMap.put("12346", "and");
    singleBrailleTranslationMap.put("156", "wh");
    singleBrailleTranslationMap.put("256", "dis");
    singleBrailleTranslationMap.put("1256", "ou");
    singleBrailleTranslationMap.put("356", "\"");
    singleBrailleTranslationMap.put("1356", "z");
    singleBrailleTranslationMap.put("2356", "'");
    singleBrailleTranslationMap.put("12356", "of");
    singleBrailleTranslationMap.put("1456", "th");
    singleBrailleTranslationMap.put("2456", "w");
    singleBrailleTranslationMap.put("12456", "er");
    singleBrailleTranslationMap.put("13456", "y");
    singleBrailleTranslationMap.put("23456", "with");
    singleBrailleTranslationMap.put("123456", "for");

    digraphBrailleTranslationMap.put("2", "ea");
    digraphBrailleTranslationMap.put("23", "bb");
    digraphBrailleTranslationMap.put("25", "cc");
    digraphBrailleTranslationMap.put("235", "ff");
    digraphBrailleTranslationMap.put("2356", "gg");
    digraphBrailleTranslationMap.put("256", ".");
    digraphBrailleTranslationMap.put("346", "ing");
  }

  /**
   * For testing, returns true if the holdings matches the given list of {@link BrailleCharacter}.
   */
  @VisibleForTesting
  boolean testing_holdingsMatches(BrailleWord expectedHoldings) {
    return holdings.equals(expectedHoldings);
  }
}
