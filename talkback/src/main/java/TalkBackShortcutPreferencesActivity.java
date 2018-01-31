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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import com.google.android.accessibility.utils.ArrayUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.HardwareUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Activity used to set TalkBack's gesture preferences. */
public class TalkBackShortcutPreferencesActivity extends Activity {

  private static final int[] HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH = {
    R.string.pref_shortcut_right_and_down_key,
    R.string.pref_shortcut_right_and_up_key,
    R.string.pref_shortcut_left_and_down_key,
    R.string.pref_category_side_tap_shortcuts_key
  };

  private ShortcutPrefFragment mPrefFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setTitle(getString(R.string.title_pref_category_manage_gestures));

    ActionBar actionBar = getActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    // Create user-interface for currently selected verbosity preset.
    mPrefFragment = new ShortcutPrefFragment();
    getFragmentManager().beginTransaction().replace(android.R.id.content, mPrefFragment).commit();
  }

  /** If action-bar "navigate up" button is pressed, end this sub-activity. */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  public Preference findPreference(String key) {
    return mPrefFragment.findPreference(key);
  }

  /** Panel holding a set of shortcut preferences. Recreated when preset value changes. */
  public static class ShortcutPrefFragment extends PreferenceFragment {
    /** Preferences managed by this activity. */
    private SharedPreferences mPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      Context context = getActivity().getApplicationContext();

      // Set preferences to use device-protected storage.
      if (BuildVersionUtils.isAtLeastN()) {
        getPreferenceManager().setStorageDeviceProtected();
      }
      mPrefs = SharedPreferencesUtils.getSharedPreferences(context);

      addPreferencesFromResource(R.xml.gesture_preferences);

      final boolean treeDebugEnabled =
          mPrefs.getBoolean(getString(R.string.pref_tree_debug_key), false);
      final boolean performanceStatsEnabled =
          mPrefs.getBoolean(getString(R.string.pref_performance_stats_key), false);
      final boolean selectorEnabled =
          mPrefs.getBoolean(getString(R.string.pref_selector_activation_key), false);

      final String[] shortcutEntries =
          readShortcutEntries(selectorEnabled, treeDebugEnabled, performanceStatsEnabled);
      final String[] shortcutEntryValues =
          readShortcutEntryValues(selectorEnabled, treeDebugEnabled, performanceStatsEnabled);

      // Reference to the string resources used as keys customizable gesture mapping preferences.
      final String[] gesturePrefKeys = getResources().getStringArray(R.array.pref_shortcut_keys);

      // Update list preference entries and entryValues.
      for (int i = 0; i < gesturePrefKeys.length; i++) {
        ListPreference listPreference = (ListPreference) findPreference(gesturePrefKeys[i]);
        listPreference.setEntries(shortcutEntries);
        listPreference.setEntryValues(shortcutEntryValues);
        listPreference.setOnPreferenceChangeListener(mPreferenceChangeListener);
      }

      // Hide fingerprint gesture setting if it's not supported.
      if (!BuildVersionUtils.isAtLeastO() || !HardwareUtils.isFingerprintSupported(context)) {
        PreferenceSettingsUtils.hidePreference(
            context, getPreferenceScreen(), R.string.pref_category_fingerprint_touch_shortcuts_key);
      }

      if (FormFactorUtils.getInstance(context).isWatch()) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH);
      }
    }

    private final OnPreferenceChangeListener mPreferenceChangeListener =
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
                if (mPrefs.contains(selectorShortcutSavedKey)) {
                  mPrefs.edit().remove(selectorShortcutSavedKey).apply();
                }
                return true;
              }

              // If the gesture key does not yet exist in preferences, do nothing with old value.
              if (!mPrefs.contains(listPreference.getKey())) {
                return true;
              }

              // Get the old value for this preference. Since we check above if the preference with
              // this key exists and return if it doesn't, this line will never return the null
              // default value.
              final String oldValue = mPrefs.getString(listPreference.getKey(), null);

              // If the old value is a selector action, do not save the old value.
              for (String selectorShortcutValue :
                  getResources().getStringArray(R.array.selector_shortcut_values)) {
                if (selectorShortcutValue.equals(oldValue)) {
                  return true;
                }
              }

              // Save the old value.
              mPrefs
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
        boolean selectorEnabled, boolean treeDebugEnabled, boolean performanceStatsEnabled) {
      String[] entries = getResources().getStringArray(R.array.pref_shortcut_entries);

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

      return entries;
    }

    /** Read shortcut entry values from SharedPreference. */
    private String[] readShortcutEntryValues(
        boolean selectorEnabled, boolean treeDebugEnabled, boolean performanceStatsEnabled) {
      String[] entryValues = getResources().getStringArray(R.array.pref_shortcut_values);

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

      return entryValues;
    }
  }
}
