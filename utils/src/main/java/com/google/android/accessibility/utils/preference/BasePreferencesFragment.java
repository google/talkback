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

import android.app.ActionBar;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Fragment that holds the preference user interface controls. */
public abstract class BasePreferencesFragment extends PreferenceFragmentCompat {
  /**
   * Gets the title which the fragment likes to show on app bar. The child class must implement this
   * function.
   *
   * @return The title of the fragment will show on app bar.
   */
  protected abstract CharSequence getTitle();

  /**
   * This function is used to get the sub title which the fragment likes to show on app bar. The
   * child class implements this function and will show sub title on app bar. If the child class
   * doesn't implement this function, sub title will not show anything.
   *
   * @return The sub title of the fragment will show on app bar.
   */
  protected abstract @Nullable CharSequence getSubTitle();

  /**
   * Gets the res id of XML to create the fragment
   *
   * @return The res id of XML.
   */
  protected abstract int getXmlResId();

  @Override
  public void onResume() {
    super.onResume();

    FragmentActivity activity = getActivity();
    activity.setTitle(getTitle());

    @Nullable CharSequence subTitle = getSubTitle();
    if (subTitle != null) {
      ActionBar actionBar = activity.getActionBar();
      if (actionBar != null) {
        actionBar.setSubtitle(subTitle);
      }
    }
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    PreferenceSettingsUtils.addPreferencesFromResource(this, getXmlResId());
  }
}
