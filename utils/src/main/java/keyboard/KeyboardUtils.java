/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.utils.keyboard;

import android.accessibilityservice.AccessibilityService;
import android.content.res.Configuration;
import com.google.android.accessibility.utils.WindowManager;

/** Helper class for keyboard utility functions */
public class KeyboardUtils {

  /**
   * Returns true if either soft or hard keyboard is active.
   *
   * @param service Accessibility Service that is currently trying to get keyboard state.
   * @return {@code true} if either soft or hard keyborad is active, else {@code false}.
   */
  // TODO: Move the logic of updating keyboard state into WindowTracker.
  public static boolean isKeyboardActive(AccessibilityService service) {
    if (service == null) {
      return false;
    }
    Configuration config = service.getResources().getConfiguration();
    WindowManager windowManager = new WindowManager(service);

    boolean isSoftKeyboardActive = windowManager.isInputWindowOnScreen();
    boolean isHardKeyboardActive =
        (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);

    return isSoftKeyboardActive || isHardKeyboardActive;
  }
}
