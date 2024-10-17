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

package com.google.android.accessibility.braille.brltty.device;

import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;
import com.google.android.accessibility.braille.common.DeviceProvider;

/** A Factory to generate object of BRLTTY device based on given information. */
public class BrlttyParameterProviderFactory {

  /** Returns a BrlttyDevice according to ConnectableDevice. */
  public ParameterProvider getParameterProvider(boolean useHid, DeviceProvider<?> provider) {
    if (useHid) {
      return new BrlttyHidParameterProvider();
    } else if (provider.getDevice() instanceof BluetoothDevice) {
      return new BrlttyBluetoothParameterProvider((BluetoothDevice) provider.getDevice());
    } else if (provider.getDevice() instanceof UsbDevice) {
      return new BrlttyUsbParameterProvider((UsbDevice) provider.getDevice());
    }
    throw new IllegalArgumentException("No matching connect type.");
  }
}
