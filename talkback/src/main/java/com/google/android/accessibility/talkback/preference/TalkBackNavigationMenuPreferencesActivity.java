/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.preference;

import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.BasePreferencesActivity;

/** Activity used to set TalkBack navigation menu preferences. */
public class TalkBackNavigationMenuPreferencesActivity extends BasePreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new TalkBackNavigationMenuPreferencesActivity.NavigationMenuFragment();
  }

  /** Panel holding a set of navigation menu preferences. */
  public static class NavigationMenuFragment extends TalkbackBaseFragment {

    // Prevents from runtime exception that will occur in some cases during state restore if the
    // no-argument constructor is not available.
    public NavigationMenuFragment() {
      super(R.xml.navigation_menu_preferences);
    }
  }
}
