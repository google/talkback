/*
 * Copyright 2020 Google Inc.
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

import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.SILENCE;

import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;

/** Utils for translation of Braille. */
public class EditBufferUtils {
  public static final int NO_CURSOR = -1;
  public static final int NOT_FOUND = -1;

  /** Determines whether emit single characters feedback. */
  public static boolean shouldEmitPerCharacterFeedback(ImeConnection imeConnection) {
    return imeConnection.announceType != SILENCE
        || !BrailleCommonUtils.isTextField(imeConnection.editorInfo);
  }

  /** Returns cursor position of the edit field. */
  public static int getCursorPosition(InputConnection inputConnection) {
    CharSequence textBeforeCursor =
        inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, /* flags= */ 0);
    if (textBeforeCursor == null) {
      return NO_CURSOR;
    }
    return textBeforeCursor.length();
  }

  /** Returns whether the edit field is multi-line. */
  public static boolean isMultiLineField(int inputType) {
    final int mask =
        EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE | EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE;
    // Consider this a multiline field if it is multiline in the main
    // text field, and not multiline only in the ime fullscreen mode.
    return ((inputType & mask) == EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
  }

  /** Finds the line break position backward from current cursor. */
  public static int findParagraphBreakBackwardIndex(InputConnection inputConnection) {
    ExtractedText extractedText =
        inputConnection.getExtractedText(
            new ExtractedTextRequest(), InputConnection.GET_EXTRACTED_TEXT_MONITOR);
    if (extractedText == null || extractedText.text == null || extractedText.selectionStart <= 0) {
      return NOT_FOUND;
    }
    return String.valueOf(extractedText.text).lastIndexOf("\n", extractedText.selectionStart - 2)
        + 1;
  }

  /** Finds the line break position forward from current cursor. */
  public static int findParagraphBreakForwardIndex(InputConnection inputConnection) {
    ExtractedText extractedText =
        inputConnection.getExtractedText(
            new ExtractedTextRequest(), InputConnection.GET_EXTRACTED_TEXT_MONITOR);
    if (extractedText == null
        || extractedText.text == null
        || extractedText.selectionEnd >= extractedText.text.length()) {
      return NOT_FOUND;
    }
    int index = String.valueOf(extractedText.text).indexOf("\n", extractedText.selectionEnd);
    if (index >= 0 && index < extractedText.text.length()) {
      return index + 1;
    } else {
      return extractedText.text.length();
    }
  }

  /** Returns the text displayed in the text field. */
  public static String getTextFieldText(InputConnection inputConnection) {
    ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    if (extractedText != null && extractedText.text != null) {
      return extractedText.text.toString();
    }
    return "";
  }

  /** Speaks cleaned up text. */
  public static void speak(Context context, TalkBackSpeaker talkBackSpeaker, String text) {
    String speakText = SpeechCleanupUtils.cleanUp(context, text).toString();
    talkBackSpeaker.speakInterrupt(speakText);
  }

  /** Speaks cleaned up deleted text. */
  public static void speakDelete(Context context, TalkBackSpeaker talkBackSpeaker, String text) {
    String speakText = SpeechCleanupUtils.cleanUp(context, text).toString();
    talkBackSpeaker.speakInterrupt(context.getString(R.string.read_out_deleted, speakText));
  }

  /** Whether the cursor is at the beginning or end of the field. */
  public static boolean isCursorAtEdge(InputConnection inputConnection) {
    return EditBufferUtils.getCursorPosition(inputConnection) == 0
        || (getTextFieldText(inputConnection).length()
            == EditBufferUtils.getCursorPosition(inputConnection));
  }

  private EditBufferUtils() {}
}
