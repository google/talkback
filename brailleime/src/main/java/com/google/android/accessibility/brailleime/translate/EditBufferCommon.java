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
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.CursorAnchorInfo;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleIme.ImeConnection;
import com.google.android.accessibility.brailleime.BrailleWord;
import com.google.android.accessibility.brailleime.TalkBackForBrailleImeInternal;
import com.google.android.accessibility.brailleime.Utils;
import java.util.Optional;

/**
 * A common {@link EditBuffer}.
 *
 * <p>A list of 'holdings' characters is kept, which corresponds closely to the Editor's 'composing
 * text', but holds {@link BrailleCharacter} instead of print, and can hold non-printable braille
 * such as UEB's capitalization prefix, depending on what input the user has provided.
 *
 * <p>Here is an example sequence of the holdings list and operations that modify it:
 *
 * <ul>
 *   <li>[]
 *   <li>appendBraille(6)
 *   <li>[6]
 *   <li>appendBraille(1245)
 *   <li>[6, 1245]
 *   <li>appendBraille(136)
 *   <li>[6, 1245, 136]
 *   <li>deleteCharacter
 *   <li>[6, 1245]
 *   <li>appendBraille(135)
 *   <li>[6, 1245, 135]
 *   <li>appendSpace()
 *   <li>[]
 *   <li>Send "Go " to Editor via InputConnection.
 * </ul>
 */
public abstract class EditBufferCommon implements EditBuffer {

  private static final String TAG = "EditBufferCommon";
  private static final int DELETE_WORD_MAX = 50;
  private final TalkBackForBrailleImeInternal talkBack;
  private final Translator translator;
  private final BrailleWord holdings = new BrailleWord();
  private final Context context;

  protected EditBufferCommon(
      Context context, Translator ueb1Translator, TalkBackForBrailleImeInternal talkBack) {
    this.context = context;
    this.translator = ueb1Translator;
    this.talkBack = talkBack;
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    String result = "";
    String previousTranslation = translator.translateToPrint(holdings);
    holdings.add(brailleCharacter);
    String currentTranslation = translator.translateToPrint(holdings);
    if (currentTranslation.startsWith(previousTranslation)) {
      result = currentTranslation.substring(previousTranslation.length());
    }

    // Use setComposingText() to send user input braille character in text but not password fields;
    // Otherwise use commitText().
    if (Utils.isTextField(imeConnection.editorInfo)
        && !Utils.isPasswordField(imeConnection.editorInfo)) {
      imeConnection.inputConnection.setComposingText(currentTranslation, 1);
    } else if (!TextUtils.isEmpty(result)) {
      imeConnection.inputConnection.commitText(result, 1);
    }

    if (TextUtils.isEmpty(result) || !BrailleTranslateUtils.isPronounceable(result)) {
      String textToSpeak =
          getAppendBrailleTextToSpeak(context.getResources(), brailleCharacter).orElse(null);
      if (TextUtils.isEmpty(textToSpeak)) {
        textToSpeak = BrailleTranslateUtils.getDotsText(context.getResources(), brailleCharacter);
      }
      talkBack.speakInterrupt(textToSpeak);
      result = textToSpeak;
    }
    return result;
  }

  /** Provides (optionally) an audial announcement for a just-appended {@code brailleCharacter}. */
  protected abstract Optional<String> getAppendBrailleTextToSpeak(
      Resources resources, BrailleCharacter brailleCharacter);

  @Override
  public void appendSpace(ImeConnection imeConnection) {
    holdings.clear();
    imeConnection.inputConnection.finishComposingText();
    imeConnection.inputConnection.commitText(" ", 1);
  }

  @Override
  public void appendNewline(ImeConnection imeConnection) {
    holdings.clear();
    imeConnection.inputConnection.finishComposingText();
    imeConnection.inputConnection.commitText("\n", 1);
  }

  @Override
  public void deleteCharacter(ImeConnection imeConnection) {
    // Delete a single terminal prefix from holdings, if any. In that case, stop.
    if (holdingsEndsWithPrefix()) {
      holdings.remove(holdings.size() - 1);
    } else {
      // Otherwise, remove the terminal root, and forward a deletion press to the Editor.
      if (!holdings.isEmpty()) {
        holdings.remove(holdings.size() - 1);
      }
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      imeConnection.inputConnection.sendKeyEvent(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
    }
  }

  @Override
  public void deleteWord(ImeConnection imeConnection) {
    // Delete all terminal prefixes from holdings, if any.
    while (holdingsEndsWithPrefix()) {
      holdings.remove(holdings.size() - 1);
    }
    // If there is any holdings left, clear it out; otherwise delete at the Editor level.
    if (holdings.size() > 0) {
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
    // Do nothing because appendBraille(InputConnection, BrailleCharacter) already committed.
  }

  @Override
  public void onUpdateCursorAnchorInfo(
      ImeConnection imeConnection, CursorAnchorInfo cursorAnchorInfo) {
    if (cursorAnchorInfo.getComposingText() != null
        && cursorAnchorInfo.getSelectionStart() == cursorAnchorInfo.getSelectionEnd()
        && cursorAnchorInfo.getComposingTextStart() + cursorAnchorInfo.getComposingText().length()
            != cursorAnchorInfo.getSelectionStart()) {
      // Without this code, moving the cursor while composing text exists causes post-cursor-moved
      // edits to be incorrect.
      holdings.clear();
      imeConnection.inputConnection.finishComposingText();
    }
  }

  private boolean holdingsEndsWithPrefix() {
    return !holdings.isEmpty()
        && translator
            .translateToPrint(holdings)
            .equals(translator.translateToPrint(holdings.subword(0, holdings.size() - 1)));
  }

  @Override
  public String toString() {
    return holdings.toString();
  }

  /**
   * For testing, returns true if the holdings matches the given list of {@link BrailleCharacter}.
   */
  @VisibleForTesting
  boolean testing_holdingsMatches(BrailleWord expectedHoldings) {
    return holdings.equals(expectedHoldings);
  }
}
