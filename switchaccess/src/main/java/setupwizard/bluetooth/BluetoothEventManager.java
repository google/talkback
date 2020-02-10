/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.switchaccess.setupwizard.bluetooth;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.ComparableBluetoothDevice.BluetoothConnectionState;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.ComparableBluetoothDevice.BluetoothDeviceActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Class that manages and shares bluetooth events with a list of {@link BluetoothCallback} classes.
 */
public class BluetoothEventManager {

  /* The context associated with this BluetoothEventManager. */
  private final Context context;

  /*
   * Request code passed to {@link Activity#startActivityForResult} when requesting to enable
   * bluetooth in order to receive a callback when bluetooth is successfully enabled.
   */
  public static final int REQUEST_ENABLE_BLUETOOTH = 100;

  /*
   * Request code passed to {@link Activity#startActivityForResult} when requesting {@link
   * permission#ACCESS_COARSE_LOCATION}.
   */
  private static final int REQUEST_ACCESS_COARSE_LOCATION = 200;

  /* List of {@link BluetoothCallback} classes that have been registered to the
   * BluetoothEventManager to receive bluetooth-related events. */
  private final Collection<BluetoothCallback> bluetoothCallbacks = new ArrayList<>();

  /* The local bluetooth adapter, used for initiating device discovery and getting paired
   * devices. */
  private BluetoothAdapter bluetoothAdapter;

  /* Boolean used to keep track of whether or not the discovery and pairing broadcast receiver has
   * been registered to prevent crashes from improper registration handling. */
  private boolean isDiscoveryAndPairingReceiverRegistered = false;

  /* The listener that will be notified of the actions performed by the user when connecting a
   * bluetooth device.
   */
  private final BluetoothDeviceActionListener bluetoothDeviceActionListener;

  /* The BroadcastReceiver will cause a crash if receiver registration isn't handled properly.
   * Attempting to register an already registered receiver or to unregister an unregistered
   * broadcast receiver will cause a crash, and there's not a good way to check if the receiver has
   * been previously registered. */
  private final BroadcastReceiver discoveryAndPairingReceiver =
      new BroadcastReceiver() {
        @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
          if (bluetoothDevice == null) {
            return;
          }
          ComparableBluetoothDevice comparableBluetoothDevice =
              new ComparableBluetoothDevice(
                  context, bluetoothAdapter, bluetoothDevice, bluetoothDeviceActionListener);
          if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)
              || BluetoothDevice.ACTION_FOUND.equals(action)) {
            String bluetoothDeviceName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
            comparableBluetoothDevice.setName(bluetoothDeviceName);
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
              BluetoothClass bluetoothDeviceClass =
                  intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
              comparableBluetoothDevice.setBluetoothClass(bluetoothDeviceClass);
            }

            /* Don't add a device if it's already been bonded (paired) to the device. */
            if (bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
              short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
              comparableBluetoothDevice.setRssi(rssi);
              dispatchDeviceDiscoveredEvent(comparableBluetoothDevice);
              // TODO: Remove available devices from adapter if they become
              // unavailable. This will most likely be unable to be addressed without API changes.
            } else {
              dispatchDevicePairedEvent(comparableBluetoothDevice);
            }
          } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
              comparableBluetoothDevice.setConnectionState(BluetoothConnectionState.CONNECTED);
              dispatchDeviceConnectionStateChangedEvent(comparableBluetoothDevice);
            }
          } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
              comparableBluetoothDevice.setConnectionState(BluetoothConnectionState.UNKNOWN);
              dispatchDeviceConnectionStateChangedEvent(comparableBluetoothDevice);
            }
          } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
            if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
              comparableBluetoothDevice.setConnectionState(BluetoothConnectionState.CONNECTED);
              dispatchDevicePairedEvent(comparableBluetoothDevice);
            } else if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE) {
              /* The call to #createBond has completed, but the Bluetooth device isn't bonded, so
               * set the connection state to unavailable. */
              comparableBluetoothDevice.setConnectionState(BluetoothConnectionState.UNAVAILABLE);
              dispatchDeviceConnectionStateChangedEvent(comparableBluetoothDevice);
            }

            /* If we canceled discovery before beginning the pairing process, resume discovery after
             * {@link BluetoothDevice#createBond} finishes. */
            if (bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDING
                && !bluetoothAdapter.isDiscovering()) {
              bluetoothAdapter.startDiscovery();
            }
          }
        }
      };

  /* Boolean used to keep track of whether or not the state broadcast receiver has been registered
   * to prevent crashes from improper registration handling. */
  private boolean isStateReceiverRegistered = false;

  /* Bluetooth state change events and bluetooth device discovery and pairing events have different
   * lifecycles, so it is necessary to separate the two to avoid crashes. */
  private final BroadcastReceiver stateReceiver =
      new BroadcastReceiver() {
        @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
              dispatchBluetoothStateChangeEvent(BluetoothAdapter.STATE_OFF);
            } else if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
              dispatchBluetoothStateChangeEvent(BluetoothAdapter.STATE_ON);
              updatePairedDevicesAndInitiateDiscovery();
            }
          }
        }
      };

  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public BluetoothEventManager(
      Context context, BluetoothDeviceActionListener bluetoothDeviceActionListener) {
    this.context = context;

    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    /* Cases where a device doesn't support Bluetooth should be handled before initializing
     * BluetoothEventManager. */

    this.bluetoothDeviceActionListener = bluetoothDeviceActionListener;
  }

  /**
   * Returns the local bluetooth adapter associated with this bluetooth event manager.
   *
   * @return the associated bluetooth adapter
   */
  public BluetoothAdapter getBluetoothAdapter() {
    return bluetoothAdapter;
  }

  /**
   * Sets the bluetooth adapter associated with this bluetooth event manager.
   *
   * @param bluetoothAdapter the bluetooth adapter for the bluetooth event manager to use
   */
  @VisibleForTesting
  void setBluetoothAdapter(BluetoothAdapter bluetoothAdapter) {
    this.bluetoothAdapter = bluetoothAdapter;
  }

  /**
   * Initiates discovery with the associated bluetooth adapter and registers a {@link
   * BroadcastReceiver} to listen to bluetooth device discovery and pairing events.
   */
  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public void initiateDiscovery() {
    if (!bluetoothAdapter.isDiscovering()) {
      if (!isDiscoveryAndPairingReceiverRegistered) {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_NAME_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(discoveryAndPairingReceiver, intentFilter);
        isDiscoveryAndPairingReceiverRegistered = true;
      }

      /* Check for the ACCESS_COARSE_LOCATION permission. If the user denies this permission, no
       * ACTION_FOUND broadcasts will be received by the discoveryAndPairingReceiver.
       * ACTION_NAME_CHANGED broadcasts will still be received, though there may be a delay before
       * users first see discovered devices.
       *
       * Applications can handle cases when this permission is denied by overriding {@link
       * Activity#onActivityResult} and listening for the {@link
       * BluetoothEventManager#REQUEST_COARSE_LOCATION} request code. */
      if (ContextCompat.checkSelfPermission(context, permission.ACCESS_COARSE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            (Activity) context,
            new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
            REQUEST_ACCESS_COARSE_LOCATION);
      }
      bluetoothAdapter.startDiscovery();
    }
  }

  /**
   * If a {@link BroadcastReceiver} has been previously registered to receive bluetooth device
   * discovery events via {@link BluetoothEventManager#initiateDiscovery()}, the associated
   * broadcast receiver will be unregistered and bluetooth device discovery will stop.
   */
  public void stopDiscovery() {
    if (isDiscoveryAndPairingReceiverRegistered) {
      context.unregisterReceiver(discoveryAndPairingReceiver);
      isDiscoveryAndPairingReceiverRegistered = false;
    }
  }

  /**
   * If a {@link BroadcastReceiver} has been previously registered to receive bluetooth state change
   * events via {@link BluetoothEventManager#requestEnableBluetoothAndInitiateDiscoveryOnSuccess()},
   * the associated broadcast receiver will be unregistered and bluetooth state change event
   * notifications will stop.
   */
  public void stopListeningForBluetoothStateChange() {
    if (isStateReceiverRegistered) {
      context.unregisterReceiver(stateReceiver);
      isStateReceiverRegistered = false;
    }
  }

  /** Stop listening for all bluetooth events. This will unregister all broadcast receivers. */
  public void stopListeningForAllEvents() {
    stopDiscovery();
    stopListeningForBluetoothStateChange();
  }

  /**
   * Registers an {@link BluetoothCallback} to this event manager. A registered callback will be
   * able to capture bluetooth events defined by {@link BluetoothCallback}.
   *
   * @param callback the {@link BluetoothCallback} to register to this bluetooth event manager
   */
  public void registerCallback(BluetoothCallback callback) {
    bluetoothCallbacks.add(callback);
  }

  /**
   * Starts an intent to enable bluetooth. If successful, discovery will automatically be initiated.
   *
   * <p>Applications are responsible for handling cases when enabling Bluetooth isn't successful by
   * overriding {@link Activity#onActivityResult} and listening for the {@link
   * BluetoothEventManager#REQUEST_ENABLE_BLUETOOTH} request code.
   */
  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  public void requestEnableBluetoothAndInitiateDiscoveryOnSuccess() {
    if (!isStateReceiverRegistered) {
      IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      this.context.registerReceiver(stateReceiver, intentFilter);
      isStateReceiverRegistered = true;
    }

    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      ((Activity) context).startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH);
    } else {
      // Restart discovery to make sure we have the most up-to-date information.
      if (bluetoothAdapter.isDiscovering()) {
        bluetoothAdapter.cancelDiscovery();
      }

      updatePairedDevicesAndInitiateDiscovery();
    }
  }

  /**
   * Dispatches device paired events for the already paired bluetooth devices and then initiates the
   * discovery process for nearby bluetooth devices.
   */
  @RequiresPermission(allOf = {permission.BLUETOOTH, permission.BLUETOOTH_ADMIN})
  private void updatePairedDevicesAndInitiateDiscovery() {
    Set<BluetoothDevice> currentlyPairedDevices = bluetoothAdapter.getBondedDevices();

    // TODO: Only show paired devices that are currently available once we can get the
    // connection state of a previously paired device.
    for (BluetoothDevice bluetoothDevice : currentlyPairedDevices) {
      /* The {@link RecyclerView.Adapter} used to display the bluetooth devices is backed by a
       * HashSet, so no duplicate checks are needed in this class. */
      dispatchDevicePairedEvent(
          new ComparableBluetoothDevice(
              context, bluetoothAdapter, bluetoothDevice, bluetoothDeviceActionListener));
    }

    initiateDiscovery();
  }

  private void dispatchDeviceDiscoveredEvent(ComparableBluetoothDevice bluetoothDevice) {
    for (BluetoothCallback callback : bluetoothCallbacks) {
      callback.onDeviceDiscovered(bluetoothDevice);
    }
  }

  private void dispatchDevicePairedEvent(ComparableBluetoothDevice bluetoothDevice) {
    for (BluetoothCallback callback : bluetoothCallbacks) {
      callback.onDevicePaired(bluetoothDevice);
    }
  }

  private void dispatchDeviceConnectionStateChangedEvent(
      ComparableBluetoothDevice bluetoothDevice) {
    for (BluetoothCallback callback : bluetoothCallbacks) {
      callback.onDeviceConnectionStateChanged(bluetoothDevice);
    }
  }

  private void dispatchBluetoothStateChangeEvent(int state) {
    for (BluetoothCallback callback : bluetoothCallbacks) {
      callback.onBluetoothStateChanged(state);
    }
  }

  /** Interface for conveying information related to bluetooth events. */
  public interface BluetoothCallback {

    /**
     * Called when {@link android.bluetooth.BluetoothAdapter#ACTION_STATE_CHANGED} events are
     * received by the {@link BluetoothEventManager}.
     *
     * @param state the current Bluetooth adapter state
     */
    void onBluetoothStateChanged(int state);

    /**
     * Called when {@link android.bluetooth.BluetoothDevice#ACTION_NAME_CHANGED} or {@link
     * android.bluetooth.BluetoothDevice#ACTION_FOUND} events are received by the {@link
     * BluetoothEventManager}.
     *
     * @param bluetoothDevice the Bluetooth device that has recently been discovered
     */
    void onDeviceDiscovered(ComparableBluetoothDevice bluetoothDevice);

    /**
     * Called when {@link android.bluetooth.BluetoothDevice#ACTION_BOND_STATE_CHANGED} events are
     * received by the {@link BluetoothEventManager}.
     *
     * @param bluetoothDevice the bluetooth device that has recently been paired
     */
    void onDevicePaired(ComparableBluetoothDevice bluetoothDevice);

    /**
     * Called when {@link BluetoothDevice#ACTION_ACL_CONNECTED} or {@link
     * BluetoothDevice#ACTION_ACL_DISCONNECTED} events are received by the {@link
     * BluetoothEventManager}.
     *
     * @param bluetoothDevice the Bluetooth device that has recently had a connection state change
     */
    void onDeviceConnectionStateChanged(ComparableBluetoothDevice bluetoothDevice);
  }
}
