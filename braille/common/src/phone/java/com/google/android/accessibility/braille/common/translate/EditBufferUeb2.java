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

import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.NORMAL;
import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static com.google.common.base.Strings.nullToEmpty;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.common.base.Strings;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified English Braille (UEB) Grade 2 EditBuffer.
 *
 * <p>This class is similar to {@link EditBufferUeb2} but differs in that it uses {@link
 * BrailleTranslator#translateToPrintPartial}.
 *
 * <p>This is a work-in-progress.
 */
public class EditBufferUeb2 implements EditBuffer {
  private static final String TAG = "EditBufferUeb2";
  public static final String PASSWORD_BULLET = "\u2022";
  private final Context context;
  private final BrailleTranslator translator;
  private final BrailleWord holdings = new BrailleWord();
  private final TalkBackSpeaker talkBack;

  // TODO: Rename these maps and cleanup appendBraille method.
  /** Mapping for initial braille characters. See: https://en.wikipedia.org/wiki/English_Braille */
  private final Map<String, String> initialCharacterTranslationMap = new HashMap<>();
  /**
   * Mapping for non-initial braille characters. See: https://en.wikipedia.org/wiki/English_Braille
   */
  private final Map<String, String> nonInitialCharacterTranslationMap = new HashMap<>();

  private static final int DELETE_WORD_MAX = 50;
  private int holdingPosition = NO_CURSOR;

  public EditBufferUeb2(
      Context context, BrailleTranslator ueb2Translator, TalkBackSpeaker talkBack) {
    this.context = context;
    this.translator = ueb2Translator;
    this.talkBack = talkBack;
    fillTranslatorMaps();
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    int previousTranslationIndex;
    if (holdingPosition == NO_CURSOR || holdingPosition == holdings.size()) {
      holdings.add(brailleCharacter);
      holdingPosition = holdings.size();
    } else {
      // TODO: Check the readout is correct.
      holdings.insert(holdingPosition, brailleCharacter);
      holdingPosition++;
    }
    previousTranslationIndex = holdingPosition - 1;
    String result =
        getAnnouncementAt(context.getResources(), translator, holdings, previousTranslationIndex);
    if (EditBufferUtils.shouldEmitPerCharacterFeedback(imeConnection)) {
      result =
          hideTextForPasswordIfNecessary(imeConnection, result, /* brailleCharacterLength= */ 1);
      EditBufferUtils.speak(context, talkBack, result);
    }
    return result;
  }

  @Override
  public void appendSpace(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection, /* ignoreHoldingsPosition= */ false);
    imeConnection.inputConnection.commitText(" ", 1);
  }

  @Override
  public void appendNewline(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection, /* ignoreHoldingsPosition= */ false);
    imeConnection.inputConnection.commitText("\n", 1);
  }

  @Override
  public void deleteCharacterBackward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      return;
    }
    if (holdingPosition <= 0) {
      return;
    }
    holdingPosition--;
    String result =
        getAnnouncementAt(context.getResources(), translator, holdings, holdingPosition);
    holdings.remove(holdingPosition);
    result = hideTextForPasswordIfNecessary(imeConnection, result, /* brailleCharacterLength= */ 1);
    EditBufferUtils.speakDelete(context, talkBack, result);
  }

  @Override
  public void deleteCharacterForward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL));
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL));
      return;
    }
    if (holdingPosition >= holdings.size()) {
      return;
    }
    String result =
        getAnnouncementAt(context.getResources(), translator, holdings, holdingPosition);
    holdings.remove(holdingPosition);
    result = hideTextForPasswordIfNecessary(imeConnection, result, /* brailleCharacterLength= */ 1);
    EditBufferUtils.speakDelete(context, talkBack, result);
  }

  @Override
  public void deleteWord(ImeConnection imeConnection) {
    // If there is any holdings left, clear it out; otherwise delete at the Editor level.
    if (!holdings.isEmpty()) {
      String deletedWord = translator.translateToPrint(holdings);
      deletedWord = hideTextForPasswordIfNecessary(imeConnection, deletedWord, holdings.size());
      EditBufferUtils.speakDelete(context, talkBack, deletedWord);
      holdingPosition = NO_CURSOR;
      holdings.clear();
      imeConnection.inputConnection.setComposingText("", 0);
    } else {
      CharSequence hunkBeforeCursor =
          imeConnection.inputConnection.getTextBeforeCursor(DELETE_WORD_MAX, 0);
      int charactersToDeleteCount =
          BrailleCommonUtils.getLastWordLengthForDeletion(hunkBeforeCursor);
      if (charactersToDeleteCount > 0) {
        imeConnection.inputConnection.deleteSurroundingText(charactersToDeleteCount, 0);
      }
    }
  }

  @Override
  public void commit(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection, /* ignoreHoldingsPosition= */ true);
  }

  @Override
  public boolean moveCursorForward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return moveTextFieldCursor(
          imeConnection, EditBufferUtils.getCursorPosition(imeConnection.inputConnection) + 1);
    }
    return moveHoldingsCursor(imeConnection, holdingPosition + 1);
  }

  @Override
  public boolean moveCursorBackward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return moveTextFieldCursor(
          imeConnection, EditBufferUtils.getCursorPosition(imeConnection.inputConnection) - 1);
    }
    return moveHoldingsCursor(imeConnection, holdingPosition - 1);
  }

  @Override
  public boolean moveCursorForwardByLine(ImeConnection imeConnection) {
    if (!holdings.isEmpty()) {
      commit(imeConnection);
    }
    // TODO: Redefine move by line.
    int newPos = EditBufferUtils.findParagraphBreakForwardIndex(imeConnection.inputConnection);
    return moveTextFieldCursor(imeConnection, newPos);
  }

  @Override
  public boolean moveCursorBackwardByLine(ImeConnection imeConnection) {
    if (!holdings.isEmpty()) {
      commit(imeConnection);
    }
    // TODO: Redefine move by line.
    int newPos = EditBufferUtils.findParagraphBreakBackwardIndex(imeConnection.inputConnection);
    return moveTextFieldCursor(imeConnection, newPos);
  }

  @Override
  public boolean moveHoldingsCursor(ImeConnection imeConnection, int index) {
    if (0 <= index && index <= holdings.size()) {
      int start = holdingPosition;
      int end = index;
      if (holdingPosition > index) {
        start = index;
        end = holdingPosition;
      }
      holdingPosition = index;
      String announcement =
          getAnnouncementRange(context.getResources(), translator, holdings, start, end);
      announcement =
          hideTextForPasswordIfNecessary(
              imeConnection, announcement, /* brailleCharacterLength= */ end - start);
      EditBufferUtils.speak(context, talkBack, announcement);
    } else {
      int oldPosition = EditBufferUtils.getCursorPosition(imeConnection.inputConnection);
      commit(imeConnection);
      if (index < 0) {
        return imeConnection.inputConnection.setSelection(oldPosition, oldPosition);
      }
    }
    return true;
  }

  @Override
  public boolean moveTextFieldCursor(ImeConnection imeConnection, int index) {
    if (0 <= index
        && index <= EditBufferUtils.getTextFieldText(imeConnection.inputConnection).length()) {
      return imeConnection.inputConnection.setSelection(index, index);
    }
    return false;
  }

  @Override
  public HoldingsInfo getHoldingsInfo(ImeConnection imeConnection) {
    return HoldingsInfo.create(ByteBuffer.wrap(holdings.toByteArray()), holdingPosition);
  }

  @Override
  public String toString() {
    return holdings.toString();
  }

  private void clearHoldingsAndSendToEditor(
      ImeConnection imeConnection, boolean ignoreHoldingsPosition) {
    if (holdings.isEmpty()) {
      return;
    }
    if (ignoreHoldingsPosition) {
      holdingPosition = holdings.size();
    }
    String holdingsBeforeCursor = translator.translateToPrint(holdings.subword(0, holdingPosition));
    String holdingsAfterCursor =
        translator.translateToPrint(holdings.subword(holdingPosition, holdings.size()));
    String currentTranslation = holdingsBeforeCursor + holdingsAfterCursor;
    ExtractedText extractedText =
        imeConnection.inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    CharSequence textLengthBeforeCursor = "";
    if (extractedText != null && extractedText.text != null) {
      textLengthBeforeCursor = extractedText.text.subSequence(0, extractedText.selectionStart);
    }
    int textLengthBeforeCursorLength =
        TextUtils.isEmpty(textLengthBeforeCursor) ? 0 : textLengthBeforeCursor.length();

    imeConnection.inputConnection.commitText(currentTranslation, 1);

    holdings.clear();
    holdingPosition = NO_CURSOR;
    moveTextFieldCursor(
        imeConnection, holdingsBeforeCursor.length() + textLengthBeforeCursorLength);
  }

  private String getAnnouncementRange(
      Resources resources,
      BrailleTranslator translator,
      BrailleWord brailleWord,
      int startIndex,
      int endIndex) {
    StringBuilder sb = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      sb.append(getAnnouncementAt(resources, translator, brailleWord, i));
    }
    return sb.toString();
  }

  private String getAnnouncementAt(
      Resources resources, BrailleTranslator translator, BrailleWord brailleWord, int index) {
    BrailleCharacter brailleCharacter = brailleWord.get(index);
    String result = getNonInitialCharacterTranslation(resources, brailleCharacter);
    if (index == 0) {
      result = getInitialCharacterTranslation(resources, brailleCharacter);
    }
    if (result.isEmpty()) {
      result = getTranslateDifference(translator, brailleWord, index, index + 1);
      if (result.isEmpty() || isAlphabet(result.charAt(0))) {
        result = getDynamicTranslation(resources, brailleCharacter, brailleWord.size() > 1);
      }
    }
    return result;
  }

  private String hideTextForPasswordIfNecessary(
      ImeConnection imeConnection, String text, int brailleCharacterLength) {
    if (imeConnection.announceType == NORMAL
        || !BrailleCommonUtils.isPasswordField(imeConnection.editorInfo)) {
      return text;
    }
    return Strings.repeat(PASSWORD_BULLET, brailleCharacterLength);
  }

  /**
   * Gets the proper translation for single braille. For example, dot 16 can be "child" or "ch",
   * it's better to get "ch" with more use experience.
   */
  private String getDynamicTranslation(
      Resources resources, BrailleCharacter brailleCharacter, boolean hasOtherHoldings) {
    return hasOtherHoldings
        ? getInitialCharacterTranslation(resources, brailleCharacter)
        : getNonInitialCharacterTranslation(resources, brailleCharacter);
  }

  private static String getTranslateDifference(
      BrailleTranslator translator, BrailleWord brailleWord, int firstIndex, int secondIndex) {
    String longerString = translator.translateToPrint(brailleWord.subword(0, secondIndex));
    String shorterString = translator.translateToPrint(brailleWord.subword(0, firstIndex));
    if (longerString.startsWith(shorterString)) {
      return longerString.substring(shorterString.length());
    }
    return "";
  }

  private static boolean isAlphabet(char character) {
    return ('a' <= character && character <= 'z') || ('A' <= character && character <= 'Z');
  }

  private String getInitialCharacterTranslation(
      Resources resources, BrailleCharacter brailleCharacter) {
    String translation = BrailleTranslateUtilsUeb.getTextToSpeak(resources, brailleCharacter);
    if (TextUtils.isEmpty(translation)) {
      translation = nullToEmpty(initialCharacterTranslationMap.get(brailleCharacter.toString()));
      if (TextUtils.isEmpty(translation)) {
        translation = BrailleTranslateUtils.getDotsText(resources, brailleCharacter);
      }
    }
    return translation;
  }

  private String getNonInitialCharacterTranslation(
      Resources resources, BrailleCharacter brailleCharacter) {
    String translation = BrailleTranslateUtilsUeb.getTextToSpeak(resources, brailleCharacter);
    if (TextUtils.isEmpty(translation)) {
      translation = nullToEmpty(nonInitialCharacterTranslationMap.get(brailleCharacter.toString()));
    }
    return translation;
  }

  private void fillTranslatorMaps() {
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
    initialCharacterTranslationMap.put("45", "^");
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
    initialCharacterTranslationMap.put("236", "\"");
    initialCharacterTranslationMap.put("1236", "v");
    initialCharacterTranslationMap.put("146", "sh");
    initialCharacterTranslationMap.put("246", "ow");
    initialCharacterTranslationMap.put("1246", "ed");
    initialCharacterTranslationMap.put("346", "ing");
    initialCharacterTranslationMap.put("1346", "x");
    initialCharacterTranslationMap.put("2346", "the");
    initialCharacterTranslationMap.put("12346", "and");
    initialCharacterTranslationMap.put("156", "wh");
    initialCharacterTranslationMap.put("256", "dis");
    initialCharacterTranslationMap.put("1256", "ou");
    initialCharacterTranslationMap.put("356", "\"");
    initialCharacterTranslationMap.put("1356", "z");
    initialCharacterTranslationMap.put("2356", "'");
    initialCharacterTranslationMap.put("12356", "of");
    initialCharacterTranslationMap.put("1456", "th");
    initialCharacterTranslationMap.put("2456", "w");
    initialCharacterTranslationMap.put("12456", "er");
    initialCharacterTranslationMap.put("13456", "y");
    initialCharacterTranslationMap.put("23456", "with");
    initialCharacterTranslationMap.put("123456", "for");

    nonInitialCharacterTranslationMap.put("2", "ea");
    nonInitialCharacterTranslationMap.put("23", "bb");
    nonInitialCharacterTranslationMap.put("25", "cc");
    nonInitialCharacterTranslationMap.put("235", "ff");
    nonInitialCharacterTranslationMap.put("2356", "gg");
    nonInitialCharacterTranslationMap.put("256", ".");
    nonInitialCharacterTranslationMap.put("346", "ing");
  }

  /**
   * For testing, returns true if the holdings matches the given list of {@link BrailleCharacter}.
   */
  @VisibleForTesting
  boolean testing_holdingsMatches(BrailleWord expectedHoldings) {
    return holdings.equals(expectedHoldings);
  }
}
