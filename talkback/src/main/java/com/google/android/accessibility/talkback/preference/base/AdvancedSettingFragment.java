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

import static com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.DOUBLE_TAP;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.TypingMethod;
import com.google.android.accessibility.talkback.preference.base.PreferenceActionHelper.WebPage;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
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
    updateTouchExplorationState();
    updateTimeFeedbackFormatPreference();
  }

  private void updateTimeFeedbackFormatPreference() {
    final ListPreference timeFeedbackFormatPref =
        findPreference(getString(R.string.pref_time_feedback_format_key));
    if (timeFeedbackFormatPref != null) {
      timeFeedbackFormatPref.setSummaryProvider(preference -> getSummaryForTimeFeedbackFormat());
    }
  }

  private String getSummaryForTimeFeedbackFormat() {
    final Resources resources = getResources();
    final String timeFeedbackFormat =
        SharedPreferencesUtils.getStringPref(
            prefs,
            resources,
            R.string.pref_time_feedback_format_key,
            R.string.pref_time_feedback_format_default);
    int timeFeedbackFormatType =
        RingerModeAndScreenMonitor.prefValueToTimeFeedbackFormat(resources, timeFeedbackFormat);

    switch (timeFeedbackFormatType) {
      case RingerModeAndScreenMonitor.TIME_FEEDBACK_FORMAT_12_HOURS:
        return getString(R.string.pref_time_feedback_format_entries_12_hour);
      case RingerModeAndScreenMonitor.TIME_FEEDBACK_FORMAT_24_HOURS:
        return getString(R.string.pref_time_feedback_format_entries_24_hour);
      default:
        return DateFormat.is24HourFormat(getContext())
            ? getString(R.string.pref_time_feedback_format_entries_24_hour)
            : getString(R.string.pref_time_feedback_format_entries_12_hour);
    }
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
      // Read talkback speak-passwords preference, with default to system preference.
      boolean speakPassValue = SpeakPasswordsManager.getAlwaysSpeakPasswordsPref(context);
      // Update talkback preference display to match read value.
      TwoStatePreference prefSpeakPasswords =
          (TwoStatePreference)
              findPreference(getString(R.string.pref_speak_passwords_without_headphones));
      if (prefSpeakPasswords != null) {
        prefSpeakPasswords.setChecked(speakPassValue);
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

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    context = getContext();
    prefs = SharedPreferencesUtils.getSharedPreferences(context);

    // Link preferences to web-viewer. The behavior depends on the type of form factors.
    if (findPreference(getString(R.string.pref_policy_key)) != null) {
      PreferenceActionHelper.assignWebIntentToPreference(
          this,
          findPreference(getString(R.string.pref_policy_key)),
          WebPage.WEB_PAGE_PRIVACY_POLICY);
    }
    if (findPreference(getString(R.string.pref_show_tos_key)) != null) {
      PreferenceActionHelper.assignWebIntentToPreference(
          this,
          findPreference(getString(R.string.pref_show_tos_key)),
          WebPage.WEB_PAGE_TERMS_OF_SERVICE);
    }

    updateSpeakPasswordsPreference();

    Preference typingPreference = findPreference(getString(R.string.pref_typing_confirmation_key));
    Preference longPressDuration =
        findPreference(getString(R.string.pref_typing_long_press_duration_key));
    // Disable the "pref_typing_long_press_duration_key" when "pref_typing_confirmation_key" is set
    // as double-tap.
    if (typingPreference != null && longPressDuration != null) {
      @TypingMethod
      int typingMethod =
          SharedPreferencesUtils.getIntFromStringPref(
              prefs,
              getResources(),
              R.string.pref_typing_confirmation_key,
              R.string.pref_typing_confirmation_default);
      longPressDuration.setEnabled(typingMethod != DOUBLE_TAP);
      typingPreference.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            longPressDuration.setEnabled(Integer.parseInt((String) newValue) != DOUBLE_TAP);
            return true;
          });
    }
  }
}
