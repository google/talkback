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

import android.content.Context;
import android.content.res.Resources;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
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
import java.util.Optional;

/**
 * Unified English Braille (UEB) Grade 2 EditBuffer.
 *
 * <p>This class is simiilar to {@link EditBufferUeb2} but differs in that it uses {@link
 * Translator#translateToPrintPartial}.
 *
 * <p>This is a work-in-progress.
 */
public class EditBufferUeb2WithPartial implements EditBuffer {
  private static final String TAG = "EditBufferUeb2WithPartial";

  private final Context context;
  private final Translator translator;
  private final BrailleWord holdings = new BrailleWord();
  private final TalkBackForBrailleImeInternal talkBack;

  private static final int DELETE_WORD_MAX = 50;
  private int cursorPosition = -1;

  public EditBufferUeb2WithPartial(
      Context context, Translator ueb2Translator, TalkBackForBrailleImeInternal talkBack) {
    this.context = context;
    this.translator = ueb2Translator;
    this.talkBack = talkBack;
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    String result = "";
    String previousTranslation = translator.translateToPrintPartial(holdings);
    holdings.add(brailleCharacter);
    String currentTranslation = translator.translateToPrintPartial(holdings);
    if (currentTranslation.startsWith(previousTranslation)) {
      result = currentTranslation.substring(previousTranslation.length());
    }

    cursorPosition = getCursorPosition(imeConnection.inputConnection);

    if (TextUtils.isEmpty(result) || !BrailleTranslateUtils.isPronounceable(result)) {
      result = getAppendBrailleTextToSpeak(context.getResources(), brailleCharacter).orElse(null);
      if (TextUtils.isEmpty(result)) {
        result = BrailleTranslateUtils.getDotsText(context.getResources(), brailleCharacter);
      }
    }

    boolean emitPerCharacterFeedback =
        imeConnection.typingEchoMode == TypingEchoMode.CHARACTERS
            || !isInputTypeText(imeConnection.editorInfo);
    if (emitPerCharacterFeedback) {
      speak(result);
    } else {
      result = "";
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
  public void deleteCharacter(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      return;
    }
    BrailleCharacter deletedBrailleCharacter = holdings.remove(holdings.size() - 1);
    speakDelete(BrailleTranslateUtils.getDotsText(context.getResources(), deletedBrailleCharacter));
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

  private static boolean isInputTypeText(EditorInfo editorInfo) {
    return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
  }
}
