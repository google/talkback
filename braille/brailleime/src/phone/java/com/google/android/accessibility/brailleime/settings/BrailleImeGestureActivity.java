package com.google.android.accessibility.brailleime.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.BASIC;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.CURSOR_MOVEMENT;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.SPELL_CHECK;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.TEXT_SELECTION_AND_EDITING;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.brailleime.BrailleImeActions.Category;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;

/** An activity for showing a BrailleIme gesture. */
public class BrailleImeGestureActivity extends PreferencesActivity {
  public static final String CATEGORY = "category";

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new BrailleImeGestureFragment();
  }

  /** Fragment that holds the braille keyboard gesture preference. */
  public static class BrailleImeGestureFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.brailleime_gesture_category);
      setPreferenceClickListener(R.string.pref_key_brailleime_basic_controls, BASIC);
      setPreferenceClickListener(R.string.pref_key_brailleime_cursor_movement, CURSOR_MOVEMENT);
      setPreferenceClickListener(
          R.string.pref_key_brailleime_text_selection_and_editing, TEXT_SELECTION_AND_EDITING);
      setPreferenceClickListener(R.string.pref_key_brailleime_spell_check, SPELL_CHECK);
    }

    private void setPreferenceClickListener(int prefKeyId, Category category) {
      Preference preference = findPreference(getString(prefKeyId));
      Intent intent = new Intent(getContext(), BrailleImeGestureCommandActivity.class);
      intent.putExtra(CATEGORY, category);
      preference.setIntent(intent);
    }
  }
}
