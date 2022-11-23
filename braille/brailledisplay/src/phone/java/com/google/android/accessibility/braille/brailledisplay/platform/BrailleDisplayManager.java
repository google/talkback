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

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.CreationArguments;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.Encoder;

/** Manages the interface to a braille display, on behalf of an AccessibilityService. */
public class BrailleDisplayManager {
  private static final String TAG = "BrailleDisplayManager";
  private final Context context;
  private final Controller controller;
  private final Encoder.Factory encoderFactory;

  private boolean connectedService;
  private boolean connectedToDisplay;
  private Connectioneer connectioneer;
  private Connectioneer.AspectTraffic aspectTraffic;
  private Connectioneer.AspectDisplayProperties aspectDisplayProperties;

  private final PowerManager.WakeLock wakeLock;

  @Nullable private Displayer displayer;

  @SuppressLint("InvalidWakeLockTag")
  public BrailleDisplayManager(
      Context context, Controller controller, Encoder.Factory encoderFactory) {
    this.context = context;
    this.controller = controller;
    this.encoderFactory = encoderFactory;
    wakeLock =
        ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "BrailleDisplay");
  }

  /** Notifies this manager the owning service has started. */
  public void onServiceStarted() {
    connectedService = true;

    connectioneer =
        Connectioneer.getInstance(
            new CreationArguments(
                context.getApplicationContext(), encoderFactory.getDeviceNameFilter()));
    connectioneer.onServiceEnabledChanged(true);
    connectioneer.aspectConnection.attach(connectionCallback);
    aspectTraffic = connectioneer.aspectTraffic.attach(trafficCallback);
    aspectDisplayProperties = connectioneer.aspectDisplayProperties;
  }

  /** Notifies this manager the owning service has stopped. */
  public void onServiceStopped() {
    controller.onDestroy();
    connectedService = false;
    connectioneer.aspectConnection.detach(connectionCallback);
    connectioneer.aspectTraffic.detach(trafficCallback);
    connectioneer.onServiceEnabledChanged(false);
    stopDisplayer();
  }

  /** Sends an {@see AccessibilityEvent} to this manager for processing. */
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (canSendRenderPackets()) {
      controller.onAccessibilityEvent(accessibilityEvent);
    }
  }

  private void stopDisplayer() {
    if (displayer != null) {
      displayer.stop();
      displayer = null;
    }
  }

  private boolean canSendPackets() {
    return connectedService && connectedToDisplay;
  }

  private boolean canSendRenderPackets() {
    return connectedService
        && connectedToDisplay
        && displayer != null
        && displayer.isDisplayReady();
  }

  // Keeps the phone awake as if there was user activity registered by the system.
  @SuppressLint("WakelockTimeout")
  private void keepAwake() {
    // (Old note): Acquiring the lock and immediately releasing it keeps the phone awake.  We don't
    // use aqcuire() with a timeout because it adds an unnecessary context switch.
    wakeLock.acquire();
    wakeLock.release();
  }

  private final Connectioneer.AspectConnection.Callback connectionCallback =
      new Connectioneer.AspectConnection.Callback() {
        @Override
        public void onScanningChanged() {
          BrailleDisplayLog.d(TAG, "onScanningChanged");
        }

        @Override
        public void onDeviceListCleared() {
          BrailleDisplayLog.d(TAG, "onDeviceListCleared");
        }

        @Override
        public void onConnectStarted() {
          controller.onConnectStarted();
        }

        @Override
        public void onConnectableDeviceSeenOrUpdated(BluetoothDevice bluetoothDevice) {
          BrailleDisplayLog.d(TAG, "onConnectableDeviceSeenOrUpdated");
        }

        @Override
        public void onConnectionStatusChanged(boolean connected, RemoteDevice remoteDevice) {
          BrailleDisplayLog.d(
              TAG,
              "onConnectionStatusChanged deviceName = " + remoteDevice + " connected:" + connected);
          BrailleDisplayManager.this.connectedToDisplay = connected;
          if (connected) {
            controller.onConnected();
            displayer = new Displayer(context, displayerCallback, encoderFactory, remoteDevice);
            displayer.start();
          } else {
            controller.onDisconnected();
            stopDisplayer();
          }
        }

        @Override
        public void onConnectFailed(@Nullable String deviceName) {
          BrailleDisplayLog.d(TAG, "onConnectFailed deviceName:" + deviceName);
        }
      };

  private final Connectioneer.AspectTraffic.Callback trafficCallback =
      new Connectioneer.AspectTraffic.Callback() {
        @Override
        public void onPacketArrived(byte[] buffer) {
          BrailleDisplayLog.v(TAG, "onPacketArrived " + buffer.length + " bytes");
          // An incoming packet may arrive while the displayer is still null, because the
          // notification informing this instance that the connection is open (which leads to the
          // instantiation of the displayer), is received after the opening of that connection; in
          // that case we ignore the incoming packet.
          if (displayer != null) {
            displayer.consumePacketFromDevice(buffer);
            displayer.readCommand();
          }
        }
      };

  private final Displayer.Callback displayerCallback =
      new Displayer.Callback() {
        @Override
        public void onStartFailed() {
          BrailleDisplayLog.e(TAG, "onStartFailed");
          controller.onDisconnected();
          connectioneer.aspectConnection.onDisplayerFailedDisconnectFromDevice(
              displayer.getDeviceAddress());
          stopDisplayer();
        }

        @Override
        public void onSendPacketToDisplay(byte[] packet) {
          if (canSendPackets()) {
            BrailleDisplayLog.v(TAG, "onSendPacketToDisplay");
            aspectTraffic.onSendTrafficOutgoingMessage(packet);
          }
        }

        @Override
        public void onDisplayReady(BrailleDisplayProperties bdr) {
          if (canSendRenderPackets()) {
            BrailleDisplayLog.d(TAG, "onDisplayReady");
            aspectDisplayProperties.onDisplayPropertiesArrived(bdr);
            controller.onDisplayerReady(displayer);
          }
        }

        @Override
        public void onBrailleInputEvent(BrailleInputEvent brailleInputEvent) {
          if (canSendRenderPackets()) {
            BrailleDisplayLog.v(TAG, "onReadCommandArrived " + brailleInputEvent);
            keepAwake();
            controller.onBrailleInputEvent(displayer, brailleInputEvent);
          }
        }
      };

  /** Indicates the data of a remote device. */
  public static class RemoteDevice {
    public final String deviceName;
    public final String address;

    RemoteDevice(String deviceName, String address) {
      this.deviceName = deviceName;
      this.address = address;
    }

    @Override
    public String toString() {
      return String.format("RemoteDevice {deviceName=%s, address=%s}", deviceName, address);
    }
  }
}
