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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Preference activity for switch access.
 *
 * <p>PreferenceActivity contains various deprecated methods because PreferenceFragment is
 * preferred. PreferenceFragment, however, can only be added to activities, not services.
 */
@SuppressWarnings("deprecation")
public class SwitchAccessPreferenceActivity extends PreferenceActivity
    implements OnPreferenceChangeListener {

  /** These are the IDs, in order, of the key assignment preferences for option scanning */
  public static final int[] OPTION_SCAN_SWITCH_CONFIG_IDS = {
    R.string.pref_key_mapped_to_click_key, R.string.pref_key_mapped_to_next_key,
    R.string.pref_key_mapped_to_switch_3_key, R.string.pref_key_mapped_to_switch_4_key,
    R.string.pref_key_mapped_to_switch_5_key
  };

  private static final String PRIVACY_POLICY_URL = "http://www.google.com/policies/privacy/";

  private static final String SCREEN_NAME_HELP_AND_FEEDBACK = "Help & feedback";

  private static final int SINGLE_VALUE = 1;

  private static final int MANY_VALUE = 2;

  private static final double PRECISION = 0.01;

  private static final int MILLISECONDS_PER_SECOND = 1000;

  /*
   * Whether the ui needs to be refreshed when the activity resumes. Refresh is necessary to
   * correctly display settings changed from outside the activity (e.g. in Setup Wizard).
   */
  private static boolean mRefreshUiOnResume;

  /**
   * Check if option scanning is enabled
   *
   * @param context The current context
   * @return {@code true} if option scanning is enabled in the preferences, {@code false} otherwise
   */
  public static boolean isOptionScanningEnabled(Context context) {
    String optionScanKey = context.getString(R.string.option_scanning_key);
    String scanPref =
        SharedPreferencesUtils.getSharedPreferences(context)
            .getString(
                context.getString(R.string.pref_scanning_methods_key),
                context.getString(R.string.pref_scanning_methods_default));
    return TextUtils.equals(scanPref, optionScanKey);
  }

  /**
   * Check if auto-scanning is enabled
   *
   * @param context The current context
   * @return {@code true} if auto scan is enabled in the preferences, {@code false} otherwise
   */
  public static boolean isAutoScanEnabled(Context context) {
    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_auto_scan_enabled),
        Boolean.parseBoolean(context.getString(R.string.pref_auto_scan_default_value)));
  }

  /**
   * Check whether point scanning is currently enabled.
   *
   * @param context The current context
   * @return {@code true} if point scanning is currently enabled
   */
  public static boolean isPointScanEnabled(Context context) {
    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_point_scan_enabled),
        context.getResources().getBoolean(R.bool.pref_point_scan_enabled_default_value));
  }

  /**
   * Check if Nomon clocks are enabled.
   *
   * @param context The current context
   * @return {@code true} if Nomon clocks are enabled in the preferences, {@code false} otherwise
   */
  public static boolean areNomonClocksEnabled(Context context) {
    if (!FeatureFlags.nomonClocks()) {
      return false;
    }

    String nomonClockKey = context.getString(R.string.nomon_clocks_key);
    String scanPref =
        SharedPreferencesUtils.getSharedPreferences(context)
            .getString(
                context.getString(R.string.pref_scanning_methods_key),
                context.getString(R.string.pref_scanning_methods_default));
    return TextUtils.equals(scanPref, nomonClockKey);
  }

  /**
   * Set whether point scanning is currently enabled.
   *
   * @param context The current context
   * @param enabled {@code true} to enter point scan mode, {@code false} to return to box scanning
   */
  public static void setPointScanEnabled(Context context, boolean enabled) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    prefs
        .edit()
        .putBoolean(context.getString(R.string.pref_key_point_scan_enabled), enabled)
        .apply();
  }

  /**
   * Check whether auto-select is currently enabled.
   *
   * @param context The current context
   * @return {@code true} auto-selecting is controlled from the auto-scan menu, {@code false}
   *     otherwise
   */
  public static boolean isAutoselectEnabled(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    Resources resources = context.getResources();
    String autoselectGlobalMenuPrefValue =
        SharedPreferencesUtils.getStringPref(
            prefs,
            resources,
            R.string.switch_access_choose_action_global_menu_behavior_key,
            R.string.switch_access_pref_choose_action_behavior_default);
    return TextUtils.equals(
        autoselectGlobalMenuPrefValue,
        resources.getString(R.string.switch_access_choose_action_auto_select_key));
  }

  /**
   * Set whether auto-select is enabled.
   *
   * @param context The current context
   * @param enabled {@code true} to enable auto-select when the global menu controls the preference,
   *     {@code false} to disable it.
   */
  public static void setAutoselectEnabled(Context context, boolean enabled) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    String newStringValue =
        (enabled)
            ? context.getString(R.string.switch_access_choose_action_auto_select_key)
            : context.getString(R.string.switch_access_choose_action_show_menu_key);
    prefs
        .edit()
        .putString(
            context.getString(R.string.switch_access_choose_action_global_menu_behavior_key),
            newStringValue)
        .apply();
  }

  /**
   * Return whether spoken feedback is enabled.
   *
   * @param context The current context
   * @return {@code true} if spoken feedback is enabled
   */
  public static boolean isSpokenFeedbackEnabled(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_switch_access_spoken_feedback), false);
  }

  /**
   * Returns whether non-actionable items should be included when scanning items on the screen.
   * Users who can read text on the screen only need to use Switch Access to scan actionable items.
   * However, some users (e.g. blind or low vision users) rely on spoken feedback to get information
   * about the screen. In addition to hearing information about actionable items, they need to hear
   * information about non-actionable items in order to use the application.
   *
   * <p>Because users cannot perform actions on non-actionable items, this feature is automatically
   * disabled when spoken feedback is disabled.
   *
   * @param context The current context
   * @return {@code true} if non-actionable items should be scanned; {@code false} otherwise
   */
  public static boolean shouldScanNonActionableItems(Context context) {
    if (!FeatureFlags.scanNonActionableItems()) {
      return false;
    }

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return (isSpokenFeedbackEnabled(context)
        && prefs.getBoolean(context.getString(R.string.pref_key_scan_non_actionable_items), false));
  }

  /**
   * Return whether the first and last highlighted item should be spoke when providing spoken
   * feedback. This setting is only relevant when more than one item is highlighted.
   *
   * @param context The current context
   * @return {@code true} if the first and last items should be spoken for groups of items
   */
  public static boolean shouldSpeakFirstAndLastItem(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_switch_access_speak_first_last_item), true);
  }

  /**
   * Return whether the number of highlighted elements should be included when providing spoken
   * feedback. This setting is only relevant when more than one item is highlighted.
   *
   * @param context The current context
   * @return {@code true} if the number of highlighted elements should be spoken
   */
  public static boolean shouldSpeakNumberOfItems(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_switch_access_speak_number_of_items), true);
  }

  /**
   * Return whether the all highlighted items should be included when providing spoken feedback.
   * This setting is only relevant when more than one item is highlighted.
   *
   * @param context The current context
   * @return {@code true} if all highlighted elements should be spokem
   */
  public static boolean shouldSpeakAllItems(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_switch_access_speak_all_items), true);
  }

  /**
   * Return whether autoscan should wait until feedback is complete before moving highlight to the
   * next item or group of items.
   *
   * @param context The current context
   * @return {@code true} if autoscan should wait until feedabck is complete before moving highlight
   *     to the next item or group of items
   */
  public static boolean shouldFinishSpeechBeforeContinuingScan(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_switch_access_spoken_feedback_finish_speech), true);
  }

  /**
   * Check whether auto-start scanning is enabled.
   *
   * @param context The current context
   * @return {@code true} if auto start scanning is currently enabled
   */
  public static boolean isAutostartScanEnabled(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getBooleanPref(
        prefs,
        context.getResources(),
        R.string.switch_access_auto_start_scan_key,
        R.bool.switch_access_auto_start_scan_default);
  }

  /**
   * Check whether press on release is enabled. When enabled, presses occur when a key is released
   * (key up). When disabled, presses occur when a key is released (key down).
   *
   * @param context The current context
   * @return {@code true} if presses should occur on key up instead of key down
   */
  public static boolean isPressOnReleaseEnabled(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return prefs.getBoolean(
        context.getString(R.string.pref_key_switch_access_press_on_release), false);
  }

  /** Get the auto scan delay. */
  public static int getAutoScanDelayMs(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    float delaySeconds;
    try {
      delaySeconds =
          SharedPreferencesUtils.getFloatFromStringPref(
              prefs,
              context.getResources(),
              R.string.pref_key_auto_scan_time_delay,
              R.string.pref_auto_scan_time_delay_default_value);
    } catch (NumberFormatException e) {
      delaySeconds =
          (float)
              Double.parseDouble(
                  context.getString(R.string.pref_auto_scan_time_delay_default_value));
    }
    return (int) (delaySeconds * MILLISECONDS_PER_SECOND);
  }

  /** Get the extra time to spend on the first element at the start of scanning. */
  public static int getFirstItemScanDelayMs(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    float delaySeconds =
        SharedPreferencesUtils.getFloatFromStringPref(
            prefs,
            context.getResources(),
            R.string.pref_key_start_scan_delay,
            R.string.pref_start_scan_delay_default_value);
    return (int) (delaySeconds * MILLISECONDS_PER_SECOND);
  }

  /** Get debounce time. */
  public static int getDebounceTimeMs(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    float delaySeconds =
        SharedPreferencesUtils.getFloatFromStringPref(
            prefs,
            context.getResources(),
            R.string.pref_key_debounce_time,
            R.string.pref_debounce_time_default);
    return (int) delaySeconds * MILLISECONDS_PER_SECOND;
  }

  /** Get the current point scan line speed. */
  public static float getPointScanLineSpeed(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    float lineSpeed;
    try {
      lineSpeed =
          SharedPreferencesUtils.getFloatFromStringPref(
              prefs,
              context.getResources(),
              R.string.pref_key_point_scan_line_speed,
              R.string.pref_point_scan_line_speed_default);
    } catch (NumberFormatException e) {
      lineSpeed = Integer.parseInt(context.getString(R.string.pref_point_scan_line_speed_default));
    }
    return lineSpeed;
  }

  /** Get the current scanning animation repeat count. */
  public static int getNumberOfScanningLoops(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getIntFromStringPref(
        prefs,
        context.getResources(),
        R.string.pref_key_point_scan_and_autoscan_loop_count,
        R.string.pref_point_scan_and_autoscan_loop_count_default);
  }

  /** Get the number of switches configured for option scanning, Next, and Select. */
  public static int getNumSwitches(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    int numSwitchesConfigured = 0;
    while ((OPTION_SCAN_SWITCH_CONFIG_IDS.length > numSwitchesConfigured)
        && !KeyAssignmentUtils.getKeyCodesForPreference(
                prefs, context.getString(OPTION_SCAN_SWITCH_CONFIG_IDS[numSwitchesConfigured]))
            .isEmpty()) {
      numSwitchesConfigured++;
    }
    return numSwitchesConfigured;
  }

  /** Get the time for any group of Nomon clocks to be selectable. */
  public static float getNomonClockTimeDelayMs(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getFloatFromStringPref(
        prefs,
        context.getResources(),
        R.string.pref_key_nomon_clock_time_delay,
        R.string.pref_nomon_clock_time_delay_default_value);
  }

  /** Get the number of user-desired groups for Nomon clock scanning. */
  public static int getNumNomonClockGroups(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getIntFromStringPref(
        prefs,
        context.getResources(),
        R.string.pref_key_nomon_clock_groups,
        R.string.pref_nomon_clock_groups_default_value);
  }

  /** Get all the highlight paints. */
  public static Paint[] getHighlightPaints(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    String[] highlightColorPrefKeys =
        context.getResources().getStringArray(R.array.switch_access_highlight_color_pref_keys);
    String[] highlightColorDefaults =
        context.getResources().getStringArray(R.array.switch_access_highlight_color_defaults);
    String[] highlightWeightPrefKeys =
        context.getResources().getStringArray(R.array.switch_access_highlight_weight_pref_keys);
    String defaultWeight = context.getString(R.string.pref_highlight_weight_default);

    Paint[] paints = new Paint[highlightColorPrefKeys.length];
    for (int i = 0; i < highlightColorPrefKeys.length; ++i) {
      String hexStringColor = prefs.getString(highlightColorPrefKeys[i], highlightColorDefaults[i]);
      int color = Integer.parseInt(hexStringColor, 16);
      Paint paint = new Paint();
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(color);
      paint.setAlpha(255);

      String stringWeight = prefs.getString(highlightWeightPrefKeys[i], defaultWeight);
      int weight = Integer.parseInt(stringWeight);
      DisplayMetrics dm = context.getResources().getDisplayMetrics();
      float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, weight, dm);
      paint.setStrokeWidth(strokeWidth);
      paints[i] = paint;
    }
    return paints;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mRefreshUiOnResume = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      getPreferenceManager().setStorageDeviceProtected();
    }

    addPreferencesFromResource(R.xml.switch_access_preferences);

    /* Switch Access setup wizard button */
    final Preference beginSwitchAccessSetupPref =
        findPreference(R.string.pref_begin_switchaccess_setup_key);
    beginSwitchAccessSetupPref.setOnPreferenceClickListener(
        new OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            Context context = getApplicationContext();
            Intent intent = new Intent(context, SetupWizardActivity.class);
            SwitchAccessPreferenceActivity.this.startActivity(intent);
            return true;
          }
        });

    // Set the appropriate summaries for these settings
    configureDelayPrefSummary(
        R.string.pref_key_auto_scan_time_delay,
        R.string.pref_auto_scan_time_delay_default_value,
        false);
    configureDelayPrefSummary(
        R.string.pref_key_start_scan_delay, R.string.pref_start_scan_delay_default_value, true);
    configureDelayPrefSummary(
        R.string.pref_key_debounce_time, R.string.pref_debounce_time_default, true);

    configureIntPrefSummary(
        R.string.pref_key_point_scan_and_autoscan_loop_count,
        R.string.pref_point_scan_and_autoscan_loop_count_default,
        R.plurals.label_num_scanning_loops_format,
        1);

    // Make sure line speed is always a double
    ensurePrefValueIsAlwaysPositiveDouble(R.string.pref_key_point_scan_line_speed);

    /* Set the Nomon clock rotation summary and group number summary */
    configureDelayPrefSummary(
        R.string.pref_key_nomon_clock_time_delay,
        R.string.pref_nomon_clock_time_delay_default_value,
        false);
    configureIntPrefSummary(
        R.string.pref_key_nomon_clock_groups,
        R.string.pref_nomon_clock_groups_default_value,
        R.plurals.label_num_nomon_groups_format,
        2);

    int[] prefsToListenTo = {
      R.string.pref_scanning_methods_key,
      R.string.pref_key_auto_scan_enabled,
      R.string.pref_key_mapped_to_click_key,
      R.string.pref_key_mapped_to_next_key,
      R.string.pref_key_mapped_to_switch_3_key,
      R.string.pref_key_mapped_to_switch_4_key,
      R.string.pref_key_mapped_to_switch_5_key
    };
    for (int prefToListenTo : prefsToListenTo) {
      findPreference(prefToListenTo).setOnPreferenceChangeListener(this);
    }
    adjustHighlightingPrefs();
    adjustKeysForScanning();
    adjustPointScanPrefs();

    final ArrayList<String> scanMethodEntries =
        new ArrayList<>(
            Arrays.asList(
                getResources().getStringArray(R.array.switch_access_scanning_methods_entries)));
    final ArrayList<String> scanMethodEntryValues =
        new ArrayList<>(
            Arrays.asList(
                getResources().getStringArray(R.array.switch_access_scanning_methods_values)));
    adjustAutoscanPrefs(scanMethodEntries, scanMethodEntryValues);
    adjustNomonClockPrefs(scanMethodEntries, scanMethodEntryValues);

    adjustScanningNonActionableItemsPrefs();

    // Add listener to "Help & feedback" preference.
    Preference helpAndFeedbackPreference = findPreference(R.string.pref_help_feedback_key);
    helpAndFeedbackPreference.setOnPreferenceClickListener(
        new OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            HelpUtils.launchHelp(SwitchAccessPreferenceActivity.this);
            Analytics analytics = Analytics.getInstanceIfExists();
            if (analytics != null) {
              analytics.onScreenShown(SCREEN_NAME_HELP_AND_FEEDBACK);
            }
            return true;
          }
        });

    // Add an action to open the privacy policy
    Preference privacyPolicyPreference = findPreference(R.string.pref_key_privacy_policy);
    Uri privacyPolicyUri = Uri.parse(PRIVACY_POLICY_URL);
    Intent privacyPolicyIntent = new Intent(Intent.ACTION_VIEW, privacyPolicyUri);
    privacyPolicyPreference.setIntent(privacyPolicyIntent);
  }

  /**
   * Set whether the ui should be refreshed when the preferences activity resumes. In order to
   * display changes made to preferences from outside the activity, the ui must be refreshed.
   */
  public static void setRefreshUiOnResume(boolean shouldRefresh) {
    mRefreshUiOnResume = shouldRefresh;
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (mRefreshUiOnResume) {
      recreate();
    }
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    recreate();
    return true;
  }

  /** Prevent fragment injection. */
  @Override
  protected boolean isValidFragment(String fragmentName) {
    return false;
  }

  private void configureDelayPrefSummary(
      int delayPrefResource, int defaultValueResource, final boolean valueCanBeZero) {
    final Preference delayPref = findPreference(delayPrefResource);
    double currentValue;
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(this);
    try {
      currentValue =
          Double.parseDouble(
              prefs.getString(getString(delayPrefResource), getString(defaultValueResource)));
    } catch (NumberFormatException e) {
      currentValue = Double.parseDouble(getString(defaultValueResource));
    }
    final int count = Math.abs(currentValue - SINGLE_VALUE) < PRECISION ? SINGLE_VALUE : MANY_VALUE;
    delayPref.setSummary(
        getResources().getQuantityString(R.plurals.time_delay_summary_format, count, currentValue));

    delayPref.setOnPreferenceChangeListener(
        new OnPreferenceChangeListener() {
          /**
           * Update the current summary of the delay preference. The newValue for the delay is
           * guaranteed to be an integer (or an empty string which is not handled).
           */
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValueObject) {
            final String newValueString = newValueObject.toString();
            if (TextUtils.isEmpty(newValueString)) {
              return false;
            }
            try {
              final double newValue = Double.parseDouble(newValueString);
              if (!valueCanBeZero && !(newValue > 0)) {
                return false;
              }
              final int count =
                  Math.abs(newValue - SINGLE_VALUE) < PRECISION ? SINGLE_VALUE : MANY_VALUE;
              preference.setSummary(
                  getResources()
                      .getQuantityString(R.plurals.time_delay_summary_format, count, newValue));
              return true;
            } catch (NumberFormatException e) {
              return false;
            }
          }
        });
  }

  private void ensurePrefValueIsAlwaysPositiveDouble(int delayPrefResource) {
    final Preference delayPref = findPreference(delayPrefResource);
    delayPref.setOnPreferenceChangeListener(
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValueObject) {
            final String newValueString = newValueObject.toString();
            if (TextUtils.isEmpty(newValueString)) {
              return false;
            }
            try {
              final double newValue = Double.parseDouble(newValueString);
              if (newValue <= 0) {
                return false;
              }
              return true;
            } catch (NumberFormatException e) {
              return false;
            }
          }
        });
  }

  private void configureIntPrefSummary(
      int prefResource, int defaultValueResource, final int pluralsFormatResource, final int minValue) {
    final Preference pref = findPreference(prefResource);

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(this);
    int currentValue;
    try {
      currentValue =
          Integer.parseInt(
              prefs.getString(getString(prefResource), getString(defaultValueResource)));
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
  }

  /*
   * Adjust the highlighting preferences to show appropriate values for option scanning or
   * row-column.
   */
  private void adjustHighlightingPrefs() {
    PreferenceCategory customizeScanningCategory =
        (PreferenceCategory) findPreference(R.string.pref_category_customize_scanning_key);
    int[] highlightPrefKeys = {
      R.string.pref_highlight_0_key,
      R.string.pref_highlight_1_key,
      R.string.pref_highlight_2_key,
      R.string.pref_highlight_3_key,
      R.string.pref_highlight_4_key
    };
    if (isOptionScanningEnabled(this)) {
      // Configure the switch names. User-facing numbers start at 1
      for (int i = 0; i < highlightPrefKeys.length; i++) {
        findPreference(highlightPrefKeys[i])
            .setTitle(getString(R.string.option_scan_switch_format, i + 1));
      }
      customizeScanningCategory.removePreference(
          findPreference(R.string.pref_standard_highlight_key));
    } else {
      customizeScanningCategory.removePreference(findPreference((R.string.pref_highlights_key)));
    }
  }

  /*
   * If option scanning is disabled, remove all keys related to it. Keep all preferences with
   * keys assigned (otherwise there's no way to clear those assignments). If option scanning
   * or Nomon clocks are enabled, assign all titles for the applicable key assignments.
   */
  private void adjustKeysForScanning() {
    int[] optionScanKeyAssignmentKeys = {
      R.string.pref_key_mapped_to_click_key,
      R.string.pref_key_mapped_to_next_key,
      R.string.pref_key_mapped_to_switch_3_key,
      R.string.pref_key_mapped_to_switch_4_key,
      R.string.pref_key_mapped_to_switch_5_key
    };
    PreferenceScreen keyAssignmentScreen =
        (PreferenceScreen) findPreference(R.string.pref_category_scan_mappings_key);

    if (!isOptionScanningEnabled(this)) {
      for (int i = 2; i < optionScanKeyAssignmentKeys.length; i++) {
        findPreference(optionScanKeyAssignmentKeys[i])
            .setTitle(getString(R.string.option_scan_switch_format, i + 1));
        removeKeyAssignmentPressIfEmpty(keyAssignmentScreen, optionScanKeyAssignmentKeys[i]);
      }
    } else {
      int numSwitchesConfigured = 0;
      for (int i = 0; i < optionScanKeyAssignmentKeys.length; i++) {
        int key = optionScanKeyAssignmentKeys[i];
        numSwitchesConfigured += prefHasKeyAssigned(this, getString(key)) ? 1 : 0;
        findPreference(key).setTitle(getString(R.string.option_scan_switch_format, i + 1));
        /* Limit key assignment options to those that are useful given current config */
        if ((i >= 2) && (numSwitchesConfigured < i)) {
          removeKeyAssignmentPressIfEmpty(keyAssignmentScreen, key);
        }
      }
    }
  }

  /*
   * If auto-scanning is disabled, remove all preferences related to it except for key assignment
   * for actions that are already configured.
   * If auto-scannning is enabled, don't allow option scanning to be enabled.
   * If option scanning is enabled, don't allow auto-scanning to be enabled.
   * If both are enabled (which shouldn't be possible, disable option scanning).
   */
  private void adjustAutoscanPrefs(
      List<String> scanMethodEntries, List<String> scanMethodEntryValues) {
    if (isAutoScanEnabled(this)) {
      ListPreference scanMethodsPref =
          (ListPreference) findPreference(R.string.pref_scanning_methods_key);
      String optionScanKey = getString(R.string.option_scanning_key);
      if (isOptionScanningEnabled(this)) {
        /* If somehow both autoscan and option scan are enabled, turn off option scan */
        scanMethodsPref.setValue(getString(R.string.row_col_scanning_key));
        SharedPreferencesUtils.getSharedPreferences(this)
            .edit()
            .putString(
                getString(R.string.pref_scanning_methods_key),
                getString(R.string.row_col_scanning_key))
            .commit();
      }
      int optionScanIndex = scanMethodEntryValues.indexOf(optionScanKey);
      scanMethodEntries.remove(optionScanIndex);
      scanMethodEntryValues.remove(optionScanIndex);
      scanMethodsPref.setEntries(scanMethodEntries.toArray(new String[scanMethodEntries.size()]));
      scanMethodsPref.setEntryValues(
          scanMethodEntryValues.toArray(new String[scanMethodEntries.size()]));
    } else {
      PreferenceScreen keyAssignmentScreen =
          (PreferenceScreen) findPreference(R.string.pref_category_scan_mappings_key);
      removeKeyAssignmentPressIfEmpty(
          keyAssignmentScreen, R.string.pref_key_mapped_to_auto_scan_key);
      removeKeyAssignmentPressIfEmpty(
          keyAssignmentScreen, R.string.pref_key_mapped_to_reverse_auto_scan_key);
      if (isOptionScanningEnabled(this)) {
        findPreference(R.string.pref_key_auto_scan_enabled).setEnabled(false);
      }
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        findPreference(R.string.pref_key_point_scan_and_autoscan_loop_count)
            .setDependency(getString(R.string.pref_key_auto_scan_enabled));
      }
    }
  }

  /*
   * Disable all point scanning preferences if the device does not support gesture dispatch
   * (that is, if the Android version is below N).
   */
  private void adjustPointScanPrefs() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      PreferenceCategory customizeScanningCategory =
          (PreferenceCategory) findPreference(R.string.pref_category_customize_scanning_key);
      // Remove line speed
      customizeScanningCategory.removePreference(
          findPreference(R.string.pref_key_point_scan_line_speed));
    }
  }

  /*
   * If Nomon Clocks are not enabled, don't show the option to change the rotation time
   * or number of groups.
   */
  private void adjustNomonClockPrefs(
      List<String> scanMethodEntries, List<String> scanMethodEntryValues) {
    if (!FeatureFlags.nomonClocks()) {
      // Remove Nomon Clock scanning method.
      String nomonClocksKey = getString(R.string.nomon_clocks_key);
      int nomonClocksIndex = scanMethodEntryValues.indexOf(nomonClocksKey);
      scanMethodEntries.remove(nomonClocksIndex);
      scanMethodEntryValues.remove(nomonClocksIndex);

      ListPreference scanMethodsPref =
          (ListPreference) findPreference(R.string.pref_scanning_methods_key);
      scanMethodsPref.setEntries(scanMethodEntries.toArray(new String[scanMethodEntries.size()]));
      scanMethodsPref.setEntryValues(
          scanMethodEntryValues.toArray(new String[scanMethodEntries.size()]));

      // Remove preferences specific to Nomon Clocks.
      PreferenceCategory customizeScanningCategory =
          (PreferenceCategory) findPreference(R.string.pref_category_customize_scanning_key);
      customizeScanningCategory.removePreference(
          findPreference(R.string.pref_key_nomon_clock_time_delay));
      customizeScanningCategory.removePreference(
          findPreference(R.string.pref_key_nomon_clock_groups));
    } else if (!areNomonClocksEnabled(this)) {
      findPreference(R.string.pref_key_nomon_clock_time_delay).setEnabled(false);
      findPreference(R.string.pref_key_nomon_clock_groups).setEnabled(false);
    } else {
      findPreference(R.string.pref_key_auto_scan_enabled).setEnabled(false);
    }
  }

  /*
   * If the scan non-actionable items feature is disabled, remove the option to scan
   * non-actionable items.
   */
  private void adjustScanningNonActionableItemsPrefs() {
    if (!FeatureFlags.scanNonActionableItems()) {
      PreferenceScreen spokenFeedbackScreen =
          (PreferenceScreen)
              findPreference(R.string.pref_key_category_switch_access_spoken_feedback);
      spokenFeedbackScreen.removePreference(
          findPreference(R.string.pref_key_spoken_feedback_general_settings));
    }
  }

  private void removeKeyAssignmentPressIfEmpty(
      PreferenceScreen keyAssignmentScreen, int prefKeyStringId) {
    if (!prefHasKeyAssigned(this, getString(prefKeyStringId))) {
      keyAssignmentScreen.removePreference(findPreference(prefKeyStringId));
    }
  }

  private static boolean prefHasKeyAssigned(Context context, String prefKeyString) {
    return (!KeyAssignmentUtils.getKeyCodesForPreference(context, prefKeyString).isEmpty());
  }

  private Preference findPreference(int preferenceKey) {
    return findPreference(getString(preferenceKey));
  }
}
