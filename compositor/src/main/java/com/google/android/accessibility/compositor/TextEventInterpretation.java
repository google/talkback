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

import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;

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

  @Nullable private CharSequence textOrDescription;
  private CharSequence mRemovedText;
  private CharSequence mAddedText;
  private CharSequence mInitialWord;
  private @Nullable CharSequence mDeselectedText;
  private @Nullable CharSequence mSelectedText;
  private @Nullable CharSequence mTraversedText;

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

  public @Nullable CharSequence getDeselectedText() {
    return mDeselectedText;
  }

  public void setSelectedText(@Nullable CharSequence text) {
    checkIsWritable();
    mSelectedText = text;
  }

  public @Nullable CharSequence getSelectedText() {
    return mSelectedText;
  }

  public void setTraversedText(@Nullable CharSequence text) {
    checkIsWritable();
    mTraversedText = text;
  }

  public @Nullable CharSequence getTraversedText() {
    return mTraversedText;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to display the data

  /** Display only non-default fields. */
  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalField("Event", Compositor.eventTypeToString(mEvent)),
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
}
