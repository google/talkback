/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.utils.input;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Retention;

/**
 * Data structure containing a more specific event type, along with extracted data from the event.
 */
public class TextEventInterpretation extends ReadOnly {

  ////////////////////////////////////////////////////////////////////////////////////
  // Constants

  // Text-events start outside the range of AccessibilityEvent.getEventType()
  private static final int AFTER_ACCESSIBILITY_EVENTS = 0x40000001;

  public static final int TEXT_CLEAR = AFTER_ACCESSIBILITY_EVENTS + 1;
  public static final int TEXT_REMOVE = AFTER_ACCESSIBILITY_EVENTS + 2;
  public static final int TEXT_ADD = AFTER_ACCESSIBILITY_EVENTS + 3;
  public static final int TEXT_REPLACE = AFTER_ACCESSIBILITY_EVENTS + 4;
  public static final int TEXT_PASSWORD_ADD = AFTER_ACCESSIBILITY_EVENTS + 5;
  public static final int TEXT_PASSWORD_REMOVE = AFTER_ACCESSIBILITY_EVENTS + 6;
  public static final int TEXT_PASSWORD_REPLACE = AFTER_ACCESSIBILITY_EVENTS + 7;
  public static final int CHANGE_INVALID = AFTER_ACCESSIBILITY_EVENTS + 8;
  public static final int SELECTION_FOCUS_EDIT_TEXT = AFTER_ACCESSIBILITY_EVENTS + 9;
  public static final int SELECTION_MOVE_CURSOR_TO_BEGINNING = AFTER_ACCESSIBILITY_EVENTS + 10;
  public static final int SELECTION_MOVE_CURSOR_TO_END = AFTER_ACCESSIBILITY_EVENTS + 11;
  public static final int SELECTION_MOVE_CURSOR_NO_SELECTION = AFTER_ACCESSIBILITY_EVENTS + 12;
  public static final int SELECTION_MOVE_CURSOR_WITH_SELECTION = AFTER_ACCESSIBILITY_EVENTS + 13;
  public static final int SELECTION_MOVE_CURSOR_SELECTION_CLEARED = AFTER_ACCESSIBILITY_EVENTS + 14;
  public static final int SELECTION_CUT = AFTER_ACCESSIBILITY_EVENTS + 15;
  public static final int SELECTION_PASTE = AFTER_ACCESSIBILITY_EVENTS + 16;
  public static final int SELECTION_TEXT_TRAVERSAL = AFTER_ACCESSIBILITY_EVENTS + 17;
  public static final int SELECTION_SELECT_ALL = AFTER_ACCESSIBILITY_EVENTS + 18;
  public static final int SELECTION_SELECT_ALL_WITH_KEYBOARD = AFTER_ACCESSIBILITY_EVENTS + 19;
  public static final int SELECTION_RESET_SELECTION = AFTER_ACCESSIBILITY_EVENTS + 20;
  public static final int SET_TEXT_BY_ACTION = AFTER_ACCESSIBILITY_EVENTS + 21;

  public static final int AFTER_TEXT_EVENTS = AFTER_ACCESSIBILITY_EVENTS + 100;

  /** Identity numbers for interpreted text events. */
  @IntDef({
    TEXT_CLEAR,
    TEXT_REMOVE,
    TEXT_ADD,
    TEXT_REPLACE,
    TEXT_PASSWORD_ADD,
    TEXT_PASSWORD_REMOVE,
    TEXT_PASSWORD_REPLACE,
    CHANGE_INVALID,
    SELECTION_FOCUS_EDIT_TEXT,
    SELECTION_MOVE_CURSOR_TO_BEGINNING,
    SELECTION_MOVE_CURSOR_TO_END,
    SELECTION_MOVE_CURSOR_NO_SELECTION,
    SELECTION_MOVE_CURSOR_WITH_SELECTION,
    SELECTION_MOVE_CURSOR_SELECTION_CLEARED,
    SELECTION_CUT,
    SELECTION_PASTE,
    SELECTION_TEXT_TRAVERSAL,
    SELECTION_SELECT_ALL,
    SELECTION_SELECT_ALL_WITH_KEYBOARD,
    SELECTION_RESET_SELECTION,
    SET_TEXT_BY_ACTION,
  })
  @Retention(SOURCE)
  public @interface TextEvent {}

  ////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private int event; // Union of @TextEvent & AccessibilityEvent.getEventType()
  private String mReason;

  private boolean mIsCutAction = false;
  private boolean mIsPasteAction = false;

  @Nullable private CharSequence textOrDescription;
  private CharSequence mRemovedText;
  private CharSequence mAddedText;
  private CharSequence mInitialWord;
  @Nullable private CharSequence mDeselectedText;
  @Nullable private CharSequence mSelectedText;
  @Nullable private CharSequence mTraversedText;

  ////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEventInterpretation(int eventArg) {
    event = eventArg;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to get/set data members

  public void setEvent(@TextEvent int event) {
    checkIsWritable();
    this.event = event;
  }

  @CanIgnoreReturnValue
  public TextEventInterpretation setInvalid(String reason) {
    setEvent(CHANGE_INVALID);
    setReason(reason);
    return this;
  }

  @TextEvent
  public int getEvent() {
    return event;
  }

  public void setReason(String reason) {
    checkIsWritable();
    mReason = reason;
  }

  public String getReason() {
    return mReason;
  }

  public void setIsCutAction(boolean isCut) {
    checkIsWritable();
    mIsCutAction = isCut;
  }

  public boolean getIsCutAction() {
    return mIsCutAction;
  }

  public void setIsPasteAction(boolean isPaste) {
    checkIsWritable();
    mIsPasteAction = isPaste;
  }

  public boolean getIsPasteAction() {
    return mIsPasteAction;
  }

  public void setTextOrDescription(@Nullable CharSequence text) {
    checkIsWritable();
    textOrDescription = text;
  }

  @Nullable
  public CharSequence getTextOrDescription() {
    return textOrDescription;
  }

  public void setRemovedText(CharSequence removedText) {
    checkIsWritable();
    mRemovedText = removedText;
  }

  public CharSequence getRemovedText() {
    return mRemovedText;
  }

  public void setAddedText(CharSequence addedText) {
    checkIsWritable();
    mAddedText = addedText;
  }

  public CharSequence getAddedText() {
    return mAddedText;
  }

  public void setInitialWord(CharSequence initialWord) {
    checkIsWritable();
    mInitialWord = initialWord;
  }

  public CharSequence getInitialWord() {
    return mInitialWord;
  }

  public void setDeselectedText(@Nullable CharSequence text) {
    checkIsWritable();
    mDeselectedText = text;
  }

  @Nullable
  public CharSequence getDeselectedText() {
    return mDeselectedText;
  }

  public void setSelectedText(@Nullable CharSequence text) {
    checkIsWritable();
    mSelectedText = text;
  }

  @Nullable
  public CharSequence getSelectedText() {
    return mSelectedText;
  }

  public void setTraversedText(@Nullable CharSequence text) {
    checkIsWritable();
    mTraversedText = text;
  }

  @Nullable
  public CharSequence getTraversedText() {
    return mTraversedText;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to display the data

  /** Display only non-default fields. */
  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalField("Event", eventTypeToString(event)),
        StringBuilderUtils.optionalText("Reason", mReason),
        StringBuilderUtils.optionalTag("isCut", mIsCutAction),
        StringBuilderUtils.optionalTag("isPaste", mIsPasteAction),
        StringBuilderUtils.optionalText("textOrDescription", textOrDescription),
        StringBuilderUtils.optionalText("removedText", mRemovedText),
        StringBuilderUtils.optionalText("addedText", mAddedText),
        StringBuilderUtils.optionalText("initialWord", mInitialWord),
        StringBuilderUtils.optionalText("deselectedText", mDeselectedText),
        StringBuilderUtils.optionalText("selectedText", mSelectedText),
        StringBuilderUtils.optionalText("traversedText", mTraversedText));
  }

  public static String eventTypeToString(@TextEvent int eventType) {
    switch (eventType) {
      case TEXT_CLEAR:
        return "TEXT_CLEAR";
      case TEXT_REMOVE:
        return "TEXT_REMOVE";
      case TEXT_ADD:
        return "TEXT_ADD";
      case TEXT_REPLACE:
        return "TEXT_REPLACE";
      case TEXT_PASSWORD_ADD:
        return "TEXT_PASSWORD_ADD";
      case TEXT_PASSWORD_REMOVE:
        return "TEXT_PASSWORD_REMOVE";
      case TEXT_PASSWORD_REPLACE:
        return "TEXT_PASSWORD_REPLACE";
      case CHANGE_INVALID:
        return "CHANGE_INVALID";
      case SELECTION_FOCUS_EDIT_TEXT:
        return "SELECTION_FOCUS_EDIT_TEXT";
      case SELECTION_MOVE_CURSOR_TO_BEGINNING:
        return "SELECTION_MOVE_CURSOR_TO_BEGINNING";
      case SELECTION_MOVE_CURSOR_TO_END:
        return "SELECTION_MOVE_CURSOR_TO_END";
      case SELECTION_MOVE_CURSOR_NO_SELECTION:
        return "SELECTION_MOVE_CURSOR_NO_SELECTION";
      case SELECTION_MOVE_CURSOR_WITH_SELECTION:
        return "SELECTION_MOVE_CURSOR_WITH_SELECTION";
      case SELECTION_MOVE_CURSOR_SELECTION_CLEARED:
        return "SELECTION_MOVE_CURSOR_SELECTION_CLEARED";
      case SELECTION_CUT:
        return "SELECTION_CUT";
      case SELECTION_PASTE:
        return "SELECTION_PASTE";
      case SELECTION_TEXT_TRAVERSAL:
        return "SELECTION_TEXT_TRAVERSAL";
      case SELECTION_SELECT_ALL:
        return "SELECTION_SELECT_ALL";
      case SELECTION_SELECT_ALL_WITH_KEYBOARD:
        return "SELECTION_SELECT_ALL_WITH_KEYBOARD";
      case SELECTION_RESET_SELECTION:
        return "SELECTION_RESET_SELECTION";
      case SET_TEXT_BY_ACTION:
        return "SET_TEXT_BY_ACTION";
      default:
        return "(unknown event " + eventType + ")";
    }
  }
}
