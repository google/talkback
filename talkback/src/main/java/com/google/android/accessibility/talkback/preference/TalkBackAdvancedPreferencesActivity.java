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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Activity used to set TalkBack advanced preferences. */
public class TalkBackAdvancedPreferencesActivity extends BasePreferencesActivity {
  private static final String TAG = "TalkBackAdvancedPreferencesActivity";

  /** Preferences managed by this activity. */
  private SharedPreferences prefs;

  /**
   * Listens to changes in the TalkBack state to determine which preference items should be enable
   * or disabled.
   */
  private final ServiceStateListener serviceStateListener =
      newState -> updateDimingPreferenceStatus();

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    prefs = SharedPreferencesUtils.getSharedPreferences(getApplicationContext());
    return new AdvancedSettingFragment();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateTalkBackShortcutStatus();
    updateTouchExplorationState();
    updateDimingPreferenceStatus();

    TalkBackService talkBackService = TalkBackService.getInstance();
    if (talkBackService != null) {
      talkBackService.addServiceStateListener(serviceStateListener);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();

    TalkBackService talkBackService = TalkBackService.getInstance();
    if (talkBackService != null) {
      talkBackService.removeServiceStateListener(serviceStateListener);
    }
  }

  /**
   * Updates the preferences state to match the actual state of touch exploration. This is called
   * once when the preferences activity launches and again whenever the actual state of touch
   * exploration changes.
   */
  private void updateTouchExplorationState() {
    final ContentResolver resolver = this.getContentResolver();
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

  private void updateDimingPreferenceStatus() {
    // Log an error if the device supports volume key shortcuts (i.e. those running Android N or
    // earlier) but the dim screen shortcut switch is not available. Don't exit the function
    // because we still want to set up the other switch.
    final TwoStatePreference dimShortcutPreference =
        (TwoStatePreference) findPreference(getString(R.string.pref_dim_volume_three_clicks_key));
    if (FeatureSupport.supportsVolumeKeyShortcuts() && dimShortcutPreference == null) {
      LogUtils.e(TAG, "Expected switch for dim screen shortcut, but switch is not present.");
    }

    if (dimShortcutPreference == null) {
      return;
    }

    final TalkBackService talkBack = TalkBackService.getInstance();
    if (talkBack == null || !DimScreenActor.isSupportedbyPlatform(talkBack)) {
      LogUtils.i(
          TAG,
          "Either TalkBack could not be found, or the platform does not support screen dimming.");
      final PreferenceGroup category =
          (PreferenceGroup) findPreference(getString(R.string.pref_category_controls_key));
      if (category == null) {
        return;
      }
      category.removePreference(dimShortcutPreference);
      return;
    }
  }

  /**
   * Returns whether touch exploration is enabled. This is more reliable than {@code
   * AccessibilityManager.isTouchExplorationEnabled()} because it updates atomically.
   */
  private static boolean isTouchExplorationEnabled(ContentResolver resolver) {
    return Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1;
  }

  /** Fragment to display advanced settings. */
  public static class AdvancedSettingFragment extends TalkbackBaseFragment {
    public AdvancedSettingFragment() {
      super(R.xml.advanced_preferences);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);

      @Nullable Activity activity = getActivity();
      if (activity == null) {
        return;
      }
      Context context = activity.getApplicationContext();

      // Link preferences to web-viewer.
      if (findPreference(getString(R.string.pref_policy_key)) != null) {
        PreferenceSettingsUtils.assignWebIntentToPreference(
            this,
            findPreference(getString(R.string.pref_policy_key)),
            "http://www.google.com/policies/privacy/");
      }
      if (findPreference(getString(R.string.pref_show_tos_key)) != null) {
        PreferenceSettingsUtils.assignWebIntentToPreference(
            this,
            findPreference(getString(R.string.pref_show_tos_key)),
            "http://www.google.com/mobile/toscountry");
      }

      Preference resumeTalkBack = findPreference(getString(R.string.pref_resume_talkback_key));
      if (resumeTalkBack != null) {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
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
                      prefs,
                      getResources(),
                      R.string.pref_show_suspension_confirmation_dialog,
                      true);
                }
              }
              return true;
            });
      }
    }
  }
}
