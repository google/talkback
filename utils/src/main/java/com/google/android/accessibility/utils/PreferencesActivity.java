/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.utils;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides interfaces and common methods for a11y preference activity used. This class inherits
 * {@link BasePreferencesActivity} and provide common functions for a11y preference activity.
 */
public abstract class PreferencesActivity extends BasePreferencesActivity {

  private PreferenceFragmentCompat preferenceFragment;

  /** Creates a PreferenceFragmentCompat when AccessibilityPreferencesActivity is called. */
  protected abstract PreferenceFragmentCompat createPreferenceFragment();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // TODO: Overlay PreferencesActivity for TV and Watch to replace of this check.
    if (FeatureSupport.isWatch(this)) {
      disableActionBar();
    } else {
      prepareActionBar(/* icon= */ null);
    }

    if (FeatureSupport.isTv(this)) {
      disableExpandActionBar();
    }

    int preferenceContainerId = getContainerId();
    if (supportHatsSurvey()) {
      setContentView(R.layout.preference_with_survey);
      preferenceContainerId = R.id.preference_root;
    }

    // Creates UI for the preferenceFragment created by the child class of
    // AccessibilityBasePreferencesActivity.
    preferenceFragment = createPreferenceFragment();
    if (preferenceFragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(preferenceContainerId, preferenceFragment, getFragmentTag())
          .commit();
    }
  }

  /** If action-bar "navigate up" button is pressed, end this sub-activity. */
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

  @Override
  protected void onStart() {
    super.onStart();
    if (preferenceFragment != null) {
      // To avoid texts showing outside of the watch face, set a padding value if the preference
      // fragment is shown on watch. Square screen and round screen have different values.
      if (FeatureSupport.isWatch(getApplicationContext())
          && preferenceFragment.getListView() != null) {
        int padding =
            (int)
                getResources()
                    .getDimension(R.dimen.full_screen_preference_fragment_padding_on_watch);
        preferenceFragment.getListView().setPadding(0, padding, 0, padding);
        // To support rotary-button input, it needs to request focus of the scrollable view.
        preferenceFragment.getListView().requestFocus();
      }
    }
  }

  /**
   * Finds preference from createPreferenceFragment() called only in onCreate(). gets non-updated
   * preferences, because base class stores only 1 createPreferenceFragment() call.
   */
  public Preference findPreference(String key) {
    return PreferenceSettingsUtils.findPreference(this, key);
  }

  /** The implementation of the activity should supports HaTS survey layout or not. */
  protected boolean supportHatsSurvey() {
    return false;
  }

  /**
   * Updates the status of preference to on or off after the selector or context menu change the
   * state while the activity is visible.
   */
  protected void updateTwoStatePreferenceStatus(
      int preferenceKeyResId, int preferenceDefaultKeyResId) {
    @Nullable Preference preference = findPreference(getString(preferenceKeyResId));
    if (preference instanceof TwoStatePreference) {
      // Make sure that we have the latest value of preference before continuing.
      boolean enabledState =
          SharedPreferencesUtils.getBooleanPref(
              SharedPreferencesUtils.getSharedPreferences(getApplicationContext()),
              getResources(),
              preferenceKeyResId,
              preferenceDefaultKeyResId);

      ((TwoStatePreference) preference).setChecked(enabledState);
    }
  }

  /**
   * Gets tag of the fragment(s) are to be used.
   *
   * @return tag of the fragment.
   */
  @Nullable
  protected String getFragmentTag() {
    return null;
  }
}
