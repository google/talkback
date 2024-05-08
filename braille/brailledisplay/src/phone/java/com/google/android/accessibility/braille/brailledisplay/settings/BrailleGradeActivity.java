/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;

/** Shows the Braille grade for the currently state. */
public class BrailleGradeActivity extends PreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new BrailleGradeFragment();
  }

  /** Fragment of BrailleGradeActivity. */
  public static final class BrailleGradeFragment extends PreferenceFragmentCompat {
    private CheckBoxPreference contractedPref;
    private CheckBoxPreference uncontractedPref;
    private PreferenceCategory tipsPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      BrailleUserPreferences.getSharedPreferences(getContext(), BRAILLE_SHARED_PREFS_FILENAME)
          .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.braille_grade);
      contractedPref = findPreference(getString(R.string.pref_key_braille_grade_contracted));
      contractedPref.setOnPreferenceClickListener(
          new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
              BrailleUserPreferences.writeContractedMode(
                  getContext(), ((CheckBoxPreference) preference).isChecked());
              return true;
            }
          });
      uncontractedPref = findPreference(getString(R.string.pref_key_braille_grade_uncontracted));
      uncontractedPref.setOnPreferenceClickListener(
          new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
              BrailleUserPreferences.writeContractedMode(
                  getContext(), !((CheckBoxPreference) preference).isChecked());
              return true;
            }
          });
      tipsPref = findPreference(getString(R.string.pref_key_braille_grade_tips));
      Preference preference = new Preference(getContext());
      preference.setSummary(R.string.bd_braille_grade_tips);
      tipsPref.addPreference(preference);

      onModelChanged();
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      BrailleUserPreferences.getSharedPreferences(getContext(), BRAILLE_SHARED_PREFS_FILENAME)
          .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    private void onModelChanged() {
      boolean contractedMode = BrailleUserPreferences.readContractedMode(getContext());
      contractedPref.setChecked(contractedMode);
      uncontractedPref.setChecked(!contractedMode);
    }

    private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode))) {
              onModelChanged();
            }
          }
        };
  }
}
