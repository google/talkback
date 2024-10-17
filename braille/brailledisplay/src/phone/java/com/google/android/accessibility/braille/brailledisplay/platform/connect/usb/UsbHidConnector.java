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
package com.google.android.accessibility.braille.brailledisplay.platform.connect.usb;

import android.content.Context;
import androidx.annotation.RequiresApi;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.Connector;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableUsbDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.hid.HidConnector;
import com.google.android.accessibility.braille.common.FakeBrailleDisplayController;

/** Sets up a usb connection using Hid protocol. */
@RequiresApi(api = 35)
public class UsbHidConnector extends HidConnector {
  private static final String TAG = "UsbHidConnector";

  public UsbHidConnector(
      Context context,
      ConnectableDevice device,
      Connector.Callback callback,
      FakeBrailleDisplayController controller) {
    super(context, device, callback, controller);
  }

  @Override
  public void connect() {
    if (!isAvailable()) {
      BrailleDisplayLog.w(TAG, "Braille HID is not supported.");
      return;
    }
    if (getBrailleDisplayController().isConnected()) {
      BrailleDisplayLog.w(TAG, "BrailleDisplayController already connected");
      return;
    }
    getBrailleDisplayController()
        .connect(((ConnectableUsbDevice) getDevice()).usbDevice(), new BrailleDisplayCallback());
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
