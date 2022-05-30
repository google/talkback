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

import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.input.WindowsDelegate;
import java.util.Objects;

/** A helper class to get the active window and window titles on the current screen. */
public final class ScreenState implements WindowsDelegate {
  private final AccessibilityWindowInfo activeWindow;
  private WindowsDelegate windowsDelegate;
  private long screenTransitionStartTime = 0;

  public ScreenState(
      WindowsDelegate windowsDelegate, AccessibilityWindowInfo activeWindow, long eventTime) {
    this.windowsDelegate = windowsDelegate;
    this.activeWindow = activeWindow;
    this.screenTransitionStartTime = eventTime;
  }

  /** Returns title of window with given window ID. */
  @Override
  public CharSequence getWindowTitle(int windowId) {
    return windowsDelegate.getWindowTitle(windowId);
  }

  /** Returns whether is split screen mode. */
  @Override
  public boolean isSplitScreenMode(int displayId) {
    return windowsDelegate.isSplitScreenMode(displayId);
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

    return AccessibilityWindowInfoUtils.equals(activeWindow, otherScreen.activeWindow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        (activeWindow == null ? 0 : activeWindow.hashCode()), screenTransitionStartTime);
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalSubObj("activeWindow", activeWindow),
        StringBuilderUtils.optionalInt("screenTransitionStartTime", screenTransitionStartTime, 0));
  }
}
