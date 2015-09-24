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

package com.android.switchaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import com.android.talkback.R;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Preference activity for switch access.
 *
 * PreferenceActivity contains various deprecated methods because
 * PreferenceFragment is preferred. PreferenceFragment, however,
 * can only be added to activities, not services.
 */
@SuppressWarnings("deprecation")
public class SwitchAccessPreferenceActivity extends PreferenceActivity implements
        OnPreferenceChangeListener {

    private static final int SINGLE_VALUE = 1;

    private static final int MANY_VALUE = 2;

    private static final double PRECISION = 0.01;

    /**
     * Check if option scanning is enabled
     *
     * @param context The current context
     * @return {@code true} if option scanning is enabled in the preferences, {@code false}
     * otherwise
     */
    public static boolean isOptionScanningEnabled(Context context) {
        String optionScanKey = context.getString(R.string.option_scanning_key);
        String scanPref = PreferenceManager.getDefaultSharedPreferences(context).getString(
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.pref_key_auto_scan_enabled),
                Boolean.parseBoolean(context.getString(R.string.pref_auto_scan_default_value)));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.switch_access_preferences);

        /* Set the summary to be the current value of the preference */
        final Preference autoScanDelayPref =
                findPreference(getString(R.string.pref_key_auto_scan_time_delay));
        double currentValue;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            currentValue = Double.parseDouble(prefs.getString(
                    getString(R.string.pref_key_auto_scan_time_delay),
                    getString(R.string.pref_auto_scan_time_delay_default_value)));
        } catch (NumberFormatException e) {
            currentValue =
                    Double.parseDouble(getString(R.string.pref_auto_scan_time_delay_default_value));
        }
        final int count = Math.abs(currentValue - SINGLE_VALUE)
                < PRECISION ? SINGLE_VALUE : MANY_VALUE;
        autoScanDelayPref.setSummary(getResources().getQuantityString(
                R.plurals.auto_scan_time_delay_summary_format, count, currentValue));

        autoScanDelayPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            /**
             * Update the current summary of the delay preference. The newValue for the delay is
             * guaranteed to be an integer (or an empty string which is not handled).
             */
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String newValueString = newValue.toString();
                if (TextUtils.isEmpty(newValueString)) {
                    return false;
                }
                try {
                    final double value = Double.parseDouble(newValueString);
                    final int count = Math.abs(value - SINGLE_VALUE)
                            < PRECISION ? SINGLE_VALUE : MANY_VALUE;
                    preference.setSummary(getResources().getQuantityString(
                            R.plurals.auto_scan_time_delay_summary_format, count, value));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        });

        int[] prefsToListenTo = {R.string.pref_scanning_methods_key,
                R.string.pref_key_auto_scan_enabled, R.string.pref_key_mapped_to_click_key,
                R.string.pref_key_mapped_to_next_key, R.string.pref_key_mapped_to_switch_3_key,
                R.string.pref_key_mapped_to_switch_4_key, R.string.pref_key_mapped_to_switch_5_key};
        for (int prefToListenTo : prefsToListenTo) {
            findPreference(getString(prefToListenTo)).setOnPreferenceChangeListener(this);
        }
        adjustHighlightingPrefs();
        adjustKeysForScanning();
        adjustAutoscanPrefs();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        recreate();
        return true;
    }

    /*
     * If option scanning is disabled, remove all highlighting prefs except the primary
     * string, which is set in xml.
     */
    private void adjustHighlightingPrefs() {
        int[] highlightPrefKeys = {R.string.pref_highlight_0_key, R.string.pref_highlight_1_key,
                R.string.pref_highlight_2_key, R.string.pref_highlight_3_key,
                R.string.pref_highlight_4_key};
        if (!isOptionScanningEnabled(this)) {
            PreferenceScreen highlightScreen =
                    (PreferenceScreen) findPreference(getString(R.string.pref_highlights_key));
            // Remove all highlight prefs except the first one
            for (int i = 1; i < highlightPrefKeys.length; i++) {
                Preference prefToRemove = findPreference(getString(highlightPrefKeys[i]));
                highlightScreen.removePreference(prefToRemove);
            }
        } else {
            // Rename all prefs to option scanning names. User-facing numbers start at 1
            for (int i = 0; i < highlightPrefKeys.length; i++) {
                findPreference(getString(highlightPrefKeys[i])).setTitle(
                        String.format(getString(R.string.option_scan_switch_format), i + 1));
            }
        }
    }

    /*
     * If option scanning is disabled, remove all keys related to it. Keep all preferences with
     * keys assigned (otherwise there's no way to clear those assignments). If option scanning
     * is enabled, assign all titles for the applicable key assignments.
     */
    private void adjustKeysForScanning() {
        int[] optionScanKeyAssignmentKeys = {R.string.pref_key_mapped_to_click_key,
                R.string.pref_key_mapped_to_next_key, R.string.pref_key_mapped_to_switch_3_key,
                R.string.pref_key_mapped_to_switch_4_key, R.string.pref_key_mapped_to_switch_5_key};
        PreferenceScreen keyAssignmentScreen = (PreferenceScreen) findPreference(
                getString(R.string.pref_category_scan_mappings_key));

        if (!isOptionScanningEnabled(this)) {
            for (int i = 2; i < optionScanKeyAssignmentKeys.length; i++) {
                findPreference(getString(optionScanKeyAssignmentKeys[i])).setTitle(
                        String.format(getString(R.string.option_scan_switch_format), i + 1));
                removeKeyAssignmentPressIfEmpty(
                        keyAssignmentScreen, optionScanKeyAssignmentKeys[i]);
            }
        } else {
            int numSwitchesConfigured = 0;
            for (int i = 0; i < optionScanKeyAssignmentKeys.length; i++) {
                int key = optionScanKeyAssignmentKeys[i];
                numSwitchesConfigured += prefHasKeyAssigned(key) ? 1 : 0;
                findPreference(getString(key)).setTitle(
                        String.format(getString(R.string.option_scan_switch_format), i + 1));
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
    private void adjustAutoscanPrefs() {
        if (isAutoScanEnabled(this)) {
            ListPreference scanMethodsPref = (ListPreference) findPreference(
                    getString(R.string.pref_scanning_methods_key));
            String optionScanKey = getString(R.string.option_scanning_key);
            if (isOptionScanningEnabled(this)) {
                /* If somehow both autoscan and option scan are enabled, turn off option scan */
                scanMethodsPref.setValue(getString(R.string.row_col_scanning_key));
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putString(getString(R.string.pref_scanning_methods_key),
                                getString(R.string.row_col_scanning_key))
                        .commit();
            }
            ArrayList<String> entries = new ArrayList<>(Arrays.asList(
                    getResources().getStringArray(R.array.switch_access_scanning_methods_entries)));
            ArrayList<String> entryValues = new ArrayList<>(Arrays.asList(
                    getResources().getStringArray(R.array.switch_access_scanning_methods_values)));
            int optionScanIndex = entryValues.indexOf(optionScanKey);
            entries.remove(optionScanIndex);
            entryValues.remove(optionScanIndex);
            scanMethodsPref.setEntries(entries.toArray(new String[entries.size()]));
            scanMethodsPref.setEntryValues(entryValues.toArray(new String[entries.size()]));
        } else {
            PreferenceScreen mainPrefScreen = (PreferenceScreen) findPreference(
                    getString(R.string.main_pref_screen_key));
            PreferenceScreen keyAssignmentScreen = (PreferenceScreen) findPreference(
                    getString(R.string.pref_category_scan_mappings_key));
            mainPrefScreen.removePreference(
                    findPreference(getString(R.string.pref_key_auto_scan_time_delay)));
            removeKeyAssignmentPressIfEmpty(keyAssignmentScreen,
                    R.string.pref_key_mapped_to_auto_scan_key);
            removeKeyAssignmentPressIfEmpty(keyAssignmentScreen,
                    R.string.pref_key_mapped_to_reverse_auto_scan_key);
            if (isOptionScanningEnabled(this)) {
                mainPrefScreen.removePreference(
                        findPreference(getString(R.string.pref_key_auto_scan_enabled)));
            }
        }
    }

    /**
     * @return {@code true} if preference was empty and has been removed
     */
    private void removeKeyAssignmentPressIfEmpty(PreferenceScreen keyAssignmentScreen,
            int prefKeyStringId) {
        if (!prefHasKeyAssigned(prefKeyStringId)) {
            keyAssignmentScreen.removePreference(findPreference(getString(prefKeyStringId)));
        }
    }

    private boolean prefHasKeyAssigned(int prefKeyStringId) {
        return (KeyComboPreference
                .getKeyCodesForPreference(this, getString(prefKeyStringId)).size() != 0);
    }
}
