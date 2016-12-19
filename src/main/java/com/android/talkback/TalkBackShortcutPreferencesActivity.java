/*
 * Copyright 2010 Google Inc.
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

package com.android.talkback;

import android.app.ActionBar;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.support.v4.os.BuildCompat;
import android.view.MenuItem;

import com.android.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity used to set TalkBack's gesture preferences.
 */
public class TalkBackShortcutPreferencesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.title_pref_category_manage_gestures));

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new TalkBackShortcutPreferenceFragment()).commit();
    }

    /**
     * Finishes the activity when the up button is pressed on the action bar.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class TalkBackShortcutPreferenceFragment extends PreferenceFragment {
        /**
         * References to the string resources used as keys customizable gesture
         * mapping preferences
         */
        private static final int[] GESTURE_PREF_KEY_IDS = {
                R.string.pref_shortcut_up_key,
                R.string.pref_shortcut_down_key,
                R.string.pref_shortcut_left_key,
                R.string.pref_shortcut_right_key,
                R.string.pref_shortcut_up_and_down_key,
                R.string.pref_shortcut_down_and_up_key,
                R.string.pref_shortcut_down_and_left_key,
                R.string.pref_shortcut_down_and_right_key,
                R.string.pref_shortcut_left_and_down_key,
                R.string.pref_shortcut_left_and_up_key,
                R.string.pref_shortcut_right_and_down_key,
                R.string.pref_shortcut_right_and_up_key,
                R.string.pref_shortcut_up_and_left_key,
                R.string.pref_shortcut_up_and_right_key,
                R.string.pref_shortcut_single_tap_key,
                R.string.pref_shortcut_double_tap_key
        };

        /** List of keys used for customizable gesture mapping preferences. */
        private final ArrayList<String> mGesturePrefKeys =
                new ArrayList<>(GESTURE_PREF_KEY_IDS.length);

        /** List of values used for customizable gesture mapping preferences. */
        private final ArrayList<String> mGesturePrefValues = new ArrayList<>();

        private SharedPreferences mPrefs;

        /**
         * Loads the preferences from the XML preference definition for gestures.
         */
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Set preferences to use device-protected storage.
            if (BuildCompat.isAtLeastN()) {
                getPreferenceManager().setStorageDeviceProtected();
            }

            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            mPrefs = SharedPreferencesUtils.getSharedPreferences(activity);

            // Populate lists of available mapping keys and values
            for (int id : GESTURE_PREF_KEY_IDS) {
                mGesturePrefKeys.add(getString(id));
            }

            final String[] defaultGesturePrefValues =
                    getResources().getStringArray(R.array.pref_shortcut_values);
            mGesturePrefValues.addAll(Arrays.asList(defaultGesturePrefValues));

            addPreferencesFromResource(R.xml.gesture_preferences);

            final boolean treeDebugEnabled =
                    mPrefs.getBoolean(getString(R.string.pref_tree_debug_key), false);
            if (treeDebugEnabled) {
                // Add the print node tree option to all shortcut preferences.
                int numDefaultValues = defaultGesturePrefValues.length;

                String[] newGesturePrefValues =
                        Arrays.copyOf(defaultGesturePrefValues, numDefaultValues + 1);
                newGesturePrefValues[numDefaultValues] =
                        getString(R.string.shortcut_value_print_node_tree);

                String[] defaultGesturePrefLabels =
                        getResources().getStringArray(R.array.pref_shortcut_entries);
                String[] newGesturePrefLabels =
                        Arrays.copyOf(defaultGesturePrefLabels, numDefaultValues + 1);
                newGesturePrefLabels[numDefaultValues] =
                        getString(R.string.shortcut_print_node_tree);

                for (int gestureKey : GESTURE_PREF_KEY_IDS) {
                    final ListPreference gesturePref =
                            (ListPreference) findPreference(getString(gestureKey));
                    gesturePref.setEntries(newGesturePrefLabels);
                    gesturePref.setEntryValues(newGesturePrefValues);
                }

                mGesturePrefValues.add(getString(R.string.shortcut_value_print_node_tree));
            }

            fixListSummaries(getPreferenceScreen());
        }

        /**
         * Since the "%s" summary is currently broken, this sets the preference
         * change listener for all {@link ListPreference} views to fill in the
         * summary with the current entry value.
         * TODO: %s works in L, but is broken in JB MR0, so check this as we move the min
         * level.
         */
        private void fixListSummaries(PreferenceGroup root) {
            if (root == null) {
                return;
            }

            final int count = root.getPreferenceCount();

            for (int i = 0; i < count; i++) {
                final Preference preference = root.getPreference(i);
                if (preference instanceof ListPreference) {
                    fixUnboundPrefSummary(preference);
                    preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
                } else if (preference instanceof PreferenceGroup) {
                    fixListSummaries((PreferenceGroup) preference);
                }
            }
        }

        /**
         * After an update that removes an entry value from a {@link ListPreference}
         * is applied, we need to ensure that we don't display incorrect values in
         * the UI if TalkBack hasn't been enabled to remap the preference value.
         */
        private void fixUnboundPrefSummary(Preference pref) {
            final String prefKey = pref.getKey();
            final String prefValue = mPrefs.getString(prefKey, "");
            if (mGesturePrefKeys.contains(prefKey)) {
                // If we're ensuring consistency of a gesture customization
                // preference, ensure its mapped action exists within the set
                // of available values. If not, clear the preference summary until
                // the user chooses a new mapping, or TalkBackUpdateHelper gets a
                // chance to remap the actual preference.
                if (!mGesturePrefValues.contains(prefValue) || pref.getSummary().charAt(0) == '%') {
                    updatePrefSummary(pref, prefValue);
                }
            }
        }

        private boolean updatePrefSummary(Preference preference, Object newValue) {
            if (preference instanceof ListPreference && newValue instanceof String) {
                final ListPreference listPreference = (ListPreference) preference;
                final int index = listPreference.findIndexOfValue((String) newValue);
                final CharSequence[] entries = listPreference.getEntries();

                if (index >= 0 && index < entries.length) {
                    preference.setSummary(entries[index].toString().replaceAll("%", "%%"));
                } else {
                    preference.setSummary("");
                }
            }

            return true;
        }

        /**
         * Listens for preference changes and updates the summary to reflect the
         * current setting. This shouldn't be necessary, since preferences are
         * supposed to automatically do this when the summary is set to "%s".
         */
        private final OnPreferenceChangeListener mPreferenceChangeListener =
                new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        return updatePrefSummary(preference, newValue);
                    }
                };
    }


}
