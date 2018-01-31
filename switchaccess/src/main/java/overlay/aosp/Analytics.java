/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.switchaccess.SwitchAccessService;

/** Singleton instance meant for gathering analytics data. This class currently does nothing. */
public class Analytics implements KeyboardAction.KeyboardActionListener, ScreenViewListener {

  private static Analytics sAnalytics;

  private Analytics(SwitchAccessService service) {}

  public static Analytics getOrCreateInstance(SwitchAccessService service) {
    if (sAnalytics == null) {
      sAnalytics = new Analytics(service);
    }
    return sAnalytics;
  }

  public static Analytics getInstanceIfExists() {
    return sAnalytics;
  }

  public void stop() {}

  @Override
  public void onKeyboardAction(int preferenceIdForAction) {}

  @Override
  public void onScreenShown(String screenName) {}
}
