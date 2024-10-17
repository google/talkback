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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.os.BuildCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManager;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.Connector;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableBluetoothDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import com.google.android.accessibility.utils.SettingsUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Handles the listening of bluetooth scanning, bluetooth bonding, and bluetooth radio on/off. */
public class BtConnectManager extends ConnectManager {
  private static final String TAG = "BtConnectManager";

  // How long to keep the scanner scanning. For example, a duration of 60_000 means that after 1
  // minute, scanning should be halted, unless a new notification comes in.
  private static final long KEEP_SCANNING_DURATION_MS = 30_000;
  private static final String HID_UUID = "00001124-0000-1000-8000-00805f9b34fb";
  private final Context context;
  private final ConnectManager.Callback connectorManagerCallback;
  // Some runtimes, such as emulators, do not have bluetooth; allow that btAdapter might be null.
  @Nullable private final BluetoothAdapter btAdapter;
  private final LinkedHashSet<ConnectableDevice> foundDevices = new LinkedHashSet<>();
  private final MainHandler mainHandler;
  private final BtConnectStateReceiver btBondedReceiver;
  private final BtOnOffReceiver btOnOffReceiver;
  private final BtScanReceiver btScanReceiver;
  private Connector btConnector;
  private D2dConnection deviceConnection;

  public BtConnectManager(Context context, ConnectManager.Callback callback) {
    Utils.assertMainThread();
    this.context = context;
    this.connectorManagerCallback = callback;
    btBondedReceiver = new BtConnectStateReceiver(context, btBondedReceiverCallback);
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
  public void connect(ConnectableDevice device, boolean manual) {
    // Cancel discovery because it otherwise slows down the connection.
    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
    BrailleDisplayLog.d(TAG, "connect: " + device);
    BluetoothDevice bluetoothDevice = ((ConnectableBluetoothDevice) device).bluetoothDevice();
    boolean result = bluetoothDevice.createBond();
    BrailleDisplayLog.d(TAG, "createBond: " + result);
    if (result) {
      // We have a bond state listener that monitors for the result.
      BrailleDisplayLog.d(TAG, "Wait for bonding result.");
    } else {
      if (BuildCompat.isAtLeastV() && useHid(context, device)) {
        BrailleDisplayLog.i(TAG, "Braille HID is supported.");
        connectorManagerCallback.onConnectStarted(Callback.Type.HID);
        // Try HID protocol first, if it fails, fallback to RFCOMM.
        btConnector =
            new BtHidConnector(
                context, device, new HidConnectorCallback(manual), getBrailleDisplayController());
      } else {
        connectorManagerCallback.onConnectStarted(Callback.Type.RFCOMM);
        btConnector = new BtRfCommConnector(device, new BtRfCommConnectorCallback(manual));
      }
      btConnector.connect();
    }
  }

  @Override
  public void disconnect() {
    BrailleDisplayLog.d(TAG, "disconnect");
    if (btConnector != null) {
      btConnector.disconnect();
      btConnector = null;
    }
    if (deviceConnection != null) {
      deviceConnection.shutdown();
      deviceConnection = null;
      connectorManagerCallback.onDisconnected();
    }
  }

  @Override
  public void forget(ConnectableDevice device) {
    BluetoothDevice bluetoothDevice = ((ConnectableBluetoothDevice) device).bluetoothDevice();
    removeBond(bluetoothDevice);
  }

  @Override
  public boolean isConnecting() {
    return btConnector != null && deviceConnection == null;
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
        .collect(toImmutableSet());
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectingDevice() {
    return Optional.ofNullable(btConnector).map(Connector::getDevice);
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectedDevice() {
    return Optional.ofNullable(deviceConnection).map(D2dConnection::getDevice);
  }

  @Override
  public boolean isHidDevice(ConnectableDevice device) {
    BluetoothDevice bluetoothDevice = ((ConnectableBluetoothDevice) device).bluetoothDevice();
    if (bluetoothDevice.fetchUuidsWithSdp()) {
      ParcelUuid[] uu = bluetoothDevice.getUuids();
      if (uu != null) {
        for (ParcelUuid u : uu) {
          if (u.getUuid().toString().equals(HID_UUID)) {
            return true;
          }
        }
      }
    }
    BrailleDisplayLog.w(TAG, "HID UUID not found.");
    return false;
  }

  @Override
  public void sendOutgoingPacket(byte[] packet) {
    BrailleDisplayLog.d(TAG, "sendOutgoingPacket");
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

  @Override
  public void stopSearch(Reason reason) {
    BrailleDisplayLog.d(TAG, "stopScan " + reason);
    mainHandler.cancelStopCheck();
    if (btAdapter != null && mayScan()) {
      btAdapter.cancelDiscovery();
    }
  }

  private boolean isScanningOngoing() {
    return mainHandler.isOngoing();
  }

  private void startScanPossibly(Reason reason) {
    BrailleDisplayLog.d(TAG, "startScanPossibly reason: " + reason);
    if (!SettingsUtils.allowLinksOutOfSettings(context)) {
      BrailleDisplayLog.d(TAG, "Disable bluetooth scanning in setup wizard");
      return;
    }
    foundDevices.clear();
    connectorManagerCallback.onDeviceListCleared();
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
      connectorManagerCallback.onSearchFailure();
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

  private void removeBond(BluetoothDevice device) {
    try {
      Method method = device.getClass().getMethod("removeBond");
      if (method != null) {
        boolean result = (boolean) method.invoke(device);
        BrailleDisplayLog.i(TAG, "removeBond: " + result);
      }
    } catch (ReflectiveOperationException e) {
      BrailleDisplayLog.w(TAG, "Unable to call removeBond. ", e);
    }
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

  private class BtRfCommConnectorCallback implements Connector.Callback {
    private final boolean manual;

    BtRfCommConnectorCallback(boolean manual) {
      this.manual = manual;
    }

    @Override
    public void onConnectSuccess(D2dConnection connection) {
      BrailleDisplayLog.d(TAG, "RFCOMM onConnectSuccess");
      deviceConnection = connection;
      connectorManagerCallback.onConnected(deviceConnection);
    }

    @Override
    public void onDisconnected() {
      disconnect();
    }

    @Override
    public void onConnectFailure(ConnectableDevice device, Exception exception) {
      BrailleDisplayLog.d(TAG, "RFCOMM onConnectFailure: " + exception.getMessage());
      disconnect();
      connectorManagerCallback.onConnectFailure(device, manual, exception);
    }
  }

  private class HidConnectorCallback implements Connector.Callback {
    private final boolean manual;

    HidConnectorCallback(boolean manual) {
      this.manual = manual;
    }

    @Override
    public void onConnectSuccess(D2dConnection connection) {
      BrailleDisplayLog.d(TAG, "HID onConnectSuccess");
      deviceConnection = connection;
      connectorManagerCallback.onConnected(connection);
    }

    @Override
    public void onDisconnected() {
      disconnect();
    }

    @Override
    public void onConnectFailure(ConnectableDevice device, Exception exception) {
      BrailleDisplayLog.d(TAG, "HID onConnectFailure: " + exception.getMessage());
      disconnect();
      // Fallback to old method.
      btConnector = new BtRfCommConnector(device, new BtRfCommConnectorCallback(manual));
      btConnector.connect();
    }
  }

  private final BtScanReceiver.Callback btScanReceiverCallback =
      new BtScanReceiver.Callback() {
        @Override
        public void onDiscoveryStarted() {
          BrailleDisplayLog.d(TAG, "onDiscoveryStarted");
          connectorManagerCallback.onSearchStatusChanged();
        }

        @Override
        public void onDiscoveryFinished() {
          BrailleDisplayLog.d(TAG, "onDiscoveryFinished");
          connectorManagerCallback.onSearchStatusChanged();
          if (btAdapter != null && isScanningOngoing() && mayScan()) {
            BrailleDisplayLog.d(TAG, "onDiscoveryFinished restart discovery");
            btAdapter.startDiscovery();
          } else {
            BrailleDisplayLog.d(TAG, "onDiscoveryFinished do not restart discovery");
          }
        }

        @Override
        public void onDeviceSeen(BluetoothDevice device) {
          BrailleDisplayLog.d(TAG, "onDeviceSeen");
          ConnectableBluetoothDevice connectedDevice =
              ConnectableBluetoothDevice.builder().setBluetoothDevice(device).build();
          foundDevices.add(connectedDevice);
          connectorManagerCallback.onDeviceSeenOrUpdated(connectedDevice);
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
          connectorManagerCallback.onConnectivityEnabled(/* enabled= */ true);
        }

        @Override
        public void onBluetoothTurnedOff() {
          BrailleDisplayLog.d(TAG, "onBluetoothTurnedOff");
          connectorManagerCallback.onConnectivityEnabled(/* enabled= */ false);
        }
      };

  private final BtConnectStateReceiver.Callback btBondedReceiverCallback =
      new BtConnectStateReceiver.Callback() {
        @Override
        public void onBonded(BluetoothDevice device) {
          BrailleDisplayLog.d(TAG, "onBonded: " + device.getName());
          if (isConnecting() && btConnector.getDevice().address().equals(device.getAddress())) {
            // It's waiting for result!
            btConnector.connect();
          }
          connectorManagerCallback.onDeviceSeenOrUpdated(
              ConnectableBluetoothDevice.builder().setBluetoothDevice(device).build());
        }

        @Override
        public void onUnBonded(BluetoothDevice device) {
          BrailleDisplayLog.d(TAG, "onUnBonded: " + device.getName());
          connectorManagerCallback.onDeviceDeleted(
              ConnectableBluetoothDevice.builder().setBluetoothDevice(device).build());
        }

        @Override
        public void onConnected(BluetoothDevice device) {
          BrailleDisplayLog.d(TAG, "onConnected");
          if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            BrailleDisplayLog.d(TAG, device.getName() + " is not paired yet.");
            return;
          }
          // After initiating the Bluetooth pairing process with the createBond() function,
          // onConnected will come before prompting the user to confirm the pairing. Once the user
          // confirms, onBonded callback will signify successful pairing.
          if (!isConnectingOrConnected()) {
            connectorManagerCallback.onDeviceSeenOrUpdated(
                ConnectableBluetoothDevice.builder().setBluetoothDevice(device).build());
          }
        }

        @Override
        public void onDisconnected(BluetoothDevice device) {
          BrailleDisplayLog.d(TAG, "onDisconnected: " + device.getName());
          if (isConnectingOrConnected()
              && btConnector.getDevice().address().equals(device.getAddress())) {
            disconnect();
          }
        }
      };
}
