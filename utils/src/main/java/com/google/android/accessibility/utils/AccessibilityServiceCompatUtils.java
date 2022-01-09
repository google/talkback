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
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.FingerprintGestureController;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AccessibilityServiceCompatUtils {

  private static final String TAG = "A11yServiceCompatUtils";

  /** Holds constants in support of BrailleIme. */
  public static class Constants {

    private Constants() {}

    /** The package name for the Messages app. */
    public static final String ANDROID_MESSAGES_PACKAGE_NAME = "com.google.android.apps.messaging";

    /** The package name for the Gboard app. */
    public static final String GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin";

    /** The package name for the Keep app. */
    public static final String KEEP_NOTES_PACKAGE_NAME = "com.google.android.keep";

    private static final String ACCESSIBILITY_SUITE_PACKAGE_NAME =
        PackageManagerUtils.TALBACK_PACKAGE;

    /** The name of the TalkBack Settings Activity. */
    public static final ComponentName SETTINGS_ACTIVITY =
        new ComponentName(
            ACCESSIBILITY_SUITE_PACKAGE_NAME, "com.android.talkback.TalkBackPreferencesActivity");

    /** The name of the TalkBack service. */
    public static final ComponentName TALKBACK_SERVICE =
        new ComponentName(
            ACCESSIBILITY_SUITE_PACKAGE_NAME, PackageManagerUtils.TALKBACK_SERVICE_NAME);

    /** The name of the Braille Ime. */
    public static final ComponentName BRAILLE_KEYBOARD =
        new ComponentName(
            ACCESSIBILITY_SUITE_PACKAGE_NAME,
            "com.google.android.accessibility.brailleime.BrailleIme");
  }

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

  public static @Nullable String getActiveWindowPackageName(AccessibilityService service) {
    @Nullable AccessibilityNodeInfoCompat rootNode = getRootInActiveWindow(service);
    try {
      return ((rootNode == null) || (rootNode.getPackageName() == null))
          ? null
          : rootNode.getPackageName().toString();
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(rootNode);
    }
  }

  public static List<AccessibilityWindowInfo> getWindows(AccessibilityService service) {
    if (BuildVersionUtils.isAtLeastN()) {
      // Use try/catch to fix REFERTO
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

  /** Returns whether input method window is on the screen. */
  public static boolean isInputWindowOnScreen(AccessibilityService service) {
    List<AccessibilityWindowInfo> windows = getWindows(service);
    for (AccessibilityWindowInfo window : windows) {
      if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        return true;
      }
    }
    return false;
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
   * <p>REFERTO. Works around NPE on some Moto devices running O.
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

  /** Returns if accessibility service is enabled. */
  public static boolean isAccessibilityServiceEnabled(Context context, String packageName) {
    @Nullable
    AccessibilityManager manager =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (manager == null) {
      return false;
    }
    List<AccessibilityServiceInfo> list =
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
    if (list != null) {
      for (AccessibilityServiceInfo serviceInfo : list) {
        if (serviceInfo.getId().contains(packageName)) {
          return true;
        }
      }
    }
    return false;
  }
}
