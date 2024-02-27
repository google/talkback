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
package com.google.android.accessibility.utils.preference;

import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.R;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides interfaces and common methods for a11y preference activity used. This class inherits
 * {@link BasePreferencesActivity} and provide common functions for a11y preference activity.
 */
public abstract class PreferencesActivity extends BasePreferencesActivity {
  // This variable is used as argument of Intent to identify which fragment should be created.
  public static final String FRAGMENT_NAME = "FragmentName";

  /** Creates a PreferenceFragmentCompat when AccessibilityPreferencesActivity is called. */
  protected abstract PreferenceFragmentCompat createPreferenceFragment();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    prepareActionBar(/* icon= */ null);

    if (supportHatsSurvey()) {
      setContentView(R.layout.preference_with_survey);
    }

    // Creates UI for the preferenceFragment created by the child class of
    // AccessibilityBasePreferencesActivity.
    PreferenceFragmentCompat preferenceFragment = createPreferenceFragment();
    if (preferenceFragment != null && savedInstanceState == null) {
      FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
      transaction.replace(getContainerId(), preferenceFragment, getFragmentTag());
      if (addRootFragmentToBackStack()) {
        transaction.addToBackStack(/* name= */ null);
      }
      transaction.commit();
    }
  }

  /**
   * If action-bar "navigate up" button is pressed, end this sub-activity when there is no fragment
   * in the stack. Otherwise, it will go to last fragment.
   */
  @Override
  public boolean onNavigateUp() {
    if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
      getSupportFragmentManager().popBackStackImmediate();
    } else {
      // Closes the activity if there is no fragment inside the stack. Otherwise the activity will
      // has a blank screen since there is no any fragment.
      finishAfterTransition();
    }
    return true;
  }

  @Override
  protected final int getContainerId() {
    return supportHatsSurvey() ? R.id.preference_root : super.getContainerId();
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
   * Gets tag of the fragment(s) are to be used.
   *
   * @return tag of the fragment.
   */
  protected @Nullable String getFragmentTag() {
    return null;
  }
}
