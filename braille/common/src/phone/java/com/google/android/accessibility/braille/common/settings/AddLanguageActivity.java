package com.google.android.accessibility.braille.common.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.common.BrailleStringUtils;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Preferred language pick up activity list all of the supported locales. */
public class AddLanguageActivity extends PreferencesActivity {
  private static final String TAG = "BrailleLanguageActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new AddLanguageFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Fragment of AddLanguageActivity. */
  public static class AddLanguageFragment extends PreferenceFragmentCompat {
    private PreferenceCategory localeCategoryPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(
          this, R.xml.braille_add_language_preferences);

      localeCategoryPreference =
          findPreference(getString(R.string.pref_braille_languages_category_key));

      initPreferences();
    }

    private void initPreferences() {
      List<Locale> locales =
          BrailleLanguages.getAvailableCodes(getContext()).stream()
              .map(code -> Locale.forLanguageTag(code.getLocale().getLanguage()))
              .distinct()
              .collect(Collectors.toList());
      Locale systemLocale = Locale.getDefault();
      final Collator collator = Collator.getInstance(systemLocale);
      locales.sort(
          (locale1, locale2) ->
              collator.compare(locale1.getDisplayLanguage(), locale2.getDisplayLanguage()));
      int systemLocaleIndex = -1;
      for (int i = 0; i < locales.size(); i++) {
        if (locales.get(i).getLanguage().equals(systemLocale.getLanguage())) {
          systemLocaleIndex = i;
          break;
        }
      }
      // Place system locale at the beginning.
      if (systemLocaleIndex != -1) {
        locales.add(/* index= */ 0, locales.remove(systemLocaleIndex));
      }
      for (Locale locale : locales) {
        localeCategoryPreference.addPreference(createAvailableLocalePreference(locale));
      }
      if (localeCategoryPreference.getPreferenceCount() == 0) {
        localeCategoryPreference.setVisible(false);
      }
    }

    private Preference createAvailableLocalePreference(Locale locale) {
      Preference preference = new Preference(getContext());
      preference.setTitle(BrailleStringUtils.toCharacterTitleCase(locale.getDisplayLanguage()));
      preference.setOnPreferenceClickListener(
          preference1 -> {
            Intent intent = new Intent(getContext(), LocaleLanguageActivity.class);
            intent.putExtra(LocaleLanguageActivity.LOCALE_KEY, locale.getLanguage());
            getContext().startActivity(intent);
            finishContainerActivity();
            return true;
          });
      return preference;
    }

    private void finishContainerActivity() {
      if (getActivity() != null) {
        getActivity().finish();
      }
    }
  }
}
