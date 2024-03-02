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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.input.WindowsDelegate;
import java.util.Objects;

/** A helper class to get the active window and window titles on the current screen. */
public final class ScreenState implements WindowsDelegate {
  private final AccessibilityWindowInfo activeWindow;
  private final WindowsDelegate windowsDelegate;
  private long screenTransitionStartTime = 0;
  private boolean interpretFirstTimeWhenWakeUp;

  public ScreenState(
      WindowsDelegate windowsDelegate, AccessibilityWindowInfo activeWindow, long eventTime) {
    this(windowsDelegate, activeWindow, eventTime, false);
  }

  public ScreenState(
      WindowsDelegate windowsDelegate,
      AccessibilityWindowInfo activeWindow,
      long eventTime,
      boolean interpretFirstTimeWhenWakeUp) {
    this.windowsDelegate = windowsDelegate;
    this.activeWindow = activeWindow;
    this.screenTransitionStartTime = eventTime;
    this.interpretFirstTimeWhenWakeUp = interpretFirstTimeWhenWakeUp;
  }

  /** Returns title of window with given window ID. */
  @Override
  public CharSequence getWindowTitle(int windowId) {
    return windowsDelegate.getWindowTitle(windowId);
  }

  @Override
  public CharSequence getAccessibilityPaneTitle(int windowId) {
    CharSequence accessibilityPaneTitle = windowsDelegate.getAccessibilityPaneTitle(windowId);
    return TextUtils.isEmpty(accessibilityPaneTitle) ? "" : accessibilityPaneTitle;
  }

  /** Returns whether is split screen mode. */
  @Override
  public boolean isSplitScreenMode(int displayId) {
    return windowsDelegate.isSplitScreenMode(displayId);
  }

  @Override
  public boolean hasAccessibilityPane(int windowId) {
    return windowsDelegate.hasAccessibilityPane(windowId);
  }

  /** Returns the current active window. */
  @Nullable
  public AccessibilityWindowInfo getActiveWindow() {
    return activeWindow;
  }

  /** Returns the transition start time of the screen state. */
  public long getScreenTransitionStartTime() {
    return screenTransitionStartTime;
  }

  /** Returns {@code true} screen is interpreted as the first one after waking up. */
  public boolean isInterpretFirstTimeWhenWakeUp() {
    return interpretFirstTimeWhenWakeUp;
  }

  /** Reset the flag which indicates the first time interpretation from waking up to false. */
  public void consumeInterpretFirstTimeWhenWakeUp() {
    interpretFirstTimeWhenWakeUp = false;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ScreenState)) {
      return false;
    }
    if (other == this) {
      return true;
    }

    ScreenState otherScreen = (ScreenState) other;
    if (screenTransitionStartTime != ((ScreenState) other).getScreenTransitionStartTime()) {
      return false;
    }

    return ((ScreenState) other).interpretFirstTimeWhenWakeUp == interpretFirstTimeWhenWakeUp
        && AccessibilityWindowInfoUtils.equals(activeWindow, otherScreen.activeWindow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        (activeWindow == null ? 0 : activeWindow.hashCode()),
        screenTransitionStartTime,
        interpretFirstTimeWhenWakeUp);
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalSubObj("activeWindow", activeWindow),
        StringBuilderUtils.optionalInt("screenTransitionStartTime", screenTransitionStartTime, 0),
        StringBuilderUtils.optionalTag(
            "interpretFirstTimeWhenWakeUp", interpretFirstTimeWhenWakeUp));
  }
}
