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
 * Asks the user to choose a scanning method for two switches from among Group Selection, Row Column
 * (Keyboard Only) Scanning, and Row Column Scanning.
 */
public class SetupWizardScanningMethodFragment extends SetupWizardScreenFragment {

  /** Indicates whether a screen corresponds to a one switch or a two switch configuration. */
  public enum NumberOfSwitches {
    ONE("OneSwitch"),
    TWO("TwoSwitches");

    private final String name;

    NumberOfSwitches(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private NumberOfSwitches numberOfSwitches;

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RadioGroup scanningMethodRadioGroup = view.findViewById(R.id.scanning_options_radio_group);
    OnCheckedChangeListener scanningMethodChangeListener =
        new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            if (checkedId == R.id.group_selection_radio_button) {
              SwitchAccessPreferenceUtils.setScanningMethod(
                  getActivity(), R.string.group_selection_key);
            } else if (checkedId == R.id.row_column_scanning_radio_button) {
              SwitchAccessPreferenceUtils.setScanningMethod(
                  getActivity(), R.string.row_col_scanning_key);
            } else if (checkedId == R.id.linear_scanning_except_keyboard_radio_button) {
              SwitchAccessPreferenceUtils.setScanningMethod(
                  getActivity(), R.string.views_linear_ime_row_col_key);
            } else if (checkedId == R.id.linear_scanning_radio_button) {
              SwitchAccessPreferenceUtils.setScanningMethod(
                  getActivity(), R.string.linear_scanning_key);
            }
          }
        };
    scanningMethodRadioGroup.setOnCheckedChangeListener(scanningMethodChangeListener);
    /* Call change listener to capture the default highlighted radio button. */
    scanningMethodChangeListener.onCheckedChanged(
        scanningMethodRadioGroup, scanningMethodRadioGroup.getCheckedRadioButtonId());
  }

  /**
   * Get the next screen to be displayed in the setup flow.
   *
   * @return Index of next screen to be shown
   */
  @Override
  public SetupScreen getNextScreen() {
    if (numberOfSwitches == NumberOfSwitches.ONE) {
      return SetupScreen.AUTO_SCAN_KEY_SCREEN;
    } else {
      // There's a small chance that getRootView() can be null if #getNextScreen is called before
      // #onAttach. This can happen if the next buttons are pressed too rapidly. If this happens, we
      // should indicate that the view has not yet been created.
      if (getRootView() == null) {
        return SetupScreen.VIEW_NOT_CREATED;
      }
      if (((RadioButton) getRootView().findViewById(R.id.group_selection_radio_button))
          .isChecked()) {
        return SetupScreen.GROUP_ONE_KEY_SCREEN;
      } else {
        return SetupScreen.NEXT_KEY_SCREEN;
      }
    }
  }

  @Override
  public String getScreenName() {
    return super.getScreenName() + numberOfSwitches.getName();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(
        getString(R.string.pref_key_auto_scan_enabled), numberOfSwitches == NumberOfSwitches.ONE);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    if (savedInstanceState != null) {
      numberOfSwitches =
          savedInstanceState.getBoolean(getString(R.string.pref_key_auto_scan_enabled))
              ? NumberOfSwitches.ONE
              : NumberOfSwitches.TWO;
    }
  }

  public void setNumberOfSwitches(NumberOfSwitches numberOfSwitches) {
    this.numberOfSwitches = numberOfSwitches;
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_scanning_method;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.scanning_method_heading);
    setSubheadingText(SetupWizardScreenFragment.EMPTY_TEXT);
    configureRadioGroupForSwitchNumber();
  }

  private void configureRadioGroupForSwitchNumber() {
    if (numberOfSwitches == NumberOfSwitches.ONE) {
      getRootView().findViewById(R.id.group_selection_radio_button).setVisibility(View.INVISIBLE);
      getRootView()
          .findViewById(R.id.group_selection_description_view)
          .setVisibility(View.INVISIBLE);
    }
  }

  @Override
  protected void updateScreenForCurrentPreferenceValues(View view) {
    RadioGroup scanningMethodRadioGroup = view.findViewById(R.id.scanning_options_radio_group);
    String scanningMethod = SwitchAccessPreferenceUtils.getCurrentScanningMethod(getActivity());
    if (scanningMethod.equals(getString(R.string.linear_scanning_key))) {
      scanningMethodRadioGroup.check(R.id.linear_scanning_radio_button);
    } else if (scanningMethod.equals(getString(R.string.views_linear_ime_row_col_key))) {
      scanningMethodRadioGroup.check(R.id.linear_scanning_except_keyboard_radio_button);
    } else if (scanningMethod.equals(getString(R.string.row_col_scanning_key))) {
      scanningMethodRadioGroup.check(R.id.row_column_scanning_radio_button);
    } else if (scanningMethod.equals(getString(R.string.group_selection_key))) {
      scanningMethodRadioGroup.check(R.id.group_selection_radio_button);
    }
  }
}
