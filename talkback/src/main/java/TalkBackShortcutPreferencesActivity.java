/*
 * Copyright 2010 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.utils.ArrayUtils;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Activity used to set TalkBack's gesture preferences. */
public class TalkBackShortcutPreferencesActivity extends BasePreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new ShortcutPrefFragment();
  }

  /** Panel holding a set of shortcut preferences. Recreated when preset value changes. */
  public static class ShortcutPrefFragment extends PreferenceFragmentCompat {
    /** Preferences managed by this activity. */
    private SharedPreferences prefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

      Context context = getActivity().getApplicationContext();

      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.gesture_preferences);

      prefs = SharedPreferencesUtils.getSharedPreferences(context);

      final boolean treeDebugEnabled =
          prefs.getBoolean(getString(R.string.pref_tree_debug_key), false);
      final boolean performanceStatsEnabled =
          prefs.getBoolean(getString(R.string.pref_performance_stats_key), false);
      final boolean selectorEnabled =
          prefs.getBoolean(getString(R.string.pref_selector_activation_key), false);
      final boolean isWatch = FeatureSupport.isWatch(context);

      // Manipulate shortcutEntries and shortcutEntryValues to alter the list of assignable actions
      // to gestures.
      final String[] shortcutEntries =
          readShortcutEntries(selectorEnabled, treeDebugEnabled, performanceStatsEnabled, isWatch);
      final String[] shortcutEntryValues =
          readShortcutEntryValues(
              selectorEnabled, treeDebugEnabled, performanceStatsEnabled, isWatch);

      // Reference to the string resources used as keys customizable gesture mapping preferences.
      final String[] gesturePrefKeys = getResources().getStringArray(R.array.pref_shortcut_keys);

      // Update list preference entries and entryValues.
      for (int i = 0; i < gesturePrefKeys.length; i++) {
        ListPreference listPreference = (ListPreference) findPreference(gesturePrefKeys[i]);
        listPreference.setEntries(shortcutEntries);
        listPreference.setEntryValues(shortcutEntryValues);
        listPreference.setOnPreferenceChangeListener(preferenceChangeListener);
      }

      // Hide fingerprint gesture setting if it's not supported.
      if (!BuildVersionUtils.isAtLeastO() || !FeatureSupport.isFingerprintSupported(context)) {
        PreferenceSettingsUtils.hidePreference(
            context, getPreferenceScreen(), R.string.pref_category_fingerprint_touch_shortcuts_key);
      }
    }

    private final OnPreferenceChangeListener preferenceChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference instanceof ListPreference && newValue instanceof String) {
              final ListPreference listPreference = (ListPreference) preference;

              String notSelectorSavedKeySuffix =
                  getString(R.string.pref_not_selector_saved_gesture_suffix);
              String selectorSavedKeySuffix =
                  getString(R.string.pref_selector_saved_gesture_suffix);
              String selectorShortcutSavedKey = listPreference.getKey() + selectorSavedKeySuffix;

              // If new value is not a selector action, do not save the old value.
              if (!isSelectorAction((String) newValue)) {
                // If the saved selector preference exists for this key, we can now remove it, as
                // the value has changed to a non-selector action.
                if (prefs.contains(selectorShortcutSavedKey)) {
                  prefs.edit().remove(selectorShortcutSavedKey).apply();
                }
                return true;
              }

              // If the gesture key does not yet exist in preferences, do nothing with old value.
              if (!prefs.contains(listPreference.getKey())) {
                return true;
              }

              // Get the old value for this preference. Since we check above if the preference with
              // this key exists and return if it doesn't, this line will never return the null
              // default value.
              final String oldValue = prefs.getString(listPreference.getKey(), null);

              // If the old value is a selector action, do not save the old value.
              for (String selectorShortcutValue :
                  getResources().getStringArray(R.array.selector_shortcut_values)) {
                if (selectorShortcutValue.equals(oldValue)) {
                  return true;
                }
              }

              // Save the old value.
              prefs
                  .edit()
                  .putString(listPreference.getKey() + notSelectorSavedKeySuffix, oldValue)
                  .apply();
            }
            return true;
          }
        };

    private boolean isSelectorAction(String newValue) {
      boolean isSelectorAction = false;
      for (String selectorShortcutValue :
          getResources().getStringArray(R.array.selector_shortcut_values)) {
        if (selectorShortcutValue.equals(newValue)) {
          isSelectorAction = true;
        }
      }
      return isSelectorAction;
    }

    /** Read shortcut entries from SharedPreference. */
    private String[] readShortcutEntries(
        boolean selectorEnabled,
        boolean treeDebugEnabled,
        boolean performanceStatsEnabled,
        boolean isWatch) {
      String[] entries = getResources().getStringArray(R.array.pref_shortcut_entries);

      if (TalkBackService.ENABLE_VOICE_COMMANDS) {
        entries = ArrayUtils.concat(entries, getString(R.string.shortcut_voice_commands));
      }
      if (selectorEnabled) {
        for (String selectorShortcut : getResources().getStringArray(R.array.selector_shortcuts)) {
          entries = ArrayUtils.concat(entries, selectorShortcut);
        }
      }
      if (treeDebugEnabled) {
        entries = ArrayUtils.concat(entries, getString(R.string.shortcut_print_node_tree));
      }
      if (performanceStatsEnabled) {
        entries = ArrayUtils.concat(entries, getString(R.string.shortcut_print_performance_stats));
      }
      // Screen search is not supported on watches.
      if (!isWatch) {
        entries = ArrayUtils.concat(entries, getString(R.string.shortcut_perform_screen_search));
      }
      return entries;
    }

    /** Read shortcut entry values from SharedPreference. */
    private String[] readShortcutEntryValues(
        boolean selectorEnabled,
        boolean treeDebugEnabled,
        boolean performanceStatsEnabled,
        boolean isWatch) {
      String[] entryValues = getResources().getStringArray(R.array.pref_shortcut_values);

      if (TalkBackService.ENABLE_VOICE_COMMANDS) {
        entryValues =
            ArrayUtils.concat(entryValues, getString(R.string.shortcut_value_voice_commands));
      }
      if (selectorEnabled) {
        for (String selectorShortcutValue :
            getResources().getStringArray(R.array.selector_shortcut_values)) {
          entryValues = ArrayUtils.concat(entryValues, selectorShortcutValue);
        }
      }
      if (treeDebugEnabled) {
        entryValues =
            ArrayUtils.concat(entryValues, getString(R.string.shortcut_value_print_node_tree));
      }
      if (performanceStatsEnabled) {
        entryValues =
            ArrayUtils.concat(
                entryValues, getString(R.string.shortcut_value_print_performance_stats));
      }
      // Screen search is not supported on watches.
      if (!isWatch) {
        entryValues =
            ArrayUtils.concat(entryValues, getString(R.string.shortcut_value_screen_search));
      }
      return entryValues;
    }
  }
}

