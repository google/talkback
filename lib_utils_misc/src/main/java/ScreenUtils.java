/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.libraries.accessibility.utils.device;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.SideEffectFree;

/** Utility class for getting the information about the screen configurations. */
public class ScreenUtils {
  /**
   * Returns the dimensions of the display in pixels. Does nothing if the default display is null.
   *
   * @param context The context that will be used to retrieve system information
   */
  public static Point getScreenSize(Context context) {
    Point screenSize = new Point();
    Display display = getDisplay(context);
    if (display != null) {
      display.getSize(screenSize);
    }
    return screenSize;
  }

  /**
   * Returns the real dimensions of the display in pixels without subtracting any window decor. Does
   * nothing if the default display is null.
   *
   * @param context The context that will be used to retrieve system information
   */
  @SideEffectFree
  public static Point getRealScreenSize(Context context) {
    Point screenSize = new Point();
    Display display = getDisplay(context);
    if (display != null) {
      display.getRealSize(screenSize);
    }
    return screenSize;
  }

  /**
   * Returns the height of the system UI elements, including the status bar and navigation bar.
   *
   * @param context The context that will be used to retrieve system information
   * @return The height of the status bar and navigation bar
   */
  public static int getSystemUiHeight(Context context) {
    Display display = getDisplay(context);
    if (display == null) {
      return 0;
    }

    // DisplayMetrics adjusts metrics using current rotation. This results in system UI height
    // (i.e. the status bar's height) returned as the metrics' width when in landscape orientation.
    boolean isLandscapeOrientation =
        (context.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE);

    final DisplayMetrics metrics = new DisplayMetrics();
    display.getMetrics(metrics);
    final int availableHeight = isLandscapeOrientation ? metrics.widthPixels : metrics.heightPixels;

    getRealMetricsForDisplay(display, metrics);
    final int totalHeight = isLandscapeOrientation ? metrics.widthPixels : metrics.heightPixels;
    return (totalHeight - availableHeight);
  }

  /**
   * Returns the display metrics based on the real size of the display.
   *
   * @param context The context that will be used to retrieve system information
   * @return The display metrics based on the real size of the display. The DisplayMetrics will be
   *     empty if the display is null
   */
  public static DisplayMetrics getRealDisplayMetrics(Context context) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getRealMetricsForDisplay(getDisplay(context), displayMetrics);
    return displayMetrics;
  }

  private static void getRealMetricsForDisplay(
      @Nullable Display display, DisplayMetrics displayMetrics) {
    if (display != null) {
      display.getRealMetrics(displayMetrics);
    }
  }

  @Nullable
  private static Display getDisplay(Context context) {
    final WindowManager windowManager =
        (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    return (windowManager == null) ? null : windowManager.getDefaultDisplay();
  }
}
