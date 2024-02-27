/*
 * Copyright 2023 Google Inc.
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

package com.google.android.accessibility.brailleime;

import static com.google.android.accessibility.brailleime.BrailleImeActions.ADD_NEWLINE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.ADD_SPACE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.BEGINNING_OF_PAGE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.COPY;
import static com.google.android.accessibility.brailleime.BrailleImeActions.CUT;
import static com.google.android.accessibility.brailleime.BrailleImeActions.DELETE_CHARACTER;
import static com.google.android.accessibility.brailleime.BrailleImeActions.DELETE_WORD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.END_OF_PAGE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.HELP_AND_OTHER_ACTIONS;
import static com.google.android.accessibility.brailleime.BrailleImeActions.HIDE_KEYBOARD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.MOVE_CURSOR_BACKWARD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.MOVE_CURSOR_FORWARD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.NEXT_CHARACTER;
import static com.google.android.accessibility.brailleime.BrailleImeActions.NEXT_GRANULARITY;
import static com.google.android.accessibility.brailleime.BrailleImeActions.NEXT_LINE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.NEXT_WORD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.PASTE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.PREVIOUS_CHARACTER;
import static com.google.android.accessibility.brailleime.BrailleImeActions.PREVIOUS_GRANULARITY;
import static com.google.android.accessibility.brailleime.BrailleImeActions.PREVIOUS_LINE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.PREVIOUS_WORD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_ALL;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_NEXT_CHARACTER;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_NEXT_LINE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_NEXT_WORD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_PREVIOUS_CHARACTER;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_PREVIOUS_LINE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SELECT_PREVIOUS_WORD;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SUBMIT_TEXT;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SWITCH_KEYBOARD;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_DOWN_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_DOWN_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_DOWN_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_LEFT_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_RIGHT_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_UP_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_UP_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT1_SWIPE_UP_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT2_SWIPE_DOWN_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT2_SWIPE_DOWN_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT2_SWIPE_UP_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT2_SWIPE_UP_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT3_SWIPE_DOWN_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT3_SWIPE_DOWN_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT3_SWIPE_UP_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOT3_SWIPE_UP_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOTS12_SWIPE_DOWN_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.HOLD_DOTS12_SWIPE_UP_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_DOWN_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_DOWN_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_DOWN_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_LEFT_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_LEFT_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_LEFT_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_RIGHT_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_RIGHT_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_RIGHT_TWO_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_UP_ONE_FINGER;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_UP_THREE_FINGERS;
import static com.google.android.accessibility.brailleime.BrailleImeGesture.SWIPE_UP_TWO_FINGERS;

import android.content.Context;
import android.os.Handler;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.translate.EditBuffer;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.brailleime.BrailleImeVibrator.VibrationType;
import com.google.android.accessibility.brailleime.analytics.BrailleImeAnalytics;
import com.google.android.accessibility.brailleime.input.DotHoldSwipe;
import com.google.android.accessibility.brailleime.input.Gesture;
import com.google.android.accessibility.brailleime.input.Swipe;
import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Controller to handle incoming gestures to BrailleIme. */
public class BrailleImeGestureController {
  private static final String TAG = "BrailleImeGestureController";

  private final TalkBackForBrailleIme talkBackForBrailleIme;
  private final Context context;
  private final TypoHandler typoHandler;
  private final BrailleImeGestureController.Callback callback;
  private final Handler handler;
  private final Map<BrailleImeGesture, BrailleImeActions> gestureActionMap = new HashMap<>();
  private final Map<Gesture, BrailleImeGesture> gestureMap = new HashMap<>();
  private EditBuffer editBuffer;
  private BrailleImeAnalytics brailleImeAnalytics;

  /** Callback for actions which request InputMethodService. */
  public interface Callback {
    void hideBrailleKeyboard();

    void switchToNextInputMethod();

    void showContextMenu();

    void performEditorAction();

    boolean isConnectionValid();

    ImeConnection getImeConnection();
  }

  public BrailleImeGestureController(
      Context context,
      TypoHandler typoHandler,
      EditBuffer editBuffer,
      BrailleImeGestureController.Callback callback,
      TalkBackForBrailleIme talkBackForBrailleIme) {
    this.context = context;
    this.typoHandler = typoHandler;
    this.editBuffer = editBuffer;
    this.callback = callback;
    this.handler = new Handler();
    this.talkBackForBrailleIme = talkBackForBrailleIme;
    brailleImeAnalytics = BrailleImeAnalytics.getInstance(context);
    createGestureMap();
    addDefaultGestureAction();
  }

  /** Updates edit buffer after switching languages or contracted mode. */
  public void updateEditBuffer(EditBuffer editBuffer) {
    this.editBuffer = editBuffer;
  }

  /** Receives gesture swipe to perform action. */
  @CanIgnoreReturnValue
  public boolean performSwipeAction(Swipe swipe) {
    BrailleImeGesture brailleImeGesture = gestureMap.get(swipe);
    if (brailleImeGesture == null) {
      BrailleImeLog.logD(TAG, "unknown swipe");
      return false;
    }
    return performAction(gestureActionMap.get(brailleImeGesture));
  }

  /** Receives gesture dot hold and swipe to perform action. */
  @CanIgnoreReturnValue
  public boolean performDotHoldAndSwipeAction(Swipe swipe, BrailleCharacter heldBrailleCharacter) {
    if (!FeatureFlagReader.useHoldAndSwipeGesture(context)) {
      return false;
    }
    BrailleImeGesture brailleImeGesture =
        gestureMap.get(
            new DotHoldSwipe(swipe.getDirection(), swipe.getTouchCount(), heldBrailleCharacter));
    if (brailleImeGesture == null) {
      BrailleImeLog.logD(TAG, "unknown dot hold and swipe");
      return false;
    }
    return performAction(gestureActionMap.get(brailleImeGesture));
  }

  /** Receives gesture hold to perform action. */
  @CanIgnoreReturnValue
  public boolean performDotHoldAction(int pointersHeldCount) {
    if (pointersHeldCount == 1 || pointersHeldCount == 2 || pointersHeldCount == 3) {
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.HOLD);
      return true;
    }
    return false;
  }

  private boolean performAction(BrailleImeActions action) {
    if (action == null) {
      return false;
    }
    talkBackForBrailleIme.interruptSpeak();
    if (performSystemAction(action)) {
      return true;
    }
    if (!callback.isConnectionValid()) {
      return false;
    }
    return performImeAction(action);
  }

  /** Performs action which does not need {@link ImeConnection}. */
  private boolean performSystemAction(BrailleImeActions action) {
    boolean result = true;
    switch (action) {
      case HIDE_KEYBOARD:
        BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
        callback.hideBrailleKeyboard();
        brailleImeAnalytics.logGestureActionCloseKeyboard();
        brailleImeAnalytics.sendAllLogs();
        break;
      case SWITCH_KEYBOARD:
        BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
        callback.switchToNextInputMethod();
        brailleImeAnalytics.logGestureActionSwitchKeyboard();
        break;
      case HELP_AND_OTHER_ACTIONS:
        BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
        callback.showContextMenu();
        break;
      case NEXT_GRANULARITY:
        BrailleImeVibrator.getInstance(context)
            .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR_OR_GRANULARITY);
        result = talkBackForBrailleIme.switchToNextEditingGranularity();
        brailleImeAnalytics.logGestureActionSwitchToNextEditingGranularity();
        break;
      case PREVIOUS_GRANULARITY:
        BrailleImeVibrator.getInstance(context)
            .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR_OR_GRANULARITY);
        result = talkBackForBrailleIme.switchToPreviousEditingGranularity();
        brailleImeAnalytics.logGestureActionSwitchToPreviousEditingGranularity();
        break;
      case PASTE:
        result = talkBackForBrailleIme.performAction(ScreenReaderAction.PASTE);
        brailleImeAnalytics.logGestureActionPaste();
        break;
      case CUT:
        result = talkBackForBrailleIme.performAction(ScreenReaderAction.CUT);
        brailleImeAnalytics.logGestureActionCut();
        break;
      case COPY:
        result = talkBackForBrailleIme.performAction(ScreenReaderAction.COPY);
        brailleImeAnalytics.logGestureActionCopy();
        break;
      default:
        return false;
    }
    return result;
  }

  /** Performs action which needs {@link ImeConnection}. */
  private boolean performImeAction(BrailleImeActions action) {
    ImeConnection imeConnection = callback.getImeConnection();
    boolean result = true;
    switch (action) {
      case SUBMIT_TEXT:
        editBuffer.commit(imeConnection);
        callback.hideBrailleKeyboard(); // Restore EBT so a11y focus could jump to next field.
        callback.performEditorAction();
        BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
        brailleImeAnalytics.logGestureActionSubmitText();
        brailleImeAnalytics.collectSessionEvents();
        break;
      case ADD_SPACE:
        if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
          if (!typoHandler.previousSuggestion()) {
            talkBackForBrailleIme.playSound(R.raw.complete, /* delayMs= */ 0);
          }
        } else {
          editBuffer.appendSpace(imeConnection);
          BrailleImeVibrator.getInstance(context)
              .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR_OR_GRANULARITY);
          brailleImeAnalytics.logGestureActionKeySpace();
        }
        break;
      case ADD_NEWLINE:
        if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
          result = typoHandler.confirmSuggestion();
          if (!result) {
            talkBackForBrailleIme.playSound(R.raw.complete, /* delayMs= */ 0);
          }
        } else {
          editBuffer.appendNewline(imeConnection);
          BrailleImeVibrator.getInstance(context).vibrate(VibrationType.NEWLINE_OR_DELETE_WORD);
          brailleImeAnalytics.logGestureActionKeyNewline();
        }
        break;
      case DELETE_CHARACTER:
        if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
          if (!typoHandler.nextSuggestion()) {
            talkBackForBrailleIme.playSound(R.raw.complete, /* delayMs= */ 0);
          }
        } else {
          editBuffer.deleteCharacterBackward(imeConnection);
          BrailleImeVibrator.getInstance(context)
              .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR_OR_GRANULARITY);
          brailleImeAnalytics.logGestureActionKeyDeleteCharacter();
        }
        break;
      case DELETE_WORD:
        if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
          result = typoHandler.undoConfirmSuggestion();
          if (!result) {
            talkBackForBrailleIme.playSound(R.raw.complete, /* delayMs= */ 0);
          }
        } else {
          editBuffer.deleteWord(imeConnection);
          BrailleImeVibrator.getInstance(context).vibrate(VibrationType.NEWLINE_OR_DELETE_WORD);
          brailleImeAnalytics.logGestureActionKeyDeleteWord();
        }
        break;
      case MOVE_CURSOR_FORWARD:
        if (talkBackForBrailleIme.shouldUseCharacterGranularity()) {
          if (!editBuffer.moveCursorForward(imeConnection)) {
            result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_CHARACTER);
          }
        } else {
          editBuffer.commit(imeConnection);
          result = talkBackForBrailleIme.moveCursorForwardByDefault();
          if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
            handler.post(typoHandler::updateTypoTarget);
          }
        }
        BrailleImeVibrator.getInstance(context)
            .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR_OR_GRANULARITY);
        brailleImeAnalytics.logGestureActionMoveCursorForward();
        break;
      case MOVE_CURSOR_BACKWARD:
        if (talkBackForBrailleIme.shouldUseCharacterGranularity()) {
          if (!editBuffer.moveCursorBackward(imeConnection)) {
            result =
                talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_CHARACTER);
          }
        } else {
          editBuffer.commit(imeConnection);
          // Commit takes time to get into the editor, post the backward movement to prevent
          // cursor movement ignoring the committed content.
          handler.post(
              () -> {
                talkBackForBrailleIme.moveCursorBackwardByDefault();
                if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
                  handler.post(typoHandler::updateTypoTarget);
                }
              });
        }
        BrailleImeVibrator.getInstance(context)
            .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR_OR_GRANULARITY);
        brailleImeAnalytics.logGestureActionMoveCursorBackward();
        break;
      case NEXT_CHARACTER:
        if (!editBuffer.moveCursorForward(imeConnection)) {
          result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_CHARACTER);
          brailleImeAnalytics.logGestureActionMoveCursorForwardByCharacter();
        }
        break;
      case PREVIOUS_CHARACTER:
        if (!editBuffer.moveCursorBackward(imeConnection)) {
          result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_CHARACTER);
          brailleImeAnalytics.logGestureActionMoveCursorBackwardByCharacter();
        }
        break;
      case NEXT_WORD:
        editBuffer.commit(imeConnection);
        result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_WORD);
        brailleImeAnalytics.logGestureActionMoveCursorForwardByWord();
        break;
      case PREVIOUS_WORD:
        editBuffer.commit(imeConnection);
        // Commit takes time to get into the editor, post the backward movement to prevent
        // cursor movement ignoring the committed content.
        handler.post(
            () -> talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_WORD));
        brailleImeAnalytics.logGestureActionMoveCursorBackwardByWord();
        break;
      case NEXT_LINE:
        editBuffer.commit(imeConnection);
        result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_LINE);
        brailleImeAnalytics.logGestureActionMoveCursorForwardByLine();
        break;
      case PREVIOUS_LINE:
        editBuffer.commit(imeConnection);
        // Commit takes time to get into the editor, post the backward movement to prevent
        // cursor movement ignoring the committed content.
        handler.post(
            () -> talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_LINE));
        brailleImeAnalytics.logGestureActionMoveCursorBackwardByLine();
        break;
      case BEGINNING_OF_PAGE:
        result = editBuffer.moveCursorToBeginning(imeConnection);
        brailleImeAnalytics.logGestureActionMoveCursorToBeginning();
        break;
      case END_OF_PAGE:
        result = editBuffer.moveCursorToEnd(imeConnection);
        brailleImeAnalytics.logGestureActionMoveCursorToEnd();
        break;
      case SELECT_NEXT_CHARACTER:
        result = selectText(imeConnection, ScreenReaderAction.SELECT_NEXT_CHARACTER);
        brailleImeAnalytics.logGestureActionSelectNextCharacter();
        break;
      case SELECT_PREVIOUS_CHARACTER:
        result = selectText(imeConnection, ScreenReaderAction.SELECT_PREVIOUS_CHARACTER);
        brailleImeAnalytics.logGestureActionSelectPreviousCharacter();
        break;
      case SELECT_NEXT_WORD:
        result = selectText(imeConnection, ScreenReaderAction.SELECT_NEXT_WORD);
        brailleImeAnalytics.logGestureActionSelectNextWord();
        break;
      case SELECT_PREVIOUS_WORD:
        result = selectText(imeConnection, ScreenReaderAction.SELECT_PREVIOUS_WORD);
        brailleImeAnalytics.logGestureActionSelectPreviousWord();
        break;
      case SELECT_NEXT_LINE:
        // TODO: As the text selection for line granularity movement does not
        // work, we mask off the action of selecting text by line.
        // result = selectText(imeConnection, ScreenReaderAction.SELECT_NEXT_LINE);
        // brailleImeAnalytics.logGestureActionSelectNextLine();
        // break;
      case SELECT_PREVIOUS_LINE:
        // result = selectText(imeConnection, ScreenReaderAction.SELECT_PREVIOUS_LINE);
        // brailleImeAnalytics.logGestureActionSelectPreviousLine();
        return false;
      case SELECT_ALL:
        result = editBuffer.selectAllText(imeConnection);
        brailleImeAnalytics.logGestureActionSelectAllText();
        break;
      default:
        return false;
    }
    return result;
  }

  private boolean selectText(ImeConnection imeConnection, ScreenReaderAction screenReaderAction) {
    editBuffer.commit(imeConnection);
    return talkBackForBrailleIme.performAction(screenReaderAction);
  }

  private void createGestureMap() {
    for (BrailleImeGesture brailleImeGesture : BrailleImeGesture.values()) {
      Optional<Gesture> gesture = brailleImeGesture.getGesture();
      if (gesture.isPresent()) {
        gestureMap.put(gesture.get(), brailleImeGesture);
        gestureMap.put(gesture.get().mirrorDots(), brailleImeGesture);
      }
    }
  }

  private void addDefaultGestureAction() {
    gestureActionMap.put(SWIPE_UP_ONE_FINGER, MOVE_CURSOR_BACKWARD);
    gestureActionMap.put(SWIPE_DOWN_ONE_FINGER, MOVE_CURSOR_FORWARD);
    gestureActionMap.put(SWIPE_LEFT_ONE_FINGER, ADD_SPACE);
    gestureActionMap.put(SWIPE_RIGHT_ONE_FINGER, DELETE_CHARACTER);
    gestureActionMap.put(SWIPE_UP_TWO_FINGERS, SUBMIT_TEXT);
    gestureActionMap.put(SWIPE_DOWN_TWO_FINGERS, HIDE_KEYBOARD);
    gestureActionMap.put(SWIPE_LEFT_TWO_FINGERS, ADD_NEWLINE);
    gestureActionMap.put(SWIPE_RIGHT_TWO_FINGERS, DELETE_WORD);
    gestureActionMap.put(SWIPE_UP_THREE_FINGERS, HELP_AND_OTHER_ACTIONS);
    gestureActionMap.put(SWIPE_DOWN_THREE_FINGERS, SWITCH_KEYBOARD);
    gestureActionMap.put(SWIPE_LEFT_THREE_FINGERS, NEXT_GRANULARITY);
    gestureActionMap.put(SWIPE_RIGHT_THREE_FINGERS, PREVIOUS_GRANULARITY);
    gestureActionMap.put(HOLD_DOT1_SWIPE_UP_ONE_FINGER, PREVIOUS_LINE);
    gestureActionMap.put(HOLD_DOT1_SWIPE_DOWN_ONE_FINGER, NEXT_LINE);
    gestureActionMap.put(HOLD_DOT1_SWIPE_UP_TWO_FINGERS, SELECT_PREVIOUS_LINE);
    gestureActionMap.put(HOLD_DOT1_SWIPE_DOWN_TWO_FINGERS, SELECT_NEXT_LINE);
    gestureActionMap.put(HOLD_DOT1_SWIPE_UP_THREE_FINGERS, CUT);
    gestureActionMap.put(HOLD_DOT1_SWIPE_DOWN_THREE_FINGERS, COPY);
    gestureActionMap.put(HOLD_DOT1_SWIPE_LEFT_THREE_FINGERS, PASTE);
    gestureActionMap.put(HOLD_DOT1_SWIPE_RIGHT_THREE_FINGERS, SELECT_ALL);
    gestureActionMap.put(HOLD_DOT2_SWIPE_UP_ONE_FINGER, PREVIOUS_WORD);
    gestureActionMap.put(HOLD_DOT2_SWIPE_DOWN_ONE_FINGER, NEXT_WORD);
    gestureActionMap.put(HOLD_DOT2_SWIPE_UP_TWO_FINGERS, SELECT_PREVIOUS_WORD);
    gestureActionMap.put(HOLD_DOT2_SWIPE_DOWN_TWO_FINGERS, SELECT_NEXT_WORD);
    gestureActionMap.put(HOLD_DOT3_SWIPE_UP_ONE_FINGER, PREVIOUS_CHARACTER);
    gestureActionMap.put(HOLD_DOT3_SWIPE_DOWN_ONE_FINGER, NEXT_CHARACTER);
    gestureActionMap.put(HOLD_DOT3_SWIPE_UP_TWO_FINGERS, SELECT_PREVIOUS_CHARACTER);
    gestureActionMap.put(HOLD_DOT3_SWIPE_DOWN_TWO_FINGERS, SELECT_NEXT_CHARACTER);
    gestureActionMap.put(HOLD_DOTS12_SWIPE_UP_ONE_FINGER, BEGINNING_OF_PAGE);
    gestureActionMap.put(HOLD_DOTS12_SWIPE_DOWN_ONE_FINGER, END_OF_PAGE);
  }

  @VisibleForTesting
  public void testing_setBrailleImeAnalytics(BrailleImeAnalytics brailleImeAnalytics) {
    this.brailleImeAnalytics = brailleImeAnalytics;
  }

  @VisibleForTesting
  public EditBuffer testing_getEditBuffer() {
    return editBuffer;
  }
}
