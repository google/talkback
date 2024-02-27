/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.brailledisplay.FeatureFlagReader;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorIme;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;

/** An event consumer interacts with the input method service. */
class EditorConsumer implements EventConsumer {
  private static final String TAG = "EditorConsumer";
  private final Context context;
  private final BehaviorIme behaviorIme;

  /** Public constructor for general use. */
  public EditorConsumer(Context context, BehaviorIme behaviorIme) {
    this.context = context;
    this.behaviorIme = behaviorIme;
  }

  @Override
  public void onActivate() {}

  @Override
  public void onDeactivate() {
    behaviorIme.onFocusCleared();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    behaviorIme.triggerUpdateDisplay();
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_KEY_DEL:
        return behaviorIme.deleteBackward();
      case BrailleInputEvent.CMD_DEL_WORD:
        return behaviorIme.deleteWordBackward();
      case BrailleInputEvent.CMD_BRAILLE_KEY:
        return behaviorIme.sendBrailleDots(event.getArgument());
      case BrailleInputEvent.CMD_NAV_TOP_OR_KEY_ACTIVATE:
      case BrailleInputEvent.CMD_NAV_TOP:
        return behaviorIme.moveToBeginning();
      case BrailleInputEvent.CMD_NAV_BOTTOM_OR_KEY_ACTIVATE:
      case BrailleInputEvent.CMD_NAV_BOTTOM:
        return behaviorIme.moveToEnd();
      case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
        // Line navigation moves by paragraph since there's no way
        // of knowing the line extents in the edit text.
        return behaviorIme.moveCursorBackwardByLine();
      case BrailleInputEvent.CMD_NAV_LINE_NEXT:
        return behaviorIme.moveCursorForwardByLine();
      case BrailleInputEvent.CMD_CHARACTER_PREVIOUS:
        return behaviorIme.moveCursorBackward();
      case BrailleInputEvent.CMD_CHARACTER_NEXT:
        return behaviorIme.moveCursorForward();
      case BrailleInputEvent.CMD_WORD_PREVIOUS:
        return behaviorIme.moveCursorBackwardByWord();
      case BrailleInputEvent.CMD_WORD_NEXT:
        return behaviorIme.moveCursorForwardByWord();
      case BrailleInputEvent.CMD_KEY_ENTER:
      case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
        return behaviorIme.performEnterKeyAction();
      case BrailleInputEvent.CMD_ROUTE:
        return behaviorIme.moveCursor(event.getArgument());
      case BrailleInputEvent.CMD_SELECTION_CUT:
        if (FeatureFlagReader.useCutCopyPaste(context)) {
          return behaviorIme.cutSelectedText();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECTION_COPY:
        if (FeatureFlagReader.useCutCopyPaste(context)) {
          return behaviorIme.copySelectedText();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECTION_PASTE:
        if (FeatureFlagReader.useCutCopyPaste(context)) {
          return behaviorIme.pasteSelectedText();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECTION_SELECT_ALL:
        if (FeatureFlagReader.useSelectAll(context)) {
          return behaviorIme.selectAllText();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECT_PREVIOUS_CHARACTER:
        if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
          return behaviorIme.selectPreviousCharacter();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECT_NEXT_CHARACTER:
        if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
          return behaviorIme.selectNextCharacter();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECT_PREVIOUS_WORD:
        if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
          return behaviorIme.selectPreviousWord();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECT_NEXT_WORD:
        if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
          return behaviorIme.selectNextWord();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECT_PREVIOUS_LINE:
        if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
          return behaviorIme.selectPreviousLine();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      case BrailleInputEvent.CMD_SELECT_NEXT_LINE:
        if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
          return behaviorIme.selectNextLine();
        }
        // Avoids to announce the unknown command feedback.
        return true;
      default:
        // Forbid navigation commands while editing. Instead, we extract the dots and act as
        // typing.
        SupportedCommand command = BrailleKeyBindingUtils.convertToCommand(context, event);
        if (command != null) {
          BrailleCharacter character = command.getPressDot();
          if (character != null
              && !character.equals(BrailleCharacter.EMPTY_CELL)
              && command.hasSpace()) {
            // Consumed event since they are available while editing.
            return false;
          }
          return behaviorIme.sendBrailleDots(character.toInt());
        }
        return false;
    }
  }
}
