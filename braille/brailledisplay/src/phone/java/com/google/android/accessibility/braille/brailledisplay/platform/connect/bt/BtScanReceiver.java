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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ActionReceiver;

/** A BroadcastReceiver that listens for Bluetooth scan results. */
public class BtScanReceiver extends ActionReceiver<BtScanReceiver, BtScanReceiver.Callback> {

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onDiscoveryStarted();

    void onDiscoveryFinished();

    void onDeviceSeen(BluetoothDevice device);
  }

  public BtScanReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
      callback.onDiscoveryStarted();
    } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
      callback.onDiscoveryFinished();
    } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
      BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
      callback.onDeviceSeen(device);
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {
      BluetoothDevice.ACTION_FOUND,
      BluetoothAdapter.ACTION_DISCOVERY_STARTED,
      BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
    };
  }
}
