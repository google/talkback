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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.ViewStub;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Setup Wizard screen that lets the user choose one of 3 pre-set auto-scan delay speeds (slow,
 * medium, fast) or choose a custom speed.
 */
public class SetupWizardStepSpeed extends SetupScreen {

  /* Text field to accept custom delay speeds. */
  private EditText mCustomSpeedEditText;

  private OnCheckedChangeListener mOnCheckedChangeListener;

  private RadioGroup mSpeedsRadioGroup;

  public SetupWizardStepSpeed(Context context, SetupWizardActivity.ScreenIterator iterator) {
    super(context, iterator);
    setHeadingText(R.string.step_speed_heading);
    setSubheadingText(R.string.step_speed_subheading);

    /* Sets the xml stub to visible for the first time. This by default inflates the stub. */
    ((ViewStub) findViewById(R.id.switch_access_setup_step_speed_import)).setVisibility(VISIBLE);
  }

  @Override
  public void onStart() {
    super.onStart();
    RadioButton slowSpeedRadioButton = (RadioButton) findViewById(R.id.slow_speed_radio_button);
    slowSpeedRadioButton.setText(
        getContext()
            .getString(
                R.string.slow_radio_button_title,
                getContext().getString(R.string.pref_auto_scan_time_delay_slow_value)));

    RadioButton mediumSpeedRadioButton = (RadioButton) findViewById(R.id.medium_speed_radio_button);
    mediumSpeedRadioButton.setText(
        getContext()
            .getString(
                R.string.medium_radio_button_title,
                getContext().getString(R.string.pref_auto_scan_time_delay_medium_value)));

    RadioButton fastSpeedRadioButton = (RadioButton) findViewById(R.id.fast_speed_radio_button);
    fastSpeedRadioButton.setText(
        getContext()
            .getString(
                R.string.fast_radio_button_title,
                getContext().getString(R.string.pref_auto_scan_time_delay_fast_value)));

    final RadioButton customSpeedRadioButton =
        (RadioButton) findViewById(R.id.custom_speed_radio_button);

    mCustomSpeedEditText = (EditText) findViewById(R.id.custom_speed_edit_text);
    mCustomSpeedEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void afterTextChanged(Editable watchedEditText) {
            final String newValueString = mCustomSpeedEditText.getText().toString();
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
              int replacedtextLength,
              int newTextLength) {}
        });

    mSpeedsRadioGroup = (RadioGroup) findViewById(R.id.autoscan_speeds_radio_group);
    mOnCheckedChangeListener =
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
              final String newValueString = mCustomSpeedEditText.getText().toString();
              if (isADecimal(newValueString)) {
                setAutoScanDelay(newValueString);
              } else {
                return;
              }
            }
          }
        };
    mSpeedsRadioGroup.setOnCheckedChangeListener(mOnCheckedChangeListener);
    setStepSpeedViewVisible(true);
    mOnCheckedChangeListener.onCheckedChanged(
        mSpeedsRadioGroup, mSpeedsRadioGroup.getCheckedRadioButtonId());
  }

  @Override
  public void onStop() {
    super.onStop();

    mSpeedsRadioGroup.setOnCheckedChangeListener(null);
    setStepSpeedViewVisible(false);
  }

  private void setStepSpeedViewVisible(boolean visible) {
    final ScrollView stepSpeedView =
        (ScrollView) findViewById(R.id.switch_access_setup_step_speed_inflated_import);
    stepSpeedView.setVisibility(visible ? VISIBLE : GONE);
    stepSpeedView.setFocusable(visible);
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
      mCustomSpeedEditText.dispatchKeyEvent(event);
    }
    return false;
  }

  @Override
  public int getNextScreen() {
    return SetupWizardActivity.INDEX_SWITCH_GAME_SCREEN;
  }

  @Override
  public String getScreenName() {
    return SetupWizardStepSpeed.class.getSimpleName();
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
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(getString(R.string.pref_key_auto_scan_time_delay), newValue);
    editor.commit();
  }
}
