/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.talkback.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import androidx.annotation.ColorInt;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;

/** Utils function for updating focus indicator stroke width and color. */
public class FocusIndicatorUtils {

  /**
   * Applies the focus appearance preference. It would set the preferences via {@link
   * AccessibilityService}
   *
   * @param service The parent service.
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   */
  public static void applyFocusAppearancePreference(
      AccessibilityService service, SharedPreferences prefs, Resources res) {
    if (FeatureSupport.supportCustomizingFocusIndicator()) {
      int borderWidth = getTalkBackFocusStrokeWidth(prefs, res);
      int borderColor = getTalkBackFocusColor(service, prefs, res);
      setAccessibilityFocusAppearance(service, borderWidth, borderColor);
    }
  }

  /**
   * Updates the focus indicator stroke width and color.
   *
   * @param service The parent service.
   * @param borderWidth the border width
   * @param borderColor the color value
   */
  public static void setAccessibilityFocusAppearance(
      AccessibilityService service, int borderWidth, int borderColor) {
    service.setAccessibilityFocusAppearance(borderWidth, borderColor);
  }

  /**
   * Gets the stroke width of TalkBack focus indicator settings.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   */
  public static int getTalkBackFocusStrokeWidth(SharedPreferences prefs, Resources res) {
    boolean isThickBorder = prefs.getBoolean(res.getString(R.string.pref_thick_border_key), false);
    int borderWidth =
        isThickBorder
            ? res.getDimensionPixelSize(R.dimen.accessibility_thick_focus_highlight_stroke_width)
            : res.getDimensionPixelSize(R.dimen.accessibility_focus_highlight_stroke_width);
    return borderWidth;
  }

  /**
   * Gets the color value of TalkBack focus indicator settings.
   *
   * @param context The context.
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   */
  @ColorInt
  public static int getTalkBackFocusColor(Context context, SharedPreferences prefs, Resources res) {
    int borderColor =
        prefs.getInt(
            res.getString(R.string.pref_border_color_key),
            res.getColor(R.color.accessibility_focus_highlight_color, null));
    return borderColor;
  }
}
