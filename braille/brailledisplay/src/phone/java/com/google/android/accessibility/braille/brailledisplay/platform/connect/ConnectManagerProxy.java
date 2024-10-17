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

package com.google.android.accessibility.braille.brailledisplay.platform.connect;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager.AccessibilityServiceContextProvider;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.bt.BtConnectManager;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.usb.UsbConnectManager;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** A proxy relay connect tasks to connect manager. */
public class ConnectManagerProxy extends ConnectManager {
  private final BtConnectManager btConnectManager;
  private final UsbConnectManager usbConnectManager;
  private ConnectManager connectManager;

  public ConnectManagerProxy(Context context, ConnectManager.Callback callback) {
    btConnectManager = new BtConnectManager(context, callback);
    usbConnectManager = new UsbConnectManager(context, callback);
    connectManager = btConnectManager;
  }

  /** Set accessibility service context provider. */
  @Override
  public void setAccessibilityServiceContextProvider(
      AccessibilityServiceContextProvider accessibilityServiceContextProvider) {
    btConnectManager.setAccessibilityServiceContextProvider(accessibilityServiceContextProvider);
    usbConnectManager.setAccessibilityServiceContextProvider(accessibilityServiceContextProvider);
  }

  /** Switch connect manager based one connect type. */
  public void switchTo(ConnectType type) {
    if (type == ConnectType.USB) {
      btConnectManager.onStop();
      connectManager = usbConnectManager;
    } else {
      usbConnectManager.onStop();
      connectManager = btConnectManager;
    }
  }

  @Override
  public ConnectType getType() {
    return connectManager.getType();
  }

  @Override
  public void onStart() {
    connectManager.onStart();
  }

  @Override
  public void onStop() {
    connectManager.onStop();
  }

  @Override
  public void startSearch(Reason reason) {
    connectManager.startSearch(reason);
  }

  @Override
  public void stopSearch(Reason reason) {
    connectManager.stopSearch(reason);
  }

  @Override
  public void connect(ConnectableDevice device, boolean manual) {
    connectManager.connect(device, manual);
  }

  @Override
  public void disconnect() {
    connectManager.disconnect();
  }

  @Override
  public void forget(ConnectableDevice device) {
    connectManager.forget(device);
  }

  @Override
  public void sendOutgoingPacket(byte[] packet) {
    connectManager.sendOutgoingPacket(packet);
  }

  @Override
  public boolean isConnecting() {
    return connectManager.isConnecting();
  }

  @Override
  public boolean isConnected() {
    return connectManager.isConnected();
  }

  @Override
  public boolean isScanning() {
    return connectManager.isScanning();
  }

  @Override
  public Collection<ConnectableDevice> getConnectableDevices() {
    return connectManager.getConnectableDevices();
  }

  @Override
  public Set<ConnectableDevice> getBondedDevices() {
    return connectManager.getBondedDevices();
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectingDevice() {
    return connectManager.getCurrentlyConnectingDevice();
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectedDevice() {
    return connectManager.getCurrentlyConnectedDevice();
  }

  @Override
  public boolean isHidDevice(ConnectableDevice device) {
    return connectManager.isHidDevice(device);
  }

  @VisibleForTesting
  public void testing_setConnectManager(ConnectManager connectManager) {
    this.connectManager = connectManager;
  }
}
