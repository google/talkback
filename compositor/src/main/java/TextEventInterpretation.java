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

package com.google.android.accessibility.compositor;

import com.google.android.accessibility.utils.ReadOnly;

/**
 * Data structure containing a more specific event type, along with extracted data from the event.
 */
public class TextEventInterpretation extends ReadOnly {

  ////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private @Compositor.Event int mEvent;
  private String mReason;

  private boolean mIsCutAction = false;
  private boolean mIsPasteAction = false;

  private CharSequence mTextOrDescription;
  private CharSequence mRemovedText;
  private CharSequence mAddedText;
  private CharSequence mInitialWord;
  private CharSequence mDeselectedText;
  private CharSequence mSelectedText;
  private CharSequence mTraversedText;

  ////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEventInterpretation(@Compositor.Event int eventArg) {
    mEvent = eventArg;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to get/set data members

  public void setEvent(@Compositor.Event int event) {
    checkIsWritable();
    mEvent = event;
  }

  public TextEventInterpretation setInvalid(String reason) {
    setEvent(Compositor.EVENT_TYPE_INPUT_CHANGE_INVALID);
    setReason(reason);
    return this;
  }

  public @Compositor.Event int getEvent() {
    return mEvent;
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

  public void setTextOrDescription(CharSequence text) {
    checkIsWritable();
    mTextOrDescription = text;
  }

  public CharSequence getTextOrDescription() {
    return mTextOrDescription;
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

  public void setDeselectedText(CharSequence text) {
    checkIsWritable();
    mDeselectedText = text;
  }

  public CharSequence getDeselectedText() {
    return mDeselectedText;
  }

  public void setSelectedText(CharSequence text) {
    checkIsWritable();
    mSelectedText = text;
  }

  public CharSequence getSelectedText() {
    return mSelectedText;
  }

  public void setTraversedText(CharSequence text) {
    checkIsWritable();
    mTraversedText = text;
  }

  public CharSequence getTraversedText() {
    return mTraversedText;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to display the data

  /** Shows the non-default data values. */
  @Override
  public String toString() {
    StringBuilder string = new StringBuilder();
    string.append(String.format(" event=%s", Compositor.eventTypeToString(mEvent)));
    string.append(String.format(" reason=%s", quote(mReason)));
    string.append(optionalMemberString("isCut", mIsCutAction));
    string.append(optionalMemberString("isPaste", mIsPasteAction));
    string.append(optionalMemberString("textOrDescription", mTextOrDescription));
    string.append(optionalMemberString("removedText", mRemovedText));
    string.append(optionalMemberString("addedText", mAddedText));
    string.append(optionalMemberString("initialWord", mInitialWord));
    string.append(optionalMemberString("deselectedText", mDeselectedText));
    string.append(optionalMemberString("selectedText", mSelectedText));
    string.append(optionalMemberString("traversedText", mTraversedText));
    return string.toString();
  }

  private static CharSequence optionalMemberString(String name, boolean value) {
    return value ? String.format(" %s=%s", name, value) : "";
  }

  private static CharSequence optionalMemberString(String name, CharSequence value) {
    return (value == null) ? "" : String.format(" %s=%s", name, quote(value));
  }

  private static CharSequence quote(CharSequence text) {
    return (text == null) ? "null" : String.format("\"%s\"", text);
  }
}
