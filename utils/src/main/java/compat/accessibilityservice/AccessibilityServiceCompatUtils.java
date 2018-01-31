/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.utils.compat.accessibilityservice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.WindowManager;
import java.util.List;

public class AccessibilityServiceCompatUtils {

  /** @return root node of the Application window */
  public static AccessibilityNodeInfoCompat getRootInActiveWindow(AccessibilityService service) {
    if (service == null) {
      return null;
    }

    AccessibilityNodeInfo root = service.getRootInActiveWindow();
    if (root == null) {
      return null;
    }
    return AccessibilityNodeInfoUtils.toCompat(root);
  }

  /** @return root node of the window that currently has accessibility focus */
  public static AccessibilityNodeInfoCompat getRootInAccessibilityFocusedWindow(
      AccessibilityService service) {
    if (service == null) {
      return null;
    }

    AccessibilityNodeInfo focusedRoot = null;
    if (BuildVersionUtils.isAtLeastLMR1()) {
      List<AccessibilityWindowInfo> windows = service.getWindows();
      // Create window manager with fake value of isInRTL = false. This is okay here since
      // isInRTL will not change the result of getCurrentWindow.
      WindowManager manager = new WindowManager(false /* isInRTL */);
      manager.setWindows(windows);
      AccessibilityWindowInfo accessibilityFocusedWindow =
          manager.getCurrentWindow(false /* useInputFocus */);

      if (accessibilityFocusedWindow != null) {
        focusedRoot = accessibilityFocusedWindow.getRoot();
      }
    }

    if (focusedRoot == null) {
      focusedRoot = service.getRootInActiveWindow();
    }

    if (focusedRoot == null) {
      return null;
    }

    return AccessibilityNodeInfoUtils.toCompat(focusedRoot);
  }

  public static String gestureIdToString(int gestureId) {
    switch (gestureId) {
      case AccessibilityService.GESTURE_SWIPE_DOWN:
        return "GESTURE_SWIPE_DOWN";
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT:
        return "GESTURE_SWIPE_DOWN_AND_LEFT";
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT:
        return "GESTURE_SWIPE_DOWN_AND_RIGHT";
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
        return "GESTURE_SWIPE_DOWN_AND_UP";
      case AccessibilityService.GESTURE_SWIPE_LEFT:
        return "GESTURE_SWIPE_LEFT";
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN:
        return "GESTURE_SWIPE_LEFT_AND_DOWN";
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
        return "GESTURE_SWIPE_LEFT_AND_RIGHT";
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP:
        return "GESTURE_SWIPE_LEFT_AND_UP";
      case AccessibilityService.GESTURE_SWIPE_RIGHT:
        return "GESTURE_SWIPE_RIGHT";
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN:
        return "GESTURE_SWIPE_RIGHT_AND_DOWN";
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
        return "GESTURE_SWIPE_RIGHT_AND_LEFT";
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP:
        return "GESTURE_SWIPE_RIGHT_AND_UP";
      case AccessibilityService.GESTURE_SWIPE_UP:
        return "GESTURE_SWIPE_UP";
      case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
        return "GESTURE_SWIPE_UP_AND_DOWN";
      case AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT:
        return "GESTURE_SWIPE_UP_AND_LEFT";
      case AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT:
        return "GESTURE_SWIPE_UP_AND_RIGHT";
      default:
        return "(unhandled " + gestureId + ")";
    }
  }

  /**
   * Gets string representative of a fingerprint gesture.
   *
   * @param fingerprintGestureId The fingerprint gesture Id
   * @return The string representative of the fingeprint gesture
   */
  @TargetApi(Build.VERSION_CODES.O)
  public static String fingerprintGestureIdToString(int fingerprintGestureId) {
    switch (fingerprintGestureId) {
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT:
        return "FINGERPRINT_GESTURE_SWIPE_LEFT";
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT:
        return "FINGERPRINT_GESTURE_SWIPE_RIGHT";
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP:
        return "FINGERPRINT_GESTURE_SWIPE_UP";
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN:
        return "FINGERPRINT_GESTURE_SWIPE_DOWN";
      default:
        return "(unhandled " + fingerprintGestureId + ")";
    }
  }
}
