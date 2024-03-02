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
import android.content.Context;
import android.os.Bundle;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ActionReceiver;

/** A BroadcastReceiver that listens for the turning on and off this device's bluetooth radio. */
public class BtOnOffReceiver extends ActionReceiver<BtOnOffReceiver, BtOnOffReceiver.Callback> {

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onBluetoothTurningOn();

    void onBluetoothTurningOff();

    void onBluetoothTurnedOn();

    void onBluetoothTurnedOff();
  }

  public BtOnOffReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
      final int state = extras.getInt(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
      if (state == BluetoothAdapter.STATE_OFF) {
        callback.onBluetoothTurnedOff();
      } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
        callback.onBluetoothTurningOff();
      } else if (state == BluetoothAdapter.STATE_ON) {
        callback.onBluetoothTurnedOn();
      } else if (state == BluetoothAdapter.STATE_TURNING_ON) {
        callback.onBluetoothTurningOn();
      }
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {
      BluetoothAdapter.ACTION_STATE_CHANGED,
    };
  }
}
