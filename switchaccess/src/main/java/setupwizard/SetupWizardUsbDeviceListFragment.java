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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Setup Wizard screen that shows the user a list of connected USB devices. */
public class SetupWizardUsbDeviceListFragment extends SetupWizardScreenFragment {

  /* The array adapter used to display the names of the currently connected usb devices. */
  private ArrayAdapter<String> usbArrayAdapter;

  /* The UsbManager associated with this fragment. The UsbManager class allows access of USB state
   * and facilitates communication with USB devices. */
  private UsbManager usbManager;

  /* This broadcast receiver has a lifecycle from SetupWizardUsbDevicesFragment#onStart() to
   * SetupWizardUsbDevices#onStop() and listens for device attached and detached broadcasts.
   */
  private final BroadcastReceiver broadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (action == null) {
            return;
          }
          UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            if (usbArrayAdapter.getPosition(getDisplayableUsbDeviceName(usbDevice)) < 0) {
              usbArrayAdapter.add(getDisplayableUsbDeviceName(usbDevice));
              usbArrayAdapter.notifyDataSetChanged();
              if (usbArrayAdapter.getCount() == 1) {
                updateScreenBasedOnIfUsbDeviceIsConnected(true);
              }
            }
          } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
            usbArrayAdapter.remove(getDisplayableUsbDeviceName(usbDevice));
            if (usbArrayAdapter.getCount() == 0) {
              updateScreenBasedOnIfUsbDeviceIsConnected(false);
            }
          }
        }
      };

  @Override
  public void onCreate(@Nullable Bundle savedInstance) {
    super.onCreate(savedInstance);
    usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
    usbArrayAdapter =
        new ArrayAdapter<>(getActivity(), R.layout.usb_item_layout, R.id.usb_device_name);
  }

  @Override
  public void onStart() {
    super.onStart();
    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
    ListView listView = getRootView().findViewById(R.id.connected_usb_devices_list);
    listView.setAdapter(usbArrayAdapter);
    for (UsbDevice device : deviceList.values()) {
      usbArrayAdapter.add(getDisplayableUsbDeviceName(device));
    }
    usbArrayAdapter.notifyDataSetChanged();
    if (usbArrayAdapter.getCount() == 0) {
      updateScreenBasedOnIfUsbDeviceIsConnected(false);
    }

    IntentFilter intent = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    intent.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    getActivity().registerReceiver(broadcastReceiver, intent);
  }

  @Override
  public void onStop() {
    super.onStop();
    getActivity().unregisterReceiver(broadcastReceiver);
  }

  @Override
  public SetupScreen getNextScreen() {
    return SetupScreen.NUMBER_OF_SWITCHES_SCREEN;
  }

  /**
   * Sets the UsbManager to be used by this fragment.
   *
   * @param usbManager the UsbManager to use
   */
  @VisibleForTesting
  public void setUsbManager(UsbManager usbManager) {
    this.usbManager = usbManager;
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_usb_device_list;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.connect_usb_heading);
    setSubheadingText(SetupWizardScreenFragment.EMPTY_TEXT);
  }

  /* Return a concatenated string of the given device's manufacturer name and product name. This
   * format is different and more readable than what is returned by {@link UsbDevice#getDeviceName}.
   * An example of a string returned by this method is "Logitech USB Keyboard." */
  private String getDisplayableUsbDeviceName(@Nullable UsbDevice usbDevice) {
    return (usbDevice == null)
        ? ""
        : String.format("%s %s", usbDevice.getManufacturerName(), usbDevice.getProductName());
  }

  private void updateScreenBasedOnIfUsbDeviceIsConnected(boolean isAtLeastOneDeviceConnected) {
    getRootView()
        .findViewById(R.id.usb_at_least_one_device_view)
        .setVisibility(isAtLeastOneDeviceConnected ? View.VISIBLE : View.GONE);
    getRootView()
        .findViewById(R.id.usb_no_devices_view)
        .setVisibility(isAtLeastOneDeviceConnected ? View.GONE : View.VISIBLE);
  }
}
