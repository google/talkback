/*
 * Copyright 2015 Google Inc.
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

package com.google.android.accessibility.talkback.controller;

public interface DimScreenController {

  /**
   * @return Whether the dim-screen setting is enabled in shared preferences. This corresponds to
   *     the actual dimming state except when TalkBack is suspended or off.
   */
  boolean isDimmingEnabled();

  /** @return whether exit dim screen instruction is displayed on screen */
  boolean isInstructionDisplayed();

  /** Turns dimming off and sets the shared preference off as well. */
  void disableDimming();

  /** Turn on screen dimming without setting the shared preference. */
  void makeScreenDim();

  /**
   * Dims the screen and also sets the dim screen shared preference.
   *
   * @return {@code true} if it shows dialog depending on users pref. {@code false} does not mean
   *     that dimming was failed, it just means that the dialog was not shown.
   */
  boolean enableDimmingAndShowConfirmDialog();

  /** @return the user pref to show the dim screen dialog. */
  boolean getShouldShowDialogPref();

  void resume();

  void suspend();

  void shutdown();
}
