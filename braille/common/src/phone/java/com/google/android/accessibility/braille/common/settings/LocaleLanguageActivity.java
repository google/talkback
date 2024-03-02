package com.google.android.accessibility.braille.common.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.common.BrailleStringUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Preferred language pick up activity based on specific locale. */
public class LocaleLanguageActivity extends PreferencesActivity {
  private static final String TAG = "PreferredLocaleLanguageActivity";

  public static final String LOCALE_KEY = "locale";
  private Locale locale;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTitle(BrailleStringUtils.toCharacterTitleCase(locale.getDisplayLanguage()));
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    locale = Locale.forLanguageTag(getIntent().getStringExtra(LOCALE_KEY));
    PreferredLocaleLanguageFragment fragment = new PreferredLocaleLanguageFragment();
    fragment.setLocale(locale);
    return fragment;
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Preferred language pick up fragment based on specific locale. */
  public static class PreferredLocaleLanguageFragment extends PreferenceFragmentCompat {
    private PreferenceCategory languages;
    private Locale locale;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(
          this, R.xml.braille_language_locale_preferences);

      languages =
          findPreference(getString(R.string.pref_braille_preferred_locale_languages_category_key));
      refresh();
    }

    private void setLocale(Locale locale) {
      this.locale = locale;
    }

    private void refresh() {
      languages.removeAll();
      List<Code> codes =
          BrailleLanguages.getAvailableCodes(getContext()).stream()
              .filter(code -> code.getLocale().getLanguage().equals(locale.getLanguage()))
              .distinct()
              .collect(Collectors.toList());
      final Collator collator = Collator.getInstance(Locale.getDefault());
      codes.sort(
          (code1, code2) ->
              collator.compare(
                  code1.getUserFacingName(getContext()), code2.getUserFacingName(getContext())));
      List<Code> preferredCodes = BrailleUserPreferences.readAvailablePreferredCodes(getContext());
      for (Code code : codes) {
        languages.addPreference(createPreference(code, preferredCodes));
      }
    }

    private CheckBoxPreference createPreference(Code code, List<Code> preferredCodes) {
      CheckBoxPreference preference = new CheckBoxPreference(getContext());
      preference.setTitle(code.getUserFacingName(getContext()));
      preference.setChecked(preferredCodes.contains(code));
      preference.setOnPreferenceClickListener(
          preference1 -> {
            boolean checked = ((CheckBoxPreference) preference1).isChecked();
            if (checked) {
              preferredCodes.add(code);
              BrailleUserPreferences.writePreferredCodes(getContext(), preferredCodes);
            } else {
              BrailleUserPreferences.removePreferredCode(getContext(), code);
            }
            return true;
          });
      return preference;
    }
  }
}
