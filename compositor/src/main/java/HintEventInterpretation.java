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
  @IntDef({HINT_TYPE_NONE, HINT_TYPE_ACCESSIBILITY_FOCUS, HINT_TYPE_INPUT_FOCUS, HINT_TYPE_SCREEN})
  @Retention(RetentionPolicy.SOURCE)
  public @interface HintType {}

  public static final int HINT_TYPE_NONE = 0;
  public static final int HINT_TYPE_ACCESSIBILITY_FOCUS = 1;
  public static final int HINT_TYPE_INPUT_FOCUS = 2;
  public static final int HINT_TYPE_SCREEN = 3;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private @HintType final int mHintType;
  private boolean forceFeedbackAudioPlaybackActive = false;
  private boolean forceFeedbackMicrophoneActive = false;
  private @Nullable CharSequence mText;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public HintEventInterpretation(@HintType int hintType) {
    mHintType = hintType;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public @HintType int getHintType() {
    return mHintType;
  }

  public void setForceFeedbackAudioPlaybackActive(boolean force) {
    checkIsWritable();
    forceFeedbackAudioPlaybackActive = force;
  }

  public void setForceFeedbackMicropphoneActive(boolean force) {
    checkIsWritable();
    forceFeedbackMicrophoneActive = force;
  }

  public boolean getForceFeedbackAudioPlaybackActive() {
    return forceFeedbackAudioPlaybackActive;
  }

  public boolean getForceFeedbackMicrophoneActive() {
    return forceFeedbackMicrophoneActive;
  }

  public void setText(CharSequence text) {
    checkIsWritable();
    mText = text;
  }

  public @Nullable CharSequence getText() {
    return mText;
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        hintTypeToString(mHintType),
        StringBuilderUtils.optionalText("Text", mText),
        StringBuilderUtils.optionalTag(
            "ForceFeedbackAudioPlaybackActive", forceFeedbackAudioPlaybackActive),
        StringBuilderUtils.optionalTag(
            "ForceFeedbackMicrophoneActive", forceFeedbackMicrophoneActive));
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
      default:
        return "(unhandled)";
    }
  }
}
