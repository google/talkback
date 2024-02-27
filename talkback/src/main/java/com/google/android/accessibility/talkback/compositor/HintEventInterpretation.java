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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Data about internally-generated hint events. */
public class HintEventInterpretation extends ReadOnly {

  //////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Types of hints -- currently accessibility focus, and input focus. */
  @IntDef({
    HINT_TYPE_NONE,
    HINT_TYPE_ACCESSIBILITY_FOCUS,
    HINT_TYPE_INPUT_FOCUS,
    HINT_TYPE_SCREEN,
    HINT_TYPE_SELECTOR,
    HINT_TYPE_TEXT_SUGGESTION,
    HINT_TYPE_TYPO
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface HintType {}

  public static final int HINT_TYPE_NONE = 0;
  public static final int HINT_TYPE_ACCESSIBILITY_FOCUS = 1;
  public static final int HINT_TYPE_INPUT_FOCUS = 2;
  public static final int HINT_TYPE_SCREEN = 3;
  public static final int HINT_TYPE_SELECTOR = 4;
  public static final int HINT_TYPE_TEXT_SUGGESTION = 5;
  public static final int HINT_TYPE_TYPO = 6;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  @HintType private final int mHintType;
  private boolean forceFeedbackEvenIfAudioPlaybackActive = false;
  private boolean forceFeedbackEvenIfMicrophoneActive = false;
  @Nullable private CharSequence mText;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public HintEventInterpretation(@HintType int hintType) {
    mHintType = hintType;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  @HintType
  public int getHintType() {
    return mHintType;
  }

  public void setForceFeedbackEvenIfAudioPlaybackActive(boolean force) {
    checkIsWritable();
    forceFeedbackEvenIfAudioPlaybackActive = force;
  }

  public void setForceFeedbackEvenIfMicrophoneActive(boolean force) {
    checkIsWritable();
    forceFeedbackEvenIfMicrophoneActive = force;
  }

  public boolean getForceFeedbackEvenIfAudioPlaybackActive() {
    return forceFeedbackEvenIfAudioPlaybackActive;
  }

  public boolean getForceFeedbackEvenIfMicrophoneActive() {
    return forceFeedbackEvenIfMicrophoneActive;
  }

  public void setText(CharSequence text) {
    checkIsWritable();
    mText = text;
  }

  @Nullable
  public CharSequence getText() {
    return mText;
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        hintTypeToString(mHintType),
        StringBuilderUtils.optionalText("Text", mText),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfAudioPlaybackActive", forceFeedbackEvenIfAudioPlaybackActive),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfMicrophoneActive", forceFeedbackEvenIfMicrophoneActive));
  }

  public static String hintTypeToString(@HintType int hintType) {
    switch (hintType) {
      case HINT_TYPE_NONE:
        return "HINT_TYPE_NONE";
      case HINT_TYPE_ACCESSIBILITY_FOCUS:
        return "HINT_TYPE_ACCESSIBILITY_FOCUS";
      case HINT_TYPE_INPUT_FOCUS:
        return "HINT_TYPE_INPUT_FOCUS";
      case HINT_TYPE_SCREEN:
        return "HINT_TYPE_SCREEN";
      case HINT_TYPE_SELECTOR:
        return "HINT_TYPE_SELECTOR";
      case HINT_TYPE_TEXT_SUGGESTION:
        return "HINT_TYPE_TEXT_SUGGESTION";
      case HINT_TYPE_TYPO:
        return "HINT_TYPE_TYPO";
      default:
        return "(unhandled)";
    }
  }
}
