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
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardConfigureSwitchFragment.Action;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.KeyFor;

/**
 * Provides setup wizard text that depends on the {@link SetupWizardConfigureSwitchFragment.Action}
 * that is currently being configured as well as color setting information that is relevant within
 * the Setup Wizard.
 */
public class SwitchActionInformationUtils {

  private static final String TAG = SwitchActionInformationUtils.class.getSimpleName();

  /* Map of actions to heading and subheading resource ids. */
  private static final Map<Action, Integer> KEY_PREFERENCE_KEYS;
  private static final Map<Action, Integer> SWITCH_NAME_KEYS;

  static {
    KEY_PREFERENCE_KEYS = new HashMap<>();
    KEY_PREFERENCE_KEYS.put(Action.AUTO_SCAN, R.string.pref_key_mapped_to_auto_scan_key);
    KEY_PREFERENCE_KEYS.put(Action.SELECT, R.string.pref_key_mapped_to_click_key);
    KEY_PREFERENCE_KEYS.put(Action.NEXT, R.string.pref_key_mapped_to_next_key);
    KEY_PREFERENCE_KEYS.put(Action.GROUP_ONE, R.string.pref_key_mapped_to_click_key);
    KEY_PREFERENCE_KEYS.put(Action.GROUP_TWO, R.string.pref_key_mapped_to_next_key);

    SWITCH_NAME_KEYS = new HashMap<>();
    SWITCH_NAME_KEYS.put(Action.AUTO_SCAN, R.string.title_pref_category_auto_scan);
    SWITCH_NAME_KEYS.put(Action.SELECT, R.string.action_name_click);
    SWITCH_NAME_KEYS.put(Action.NEXT, R.string.action_name_next);
    SWITCH_NAME_KEYS.put(Action.GROUP_ONE, R.string.option_scan_switch_title);
    SWITCH_NAME_KEYS.put(Action.GROUP_TWO, R.string.option_scan_switch_title);
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
  public static String getActionName(
      Context context, @KeyFor("KEY_PREFERENCE_KEYS") Action action) {
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
    return context.getString(R.string.title_assign_scan_key, getSwitchName(context, action));
  }

  /**
   * Gets subheading text for the key configuration page.
   *
   * @param context Current application context
   * @param action Action for which the subheading should be retrieved
   * @return subheading text formatted for the current action
   */
  public static String getSubheading(Context context, @KeyFor("SWITCH_NAME_KEYS") Action action) {
    if (action == Action.GROUP_ONE) {
      String groupOneColorString =
          getColorStringFromSharedPreferences(
              R.string.pref_highlight_0_color_key,
              R.string.pref_highlight_0_color_default,
              context);
      return context.getString(R.string.option_scan_switch_subtitle, groupOneColorString);
    } else if (action == Action.GROUP_TWO) {
      String groupTwoColorString =
          getColorStringFromSharedPreferences(
              R.string.pref_highlight_1_color_key,
              R.string.pref_highlight_1_color_default,
              context);
      return context.getString(R.string.option_scan_switch_subtitle, groupTwoColorString);
    } else if (action == Action.AUTO_SCAN) {
      return context.getString(
          R.string.assign_switch_subtitle,
          context.getString(R.string.auto_scan_action_description));
    } else {
      return context.getString(
          R.string.assign_switch_subtitle, context.getString(SWITCH_NAME_KEYS.get(action)));
    }
  }

  /**
   * Gets the name for the switches assigned to a given action. For example, the name for the
   * switches assigned to the select action is "Select".
   *
   * @param context Current application context
   * @param action Action for which the switches are assigned to
   * @return name for the assigned switches
   */
  public static String getSwitchName(Context context, @KeyFor("SWITCH_NAME_KEYS") Action action) {
    if (action == Action.GROUP_ONE) {
      String groupOneColorString =
          getColorStringFromGroupSelectionSwitchNumber(context, 0 /* switchNumber */);
      return context.getString(SWITCH_NAME_KEYS.get(action), groupOneColorString);
    } else if (action == Action.GROUP_TWO) {
      String groupTwoColorString =
          getColorStringFromGroupSelectionSwitchNumber(context, 1 /* switchNumber */);
      return context.getString(SWITCH_NAME_KEYS.get(action), groupTwoColorString);
    } else {
      return context.getString(SWITCH_NAME_KEYS.get(action));
    }
  }

  /**
   * Returns the color string of the group selection group highlight color given the group selection
   * switch number.
   *
   * @param context Current application context
   * @param switchNumber The group selection switch number for which to get the color string
   * @return String representing the proper color name
   */
  public static String getColorStringFromGroupSelectionSwitchNumber(
      Context context, int switchNumber) {
    String preferenceKey =
        context.getResources()
            .getStringArray(R.array.switch_access_highlight_color_pref_keys)[switchNumber];
    String preferenceDefault =
        context.getResources()
            .getStringArray(R.array.switch_access_highlight_color_defaults)[switchNumber];

    return getColorStringFromSharedPreferences(preferenceKey, preferenceDefault, context);
  }

  /**
   * Returns the color string of the highlight color given the key of the highlight color
   * preference.
   *
   * @param preferenceKey Key of the highlight color preference
   * @param preferenceDefault Default value of the preference to be found
   * @param context Current application context
   * @return String representing the proper color name
   */
  public static String getColorStringFromSharedPreferences(
      int preferenceKey, int preferenceDefault, Context context) {
    return getColorStringFromSharedPreferences(
        context.getString(preferenceKey), context.getString(preferenceDefault), context);
  }

  private static String getColorStringFromSharedPreferences(
      String preferenceKey, String preferenceDefault, Context context) {
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    String hexString = sharedPreferences.getString(preferenceKey, preferenceDefault);

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
    LogUtils.e(TAG, "Hex value could not be matched to a string");
    return "";
  }
}
