/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.preference.Preference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Panel holding a set of context menu preferences. */
public class ContextMenuFragment extends TalkbackBaseFragment {

  private SharedPreferences.OnSharedPreferenceChangeListener listener;

  // Prevents from runtime exception that will occur in some cases during state restore if the
  // no-argument constructor is not available.
  public ContextMenuFragment() {
    super(R.xml.context_menu_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_category_context_menu);
  }

  @Override
  public void onCreate(Bundle bundle) {
    PreferencesActivityUtils.removeEditingKey(getContext());
    super.onCreate(bundle);
  }

  /** Preferences managed by this fragment. */
  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    final Context context = getContext();
    if (context == null) {
      return;
    }

    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

    Preference preferenceGranularityDetail =
        findPreference(
            context.getString(R.string.pref_show_context_menu_granularity_detail_setting_key));

    if (!SharedPreferencesUtils.getBooleanPref(
        prefs,
        context.getResources(),
        R.string.pref_show_context_menu_granularity_setting_key,
        R.bool.pref_show_context_menu_granularity_default)) {
      if (preferenceGranularityDetail != null) {
        preferenceGranularityDetail.setEnabled(false);
      }
    }

    listener =
        (preference, keyString) -> {
          if (preferenceGranularityDetail == null) {
            return;
          }
          if (TextUtils.equals(
              keyString,
              context.getString(R.string.pref_show_context_menu_granularity_setting_key))) {
            if (SharedPreferencesUtils.getBooleanPref(
                preference,
                context.getResources(),
                R.string.pref_show_context_menu_granularity_setting_key,
                R.bool.pref_show_context_menu_granularity_default)) {
              preferenceGranularityDetail.setEnabled(true);
            } else {
              preferenceGranularityDetail.setEnabled(false);
            }
          }
        };
    prefs.registerOnSharedPreferenceChangeListener(listener);

    // For describe-image menu item.
    Preference describeImagePreference =
        findPreference(
            context.getString(R.string.pref_show_context_menu_image_caption_setting_key));
    if (describeImagePreference != null) {
      describeImagePreference.setVisible(ImageCaptioner.supportsImageCaption(context));
    }
  }

  @Override
  public void onDestroy() {
    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    if (listener != null) {
      prefs.unregisterOnSharedPreferenceChangeListener(listener);
      listener = null;
    }
    super.onDestroy();
  }
}
