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

package com.google.android.accessibility.brailleime.settings;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.TEXT_SELECTION_AND_EDITING;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.LINE;
import static com.google.android.accessibility.brailleime.settings.BrailleImeGestureActivity.CATEGORY;
import static java.util.Arrays.stream;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.brailleime.BrailleImeActions;
import com.google.android.accessibility.brailleime.BrailleImeActions.Category;
import com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.List;
import java.util.stream.Collectors;

/** An activity for showing BrailleIme gesture commands. */
public class BrailleImeGestureCommandActivity extends PreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new BrailleImeGestureCommandFragment();
  }

  /** Fragment that holds the braille keyboard gesture command preference. */
  public static class BrailleImeGestureCommandFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      Category category = (Category) getActivity().getIntent().getSerializableExtra(CATEGORY);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.brailleime_gesture_commands);
      getActivity().setTitle(category.getTitle(getResources()));

      String description = category.getDescription(getResources());
      Preference descriptionPreference =
          findPreference(getString(R.string.pref_key_brailleime_action_category_description));
      if (TextUtils.isEmpty(description)) {
        getPreferenceScreen().removePreference(descriptionPreference);
      } else {
        descriptionPreference.setSummary(description);
      }

      for (SubCategory subCategory : category.getSubCategories()) {
        if (category == TEXT_SELECTION_AND_EDITING && subCategory == LINE) {
          // TODO: As the text selection for line granularity movement does not work,
          // we mask off the preference of selecting text by line.
          continue;
        }
        String subCategoryTitle = subCategory.getName(getResources());
        PreferenceCategory preferenceCategory = null;
        if (!TextUtils.isEmpty(subCategoryTitle)) {
          preferenceCategory = new PreferenceCategory(getContext());
          preferenceCategory.setTitle(subCategory.getName(getResources()));
          getPreferenceScreen().addPreference(preferenceCategory);
        }
        List<BrailleImeActions> actions =
            stream(BrailleImeActions.values())
                .filter(
                    (BrailleImeActions brailleImeActions) ->
                        brailleImeActions.getCategory() == category
                            && brailleImeActions.getSubCategory() == subCategory
                            && brailleImeActions.isAvailable(getContext()))
                .collect(Collectors.toList());
        for (BrailleImeActions action : actions) {
          Preference preference = new Preference(getContext());
          preference.setTitle(action.getDescriptionRes(getResources()));
          if (action.hasIcon()) {
            preference.setLayoutResource(com.google.android.accessibility.braille.common.R.layout.braille_common_text_with_icon);
            preference.setIcon(action.getIconRes(getContext()));
          } else {
            preference.setLayoutResource(com.google.android.accessibility.braille.common.R.layout.braille_common_text);
          }
          preference.setSelectable(false);
          if (preferenceCategory != null) {
            preferenceCategory.addPreference(preference);
          } else {
            getPreferenceScreen().addPreference(preference);
          }
        }
      }
    }
  }
}
