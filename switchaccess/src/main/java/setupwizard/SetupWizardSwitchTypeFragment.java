/*
 * Copyright (C) 2018 Google Inc.
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

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import android.widget.RadioButton;
import android.widget.TextView;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Setup Wizard screen that asks users whether they will use a USB switch or a bluetooth switch. The
 * following screen will help them connect their switch.
 */
public class SetupWizardSwitchTypeFragment extends SetupWizardScreenFragment {

  /* The Bluetooth adapter associated with this context. */
  private BluetoothAdapter bluetoothAdapter;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  }

  @Override
  public void onStart() {
    super.onStart();

    // If the bluetooth adapter is null, it means that the device does not support Bluetooth, and we
    // should disable the option to pair a bluetooth switch.
    if (bluetoothAdapter == null) {
      RadioButton bluetoothSwitch = getRootView().findViewById(R.id.bluetooth_switch_radio_button);
      bluetoothSwitch.setChecked(false);
      bluetoothSwitch.setEnabled(false);
      TextView bluetoothDescriptionText =
          getRootView().findViewById(R.id.bluetooth_switch_option_description);
      bluetoothDescriptionText.setText(R.string.bluetooth_unsupported_description_text);
    }
  }

  @Override
  public SetupScreen getNextScreen() {
    // There's a small chance that getRootView() can be null if #getNextScreen is called before
    // #onAttach. This can happen if the next buttons are pressed too rapidly. If this happens, we
    // should indicate that the view has not yet been created.
    if (getRootView() == null) {
      return SetupScreen.VIEW_NOT_CREATED;
    }

    if (((RadioButton) getRootView().findViewById(R.id.usb_switch_radio_button)).isChecked()) {
      return SetupScreen.USB_DEVICE_LIST_SCREEN;
    } else {
      return SetupScreen.PAIR_BLUETOOTH_SCREEN;
    }
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_switch_type;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.intro_heading);
    setSubheadingText(SetupWizardScreenFragment.EMPTY_TEXT);
  }

  @VisibleForTesting
  public void setBluetoothAdapter(BluetoothAdapter bluetoothAdapter) {
    this.bluetoothAdapter = bluetoothAdapter;
  }
}
