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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Setup Wizard screen that lets the user choose one of 3 pre-set auto-scan delay speeds (slow,
 * medium, fast) or choose a custom speed.
 */
public class SetupWizardStepSpeedFragment extends SetupWizardScreenFragment {

  /* Text field to accept custom delay speeds. */
  private EditText customSpeedEditText;

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    RadioButton slowSpeedRadioButton = view.findViewById(R.id.slow_speed_radio_button);
    slowSpeedRadioButton.setText(
        getActivity()
            .getString(
                R.string.slow_radio_button_title,
                getActivity().getString(R.string.pref_auto_scan_time_delay_slow_value)));

    RadioButton mediumSpeedRadioButton = view.findViewById(R.id.medium_speed_radio_button);
    mediumSpeedRadioButton.setText(
        getActivity()
            .getString(
                R.string.medium_radio_button_title,
                getActivity().getString(R.string.pref_auto_scan_time_delay_medium_value)));

    RadioButton fastSpeedRadioButton = view.findViewById(R.id.fast_speed_radio_button);
    fastSpeedRadioButton.setText(
        getActivity()
            .getString(
                R.string.fast_radio_button_title,
                getActivity().getString(R.string.pref_auto_scan_time_delay_fast_value)));

    final RadioButton customSpeedRadioButton = view.findViewById(R.id.custom_speed_radio_button);

    customSpeedEditText = view.findViewById(R.id.custom_speed_edit_text);
    customSpeedEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void afterTextChanged(Editable watchedEditText) {
            final String newValueString = customSpeedEditText.getText().toString();
            if (isADecimal(newValueString)) {
              customSpeedRadioButton.setChecked(true);
              setAutoScanDelay(newValueString);
            }
          }

          @Override
          public void beforeTextChanged(
              CharSequence textBeforeChange,
              int changeStartIndex,
              int changeLength,
              int newTextLength) {}

          @Override
          public void onTextChanged(
              CharSequence textAfterChange,
              int changeStartIndex,
              int replacedTextLength,
              int newTextLength) {}
        });

    RadioGroup speedsRadioGroup = view.findViewById(R.id.autoscan_speeds_radio_group);
    OnCheckedChangeListener onCheckedChangeListener =
        new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(RadioGroup rg, int checkedId) {
            if (checkedId == R.id.fast_speed_radio_button) {
              setAutoScanDelay(getString(R.string.pref_auto_scan_time_delay_fast_value));
            } else if (checkedId == R.id.medium_speed_radio_button) {
              setAutoScanDelay(getString(R.string.pref_auto_scan_time_delay_medium_value));
            } else if (checkedId == R.id.slow_speed_radio_button) {
              setAutoScanDelay(getString(R.string.pref_auto_scan_time_delay_slow_value));
            } else if (checkedId == R.id.custom_speed_radio_button) {
              final String newValueString = customSpeedEditText.getText().toString();
              if (isADecimal(newValueString)) {
                setAutoScanDelay(newValueString);
              }
            }
          }
        };
    speedsRadioGroup.setOnCheckedChangeListener(onCheckedChangeListener);
    onCheckedChangeListener.onCheckedChanged(
        speedsRadioGroup, speedsRadioGroup.getCheckedRadioButtonId());
  }

  /**
   * Handle KeyEvents on this screen. Do not consume the KeyEvent.
   *
   * @param event KeyEvent to be consumed or ignored.
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    /*
     * Redirects captured key events to the EditText. This is necessary as the overridden
     * dispatch key event in SetupWizardActivity was consuming the key events, so we must
     * manually route the events to the EditText here.
     */
    if ((event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD) {
      customSpeedEditText.dispatchKeyEvent(event);
      return true;
    }
    return false;
  }

  @Override
  public SetupScreen getNextScreen() {
    Activity activity = getActivity();
    if (activity == null) {
      return SetupScreen.VIEW_NOT_CREATED;
    }

    if (KeyAssignmentUtils.isConfigurationFunctionalAfterSetup(
        SharedPreferencesUtils.getSharedPreferences(activity), activity)) {
      return SetupScreen.SWITCH_GAME_VALID_CONFIGURATION_SCREEN;
    } else {
      return SetupScreen.SWITCH_GAME_INVALID_CONFIGURATION_SCREEN;
    }
  }

  private boolean isADecimal(String string) {
    if (TextUtils.isEmpty(string)) {
      return false;
    }

    try {
      Double.parseDouble(string);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private void setAutoScanDelay(String newValue) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getActivity());
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(getString(R.string.pref_key_auto_scan_time_delay), newValue);
    editor.apply();
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_step_speed;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.step_speed_heading);
    setSubheadingText(R.string.step_speed_subheading);
  }

  @Override
  protected void updateScreenForCurrentPreferenceValues(View view) {
    RadioGroup scanningMethodRadioGroup = view.findViewById(R.id.autoscan_speeds_radio_group);
    double autoScanDelay = SwitchAccessPreferenceUtils.getAutoScanDelaySeconds(getActivity());
    if (autoScanDelay
        == Double.parseDouble(getString(R.string.pref_auto_scan_time_delay_fast_value))) {
      scanningMethodRadioGroup.check(R.id.fast_speed_radio_button);
    } else if (autoScanDelay
        == Double.parseDouble(getString(R.string.pref_auto_scan_time_delay_medium_value))) {
      scanningMethodRadioGroup.check(R.id.medium_speed_radio_button);
    } else if (autoScanDelay
        == Double.parseDouble(getString(R.string.pref_auto_scan_time_delay_slow_value))) {
      scanningMethodRadioGroup.check(R.id.slow_speed_radio_button);
    } else {
      scanningMethodRadioGroup.check(R.id.custom_speed_radio_button);
      EditText editText = view.findViewById(R.id.custom_speed_edit_text);
      editText.setText(Double.toString(autoScanDelay));
    }
  }
}
