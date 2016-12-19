/*
 * Copyright 2016 Google Inc.
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
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.os.BuildCompat;
import android.text.SpannableString;
import android.text.style.TtsSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityEvent;

import com.android.utils.AccessibilityEventUtils;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.ArrayList;

/**
 * Activity used to set TalkBack's dump events preferences.
 */
public class TalkBackDumpAccessibilityEventActivity extends Activity {

    private MenuItem.OnMenuItemClickListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.title_activity_dump_a11y_event));

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        TalkBackDumpAccessibilityEventFragment fragment =
                new TalkBackDumpAccessibilityEventFragment();
        mListener = fragment;
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment).commit();
    }

    /**
     * Finishes the activity when the up button is pressed on the action bar.
     * Perform function in TalkBackAccessibilityEventFragment when clear_all/check_all button is
     * pressed.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.clear_all:
            case R.id.check_all:
                return mListener.onMenuItemClick(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dump_a11y_event_menu, menu);
        return true;
    }

    public static class TalkBackDumpAccessibilityEventFragment extends PreferenceFragment
            implements MenuItem.OnMenuItemClickListener, OnPreferenceChangeListener {

        private TalkBackService mService;
        /**
         * List of all the accessibility event types
         */
        private static final int[] DUMP_A11Y_EVENT_IDS = AccessibilityEventUtils.getAllEventTypes();

        /**
         * List of preference keys
         */
        private ArrayList<String> mDumpEventPrefKeys = new ArrayList<>(DUMP_A11Y_EVENT_IDS.length);

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Set preferences to use device-protected storage.
            if (BuildCompat.isAtLeastN()) {
                getPreferenceManager().setStorageDeviceProtected();
            }

            mService = TalkBackService.getInstance();

            // Compose preference keys from a key prefix and accessibility event types.
            for (int id : DUMP_A11Y_EVENT_IDS) {
                mDumpEventPrefKeys.add(getString(R.string.pref_dump_event_key_prefix, id));
            }

            addPreferencesFromResource(R.xml.dump_events_preferences);
            PreferenceScreen screen = getPreferenceScreen();

            for (int i = 0; i < DUMP_A11Y_EVENT_IDS.length; i++) {
                CheckBoxPreference preference = new CheckBoxPreference(
                        getActivity().getApplicationContext());
                preference.setKey(mDumpEventPrefKeys.get(i));

                String title = AccessibilityEvent.eventTypeToString(DUMP_A11Y_EVENT_IDS[i]);
                // Add TtsSpan to the titles to improve readability.
                SpannableString spannableString = new SpannableString(title);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    TtsSpan ttsSpan = new TtsSpan.TextBuilder(title.replaceAll("_", " ")).build();
                    spannableString.setSpan(ttsSpan, 0, title.length(), 0 /* no flag */);
                }
                preference.setTitle(spannableString);

                screen.addPreference(preference);
                preference.setOnPreferenceChangeListener(this);
            }
        }

        private void setAllPreferenceValue(boolean value) {
            for (String prefKey : mDumpEventPrefKeys) {
                Preference preference = findPreference(prefKey);
                if (preference != null) {
                    ((CheckBoxPreference) preference).setChecked(value);
                    onPreferenceChange(preference, value);
                }
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.clear_all:
                    setAllPreferenceValue(false);
                    return true;
                case R.id.check_all:
                    setAllPreferenceValue(true);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (mService != null) {
                String prefKey = preference.getKey();
                int index = mDumpEventPrefKeys.indexOf(prefKey);
                mService.notifyDumpEventPreferenceChanged(DUMP_A11Y_EVENT_IDS[index],
                        (boolean) newValue);
            }
            return true;
        }
    }
}
