/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.google.android.accessibility.talkback.preference.TalkBackPreferenceFilter;
import com.google.android.accessibility.talkback.preference.WearListPreference;
import com.google.android.accessibility.talkback.utils.TalkbackCustomViewSwipeAction;
import com.google.android.accessibility.utils.BasePreferencesFragment;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;

/** Panel holding a set of base fragment for preferences. */
public abstract class TalkbackBaseFragment extends BasePreferencesFragment {
  private static final int INVALID_VALUE = -1;

  private final int xmlResId;

  public TalkbackBaseFragment() {
    xmlResId = INVALID_VALUE;
  }

  public TalkbackBaseFragment(int xmlResId) {
    this.xmlResId = xmlResId;
  }

  /** Preferences managed by this fragment. */
  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    if (xmlResId != INVALID_VALUE) {
      PreferenceSettingsUtils.addPreferencesFromResource(this, xmlResId);
      final Context context = getContext();
      if (context == null) {
        return;
      }
      TalkBackPreferenceFilter talkBackPreferenceFilter = new TalkBackPreferenceFilter(context);
      talkBackPreferenceFilter.filterPreferences(getPreferenceScreen());
    }
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View inflatedView = super.onCreateView(inflater, container, savedInstanceState);
    if (FeatureSupport.isWatch(getContext()) && getListView() != null) {
      // To support rotary-button input, it needs to request focus of the scrollable view.
      getListView().requestFocus();
    }
    // Customs swipe action for view. This is for wear only.
    return TalkbackCustomViewSwipeAction.wrapWithSwipeHandler(getActivity(), inflatedView);
  }

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    if (preference instanceof WearListPreference) {
      PreferenceDialogFragmentCompat dialogFragment =
          ((WearListPreference) preference).createDialogFragment();
      dialogFragment.setTargetFragment(this, 0);
      dialogFragment.show(getParentFragmentManager(), preference.getKey());
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }

  /** Turns a preference on or off, selecting the preference by key-resource-ID. */
  public void setEnabled(Context context, int preferenceKeyResId, boolean enable) {
    Preference preference = findPreference(context.getString(preferenceKeyResId));
    if (preference != null) {
      preference.setEnabled(enable);
    }
  }
}
