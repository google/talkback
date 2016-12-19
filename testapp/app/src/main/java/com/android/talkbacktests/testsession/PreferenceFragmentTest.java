/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkbacktests.testsession;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.talkbacktests.R;

public class PreferenceFragmentTest extends BaseTestContent implements View.OnClickListener {

    private Context mContext;

    public PreferenceFragmentTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(LayoutInflater inflater, ViewGroup container, Context context) {
        mContext = context;
        View view = inflater.inflate(R.layout.test_preference_fragment, container, false);
        view.findViewById(R.id.button_preferencefragment).setOnClickListener(this);
        view.findViewById(R.id.button_preferencefragmentcompat).setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_preferencefragment:
                Intent intent = new Intent(mContext, PrefsActivity.class);
                mContext.startActivity(intent);
                break;
            case R.id.button_preferencefragmentcompat:
                Intent intent_compat = new Intent(mContext, PrefsCompatActivity.class);
                mContext.startActivity(intent_compat);
                break;
        }
    }


    public static final class PrefsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.test_sample_preferences);

            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            android.preference.Preference pref = findPreference(key);
            if (pref instanceof android.preference.ListPreference) {
                android.preference.ListPreference listPref =
                        (android.preference.ListPreference) pref;
                listPref.setSummary(listPref.getEntry());
            } else if (pref instanceof android.preference.EditTextPreference) {
                android.preference.EditTextPreference editTextPref =
                        (android.preference.EditTextPreference) pref;
                editTextPref.setSummary(editTextPref.getText());
            }
        }
    }

    public static final class PrefsFragmentCompat extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            setPreferencesFromResource(R.xml.test_sample_preferences, s);

            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Preference pref = findPreference(key);
            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                listPref.setSummary(listPref.getEntry());
            } else if (pref instanceof EditTextPreference) {
                EditTextPreference editTextPref = (EditTextPreference) pref;
                editTextPref.setSummary(editTextPref.getText());
            }
        }
    }

    public static final class PrefsActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PrefsFragment()).commit();
        }
    }

    public static final class PrefsCompatActivity extends AppCompatActivity
            implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PrefsFragmentCompat()).commit();
        }

        @Override
        public boolean onPreferenceStartScreen(PreferenceFragmentCompat preferenceFragmentCompat,
                                               PreferenceScreen preferenceScreen) {
            PrefsFragmentCompat fragment = new PrefsFragmentCompat();
            Bundle args = new Bundle();
            args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment, preferenceScreen.getKey())
                    .addToBackStack(preferenceScreen.getKey())
                    .commit();

            return true;
        }
    }
}