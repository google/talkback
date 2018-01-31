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

package com.google.android.accessibility.switchaccess.utils;

import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/** Utility class for getting the information about the screen configurations. */
public class ScreenUtils {
  /**
   * Returns the dimensions of the display in pixels.
   *
   * @param context The context that will be used to retrieve system information
   * @param screenSize The {@link Point} used to store the dimensions of the display
   */
  public static void getScreenSize(Context context, Point screenSize) {
    getDisplay(context).getSize(screenSize);
  }

  /**
   * Returns the height of the status bar.
   *
   * @param context The context that will be used to retrieve system information
   * @return The height of the status bar
   */
  public static int getStatusBarHeight(Context context) {
    Display display = getDisplay(context);

    final DisplayMetrics metrics = new DisplayMetrics();
    display.getMetrics(metrics);
    final int availableHeight = metrics.heightPixels;

    display.getRealMetrics(metrics);
    final int totalHeight = metrics.heightPixels;
    return (totalHeight - availableHeight);
  }

  private static Display getDisplay(Context context) {
    final WindowManager windowManager =
        (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    return windowManager.getDefaultDisplay();
  }
}
