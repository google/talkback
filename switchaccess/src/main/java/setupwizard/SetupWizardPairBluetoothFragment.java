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

import static android.app.Activity.RESULT_CANCELED;

import android.Manifest.permission;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.RequiresPermission;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessLogger;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.BluetoothDeviceDescriptionSet;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.BluetoothDeviceListAdapter;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.BluetoothEventManager;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.BluetoothEventManager.BluetoothCallback;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.ComparableBluetoothDevice;
import com.google.common.annotations.VisibleForTesting;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Setup Wizard screen that helps the user pair a bluetooth switch device. */
public class SetupWizardPairBluetoothFragment extends SetupWizardScreenFragment
    implements BluetoothCallback {

  /*
   * {@link RecyclerView.Adapter} used for displaying the list of paired bluetooth devices in a
   * {@link RecyclerView}.
   */
  private BluetoothDeviceListAdapter pairedDevicesAdapter;

  /*
   * {@link RecyclerView.Adapter} used for displaying the list of available bluetooth devices in a
   * {@link RecyclerView}.
   */
  private BluetoothDeviceListAdapter availableDevicesAdapter;

  /*
   * The bluetooth event manager used for receiving bluetooth device discovery and pairing events.
   */
  private BluetoothEventManager bluetoothEventManager;

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == BluetoothEventManager.REQUEST_ENABLE_BLUETOOTH
        && resultCode == RESULT_CANCELED) {
      /* The user denied the request to enable Bluetooth. */
      updateScreenBasedOnBluetoothPermission(false);
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    BluetoothDeviceDescriptionSet descriptionManager =
        BluetoothDeviceDescriptionSet.builder()
            .setComputerContentDescription(
                getString(R.string.bluetooth_computer_content_description))
            .setPhoneContentDescription(getString(R.string.bluetooth_phone_content_description))
            .setPeripheralContentDescription(
                getString(R.string.bluetooth_peripheral_content_description))
            .setImagingContentDescription(getString(R.string.bluetooth_imaging_content_description))
            .setHeadphoneContentDescription(
                getString(R.string.bluetooth_headphone_content_description))
            .setDefaultBluetoothDeviceContentDescription(
                getString(R.string.bluetooth_default_content_description))
            .setConnectedDescription(getString(R.string.bluetooth_connected_description))
            .setConnectingDescription(getString(R.string.bluetooth_connecting_description))
            .setUnavailableDeviceDescription(
                getString(R.string.bluetooth_device_unavailable_description))
            .setReconnectingUnsupportedTitle(getString(R.string.reconnecting_unsupported_title))
            .setReconnectingUnsupportedMessage(getString(R.string.reconnecting_unsupported_message))
            .setLaunchBluetoothSettingsButtonText(
                getString(R.string.reconnecting_unsupported_launch_bluetooth_settings))
            .build();

    pairedDevicesAdapter =
        new BluetoothDeviceListAdapter(
            descriptionManager,
            R.layout.bluetooth_item_layout,
            R.id.bluetooth_device_name,
            R.id.bluetooth_device_icon);
    availableDevicesAdapter =
        new BluetoothDeviceListAdapter(
            descriptionManager,
            R.layout.bluetooth_item_layout,
            R.id.bluetooth_device_name,
            R.id.bluetooth_device_icon);
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RecyclerView pairedDevicesListView = view.findViewById(R.id.paired_bluetooth_devices_list);
    pairedDevicesListView.setNestedScrollingEnabled(false);
    LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
    layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
    pairedDevicesListView.setAdapter(pairedDevicesAdapter);
    pairedDevicesListView.setLayoutManager(layoutManager);

    RecyclerView availableDevicesListView =
        view.findViewById(R.id.available_bluetooth_devices_list);
    availableDevicesListView.setNestedScrollingEnabled(false);
    availableDevicesListView.setAdapter(availableDevicesAdapter);
    layoutManager = new LinearLayoutManager(getActivity());
    layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
    availableDevicesListView.setLayoutManager(layoutManager);

    Button enableBluetoothButton = view.findViewById(R.id.enable_bluetooth_button);
    enableBluetoothButton.setOnClickListener(
        clickedView -> bluetoothEventManager.requestEnableBluetoothAndInitiateDiscoveryOnSuccess());
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  @Override
  public void onStart() {
    super.onStart();

    /* If we've previously set the Bluetooth event manager in the setter, don't attempt to set it
     * here. */
    if (bluetoothEventManager == null) {
      bluetoothEventManager =
          new BluetoothEventManager(
              getActivity(),
              SwitchAccessLogger.getOrCreateInstance((SetupWizardActivity) getActivity()));
    }
    bluetoothEventManager.registerCallback(this);
    bluetoothEventManager.requestEnableBluetoothAndInitiateDiscoveryOnSuccess();
  }

  @Override
  public void onStop() {
    super.onStop();
    bluetoothEventManager.stopListeningForAllEvents();
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (hidden) {
      bluetoothEventManager.stopDiscovery();
    } else {
      bluetoothEventManager.requestEnableBluetoothAndInitiateDiscoveryOnSuccess();
    }
  }

  @Override
  public SetupScreen getNextScreen() {
    return SetupScreen.NUMBER_OF_SWITCHES_SCREEN;
  }

  /**
   * Updates the Bluetooth event manager with a custom manager class.
   *
   * @param bluetoothEventManager the Bluetooth event manager to use in bluetooth interactions
   */
  @VisibleForTesting
  public void setBluetoothEventManager(BluetoothEventManager bluetoothEventManager) {
    this.bluetoothEventManager = bluetoothEventManager;
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_pair_bluetooth;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.pair_bluetooth_heading);
    setSubheadingText(getString(R.string.pair_bluetooth_subheading));
  }

  private void updateScreenBasedOnBluetoothPermission(boolean isPermissionGranted) {
    getRootView()
        .findViewById(R.id.bluetooth_enabled_layout)
        .setVisibility(isPermissionGranted ? View.VISIBLE : View.GONE);
    getRootView()
        .findViewById(R.id.bluetooth_not_enabled_layout)
        .setVisibility(isPermissionGranted ? View.GONE : View.VISIBLE);
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  @Override
  public void onBluetoothStateChanged(int state) {
    if (state == BluetoothAdapter.STATE_OFF) {
      availableDevicesAdapter.reset();
      pairedDevicesAdapter.reset();
      updateScreenBasedOnBluetoothPermission(false);
    } else if (state == BluetoothAdapter.STATE_ON) {
      updateScreenBasedOnBluetoothPermission(true);
    }
  }

  @Override
  public void onDeviceDiscovered(ComparableBluetoothDevice bluetoothDevice) {
    availableDevicesAdapter.add(bluetoothDevice);
  }

  @Override
  public void onDeviceConnectionStateChanged(ComparableBluetoothDevice bluetoothDevice) {
    pairedDevicesAdapter.updateBluetoothDevice(bluetoothDevice);
    availableDevicesAdapter.updateBluetoothDevice(bluetoothDevice);
  }

  @Override
  public void onDevicePaired(ComparableBluetoothDevice bluetoothDevice) {
    pairedDevicesAdapter.add(bluetoothDevice);
    availableDevicesAdapter.remove(bluetoothDevice);

    // Update the view after the first paired device is found.
    if (pairedDevicesAdapter.getItemCount() > 0) {
      getRootView().findViewById(R.id.bluetooth_no_paired_devices_view).setVisibility(View.GONE);
    }
  }
}
