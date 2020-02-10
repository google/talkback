/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.setupwizard;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.ScreenViewListener;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Provides a generic framework for each screen of the setup wizard. */
public abstract class SetupWizardScreenFragment extends Fragment {

  // Constant value used to indicate no that resource is being put into the heading/subheading
  static final int EMPTY_TEXT = 0;

  // The root view for this fragment's layout (the one returned by #onCreateView). This is the same
  // View returned by Fragment#getView, but this is guaranteed to be non-null once #onCreateView has
  // been called
  private View rootView;

  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);
    View view = getView();

    if (view == null) {
      // Inflate the base view for the setup wizard from the template, then inflate the content view
      // for the fragment. This base view holds the stubs of the specific Setup Screens.
      View setupBaseView = inflater.inflate(R.layout.switch_access_setup_layout, container, false);
      int contentResId = getLayoutResourceId();
      if (contentResId > 0) {
        ViewGroup content = setupBaseView.findViewById(R.id.fragment_layout_container);
        content.removeAllViews();
        View currentView = getActivity().getLayoutInflater().inflate(contentResId, content, false);
        updateScreenForCurrentPreferenceValues(currentView);
        return rootView = currentView;
      }
      return rootView = setupBaseView;
    } else {
      return rootView = view;
    }
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    updateUiOnCreateOrRefresh();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    /* Don't update the UI of the fragment if it's being hidden. When the activity is destroyed,
     * this method is called, so a null check is needed to prevent a UI update from being called
     * during an activity recreate.
     */
    if (!hidden && getActivity() != null) {
      updateUiOnCreateOrRefresh();
    }
  }

  View getRootView() {
    return rootView;
  }

  /**
   * Sets the heading text for the wizard.
   *
   * @param resId Resource id of the string that will be placed in the heading
   */
  void setHeadingText(int resId) {
    if (resId == EMPTY_TEXT) {
      setText(getActivity().findViewById(R.id.setup_heading), null);
    } else {
      setText(getActivity().findViewById(R.id.setup_heading), getString(resId));
    }
  }

  /**
   * Sets the heading text for the wizard.
   *
   * @param headingString String to be placed in the heading
   */
  void setHeadingText(String headingString) {
    setText(getActivity().findViewById(R.id.setup_heading), headingString);
  }

  /**
   * Sets the subheading text for the wizard.
   *
   * @param resId Resource id of the string that will be placed in the subheading
   */
  void setSubheadingText(int resId) {
    if (resId == EMPTY_TEXT) {
      setText(getActivity().findViewById(R.id.setup_subheading), "");
    } else {
      setText(getActivity().findViewById(R.id.setup_subheading), getString(resId));
    }
  }

  /**
   * Sets the subheading text for the wizard.
   *
   * @param subheadingString String to be placed in the heading
   */
  void setSubheadingText(String subheadingString) {
    setText(getActivity().findViewById(R.id.setup_subheading), subheadingString);
  }

  private void setText(TextView textView, @Nullable String text) {
    if (TextUtils.isEmpty(text)) {
      textView.setVisibility(View.GONE);
    } else {
      textView.setText(text);
      textView.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Returns the name of the current screen. This name is passed to a {@link ScreenViewListener}.
   */
  public String getScreenName() {
    return this.getClass().getSimpleName();
  }

  /**
   * Handle keys pressed when the user is on this screen. The default behavior is to ignore the key
   * press.
   *
   * @param event KeyEvent to be consumed or ignored
   * @return {@code true} If key event was consumed, false otherwise
   */
  public boolean dispatchKeyEvent(KeyEvent event) {
    return false;
  }

  /**
   * Get the index of the next screen to be shown.
   *
   * @return The index of the next screen
   */
  public abstract SetupScreen getNextScreen();

  /** Returns the resource id associated with a specific fragment's view. */
  protected abstract int getLayoutResourceId();

  /**
   * This method should be used to update any shared UI elements, such as heading, that may have
   * changed when the fragment was hidden and re-shown. This method is called in {@link
   * #onViewCreated} and in {@link #onHiddenChanged}.
   */
  protected abstract void updateUiOnCreateOrRefresh();

  /**
   * This method should be overridden if a child class wants to use the current preference value.
   * This is called in {@link #onViewCreated}.
   */
  void updateScreenForCurrentPreferenceValues(View view) {}
}
