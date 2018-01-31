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

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.ViewStub;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Setup Wizard screen that asks the user whether they would like to configure Switch Access for one
 * or two switches.
 */
public class SetupWizardNumberOfSwitches extends SetupScreen {

  /**
   * Initializes the screen that allows the user to choose the number of switches that they wish to
   * use.
   *
   * @param context The Activity context
   * @param iterator The iterator for controlling movement between setup wizard screens
   */
  public SetupWizardNumberOfSwitches(Context context, SetupWizardActivity.ScreenIterator iterator) {
    super(context, iterator);
    setHeadingText(R.string.number_of_switches_heading);
    setSubheadingText(SetupScreen.EMPTY_TEXT);

    /* Sets the xml stub to visible for the first time. This by default inflates the stub. */
    ((ViewStub) findViewById(R.id.switch_access_setup_number_of_switches_import))
        .setVisibility(VISIBLE);
  }

  /**
   * Add the appropriate action to update the shared preferences to the radio buttons in this view.
   */
  @Override
  public void onStart() {
    super.onStart();
    RadioGroup numberOfSwitchesRadioGroup =
        (RadioGroup) findViewById(R.id.number_of_switches_radio_group);

    OnCheckedChangeListener numberOfSwitchesChangeListener =
        new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
            SharedPreferences.Editor editor = prefs.edit();
            if (checkedId == R.id.one_switch_radio_button) {
              editor.putBoolean(getString(R.string.pref_key_auto_scan_enabled), true);
            } else if (checkedId == R.id.two_switches_radio_button) {
              editor.putBoolean(getString(R.string.pref_key_auto_scan_enabled), false);
            }
            editor.commit();
          }
        };
    numberOfSwitchesRadioGroup.setOnCheckedChangeListener(numberOfSwitchesChangeListener);
    /* Call change listener to capture the default highlighted radio button. */
    numberOfSwitchesChangeListener.onCheckedChanged(
        numberOfSwitchesRadioGroup, numberOfSwitchesRadioGroup.getCheckedRadioButtonId());
    setNumberOfSwitchesViewVisible(true);
  }

  @Override
  public void onStop() {
    super.onStop();
    RadioGroup numberOfSwitchesRadioGroup =
        (RadioGroup) findViewById(R.id.number_of_switches_radio_group);

    numberOfSwitchesRadioGroup.setOnCheckedChangeListener(null);

    setNumberOfSwitchesViewVisible(false);
  }

  private void setNumberOfSwitchesViewVisible(boolean isVisible) {
    final ScrollView numberOfSwitchesView =
        (ScrollView) findViewById(R.id.switch_access_setup_number_of_switches_inflated_import);
    numberOfSwitchesView.setVisibility(isVisible ? VISIBLE : GONE);
    numberOfSwitchesView.setFocusable(isVisible);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return false;
  }

  @Override
  public int getNextScreen() {
    if (((RadioButton) findViewById(R.id.two_switches_radio_button)).isChecked()) {
      return SetupWizardActivity.INDEX_TWO_SWITCH_OPTION_SCREEN;
    } else {
      return SetupWizardActivity.INDEX_ONE_SWITCH_OPTION_SCREEN;
    }
  }

  @Override
  public String getScreenName() {
    return SetupWizardNumberOfSwitches.class.getSimpleName();
  }
}
