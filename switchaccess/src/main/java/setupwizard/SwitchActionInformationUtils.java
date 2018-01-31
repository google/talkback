/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.setupwizard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardConfigureSwitch.Action;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides setup wizard text that depends on the {@link SetupWizardConfigureSwitch.Action} that is
 * currently being configured as well as color setting information that is relevant within the Setup
 * Wizard.
 */
public class SwitchActionInformationUtils {

  private static final String TAG = SwitchActionInformationUtils.class.getSimpleName();

  /* Map of actions to heading and subheading resource ids. */
  private static final Map<Action, Integer> KEY_PREFERENCE_KEYS;
  private static final Map<Action, Integer> HEADING_KEYS;

  static {
    KEY_PREFERENCE_KEYS = new HashMap<>();
    KEY_PREFERENCE_KEYS.put(Action.AUTO_SCAN, R.string.pref_key_mapped_to_auto_scan_key);
    KEY_PREFERENCE_KEYS.put(Action.SELECT, R.string.pref_key_mapped_to_click_key);
    KEY_PREFERENCE_KEYS.put(Action.NEXT, R.string.pref_key_mapped_to_next_key);
    KEY_PREFERENCE_KEYS.put(Action.OPTION_ONE, R.string.pref_key_mapped_to_click_key);
    KEY_PREFERENCE_KEYS.put(Action.OPTION_TWO, R.string.pref_key_mapped_to_next_key);

    HEADING_KEYS = new HashMap<>();
    HEADING_KEYS.put(Action.AUTO_SCAN, R.string.title_pref_category_auto_scan);
    HEADING_KEYS.put(Action.SELECT, R.string.action_name_click);
    HEADING_KEYS.put(Action.NEXT, R.string.action_name_next);
    HEADING_KEYS.put(Action.OPTION_ONE, R.string.option_scan_switch_title);
    HEADING_KEYS.put(Action.OPTION_TWO, R.string.option_scan_switch_title);
  }

  private SwitchActionInformationUtils() {
    /* This class should not be instantiated */
  }

  /**
   * Gets the name of the action being configured.
   *
   * @param context Current application context
   * @param action Action for which the name should be retrieved
   * @return string name of the action
   */
  public static String getActionName(Context context, Action action) {
    return context.getString(KEY_PREFERENCE_KEYS.get(action));
  }

  /**
   * Gets heading text for key configuration page.
   *
   * @param context Current application context
   * @param action Action for which the heading should be retrieved
   * @return heading text formatted for the current action
   */
  public static String getHeading(Context context, Action action) {
    if (action == Action.OPTION_ONE) {
      String optionOneColorString =
          hexToColorString(
              getHexFromSharedPreferences(
                  R.string.pref_highlight_0_color_key,
                  R.string.pref_highlight_0_color_default,
                  context),
              context);
      return context.getString(HEADING_KEYS.get(action), optionOneColorString);
    } else if (action == Action.OPTION_TWO) {
      String optionTwoColorString =
          hexToColorString(
              getHexFromSharedPreferences(
                  R.string.pref_highlight_1_color_key,
                  R.string.pref_highlight_1_color_default,
                  context),
              context);
      return context.getString(HEADING_KEYS.get(action), optionTwoColorString);
    } else {
      return context.getString(HEADING_KEYS.get(action));
    }
  }

  /**
   * Gets subheading text for the key configuration page.
   *
   * @param context Current application context
   * @param action Action for which the subheading should be retrieved
   * @return subheading text formatted for the current action
   */
  public static String getSubheading(Context context, Action action) {
    if (action == Action.OPTION_ONE) {
      String optionOneColorString =
          hexToColorString(
              getHexFromSharedPreferences(
                  R.string.pref_highlight_0_color_key,
                  R.string.pref_highlight_0_color_default,
                  context),
              context);
      return context.getString(R.string.option_scan_switch_subtitle, optionOneColorString);
    } else if (action == Action.OPTION_TWO) {
      String optionTwoColorString =
          hexToColorString(
              getHexFromSharedPreferences(
                  R.string.pref_highlight_1_color_key,
                  R.string.pref_highlight_1_color_default,
                  context),
              context);
      return context.getString(R.string.option_scan_switch_subtitle, optionTwoColorString);
    } else if (action == Action.AUTO_SCAN) {
      return context.getString(
          R.string.assign_switch_subtitle,
          context.getString(R.string.auto_scan_action_description));
    } else {
      return context.getString(
          R.string.assign_switch_subtitle, context.getString(HEADING_KEYS.get(action)));
    }
  }

  /**
   * Finds the hex value of a preference highlight color from shared preferences.
   *
   * @param preferenceKey Key of the highlight color preference
   * @param preferenceDefault Default value of the preference to be found
   * @param context Current application context
   * @return string representing the hex value of the color
   */
  public static String getHexFromSharedPreferences(
      int preferenceKey, int preferenceDefault, Context context) {
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    return sharedPreferences.getString(
        context.getString(preferenceKey), context.getString(preferenceDefault));
  }

  /**
   * Converts the hex color value to a proper color string using prepared resource arrays.
   *
   * @param hexString The string value of the hex representation of the color
   * @param context The current application context
   * @return string representing the proper color name
   */
  public static String hexToColorString(String hexString, Context context) {
    String[] preferenceColorHex =
        context.getResources().getStringArray(R.array.switch_access_color_values);
    String[] preferenceColorName =
        context.getResources().getStringArray(R.array.switch_access_color_entries);
    for (int i = 0; i < preferenceColorHex.length; i++) {
      if (hexString.equals(preferenceColorHex[i])) {
        return preferenceColorName[i];
      }
    }
    /* This should never be reached */
    Log.e(TAG, "Hex value could not be matched to a string");
    return "";
  }
}
