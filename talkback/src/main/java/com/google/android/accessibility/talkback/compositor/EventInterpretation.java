/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.input.TextEventInterpretation;

/**
 * Data structure containing a more specific event type, along with extracted data from the event.
 * Most fields are used by only one event type.
 */
public class EventInterpretation extends ReadOnly {

  ////////////////////////////////////////////////////////////////////////////////////
  // Member data

  @Compositor.Event private int mEvent;
  @Nullable private CharSequence mPackageName;
  @Nullable private TextEventInterpretation mText;
  @Nullable private AccessibilityFocusEventInterpretation mAccessibilityFocus;
  @Nullable private HintEventInterpretation mHint;

  ////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public EventInterpretation(@Compositor.Event int compositorEvent) {
    mEvent = compositorEvent;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to get/set data members

  public void setEvent(@Compositor.Event int event) {
    checkIsWritable();
    mEvent = event;
  }

  @Compositor.Event
  public int getEvent() {
    return mEvent;
  }

  public void setPackageName(CharSequence name) {
    checkIsWritable();
    mPackageName = name;
  }

  @Nullable
  public CharSequence getPackageName() {
    return mPackageName;
  }

  public void setTextEventInterpretation(TextEventInterpretation text) {
    checkIsWritable();
    mText = text;
  }

  @Nullable
  public TextEventInterpretation getText() {
    return mText;
  }

  public void setAccessibilityFocusInterpretation(
      AccessibilityFocusEventInterpretation interpretation) {
    mAccessibilityFocus = interpretation;
  }

  @Nullable
  public AccessibilityFocusEventInterpretation getAccessibilityFocusInterpretation() {
    return mAccessibilityFocus;
  }

  public void setHint(HintEventInterpretation hint) {
    checkIsWritable();
    mHint = hint;
  }

  @Nullable
  public HintEventInterpretation getHint() {
    return mHint;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to display the data

  /** Show only non-default data values. */
  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        Compositor.eventTypeToString(mEvent),
        StringBuilderUtils.optionalText("Package", mPackageName),
        StringBuilderUtils.optionalSubObj("Text", mText),
        StringBuilderUtils.optionalSubObj("Hint", mHint));
  }
}
