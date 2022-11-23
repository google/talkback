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
import android.content.SharedPreferences;
import android.util.Pair;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager.RemoteDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.bt.BluetoothDevices;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.bt.BtConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.bt.BtConnector;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.bt.BtManager;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ScreenOnOffReceiver;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Manages connectivity between a phone and a BT rfcomm-capable braille display, in addition to
 * monitoring the state of the controlling service and providing access to the device-specific
 * BrailleDisplayProperties.
 */
public class Connectioneer {
  private static final String TAG = "Connectioneer";
  /** Arguments needed to instantiate the singleton. */
  public static class CreationArguments {
    public final Context applicationContext;
    public final Predicate<String> deviceNameFilter;

    public CreationArguments(Context applicationContext, Predicate<String> deviceNameFilter) {
      this.applicationContext = applicationContext;
      this.deviceNameFilter = deviceNameFilter;
    }
  }

  @SuppressLint("StaticFieldLeak")
  private static Connectioneer instance;

  /** Get the static singleton instance, creating it if necessary. */
  public static Connectioneer getInstance(CreationArguments arguments) {
    if (instance == null) {
      instance = new Connectioneer(arguments);
    }
    return instance;
  }

  private final BtManager btManager;

  private boolean controllingServiceEnabled;

  private BtConnector btConnector;
  private BtConnection btConnection;
  private BrailleDisplayProperties displayProperties;

  private enum ConnectReason {
    USER_CHOSE_CONNECT_DEVICE,
    BONDED_BROADCAST,
    AUTO_CONNECT_DEVICE_SEEN,
    AUTO_CONNECT_BONDED_REMEMBERED_BD_ENABLED,
    AUTO_CONNECT_BONDED_REMEMBERED_AUTO_CONNECT_ENABLED,
    AUTO_CONNECT_BONDED_REMEMBERED_BT_TURNED_ON,
    AUTO_CONNECT_BONDED_REMEMBERED_SCREEN_ON,
  }

  private final Context context;
  private final Predicate<String> deviceNameFilter;

  public final AspectEnablement aspectEnablement = new AspectEnablement(this);
  public final AspectConnection aspectConnection = new AspectConnection(this);
  public final AspectTraffic aspectTraffic = new AspectTraffic(this);
  public final AspectDisplayProperties aspectDisplayProperties = new AspectDisplayProperties(this);

  private final Set<String> userDisconnectedDevices = new HashSet<>();

  private Connectioneer(CreationArguments arguments) {
    this.context = arguments.applicationContext;
    this.deviceNameFilter = arguments.deviceNameFilter;

    btManager = new BtManager(context, new ScanManagerCallback());

    // We knowingly register this listener with no intention of deregistering it.  As this
    // registration happens in the constructor, and the constructor runs only once per process,
    // because we are a singleton, the lack of deregistration is okay.
    PersistentStorage.registerListener(context, preferencesListener);
  }

  /** Informs that the controlling service has changed its enabled status. */
  public void onServiceEnabledChanged(boolean serviceEnabled) {
    this.controllingServiceEnabled = serviceEnabled;
    figureEnablement(serviceEnabled, PersistentStorage.isConnectionEnabledByUser(context));
  }

  private void figureEnablement(boolean serviceEnabled, boolean userSettingEnabled) {
    boolean enable = serviceEnabled && userSettingEnabled;
    BrailleDisplayLog.d(
        TAG,
        "figureEnablement serviceEnabled: "
            + serviceEnabled
            + ", userSettingEnabled: "
            + userSettingEnabled
            + ", enable: "
            + enable);
    // Enable the Connectioneer as long as the controlling service is on and user-controlled
    // enablement is true; we purposely do not burden ourselves with other concerns such as
    // bluetooth radio being on or permissions are granted.
    if (enable) {
      autoConnectIfPossibleToBondedDevice(ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_BD_ENABLED);
      screenOnOffReceiver.registerSelf(context);
      btManager.onStart();
    } else {
      shutdownConnectorIfPresent();
      shutdownConnectionIfPresent();
      screenOnOffReceiver.unregisterSelf(context);
      btManager.onStop();
      // Clear auto connect restricted devices list.
      userDisconnectedDevices.clear();
    }
    aspectEnablement.notifyEnablementChange(controllingServiceEnabled, userSettingEnabled);
  }

  private void onUserSettingEnabledChanged(boolean userSettingEnabled) {
    figureEnablement(controllingServiceEnabled, userSettingEnabled);
  }

  private void onAutoConnectChanged(boolean autoConnect) {
    if (autoConnect) {
      autoConnectIfPossibleToBondedDevice(
          ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_AUTO_CONNECT_ENABLED);
    }
  }

  private void onSendTrafficOutgoingMessage(byte[] packet) {
    if (btConnection != null) {
      btConnection.sendOutgoingPacket(packet);
    }
  }

  private void autoConnectIfPossibleToBondedDevice(ConnectReason reason) {
    Set<BluetoothDevice> bondedDevices = btManager.getBondedDevices();
    autoConnectIfPossible(bondedDevices, reason);
  }

  @SuppressLint("DefaultLocale")
  private void autoConnectIfPossible(Collection<BluetoothDevice> devices, ConnectReason reason) {
    BrailleDisplayLog.d(
        TAG,
        "autoConnectIfPossible; reason: " + reason + "; examining " + devices.size() + " devices");
    if (PersistentStorage.isAutoConnect(context)) {
      Optional<BluetoothDevice> device =
          PersistentStorage.getRememberedDevices(context).stream()
              .flatMap(
                  r ->
                      devices.stream()
                          .filter(d -> acceptAutoConnect(d) && r.first.equals(d.getName())))
              .findFirst();
      if (device.isPresent()) {
        BrailleDisplayLog.d(
            TAG,
            "autoConnectIfPossible; found bonded remembered device "
                + BluetoothDevices.toStringWithAddress(device.get()));
        submitConnectionRequest(device.get(), reason);
      }
    }
  }

  private boolean acceptAutoConnect(BluetoothDevice bluetoothDevice) {
    return allowDevice(bluetoothDevice)
        && !userDisconnectedDevices.contains(bluetoothDevice.getAddress());
  }

  private boolean allowDevice(BluetoothDevice bluetoothDevice) {
    @Nullable String name = bluetoothDevice.getName();
    if (name == null) {
      return false;
    }
    return deviceNameFilter.test(name);
  }

  private boolean isConnecting() {
    return btConnector != null;
  }

  private boolean isConnected() {
    return btConnection != null;
  }

  private boolean isConnectingOrConnected() {
    return isConnecting() || isConnected();
  }

  private void submitConnectionRequest(BluetoothDevice device, ConnectReason reason) {
    BrailleDisplayLog.d(TAG, "submitConnectionRequest to " + device + ", reason:" + reason);
    if (isConnectingOrConnected()) {
      BrailleDisplayLog.d(
          TAG, "submitConnectionRequest ignored because already connecting or connected");
      return;
    }
    btConnector = new BtConnector(device, new BtConnectorCallback());
    btConnector.connect();
    aspectConnection.notifyScanningChanged();
  }

  private void shutdownConnectorIfPresent() {
    if (btConnector != null) {
      btConnector.shutdown();
      btConnector = null;
    }
  }

  private void shutdownConnectionIfPresent() {
    if (btConnection != null) {
      btConnection.shutdown();
      btConnection = null;
      displayProperties = null;
      aspectConnection.notifyConnectionStatusChanged(false, null);
    }
  }

  private static class Aspect<A extends Aspect<A, L>, L> {
    protected final Connectioneer connectioneer;
    protected final List<L> listeners = new ArrayList<>();

    public Aspect(Connectioneer connectioneer) {
      this.connectioneer = connectioneer;
    }

    @SuppressWarnings("unchecked")
    public A attach(L callback) {
      listeners.add(callback);
      return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A detach(L callback) {
      listeners.remove(callback);
      return (A) this;
    }

    protected void notifyListeners(Consumer<L> consumer) {
      for (L callback : listeners) {
        consumer.accept(callback);
      }
    }
  }

  /** Aspect for enablement. */
  public static class AspectEnablement extends Aspect<AspectEnablement, AspectEnablement.Callback> {
    public AspectEnablement(Connectioneer connectioneer) {
      super(connectioneer);
    }

    /** Callback for this aspect. */
    public interface Callback {
      void onEnablementChange(boolean controllingServiceEnabled, boolean connectionEnabledByUser);
    }

    private void notifyEnablementChange(
        boolean controllingServiceEnabled, boolean connectionEnabledByUser) {
      notifyListeners(
          callback ->
              callback.onEnablementChange(controllingServiceEnabled, connectionEnabledByUser));
    }

    /** Asks if the controlling service is enabled. */
    public boolean isServiceEnabled() {
      return connectioneer.controllingServiceEnabled;
    }
  }

  /** Aspect for the connectivity between this device and the remote device. */
  public static class AspectConnection extends Aspect<AspectConnection, AspectConnection.Callback> {
    private AspectConnection(Connectioneer connectioneer) {
      super(connectioneer);
    }

    /** Callback for this aspect. */
    public interface Callback {
      /** Callbacks when scanning changed. */
      void onScanningChanged();

      /** Callbacks when stored device list cleared. */
      void onDeviceListCleared();

      /** Callbacks when starting a device connection. */
      void onConnectStarted();

      /** Callbacks when connectable device seen or updated. */
      void onConnectableDeviceSeenOrUpdated(BluetoothDevice bluetoothDevice);

      /** Callbacks when a device connection status changed. */
      void onConnectionStatusChanged(boolean connected, RemoteDevice remoteDevice);

      /** Callbacks when a device connection failed. */
      void onConnectFailed(@Nullable String deviceName);
    }

    private void notifyScanningChanged() {
      notifyListeners(Callback::onScanningChanged);
    }

    private void notifyDeviceListCleared() {
      notifyListeners(Callback::onDeviceListCleared);
    }

    private void notifyConnectableDeviceSeen(BluetoothDevice bluetoothDevice) {
      notifyListeners(callback -> callback.onConnectableDeviceSeenOrUpdated(bluetoothDevice));
    }

    private void notifyConnectStarted() {
      notifyListeners(callback -> callback.onConnectStarted());
    }

    private void notifyConnectionStatusChanged(
        boolean connected, @Nullable RemoteDevice remoteDevice) {
      notifyListeners(callback -> callback.onConnectionStatusChanged(connected, remoteDevice));
    }

    private void notifyConnectFailed(@Nullable String deviceName) {
      notifyListeners(callback -> callback.onConnectFailed(deviceName));
    }

    /** Informs that the user has requested a rescan. */
    public void onUserSelectedRescan() {
      connectioneer.btManager.onUserSelectedRescanFromSettings();
      notifyListeners(Callback::onScanningChanged);
    }

    /** Informs that the user has entered the Settings UI. */
    public void onSettingsEntered() {
      connectioneer.btManager.onSettingsEntered();
    }

    /** Informs that the user has chosen to connect to a device. */
    public void onUserChoseConnectDevice(BluetoothDevice device) {
      connectioneer.userDisconnectedDevices.remove(device.getName());
      connectioneer.submitConnectionRequest(device, ConnectReason.USER_CHOSE_CONNECT_DEVICE);
    }

    /** Informs that the user has chosen to disconnect from a device. */
    public void onUserChoseDisconnectFromDevice(String deviceAddress) {
      connectioneer.userDisconnectedDevices.add(deviceAddress);
      disconnectFromDevice(deviceAddress);
    }

    /** Informs that disconnect from a device due to displayer failed. */
    public void onDisplayerFailedDisconnectFromDevice(String deviceAddress) {
      disconnectFromDevice(deviceAddress);
    }

    /** Asks if the device's bluetooth radio is on. */
    public boolean isBluetoothOn() {
      return connectioneer.btManager.isBluetoothOn();
    }

    /** Asks if the device's bluetooth radio is currently scanning. */
    public boolean isScanning() {
      return connectioneer.btManager.isScanning();
    }

    /** Gets a copy list of currently visible devices. */
    public Collection<BluetoothDevice> getScannedDevicesCopy() {
      return connectioneer.btManager.getScannedDevicesCopy().stream()
          .filter(connectioneer::allowDevice)
          .collect(Collectors.toList());
    }

    /** Asks if connecting is in progress. */
    public boolean isConnecting() {
      return connectioneer.isConnecting();
    }

    /** Asks for the optional connecting-in-progress device. */
    public Optional<BluetoothDevice> getCurrentlyConnectingDevice() {
      return connectioneer.btConnector == null
          ? Optional.empty()
          : Optional.of(connectioneer.btConnector.getDevice());
    }

    /** Asks if the given device is connecting-in-progress. */
    public boolean isConnectingTo(BluetoothDevice candidate) {
      return getCurrentlyConnectingDevice().filter(device -> device.equals(candidate)).isPresent();
    }

    /** Asks if the given device name is connecting-in-progress. */
    public boolean isConnectingTo(String candidateAddress) {
      return getCurrentlyConnectingDevice()
          .filter(device -> device.getAddress().equals(candidateAddress))
          .isPresent();
    }

    /** Asks if a connection is currently active. */
    public boolean isConnected() {
      return connectioneer.isConnected();
    }

    /** Asks for the optional currently connected device. */
    public Optional<BluetoothDevice> getCurrentlyConnectedDevice() {
      return connectioneer.btConnection == null
          ? Optional.empty()
          : Optional.of(connectioneer.btConnection.getDevice());
    }

    /** Asks if the given device is currently connected. */
    public boolean isConnectedTo(BluetoothDevice candidate) {
      return getCurrentlyConnectedDevice().filter((device) -> device.equals(candidate)).isPresent();
    }

    /** Asks if the given device name is currently connected. */
    public boolean isConnectedTo(String candidateName) {
      return getCurrentlyConnectedDevice()
          .filter((device) -> device.getName().equals(candidateName))
          .isPresent();
    }

    /** Asks if either connecting is in progress or if connection is active. */
    public boolean isConnectingOrConnected() {
      return connectioneer.isConnectingOrConnected();
    }

    private void disconnectFromDevice(String deviceAddress) {
      if (getCurrentlyConnectingDevice()
          .filter(device -> device.getAddress().equals(deviceAddress))
          .isPresent()) {
        connectioneer.shutdownConnectorIfPresent();
      }
      if (getCurrentlyConnectedDevice()
          .filter(device -> device.getAddress().equals(deviceAddress))
          .isPresent()) {
        connectioneer.shutdownConnectionIfPresent();
      }
    }
  }

  /** Aspect for the packet traffic between this device and the remote device. */
  public static class AspectTraffic extends Aspect<AspectTraffic, AspectTraffic.Callback> {
    private AspectTraffic(Connectioneer connectioneer) {
      super(connectioneer);
    }

    /** Callback for this aspect. */
    public interface Callback {
      void onPacketArrived(byte[] buffer);
    }

    private void notifyPacketArrived(byte[] buffer) {
      notifyListeners(callback -> callback.onPacketArrived(buffer));
    }

    /** Informs that the given outgoing message should be sent to the remote device. */
    public void onSendTrafficOutgoingMessage(byte[] packet) {
      connectioneer.onSendTrafficOutgoingMessage(packet);
    }
  }

  /** Aspect for the display properties of the remote device. */
  public static class AspectDisplayProperties
      extends Aspect<AspectDisplayProperties, AspectDisplayProperties.Callback> {

    private AspectDisplayProperties(Connectioneer connectioneer) {
      super(connectioneer);
    }

    /** Callback for this aspect. */
    public interface Callback {
      void onDisplayPropertiesArrived(BrailleDisplayProperties brailleDisplayProperties);
    }

    private void notifyDisplayPropertiesArrived() {
      notifyListeners(
          callback -> callback.onDisplayPropertiesArrived(connectioneer.displayProperties));
    }

    /** Informs that the display properties have arrived from the remote device. */
    public void onDisplayPropertiesArrived(BrailleDisplayProperties displayProperties) {
      connectioneer.displayProperties = displayProperties;
      notifyDisplayPropertiesArrived();
    }

    /** Asks for the display properties of the remote device. */
    public BrailleDisplayProperties getDisplayProperties() {
      return connectioneer.displayProperties;
    }
  }

  private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener =
      new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (PersistentStorage.PREF_CONNECTION_ENABLED.equals(key)) {
            onUserSettingEnabledChanged(PersistentStorage.isConnectionEnabledByUser(context));
          } else if (PersistentStorage.PREF_AUTO_CONNECT.equals(key)) {
            onAutoConnectChanged(PersistentStorage.isAutoConnect(context));
          }
        }
      };

  private final ScreenOnOffReceiver screenOnOffReceiver =
      new ScreenOnOffReceiver(
          new ScreenOnOffReceiver.Callback() {
            @Override
            public void onScreenOn() {
              BrailleDisplayLog.d(TAG, "onScreenOn");
              Connectioneer.this.btManager.onScreenTurnedOn();
              autoConnectIfPossibleToBondedDevice(
                  ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_SCREEN_ON);
            }

            @Override
            public void onScreenOff() {
              BrailleDisplayLog.d(TAG, "onScreenOff");
              // TODO consider disconnecting
              Connectioneer.this.btManager.onScreenTurnedOff();
            }
          });

  private class ScanManagerCallback implements BtManager.Callback {
    @Override
    public void onDeviceListCleared() {
      BrailleDisplayLog.d(TAG, "onDeviceListCleared");
      aspectConnection.notifyDeviceListCleared();
    }

    @Override
    public void onDeviceSeen(BluetoothDevice device) {
      if (allowDevice(device)) {
        autoConnectIfPossible(
            Collections.singleton(device), ConnectReason.AUTO_CONNECT_DEVICE_SEEN);
        aspectConnection.notifyConnectableDeviceSeen(device);
      }
    }

    @Override
    public void onBluetoothTurnedOn() {
      BrailleDisplayLog.d(TAG, "onBluetoothTurnedOn");
      autoConnectIfPossibleToBondedDevice(
          ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_BT_TURNED_ON);
    }

    @Override
    public void onReceivedDeviceBondedBroadcast(BluetoothDevice device) {
      BrailleDisplayLog.d(TAG, "onReceivedDeviceBondedBroadcast");
      if (allowDevice(device)) {
        submitConnectionRequest(device, ConnectReason.BONDED_BROADCAST);
      }
    }

    @Override
    public void onScanningStarted() {
      BrailleDisplayLog.d(TAG, "onScanningStarted");
      aspectConnection.notifyScanningChanged();
    }

    @Override
    public void onScanningFinished() {
      BrailleDisplayLog.d(TAG, "onScanningFinished");
      aspectConnection.notifyScanningChanged();
    }

    @Override
    public void onScanningFailure() {
      BrailleDisplayLog.d(TAG, "onScanningFailure");
      aspectConnection.notifyScanningChanged();
    }

    @Override
    public boolean isConnectingOrConnected() {
      return Connectioneer.this.isConnectingOrConnected();
    }
  }

  private class BtConnectorCallback implements BtConnector.Callback {

    @Override
    public void onConnectStarted() {
      aspectConnection.notifyConnectStarted();
    }

    @Override
    public void onConnectSuccess(BtConnection btConnection) {
      BrailleDisplayLog.d(TAG, "onConnectSuccess");
      shutdownConnectorIfPresent();
      Connectioneer.this.btConnection = btConnection;
      BluetoothDevice bluetoothDevice = btConnection.getDevice();
      if (bluetoothDevice != null && bluetoothDevice.getName() != null) {
        PersistentStorage.addRememberedDevice(
            context, new Pair<>(bluetoothDevice.getName(), bluetoothDevice.getAddress()));
      }
      // In case you are wondering what happens if the call to open() leads to failure... such
      // a failure will NOT be handled on the current tick (see the docs for
      // BtConnection.open()). Therefore, any code that follows the call to open() will
      // execute BEFORE any failure callback gets invoked, which is reasonable and consistent.
      Connectioneer.this.btConnection.open(mD2dConnectionCallback);

      RemoteDevice remoteDevice =
          new RemoteDevice(
              btConnection.getDevice().getName(), btConnection.getDevice().getAddress());
      aspectConnection.notifyConnectionStatusChanged(true, remoteDevice);
    }

    @Override
    public void onConnectFailure(BluetoothDevice device, Exception exception) {
      BrailleDisplayLog.d(TAG, "onConnectFailure: " + exception.getMessage());
      shutdownConnectorIfPresent();
      aspectConnection.notifyConnectFailed(device == null ? null : device.getName());
      aspectConnection.notifyScanningChanged();
    }
  }

  private final D2dConnection.Callback mD2dConnectionCallback =
      new D2dConnection.Callback() {

        @Override
        public void onPacketArrived(byte[] packet) {
          // As stated in the docs for {@link D2dConnection#onTrafficConsume()}, we are on the
          // main thread right now, and if we have arrived here then the connection is viable (has
          // neither failed nor been shutdown). Any downstream method invocations stemming from
          // here can safely use the connection.
          aspectTraffic.notifyPacketArrived(packet);
        }

        @Override
        public final void onFatalError(Exception exception) {
          BrailleDisplayLog.e(TAG, "onFatalError: " + exception.getMessage());
          shutdownConnectionIfPresent();
          Toast.makeText(
                  context,
                  context.getString(R.string.bd_bt_connection_disconnected_message),
                  Toast.LENGTH_LONG)
              .show();
        }
      };
}
