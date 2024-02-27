/*
 * Copyright 2021 Google Inc.
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

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Exposes some TalkBack behavior to BrailleDisplay. */
public interface TalkBackForBrailleDisplay {
  /** Obtains the AccessibilityService. */
  AccessibilityService getAccessibilityService();

  /** Performs specific actions for screen reader. */
  @CanIgnoreReturnValue
  boolean performAction(ScreenReaderAction action, Object... arg);

  /** Sets voice feedback state. */
  boolean setVoiceFeedback(boolean enabled);

  /** Gets voice feedback enabled status. */
  boolean getVoiceFeedbackEnabled();

  /** Gets accessibility focus node. */
  AccessibilityNodeInfoCompat getAccessibilityFocusNode(boolean fallbackOnRoot);

  /** Creates {@link FocusFinder} instance. */
  FocusFinder createFocusFinder();

  /** Gets TalkBack's feedback controller. */
  FeedbackController getFeedBackController();

  /** Shows custom label dialog for the Accessibility node to add or edit a label. */
  boolean showLabelDialog(CustomLabelAction action, AccessibilityNodeInfoCompat node);

  /** Gets defined custom label. */
  CharSequence getCustomLabelText(AccessibilityNodeInfoCompat node);

  /** Returns whether {@param AccessibilityNodeInfoCompat node} needs a label. */
  boolean needsLabel(AccessibilityNodeInfoCompat node);

  /** Returns whether a label can be added for this {@param AccessibilityNodeInfoCompat}. */
  boolean supportsLabel(AccessibilityNodeInfoCompat node);

  /** TalkBack provides the ability to speak an announcement. */
  void speak(CharSequence charSequence, int delayMs, SpeakOptions speakOptions);

  /** Returns keyboard status. */
  boolean isOnscreenKeyboardActive();

  /**
   * Returns active onscreen keyboard window name.
   *
   * @return empty if window title is null.
   */
  CharSequence getOnScreenKeyboardName();

  /** Custom label actions. */
  enum CustomLabelAction {
    ADD_LABEL,
    EDIT_LABEL
  }

  /** Switch the input method to braille keyboard. */
  @CanIgnoreReturnValue
  boolean switchInputMethodToBrailleKeyboard();
}
