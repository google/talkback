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

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;

/**
 * Data structure containing extracted data from {@link
 * AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} events.
 */
public class AccessibilityFocusEventInterpretation extends ReadOnly {

  private @Compositor.Event int event;

  private boolean forceFeedbackAudioPlaybackActive = false;
  private boolean forceFeedbackMicrophoneActive = false;
  private boolean forceFeedbackSsbActive = false;
  private boolean isInitialFocusAfterScreenStateChange = false;
  private boolean shouldMuteFeedback = false;

  public AccessibilityFocusEventInterpretation(@Compositor.Event int eventArg) {
    this.event = eventArg;
  }

  public void setEvent(@Compositor.Event int event) {
    checkIsWritable();
    this.event = event;
  }

  public @Compositor.Event int getEvent() {
    return event;
  }

  public void setForceFeedbackAudioPlaybackActive(boolean forceFeedback) {
    checkIsWritable();
    this.forceFeedbackAudioPlaybackActive = forceFeedback;
  }

  public void setForceFeedbackMicrophoneActive(boolean forceFeedback) {
    checkIsWritable();
    this.forceFeedbackMicrophoneActive = forceFeedback;
  }

  public void setForceFeedbackSsbActive(boolean forceFeedback) {
    checkIsWritable();
    this.forceFeedbackSsbActive = forceFeedback;
  }

  public boolean getForceFeedbackAudioPlaybackActive() {
    return forceFeedbackAudioPlaybackActive;
  }

  public boolean getForceFeedbackMicrophoneActive() {
    return forceFeedbackMicrophoneActive;
  }

  public boolean getForceFeedbackSsbActive() {
    return forceFeedbackSsbActive;
  }

  public void setShouldMuteFeedback(boolean shouldMuteFeedback) {
    checkIsWritable();
    this.shouldMuteFeedback = shouldMuteFeedback;
  }

  public boolean getShouldMuteFeedback() {
    return shouldMuteFeedback;
  }

  public void setIsInitialFocusAfterScreenStateChange(
      boolean isInitialFocusAfterScreenStateChange) {
    checkIsWritable();
    this.isInitialFocusAfterScreenStateChange = isInitialFocusAfterScreenStateChange;
  }

  public boolean getIsInitialFocusAfterScreenStateChange() {
    return isInitialFocusAfterScreenStateChange;
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        "AccessibilityFocusEventInterpretation{",
        StringBuilderUtils.optionalInt("event", event, -1),
        StringBuilderUtils.optionalTag(
            "forceFeedbackAudioPlaybackActive", forceFeedbackAudioPlaybackActive),
        StringBuilderUtils.optionalTag(
            "forceFeedbackMicrophoneActive", forceFeedbackMicrophoneActive),
        StringBuilderUtils.optionalTag("forceFeedbackSsbActive", forceFeedbackSsbActive),
        StringBuilderUtils.optionalTag("shouldMuteFeedback", shouldMuteFeedback),
        StringBuilderUtils.optionalTag(
            "isInitialFocusAfterScreenStateChange", isInitialFocusAfterScreenStateChange),
        "}");
  }
}
