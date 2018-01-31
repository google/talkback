/*
 * Copyright (C) 2015 Google Inc.
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

import android.accessibilityservice.AccessibilityService;

/**
 * Enum that associates preferences with global actions. TODO rename to be less similar to
 * keyboardaction class
 */
public enum KeyboardBasedGlobalAction {
  BACK(R.string.pref_key_mapped_to_back_key, AccessibilityService.GLOBAL_ACTION_BACK),
  HOME(R.string.pref_key_mapped_to_home_key, AccessibilityService.GLOBAL_ACTION_HOME),
  NOTIFICATIONS(
      R.string.pref_key_mapped_to_notifications_key,
      AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
  QUICK_SETTINGS(
      R.string.pref_key_mapped_to_quick_settings_key,
      AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
  OVERVIEW(R.string.pref_key_mapped_to_overview_key, AccessibilityService.GLOBAL_ACTION_RECENTS);

  private final int mPreferenceResId;

  private final int mGlobalActionId;

  private KeyboardBasedGlobalAction(int preferenceResId, int globalActionId) {
    mPreferenceResId = preferenceResId;
    mGlobalActionId = globalActionId;
  }

  public int getPreferenceResId() {
    return mPreferenceResId;
  }

  public int getGlobalActionId() {
    return mGlobalActionId;
  }
}
