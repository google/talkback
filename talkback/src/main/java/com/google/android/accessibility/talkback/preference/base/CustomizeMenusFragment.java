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
package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.os.Bundle;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;

/** Panel holding a set of customize menus preferences. */
public class CustomizeMenusFragment extends TalkbackBaseFragment {
  public CustomizeMenusFragment() {
    super(R.xml.customize_menus_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_manage_customize_menus);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    final Context context = getContext();
    if (context == null) {
      return;
    }

    if (!FeatureSupport.isMultiFingerGestureSupported()
        || FormFactorUtils.getInstance().isAndroidWear()) {
      PreferencesActivityUtils.setSummary(
          context,
          getPreferenceManager(),
          R.string.pref_category_manage_context_menu_key,
          R.string.pref_category_context_menu_summary_single_finger);
      PreferencesActivityUtils.setSummary(
          context,
          getPreferenceManager(),
          R.string.pref_category_manage_selector_menu_key,
          R.string.pref_category_selector_menu_summary_single_finger);
    }
  }
}
