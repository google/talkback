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

import com.google.android.accessibility.utils.ReadOnly;

/**
 * Data structure containing a more specific event type, along with extracted data from the event.
 * Most fields are used by only one event type.
 */
public class EventInterpretation extends ReadOnly {

  ////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private @Compositor.Event int mEvent;
  private TextEventInterpretation mText;
  private SelectorEventInterpretation mSelector;
  private ScrollEventInterpretation mScroll;

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

  public @Compositor.Event int getEvent() {
    return mEvent;
  }

  public void setTextEventInterpretation(TextEventInterpretation text) {
    checkIsWritable();
    mText = text;
  }

  public TextEventInterpretation getText() {
    return mText;
  }

  public void setSelector(SelectorEventInterpretation selector) {
    checkIsWritable();
    mSelector = selector;
  }

  public SelectorEventInterpretation getSelector() {
    return mSelector;
  }

  public void setScroll(ScrollEventInterpretation scroll) {
    checkIsWritable();
    mScroll = scroll;
  }

  public ScrollEventInterpretation getScroll() {
    return mScroll;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to display the data

  /** Show only non-default data values. */
  @Override
  public String toString() {
    StringBuilder string = new StringBuilder();
    string.append(String.format(" event=%s", Compositor.eventTypeToString(mEvent)));
    string.append(optionalMemberString("text", mText));
    string.append(optionalMemberString("selector", mSelector));
    string.append(optionalMemberString("scroll", mScroll));
    return string.toString();
  }

  public static String optionalMemberString(String name, Object member) {
    return (member == null) ? "" : String.format(" %s=%s", name, member);
  }
}
