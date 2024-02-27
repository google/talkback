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
import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.CreationArguments;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.Encoder;

/** Manages the interface to a braille display, on behalf of an AccessibilityService. */
public class BrailleDisplayManager {
  private static final String TAG = "BrailleDisplayManager";
  private final Context context;
  private final Controller controller;
  private final PowerManager.WakeLock wakeLock;
  private final Displayer displayer;
  private final Connectioneer connectioneer;
  private boolean connectedService;
  private boolean connectedToDisplay;

  /** Provides instance of accessibility service context. */
  public interface AccessibilityServiceContextProvider {
    Context getAccessibilityServiceContext();
  }

  @SuppressLint("InvalidWakeLockTag")
  public BrailleDisplayManager(
      Context context, Controller controller, Encoder.Factory encoderFactory) {
    this.context = context;
    this.controller = controller;
    wakeLock =
        ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
    displayer = new Displayer(context, displayerCallback, encoderFactory);
    connectioneer =
        Connectioneer.getInstance(
            new CreationArguments(
                context.getApplicationContext(), encoderFactory.getDeviceNameFilter()));
  }

  /** Sets accessibility service context provider. */
  public void setAccessibilityServiceContextProvider(
      AccessibilityServiceContextProvider accessibilityServiceContextProvider) {
    connectioneer.setAccessibilityServiceContextProvider(accessibilityServiceContextProvider);
  }

  /** Notifies this manager the owning service has started. */
  public void onServiceStarted() {
    connectedService = true;
    connectioneer.onServiceEnabledChanged(true);
    connectioneer.aspectConnection.attach(connectionCallback);
    connectioneer.aspectTraffic.attach(trafficCallback);
  }

  /** Notifies this manager the owning service has stopped. */
  public void onServiceStopped() {
    controller.onDestroy();
    connectedService = false;
    connectioneer.aspectConnection.detach(connectionCallback);
    connectioneer.aspectTraffic.detach(trafficCallback);
    connectioneer.onServiceEnabledChanged(false);
    displayer.stop();
  }

  /** Sends an {@code AccessibilityEvent} to this manager for processing. */
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (canSendRenderPackets()) {
      controller.onAccessibilityEvent(accessibilityEvent);
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
  private void keepAwake() {
    wakeLock.acquire(/* timeout= */ 0);
    try {
      wakeLock.release();
    } catch (RuntimeException e) {
      // A wakelock acquired with a timeout may be released by the system before calling
      // `release`.
      // Ignore: already released by timeout.
    }
  }

  @VisibleForTesting
  Displayer.Callback testing_getDisplayerCallback() {
    return displayerCallback;
  }

  @VisibleForTesting
  Displayer testing_getDisplayer() {
    return displayer;
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
        public void onConnectableDeviceSeenOrUpdated(ConnectableDevice device) {
          BrailleDisplayLog.d(TAG, "onConnectableDeviceSeenOrUpdated");
        }

        @Override
        public void onConnectionStatusChanged(ConnectStatus status, ConnectableDevice device) {
          BrailleDisplayLog.d(
              TAG, "onConnectionStatusChanged deviceName = " + device + " connected:" + status);
          connectedToDisplay = status == ConnectStatus.CONNECTED;
          if (status == ConnectStatus.CONNECTED) {
            controller.onConnected();
            displayer.start(device);
          } else if (status == ConnectStatus.DISCONNECTED) {
            controller.onDisconnected();
            displayer.stop();
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
          }
        }

        @Override
        public void onRead() {
          if (canSendPackets()) {
            displayer.readCommand();
          }
        }
      };

  private final Displayer.Callback displayerCallback =
      new Displayer.Callback() {
        @Override
        public void onStartFailed() {
          BrailleDisplayLog.e(TAG, "onStartFailed");
          connectioneer.aspectConnection.onDisplayerStartFailed(displayer.getDeviceAddress());
          displayer.stop();
        }

        @Override
        public void onSendPacketToDisplay(byte[] packet) {
          if (canSendPackets()) {
            BrailleDisplayLog.v(TAG, "onSendPacketToDisplay");
            connectioneer.aspectTraffic.onSendTrafficOutgoingMessage(packet);
          }
        }

        @Override
        public void onDisplayReady(BrailleDisplayProperties bdr) {
          if (canSendRenderPackets()) {
            BrailleDisplayLog.d(TAG, "onDisplayReady");
            connectioneer.aspectDisplayProperties.onDisplayPropertiesArrived(bdr);
            controller.onDisplayerReady(displayer);
          }
        }

        @Override
        public void onBrailleInputEvent(BrailleInputEvent brailleInputEvent) {
          if (canSendRenderPackets()) {
            BrailleDisplayLog.v(TAG, "onReadCommandArrived " + brailleInputEvent);
            KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager.isKeyguardLocked()) {
              keepAwake();
            }
            controller.onBrailleInputEvent(brailleInputEvent);
          }
        }
      };
}
