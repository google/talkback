/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.brltty;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import java.util.Set;

/** Provides Bluetooth data for BRLTTY native library requested. */
public class BluetoothConnection {
  protected static final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  protected final long remoteAddressValue;
  protected final byte[] remoteAddressBytes = new byte[6];
  private static BluetoothDevice[] pairedDevices = null;

  public BluetoothConnection(long address) {
    remoteAddressValue = address;
    long value = remoteAddressValue;
    int i = remoteAddressBytes.length;
    while (i > 0) {
      remoteAddressBytes[--i] = (byte) (value & 0XFF);
      value >>= 8;
    }
  }

  public static boolean isUp() {
    if (bluetoothAdapter == null) {
      return false;
    }
    return bluetoothAdapter.isEnabled();
  }

  public static String getName(long address) {
    return new BluetoothConnection(address).getName();
  }

  public final String getName() {
    BluetoothDevice device = getDevice();
    if (device == null) {
      return null;
    }
    return device.getName();
  }

  public final BluetoothDevice getDevice() {
    if (!isUp()) {
      return null;
    }
    return bluetoothAdapter.getRemoteDevice(remoteAddressBytes);
  }

  public static int getPairedDeviceCount() {
    if (isUp()) {
      Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
      if (devices != null) {
        pairedDevices = devices.toArray(new BluetoothDevice[devices.size()]);
        return pairedDevices.length;
      }
    }
    pairedDevices = null;
    return 0;
  }

  public static String getPairedDeviceAddress(int index) {
    BluetoothDevice device = getPairedDevice(index);
    if (device == null) {
      return null;
    }
    return device.getAddress();
  }

  public static String getPairedDeviceName(int index) {
    BluetoothDevice device = getPairedDevice(index);
    if (device == null) {
      return null;
    }
    return device.getName();
  }

  private static BluetoothDevice getPairedDevice(int index) {
    if (index >= 0) {
      if (pairedDevices != null) {
        if (index < pairedDevices.length) {
          return pairedDevices[index];
        }
      }
    }
    return null;
  }
}
