/*
 * Copyright 2018 Google Inc.
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
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Activity used to set TalkBack's selector preferences. */
public class TalkBackSelectorPreferencesActivity extends BasePreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new SelectorPrefFragment();
  }

  /** Panel holding a set of selector preferences. */
  public static class SelectorPrefFragment extends PreferenceFragmentCompat {

    /** Preferences managed by this activity. */
    private SharedPreferences prefs;

    private Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      context = getActivity().getApplicationContext();
      prefs = SharedPreferencesUtils.getSharedPreferences(context);

      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.selector_preferences);

      final TwoStatePreference selectorActivation =
          (TwoStatePreference) findPreference(getString(R.string.pref_selector_activation_key));
      if (selectorActivation != null) {
        selectorActivation.setOnPreferenceChangeListener(selectorActivationChangeListener);
        enableOrDisableSelectorSettings(selectorActivation.isChecked());
      }
    }

    private final OnPreferenceChangeListener selectorActivationChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (Boolean.TRUE.equals(newValue)) {
              // Assign selector shortcuts.
              assignSelectorShortcuts();
              enableOrDisableSelectorSettings(true); // Enable preferences for settings.
            } else {
              // Remove all selector assignments and restore pre-selector assignments.
              removeSelectorShortcuts();
              enableOrDisableSelectorSettings(false);
            }
            return true;
          }
        };

    /**
     * Enables or disables the setting configuration preference category, depending on the on/off
     * state of the selector.
     */
    private void enableOrDisableSelectorSettings(boolean enable) {
      PreferenceCategory settingsCategory =
          (PreferenceCategory)
              findPreference(getString(R.string.pref_category_selector_settings_configuration_key));
      if (settingsCategory == null) {
        return;
      }

      final int count = settingsCategory.getPreferenceCount();

      for (int i = 0; i < count; i++) {
        final Preference preference = settingsCategory.getPreference(i);

        if (preference instanceof TwoStatePreference) {
          TwoStatePreference switchPreference = (TwoStatePreference) preference;
          switchPreference.setEnabled(enable);
        }
      }
    }

    /**
     * Initialize gestures with selector shortcuts, or restore any previous selector assignments.
     */
    private void assignSelectorShortcuts() {
      String selectorSavedKeySuffix = getString(R.string.pref_selector_saved_gesture_suffix);

      // If a device has a fingerprint sensor, assign gestures to the selector shortcut values
      // If not, it is up to the user to assign gestures.
      if (FeatureSupport.isFingerprintSupported(getActivity())) {
        // If this is the first time the selector is turned on, assign selector shortcuts to
        // specific gestures.
        if (prefs.getBoolean(getString(R.string.pref_selector_first_time_activation_key), true)) {
          setInitialSelectorShortcuts(selectorSavedKeySuffix);
        } else {
          // Reassign saved selector assignments.
          restoreSelectorShortcuts(selectorSavedKeySuffix);
        }
      } else {
        // In case of first time activation, do nothing. Otherwise reassign.
        restoreSelectorShortcuts(selectorSavedKeySuffix);
      }
    }

    /** Reassign any saved selector assignments. */
    private void restoreSelectorShortcuts(String selectorSavedKeySuffix) {
      String[] gestureShortcutKeys =
          context.getResources().getStringArray(R.array.pref_shortcut_keys);

      for (String gestureShortcutKey : gestureShortcutKeys) {
        // Assign the gesture to its saved selector shortcut. There is no need to backup the
        // non-selector value here, since it gets backed up in the preference change listener for
        // the gesture in TalkBackPreferencesActivity, where we make sure the old value
        // (the value to save) of the preference is non-selector and the new value is selector.
        setPrefWithBackup(gestureShortcutKey, selectorSavedKeySuffix);
      }
    }

    /** Reassign the gestures with selector shortcut assignments to their pre-selector shortcuts. */
    private void removeSelectorShortcuts() {
      String[] gestureShortcutKeys =
          context.getResources().getStringArray(R.array.pref_shortcut_keys);

      // Iterate through all the gestures and their shortcut assignments.
      for (String gestureShortcutKey : gestureShortcutKeys) {
        if (prefs.contains(gestureShortcutKey)) {
          String gestureAction =
              prefs.getString(gestureShortcutKey, null); // Null will never be used.
          // Check if assigned action for a gesture is a selector shortcut. If it is,
          // replace with the saved non-selector preference and save the selector assignment.
          if (isSelectorAction(gestureAction)) {
            handleSelectorShortcutRemoval(gestureShortcutKey, gestureAction);
          }
        }
      }
    }

    /**
     * Handle the backup and restoration of a gesture assigned to a selector shortcut. Backup the
     * selector action, and restore the non-selector action.
     */
    private void handleSelectorShortcutRemoval(String prefKey, String selectorAction) {
      String notSelectorSavedKeySuffix = getString(R.string.pref_not_selector_saved_gesture_suffix);
      String selectorSavedKeySuffix = getString(R.string.pref_selector_saved_gesture_suffix);

      // If the non-selector backup exists, use this backup.
      if (setPrefWithBackup(prefKey, notSelectorSavedKeySuffix)) {
        // Backup the selector value of this gesture.
        prefs.edit().putString(prefKey + selectorSavedKeySuffix, selectorAction).apply();
      } else {
        // Non-selector backup doesn't exist, so gesture was never initially assigned before being
        // assigned to a selector shortcut. Remove the key from preferences, so the default value
        // of the gesture is used.
        prefs.edit().remove(prefKey).apply();
      }
    }

    /**
     * Assign to a preference its backup value.
     *
     * @param prefKey the key of the preference to restore.
     * @param newBackupSuffix the string to append to the preference key for retrieving the backup
     *     value.
     * @return {@code true} if the backup value exists and the preference is assigned to this value.
     */
    private boolean setPrefWithBackup(String prefKey, String newBackupSuffix) {
      if (prefs.contains(prefKey + newBackupSuffix)) {
        String newValue =
            prefs.getString(prefKey + newBackupSuffix, null); // Will never return null.
        prefs.edit().putString(prefKey, newValue).apply();
        return true;
      }
      return false;
    }

    /** Check if assigned action for a gesture is a selector shortcut. */
    private boolean isSelectorAction(String gestureAction) {
      String[] selectorShortcutValues =
          context.getResources().getStringArray(R.array.selector_shortcut_values);
      // Iterate through the selector shortcut values.
      for (String selectorShortcutValue : selectorShortcutValues) {
        if (gestureAction.equals(selectorShortcutValue)) {
          return true;
        }
      }
      return false;
    }

    /** Set the initial selector assignments for first time activation. */
    private void setInitialSelectorShortcuts(String selectorSavedKeySuffix) {
      String[] initialSelectorGestures =
          context.getResources().getStringArray(R.array.initial_selector_gestures);
      String[] selectorShortcutValues =
          context.getResources().getStringArray(R.array.selector_shortcut_values);
      String notSelectorSavedKeySuffix = getString(R.string.pref_not_selector_saved_gesture_suffix);

      if (initialSelectorGestures.length != selectorShortcutValues.length) {
        return;
      }

      for (int i = 0; i < initialSelectorGestures.length; i++) {
        if (prefs.contains(initialSelectorGestures[i])) {
          // Save the current assignments for initial gestures.
          prefs
              .edit()
              .putString(
                  initialSelectorGestures[i] + notSelectorSavedKeySuffix,
                  prefs.getString(initialSelectorGestures[i], null)) // Will never return null.
              .apply();
        }

        // Save the selector assignments for initial gestures.
        prefs
            .edit()
            .putString(
                initialSelectorGestures[i] + selectorSavedKeySuffix, selectorShortcutValues[i])
            .apply();

        // Assign selector shortcuts to gestures.
        prefs.edit().putString(initialSelectorGestures[i], selectorShortcutValues[i]).apply();
      }
      prefs
          .edit()
          .putBoolean(getString(R.string.pref_selector_first_time_activation_key), false)
          .apply();
    }
  }
}
