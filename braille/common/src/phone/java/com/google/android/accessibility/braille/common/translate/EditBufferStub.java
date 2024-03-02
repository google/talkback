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

import android.content.Context;
import android.view.KeyEvent;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;

/** A stub {@link EditBuffer}. */
public class EditBufferStub implements EditBuffer {

  private static final String TAG = "EditBufferStub";
  private static final int DELETE_WORD_MAX = 50;
  private final BrailleTranslator translator;

  public EditBufferStub(Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    this.translator = translator;
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    String currentTranslation = translator.translateToPrint(new BrailleWord(brailleCharacter));
    imeConnection.inputConnection.commitText(currentTranslation, 1);
    return currentTranslation;
  }

  @Override
  public void appendSpace(ImeConnection imeConnection) {
    imeConnection.inputConnection.finishComposingText();
    imeConnection.inputConnection.commitText(" ", 1);
  }

  @Override
  public void appendNewline(ImeConnection imeConnection) {
    imeConnection.inputConnection.finishComposingText();
    imeConnection.inputConnection.commitText("\n", 1);
  }

  @Override
  public void deleteCharacterBackward(ImeConnection imeConnection) {
    BrailleCommonUtils.performKeyAction(imeConnection.inputConnection, KeyEvent.KEYCODE_DEL);
  }

  @Override
  public void deleteCharacterForward(ImeConnection imeConnection) {
    BrailleCommonUtils.performKeyAction(
        imeConnection.inputConnection, KeyEvent.KEYCODE_FORWARD_DEL);
  }

  @Override
  public void deleteWord(ImeConnection imeConnection) {
    CharSequence hunkBeforeCursor =
        imeConnection.inputConnection.getTextBeforeCursor(DELETE_WORD_MAX, 0);
    int charactersToDeleteCount = BrailleCommonUtils.getLastWordLengthForDeletion(hunkBeforeCursor);
    if (charactersToDeleteCount > 0) {
      imeConnection.inputConnection.deleteSurroundingText(charactersToDeleteCount, 0);
    }
  }

  @Override
  public void commit(ImeConnection imeConnection) {}

  @Override
  public boolean moveCursorForward(ImeConnection imeConnection) {
    return true;
  }

  @Override
  public boolean moveCursorBackward(ImeConnection imeConnection) {
    return true;
  }

  @Override
  public boolean moveTextFieldCursor(ImeConnection imeConnection, int index) {
    return true;
  }

  @Override
  public boolean moveCursorToBeginning(ImeConnection imeConnection) {
    return true;
  }

  @Override
  public boolean moveCursorToEnd(ImeConnection imeConnection) {
    return true;
  }

  @Override
  public HoldingsInfo getHoldingsInfo(ImeConnection imeConnection) {
    return null;
  }

  @Override
  public boolean selectAllText(ImeConnection imeConnection) {
    return false;
  }
}
