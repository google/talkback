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

package com.google.android.accessibility.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** Utility functions for system-UI windows. */
public class WindowUtils {

  private WindowUtils() {}

  /** Return whether the current screen layout is RTL. */
  public static boolean isScreenLayoutRTL(Context context) {
    Configuration config = context.getResources().getConfiguration();
    if (config == null) {
      return false;
    }
    return (config.screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK)
        == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
  }

  /**
   * Uses window's bounds to guess StatusBar on top.
   *
   * @param context context
   * @param window the target window to check
   * @return {@code true} if the window is StatusBar on top
   */
  public static boolean isStatusBar(Context context, AccessibilityWindowInfo window) {
    if (context == null || window == null) {
      return false;
    }

    android.view.WindowManager windowManager =
        (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    @Nullable Display display = (windowManager == null) ? null : windowManager.getDefaultDisplay();
    if (display == null) {
      return false;
    }

    DisplayMetrics metrics = new DisplayMetrics();
    display.getRealMetrics(metrics);
    Rect rect = new Rect();
    window.getBoundsInScreen(rect);
    return isRoughEqual(rect.top, 0)
        && isRoughEqual(rect.left, 0)
        && isRoughEqual(rect.right, metrics.widthPixels)
        && rect.bottom < (metrics.heightPixels / 5);
  }

  /**
   * Uses window's bounds to guess NavigationBar on bottom.
   *
   * @param context context
   * @param window the target window to check
   * @return {@code true} if the window is NavigationBar on bottom
   */
  public static boolean isNavigationBar(Context context, AccessibilityWindowInfo window) {
    if (context == null || window == null) {
      return false;
    }

    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    @Nullable Display display = (windowManager == null) ? null : windowManager.getDefaultDisplay();
    if (display == null) {
      return false;
    }

    DisplayMetrics metrics = new DisplayMetrics();
    display.getRealMetrics(metrics);
    Rect rect = new Rect();
    window.getBoundsInScreen(rect);
    switch (display.getRotation()) {
        // NavigationBar may located in the left side or right side in the landscape mode.
      case (Surface.ROTATION_90):
      case (Surface.ROTATION_270):
        if (isRoughEqual(rect.top, 0)
            && rect.left > ((metrics.heightPixels / 4) * 3)
            && isRoughEqual(rect.right, metrics.widthPixels)
            && isRoughEqual(rect.bottom, metrics.heightPixels)) {
          return true;
        } else if (isRoughEqual(rect.top, 0)
            && isRoughEqual(rect.left, 0)
            && rect.right < (metrics.widthPixels / 4)
            && isRoughEqual(rect.bottom, metrics.heightPixels)) {
          return true;
        }
        break;
      default:
        if (rect.top > ((metrics.heightPixels / 4) * 3)
            && isRoughEqual(rect.left, 0)
            && isRoughEqual(rect.right, metrics.widthPixels)
            && isRoughEqual(rect.bottom, metrics.heightPixels)) {
          return true;
        }
        break;
    }
    return false;
  }

  /**
   * Returns true if {@code resId} is the resource ID of the node on the currently active window.
   */
  public static boolean rootChildMatchesResId(AccessibilityService service, int resId) {
    AccessibilityNodeInfo root = service.getRootInActiveWindow();
    try {
      return (root != null)
          && WindowUtils.isChildNodeResId(
              service, AccessibilityNodeInfoUtils.getWindow(root), resId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root);
    }
  }

  /** Return true if {@code resId} is the resource ID of child node. */
  private static boolean isChildNodeResId(
      Context context, @Nullable AccessibilityWindowInfo window, int resId) {
    if (window == null) {
      return false;
    }

    AccessibilityNodeInfo root = window.getRoot();
    if (root == null) {
      return false;
    }

    AccessibilityNodeInfo node = null;
    try {
      for (int i = 0; i < root.getChildCount(); i++) {
        node = root.getChild(i);
        if (node == null) {
          continue;
        }
        boolean result =
            TextUtils.equals(
                node.getViewIdResourceName(), context.getResources().getResourceName(resId));
        AccessibilityNodeInfoUtils.recycleNodes(node);
        node = null;
        if (result) {
          return true;
        }
      }
      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node, root);
    }
  }

  /** Return {@code true} if {@code intA} is equal to {@code intB} roughly. */
  private static boolean isRoughEqual(int intA, int intB) {
    final int distance = 5;
    return (intA < (intB + distance)) && (intA > (intB - distance));
  }
}
