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
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
   * Returns {@code true} if the input system window is a system bar window, for example status bar,
   * navigation bar and caption bar. And this method supports multi-display feature.
   *
   * @param context context
   * @param window the target window to check
   * @see android.view.WindowInsets.Type
   */
  public static boolean isSystemBar(
      @NonNull Context context, @NonNull AccessibilityWindowInfo window) {
    if (window.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) {
      return false;
    }
    if (!FeatureSupport.supportReportingInsetsByZOrder()) {
      return isNavigationBar(context, window) || isStatusBar(context, window);
    }

    final WindowManager windowManager;
    int displayId = AccessibilityWindowInfoUtils.getDisplayId(window);
    if (displayId == Display.DEFAULT_DISPLAY) {
      windowManager = context.getSystemService(WindowManager.class);
    } else {
      DisplayManager displayManager = context.getSystemService(DisplayManager.class);
      Display display = displayManager.getDisplay(displayId);
      final Context displayContext = context.createDisplayContext(display);
      windowManager = displayContext.getSystemService(WindowManager.class);
    }
    WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
    Rect windowBoundsExcludedSystemBars = new Rect(windowMetrics.getBounds());
    Insets windowInsets =
        windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
    windowBoundsExcludedSystemBars.inset(windowInsets);

    // The systemBar window should not intersect or overlap the non-systemBar window.
    // While on the foldable phone, the navigation bar window would have 1dp overlaps with
    // non-systemBar window due to the rounded corner.
    Rect windowBounds = new Rect();
    window.getBoundsInScreen(windowBounds);
    if (windowBounds.intersect(windowMetrics.getBounds())
        && Rect.intersects(windowBoundsExcludedSystemBars, windowBounds)) {
      return !windowBoundsExcludedSystemBars.contains(windowBounds);
    } else {
      return true;
    }
  }

  /**
   * Gets the global window insets from the window metrics.
   *
   * @param windowMetrics Metrics about a Window
   * @return windowInsets
   */
  @NonNull
  public static Insets getWindowInsets(WindowMetrics windowMetrics) {
    if (FeatureSupport.supportReportingInsetsByZOrder()) {
      return windowMetrics
          .getWindowInsets()
          .getInsetsIgnoringVisibility(
              WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
    }
    return Insets.NONE;
  }

  /**
   * Returns true if {@code resId} is the resource ID of the node on the currently active window.
   */
  public static boolean rootChildMatchesResId(AccessibilityService service, int resId) {
    AccessibilityNodeInfo root = service.getRootInActiveWindow();
    return (root != null)
        && WindowUtils.isChildNodeResId(service, AccessibilityNodeInfoUtils.getWindow(root), resId);
  }

  /** Gets the list of all displays. */
  @NonNull
  public static List<Display> getAllDisplays(Context context) {
    List<Display> displays =
        Arrays.asList(context.getSystemService(DisplayManager.class).getDisplays());
    displays.removeAll(Collections.singletonList(null));
    return displays;
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

    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo node = root.getChild(i);
      if (node == null) {
        continue;
      }

      if (TextUtils.equals(
          node.getViewIdResourceName(), context.getResources().getResourceName(resId))) {
        return true;
      }
    }
    return false;
  }

  /** Return {@code true} if {@code intA} is equal to {@code intB} roughly. */
  private static boolean isRoughEqual(int intA, int intB) {
    final int distance = 5;
    return (intA < (intB + distance)) && (intA > (intB - distance));
  }
}
