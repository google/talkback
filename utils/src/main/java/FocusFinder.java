/*
 * Copyright (C) 2014 Google Inc.
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

package com.google.android.accessibility.utils;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Functions to find focus.
 *
 * <p>NOTE: To give a consistent behaviour, this code should be kept in sync with the relevant
 * subset of code in the {@code CursorController} class in TalkBack.
 */
public class FocusFinder {

  private static final String TAG = "FocusFinder";

  /** This class should not be instantiated. */
  private FocusFinder() {}

  public static @Nullable AccessibilityNodeInfoCompat getFocusedNode(
      AccessibilityService service, boolean fallbackOnRoot) {
    AccessibilityNodeInfo root = service.getRootInActiveWindow();
    AccessibilityNodeInfo focused = null;

    try {
      AccessibilityNodeInfo ret = null;
      if (root != null) {
        focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focused != null && focused.isVisibleToUser()) {
          ret = focused;
          focused = null;
        } else if (fallbackOnRoot) {
          ret = root;
          root = null;
        }
      } else {
        LogUtils.e(TAG, "No current window root");
      }

      if (ret != null) {
        return AccessibilityNodeInfoUtils.toCompat(ret);
      }
    } finally {
      if (root != null) {
        root.recycle();
      }

      if (focused != null) {
        focused.recycle();
      }
    }

    return null;
  }
}
