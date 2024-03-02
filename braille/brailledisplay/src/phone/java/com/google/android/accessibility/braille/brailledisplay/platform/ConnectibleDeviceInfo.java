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

package com.google.android.accessibility.braille.brailledisplay.platform;

import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import java.util.Objects;

/** Holds device information about a Bluetooth device, such as one that is seen by a scan. */
public class ConnectibleDeviceInfo {

  public final String deviceName;
  public final String deviceAddress;
  public final boolean isRemembered;
  public final boolean isConnecting;
  public final boolean isConnected;
  @Nullable public final ConnectableDevice device;

  public ConnectibleDeviceInfo(
      String deviceName,
      String deviceAddress,
      boolean isRemembered,
      boolean isConnecting,
      boolean isConnected,
      @Nullable ConnectableDevice btDevice) {
    this.deviceName = deviceName;
    this.deviceAddress = deviceAddress;
    this.isRemembered = isRemembered;
    this.isConnecting = isConnecting;
    this.isConnected = isConnected;
    this.device = btDevice;
  }

  public boolean hasConnectableDevice() {
    return device != null;
  }

  public boolean isConnectingOrConnected() {
    return isConnecting || isConnected;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConnectibleDeviceInfo rowDevice = (ConnectibleDeviceInfo) o;
    return deviceName.equals(rowDevice.deviceName) && Objects.equals(device, rowDevice.device);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceName, device);
  }

  @Override
  public String toString() {
    return "RowDevice{"
        + "='"
        + String.format("%-30s", deviceName)
        + '\''
        + ", "
        + (device != null ? "Vis" : "***")
        + ", "
        + (isRemembered ? "Rem" : "***")
        + ", "
        + (isConnecting ? "Ing" : "***")
        + ", "
        + (isConnected ? "Ted" : "***")
        + '}';
  }
}
