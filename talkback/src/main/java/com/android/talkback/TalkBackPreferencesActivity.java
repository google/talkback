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

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.HatsSurveyRequester;
import com.google.android.accessibility.talkback.preference.base.TalkBackKeyboardShortcutPreferenceFragment;
import com.google.android.accessibility.talkback.preference.base.TalkBackPreferenceFragment;
import com.google.android.accessibility.talkback.preference.base.VerbosityPrefFragment;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferencesActivity;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Activity used to set TalkBack's service preferences.
 *
 * <p>Never change preference types. This is because of AndroidManifest.xml setting
 * android:restoreAnyVersion="true", which supports restoring preferences from a new play-store
 * installed talkback onto a clean device with older bundled talkback.
 * REFERTO
 */
public class TalkBackPreferencesActivity extends PreferencesActivity {

  private static final String TAG = "PreferencesActivity";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Request the HaTS.
    if (supportHatsSurvey()) {
      new HatsSurveyRequester(this).requestSurvey();
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    String fragmentName = intent.getStringExtra(FRAGMENT_NAME);
    PreferenceFragmentCompat fragment = getFragmentByName(fragmentName);

    if (fragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(getContainerId(), fragment, getFragmentTag())
          // Add root page to back-history
          .addToBackStack(/* name= */ null)
          .commit();
    }
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    Intent intent = getIntent();
    PreferenceFragmentCompat fragment = null;
    if (intent != null) {
      String fragmentName = intent.getStringExtra(FRAGMENT_NAME);

      fragment = getFragmentByName(fragmentName);
    }

    return (fragment == null) ? new TalkBackPreferenceFragment() : fragment;
  }

  private static PreferenceFragmentCompat getFragmentByName(String fragmentName) {
    if (fragmentName == null) {
      return null;
    }

    if (fragmentName.equals(TalkBackKeyboardShortcutPreferenceFragment.getFragmentName())) {
      return new TalkBackKeyboardShortcutPreferenceFragment();
    } else if (fragmentName.equals(VerbosityPrefFragment.getFragmentName())) {
      return new VerbosityPrefFragment();
    }
    return null;
  }

  @Override
  protected boolean supportHatsSurvey() {
    // HaTS requests Theme.AppCompat to display the survey, so disable it if the setting activity is
    // using the material next theme.
    return !FeatureSupport.supportSettingsTheme();
  }

  @Override
  public void onBackPressed() {
    Toast.makeText(this, "Pressed back", Toast.LENGTH_SHORT).show();
    super.onBackPressed();
  }
}
