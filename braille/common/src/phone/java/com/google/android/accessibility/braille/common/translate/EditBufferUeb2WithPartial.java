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

import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.KeyEvent;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Unified English Braille (UEB) Grade 2 EditBuffer.
 *
 * <p>This class is similar to {@link EditBufferUeb2} but differs in that it uses {@link
 * BrailleTranslator#translateToPrintPartial}.
 *
 * <p>This is a work-in-progress.
 */
public class EditBufferUeb2WithPartial implements EditBuffer {
  private static final String TAG = "EditBufferUeb2";
  private final Context context;
  private final BrailleTranslator translator;
  private final BrailleWord holdings = new BrailleWord();
  private final TalkBackSpeaker talkBack;

  private static final int DELETE_WORD_MAX = 50;
  private int holdingPosition = NO_CURSOR;

  public EditBufferUeb2WithPartial(
      Context context, BrailleTranslator ueb2Translator, TalkBackSpeaker talkBack) {
    this.context = context;
    this.translator = ueb2Translator;
    this.talkBack = talkBack;
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    String result = "";
    int previousTranslationIndex;
    if (holdingPosition == NO_CURSOR || holdingPosition == holdings.size()) {
      holdings.add(brailleCharacter);
      holdingPosition = holdings.size();
      previousTranslationIndex = holdingPosition - 1;
    } else {
      // TODO: Check the readout is correct.
      holdings.insert(holdingPosition, brailleCharacter);
      previousTranslationIndex = holdingPosition;
    }
    String previousTranslation =
        translator.translateToPrintPartial(holdings.subword(0, previousTranslationIndex));
    String currentTranslation =
        translator.translateToPrintPartial(holdings.subword(0, previousTranslationIndex + 1));
    if (currentTranslation.startsWith(previousTranslation)) {
      result = currentTranslation.substring(previousTranslation.length());
    }

    if (TextUtils.isEmpty(result) || !BrailleTranslateUtils.isPronounceable(result)) {
      result = getAppendBrailleTextToSpeak(context.getResources(), brailleCharacter).orElse(null);
      if (TextUtils.isEmpty(result)) {
        result = BrailleTranslateUtils.getDotsText(context.getResources(), brailleCharacter);
      }
    }
    if (EditBufferUtils.shouldEmitPerCharacterFeedback(imeConnection)) {
      speak(result);
    }
    return result;
  }

  private static Optional<String> getAppendBrailleTextToSpeak(
      Resources resources, BrailleCharacter brailleCharacter) {
    return Optional.of(BrailleTranslateUtilsUeb.getTextToSpeak(resources, brailleCharacter));
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
    String previousTranslation = translator.translateToPrintPartial(holdings);
    BrailleCharacter deletedBrailleCharacter = holdings.remove(holdingPosition);
    speakDelete(getDeletedTextToSpeak(previousTranslation, deletedBrailleCharacter));
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
    String previousTranslation = translator.translateToPrintPartial(holdings);
    BrailleCharacter deletedBrailleCharacter = holdings.remove(holdingPosition);
    speakDelete(getDeletedTextToSpeak(previousTranslation, deletedBrailleCharacter));
  }

  @Override
  public void deleteWord(ImeConnection imeConnection) {
    // If there is any holdings left, clear it out; otherwise delete at the Editor level.
    if (!holdings.isEmpty()) {
      String deletedWord = translator.translateToPrint(holdings);
      speakDelete(deletedWord);
      holdingPosition = NO_CURSOR;
      // speak(context.getString(R.string.read_out_deleted));
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
    clearHoldingsAndSendToEditor(imeConnection);
  }

  @Override
  public boolean moveCursorForward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return moveTextFieldCursor(
          imeConnection, EditBufferUtils.getCursorPosition(imeConnection.inputConnection) + 1);
    } else {
      return moveHoldingsCursor(imeConnection, holdingPosition + 1);
    }
  }

  @Override
  public boolean moveCursorBackward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return moveTextFieldCursor(
          imeConnection, EditBufferUtils.getCursorPosition(imeConnection.inputConnection) - 1);
    } else {
      return moveHoldingsCursor(imeConnection, holdingPosition - 1);
    }
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
    int newPos = EditBufferUtils.findParagraphBreakForwardIndex(imeConnection.inputConnection);
    return moveTextFieldCursor(imeConnection, newPos);
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
  public boolean moveHoldingsCursor(ImeConnection imeConnection, int index) {
    if (0 <= index && index <= holdings.size()) {
      // TODO: speak feedback.
      holdingPosition = index;
      return true;
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

  private String getDeletedTextToSpeak(
      String previousTranslation, BrailleCharacter deletedBrailleCharacter) {
    String result = "";
    String currentTranslation = translator.translateToPrintPartial(holdings);
    if (previousTranslation.startsWith(currentTranslation)) {
      result = previousTranslation.substring(currentTranslation.length());
    }

    if (TextUtils.isEmpty(result) || !BrailleTranslateUtils.isPronounceable(result)) {
      result =
          getAppendBrailleTextToSpeak(context.getResources(), deletedBrailleCharacter).orElse(null);
      if (TextUtils.isEmpty(result)) {
        result = BrailleTranslateUtils.getDotsText(context.getResources(), deletedBrailleCharacter);
      }
    }
    return result;
  }

  private void clearHoldingsAndSendToEditor(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return;
    }
    String currentTranslation = translator.translateToPrint(holdings);
    imeConnection.inputConnection.commitText(currentTranslation, 1);

    holdings.clear();
    holdingPosition = NO_CURSOR;
  }

  private void speak(String text) {
    String speakText = SpeechCleanupUtils.cleanUp(context, text).toString();
    talkBack.speakInterrupt(speakText);
  }

  private void speakDelete(String text) {
    String speakText = SpeechCleanupUtils.cleanUp(context, text).toString();
    talkBack.speakInterrupt(context.getString(R.string.read_out_deleted, speakText));
  }

  /**
   * For testing, returns true if the holdings matches the given list of {@link BrailleCharacter}.
   */
  @VisibleForTesting
  boolean testing_holdingsMatches(BrailleWord expectedHoldings) {
    return holdings.equals(expectedHoldings);
  }
}
