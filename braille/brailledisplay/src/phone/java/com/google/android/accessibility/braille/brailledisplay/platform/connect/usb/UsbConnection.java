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

package com.google.android.accessibility.braille.brailledisplay.platform.connect.usb;

import android.os.Handler;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;

/** A usb connection traffic handler. */
public class UsbConnection implements D2dConnection {
  private static final int DELAY_MS = 20;
  private final ConnectableDevice device;
  private final Handler handler;

  public UsbConnection(ConnectableDevice device) {
    this.device = device;
    handler = new Handler();
  }

  @Override
  public void open(Callback callback) {
    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            callback.onRead();
            handler.postDelayed(this, DELAY_MS);
          }
        },
        DELAY_MS);
  }

  @Override
  public void sendOutgoingPacket(byte[] packet) {}

  @Override
  public void shutdown() {
    handler.removeCallbacksAndMessages(null);
  }

  @Override
  public ConnectableDevice getDevice() {
    return device;
  }
}
