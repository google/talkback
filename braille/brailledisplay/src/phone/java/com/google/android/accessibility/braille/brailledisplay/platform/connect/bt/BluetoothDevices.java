/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brailledisplay.platform.connect.bt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.util.UUID;

/** Utilities related to {@link BluetoothDevice}. */
public class BluetoothDevices {

  private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

  /** Invokes createInsecureRfcommSocketToServiceRecord with the standard UUID. */
  public static BluetoothSocket createInsecureRfcommSocketToServiceRecord(BluetoothDevice device)
      throws IOException {
    return device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
  }

  /** Invokes createRfcommSocketToServiceRecord with the standard UUID. */
  public static BluetoothSocket createRfcommSocketToServiceRecord(BluetoothDevice device)
      throws IOException {
    return device.createRfcommSocketToServiceRecord(MY_UUID);
  }
}
