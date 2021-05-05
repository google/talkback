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
package com.google.android.accessibility.talkback.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Activity used to set TalkBack context menu preferences. */
public class TalkBackContextMenuPreferencesActivity extends BasePreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new TalkBackContextMenuPreferencesActivity.ContextMenuFragment();
  }

  /** Panel holding a set of context menu preferences. */
  public static class ContextMenuFragment extends TalkbackBaseFragment {

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    // Prevents from runtime exception that will occur in some cases during state restore if the
    // no-argument constructor is not available.
    public ContextMenuFragment() {
      super(R.xml.context_menu_preferences);
    }

    /** Preferences managed by this activity. */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);

      PreferenceScreen preferenceScreen = getPreferenceScreen();
      Context context = getActivity().getApplicationContext();
      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

      if (FeatureSupport.hasAccessibilityShortcut(context)) {
        PreferenceSettingsUtils.hidePreference(
            context, preferenceScreen, R.string.pref_show_context_menu_pause_feedback_setting_key);
      }

      if (FeatureSupport.isWatch(context)) {
        PreferenceSettingsUtils.hidePreference(
            context, preferenceScreen, R.string.pref_show_context_menu_find_on_screen_setting_key);
      }

      if (!TalkBackService.ENABLE_VOICE_COMMANDS) {
        PreferenceSettingsUtils.hidePreference(
            context, preferenceScreen, R.string.pref_show_context_menu_voice_commands_setting_key);
      }

      if (!FeatureSupport.isVibratorSupported(context)) {
        PreferenceSettingsUtils.hidePreference(
            context,
            preferenceScreen,
            R.string.pref_show_context_menu_vibration_feedback_setting_key);
      }

      if (!FeatureSupport.supportSystemActions()) {
        PreferenceSettingsUtils.hidePreference(
            context, preferenceScreen, R.string.pref_show_context_menu_system_action_setting_key);
      }

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
                getContext().getString(R.string.pref_show_context_menu_granularity_setting_key))) {
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
}
