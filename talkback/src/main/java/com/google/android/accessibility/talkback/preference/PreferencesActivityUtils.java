/*
 * Copyright (C) 2020 Google Inc.
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
package com.google.android.accessibility.talkback.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Utility class for Preferences and Activity */
public class PreferencesActivityUtils {

  public static final String HELP_URL =
      "https://support.google.com/accessibility/" + "android/answer/6283677";

  private PreferencesActivityUtils() {} // Not instantiable

  /** Returns a boolean-preference-value that may be overridden by diagnosis-mode. */
  public static boolean getDiagnosticPref(Context context, int prefKeyResId, int prefDefaultResId) {
    SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
    Resources resources = context.getResources();
    return getDiagnosticPref(preferences, resources, prefKeyResId, prefDefaultResId);
  }

  public static boolean getDiagnosticPref(
      SharedPreferences preferences, Resources resources, int prefKeyResId, int prefDefaultResId) {
    return isDiagnosisModeOn(preferences, resources)
        || SharedPreferencesUtils.getBooleanPref(
            preferences, resources, prefKeyResId, prefDefaultResId);
  }

  public static boolean isDiagnosisModeOn(SharedPreferences preferences, Resources resources) {
    return SharedPreferencesUtils.getBooleanPref(
        preferences,
        resources,
        R.string.pref_diagnosis_mode_key,
        R.bool.pref_diagnosis_mode_default);
  }

  /**
   * Notification ID for the Gesture Change notification. This is also used in the
   * GestureChangeNotificationActivity to dismiss the notification.
   */
  public static final int GESTURE_CHANGE_NOTIFICATION_ID = 2;

  /**
   * Utility method for announcing text via accessibility event.
   *
   * @param text The text to announce.
   * @param context The context used to resolve string resources.
   */
  public static void announceText(String text, Context context) {
    AccessibilityManager accessibilityManager =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (accessibilityManager.isEnabled()) {
      AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
      event.setContentDescription(text);
      accessibilityManager.sendAccessibilityEvent(event);
    }
  }

  /** Provide preferences a way to change their summary dynamically. */
  public static void setSummary(
      Context context,
      PreferenceManager preferenceManager,
      @StringRes int preferenceKeyResId,
      @StringRes int summaryResId) {
    if (preferenceManager == null) {
      return;
    }
    @Nullable
    Preference preference = preferenceManager.findPreference(context.getString(preferenceKeyResId));
    if (preference == null) {
      return;
    }
    preference.setSummary(summaryResId);
  }

  /**
   * Combines the key value of custom action key and editing key, then removes editing key.
   *
   * @param context The context used to resolve string resources.
   */
  public static void removeEditingKey(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    final SharedPreferences.Editor prefEditor = prefs.edit();
    String editingKey = context.getString(R.string.pref_show_context_menu_editing_setting_key);

    if (prefs.contains(editingKey)) {
      boolean editingState =
          SharedPreferencesUtils.getBooleanPref(
              prefs,
              context.getResources(),
              R.string.pref_show_context_menu_editing_setting_key,
              R.bool.pref_show_context_menu_editing_default);
      // Changes custom action key value to true when editingState is true
      if (editingState) {
        SharedPreferencesUtils.putBooleanPref(
            prefs,
            context.getResources(),
            R.string.pref_show_context_menu_custom_action_setting_key,
            editingState);
      }
      prefEditor.remove(editingKey);
      prefEditor.apply();
    }
  }
}
