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

package com.google.android.accessibility.compositor;

import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;

/**
 * Data structure containing a more specific event type, along with extracted data from the event.
 * Most fields are used by only one event type.
 */
public class EventInterpretation extends ReadOnly {

  ////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private @Compositor.Event int mEvent;
  private @Nullable CharSequence mPackageName;
  private @Nullable TextEventInterpretation mText;
  private @Nullable AccessibilityFocusEventInterpretation mAccessibilityFocus;
  private @Nullable SelectorEventInterpretation mSelector;
  private @Nullable ScrollEventInterpretation mScroll;
  private @Nullable HintEventInterpretation mHint;
  // Whether the node associated with the event has multiple Switch Access actions. This variable
  // is only used when the event was generated from Switch Access.
  private boolean hasMultipleSwitchAccessActions;

  ////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public EventInterpretation(@Compositor.Event int compositorEvent) {
    mEvent = compositorEvent;
    hasMultipleSwitchAccessActions = false;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to get/set data members

  public void setEvent(@Compositor.Event int event) {
    checkIsWritable();
    mEvent = event;
  }

  public @Compositor.Event int getEvent() {
    return mEvent;
  }

  public void setPackageName(CharSequence name) {
    checkIsWritable();
    mPackageName = name;
  }

  public @Nullable CharSequence getPackageName() {
    return mPackageName;
  }

  public void setTextEventInterpretation(TextEventInterpretation text) {
    checkIsWritable();
    mText = text;
  }

  public @Nullable TextEventInterpretation getText() {
    return mText;
  }

  public void setAccessibilityFocusInterpretation(
      AccessibilityFocusEventInterpretation interpretation) {
    mAccessibilityFocus = interpretation;
  }

  public @Nullable AccessibilityFocusEventInterpretation getAccessibilityFocusInterpretation() {
    return mAccessibilityFocus;
  }

  public void setSelector(SelectorEventInterpretation selector) {
    checkIsWritable();
    mSelector = selector;
  }

  public @Nullable SelectorEventInterpretation getSelector() {
    return mSelector;
  }

  public void setScroll(ScrollEventInterpretation scroll) {
    checkIsWritable();
    mScroll = scroll;
  }

  public @Nullable ScrollEventInterpretation getScroll() {
    return mScroll;
  }

  public void setHint(HintEventInterpretation hint) {
    checkIsWritable();
    mHint = hint;
  }

  public @Nullable HintEventInterpretation getHint() {
    return mHint;
  }

  public void setHasMultipleSwitchAccessActions(boolean hasMultipleSwitchAccessActions) {
    checkIsWritable();
    this.hasMultipleSwitchAccessActions = hasMultipleSwitchAccessActions;
  }

  public boolean getHasMultipleSwitchAccessActions() {
    return hasMultipleSwitchAccessActions;
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
        StringBuilderUtils.optionalSubObj("Selector", mSelector),
        StringBuilderUtils.optionalSubObj("Scroll", mScroll),
        StringBuilderUtils.optionalSubObj("Hint", mHint),
        StringBuilderUtils.optionalTag(
            "HasMultipleSwitchAccessActions", hasMultipleSwitchAccessActions));
  }
}
