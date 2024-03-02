package com.google.android.accessibility.braille.common.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.ArrayList;
import java.util.List;

/** Picks up braille preferred languages activity. */
public class BrailleLanguagesActivity extends PreferencesActivity {
  private static final String TAG = "BrailleLanguageActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new BrailleLanguageFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Picks up preferred languages fragment. */
  public static class BrailleLanguageFragment extends PreferenceFragmentCompat {
    private PreferenceCategory languagesPreferenceCategory;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.braille_languages_preferences);
      languagesPreferenceCategory =
          findPreference(getString(R.string.pref_braille_preferred_languages_category_key));
      Preference addLanguagePreference =
          findPreference(getString(R.string.pref_braille_add_language_preference_key));
      addLanguagePreference.setIntent(new Intent(getContext(), AddLanguageActivity.class));
      DrawableCompat.setTint(
          addLanguagePreference.getIcon(), getContext().getColor(R.color.settings_primary_text));

      refresh();
    }

    @Override
    public void onResume() {
      super.onResume();
      refresh();
    }

    private void refresh() {
      List<Code> codes = BrailleUserPreferences.readAvailablePreferredCodes(getContext());
      List<Preference> languagesCategoryList = getLanguagesCategoryList();
      for (int i = 0; i < codes.size(); i++) {
        if (i < languagesCategoryList.size()) {
          if (!codes.get(i).name().equals(languagesCategoryList.get(i).getKey())) {
            updateLanguagePreference(languagesPreferenceCategory.getPreference(i), codes.get(i));
          }
        } else {
          languagesPreferenceCategory.addPreference(createLanguagePreference(codes.get(i)));
        }
      }
      for (int i = languagesPreferenceCategory.getPreferenceCount() - 1; i >= codes.size(); i--) {
        languagesPreferenceCategory.removePreference(languagesPreferenceCategory.getPreference(i));
      }
    }

    private Preference createLanguagePreference(Code code) {
      Preference preference = new LanguagePreference(getContext());
      preference.setTitle(code.getUserFacingName(getContext()));
      preference.setKey(code.name());
      return preference;
    }

    private void updateLanguagePreference(Preference preference, Code code) {
      preference.setTitle(code.getUserFacingName(getContext()));
      preference.setKey(code.name());
    }

    private List<Preference> getLanguagesCategoryList() {
      List<Preference> preferences = new ArrayList<>();
      for (int i = 0; i < languagesPreferenceCategory.getPreferenceCount(); i++) {
        preferences.add(languagesPreferenceCategory.getPreference(i));
      }
      return preferences;
    }

    private static class LanguagePreference extends Preference {

      public LanguagePreference(Context context) {
        super(context);
        // Prevent triggered by clicking preference.
        setSelectable(false);
        setWidgetLayoutResource(R.layout.image_preference);
      }

      @Override
      public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ImageButton removeButton = (ImageButton) holder.findViewById(R.id.remove_button);
        DrawableCompat.setTint(
            removeButton.getDrawable(), getContext().getColor(R.color.settings_primary_text));
        removeButton.setContentDescription(
            getContext()
                .getString(R.string.remove_language_button_content_description, getTitle()));
        removeButton.setOnClickListener(
            v -> {
              BrailleUserPreferences.removePreferredCode(getContext(), Code.valueOf(getKey()));
              this.getParent().removePreference(this);
            });
      }
    }
  }
}
