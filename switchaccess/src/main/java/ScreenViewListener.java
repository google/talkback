/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess;

/** A listener to monitor when screens are shown. */
public interface ScreenViewListener {
  /**
   * Called when a screen is shown. This can be any types of screen, such as setup screen, Switch
   * Access menu screen, and settings screen.
   *
   * @param screenName The name of the screen shown
   */
  void onScreenShown(String screenName);
}
