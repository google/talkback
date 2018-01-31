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
import android.view.View;
import android.view.ViewStub;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Asks the user to choose a scanning method for two switches from among Option Scanning, Row Column
 * (Keyboard Only) Scanning, and Row Column Scanning.
 */
public class SetupWizardScanningMethod extends SetupScreen {

  /** Indicates whether a screen corresponds to a one switch or a two switch configuration. */
  public enum NumberOfSwitches {
    ONE("OneSwitch"),
    TWO("TwoSwitches");

    private final String mName;

    private NumberOfSwitches(String name) {
      mName = name;
    }

    public String getName() {
      return mName;
    }
  };

  private final NumberOfSwitches mNumberOfSwitches;

  /**
   * @param context Context the screen was created in
   * @param iterator Iterator to control movement through the wizard
   * @param numberOfSwitches Enum value to indicate the number of switches being configured
   */
  public SetupWizardScanningMethod(
      Context context,
      SetupWizardActivity.ScreenIterator iterator,
      NumberOfSwitches numberOfSwitches) {
    super(context, iterator);

    mNumberOfSwitches = numberOfSwitches;
    setHeadingText(R.string.scanning_method_heading);
    setSubheadingText(SetupScreen.EMPTY_TEXT);

    /* Sets the xml stub to visible for the first time. This by default inflates the stub. */
    ((ViewStub) findViewById(R.id.switch_access_setup_scanning_method_import))
        .setVisibility(VISIBLE);
  }

  @Override
  public void onStart() {
    super.onStart();
    final SharedPreferences sharedPreferences =
        SharedPreferencesUtils.getSharedPreferences(getContext());

    configureRadioGroupForSwitchNumber();

    RadioGroup scanningMethodRadioGroup =
        (RadioGroup) findViewById(R.id.scanning_options_radio_group);
    OnCheckedChangeListener scanningMethodChangeListener =
        new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (checkedId == R.id.option_scanning_radio_button) {
              editor.putString(
                  getString(R.string.pref_scanning_methods_key),
                  getString(R.string.option_scanning_key));
            } else if (checkedId == R.id.row_column_scanning_radio_button) {
              editor.putString(
                  getString(R.string.pref_scanning_methods_key),
                  getString(R.string.row_col_scanning_key));
            } else if (checkedId == R.id.linear_scanning_radio_button) {
              editor.putString(
                  getString(R.string.pref_scanning_methods_key),
                  getString(R.string.views_linear_ime_row_col_key));
            }
            editor.commit();
          }
        };
    scanningMethodRadioGroup.setOnCheckedChangeListener(scanningMethodChangeListener);
    /* Call change listener to capture the default highlighted radio button. */
    scanningMethodChangeListener.onCheckedChanged(
        scanningMethodRadioGroup, scanningMethodRadioGroup.getCheckedRadioButtonId());
    setScanningMethodConfigViewVisible(true);
  }

  @Override
  public void onStop() {
    super.onStop();

    RadioGroup scanningMethodRadioGroup =
        (RadioGroup) findViewById(R.id.scanning_options_radio_group);
    scanningMethodRadioGroup.setOnCheckedChangeListener(null);

    setScanningMethodConfigViewVisible(false);
  }

  private void configureRadioGroupForSwitchNumber() {
    if (mNumberOfSwitches == NumberOfSwitches.ONE) {
      ((RadioButton) findViewById(R.id.option_scanning_radio_button)).setVisibility(View.INVISIBLE);
      ((TextView) findViewById(R.id.option_scanning_description_view))
          .setVisibility(View.INVISIBLE);
    }
  }

  private void setScanningMethodConfigViewVisible(boolean visible) {
    final ScrollView scanningMethodView =
        (ScrollView) findViewById(R.id.switch_access_setup_scanning_method_inflated_import);
    scanningMethodView.setVisibility(visible ? VISIBLE : GONE);
    scanningMethodView.setFocusable(visible);
  }

  /**
   * Do not consume KeyEvents passed to this screen.
   *
   * @param event KeyEvent to be consumed or ignored
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return false;
  }

  /**
   * Get the next screen to be displayed in the setup flow.
   *
   * @return Index of next screen to be shown
   */
  @Override
  public int getNextScreen() {
    if (mNumberOfSwitches == NumberOfSwitches.ONE) {
      return SetupWizardActivity.INDEX_AUTO_SCAN_KEY_SCREEN;
    } else {
      if (((RadioButton) findViewById(R.id.option_scanning_radio_button)).isChecked()) {
        return SetupWizardActivity.INDEX_OPTION_ONE_KEY_SCREEN;
      } else {
        return SetupWizardActivity.INDEX_NEXT_KEY_SCREEN;
      }
    }
  }

  @Override
  public String getScreenName() {
    return SetupWizardScanningMethod.class.getSimpleName() + mNumberOfSwitches.getName();
  }
}
