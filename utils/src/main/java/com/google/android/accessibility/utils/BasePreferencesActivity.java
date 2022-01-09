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

import android.graphics.drawable.Drawable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Preference activity used. This base class is separate from PreferencesActivity to allow partner
 * code (for open-source) to inherit from AppCompatActivity.
 */
public abstract class BasePreferencesActivity extends AppCompatActivity {
  private static final int DEFAULT_CONTAINER_ID = android.R.id.content;

  /** Disables action bar */
  protected void disableActionBar() {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.hide();
    }
  }

  /**
   * Prepares action bar
   *
   * @param icon Icon shows on action bar.
   */
  protected void prepareActionBar(@Nullable Drawable icon) {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      if (icon != null) {
        actionBar.setIcon(icon);
      }
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
  }

  /** Disables to expand action bar */
  protected void disableExpandActionBar() {}

  /**
   * Sets the title of the action bar
   *
   * @param title The title of the action bar which likes to set.
   */
  protected void setActionBarTitle(String title) {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setSubtitle(title);
    }
  }

  /**
   * Gets Identifier of the container whose fragment(s) at the activity should use.
   *
   * @return The fragments container in the activity.
   */
  protected final int getContainerId() {
    return DEFAULT_CONTAINER_ID;
  }
}
