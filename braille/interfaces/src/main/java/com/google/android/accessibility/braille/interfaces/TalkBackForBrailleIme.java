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
package com.google.android.accessibility.braille.interfaces;

import android.graphics.Region;
import android.view.WindowManager;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Exposes some TalkBack behavior to BrailleIme. */
public interface TalkBackForBrailleIme {

  /** Returns the callback of BrailleIme to TalkBack. */
  interface BrailleImeForTalkBackProvider {
    /** A reference to the active Braille IME if any. */
    BrailleImeForTalkBack getBrailleImeForTalkBack();
  }

  /** TalkBack service status. */
  enum ServiceStatus {
    ON,
    OFF,
    SUSPEND,
  }

  /** Performs specific actions for screen reader. */
  @CanIgnoreReturnValue
  boolean performAction(ScreenReaderAction action, Object... arg);

  /**
   * BrailleIme invokes this when it becomes active. When TalkBack gets this signal, it should enter
   * an IME-friendly mode (by disabling Explore-by-Touch, for example).
   */
  void onBrailleImeActivated(
      boolean disableEbt,
      boolean usePassThrough,
      Region passThroughRegion);

  /**
   * BrailleIme invokes this when it becomes inactive. When TalkBack gets this signal, it should
   * restore its typical (non-IME-friendly) mode.
   */
  void onBrailleImeInactivated(boolean usePassThrough, boolean brailleImeActive);

  /** TalkBack provides the ability to enable BrailleIme. */
  boolean setInputMethodEnabled();

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

  /** Checks should braille keyboard announce character when on-screen mode. */
  boolean shouldAnnounceCharacterForOnScreenKeyboard();

  /** Checks should braille keyboard announce character when physical mode. */
  boolean shouldAnnounceCharacterForPhysicalKeyboard();

  /** Checks should braille keyboard announce password for typing. */
  boolean shouldSpeakPassword();

  /** Whether use character granularity to be the moving granularity. */
  boolean shouldUseCharacterGranularity();

  boolean isCurrentGranularityTypoCorrection();

  /** Moves the text cursor forward by current granularity from Talkback. */
  boolean moveCursorForwardByDefault();

  /** Moves the text cursor backward by current granularity from Talkback. */
  @CanIgnoreReturnValue
  boolean moveCursorBackwardByDefault();

  /** Checks the status of the screen. */
  boolean isHideScreenMode();

  /** Switches to next granularity. */
  boolean switchToNextEditingGranularity();

  /** Switches to previous granularity. */
  boolean switchToPreviousEditingGranularity();

  /** Resets the granularity to CHARACTER. */
  void resetGranularity();

  /** Provides BrailleIme callback provider to TalkBack. */
  BrailleImeForTalkBackProvider getBrailleImeForTalkBackProvider();

  /** Sets BrailleIme callback provider. */
  void setBrailleImeForTalkBack(BrailleImeForTalkBack brailleImeForTalkBack);

  /** Creates {@link FocusFinder} instance. */
  FocusFinder createFocusFinder();
}
