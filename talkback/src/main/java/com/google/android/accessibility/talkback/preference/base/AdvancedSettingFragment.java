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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.utils.RemoteIntentUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Fragment to display advanced settings. */
public class AdvancedSettingFragment extends TalkbackBaseFragment {
  private static final String TAG = "AdvancedSettingFragment";

  private Context context;

  private SharedPreferences prefs;

  public AdvancedSettingFragment() {
    super(R.xml.advanced_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_advanced_settings);
  }

  @Override
  public void onResume() {
    super.onResume();
    updateTalkBackShortcutStatus();
    updateTouchExplorationState();
  }

  /**
   * Returns whether touch exploration is enabled. This is more reliable than {@code
   * AccessibilityManager.isTouchExplorationEnabled()} because it updates atomically.
   */
  private static boolean isTouchExplorationEnabled(ContentResolver resolver) {
    return Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1;
  }

  /**
   * In versions O and above, updates preference for speaking passwords out loud. This way, if the
   * user already wants the system to speak speak passwords out loud, the user will see no change
   * and passwords will continue to be spoken. In M and below, hide this preference.
   */
  private void updateSpeakPasswordsPreference() {
    if (FeatureSupport.useSpeakPasswordsServicePref()) {
      // Read talkback speak-passwords preference, with default to system preference.
      boolean speakPassValue = SpeakPasswordsManager.getAlwaysSpeakPasswordsPref(context);
      // Update talkback preference display to match read value.
      TwoStatePreference prefSpeakPasswords =
          (TwoStatePreference)
              findPreference(getString(R.string.pref_speak_passwords_without_headphones));
      if (prefSpeakPasswords != null) {
        prefSpeakPasswords.setChecked(speakPassValue);
      }
    } else {
      // Hides audio category and speak-passwords preference
      PreferenceSettingsUtils.hidePreference(
          context, getPreferenceScreen(), R.string.pref_category_audio_key);
      PreferenceSettingsUtils.hidePreference(
          context, getPreferenceScreen(), R.string.pref_speak_passwords_without_headphones);
    }
  }

  /**
   * Updates the preferences state to match the actual state of touch exploration. This is called
   * once when the preferences activity launches and again whenever the actual state of touch
   * exploration changes.
   */
  private void updateTouchExplorationState() {
    final ContentResolver resolver = context.getContentResolver();
    final Resources res = getResources();
    final boolean requestedState =
        SharedPreferencesUtils.getBooleanPref(
            prefs, res, R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
    final boolean actualState;

    // If accessibility is disabled then touch exploration is always
    // disabled, so the "actual" state should just be the requested state.
    if (TalkBackService.isServiceActive()) {
      actualState = isTouchExplorationEnabled(resolver);
    } else {
      actualState = requestedState;
    }

    // Enable/disable preferences that depend on explore-by-touch.
    // Cannot use "dependency" attribute in preferences XML file, because touch-explore-preference
    // is in a different preference-activity (developer preferences).
    Preference singleTapPref = findPreference(getString(R.string.pref_single_tap_key));
    if (singleTapPref != null) {
      singleTapPref.setEnabled(actualState);
    }
  }

  private void updateTalkBackShortcutStatus() {
    final TwoStatePreference preference =
        (TwoStatePreference) findPreference(getString(R.string.pref_two_volume_long_press_key));
    if (preference == null) {
      return;
    }
    preference.setEnabled(TalkBackService.getInstance() != null || preference.isChecked());
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    context = getContext();
    prefs = SharedPreferencesUtils.getSharedPreferences(context);

    // Link preferences to web-viewer.
    if (findPreference(getString(R.string.pref_policy_key)) != null) {
      linkToWebPage(
          this,
          findPreference(getString(R.string.pref_policy_key)),
          "http://www.google.com/policies/privacy/");
    }
    if (findPreference(getString(R.string.pref_show_tos_key)) != null) {
      linkToWebPage(
          this,
          findPreference(getString(R.string.pref_show_tos_key)),
          "http://www.google.com/mobile/toscountry");
    }

    updateSpeakPasswordsPreference();

    Preference resumeTalkBack = findPreference(getString(R.string.pref_resume_talkback_key));
    if (resumeTalkBack != null) {
      resumeTalkBack.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            final String key = preference.getKey();
            if (getString(R.string.pref_resume_talkback_key).equals(key)) {
              final String oldValue =
                  SharedPreferencesUtils.getStringPref(
                      prefs,
                      getResources(),
                      R.string.pref_resume_talkback_key,
                      R.string.pref_resume_talkback_default);
              if (!newValue.equals(oldValue)) {
                // Reset the suspend warning dialog when the resume
                // preference changes.
                SharedPreferencesUtils.putBooleanPref(
                    prefs, getResources(), R.string.pref_show_suspension_confirmation_dialog, true);
              }
            }
            return true;
          });
    }
  }

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL.
   *
   * @param fragment PreferenceFragmentCompat to get context.
   * @param preference Preference to send Intent
   * @param url URL which launches web page
   */
  private static void linkToWebPage(
      PreferenceFragmentCompat fragment, Preference preference, String url) {
    if (fragment == null) {
      return;
    }

    if (FeatureSupport.isWatch(fragment.getContext())) {
      RemoteIntentUtils.assignWebIntentToPreference(fragment, preference, url);
    } else {
      PreferenceSettingsUtils.assignWebIntentToPreference(fragment, preference, url);
    }
  }
}
