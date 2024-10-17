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
package com.google.android.accessibility.braille.brailledisplay.platform.connect.hid;

import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.utils.Consumer;

/**
 * A hid connection traffic handler. It doesn't read or write packets from braille display because
 * it's been handled by accessibility framework. The read result will callback from
 * BrailleDisplayController.BrailleDisplayCallback#onInput().
 */
public class HidConnection implements D2dConnection {
  private final ConnectableDevice device;
  private final Consumer<D2dConnection.Callback> callbackConsumer;

  public HidConnection(
      ConnectableDevice device, Consumer<D2dConnection.Callback> callbackConsumer) {
    device.setUseHid(true);
    this.device = device;
    this.callbackConsumer = callbackConsumer;
  }

  @Override
  public void open(D2dConnection.Callback callback) {
    // Don't need to open to read for hid. Pass the callback back for onRead.
    callbackConsumer.accept(callback);
  }

  @Override
  public void sendOutgoingPacket(byte[] packet) {}

  @Override
  public void shutdown() {
    callbackConsumer.accept(null);
  }

  @Override
  public ConnectableDevice getDevice() {
    return device;
  }
}
