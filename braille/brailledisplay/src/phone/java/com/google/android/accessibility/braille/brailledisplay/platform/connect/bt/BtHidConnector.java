/*
 * Copyright 2024 Google Inc.
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
import android.os.Handler;
import androidx.annotation.RequiresApi;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.Connector;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableBluetoothDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.hid.HidConnector;
import com.google.android.accessibility.braille.common.FakeBrailleDisplayController;

/**
 * Sets up a Bluetooth hid connection to a remote device that is advertising itself or already
 * bonded.
 */
@RequiresApi(api = 35)
public class BtHidConnector extends HidConnector {
  private static final String TAG = "BtHidConnector";
  private static final int BRAILLE_DISPLAY_CONTROLLER_DELAY_MS = 1000;
  private final FakeBrailleDisplayController brailleDisplayController;

  public BtHidConnector(
      Context context,
      ConnectableDevice device,
      Connector.Callback callback,
      FakeBrailleDisplayController brailleDisplayController) {
    super(context, device, callback, brailleDisplayController);
    this.brailleDisplayController = brailleDisplayController;
  }

  @Override
  public void connect() {
    if (!isAvailable()) {
      BrailleDisplayLog.w(TAG, "Braille HID is not supported.");
      return;
    }
    BluetoothDevice device = ((ConnectableBluetoothDevice) getDevice()).bluetoothDevice();
    if (device.createBond()) {
      BrailleDisplayLog.d(TAG, "Wait for bonding result.");
    } else {
      if (brailleDisplayController.isConnected()) {
        BrailleDisplayLog.w(TAG, "BrailleDisplayController already connected");
        return;
      }
      new Handler()
          .postDelayed(
              () ->
                  getBrailleDisplayController()
                      .connect(
                          ((ConnectableBluetoothDevice) getDevice()).bluetoothDevice(),
                          new BrailleDisplayCallback()),
              BRAILLE_DISPLAY_CONTROLLER_DELAY_MS);
    }
  }

  @Override
  public void disconnect() {
    if (!isAvailable()) {
      BrailleDisplayLog.w(TAG, "Braille HID is not supported.");
      return;
    }
    getBrailleDisplayController().disconnect();
  }
}
