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

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.Locale;

/** Activity displays all braille display advanced settings preferences. */
public class AdvancedSettingsActivity extends PreferencesActivity {
  private static final String TAG = "AdvancedSettingsActivity";
  private static final int MILLIS_PER_SECOND = 1000;

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new AdvancedSettingsFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Fragment that holds advanced settings preferences. */
  public static class AdvancedSettingsFragment extends PreferenceFragmentCompat {
    private ListPreference timedMessageDurationPreference;
    private ListPreference blinkingIntervalPreference;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(
          this, R.xml.bd_advanced_settings_preferences);
      timedMessageDurationPreference =
          findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_timed_message_duration_fraction_key));
      blinkingIntervalPreference =
          findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_blinking_interval_key));
      getPreferenceManager()
          .getSharedPreferences()
          .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void onResume() {
      super.onResume();
      updatePreferences();
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      getPreferenceManager()
          .getSharedPreferences()
          .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    private void updatePreferences() {
      // Timed message duration
      timedMessageDurationPreference.setValue(
          BrailleUserPreferences.readTimedMessageDurationFraction(getContext()));

      // Blinking interval
      String[] values = BrailleUserPreferences.getAvailableBlinkingIntervalsMs(getContext());
      blinkingIntervalPreference.setEntryValues(values);
      String[] entries = new String[values.length];
      NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
      for (int i = 0; i < values.length; i++) {
        float second = (float) Integer.parseInt(values[i]) / MILLIS_PER_SECOND;
        entries[i] =
            getResources()
                .getQuantityString(
                    R.plurals.bd_blinking_interval,
                    getQuantity(second),
                    numberFormat.format(second));
      }
      blinkingIntervalPreference.setEntries(entries);
      blinkingIntervalPreference.setValue(
          String.valueOf(BrailleUserPreferences.readBlinkingIntervalMs(getContext())));
    }

    private int getQuantity(float number) {
      // All number should be a plural except 1.
      return number == 1 ? 1 : 2;
    }

    private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
        (sharedPreferences, key) -> {
          if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_blinking_interval_key))) {
            BrailleDisplayAnalytics.getInstance(getContext())
                .logBlinkRate(BrailleUserPreferences.readBlinkingIntervalMs(getContext()));
          } else if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_reverse_panning_buttons))) {
            BrailleDisplayAnalytics.getInstance(getContext())
                .logReversePanningKey(
                    BrailleUserPreferences.readReversePanningButtons(getContext()));
          } else if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_timed_message_duration_fraction_key))) {
            int durationMs =
                Math.round(
                    Float.parseFloat(
                            BrailleUserPreferences.readTimedMessageDurationFraction(getContext()))
                        * MILLIS_PER_SECOND);
            BrailleDisplayAnalytics.getInstance(getContext()).logTimedMessageDurationMs(durationMs);
          }
        };
  }
}
