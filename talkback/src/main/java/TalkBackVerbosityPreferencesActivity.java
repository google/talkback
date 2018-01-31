/*
 * Copyright 2017 Google Inc.
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

import static com.android.talkback.TalkBackPreferencesActivity.TalkBackPreferenceFragment.HIDDEN_PREFERENCE_KEY_IDS_IN_ARC;
import static com.android.talkback.TalkBackPreferencesActivity.TalkBackPreferenceFragment.HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;

/**
 * Activity used to set TalkBack's verbosity preferences.
 *
 * <p>Allow flexibility for multiple customizable presets, in the future.
 */
public class TalkBackVerbosityPreferencesActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Configure action bar.
    setTitle(getString(R.string.pref_verbosity_title));
    ActionBar actionBar = getActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    // Create user-interface for currently selected verbosity preset.
    VerbosityPrefFragment fragment = new VerbosityPrefFragment();
    getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
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

  /** Panel holding a set of verbosity preferences. Recreated when preset value changes. */
  public static class VerbosityPrefFragment extends PreferenceFragment {

    // Member data
    private SharedPreferences mPreferences;
    private String mPresetValue; // String identifier for selected preset.

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      // Get initial preset value.
      if (BuildVersionUtils.isAtLeastN()) {
        getPreferenceManager().setStorageDeviceProtected();
      }
      Context context = getActivity().getApplicationContext();
      mPreferences = SharedPreferencesUtils.getSharedPreferences(context);
      mPresetValue =
          SharedPreferencesUtils.getStringPref(
              mPreferences,
              getResources(),
              R.string.pref_verbosity_preset_key,
              R.string.pref_verbosity_preset_value_default);

      // Create the preferences screen.
      addPreferencesFromResource(R.xml.verbosity_preferences);

      // Hide some preferences for devices like watch and ARC.
      if (FormFactorUtils.getInstance(context).isArc()) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_IN_ARC);
      }
      if (FormFactorUtils.getInstance(context).isWatch()) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH);
      }

      ArrayList<Preference> detailedPrefs = collectDetailedPreferences();
      copyPresetsToUi(detailedPrefs); // Cheap, just reading preferences.

      // Disable default preset preference details.
      if (mPresetValue.equals(getString(R.string.pref_verbosity_preset_value_high))
          || mPresetValue.equals(getString(R.string.pref_verbosity_preset_value_low))) {
        disablePreferenceDetails(detailedPrefs);
      }

      // Attach listeners after preset values are copied to active, so that copying preset does
      // not invoke preference-change listener.
      attachPreferenceListeners();
    }

    /** Collects all preset-controlled preferences. */
    private ArrayList<Preference> collectDetailedPreferences() {
      ArrayList<Preference> detailedPrefs = new ArrayList<Preference>();
      PreferenceGroup prefGroup =
          (PreferenceGroup) findPreference(R.string.pref_verbosity_category_preset_settings_key);
      if (prefGroup == null) {
        return detailedPrefs;
      }

      // For each preference... collect
      for (int p = 0; p < prefGroup.getPreferenceCount(); p++) {
        Preference preference = prefGroup.getPreference(p);
        if (preference != null) {
          detailedPrefs.add(preference);
        }
      }
      return detailedPrefs;
    }

    private void copyPresetsToUi(ArrayList<Preference> detailedPrefs) {
      // For each detailed preference...
      for (Preference preference : detailedPrefs) {
        // Change active key to preset key.
        String key = preference.getKey();
        String keyForPreset = VerbosityPreferences.toPresetPrefKey(mPresetValue, key);
        preference.setKey(keyForPreset);

        // Retrieve preset preference value and update UI element.
        if (preference instanceof SwitchPreference) {
          SwitchPreference prefSwitch = (SwitchPreference) preference;
          boolean value =
              VerbosityPreferences.getPreferencePresetBool(
                  mPreferences,
                  getResources(),
                  mPresetValue,
                  key,
                  getDefaultValueForSwitchPreferences(key));
          prefSwitch.setChecked(value);
        } else if (preference instanceof ListPreference) {
          ListPreference prefList = (ListPreference) preference;
          String value =
              VerbosityPreferences.getPreferencePresetString(
                  mPreferences, getResources(), mPresetValue, key, null);
          if (value != null) {
            prefList.setValue(value);
          }
        } else {
          LogUtils.log(
              this,
              Log.ERROR,
              "Unhandled preference type %s",
              preference.getClass().getSimpleName());
        }
      }
    }

    private void disablePreferenceDetails(ArrayList<Preference> detailedPrefs) {
      // For each detailed preference... disable preference.
      for (Preference preference : detailedPrefs) {
        preference.setEnabled(false);
      }
    }

    // Returns the default value for the given key.
    private boolean getDefaultValueForSwitchPreferences(String key) {
      if (key.equals(getResources().getString(R.string.pref_screenoff_key))) {
        return getResources().getBoolean(R.bool.pref_screenoff_default);
      } else if (key.equals(getResources().getString(R.string.pref_a11y_hints_key))) {
        return getResources().getBoolean(R.bool.pref_a11y_hints_default);
      } else if (key.equals(getResources().getString(R.string.pref_intonation_key))) {
        return getResources().getBoolean(R.bool.pref_intonation_default);
      } else if (key.equals(getResources().getString(R.string.pref_phonetic_letters_key))) {
        return getResources().getBoolean(R.bool.pref_phonetic_letters_default);
      } else if (key.equals(getResources().getString(R.string.pref_speak_roles_key))) {
        return getResources().getBoolean(R.bool.pref_speak_roles_default);
      } else if (key.equals(
          getResources().getString(R.string.pref_speak_container_element_positions_key))) {
        return getResources().getBoolean(R.bool.pref_speak_container_element_positions_default);
      }
      return true;
    }

    @Override
    public void onResume() {
      super.onResume();
      attachPreferenceListeners();
      String presetValueString =
          mPreferences.getString(
              getString(R.string.pref_verbosity_preset_key),
              getString(R.string.pref_verbosity_preset_value_default));
      replaceFragment(presetValueString);
    }

    private void attachPreferenceListeners() {
      mPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
      super.onPause();
      mPreferences.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    /** Listener for preference changes. */
    private final OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            // Handles ListPreference changed case and case where the preset is changed
            // using the selector and the fragment is visible.
            if (TextUtils.equals(key, getString(R.string.pref_verbosity_preset_key))) {
              String newValueString =
                  mPreferences.getString(
                      getString(R.string.pref_verbosity_preset_key),
                      getString(R.string.pref_verbosity_preset_value_default));

              replaceFragment(newValueString);

              // Announce new preset. If the verbosity is changed using the selector,
              // GestureControllerApp.changeVerbosity will also call this method. SpeechController
              // will then deduplicate the announcement event so only one is spoken.
              announcePresetChange(newValueString, getActivity());
            }
          }
        };

    /** Replace preference fragment if the preset value has changed */
    private void replaceFragment(String newValueString) {
      if (TextUtils.equals(mPresetValue, newValueString)) {
        return;
      }
      VerbosityPrefFragment newFragment = new VerbosityPrefFragment();
      getFragmentManager().beginTransaction().replace(android.R.id.content, newFragment).commit();
    }

    private Preference findPreference(int keyId) {
      return getPreferenceScreen().findPreference(getString(keyId));
    }
  }

  public static void announcePresetChange(String newValueString, Context context) {
    String announcement =
        String.format(
            context.getString(R.string.pref_verbosity_preset_change),
            presetValueToName(newValueString, context));
    TalkBackKeyboardShortcutPreferencesActivity.announceText(announcement, context);
  }

  /** Map preset value key to preset name. */
  private static String presetValueToName(String presetValueKey, Context context) {
    if (presetValueKey.equals(context.getString(R.string.pref_verbosity_preset_value_high))) {
      return context.getString(R.string.pref_verbosity_preset_entry_high);
    } else if (presetValueKey.equals(
        context.getString(R.string.pref_verbosity_preset_value_custom))) {
      return context.getString(R.string.pref_verbosity_preset_entry_custom);
    } else if (presetValueKey.equals(context.getString(R.string.pref_verbosity_preset_value_low))) {
      return context.getString(R.string.pref_verbosity_preset_entry_low);
    } else {
      return null;
    }
  }
}
