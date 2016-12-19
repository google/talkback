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

package com.android.switchaccess.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import com.android.switchaccess.KeyComboPreference;
import com.android.switchaccess.SwitchAccessPreferenceActivity;
import com.android.talkback.BuildConfig;
import com.android.talkback.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPreferenceManager;
import org.robolectric.util.ActivityController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("deprecation")
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
public class PreferenceActivityTest {

    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();

    private final SharedPreferences mSharedPreferences =
            ShadowPreferenceManager.getDefaultSharedPreferences(mContext);

    private final String mAutoScanKey = mContext.getString(R.string.pref_key_auto_scan_time_delay);

    @Before
    public void setUp() {
        /* Set up preference and key event */
        mSharedPreferences.edit().clear().commit();
    }

    @Test
    public void testOnCreate_autoScanDelayIsDefault() {
        setAutoScanEnabled(true);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        final Preference autoScanDelayPref = activity.findPreference(mAutoScanKey);
        assertEquals("1 Second", autoScanDelayPref.getSummary());
    }

    @Test
    public void testOnCreateWithInvalidAutoScanDelay_shouldUseDefault() {
        setAutoScanEnabled(true);
        mSharedPreferences.edit().putString(mAutoScanKey, ".").commit();
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        final Preference autoScanDelayPref = activity.findPreference(mAutoScanKey);
        assertEquals("1 Second", autoScanDelayPref.getSummary());
    }

    @Test
    public void testOnCreateWithAutoScanDelayHalfSecond_properlyInitialized() {
        setAutoScanEnabled(true);
        mSharedPreferences.edit().putString(mAutoScanKey, "0.5").commit();
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        final Preference autoScanDelayPref = activity.findPreference(mAutoScanKey);
        assertEquals("0.50 Seconds", autoScanDelayPref.getSummary());
    }

    @Test
    public void testAutoScanDelayChangeTwoSeconds_stringUpdates() {
        setAutoScanEnabled(true);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        final Preference autoScanDelayPref = activity.findPreference(mAutoScanKey);
        autoScanDelayPref.getOnPreferenceChangeListener()
                .onPreferenceChange(autoScanDelayPref, "2.0");
        assertEquals("2.00 Seconds", autoScanDelayPref.getSummary());
    }

    @Test
    public void testAutoScanDelayChangeOneSecond_stringUpdates() {
        setAutoScanEnabled(true);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        final Preference autoScanDelayPref = activity.findPreference(mAutoScanKey);
        autoScanDelayPref.getOnPreferenceChangeListener()
                .onPreferenceChange(autoScanDelayPref, "2.0");
        autoScanDelayPref.getOnPreferenceChangeListener()
                .onPreferenceChange(autoScanDelayPref, "1.0");
        assertEquals("1 Second", autoScanDelayPref.getSummary());
    }

    @Test
    public void testAutoScanDelayChangeInvalid_doesNotUpdate() {
        setAutoScanEnabled(true);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        final Preference autoScanDelayPref = activity.findPreference(mAutoScanKey);
        assertFalse(autoScanDelayPref.getOnPreferenceChangeListener()
                .onPreferenceChange(autoScanDelayPref, "."));
        assertEquals("1 Second", autoScanDelayPref.getSummary());
    }

    @Test
    public void isAutoscanEnabledWithDisabled_shouldReturnFalse() {
        setAutoScanEnabled(false);
        assertFalse(SwitchAccessPreferenceActivity.isAutoScanEnabled(mContext));
    }

    @Test
    public void isAutoscanEnabledWithEnabled_shouldReturnTrue() {
        setAutoScanEnabled(true);
        assertTrue(SwitchAccessPreferenceActivity.isAutoScanEnabled(mContext));
    }

    @Test
    public void isOptionScanningEnabledWithDisabled_shouldReturnFalse() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.views_linear_ime_row_col_key));
        assertFalse(SwitchAccessPreferenceActivity.isOptionScanningEnabled(mContext));
    }

    @Test
    public void isOptionScanningEnabledWithEnabled_shouldReturnTrue() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        assertTrue(SwitchAccessPreferenceActivity.isOptionScanningEnabled(mContext));
    }

    @Test
    public void whenOptionScanningDisabledAndNoKeysConfigured_showNoOptionScanSettings() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.views_linear_ime_row_col_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_3_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_4_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_5_key));
        assertFalse(preferencePresent(activity, R.string.pref_highlight_1_key));
        assertFalse(preferencePresent(activity, R.string.pref_highlight_2_key));
        assertFalse(preferencePresent(activity, R.string.pref_highlight_3_key));
        assertFalse(preferencePresent(activity, R.string.pref_highlight_4_key));
    }

    @Test
    public void whenOptionScanningEnabled_showOptionScanHighlighting() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertTrue(preferencePresent(activity, R.string.pref_highlight_1_key));
        assertTrue(preferencePresent(activity, R.string.pref_highlight_2_key));
        assertTrue(preferencePresent(activity, R.string.pref_highlight_3_key));
        assertTrue(preferencePresent(activity, R.string.pref_highlight_4_key));
    }

    @Test
    public void whenOptionScanningEnabledAndNoKeysConfigured_showTwoOptionScanKeyPrefs() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_click_key));
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_next_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_3_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_4_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_5_key));
    }

    @Test
    public void whenOptionScanningEnabledAndTwoKeysConfigured_showThreeOptionScanKeyPrefs() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(keyEvent);
        Set<String> stringSet = new HashSet<>(Arrays.asList((new Long(keyCode)).toString()));
        mSharedPreferences.edit()
                .putStringSet(mContext.getString(R.string.pref_key_mapped_to_click_key), stringSet)
                .putStringSet(mContext.getString(R.string.pref_key_mapped_to_next_key), stringSet)
                .apply();
        notifyPreferenceChanged(activity, R.string.pref_key_mapped_to_next_key);

        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_click_key));
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_next_key));
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_switch_3_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_4_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_switch_5_key));
    }

    @Test
    public void whenOptionScanningEnabled_autoScanPrefShouldDisappear() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertFalse(preferencePresent(activity, R.string.pref_key_auto_scan_enabled));
    }

    @Test
    public void whenOptionScanningEnabled_shouldShowOnlyOptionHighlightPref() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertTrue(preferencePresent(activity, R.string.pref_highlights_key));
        assertFalse(preferencePresent(activity, R.string.pref_standard_highlight_key));
    }

    @Test
    public void whenOptionScanningDisabled_shouldShowOnlyStandardHighlightPref() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.views_linear_ime_row_col_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertTrue(preferencePresent(activity, R.string.pref_standard_highlight_key));
        assertFalse(preferencePresent(activity, R.string.pref_highlights_key));
    }

    @Test
    public void whenOptionScanningAndAutoScanEnabled_optionScanningShouldBeDisabled() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        setAutoScanEnabled(true);
        getPrefActivity();
        assertFalse(SwitchAccessPreferenceActivity.isOptionScanningEnabled(mContext));
    }

    @Test
    public void whenOptionScanningDisabled_stringsDontMentionOptionScanning() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.views_linear_ime_row_col_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        Preference nextKeyAssignmentPref =
                activity.findPreference(mContext.getString(R.string.pref_key_mapped_to_next_key));
        Preference clickKeyAssignmentPref =
                activity.findPreference(mContext.getString(R.string.pref_key_mapped_to_click_key));
        Preference highlightColor0Pref =
                activity.findPreference(mContext.getString(R.string.pref_highlight_0_key));

        assertEquals(mContext.getString(R.string.action_name_next),
                nextKeyAssignmentPref.getTitle());
        assertEquals(mContext.getString(R.string.action_name_click),
                clickKeyAssignmentPref.getTitle());
    }

    @Test
    public void whenOptionScanningEnabled_stringsMentionOptionScanning() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        Preference clickKeyAssignmentPref =
                activity.findPreference(mContext.getString(R.string.pref_key_mapped_to_click_key));
        Preference nextKeyAssignmentPref =
                activity.findPreference(mContext.getString(R.string.pref_key_mapped_to_next_key));
        Preference highlightColor0Pref =
                activity.findPreference(mContext.getString(R.string.pref_highlight_0_key));

        assertEquals(String.format(mContext.getString(R.string.option_scan_switch_format), 1),
                clickKeyAssignmentPref.getTitle());
        assertEquals(String.format(mContext.getString(R.string.option_scan_switch_format), 2),
                nextKeyAssignmentPref.getTitle());
        assertEquals(String.format(mContext.getString(R.string.option_scan_switch_format), 1),
                highlightColor0Pref.getTitle());
    }

    @Test
    public void optionScanningDisabledButKeysMappedToOption3_showPreference() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.views_linear_ime_row_col_key));
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(keyEvent);
        Set<String> stringSet = new HashSet<>(Arrays.asList((new Long(keyCode)).toString()));
        mSharedPreferences.edit().putStringSet(
                mContext.getString(R.string.pref_key_mapped_to_switch_3_key), stringSet).apply();
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_switch_3_key));
    }

    @Test
    public void whenOptionScanningStateChanges_preferencesUpdate() {
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.views_linear_ime_row_col_key));
        notifyPreferenceChanged(activity, R.string.pref_scanning_methods_key);
        Preference nextKeyAssignmentPref =
                activity.findPreference(mContext.getString(R.string.pref_key_mapped_to_next_key));
        assertEquals(mContext.getString(R.string.action_name_next),
                nextKeyAssignmentPref.getTitle());
    }

    @Test
    public void whenAutoScanDisabled_delayPrefNotShown() {
        setAutoScanEnabled(false);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertFalse(preferencePresent(activity, R.string.pref_key_auto_scan_time_delay));
    }

    @Test
    public void whenAutoScanEnabled_delayPrefShown() {
        setAutoScanEnabled(true);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertTrue(preferencePresent(activity, R.string.pref_key_auto_scan_time_delay));
    }

    @Test
    public void whenAutoScanDisabledAndNoKeyMapped_keyPrefsNotShown() {
        setAutoScanEnabled(false);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_auto_scan_key));
        assertFalse(preferencePresent(activity, R.string.pref_key_mapped_to_reverse_auto_scan_key));
    }

    @Test
    public void whenAutoScanEnabled_optionScanningNotAvailable() {
        setAutoScanEnabled(true);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        ListPreference scanMethodsPref = (ListPreference)
                activity.findPreference(mContext.getString(R.string.pref_scanning_methods_key));
        assertEquals(-1,
                scanMethodsPref.findIndexOfValue(mContext.getString(R.string.option_scanning_key)));
    }

    @Test
    public void whenAutoScanDisabledButKeyMapped_keyPrefShown() {
        setAutoScanEnabled(false);
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        long keyCode = KeyComboPreference.keyEventToExtendedKeyCode(keyEvent);
        Set<String> stringSet = new HashSet<>(Arrays.asList((new Long(keyCode)).toString()));
        mSharedPreferences.edit().putStringSet(mContext
                .getString(R.string.pref_key_mapped_to_auto_scan_key), stringSet).apply();
        mSharedPreferences.edit().putStringSet(mContext
                .getString(R.string.pref_key_mapped_to_reverse_auto_scan_key), stringSet).apply();
        SwitchAccessPreferenceActivity activity = getPrefActivity();

        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_auto_scan_key));
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_reverse_auto_scan_key));
    }

    @Test
    public void whenAutoScanBecomesEnabled_prefsUpdate() {
        setAutoScanEnabled(false);
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        setAutoScanEnabled(true);
        notifyPreferenceChanged(activity, R.string.pref_key_auto_scan_enabled);
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_auto_scan_key));
        assertTrue(preferencePresent(activity, R.string.pref_key_mapped_to_reverse_auto_scan_key));
    }

    @Test
    public void testHighlightStylesForOptionScanning_defaultsAreDifferent() {
        int[] prefIds = {R.string.pref_highlight_0_color_key, R.string.pref_highlight_1_color_key,
                R.string.pref_highlight_2_color_key, R.string.pref_highlight_3_color_key,
                R.string.pref_highlight_4_color_key};
        setStringPreference(R.string.pref_scanning_methods_key,
                mContext.getString(R.string.option_scanning_key));
        SwitchAccessPreferenceActivity activity = getPrefActivity();
        Set<String> defaultColors = new HashSet<>();
        for (int id : prefIds) {
            ListPreference highlightPref =
                    (ListPreference) activity.findPreference(mContext.getString(id));
            String defaultColor = highlightPref.getValue();
            assertFalse(defaultColors.contains(defaultColor));
            defaultColors.add(defaultColor);
        }
    }

    @Test
    public void testIsGlobalMenuAutoSelectOn_returnsCorrectValue() {
        SwitchAccessPreferenceActivity.setGlobalMenuAutoselectOn(mContext, true);
        assertTrue(SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext));
        SwitchAccessPreferenceActivity.setGlobalMenuAutoselectOn(mContext, false);
        assertFalse(SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext));
    }

    private void setStringPreference(int prefStringId, String value) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(mContext.getString(prefStringId), value).commit();
    }

    private boolean preferencePresent(SwitchAccessPreferenceActivity activity, int prefKeyId) {
        return activity.findPreference(mContext.getString(prefKeyId)) != null;
    }

    private void setAutoScanEnabled(boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(mContext.getString(R.string.pref_key_auto_scan_enabled), enabled)
                .apply();
    }

    private SwitchAccessPreferenceActivity getPrefActivity() {
        return Robolectric
                .buildActivity(SwitchAccessPreferenceActivity.class).create().start().get();
    }

    private void notifyPreferenceChanged(PreferenceActivity activity, int prefKeyId) {
        Preference pref = activity.findPreference(mContext.getString(prefKeyId));
        Preference.OnPreferenceChangeListener listener = pref.getOnPreferenceChangeListener();
        Map<String, ?> prefMap = mSharedPreferences.getAll();
        if (listener != null) {
            listener.onPreferenceChange(pref, prefMap.get(pref.getKey()));
        }
    }
}
