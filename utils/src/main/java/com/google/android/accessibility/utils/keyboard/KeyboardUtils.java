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
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import java.util.ArrayList;
import java.util.List;

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

    boolean isSoftKeyboardActive = AccessibilityServiceCompatUtils.isInputWindowOnScreen(service);
    boolean isHardKeyboardActive =
        (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);

    return isSoftKeyboardActive || isHardKeyboardActive;
  }

  /** Returns {@code true} if {@code componentName} is an enabled input method */
  public static boolean isImeEnabled(Context context, ComponentName componentName) {
    for (InputMethodInfo inputMethodInfo : getEnabledInputMethodList(context)) {
      if (inputMethodInfo.getComponent().equals(componentName)) {
        return true;
      }
    }
    return false;
  }

  /** Returns {@code true} if the system has more than one IME enabled. */
  public static boolean areMultipleImesEnabled(Context context) {
    List<InputMethodInfo> list = getEnabledInputMethodList(context);
    return list != null && list.size() > 1;
  }

  /** Gets id of first enabled {@link InputMethodInfo}whose package matches {@code packageName}. */
  public static String getEnabledImeId(Context context, String packageName) {
    for (InputMethodInfo inputMethodInfo : getEnabledInputMethodList(context)) {
      if (inputMethodInfo.getPackageName().equals(packageName)) {
        return inputMethodInfo.getId();
      }
    }
    return "";
  }

  /**
   * Returns next enabled ime id. If it's the tail of enabled list, return the first enabled ime.
   * Empty if it's the only enabled ime.
   */
  public static String getNextEnabledImeId(Context context) {
    String id = "";
    String firstId = "";
    boolean next = false;
    for (InputMethodInfo inputMethodInfo : getEnabledInputMethodList(context)) {
      if (next) {
        id = inputMethodInfo.getId();
        break;
      } else if (inputMethodInfo.getPackageName().equals(context.getPackageName())) {
        next = true;
      } else if (TextUtils.isEmpty(firstId)) {
        firstId = inputMethodInfo.getId();
      }
    }
    return next && TextUtils.isEmpty(id) ? firstId : id;
  }

  private static List<InputMethodInfo> getEnabledInputMethodList(Context context) {
    InputMethodManager inputMethodManager =
        (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (inputMethodManager != null) {
      List<InputMethodInfo> inputMethodInfoList = inputMethodManager.getEnabledInputMethodList();
      if (inputMethodInfoList != null) {
        return inputMethodInfoList;
      }
    }
    return new ArrayList<>();
  }
}
