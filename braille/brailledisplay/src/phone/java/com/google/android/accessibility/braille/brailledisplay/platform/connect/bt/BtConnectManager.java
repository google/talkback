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

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.os.BuildCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManager;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableBluetoothDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Handles the listening of bluetooth scanning, bluetooth bonding, and bluetooth radio on/off. */
public class BtConnectManager extends ConnectManager {
  private static final String TAG = "BtConnectManager";

  // How long to keep the scanner scanning. For example, a duration of 60_000 means that after 1
  // minute, scanning should be halted, unless a new notification comes in.
  private static final long KEEP_SCANNING_DURATION_MS = 30_000;

  private final Context context;
  private final Callback callback;
  // Some runtimes, such as emulators, do not have bluetooth; allow that btAdapter might be null.
  @Nullable private final BluetoothAdapter btAdapter;
  private final LinkedHashSet<ConnectableDevice> foundDevices = new LinkedHashSet<>();
  private final MainHandler mainHandler;
  private final BtBondedReceiver btBondedReceiver;
  private final BtOnOffReceiver btOnOffReceiver;
  private final BtScanReceiver btScanReceiver;

  private BtConnector btConnector;
  private BtConnection deviceConnection;

  public BtConnectManager(Context context, Callback callback) {
    Utils.assertMainThread();
    this.context = context;
    this.callback = callback;
    btBondedReceiver = new BtBondedReceiver(context, btBondedReceiverCallback);
    btOnOffReceiver = new BtOnOffReceiver(context, btOnOffReceiverCallback);
    btScanReceiver = new BtScanReceiver(context, btScanReceiverCallback);
    btAdapter =
        ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
    mainHandler = new MainHandler();
  }

  @Override
  public ConnectType getType() {
    return ConnectType.BLUETOOTH;
  }

  /** Instructs this manager to start its behaviors, such as listening for bond events. */
  @Override
  public void onStart() {
    btScanReceiver.registerSelf();
    btOnOffReceiver.registerSelf();
    btBondedReceiver.registerSelf();
    startScanPossibly(Reason.START_STARTED);
  }

  /** Instructs this manager to stop its behaviors. */
  @Override
  public void onStop() {
    btScanReceiver.unregisterSelf();
    btOnOffReceiver.unregisterSelf();
    btBondedReceiver.unregisterSelf();
    stopSearch(Reason.STOP_STOPPED);
    disconnect();
  }

  @Override
  public void startSearch(Reason reason) {
    startScanPossibly(reason);
  }

  @Override
  public void connect(ConnectableDevice device) {
    btConnector = new BtConnector(device, new BtConnectorCallback());
    btConnector.connect();
  }

  @Override
  public void disconnect() {
    if (btConnector != null) {
      btConnector.shutdown();
      btConnector = null;
    }
    if (deviceConnection != null) {
      deviceConnection.shutdown();
      deviceConnection = null;
      callback.onDisconnected();
    }
  }

  @Override
  public boolean isConnecting() {
    return btConnector != null;
  }

  @Override
  public boolean isConnected() {
    return deviceConnection != null;
  }

  /** Gets the set of currently bonded devices. */
  @Override
  public Set<ConnectableDevice> getBondedDevices() {
    Set<BluetoothDevice> bondedDevices = new HashSet<>();
    if (btAdapter != null && mayConnect()) {
      // According to the javadoc for {@link BluetoothAdapter#getBondedDevices}, that method can
      // return null; and we have a ticket of this happening in the field.
      bondedDevices = btAdapter.getBondedDevices();
      if (bondedDevices == null) {
        return new HashSet<>();
      }
    }
    return bondedDevices.stream()
        .map(
            bluetoothDevice ->
                ConnectableBluetoothDevice.builder().setBluetoothDevice(bluetoothDevice).build())
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectingDevice() {
    return Optional.ofNullable(btConnector).map(BtConnector::getDevice);
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectedDevice() {
    return Optional.ofNullable(deviceConnection).map(BtConnection::getDevice);
  }

  @Override
  public void sendOutgoingPacket(byte[] packet) {
    if (deviceConnection != null) {
      deviceConnection.sendOutgoingPacket(packet);
    }
  }

  /** Returns whether or not this manager is actively scanning. */
  @Override
  public boolean isScanning() {
    // return btAdapter.isDiscovering();
    return isScanningOngoing();
  }

  /** Gets a copy of the list of devices found since scanning began. */
  @Override
  public Collection<ConnectableDevice> getConnectableDevices() {
    // Return a copy, otherwise clients can modify our data model.
    return new ArrayList<>(foundDevices);
  }

  private boolean isScanningOngoing() {
    return mainHandler.isOngoing();
  }

  private void startScanPossibly(Reason reason) {
    BrailleDisplayLog.d(TAG, "startScanPossibly reason: " + reason);
    foundDevices.clear();
    callback.onDeviceListCleared();
    mainHandler.scheduleStopChecks();
    boolean startSuccess = false;
    boolean shouldStartScan =
        reason == Reason.START_USER_SELECTED_RESCAN || !isConnectingOrConnected();
    if (shouldStartScan && btAdapter != null && mayScan()) {
      startSuccess = btAdapter.startDiscovery();
    }
    if (!startSuccess) {
      BrailleDisplayLog.e(TAG, "startScanPossibly failed to start discovery");
      stopSearch(Reason.STOP_DISCOVERY_FAILED);
      callback.onSearchFailure();
    }
  }

  @Override
  public void stopSearch(Reason reason) {
    BrailleDisplayLog.d(TAG, "stopScan " + reason);
    mainHandler.cancelStopCheck();
    if (btAdapter != null && mayScan()) {
      btAdapter.cancelDiscovery();
    }
  }

  // Invoking BluetoothAdapter getBondedDevices method S+ throws SecurityException if permission
  // absent.
  private boolean mayConnect() {
    return !BuildCompat.isAtLeastS()
        || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
  }

  // Invoking BluetoothAdapter discovery methods S+ throws SecurityException if permission absent.
  private boolean mayScan() {
    return !BuildCompat.isAtLeastS()
        || (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED);
  }

  private boolean isConnectingOrConnected() {
    return isConnecting() || isConnected();
  }

  @SuppressLint("HandlerLeak")
  private class MainHandler extends Handler {

    private static final int MSG_STOP = 0;

    private void scheduleStopChecks() {
      removeMessages(MSG_STOP);
      sendEmptyMessageDelayed(MSG_STOP, KEEP_SCANNING_DURATION_MS);
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_STOP) {
        BrailleDisplayLog.v(TAG, "invoke stopDiscovery from handler");
        if (btAdapter != null && mayScan()) {
          btAdapter.cancelDiscovery();
        }
      }
    }

    private void cancelStopCheck() {
      removeMessages(MSG_STOP);
    }

    private boolean isOngoing() {
      return hasMessages(MSG_STOP);
    }
  }

  private class BtConnectorCallback implements BtConnector.Callback {

    @Override
    public void onConnectStarted() {
      callback.onConnectStarted();
    }

    @Override
    public void onConnectSuccess(BtConnection btConnection) {
      BrailleDisplayLog.d(TAG, "onConnectSuccess");
      BtConnectManager.this.deviceConnection = btConnection;
      callback.onConnected(btConnection);
    }

    @Override
    public void onConnectFailure(ConnectableDevice device, Exception exception) {
      BrailleDisplayLog.d(TAG, "onConnectFailure: " + exception.getMessage());
      callback.onConnectFailure(device, exception);
    }
  }

  private final BtScanReceiver.Callback btScanReceiverCallback =
      new BtScanReceiver.Callback() {
        @Override
        public void onDiscoveryStarted() {
          BrailleDisplayLog.d(TAG, "onDiscoveryStarted");
          callback.onSearchStatusChanged();
        }

        @Override
        public void onDiscoveryFinished() {
          callback.onSearchStatusChanged();
          if (btAdapter != null && isScanningOngoing() && mayScan()) {
            BrailleDisplayLog.d(TAG, "onDiscoveryFinished restart discovery");
            btAdapter.startDiscovery();
          } else {
            BrailleDisplayLog.d(TAG, "onDiscoveryFinished do not restart discovery");
          }
        }

        @Override
        public void onDeviceSeen(BluetoothDevice device) {
          ConnectableBluetoothDevice connectedDevice =
              ConnectableBluetoothDevice.builder().setBluetoothDevice(device).build();
          foundDevices.add(connectedDevice);
          callback.onDeviceSeen(connectedDevice);
        }
      };

  private final BtOnOffReceiver.Callback btOnOffReceiverCallback =
      new BtOnOffReceiver.Callback() {
        @Override
        public void onBluetoothTurningOn() {
          BrailleDisplayLog.d(TAG, "onBluetoothTurningOn");
        }

        @Override
        public void onBluetoothTurningOff() {
          BrailleDisplayLog.d(TAG, "onBluetoothTurningOff");
        }

        @Override
        public void onBluetoothTurnedOn() {
          BrailleDisplayLog.d(TAG, "onBluetoothTurnedOn");
          callback.onConnectivityEnabled(/* enabled= */ true);
        }

        @Override
        public void onBluetoothTurnedOff() {
          BrailleDisplayLog.d(TAG, "onBluetoothTurnedOff");
          callback.onConnectivityEnabled(/* enabled= */ false);
        }
      };

  private final BtBondedReceiver.Callback btBondedReceiverCallback =
      new BtBondedReceiver.Callback() {
        @Override
        public void onBonded(BluetoothDevice device) {
          BrailleDisplayLog.d(TAG, "onBonded " + device.getName());
          callback.onDeviceSeen(
              ConnectableBluetoothDevice.builder().setBluetoothDevice(device).build());
        }
      };
}
