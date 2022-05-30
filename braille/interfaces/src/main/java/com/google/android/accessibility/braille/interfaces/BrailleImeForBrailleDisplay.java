/*
 * Copyright 2021 Google Inc.
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
package com.google.android.accessibility.braille.interfaces;

/** Allows BrailleDisplay to signal to BrailleIme. */
public interface BrailleImeForBrailleDisplay {
  /** The result of actions. */
  public enum Result {
    SUCCESS,
    REACH_EDGE,
    INVALID_INPUT_CONNECTION,
  }

  /** Tells BrailleIme to move the cursor of the editing text forward. */
  default Result moveCursorForward() {
    return Result.SUCCESS;
  }

  /** Tells BrailleIme to move the cursor of the editing text backward. */
  default Result moveCursorBackward() {
    return Result.SUCCESS;
  }

  /** When a physical braille display is connected, informs BrailleIme. */
  void onBrailleDisplayConnected();

  /** When a physical braille display is disconnected, informs BrailleIme. */
  void onBrailleDisplayDisconnected();

  /** Sends the input from a physical braille display to BrailleIme. */
  boolean sendBrailleDots(BrailleCharacter dots);

  /** Tells BrailleIme to move the cursor of the editing text forward by line. */
  boolean moveCursorForwardByLine();

  /** Tells BrailleIme to move the cursor of the editing text backward by line. */
  boolean moveCursorBackwardByLine();

  /** Tells BrailleIme to move the cursor in the text field to a specified position. */
  boolean moveTextFieldCursor(int toIndex);

  /** Tells BrailleIme to move the cursor of holdings to a specified position. */
  boolean moveHoldingsCursor(int toIndex);

  /** Tells BrailleIme to delete the editing text before cursor. */
  boolean deleteBackward();

  /** Tells BrailleIme to delete the editing text after cursor. */
  boolean deleteForward();

  /** Commits the holdings and perform editor's default action. */
  boolean submit();

  /** Performs enter key action. */
  boolean performEnterKeyAction();

  /** Tells BrailleIme to hide itself. */
  void hideKeyboard();

  /** Tells BrailleIme to update result. */
  void updateResultForDisplay();
}
