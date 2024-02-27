package com.google.android.accessibility.braille.brailledisplay.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.text.ParcelableSpan;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.TtsSpan;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.Locale;

/** Shows the representations of each braille elements. */
public class BrailleElementsActivity extends PreferencesActivity {
  private static final String TAG = "BrailleElementsActivity";
  private static final int HEADING_LEVEL_MIN = 1;
  private static final int HEADING_LEVEL_MAX = 6;

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new BrailleElementsFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Fragment that holds the braille elements preference. */
  public static class BrailleElementsFragment extends PreferenceFragmentCompat {

    private PreferenceScreen preferenceScreen;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.bd_braille_elements);
      preferenceScreen = findPreference(getString(R.string.pref_key_braille_elements));
      setHeadingWithLevelTitle();
      setTtsSpanTitle();
      setColorSpanTitle();
    }

    private void setHeadingWithLevelTitle() {
      NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
      Preference headingWithLevel =
          preferenceScreen.findPreference(
              getString(R.string.pref_key_bd_braille_elements_h1_to_h6));
      headingWithLevel.setTitle(
          getString(
              R.string.bd_braille_elements_h1_to_h6,
              numberFormat.format(HEADING_LEVEL_MIN),
              numberFormat.format(HEADING_LEVEL_MAX)));
    }

    private void setTtsSpanTitle() {
      // Add TtsSpan to element checked. Don't give initial title because title with same string
      // won't take effect.
      Preference checkedPreference =
          preferenceScreen.findPreference(getString(R.string.pref_key_bd_braille_elements_checked));
      Preference expandedPreference =
          preferenceScreen.findPreference(
              getString(R.string.pref_key_bd_braille_elements_expanded));
      checkedPreference.setTitle(
          getTextWithTtsSpan(
              getString(R.string.bd_braille_elements_checked),
              getString(R.string.bd_braille_elements_checked_content_description)));
      expandedPreference.setTitle(
          getTextWithTtsSpan(
              getString(R.string.bd_braille_elements_expanded),
              getString(R.string.bd_braille_elements_expanded_content_description)));
    }

    private void setColorSpanTitle() {
      for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
        Preference preference = preferenceScreen.getPreference(i);
        // In order to let title color to not change to gray, when the Preference is not
        // selectable.
        CharSequence title = preference.getTitle();
        if (!TextUtils.isEmpty(title)) {
          preference.setTitle(getTextWithColorSpan(title, R.color.settings_primary_text));
          preference.setSelectable(false);
        }
      }
    }

    private SpannableString getTextWithColorSpan(CharSequence text, int colorResId) {
      return getTextWithSpan(
          text,
          new ForegroundColorSpan(
              getContext().getResources().getColor(colorResId, /* theme= */ null)));
    }

    private SpannableString getTextWithTtsSpan(CharSequence text, String contentDescription) {
      return getTextWithSpan(text, new TtsSpan.TextBuilder(contentDescription).build());
    }

    private SpannableString getTextWithSpan(CharSequence text, ParcelableSpan textWithSpan) {
      SpannableString spannableString = new SpannableString(text);
      spannableString.setSpan(
          textWithSpan, /* start= */ 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannableString;
    }
  }
}
