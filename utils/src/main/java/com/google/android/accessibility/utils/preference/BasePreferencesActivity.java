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

import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Preference activity used. This base class is separate from PreferencesActivity to allow partner
 * code (for open-source) to inherit from AppCompatActivity.
 */
public abstract class BasePreferencesActivity extends AppCompatActivity {
  private static final int DEFAULT_CONTAINER_ID = android.R.id.content;

  /**
   * If action-bar back key button is pressed, end this sub-activity when there is no fragment in
   * the stack. Otherwise, it will go to last fragment.
   */
  @Override
  public void onBackPressed() {
    super.onBackPressed();

    // Closes the activity if there is no fragment inside the stack. Otherwise the activity will has
    // a blank screen since there is no any fragment. onBackPressed() in Activity.java only handles
    // popBackStackImmediate(). This will close activity to avoid a blank screen.
    if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
      finishAfterTransition();
    }
  }

  /**
   * Prepares action bar
   *
   * @param icon Icon shows on action bar.
   */
  protected void prepareActionBar(@Nullable Drawable icon) {
    ActionBar actionBar = getSupportActionBar();
    if (FeatureSupport.isWatch(getApplicationContext())) {
      if (actionBar != null) {
        actionBar.hide();
      }
      return;
    }

    if (actionBar != null) {
      if (icon != null) {
        actionBar.setIcon(icon);
      }
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    if (FeatureSupport.isTv(getApplicationContext())) {
      hideBackButton();
    }
  }

  /** Hides the back button (or home) inside the action bar */
  private void hideBackButton() {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(false);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
        onBackPressed();
    }
    return super.onOptionsItemSelected(item);
  }

  /**
   * Gets Identifier of the container whose fragment(s) at the activity should use.
   *
   * @return The fragments container in the activity.
   */
  protected int getContainerId() {
    return DEFAULT_CONTAINER_ID;
  }

  /** Returns {@code true} if the root fragment should be added to the fragment back stack. */
  protected boolean addRootFragmentToBackStack() {
    return !FeatureSupport.isTv(getApplicationContext());
  }
}
