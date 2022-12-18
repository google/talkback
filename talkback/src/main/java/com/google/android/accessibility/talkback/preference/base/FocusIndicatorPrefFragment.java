/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.talkback.preference.base;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.utils.FocusIndicatorUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Fragment used to display TalkBack focus indicator preferences. If TalkBackService is activated
 * when entering this fragment, it could sets the focus indicator settings via {@link
 * TalkBackService#reloadPreferences()}
 */
public class FocusIndicatorPrefFragment extends TalkbackBaseFragment {

  private PreferenceCategory colorPrefCategory;
  private SharedPreferences prefs;

  // INFO: TalkBack For Developers modification
  @Override
  public CharSequence getTitle() {
    return getString(R.string.title_pref_category_manage_focus_indicator);
  }
  // ------------------------------------------

  /** Preference items for focus indicator colors. */
  @VisibleForTesting
  public enum FocusIndicatorPref {
    // TODO: Customize the preference titles and color values.
    DEFAULT_COLOR(R.string.title_pref_default_color, R.color.accessibility_focus_highlight_color),
    RED(R.string.title_pref_red, R.color.focus_indicator_red),
    ORANGE(R.string.title_pref_orange, R.color.focus_indicator_orange),
    YELLOW(R.string.title_pref_yellow, R.color.focus_indicator_yellow),
    GREEN(R.string.title_pref_green, R.color.focus_indicator_green),
    BLUE(R.string.title_pref_blue, R.color.focus_indicator_blue),
    GREY(R.string.title_pref_grey, R.color.focus_indicator_grey);

    private final int titleId;
    private final int colorId;

    FocusIndicatorPref(int titleId, int colorId) {
      this.titleId = titleId;
      this.colorId = colorId;
    }

    public int getTitleId() {
      return titleId;
    }

    public int getColorId() {
      return colorId;
    }
  }

  public FocusIndicatorPrefFragment() {
    super(R.xml.focus_indicator_preferences);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    if (getContext() == null || !FeatureSupport.supportCustomizingFocusIndicator()) {
      return;
    }
    prefs = SharedPreferencesUtils.getSharedPreferences(getContext());

    initBorderColorPref();
  }

  @Override
  public boolean onPreferenceTreeClick(Preference preference) {
    if (preference.getParent().equals(colorPrefCategory)) {
      String prefTitle = preference.getTitle().toString();
      colorSelected(prefTitle);

      for (FocusIndicatorPref source : FocusIndicatorPref.values()) {
        if (TextUtils.equals(prefTitle, getString(source.getTitleId()))) {
          SharedPreferencesUtils.putIntPref(
              prefs, getResources(), R.string.pref_border_color_key, getColor(source.getColorId()));
          break;
        }
      }
    }

    return super.onPreferenceTreeClick(preference);
  }

  private void initBorderColorPref() {
    colorPrefCategory = findPreference(getString(R.string.pref_border_color_category_key));
    int focusIndicatorColor =
        FocusIndicatorUtils.getTalkBackFocusColor(getContext(), prefs, getResources());
    for (FocusIndicatorPref source : FocusIndicatorPref.values()) {
      if (focusIndicatorColor == getColor(source.getColorId())) {
        colorSelected(getString(source.getTitleId()));
        break;
      }
    }
  }

  private void colorSelected(String selectedPref) {
    int count = colorPrefCategory.getPreferenceCount();
    for (int i = 0; i < count; i++) {
      CheckBoxPreference pref = (CheckBoxPreference) colorPrefCategory.getPreference(i);
      boolean shouldCheck = TextUtils.equals(selectedPref, pref.getTitle().toString());
      pref.setChecked(shouldCheck);
    }
  }

  @ColorInt
  private int getColor(int colorResId) {
    return getResources().getColor(colorResId, null);
  }
}
