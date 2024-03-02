package com.google.android.accessibility.braille.brailledisplay.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;

/** Shows the auto scroll setting. */
public class AutoScrollActivity extends PreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new AutoScrollFragment();
  }

  /** Fragment of AutoScrollActivity. */
  public static final class AutoScrollFragment extends PreferenceFragmentCompat {
    private AutoScrollDurationPreference autoScrollDurationPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.bd_auto_scroll);
      autoScrollDurationPref = findPreference(getString(R.string.pref_key_bd_auto_scroll_duration));
      SwitchPreference autoAdjustDurationPref =
          findPreference(getString(R.string.pref_key_bd_auto_adjust_duration));
      autoAdjustDurationPref.setChecked(
          BrailleUserPreferences.readAutoAdjustDurationEnable(getContext()));
      autoAdjustDurationPref.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            BrailleUserPreferences.writeAutoAdjustDurationEnable(getContext(), (boolean) newValue);
            return true;
          });
    }

    @Override
    public void onResume() {
      super.onResume();
      refresh();
      BrailleUserPreferences.getSharedPreferences(getContext(), BRAILLE_SHARED_PREFS_FILENAME)
          .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
      super.onPause();
      BrailleUserPreferences.getSharedPreferences(getContext(), BRAILLE_SHARED_PREFS_FILENAME)
          .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
        (SharedPreferences sharedPreferences, String key) -> {
          if (key.equals(getString(R.string.pref_bd_auto_scroll_duration_key))) {
            refresh();
          }
        };

    private void refresh() {
      autoScrollDurationPref.setValue(BrailleUserPreferences.readAutoScrollDuration(getContext()));
    }
  }
}
