/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.utils.input;

/** Gets information about the window on the screen. */
public interface WindowsDelegate {

  /** Gets window title given the window ID. */
  CharSequence getWindowTitle(int windowId);

  /** Gets the title of the accessibility pane associated with the given window ID. */
  CharSequence getAccessibilityPaneTitle(int windowId);

  /**
   * Determines whether the screen is in the split-screen mode, where the screen has two
   * non-parented application windows.
   */
  boolean isSplitScreenMode(int displayId);

  /**
   * Returns {@code true} if the window contains the accessibility pane.
   *
   * @param windowId the ID of the window info
   */
  boolean hasAccessibilityPane(int windowId);
}
