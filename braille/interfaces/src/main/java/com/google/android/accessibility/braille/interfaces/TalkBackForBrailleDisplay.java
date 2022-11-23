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

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/** Exposes some TalkBack behavior to BrailleDisplay. */
public interface TalkBackForBrailleDisplay {
  /** Performs specific actions for screen reader. */
  boolean performAction(ScreenReaderAction action, Object... arg);

  /** Sets voice feedback state. */
  boolean setVoiceFeedback(boolean enabled);

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

  /** Returns the callback of BrailleIme to BrailleDisplay. */
  @Nullable
  BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay();

  /** TalkBack provides the ability to speak an announcement. */
  void speak(CharSequence charSequence, int delayMs, SpeakOptions speakOptions);

  /** Screen reader actions. */
  public enum ScreenReaderAction {
    NEXT_ITEM,
    PREVIOUS_ITEM,
    NEXT_LINE,
    PREVIOUS_LINE,
    NEXT_WINDOW,
    PREVIOUS_WINDOW,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    NAVIGATE_TO_TOP,
    NAVIGATE_TO_BOTTOM,
    NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD,
    NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD,
    CLICK_CURRENT,
    CLICK_NODE,
    LONG_CLICK_CURRENT,
    LONG_CLICK_NODE,
    PREVIOUS_READING_CONTROL,
    NEXT_READING_CONTROL,
    SCREEN_SEARCH,
    OPEN_TALKBACK_MENU,
    TOGGLE_VOICE_FEEDBACK,
    GLOBAL_HOME,
    GLOBAL_BACK,
    GLOBAL_RECENTS,
    GLOBAL_NOTIFICATIONS,
    GLOBAL_QUICK_SETTINGS,
    GLOBAL_ALL_APPS,
    WEB_NEXT_HEADING,
    WEB_PREVIOUS_HEADING,
    WEB_NEXT_CONTROL,
    WEB_PREVIOUS_CONTROL,
    WEB_NEXT_LINK,
    WEB_PREVIOUS_LINK,
  }

  /** Custom label actions. */
  enum CustomLabelAction {
    ADD_LABEL,
    EDIT_LABEL
  }
}
