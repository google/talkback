/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/** View display related utility methods. */
public final class DisplayUtils {
  private DisplayUtils() {}

  /**
   * Converts DP to Pixel.
   *
   * @param context Context instance
   * @param dp DP value
   * @return equivalent size in pixel
   */
  public static int dpToPx(Context context, int dp) {
    DisplayMetrics dm = context.getResources().getDisplayMetrics();
    return Math.round(dp * dm.density);
  }

  /** Returns screen pixel size excludes navigation bar and status bar area. */
  public static Point getScreenPixelSizeWithoutWindowDecor(Context context) {
    Configuration configuration = context.getResources().getConfiguration();
    return new Point(
        dpToPx(context, configuration.screenWidthDp),
        dpToPx(context, configuration.screenHeightDp));
  }

  /** Returns status bar height in pixel. */
  // TODO: We need to better way to get status bar height.
  public static int getStatusBarHeightInPixel(Context context) {
    int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
    int statusBarHeight = 0;
    if (resourceId > 0) {
      statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
    }

    return statusBarHeight;
  }

  /**
   * Returns context with default screen densityDpi.This context is used to keep the layout size as
   * per the default screen densityDpi and not based on the display size setting changed by the
   * user.
   */
  public static Context getDefaultScreenDensityContext(Context context) {
    Resources res = context.getResources();
    Configuration configuration = new Configuration(res.getConfiguration());

    /* get default display density */
    if (BuildVersionUtils.isAtLeastN()) {
      configuration.densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE;
    } else {
      WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      DisplayMetrics dm = new DisplayMetrics();
      wm.getDefaultDisplay().getRealMetrics(dm);
      configuration.densityDpi = dm.densityDpi;
    }
    configuration.setTo(configuration);

    return context.createConfigurationContext(configuration);
  }
}
