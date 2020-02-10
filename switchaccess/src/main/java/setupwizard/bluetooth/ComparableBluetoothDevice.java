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
package com.google.android.accessibility.switchaccess.setupwizard.bluetooth;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothClass.Device.Major;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import androidx.annotation.RequiresPermission;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessBluetoothEventTypeEnum.BluetoothEventType;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Wrapper class for {@link BluetoothDevice} that implements the {@link Comparable} interface to
 * custom sort the bluetooth devices in a list.
 */
public class ComparableBluetoothDevice implements Comparable<ComparableBluetoothDevice> {

  private static final String TAG = "ComparableBTDevice";

  /* TODO: Investigate supporting disconnecting a Bluetooth device. This is dependent
   * on Bluetooth API changes that enable this functionality. */
  /**
   * Represents the connection state of this Bluetooth device. By default, this will be {@link
   * BluetoothConnectionState#UNKNOWN}. If the {@link BluetoothEventManager} receives an {@link
   * BluetoothDevice#ACTION_BOND_STATE_CHANGED} or a user initiates a pairing request on the
   * associated Bluetooth device, this should be updated.
   *
   * <p>"Disconnecting" from a BluetoothDevice isn't represented because disconnecting from a
   * Bluetooth device in {@link BluetoothDeviceListAdapter} isn't currently supported.
   */
  public enum BluetoothConnectionState {
    UNKNOWN,
    CONNECTED,
    CONNECTING,
    UNAVAILABLE
  }

  /* The context associated with this ComparableBluetoothDevice. */
  private final Context context;

  /* The bluetooth device associated with this ComparableBluetoothDevice. */
  private final BluetoothDevice bluetoothDevice;

  /* The name of the bluetooth device associated with this ComparableBluetoothDevice. */
  private String name;

  /* The Bluetooth class associated with this ComparableBluetoothDevice. */
  @Nullable private BluetoothClass bluetoothClass;

  /*
   * The received signal strength indicator (rssi) of the bluetooth device, used for sorting the
   * devices by their signal strength.
   */
  private short rssi;

  /* The local bluetooth adapter, used for initiating bluetooth device discovery and getting paired
   * devices. */
  private final BluetoothAdapter bluetoothAdapter;

  /* The connection state of this ComparableBluetoothDevice. This is unknown by default and only
   * changes by a user-initiated pairing request on the associated Bluetooth device. This might not
   * represent the Bluetooth device's actual connection state, since ComparableBluetoothDevice isn't
   * guaranteed to have the most up-to-date information on connection state. */
  private BluetoothConnectionState connectionState = BluetoothConnectionState.UNKNOWN;

  /* The listener that will be notified of the actions performed by the user when connecting a
   * bluetooth device.
   */
  private final BluetoothDeviceActionListener bluetoothDeviceActionListener;

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public ComparableBluetoothDevice(
      Context context,
      BluetoothAdapter bluetoothAdapter,
      BluetoothDevice bluetoothDevice,
      BluetoothDeviceActionListener bluetoothDeviceActionListener) {
    this.context = context;
    this.bluetoothDevice = bluetoothDevice;
    this.bluetoothAdapter = bluetoothAdapter;
    this.bluetoothDeviceActionListener = bluetoothDeviceActionListener;
    name = this.bluetoothDevice.getName();
    if (name == null) {
      name = this.bluetoothDevice.getAddress();
    }

    bluetoothClass = bluetoothDevice.getBluetoothClass();
  }

  /**
   * Sets the rssi (received signal strength indicator) for the associated bluetooth device.
   *
   * @param rssi the received signal strength indicator of the associated bluetooth device
   */
  public void setRssi(short rssi) {
    this.rssi = rssi;
  }

  public String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    if (name != null) {
      this.name = name;
    }
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public int getBondState() {
    return bluetoothDevice.getBondState();
  }

  /** Returns the {@link BluetoothClass} of the associated Bluetooth device. */
  @Nullable
  public BluetoothClass getBluetoothClass() {
    return bluetoothClass;
  }

  /** Updates the {@link BluetoothClass} associated with this ComparableBluetoothDevice. */
  public void setBluetoothClass(@Nullable BluetoothClass bluetoothClass) {
    this.bluetoothClass = bluetoothClass;
  }

  /**
   * Starts the pairing process with the bluetooth device associated with this
   * ComparableBluetoothDevice. This cancels discovery if the bluetooth adapter is currently
   * discovering.
   *
   * @return if the device was paired successfully
   */
  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public boolean startPairing() {
    boolean pairingSucceeded = pairDevice();

    if (bluetoothDeviceActionListener != null) {
      bluetoothDeviceActionListener.onBluetoothDeviceAction(
          pairingSucceeded
              ? BluetoothEventType.PAIR_BLUETOOTH_SWITCH_SUCCESS
              : BluetoothEventType.PAIR_BLUETOOTH_SWITCH_FAILED);
    }

    return pairingSucceeded;
  }

  /* TODO: Once API limitations are resolved, this method should attempt to reconnect
   * the device instead of launching a dialog if the application has adequate permission. */
  /**
   * Attempt to reconnect to a previously paired device. Launches a dialog explaining limitations of
   * reconnecting previously paired devices if the Bluetooth device's connection state is unknown or
   * unavailable.
   *
   * @param reconnectionTitle the dialog title
   * @param reconnectionMessage the dialog message
   * @param reconnectionButtonText the text of the button that takes the user to Bluetooth Settings
   */
  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public void reconnect(
      String reconnectionTitle, String reconnectionMessage, String reconnectionButtonText) {
    boolean reconnectionSucceeded = pairDevice();

    if (connectionState == BluetoothConnectionState.UNKNOWN
        || connectionState == BluetoothConnectionState.UNAVAILABLE) {
      AlertDialog.Builder bluetoothSettingsBuilder = new AlertDialog.Builder(context);
      bluetoothSettingsBuilder.setTitle(reconnectionTitle);
      bluetoothSettingsBuilder.setMessage(reconnectionMessage);

      bluetoothSettingsBuilder.setNegativeButton(
          android.R.string.cancel, (dialog, id) -> dialog.cancel());

      bluetoothSettingsBuilder.setPositiveButton(
          reconnectionButtonText,
          (dialog, id) -> {
            try {
              Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
              context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
              LogUtils.e(TAG, "Bluetooth Settings Activity not found");
            }

            if (bluetoothDeviceActionListener != null) {
              bluetoothDeviceActionListener.onBluetoothDeviceAction(
                  BluetoothEventType.LAUNCHED_BLUETOOTH_SETTINGS);
            }
            dialog.cancel();
          });

      AlertDialog bluetoothSettingsDialog = bluetoothSettingsBuilder.create();
      bluetoothSettingsDialog.show();
    }

    if (bluetoothDeviceActionListener != null) {
      bluetoothDeviceActionListener.onBluetoothDeviceAction(
          reconnectionSucceeded
              ? BluetoothEventType.RECONNECT_PREVIOUSLY_PAIRED_DEVICE_SUCCESS
              : BluetoothEventType.RECONNECT_PREVIOUSLY_PAIRED_DEVICE_FAILED);
    }
  }

  @Override
  public int hashCode() {
    return bluetoothDevice.getAddress().hashCode();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof ComparableBluetoothDevice)) {
      return false;
    }

    return bluetoothDevice.equals(((ComparableBluetoothDevice) o).bluetoothDevice);
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  @Override
  public int compareTo(ComparableBluetoothDevice another) {
    /* Current implementation is based on how devices are sorted in Android settings with the
     * inclusion of a preference for peripheral devices. Not all properties used for sorting in
     * Android settings are easily accessible. */

    /* Prefer paired above not paired. */
    int comparison =
        (another.getBondState() == BluetoothDevice.BOND_BONDED ? 1 : 0)
            - (getBondState() == BluetoothDevice.BOND_BONDED ? 1 : 0);
    if (comparison != 0) {
      return comparison;
    }

    /* Prefer peripheral bluetoothDevice type over other bluetoothDevice types. */
    if ((another.bluetoothClass != null) && (bluetoothClass != null)) {
      int anotherMajorDeviceClass = another.bluetoothClass.getMajorDeviceClass();
      // The null checker is being conservative and assumes that BluetoothClass#getMajorDeviceClass
      // has side effects that could cause bluetoothClass to be null again at this point. The ideal
      // solution would be to put a @SideEffectFree annotation on BluetoothClass#getMajorDeviceClass
      // but we don't have control over that class.
      @SuppressWarnings("nullness:dereference.of.nullable")
      int majorDeviceClass = bluetoothClass.getMajorDeviceClass();
      comparison =
          (anotherMajorDeviceClass == Major.PERIPHERAL ? 1 : 0)
              - (majorDeviceClass == Major.PERIPHERAL ? 1 : 0);
      if (comparison != 0) {
        return comparison;
      }
    } else {
      /* Prefer non-null Bluetooth classes over null Bluetooth classes. */
      comparison = ((another.bluetoothClass != null) ? 1 : 0) - ((bluetoothClass != null) ? 1 : 0);
      if (comparison != 0) {
        return comparison;
      }
    }

    /* Prefer stronger signal above weaker signal. */
    comparison = another.rssi - rssi;
    if (comparison != 0) {
      return comparison;
    }

    /* Fallback on name. */
    return name.compareTo(another.name);
  }

  /**
   * Returns the connection state of this ComparableBluetoothDevice. This is unknown by default and
   * only changes by a user-initiated pairing request on the associated Bluetooth device. This is
   * different from {@link #getBondState()} as a device can be bonded without being connected. For
   * example, a previously paired Bluetooth device that is currently turned off would be considered
   * bonded but unconnected.
   *
   * <p>Due to the way that Bluetooth connection states are managed, we are unable to easily capture
   * current connection status and can only represent changes in connection status after the
   * application has started. Therefore, this might not represent the Bluetooth device's actual
   * connection state, since ComparableBluetoothDevice isn't guaranteed to have the most up-to-date
   * information on connection state.
   *
   * @return the connection state of this ComparableBluetoothDevice
   */
  public BluetoothConnectionState getConnectionState() {
    return connectionState;
  }

  /**
   * Sets the connection state of this ComparableBluetoothDevice. This is unknown by default and
   * should be set after an event that causes the connection state of the associated Bluetooth
   * device to be known, such as a user-initiated pairing request on the associated Bluetooth
   * device.
   *
   * @param connectionState the connection state of this Bluetooth device
   */
  public void setConnectionState(BluetoothConnectionState connectionState) {
    this.connectionState = connectionState;
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  private boolean pairDevice() {
    /* Cancel discovery as pairing is unreliable when discovering. */
    if (bluetoothAdapter.isDiscovering()) {
      bluetoothAdapter.cancelDiscovery();
    }

    boolean deviceAvailable = bluetoothDevice.createBond();
    if (deviceAvailable) {
      connectionState = BluetoothConnectionState.CONNECTING;
    } else if (connectionState != BluetoothConnectionState.CONNECTED) {
      connectionState = BluetoothConnectionState.UNAVAILABLE;
    }

    return deviceAvailable;
  }

  /** Interface to monitor the actions performed by the user when connecting a bluetooth device. */
  public interface BluetoothDeviceActionListener {
    /** Called when an action is performed by the user when connecting a bluetooth device. */
    void onBluetoothDeviceAction(BluetoothEventType eventType);
  }
}
