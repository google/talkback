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
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Setup Wizard screen that asks the user whether they would like to configure Switch Access for one
 * or two switches.
 */
public class SetupWizardNumberOfSwitchesFragment extends SetupWizardScreenFragment {

  /**
   * Add the appropriate action to update the shared preferences to the radio buttons in this view.
   */
  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    RadioGroup numberOfSwitchesRadioGroup = view.findViewById(R.id.number_of_switches_radio_group);

    OnCheckedChangeListener numberOfSwitchesChangeListener =
        (radioGroup, checkedId) -> {
          if (checkedId == R.id.one_switch_radio_button) {
            SwitchAccessPreferenceUtils.setAutoScanEnabled(getActivity(), true);
          } else if (checkedId == R.id.two_switches_radio_button) {
            SwitchAccessPreferenceUtils.setAutoScanEnabled(getActivity(), false);
          }
        };
    numberOfSwitchesRadioGroup.setOnCheckedChangeListener(numberOfSwitchesChangeListener);
    /* Call change listener to capture the default highlighted radio button. */
    numberOfSwitchesChangeListener.onCheckedChanged(
        numberOfSwitchesRadioGroup, numberOfSwitchesRadioGroup.getCheckedRadioButtonId());
  }

  @Override
  public SetupScreen getNextScreen() {
    // There's a small chance that getRootView() can be null if #getNextScreen is called before
    // #onAttach. This can happen if the next buttons are pressed too rapidly. If this happens, we
    // should indicate that the view has not yet been created.
    if (getRootView() == null) {
      return SetupScreen.VIEW_NOT_CREATED;
    }

    if (((RadioButton) getRootView().findViewById(R.id.two_switches_radio_button)).isChecked()) {
      return SetupScreen.TWO_SWITCH_OPTION_SCREEN;
    } else {
      return SetupScreen.ONE_SWITCH_OPTION_SCREEN;
    }
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_number_of_switches;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.number_of_switches_heading);
    setSubheadingText(SetupWizardScreenFragment.EMPTY_TEXT);
  }

  @Override
  protected void updateScreenForCurrentPreferenceValues(View view) {
    if (SwitchAccessPreferenceUtils.isAutoScanEnabled(getActivity())) {
      ((RadioButton) view.findViewById(R.id.one_switch_radio_button)).setChecked(true);
    } else {
      ((RadioButton) view.findViewById(R.id.two_switches_radio_button)).setChecked(true);
    }
  }
}
