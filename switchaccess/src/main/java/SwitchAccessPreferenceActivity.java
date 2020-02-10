/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.widget.BaseAdapter;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache.SwitchAccessPreferenceChangedListener;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity;
import com.google.android.accessibility.switchaccess.setupwizard.SwitchActionInformationUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Preference activity for Switch Access. */
public class SwitchAccessPreferenceActivity extends PreferenceActivity {
  /*
   * Whether the ui needs to be refreshed when the activity resumes. Refresh is necessary to
   * correctly display settings changed from outside the activity (e.g. in Setup Wizard).
   */
  private static boolean refreshUiOnResume;

  private static PreferenceActivityEventListener preferenceActivityListener;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    refreshUiOnResume = false;
    recreateFragment();

    preferenceActivityListener = SwitchAccessLogger.getOrCreateInstance(this);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (preferenceActivityListener != null) {
      preferenceActivityListener.onPreferenceActivityShown();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (preferenceActivityListener != null) {
      preferenceActivityListener.onPreferenceActivityHidden();
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (refreshUiOnResume) {
      recreateFragment();
      refreshUiOnResume = false;
    }
  }

  @Override
  public void onDestroy() {
    SwitchAccessLogger analytics = SwitchAccessLogger.getInstanceIfExists();
    if (analytics != null) {
      analytics.stop(this);
    }

    SwitchAccessPreferenceCache.shutdownIfInitialized(this);
    super.onDestroy();
  }

  /**
   * Updates the preference activity event listener with a custom listener class.
   *
   * @param listener The preference activity event listener to notify when a preference activity
   *     event happened
   */
  @VisibleForTesting
  void setPreferenceActivityListener(PreferenceActivityEventListener listener) {
    preferenceActivityListener = listener;
  }

  /** Prevent fragment injection. */
  @Override
  protected boolean isValidFragment(String fragmentName) {
    return fragmentName.equals(SwitchAccessPreferenceFragment.class.getName());
  }

  private void recreateFragment() {
    getFragmentManager()
        .beginTransaction()
        .replace(android.R.id.content, new SwitchAccessPreferenceFragment())
        .commit();
  }

  /** Fragment that contains user preferences. */
  public static class SwitchAccessPreferenceFragment extends PreferenceFragment
      implements SwitchAccessPreferenceChangedListener {

    private static final String TTS_SETTINGS_INTENT = "com.android.settings.TTS_SETTINGS";

    private static final String PRIVACY_POLICY_URL = "https://www.google.com/policies/privacy/";

    private static final String SCREEN_NAME_HELP_AND_FEEDBACK = "Help & feedback";

    private static final int SINGLE_VALUE = 1;

    private static final int MANY_VALUE = 2;

    private static final double PRECISION = 0.01;

    /**
     * Keeps track of any removed preferences that may need to be re-added at a later time. There
     * isn't a way to dynamically hide and show preferences as they are needed, so storing removed
     * preferences is necessary to show them at a later time without rebuilding the fragment. We
     * want to re-add preferences back because waiting until we can recreate the fragment can result
     * in stale values. We only need to keep track of a removed preference if another preference
     * change would cause the removed preference to be re-added.
     */
    private HashMap<Integer, Preference> removedPreferenceMap = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.switch_access_preferences);

      /* Switch Access setup wizard button */
      final Preference beginSwitchAccessSetupPref =
          findPreference(R.string.pref_begin_switchaccess_setup_key);
      beginSwitchAccessSetupPref.setOnPreferenceClickListener(
          preference -> {
            Context context = getActivity();
            Intent intent = new Intent(context, SetupWizardActivity.class);
            getActivity().startActivity(intent);
            return true;
          });

      assignTtsSettingsIntentOrRemovePref();

      // Configure Auto-scan summaries
      configureDelayPrefSummary(
          R.string.pref_key_auto_scan_time_delay,
          R.string.pref_auto_scan_time_delay_default_value,
          false);
      configureDelayPrefSummary(
          R.string.pref_key_start_scan_delay, R.string.pref_start_scan_delay_default_value, true);
      configureIntPrefSummary(
          R.string.pref_key_point_scan_and_autoscan_loop_count,
          R.string.pref_point_scan_and_autoscan_loop_count_default,
          R.plurals.label_num_scanning_loops_format,
          1);
      configureDelayPrefSummary(
          R.string.pref_key_switch_access_spoken_feedback_maximum_time_per_item,
          R.integer.pref_maximum_time_per_item_default_value_seconds,
          false);

      // Configure Point scan summaries
      configurePrefSummary(
          R.string.pref_key_point_scan_line_speed,
          R.string.pref_point_scan_line_speed_default,
          R.plurals.line_speed_summary_format,
          false);
      PreferenceScreen pointScanScreen =
          (PreferenceScreen) findPreference(R.string.pref_category_point_scan_key);
      configureDelayPrefSummary(
          pointScanScreen.findPreference(getString(R.string.pref_key_start_scan_delay)),
          R.string.pref_start_scan_delay_default_value,
          true);
      configureIntPrefSummary(
          pointScanScreen.findPreference(
              getString(R.string.pref_key_point_scan_and_autoscan_loop_count)),
          R.string.pref_point_scan_and_autoscan_loop_count_default,
          R.plurals.label_num_scanning_loops_format,
          1);

      // Configure debounce time summary
      configureDelayPrefSummary(
          R.string.pref_key_debounce_time, R.string.pref_debounce_time_default, true);

      adjustAutoSelectPref();
      adjustHighlightingPrefs();
      adjustKeysForScanning();
      adjustPointScanPrefs();

      adjustLinearScanEverywherePrefs();
      adjustAutoscanPrefs();
      adjustSpokenFeedbackPrefs();

      // Add listener to "Help & feedback" preference.
      Preference helpAndFeedbackPreference = findPreference(R.string.pref_help_feedback_key);
      helpAndFeedbackPreference.setOnPreferenceClickListener(
          preference -> {
            HelpUtils.launchHelp(getActivity());
            SwitchAccessLogger analytics = SwitchAccessLogger.getInstanceIfExists();
            if (analytics != null) {
              analytics.onScreenShown(SCREEN_NAME_HELP_AND_FEEDBACK);
            }
            return true;
          });

      // Add an action to open the privacy policy
      Preference privacyPolicyPreference = findPreference(R.string.pref_key_privacy_policy);
      Uri privacyPolicyUri = Uri.parse(PRIVACY_POLICY_URL);
      Intent privacyPolicyIntent = new Intent(Intent.ACTION_VIEW, privacyPolicyUri);
      privacyPolicyPreference.setIntent(privacyPolicyIntent);

      // Add a preference change listener, so we can adjust preferences when they are changed from
      // both inside and outside the preference activity.
      SwitchAccessPreferenceUtils.registerSwitchAccessPreferenceChangedListener(
          getActivity(), this);
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      SwitchAccessPreferenceUtils.unregisterSwitchAccessPreferenceChangedListener(this);
    }

    /**
     * Handle the preferences that can either affect the state of other preferences or can be set
     * from outside the Preferences activity.
     */
    @Override
    public void onPreferenceChanged(SharedPreferences prefs, String key) {
      if (!isAdded()) {
        // If the fragment isn't added to an activity, ignore any preference changes. This should
        // only happen when a preference change happens while the fragment is in the process of
        // shutting down.
        return;
      }

      if (preferenceActivityListener != null) {
        preferenceActivityListener.onPreferenceChanged(key);
      }

      // In general, we can recreate the activity when preferences change. However, the toggle
      // preferences (e.g. Auto-select) should be handled differently. This is because after the
      // activity is recreated, the screen doesn't change much and the tree is not rebuilt. So, the
      // corresponding NodeInfoCompats for these toggle preferences will not be updated, causing
      // these
      // preferences to be uncheckable.

      // Handle the Auto-select preference.
      if (TextUtils.equals(
          key, getString(R.string.switch_access_choose_action_global_menu_behavior_key))) {
        SwitchPreference autoSelectPreference =
            (SwitchPreference) findPreference(R.string.switch_access_choose_action_auto_select_key);
        autoSelectPreference.setChecked(
            SwitchAccessPreferenceUtils.isAutoselectEnabled(getActivity()));
        return;
      }

      // Handle the Point-scan preference.
      if (TextUtils.equals(key, getString(R.string.pref_key_point_scan_enabled))) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          // Point scan screen will only be shown if the build version is larger or equal to N.
          SwitchPreference pointScanPreference =
              (SwitchPreference) findPreference(R.string.pref_key_point_scan_enabled);
          pointScanPreference.setChecked(
              SwitchAccessPreferenceUtils.isPointScanEnabled(getActivity()));

          adjustPointScanPrefs();
        }
        return;
      }

      // Handle the Auto-scan preference.
      if (TextUtils.equals(key, getString(R.string.pref_key_auto_scan_enabled))) {
        SwitchPreference autoScanPreference =
            (SwitchPreference) findPreference(R.string.pref_key_auto_scan_enabled);
        autoScanPreference.setChecked(SwitchAccessPreferenceUtils.isAutoScanEnabled(getActivity()));

        adjustAutoscanPrefs();
        return;
      }

      // Handle preferences that are mirrored in Auto-scan and Point scan settings.
      if (TextUtils.equals(key, getString(R.string.pref_key_start_scan_delay))) {
        updateStartScanDelayPrefs();
        return;
      }
      if (TextUtils.equals(key, getString(R.string.pref_key_point_scan_and_autoscan_loop_count))) {
        updateLoopCountPrefs();
        return;
      }

      if (TextUtils.equals(key, getString(R.string.pref_key_switch_access_spoken_feedback))) {
        adjustSpokenFeedbackPrefs();
        return;
      }

      String[] colorPrefKeys =
          getContext()
              .getResources()
              .getStringArray(R.array.switch_access_highlight_color_pref_keys);
      for (String element : colorPrefKeys) {
        if (TextUtils.equals(key, element)) {
          adjustKeysForScanning();
        }
      }

      // Handle the scanning method preference.
      if (TextUtils.equals(key, getString(R.string.pref_scanning_methods_key))) {
        ScanningMethodPreference scanningMethodPreference =
            (ScanningMethodPreference) findPreference(key);
        scanningMethodPreference.notifyChanged();
        adjustHighlightingPrefs();
        adjustKeysForScanning();
      }

      // Handle the list of preferences that can affect the keys assigned for scanning.
      int[] prefsThatTriggerRecreate = {
        R.string.pref_key_mapped_to_click_key,
        R.string.pref_key_mapped_to_next_key,
        R.string.pref_key_mapped_to_switch_3_key,
        R.string.pref_key_mapped_to_switch_4_key,
        R.string.pref_key_mapped_to_switch_5_key
      };
      for (int prefThatTriggersRecreate : prefsThatTriggerRecreate) {
        if (TextUtils.equals(getString(prefThatTriggersRecreate), key)) {
          refreshUiOnResume = true;
          return;
        }
      }
    }

    private void assignTtsSettingsIntentOrRemovePref() {
      PreferenceScreen preferenceScreen =
          (PreferenceScreen)
              findPreference(R.string.pref_key_category_switch_access_speech_sound_vibration);
      Preference ttsSettingsPreference =
          findPreference(R.string.pref_key_switch_access_tts_settings);

      if (preferenceScreen == null || ttsSettingsPreference == null) {
        return;
      }

      Intent ttsSettingsIntent = new Intent(TTS_SETTINGS_INTENT);
      ttsSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (canHandleIntent(ttsSettingsIntent)) {
        ttsSettingsPreference.setIntent(ttsSettingsIntent);
      } else {
        // We should remove the preference if there is no TTS Settings intent filter in settings
        // app.
        preferenceScreen.removePreference(ttsSettingsPreference);
      }
    }

    private boolean canHandleIntent(Intent intent) {
      Activity activity = getActivity();
      if (activity == null) {
        return false;
      }

      PackageManager manager = activity.getPackageManager();
      List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
      return (infos != null) && !infos.isEmpty();
    }

    private void configureDelayPrefSummary(
        int delayPrefResource, int defaultValueResource, final boolean valueCanBeZero) {
      configurePrefSummary(
          delayPrefResource,
          defaultValueResource,
          R.plurals.time_delay_summary_format,
          valueCanBeZero);
    }

    private void configureDelayPrefSummary(
        Preference delayPref, int defaultValueResource, final boolean valueCanBeZero) {
      configurePrefSummary(
          delayPref, defaultValueResource, R.plurals.time_delay_summary_format, valueCanBeZero);
    }

    private void configurePrefSummary(
        int prefResource,
        int defaultValueResource,
        int pluralsFormat,
        final boolean valueCanBeZero) {
      configurePrefSummary(
          findPreference(prefResource), defaultValueResource, pluralsFormat, valueCanBeZero);
    }

    private void configurePrefSummary(
        Preference pref,
        int defaultValueResource,
        int pluralsFormat,
        final boolean valueCanBeZero) {
      double currentValue;
      SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getActivity());
      try {
        currentValue =
            Double.parseDouble(prefs.getString(pref.getKey(), getString(defaultValueResource)));
      } catch (NumberFormatException e) {
        currentValue = Double.parseDouble(getString(defaultValueResource));
      }
      final int count =
          Math.abs(currentValue - SINGLE_VALUE) < PRECISION ? SINGLE_VALUE : MANY_VALUE;
      pref.setSummary(getResources().getQuantityString(pluralsFormat, count, currentValue));

      pref.setOnPreferenceChangeListener(
          new OnPreferenceChangeListener() {
            /**
             * Update the current summary of the given preference. The newValue for the preference
             * is guaranteed to be a positive double (or an empty string which is not handled).
             */
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValueObject) {
              final String newValueString = newValueObject.toString();
              if (TextUtils.isEmpty(newValueString)) {
                return false;
              }
              try {
                final double newValue = Double.parseDouble(newValueString);
                if (!valueCanBeZero && (newValue <= 0)) {
                  return false;
                }
                final int count =
                    Math.abs(newValue - SINGLE_VALUE) < PRECISION ? SINGLE_VALUE : MANY_VALUE;
                preference.setSummary(
                    getResources().getQuantityString(pluralsFormat, count, newValue));
                return true;
              } catch (NumberFormatException e) {
                return false;
              }
            }
          });

      if (pref instanceof EditTextPreference) {
        setOnPreferenceClickListenerForEditTextPreferenceAndUpdateHeight((EditTextPreference) pref);
      }
    }

    private void configureIntPrefSummary(
        int prefResource,
        int defaultValueResource,
        final int pluralsFormatResource,
        final int minValue) {
      configureIntPrefSummary(
          findPreference(prefResource), defaultValueResource, pluralsFormatResource, minValue);
    }

    private void configureIntPrefSummary(
        Preference pref,
        int defaultValueResource,
        final int pluralsFormatResource,
        final int minValue) {
      SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getActivity());
      int currentValue;
      try {
        currentValue =
            Integer.parseInt(prefs.getString(pref.getKey(), getString(defaultValueResource)));
      } catch (NumberFormatException e) {
        currentValue = Integer.parseInt(getString(defaultValueResource));
      }
      pref.setSummary(
          getResources().getQuantityString(pluralsFormatResource, currentValue, currentValue));

      pref.setOnPreferenceChangeListener(
          new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValueObject) {
              final String newValueString = newValueObject.toString();
              if (TextUtils.isEmpty(newValueString)) {
                return false;
              }
              try {
                int newValue = Integer.parseInt(newValueString);
                if (newValue < minValue) {
                  return false;
                }
                preference.setSummary(
                    getResources().getQuantityString(pluralsFormatResource, newValue, newValue));
                return true;
              } catch (NumberFormatException e) {
                return false;
              }
            }
          });

      if (pref instanceof EditTextPreference) {
        setOnPreferenceClickListenerForEditTextPreferenceAndUpdateHeight((EditTextPreference) pref);
      }
    }

    private void adjustAutoSelectPref() {
      SwitchPreference autoselectPreference =
          (SwitchPreference) findPreference(R.string.switch_access_choose_action_auto_select_key);

      autoselectPreference.setChecked(
          SwitchAccessPreferenceUtils.isAutoselectEnabled(getActivity()));

      autoselectPreference.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            SwitchAccessPreferenceUtils.setAutoselectEnabled(getActivity(), (boolean) newValue);
            return true;
          });
    }

    /*
     * Adjust the highlighting preferences to show appropriate values for group selection or
     * row-column.
     */
    private void adjustHighlightingPrefs() {
      PreferenceCategory customizeScanningCategory =
          (PreferenceCategory) findPreference(R.string.pref_category_display_and_sound_key);
      int[] highlightPrefKeys = {
        R.string.pref_highlight_0_key,
        R.string.pref_highlight_1_key,
        R.string.pref_highlight_2_key,
        R.string.pref_highlight_3_key,
        R.string.pref_highlight_4_key
      };
      if (SwitchAccessPreferenceUtils.isGroupSelectionEnabled(getActivity())) {
        // Configure the switch names. User-facing numbers start at 1
        addPreferenceIfPreviouslyRemoved(customizeScanningCategory, R.string.pref_highlights_key);
        for (int i = 0; i < highlightPrefKeys.length; i++) {
          findPreference(highlightPrefKeys[i])
              .setTitle(getString(R.string.option_scan_switch_format, i + 1));
        }
        removeAndSavePreference(customizeScanningCategory, R.string.pref_standard_highlight_key);
      } else {
        addPreferenceIfPreviouslyRemoved(
            customizeScanningCategory, R.string.pref_standard_highlight_key);
        removeAndSavePreference(customizeScanningCategory, R.string.pref_highlights_key);
      }
    }

    /*
     * If group selection is disabled, remove all keys related to it. Keep all preferences with
     * keys assigned (otherwise there's no way to clear those assignments). If group selection
     * is enabled, assign all titles for the applicable key assignments.
     */
    private void adjustKeysForScanning() {
      int[] groupSelectionKeyAssignmentKeys = {
        R.string.pref_key_mapped_to_click_key,
        R.string.pref_key_mapped_to_next_key,
        R.string.pref_key_mapped_to_switch_3_key,
        R.string.pref_key_mapped_to_switch_4_key,
        R.string.pref_key_mapped_to_switch_5_key
      };

      PreferenceCategory movementAndSelectionCategory = getMovementAndSelectionCategory();
      if (!SwitchAccessPreferenceUtils.isGroupSelectionEnabled(getActivity())) {
        // Ensure that the title and summary are correct for the click and next preferences. These
        // may have changed if the scanning method was previously changed.
        Preference clickPreference = findPreference(groupSelectionKeyAssignmentKeys[0]);
        Preference nextPreference = findPreference(groupSelectionKeyAssignmentKeys[1]);
        clickPreference.setTitle(getString(R.string.action_name_click));
        clickPreference.setSummary(getString(R.string.action_key_summary_click));
        nextPreference.setTitle(getString(R.string.action_name_next));
        nextPreference.setSummary(getString(R.string.action_key_summary_next));
        for (int i = 2; i < groupSelectionKeyAssignmentKeys.length; i++) {
          addPreferenceIfPreviouslyRemoved(
              movementAndSelectionCategory, groupSelectionKeyAssignmentKeys[i]);
          Preference highlightPreference = findPreference(groupSelectionKeyAssignmentKeys[i]);
          highlightPreference.setTitle(getGroupScanSwitchTitleWithColor(i));
          highlightPreference.setSummary(getString(R.string.option_scan_key_summary_format, i + 1));
          removeKeyAssignmentPressIfEmpty(
              movementAndSelectionCategory, groupSelectionKeyAssignmentKeys[i]);
        }
      } else {
        int numSwitchesConfigured = 0;
        for (int i = 0; i < groupSelectionKeyAssignmentKeys.length; i++) {
          int key = groupSelectionKeyAssignmentKeys[i];
          numSwitchesConfigured += prefHasKeyAssigned(getActivity(), getString(key)) ? 1 : 0;
          addPreferenceIfPreviouslyRemoved(movementAndSelectionCategory, key);
          Preference highlightPreference = findPreference(key);
          highlightPreference.setTitle(getGroupScanSwitchTitleWithColor(i));
          highlightPreference.setSummary(getString(R.string.option_scan_key_summary_format, i + 1));
          /* Limit key assignment options to those that are useful given current config */
          if ((i >= 2) && (numSwitchesConfigured < i)) {
            removeKeyAssignmentPressIfEmpty(movementAndSelectionCategory, key);
          }
        }
      }
    }

    /* Show linear scanning everywhere scanning method. */
    private void adjustLinearScanEverywherePrefs() {
      ScanningMethodPreference scanMethodPref = getScanningMethodPreference();
      scanMethodPref.enableScanningMethod(R.id.linear_scanning_radio_button, true);
    }

    /*
     * <p>If auto-scanning is disabled, remove all preferences related to it except for key
     * assignment for actions that are already configured.
     *
     * <p>If auto-scanning is enabled, don't allow group selection to be enabled (unless the
     * corresponding feature flag is enabled).
     *
     * <p>If group selection is enabled, don't allow auto-scanning to be enabled (unless the
     * corresponding feature flag is enabled).
     *
     * <p>If both are enabled and the corresponding feature flag is disabled (which shouldn't be
     * possible), disable group selection.
     */
    private void adjustAutoscanPrefs() {
      PreferenceScreen autoScanScreen =
          (PreferenceScreen) findPreference(R.string.pref_category_auto_scan_key);
      PreferenceCategory movementAndSelectionCategory = getMovementAndSelectionCategory();
      Preference autoScanKeyPreference =
          movementAndSelectionCategory.findPreference(
              getString(R.string.pref_key_mapped_to_auto_scan_key));
      Preference reverseAutoScanKeyPreference =
          movementAndSelectionCategory.findPreference(
              getString(R.string.pref_key_mapped_to_reverse_auto_scan_key));

      boolean isAutoScanEnabled = SwitchAccessPreferenceUtils.isAutoScanEnabled(getActivity());
      if (isAutoScanEnabled) {
        autoScanScreen.setSummary(R.string.preference_on);
        autoScanKeyPreference.setTitle(R.string.title_pref_category_auto_scan);
        reverseAutoScanKeyPreference.setTitle(R.string.action_name_reverse_auto_scan);

        if (FeatureFlags.groupSelectionWithAutoScan()) {
          return;
        }

        if (SwitchAccessPreferenceUtils.isGroupSelectionEnabled(getActivity())) {
          /* If somehow both autoscan and group selection are enabled, turn off group selection. */
          SwitchAccessPreferenceUtils.setScanningMethod(
              getActivity(), R.string.row_col_scanning_key);
        }
      } else {
        autoScanScreen.setSummary(R.string.preference_off);
        autoScanKeyPreference.setTitle(R.string.title_pref_auto_scan_disabled);
        reverseAutoScanKeyPreference.setTitle(R.string.title_pref_reverse_auto_scan_disabled);
        if (SwitchAccessPreferenceUtils.isGroupSelectionEnabled(getActivity())
            && !FeatureFlags.groupSelectionWithAutoScan()) {
          findPreference(R.string.pref_category_auto_scan_key).setEnabled(false);
        }
      }
      ((BaseAdapter) autoScanScreen.getRootAdapter()).notifyDataSetChanged();
      ScanningMethodPreference scanMethodsPref = getScanningMethodPreference();
      scanMethodsPref.enableScanningMethod(R.string.group_selection_key, !isAutoScanEnabled);
    }

    /*
     * Disable all point scanning preferences if the device does not support gesture dispatch
     * (that is, if the Android version is below N).
     */
    private void adjustPointScanPrefs() {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        // Remove point scan screen
        PreferenceCategory customizeScanningCategory =
            (PreferenceCategory) findPreference(R.string.pref_category_customize_scanning_key);
        customizeScanningCategory.removePreference(
            findPreference(R.string.pref_category_point_scan_key));
      } else {
        SwitchPreference pointScanPreference =
            (SwitchPreference) findPreference(R.string.pref_key_point_scan_enabled);
        String enableAnimationsMessage =
            ((VERSION.SDK_INT >= VERSION_CODES.O) && !ValueAnimator.areAnimatorsEnabled())
                ? getString(R.string.point_scan_enable_animations_message)
                : "";
        String pointScanPreferenceSummary =
            getString(R.string.summary_pref_point_scan, enableAnimationsMessage);
        pointScanPreference.setSummary(pointScanPreferenceSummary);
        pointScanPreference.setEnabled(
            (VERSION.SDK_INT < VERSION_CODES.O) || ValueAnimator.areAnimatorsEnabled());

        // Make sure the subtitle reflects Point scan state
        PreferenceScreen pointScanScreen =
            (PreferenceScreen) findPreference(R.string.pref_category_point_scan_key);
        if (SwitchAccessPreferenceUtils.isPointScanEnabled(getActivity())) {
          pointScanScreen.setSummary(R.string.preference_on);
        } else {
          pointScanScreen.setSummary(R.string.preference_off);
        }
        ((BaseAdapter) pointScanScreen.getRootAdapter()).notifyDataSetChanged();
      }
    }

    private void adjustSpokenFeedbackPrefs() {
      PreferenceScreen spokenFeedbackScreen =
          (PreferenceScreen)
              findPreference(R.string.pref_key_category_switch_access_speech_sound_vibration);
      if (SwitchAccessPreferenceUtils.isSpokenFeedbackEnabled(getActivity())) {
        spokenFeedbackScreen.setSummary(R.string.preference_on);
      } else {
        spokenFeedbackScreen.setSummary(R.string.preference_off);
      }
      ((BaseAdapter) spokenFeedbackScreen.getRootAdapter()).notifyDataSetChanged();
    }

    private void removeKeyAssignmentPressIfEmpty(
        PreferenceCategory keyAssignmentScreen, int prefKeyStringId) {
      // Because some preference categories and preferences seem to become null when the activity
      // is recreated due to a preference change, check for nullness.
      if (!prefHasKeyAssigned(getActivity(), getString(prefKeyStringId))
          && (keyAssignmentScreen != null)) {
        removeAndSavePreference(keyAssignmentScreen, prefKeyStringId);
      }
    }

    private void updateStartScanDelayPrefs() {
      String startScanDelaySeconds =
          Float.toString(SwitchAccessPreferenceUtils.getFirstItemScanDelaySeconds(getActivity()));

      PreferenceScreen autoScanScreen =
          (PreferenceScreen) findPreference(R.string.pref_category_auto_scan_key);
      EditTextPreference autoScanStartDelayPreference =
          (EditTextPreference)
              autoScanScreen.findPreference(getString(R.string.pref_key_start_scan_delay));
      updatePreferenceValue(autoScanStartDelayPreference, startScanDelaySeconds);

      if (VERSION.SDK_INT >= VERSION_CODES.N) {
        PreferenceScreen pointScanScreen =
            (PreferenceScreen) findPreference(R.string.pref_category_point_scan_key);
        EditTextPreference pointScanStartDelayPreference =
            (EditTextPreference)
                pointScanScreen.findPreference(getString(R.string.pref_key_start_scan_delay));
        updatePreferenceValue(pointScanStartDelayPreference, startScanDelaySeconds);
      }
    }

    private void updateLoopCountPrefs() {
      String loopCount =
          Integer.toString(SwitchAccessPreferenceUtils.getNumberOfScanningLoops(getActivity()));

      PreferenceScreen autoScanScreen =
          (PreferenceScreen) findPreference(R.string.pref_category_auto_scan_key);
      EditTextPreference autoScanLoopCountPreference =
          (EditTextPreference)
              autoScanScreen.findPreference(
                  getString(R.string.pref_key_point_scan_and_autoscan_loop_count));
      updatePreferenceValue(autoScanLoopCountPreference, loopCount);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PreferenceScreen pointScanScreen =
            (PreferenceScreen) findPreference(R.string.pref_category_point_scan_key);
        EditTextPreference pointScanLoopCountPreference =
            (EditTextPreference)
                pointScanScreen.findPreference(
                    getString(R.string.pref_key_point_scan_and_autoscan_loop_count));
        updatePreferenceValue(pointScanLoopCountPreference, loopCount);
      }
    }

    private void updatePreferenceValue(EditTextPreference preference, String newValue) {
      preference.setText(newValue);
      preference.getOnPreferenceChangeListener().onPreferenceChange(preference, newValue);
    }

    private static boolean prefHasKeyAssigned(Context context, String prefKeyString) {
      return (!KeyAssignmentUtils.getKeyCodesForPreference(context, prefKeyString).isEmpty());
    }

    private PreferenceCategory getMovementAndSelectionCategory() {
      PreferenceScreen keyAssignmentScreen =
          (PreferenceScreen) findPreference(R.string.pref_category_scan_mappings_key);
      return (PreferenceCategory)
          keyAssignmentScreen.findPreference(
              getString(R.string.pref_category_mappings_movement_and_selection_key));
    }

    private ScanningMethodPreference getScanningMethodPreference() {
      PreferenceCategory customizeScanningCategory =
          (PreferenceCategory) findPreference(R.string.pref_category_customize_scanning_key);
      return (ScanningMethodPreference)
          customizeScanningCategory.findPreference(getString(R.string.pref_scanning_methods_key));
    }

    private Preference findPreference(int preferenceKey) {
      return findPreference(getString(preferenceKey));
    }

    private void removeAndSavePreference(PreferenceCategory category, int keyOfPreferenceToRemove) {
      Preference preferenceToRemove = findPreference(keyOfPreferenceToRemove);
      if (preferenceToRemove != null) {
        category.removePreference(preferenceToRemove);
        removedPreferenceMap.put(keyOfPreferenceToRemove, preferenceToRemove);
      }
    }

    private void addPreferenceIfPreviouslyRemoved(
        PreferenceCategory category, int keyOfPreferenceToAdd) {
      Preference previouslyRemovedPreference = removedPreferenceMap.remove(keyOfPreferenceToAdd);
      if ((previouslyRemovedPreference != null) && (findPreference(keyOfPreferenceToAdd) == null)) {
        category.addPreference(previouslyRemovedPreference);
      }
    }

    private void setOnPreferenceClickListenerForEditTextPreferenceAndUpdateHeight(
        EditTextPreference preference) {
      preference.setOnPreferenceClickListener(
          clickedPreference -> {
            EditTextPreference editTextPreference = (EditTextPreference) clickedPreference;
            editTextPreference.getEditText().setSelection(editTextPreference.getText().length());
            return true;
          });

      // The default EditTextPreference height is less than the recommended touch target size, so
      // we need to manually ensure that the minimum height is at least 48px. Since the EditText
      // is created as a preference, the minHeight property can't be set in XML.
      preference
          .getEditText()
          .setMinHeight((int) getResources().getDimension(R.dimen.edit_text_preference_min_height));
    }

    private String getGroupScanSwitchTitleWithColor(int switchIndex) {
      String colorString =
          SwitchActionInformationUtils.getColorStringFromGroupSelectionSwitchNumber(
              getContext(), switchIndex);
      return getString(R.string.option_scan_switch_format_with_color, switchIndex + 1, colorString);
    }
  }

  /** A listener that is notified when a preference activity event happens. */
  public interface PreferenceActivityEventListener {
    /** Called when the preference activity is shown. */
    void onPreferenceActivityShown();

    /** Called when a preference is changed. */
    void onPreferenceChanged(String key);

    /** Called when the preference activity is hidden. */
    void onPreferenceActivityHidden();
  }
}
