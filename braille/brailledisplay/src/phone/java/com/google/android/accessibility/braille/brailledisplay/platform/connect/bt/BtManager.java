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
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Handles the listening of bluetooth scanning, bluetooth bonding, and bluetooth radio on/off. */
public class BtManager {
  private static final String TAG = "BtManager";
  /** Callback for {@link BtManager}. */
  public interface Callback {
    void onDeviceListCleared();

    void onDeviceSeen(BluetoothDevice device);

    void onBluetoothTurnedOn();

    void onReceivedDeviceBondedBroadcast(BluetoothDevice device);

    void onScanningStarted();

    void onScanningFinished();

    void onScanningFailure();

    boolean isConnectingOrConnected();
  }

  // How long to keep the scanner scanning. For example, a duration of 60_000 means that after 1
  // minute, scanning should be halted, unless a new notification comes in.
  private static final long KEEP_SCANNING_DURATION_MS = 30_000;

  private final Context context;
  private final Callback callback;
  // Some runtimes, such as emulators, do not have bluetooth; allow that btAdapter might be null.
  @Nullable private final BluetoothAdapter btAdapter;
  private final LinkedHashSet<BluetoothDevice> foundDevices = new LinkedHashSet<>();
  private final MainHandler mainHandler;

  // Encodes the reason for the most recent state change.
  private enum Reason {
    UNKNOWN,
    START_STARTED,
    START_SCREEN_ON,
    START_SETTINGS,
    START_BLUETOOTH_TURNED_ON,
    START_USER_SELECTED_RESCAN,
    STOP_STOPPED,
    STOP_SCREEN_OFF,
    STOP_DISCOVERY_FAILED,
  }

  public BtManager(Context context, Callback callback) {
    Utils.assertMainThread();
    this.context = context;
    this.callback = callback;

    btAdapter =
        ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
    mainHandler = new MainHandler();
  }

  /** Instructs this manager to start its behaviors, such as listening for bond events. */
  public void onStart() {
    btScanReceiver.registerSelf(context);
    btOnOffReceiver.registerSelf(context);
    btBondedReceiver.registerSelf(context);
    startScanPossibly(Reason.START_STARTED);
  }

  /** Instructs this manager to stop its behaviors. */
  public void onStop() {
    btScanReceiver.unregisterSelf(context);
    btOnOffReceiver.unregisterSelf(context);
    btBondedReceiver.unregisterSelf(context);
    stopScan(Reason.STOP_STOPPED);
  }

  /** Gets the set of currently bonded devices. */
  public Set<BluetoothDevice> getBondedDevices() {
    Set<BluetoothDevice> bondedDevices = new HashSet<>();
    if (btAdapter != null && mayConnect()) {
      // According to the javadoc for {@link BluetoothAdapter#getBondedDevices}, that method can
      // return null; and we have a ticket of this happening in the field.
      bondedDevices = btAdapter.getBondedDevices();
      if (bondedDevices == null) {
        return new HashSet<>();
      }
    }
    return bondedDevices;
  }

  /** Returns whether or not the bluetooth radio is on. */
  public boolean isBluetoothOn() {
    return btAdapter != null && btAdapter.isEnabled();
  }

  /** Returns whether or not this manager is actively scanning. */
  public boolean isScanning() {
    // return btAdapter.isDiscovering();
    return isScanningOngoing();
  }

  /** Gets a copy of the list of devices found since scanning began. */
  public Collection<BluetoothDevice> getScannedDevicesCopy() {
    // Return a copy, otherwise clients can modify our data model.
    return new ArrayList<>(foundDevices);
  }

  /** Informs this manager that the device's screen just turned on. */
  public void onScreenTurnedOn() {
    startScanPossibly(Reason.START_SCREEN_ON);
  }

  /** Informs this manager that the device's screen just turned off. */
  public void onScreenTurnedOff() {
    stopScan(Reason.STOP_SCREEN_OFF);
  }

  /** Informs this manager that the user just selected to rescan. */
  public void onUserSelectedRescanFromSettings() {
    startScanPossibly(Reason.START_USER_SELECTED_RESCAN);
  }

  /** Informs this manager that a scan-related settings page was just opened. */
  public void onSettingsEntered() {
    startScanPossibly(Reason.START_SETTINGS);
  }

  private boolean isScanningOngoing() {
    return mainHandler.isOngoing();
  }

  private void startScanPossibly(Reason reason) {
    BrailleDisplayLog.d(TAG, "startScanPossibly reason: " + reason);
    clearDeviceListAndNotifyListener();
    mainHandler.scheduleStopChecks();
    boolean startSuccess = false;
    boolean shouldStartScan =
        reason == Reason.START_USER_SELECTED_RESCAN || !callback.isConnectingOrConnected();
    if (shouldStartScan && btAdapter != null && mayScan()) {
      startSuccess = btAdapter.startDiscovery();
    }
    if (!startSuccess) {
      BrailleDisplayLog.e(TAG, "startScanPossibly failed to start discovery");
      stopScan(Reason.STOP_DISCOVERY_FAILED);
      callback.onScanningFailure();
    }
  }

  private void clearDeviceListAndNotifyListener() {
    foundDevices.clear();
    callback.onDeviceListCleared();
  }

  private void stopScan(Reason reason) {
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

  private final BtScanReceiver btScanReceiver =
      new BtScanReceiver(
          new BtScanReceiver.Callback() {
            @Override
            public void onDiscoveryStarted() {
              BrailleDisplayLog.d(TAG, "onDiscoveryStarted");
              callback.onScanningStarted();
            }

            @Override
            public void onDiscoveryFinished() {
              callback.onScanningFinished();
              if (btAdapter != null && isScanningOngoing() && mayScan()) {
                BrailleDisplayLog.d(TAG, "onDiscoveryFinished restart discovery");
                btAdapter.startDiscovery();
              } else {
                BrailleDisplayLog.d(TAG, "onDiscoveryFinished do not restart discovery");
              }
            }

            @Override
            public void onDeviceSeen(BluetoothDevice device) {
              foundDevices.add(device);
              callback.onDeviceSeen(device);
            }
          });

  private final BtOnOffReceiver btOnOffReceiver =
      new BtOnOffReceiver(
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
              startScanPossibly(Reason.START_BLUETOOTH_TURNED_ON);
              callback.onBluetoothTurnedOn();
            }

            @Override
            public void onBluetoothTurnedOff() {
              BrailleDisplayLog.d(TAG, "onBluetoothTurnedOff");
              clearDeviceListAndNotifyListener();
            }
          });

  private BtBondedReceiver btBondedReceiver =
      new BtBondedReceiver(
          new BtBondedReceiver.Callback() {
            @Override
            public void onBonded(BluetoothDevice device) {
              BrailleDisplayLog.d(TAG, "onBonded " + device.getName());
              callback.onReceivedDeviceBondedBroadcast(device);
            }
          });
}
