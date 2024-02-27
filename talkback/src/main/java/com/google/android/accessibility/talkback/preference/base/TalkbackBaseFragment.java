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
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import com.google.android.accessibility.talkback.preference.TalkBackPreferenceFilter;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.material.WrapSwipeDismissLayoutHelper;
import com.google.android.accessibility.utils.preference.BasePreferencesFragment;
import org.checkerframework.checker.nullness.qual.Nullable;

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

  /**
   * This function is used to get the sub title which the fragment likes to show on app bar. The
   * child class implements this function and will show sub title on app bar. If the child class
   * doesn't implement this function, sub title will not show anything.
   *
   * @return The sub title of the fragment will show on app bar.
   */
  @Override
  public @Nullable CharSequence getSubTitle() {
    return null;
  }

  @Override
  public int getXmlResId() {
    return xmlResId;
  }

  /** Preferences managed by this fragment. */
  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    if (xmlResId != INVALID_VALUE) {
      super.onCreatePreferences(savedInstanceState, rootKey);

      final Context context = getContext();
      if (context == null) {
        return;
      }
      TalkBackPreferenceFilter talkBackPreferenceFilter = new TalkBackPreferenceFilter(context);
      talkBackPreferenceFilter.filterPreferences(getPreferenceScreen());
    }
  }

  // TODO: Wrap the root container instead of each view in the swipe dismiss layout.
  /** A default implementation of wrapping swipe dismiss listener for the view. */
  protected View wrapSwipeDismissLayout(View view) {
    // Customs swipe action for view. This is for wear only.
    return WrapSwipeDismissLayoutHelper.wrapSwipeDismissLayout(
        getActivity(), view, /* swipeDismissListener= */ null);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View inflatedView = super.onCreateView(inflater, container, savedInstanceState);
    if (FormFactorUtils.getInstance().isAndroidWear() && getListView() != null) {
      // To support rotary-button input, it needs to request focus of the scrollable view.
      getListView().requestFocus();
    }
    return wrapSwipeDismissLayout(inflatedView);
  }

  /** Turns a preference on or off, selecting the preference by key-resource-ID. */
  public void setEnabled(Context context, int preferenceKeyResId, boolean enable) {
    Preference preference = findPreference(context.getString(preferenceKeyResId));
    if (preference != null) {
      preference.setEnabled(enable);
    }
  }

  /** Returns the preference associated with the specified resource identifier. */
  public Preference findPreferenceByResId(@StringRes int resId) {
    return findPreference(getString(resId));
  }
}
