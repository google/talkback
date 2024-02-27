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
import android.content.Context;
import android.os.Bundle;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ActionReceiver;

/** A BroadcastReceiver that listens for the bonding of this device with a bluetooth device. */
public class BtBondedReceiver extends ActionReceiver<BtBondedReceiver, BtBondedReceiver.Callback> {

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onBonded(BluetoothDevice device);
  }

  public BtBondedReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
      int bondState = extras.getInt(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
      if (bondState == BluetoothDevice.BOND_BONDED) {
        BluetoothDevice bthDev = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
        callback.onBonded(bthDev);
      }
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {
      BluetoothDevice.ACTION_BOND_STATE_CHANGED,
    };
  }
}
