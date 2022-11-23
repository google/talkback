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

import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorFocus;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorIme;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;

/**
 * Implements a navigation mode which interacts with the input method service. Forwards calls not
 * handled by the hosted IME to another NavigationMode.
 */
public class ImeNavigationMode implements NavigationMode {

  private static final String TAG = "IMENavigationMode";

  /** Accessibility event types that warrant rechecking the current state. */
  private static final int UPDATE_STATE_EVENT_MASK =
      (AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_WINDOWS_CHANGED
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);

  private final NavigationMode next;
  private final BehaviorFocus behaviorFocus;
  private final BehaviorIme behaviorIme;

  private enum State {
    // Disregard all input.
    INACTIVE,
    // Accept text input, but leave navigation alone.
    TEXT_ONLY,
    // Deactivate the underlying mode and present an editor.
    MODAL_EDITOR;

    public boolean acceptsText() {
      return this != INACTIVE;
    }

    public boolean controlsDisplay() {
      return this == MODAL_EDITOR;
    }
  }

  private State state = State.INACTIVE;
  private boolean nextActive = false;

  /** Public constructor for general use. */
  public ImeNavigationMode(
      NavigationMode next, BehaviorFocus behaviorFocus, BehaviorIme behaviorIme) {
    this.behaviorFocus = behaviorFocus;
    this.behaviorIme = behaviorIme;
    this.next = next;
  }

  @Override
  public void onActivate() {
    updateState();
    if (state.controlsDisplay()) {
      return;
    }
    activateNext();
  }

  @Override
  public void onDeactivate() {
    if (behaviorIme.acceptInput() && state.controlsDisplay()) {
      return;
    }
    deactivateNext();
  }

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    if ((event.getEventType() & UPDATE_STATE_EVENT_MASK) != 0) {
      State oldState = state;
      updateState();
      handleState(oldState, state);
      // Force update display when text selection changed for fixing the case of user selects text
      // via TalkBack gesture.
      if (state.controlsDisplay()
          && (!oldState.controlsDisplay()
              || event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)) {
        behaviorIme.triggerUpdateDisplay();
      }
      if (state.controlsDisplay()) {
        return true;
      }
    }
    return next.onAccessibilityEvent(event);
  }

  @Override
  public boolean onPanLeftOverflow() {
    if (state.controlsDisplay()) {
      // Forbid leaving editor using pan key while editing to prevent leaving accidentally.
      return false;
    }
    return next.onPanLeftOverflow();
  }

  @Override
  public boolean onPanRightOverflow() {
    if (state.controlsDisplay()) {
      // Forbid leaving editor using pan key while editing to prevent leaving accidentally.
      return false;
    }
    return next.onPanRightOverflow();
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    // These commands are handled by the IME whenever it is accepting
    // input, even if it has not taken control of the display. For instance, when keyboard is open
    // but accessibility focus is not on an editbox.
    if (state.acceptsText()) {
      switch (event.getCommand()) {
        case BrailleInputEvent.CMD_KEY_DEL:
          return behaviorIme.deleteBackward();
        case BrailleInputEvent.CMD_DEL_WORD:
          return behaviorIme.deleteWordBackward();
        case BrailleInputEvent.CMD_BRAILLE_KEY:
          return behaviorIme.sendBrailleDots(event.getArgument());
        default: // fall through.
      }
    }

    // These commands are handled by the IME only when it has taken control of the display, for
    // example when keyboard is open and accessibility focus is on an editbox. Otherwise, they are
    // delegated. If navigation commands are handled by the IME in this state, then move the cursor
    // by the appropriate granularity.
    if (state.controlsDisplay()) {
      switch (event.getCommand()) {
        case BrailleInputEvent.CMD_NAV_TOP:
          return behaviorIme.moveToBeginning();
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
        default: // fall through.
      }
    }

    // If all else fails, delegate the event.
    return next.onMappedInputEvent(event);
  }

  /** Changes states based on new information. */
  private void updateState() {
    State newState = State.TEXT_ONLY;
    if (!behaviorIme.acceptInput()) {
      newState = State.INACTIVE;
    } else if (isModalFieldFocused() || behaviorIme.isSuspended()) {
      // In some situations for example, after context menu is dismissed or switching from bd
      // version keyboard to full braille keyboard, the focused node we get is not an EditText.
      // Thus, treat suspended state as editor modal because it's impossible to have a full screen
      // braille keyboard on non edit box node.
      newState = State.MODAL_EDITOR;
    }
    if (newState != state) {
      State oldState = state;
      state = newState;
      BrailleDisplayLog.v(TAG, "state changed: " + oldState + " -> " + state);
    }
  }

  private void handleState(State oldState, State newState) {
    if (oldState.controlsDisplay() && !newState.controlsDisplay()) {
      activateNext();
      if (newState.acceptsText()) {
        behaviorIme.onFocusCleared();
      }
    } else if (!oldState.controlsDisplay() && newState.controlsDisplay()) {
      deactivateNext();
    }
  }

  /** Activates the next navigation mode, suppressing spurious calls. */
  private void activateNext() {
    if (!nextActive) {
      nextActive = true;
      next.onActivate();
    }
  }

  /** Deactivates the next navigation mode, suppressing spurious calls. */
  private void deactivateNext() {
    if (nextActive) {
      nextActive = false;
      next.onDeactivate();
    }
  }

  /** Returns true if a field suitable for modal editing is focused. */
  private boolean isModalFieldFocused() {
    // Only instances of EditText with both input and accessibility focus
    // should be edited modally.
    AccessibilityNodeInfoCompat accessibilityFocused =
        behaviorFocus.getAccessibilityFocusNode(false);
    if (accessibilityFocused == null) {
      return false;
    }
    return (accessibilityFocused != null
        && accessibilityFocused.isFocused()
        && AccessibilityNodeInfoUtils.nodeMatchesClassByType(accessibilityFocused, EditText.class));
  }
}
