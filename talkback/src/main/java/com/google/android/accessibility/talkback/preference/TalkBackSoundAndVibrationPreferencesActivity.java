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
package com.google.android.accessibility.talkback.preference;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Activity used to set TalkBack sound and vibration preferences. */
public class TalkBackSoundAndVibrationPreferencesActivity extends BasePreferencesActivity {

  /** Preferences managed by this activity. */
  private SharedPreferences prefs;

  /** Listens to shared preference changes and updates the preference items accordingly. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (sharedPrefs, key) -> {
        if (key == null) {
          return;
        }

        if (key.equals(getString(R.string.pref_use_audio_focus_key))) {
          updateTwoStatePreferenceStatus(
              R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
        } else if (key.equals(getString(R.string.pref_soundback_key))) {
          updateTwoStatePreferenceStatus(
              R.string.pref_soundback_key, R.bool.pref_soundback_default);
        } else if (key.equals(getString(R.string.pref_vibration_key))) {
          if (FeatureSupport.isVibratorSupported(getApplicationContext())) {
            updateTwoStatePreferenceStatus(
                R.string.pref_vibration_key, R.bool.pref_vibration_default);
          }
        }
      };

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    prefs = SharedPreferencesUtils.getSharedPreferences(getApplicationContext());
    return new SoundAndVibrationFragment();
  }

  @Override
  protected void onResume() {
    super.onResume();
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    updateTwoStatePreferenceStatus(
        R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    updateTwoStatePreferenceStatus(R.string.pref_soundback_key, R.bool.pref_soundback_default);
    if (FeatureSupport.isVibratorSupported(getApplicationContext())) {
      updateTwoStatePreferenceStatus(R.string.pref_vibration_key, R.bool.pref_vibration_default);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
  }

  /** Fragment to display sound and vibration settings. */
  public static class SoundAndVibrationFragment extends TalkbackBaseFragment {
    public SoundAndVibrationFragment() {
      super(R.xml.sound_and_vibration_preferences);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);
    }
  }
}
