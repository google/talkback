/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.preference.base;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.text.TextUtils;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Fragment to display sound and vibration settings. */
public class SoundAndVibrationFragment extends TalkbackBaseFragment {
  /** Preferences managed by this activity. */
  private SharedPreferences prefs;

  public SoundAndVibrationFragment() {
    super(R.xml.sound_and_vibration_preferences);
  }

  @Override
  public CharSequence getTitle() {
    // Changes title by supporting vibration or not. Preference Vibration Feedback will be removed
    // by TalkBackPreferenceFilter if this device doesn't support vibration.
    int titleResid =
        FeatureSupport.isVibratorSupported(getContext())
            ? R.string.title_pref_sound_and_vibration
            : R.string.title_pref_sound;
    return getText(titleResid);
  }

  /** Listens to shared preference changes and updates the preference items accordingly. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (sharedPrefs, key) -> {
        FragmentActivity activity = getActivity();
        if (TextUtils.equals(key, getString(R.string.pref_use_audio_focus_key))) {
          updateTwoStatePreferenceStatus(
              activity, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
        } else if (TextUtils.equals(key, getString(R.string.pref_soundback_key))) {
          updateTwoStatePreferenceStatus(
              activity, R.string.pref_soundback_key, R.bool.pref_soundback_default);
        } else if (TextUtils.equals(key, getString(R.string.pref_vibration_key))) {
          if (FeatureSupport.isVibratorSupported(getContext())) {
            updateTwoStatePreferenceStatus(
                activity, R.string.pref_vibration_key, R.bool.pref_vibration_default);
          }
        }
      };

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
  }

  @Override
  public void onResume() {
    super.onResume();
    FragmentActivity activity = this.getActivity();
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    updateTwoStatePreferenceStatus(
        activity, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    updateTwoStatePreferenceStatus(
        activity, R.string.pref_soundback_key, R.bool.pref_soundback_default);
    if (FeatureSupport.isVibratorSupported(getContext())) {
      updateTwoStatePreferenceStatus(
          activity, R.string.pref_vibration_key, R.bool.pref_vibration_default);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
  }

  /**
   * Updates the status of preference to on or off after the selector or context menu change the
   * state while the activity is visible.
   *
   * @param activity FragmentActivity which contain Fragments.
   * @param preferenceKeyResId key string id of Preference which likes to find
   * @param preferenceDefaultKeyResId default key string id of Preference which likes to find
   */
  private static void updateTwoStatePreferenceStatus(
      FragmentActivity activity, int preferenceKeyResId, int preferenceDefaultKeyResId) {

    @Nullable Preference preference =
        PreferenceSettingsUtils.findPreference(activity, activity.getString(preferenceKeyResId));
    if (preference instanceof TwoStatePreference) {
      // Make sure that we have the latest value of preference before continuing.
      boolean enabledState =
          SharedPreferencesUtils.getBooleanPref(
              SharedPreferencesUtils.getSharedPreferences(activity.getApplicationContext()),
              activity.getResources(),
              preferenceKeyResId,
              preferenceDefaultKeyResId);

      ((TwoStatePreference) preference).setChecked(enabledState);
    }
  }
}
