/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.SharedPreferences;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Utility functions for verbosity preferences. Verbosity preferences should be read through
 * getPreferenceValue() to use preference preset rules.
 */
public class VerbosityPreferences {

  private static final String TAG = "VerbosityPreferences";

  private VerbosityPreferences() {} // Do not instantiate.

  public static boolean getPreferenceValueBool(
      SharedPreferences preferences, Resources resources, String key, boolean defaultValue) {

    // If no preset selected... use old preference. Otherwise use preset rule or custom value.
    String presetValue =
        SharedPreferencesUtils.getStringPref(
            preferences, resources, R.string.pref_verbosity_preset_key, 0); // Default to null.
    if (presetValue == null) {
      return preferences.getBoolean(key, defaultValue);
    }
    return getPreferencePresetBool(preferences, resources, presetValue, key, defaultValue);
  }

  public static String getPreferenceValueString(
      SharedPreferences preferences, Resources resources, String key, String defaultValue) {

    // If no preset selected... use old preference. Otherwise use preset rule or custom value.
    String presetValue =
        SharedPreferencesUtils.getStringPref(
            preferences, resources, R.string.pref_verbosity_preset_key, 0); // Default to null.
    if (presetValue == null) {
      return preferences.getString(key, defaultValue);
    }
    return getPreferencePresetString(preferences, resources, presetValue, key, defaultValue);
  }

  public static boolean getPreferencePresetBool(
      SharedPreferences preferences,
      Resources resources,
      @NonNull String presetValue,
      String key,
      boolean defaultValue) {
    if (presetValue.equals(resources.getString(R.string.pref_verbosity_preset_value_high))) {
      return true;
    } else if (presetValue.equals(resources.getString(R.string.pref_verbosity_preset_value_low))) {
      return false;
    } else {
      String keyForPreset = toPresetPrefKey(presetValue, key);
      return preferences.getBoolean(keyForPreset, defaultValue);
    }
  }

  public static String getPreferencePresetString(
      SharedPreferences preferences,
      Resources resources,
      @NonNull String presetValue,
      String key,
      String defaultValue) {
    // If verbosity is high... use rule to select list preference value.
    if (presetValue.equals(resources.getString(R.string.pref_verbosity_preset_value_high))) {
      if (key.equals(resources.getString(R.string.pref_keyboard_echo_key))) {
        String[] keyboardEchoValues = resources.getStringArray(R.array.pref_keyboard_echo_values);
        return (keyboardEchoValues == null || keyboardEchoValues.length == 0)
            ? null
            : keyboardEchoValues[0];
      } else {
        LogUtils.e(TAG, "Unhandled key \"%s\"", key);
        return null;
      }
      // If verbosity is low... use rule to select list preference value.
    } else if (presetValue.equals(resources.getString(R.string.pref_verbosity_preset_value_low))) {
      if (key.equals(resources.getString(R.string.pref_keyboard_echo_key))) {
        String[] keyboardEchoValues = resources.getStringArray(R.array.pref_keyboard_echo_values);
        return (keyboardEchoValues == null || keyboardEchoValues.length == 0)
            ? null
            : keyboardEchoValues[keyboardEchoValues.length - 1];
      } else {
        LogUtils.e(TAG, "Unhandled key \"%s\"", key);
        return null;
      }
      // If verbosity is custom... retrieve preference value.
    } else {
      String keyForPreset = toPresetPrefKey(presetValue, key);
      return preferences.getString(keyForPreset, defaultValue);
    }
  }

  public static String toPresetPrefKey(String presetName, String preferenceKey) {
    return presetName + "_" + preferenceKey;
  }
}
