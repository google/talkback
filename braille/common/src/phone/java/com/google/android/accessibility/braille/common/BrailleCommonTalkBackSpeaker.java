/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.common;

import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleCommon;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/** Speaker provides announce abilities based on TalkBack. */
public class BrailleCommonTalkBackSpeaker implements TalkBackSpeaker {
  private static final String TAG = "BrailleCommonTalkBackSpeaker";

  @SuppressWarnings("NonFinalStaticField")
  private static BrailleCommonTalkBackSpeaker instance;

  @Nullable private TalkBackForBrailleCommon talkBackForBrailleCommon;

  /** Get the static singleton instance, creating it if necessary. */
  public static BrailleCommonTalkBackSpeaker getInstance() {
    if (instance == null) {
      instance = new BrailleCommonTalkBackSpeaker();
    }
    return instance;
  }

  public void initialize(TalkBackForBrailleCommon talkBackForBrailleCommon) {
    this.talkBackForBrailleCommon = talkBackForBrailleCommon;
  }

  @Override
  public void speak(CharSequence text, int delayMs, SpeakOptions speakOptions) {
    if (talkBackForBrailleCommon == null) {
      BrailleCommonLog.e(TAG, "Instance does not init correctly.");
      return;
    }
    talkBackForBrailleCommon.speak(text, delayMs, speakOptions);
  }
}
