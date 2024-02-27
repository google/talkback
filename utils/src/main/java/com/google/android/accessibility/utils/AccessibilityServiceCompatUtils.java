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
import android.content.ComponentName;
import android.content.Context;
import android.util.SparseArray;
import android.view.Display;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.gestures.GestureManifold;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AccessibilityServiceCompatUtils {

  private static final String TAG = "A11yServiceCompatUtils";

  /** Holds constants in support of BrailleIme and TalkBack-on-TV. */
  public static class Constants {

    private Constants() {}

    /** The package name for the Messages app. */
    public static final String ANDROID_MESSAGES_PACKAGE_NAME = "com.google.android.apps.messaging";

    /** The package name for the Gboard app. */
    public static final String GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin";

    /**
     * The package name for the Gboard app currently under development. It is expected to have
     * {@link #GBOARD_PACKAGE_NAME} as prefix.
     */
    public static final String GBOARD_PACKAGE_NAME_DEV = "com.google.android.inputmethod.latin.dev";

    /** The minimum version of the Gboard app that TalkBack is compatible with on TV. */
    public static final int GBOARD_MIN_SUPPORTED_VERSION = 107460889;

    private static final String ACCESSIBILITY_SUITE_PACKAGE_NAME =
        PackageManagerUtils.TALKBACK_PACKAGE;

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

    /** The name of the Braille keyboard settings activity. */
    public static final ComponentName BRAILLE_KEYBOARD_SETTINGS =
        new ComponentName(
            ACCESSIBILITY_SUITE_PACKAGE_NAME,
            "com.google.android.accessibility.brailleime.settings.BrailleImePreferencesActivity");

    /** The name of the Braille display settings activity. */
    public static final ComponentName BRAILLE_DISPLAY_SETTINGS =
        new ComponentName(
            ACCESSIBILITY_SUITE_PACKAGE_NAME,
            "com.google.android.accessibility.braille.brailledisplay.settings.BrailleDisplaySettingsActivity");
  }

  /** Returns root node of the Application window. */
  public static @Nullable AccessibilityNodeInfoCompat getRootInActiveWindow(
      AccessibilityService service) {
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
    return ((rootNode == null) || (rootNode.getPackageName() == null))
        ? null
        : rootNode.getPackageName().toString();
  }

  /**
   * Gets the windows on the screen of the default display.
   *
   * @see AccessibilityService#getWindows()
   */
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

  /**
   * Gets the windows on the screen of all displays.
   *
   * @see AccessibilityService#getWindowsOnAllDisplays()
   */
  @NonNull
  public static SparseArray<List<AccessibilityWindowInfo>> getWindowsOnAllDisplays(
      AccessibilityService service) {
    if (FeatureSupport.supportMultiDisplay()) {
      try {
        return service.getWindowsOnAllDisplays();
      } catch (SecurityException e) {
        LogUtils.e(
            TAG,
            "SecurityException occurred at AccessibilityService#getWindowsOnAllDisplays(): %s",
            e);
        return new SparseArray<>();
      }
    } else {
      SparseArray<List<AccessibilityWindowInfo>> windows = new SparseArray<>();
      windows.put(Display.DEFAULT_DISPLAY, getWindows(service));
      return windows;
    }
  }

  /**
   * Iterate through all window info list on all displays and operate the task on all of the window
   * info list.
   *
   * @param service The parent service
   * @param task The task to be performed for each element
   */
  public static void forEachWindowInfoListOnAllDisplays(
      AccessibilityService service, @NonNull Consumer<List<AccessibilityWindowInfo>> task) {
    SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays =
        AccessibilityServiceCompatUtils.getWindowsOnAllDisplays(service);
    final int displaySize = windowsOnAllDisplays.size();
    for (int i = 0; i < displaySize; i++) {
      task.accept(windowsOnAllDisplays.valueAt(i));
    }
  }

  public static @Nullable AccessibilityWindowInfo getActiveWidow(AccessibilityService service) {
    if (service == null) {
      return null;
    }

    AccessibilityNodeInfo rootInActiveWindow = service.getRootInActiveWindow();
    if (rootInActiveWindow == null) {
      return null;
    }
    return AccessibilityNodeInfoUtils.getWindow(rootInActiveWindow);
  }

  /** Returns whether input method window is on the screen. */
  public static boolean isInputWindowOnScreen(AccessibilityService service) {
    return getOnscreenInputWindowInfo(service) != null;
  }

  /** Returns the picture-in-picture window if open, or {@code null}. */
  public static @Nullable AccessibilityWindowInfo getPipWindow(AccessibilityService service) {
    for (AccessibilityWindowInfo window : getWindows(service)) {
      if (window.isInPictureInPictureMode()) {
        return window;
      }
    }
    return null;
  }

  /**
   * Returns the active onscreen input {@link AccessibilityWindowInfo}.
   *
   * @return null if no active input window.
   */
  public static @Nullable AccessibilityWindowInfo getOnscreenInputWindowInfo(
      AccessibilityService service) {
    List<AccessibilityWindowInfo> windows = getWindows(service);
    for (AccessibilityWindowInfo window : windows) {
      if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        return window;
      }
    }
    return null;
  }

  /** Returns a list of system window on the screen. */
  public static List<AccessibilityWindowInfo> getSystemWindows(AccessibilityService service) {
    List<AccessibilityWindowInfo> windows = new ArrayList<>();
    for (AccessibilityWindowInfo window : getWindows(service)) {
      if (window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
        windows.add(window);
      }
    }
    return windows;
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
      case AccessibilityService.GESTURE_DOUBLE_TAP:
        return "GESTURE_DOUBLE_TAP";
      case AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD:
        return "GESTURE_DOUBLE_TAP_AND_HOLD";
      case AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP:
        return "GESTURE_2_FINGER_SINGLE_TAP";
      case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP:
        return "GESTURE_2_FINGER_DOUBLE_TAP";
      case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP:
        return "GESTURE_2_FINGER_TRIPLE_TAP";
      case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP:
        return "GESTURE_3_FINGER_SINGLE_TAP";
      case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP:
        return "GESTURE_3_FINGER_DOUBLE_TAP";
      case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP:
        return "GESTURE_3_FINGER_TRIPLE_TAP";
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_UP:
        return "GESTURE_2_FINGER_SWIPE_UP";
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN:
        return "GESTURE_2_FINGER_SWIPE_DOWN";
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT:
        return "GESTURE_2_FINGER_SWIPE_LEFT";
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT:
        return "GESTURE_2_FINGER_SWIPE_RIGHT";
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_UP:
        return "GESTURE_3_FINGER_SWIPE_UP";
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN:
        return "GESTURE_3_FINGER_SWIPE_DOWN";
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT:
        return "GESTURE_3_FINGER_SWIPE_LEFT";
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT:
        return "GESTURE_3_FINGER_SWIPE_RIGHT";
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_UP:
        return "GESTURE_4_FINGER_SWIPE_UP";
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN:
        return "GESTURE_4_FINGER_SWIPE_DOWN";
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT:
        return "GESTURE_4_FINGER_SWIPE_LEFT";
      case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP:
        return "GESTURE_4_FINGER_DOUBLE_TAP";
      case AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP:
        return "GESTURE_4_FINGER_TRIPLE_TAP";
      case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD:
        return "GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD";
      case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD:
        return "GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD";
      case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD:
        return "GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD";
      case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD:
        return "GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD";
      case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD:
        return "GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD";
      case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD:
        return "GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD";
      case GestureManifold.GESTURE_FAKED_SPLIT_TYPING:
        return "GESTURE_FAKED_SPLIT_TYPING";
      case GestureManifold.GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP:
        return "GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP";
      case GestureManifold.GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP:
        return "GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP";
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
    @Nullable AccessibilityManager manager =
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
