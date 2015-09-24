/*
 * Copyright 2015 Google Inc.
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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.view.MenuItem;

/**
 * Activity used to set TalkBack's keyboard shortcut preferences.
 */
public class TalkBackKeyboardShortcutPreferencesActivity extends PreferenceActivity {

    private final OnPreferenceChangeListener mPreferenceChangeListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (preference instanceof KeyboardShortcutDialogPreference &&
                            newValue instanceof Long) {
                        KeyboardShortcutDialogPreference keyBoardPreference =
                                (KeyboardShortcutDialogPreference) preference;
                        keyBoardPreference.setKeyComboCode((Long) newValue);
                        keyBoardPreference.notifyChanged();
                    }
                    return true;
                }
            };

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        addPreferencesFromResource(R.xml.key_combo_preferences);
        setPreferenceChangeListeners(getPreferenceScreen());
    }

    private void setPreferenceChangeListeners(PreferenceGroup root) {
        if (root == null) {
            return;
        }

        final int count = root.getPreferenceCount();

        for (int i = 0; i < count; i++) {
            final Preference preference = root.getPreference(i);
            if (preference instanceof KeyboardShortcutDialogPreference) {
                preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
            } else if (preference instanceof PreferenceGroup) {
                setPreferenceChangeListeners((PreferenceGroup) preference);
            }
        }
    }

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
}
