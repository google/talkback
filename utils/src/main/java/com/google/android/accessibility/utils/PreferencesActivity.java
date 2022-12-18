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
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
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

    // TODO: Overlay PreferencesActivity for TV and Watch to replace of this check.
    if (FeatureSupport.isWatch(this)) {
      disableActionBar();
    } else {
      prepareActionBar(/* icon= */ null);
    }

    if (FeatureSupport.isTv(this)) {
      disableExpandActionBar();
      hideBackButton();
    }

    if (supportHatsSurvey()) {
      setContentView(R.layout.preference_with_survey);
    }

    // Creates UI for the preferenceFragment created by the child class of
    // AccessibilityBasePreferencesActivity.
    PreferenceFragmentCompat preferenceFragment = createPreferenceFragment();
    if (preferenceFragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(getContainerId(), preferenceFragment, getFragmentTag())
          // Add root page to back-history
          .addToBackStack(/* name= */ null)
          .commit();
    }
  }

  /**
   * If action-bar "navigate up" button is pressed, end this sub-activity when there is no fragment
   * in the stack. Otherwise, it will go to last fragment.
   */
  @Override
  public boolean onNavigateUp() {
    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
      getSupportFragmentManager().popBackStackImmediate();
    }

    // Closes the activity if there is no fragment inside the stack. Otherwise the activity will has
    // a blank screen since there is no any fragment.
    if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
      finishAfterTransition();
    }
    return true;
  }
  // INFO: TalkBack For Developers modification
  @Override
  public boolean onSupportNavigateUp() {
    return onNavigateUp();
  }

  @Override
  public void onBackPressed() {
    onNavigateUp();
  }
  // ------------------------------------------

  @Override
  protected void onStart() {
    super.onStart();
    // To avoid texts showing outside of the watch face, set a padding value if the preference
    // fragment is shown on watch. Square screen and round screen have different values.
    if (FeatureSupport.isWatch(getApplicationContext())) {
      int padding =
          (int)
              getResources().getDimension(R.dimen.full_screen_preference_fragment_padding_on_watch);
      View activityView = getWindow().getDecorView();
      activityView.setBackgroundResource(R.color.google_black);
      activityView.setPadding(/* left= */ 0, padding, /* right= */ 0, padding);
    }
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
