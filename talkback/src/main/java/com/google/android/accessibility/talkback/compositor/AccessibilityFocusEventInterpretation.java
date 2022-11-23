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

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;

/**
 * Data structure containing extracted data from {@link
 * AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} events.
 */
public class AccessibilityFocusEventInterpretation extends ReadOnly {

  @Compositor.Event private int event;

  private boolean forceFeedbackEvenIfAudioPlaybackActive = true;
  private boolean forceFeedbackEvenIfMicrophoneActive = true;
  private boolean forceFeedbackEvenIfSsbActive = false;
  private boolean isInitialFocusAfterScreenStateChange = false;
  private boolean shouldMuteFeedback = false;
  private boolean isNavigateByUser = false;

  public AccessibilityFocusEventInterpretation(@Compositor.Event int eventArg) {
    this.event = eventArg;
  }

  public void setEvent(@Compositor.Event int event) {
    checkIsWritable();
    this.event = event;
  }

  @Compositor.Event
  public int getEvent() {
    return event;
  }

  public void setForceFeedbackEvenIfAudioPlaybackActive(boolean forceFeedback) {
    checkIsWritable();
    this.forceFeedbackEvenIfAudioPlaybackActive = forceFeedback;
  }

  public void setForceFeedbackEvenIfMicrophoneActive(boolean forceFeedback) {
    checkIsWritable();
    this.forceFeedbackEvenIfMicrophoneActive = forceFeedback;
  }

  public void setForceFeedbackEvenIfSsbActive(boolean forceFeedback) {
    checkIsWritable();
    this.forceFeedbackEvenIfSsbActive = forceFeedback;
  }

  public boolean getForceFeedbackEvenIfAudioPlaybackActive() {
    return forceFeedbackEvenIfAudioPlaybackActive;
  }

  public boolean getForceFeedbackEvenIfMicrophoneActive() {
    return forceFeedbackEvenIfMicrophoneActive;
  }

  public boolean getForceFeedbackEvenIfSsbActive() {
    return forceFeedbackEvenIfSsbActive;
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

  public void setIsNavigateByUser(boolean isNavigateByUser) {
    checkIsWritable();
    this.isNavigateByUser = isNavigateByUser;
  }

  public boolean getIsNavigateByUser() {
    return isNavigateByUser;
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        "AccessibilityFocusEventInterpretation{",
        StringBuilderUtils.optionalInt("event", event, -1),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfAudioPlaybackActive", forceFeedbackEvenIfAudioPlaybackActive),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfMicrophoneActive", forceFeedbackEvenIfMicrophoneActive),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfSsbActive", forceFeedbackEvenIfSsbActive),
        StringBuilderUtils.optionalTag("shouldMuteFeedback", shouldMuteFeedback),
        StringBuilderUtils.optionalTag(
            "isInitialFocusAfterScreenStateChange", isInitialFocusAfterScreenStateChange),
        StringBuilderUtils.optionalTag("isNavigateByUser", isNavigateByUser),
        "}");
  }
}
