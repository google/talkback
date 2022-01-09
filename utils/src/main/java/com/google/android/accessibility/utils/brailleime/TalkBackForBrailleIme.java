/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils.brailleime;

import android.graphics.Region;
import android.view.WindowManager;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/** Exposes some TalkBack behavior to BrailleIme. */
public interface TalkBackForBrailleIme {

  /** TalkBack service status. */
  public enum ServiceStatus {
    ON,
    OFF,
    SUSPEND,
  }

  /**
   * BrailleIme invokes this when it becomes active. When TalkBack gets this signal, it should enter
   * an IME-friendly mode (by disabling Explore-by-Touch, for example).
   */
  void onBrailleImeActivated(
      BrailleImeForTalkBack brailleImeForTalkBack,
      boolean usePassThrough,
      Region passThroughRegion);

  /**
   * BrailleIme invokes this when it becomes inactive. When TalkBack gets this signal, it should
   * restore its typical (non-IME-friendly) mode.
   */
  void onBrailleImeInactivated(boolean usePassThrough);

  /** TalkBack provides its privileged WindowManager to BrailleIme. */
  WindowManager getWindowManager();

  /** TalkBack provides its active/suspended/inactive status to BrailleIme. */
  ServiceStatus getServiceStatus();

  /** TalkBack provides the ability to speak an announcement via queue mode. */
  void speak(CharSequence charSequence, int delayMs, SpeakOptions speakOptions);

  /** TalkBack provides the ability to interrupt all of the queuing announcement. */
  void interruptSpeak();

  /** TalkBack provides the ability to play sound. */
  void playSound(int resId, int delayMs);

  /** Disables proximity sensor to silence speech. */
  void disableSilenceOnProximity();

  /** Restores the state of silence on proximity. */
  void restoreSilenceOnProximity();

  /** Checks a user is using TalkBack context menu or not. */
  boolean isContextMenuExist();

  /** Checks vibration feedback is enabled. */
  boolean isVibrationFeedbackEnabled();

  /** Checks should braille keyboard announce character itself. */
  boolean shouldAnnounceCharacter();
}
