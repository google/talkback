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

package com.google.android.accessibility.utils;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.annotation.TargetApi;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AccessibilityServiceCompatUtils {

  private static final String TAG = "A11yServiceCompatUtils";

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

  public static List<AccessibilityWindowInfo> getWindows(AccessibilityService service) {
    if (BuildVersionUtils.isAtLeastN()) {
      // Use try/catch to fix 
      try {
        return service.getWindows();
      } catch (SecurityException e) {
        LogUtils.e(TAG, "SecurityException occurred at AccessibilityService#getWindows(): %s", e);
        return Collections.emptyList();
      }
    }
    // If build version is not isAtLeastN(), there is a chance of ClassCastException or
    // NullPointerException.
    try {
      return service.getWindows();
    } catch (Exception e) {
      LogUtils.e(TAG, "Exception occurred at AccessibilityService#getWindows(): %s", e);
      return Collections.emptyList();
    }
  }

  /** @return root node of the window that currently has accessibility focus */
  public static @Nullable AccessibilityNodeInfoCompat getRootInAccessibilityFocusedWindow(
      AccessibilityService service) {
    if (service == null) {
      return null;
    }

    AccessibilityNodeInfo focusedRoot = null;
    List<AccessibilityWindowInfo> windows = getWindows(service);
    // Create window manager with fake value of isInRTL = false. This is okay here since
    // isInRTL will not change the result of getCurrentWindow.
    WindowManager manager = new WindowManager(false /* isInRTL */);
    manager.setWindows(windows);
    AccessibilityWindowInfo accessibilityFocusedWindow =
        manager.getCurrentWindow(false /* useInputFocus */);

    if (accessibilityFocusedWindow != null) {
      focusedRoot = AccessibilityWindowInfoUtils.getRoot(accessibilityFocusedWindow);
    }

    if (focusedRoot == null) {
      focusedRoot = service.getRootInActiveWindow();
    }

    if (focusedRoot == null) {
      return null;
    }

    return AccessibilityNodeInfoUtils.toCompat(focusedRoot);
  }

  public static AccessibilityWindowInfo getActiveWidow(AccessibilityService service) {
    if (service == null) {
      return null;
    }

    AccessibilityNodeInfo rootInActiveWindow = service.getRootInActiveWindow();
    if (rootInActiveWindow == null) {
      return null;
    }
    AccessibilityWindowInfo window = AccessibilityNodeInfoUtils.getWindow(rootInActiveWindow);
    rootInActiveWindow.recycle();
    return window;
  }

  public static AccessibilityNodeInfoCompat getInputFocusedNode(AccessibilityService service) {
    // TODO: Shall we use active window or accessibility focused window?
    AccessibilityNodeInfoCompat activeRoot = getRootInActiveWindow(service);
    if (activeRoot != null) {
      try {
        return activeRoot.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
      } finally {
        activeRoot.recycle();
      }
    }
    return null;
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

  /**
   * Returns {@code true} if a11y button is currently available.
   *
   * <p>. Works around NPE on some Moto devices running O.
   */
  public static boolean isAccessibilityButtonAvailableCompat(
      AccessibilityButtonController controller) {
    try {
      return controller.isAccessibilityButtonAvailable();
    } catch (NullPointerException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }
}
