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
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.util.Pair;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager.AccessibilityServiceContextProvider;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.AspectConnection.Callback.ConnectStatus;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManager;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManager.ConnectType;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManager.Reason;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManagerProxy;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.usb.UsbAttachedReceiver;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ScreenOnOffReceiver;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Manages connectivity between a phone and a BT rfcomm-capable or usb-capable braille display, in
 * addition to monitoring the state of the controlling service and providing access to the
 * device-specific BrailleDisplayProperties.
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

  private enum ConnectReason {
    USER_CHOSE_CONNECT_DEVICE,
    BONDED_BROADCAST,
    AUTO_CONNECT_DEVICE_SEEN,
    AUTO_CONNECT_BONDED_REMEMBERED_BD_ENABLED,
    AUTO_CONNECT_BONDED_REMEMBERED_AUTO_CONNECT_ENABLED,
    AUTO_CONNECT_BONDED_REMEMBERED_BT_TURNED_ON,
    AUTO_CONNECT_BONDED_REMEMBERED_SCREEN_ON,
    AUTO_CONNECT_USB_UNPLUGGED,
    AUTO_CONNECT_USB_PLUGGED,
  }

  public final AspectEnablement aspectEnablement = new AspectEnablement(this);
  public final AspectConnection aspectConnection = new AspectConnection(this);
  public final AspectTraffic aspectTraffic = new AspectTraffic(this);
  public final AspectDisplayProperties aspectDisplayProperties = new AspectDisplayProperties(this);

  private final Set<String> userDisconnectedDevices = new HashSet<>();
  private final Set<String> userDeniedDevices = new HashSet<>();
  private final Context context;
  private final Predicate<String> deviceNameFilter;
  private final ScreenOnOffReceiver screenOnOffReceiver;
  private final UsbAttachedReceiver usbAttachedReceiver;
  private final ConnectManagerProxy connectManagerProxy;

  private BrailleDisplayProperties displayProperties;
  private boolean controllingServiceEnabled;
  private ConnectManagerCallback connectManagerCallback;

  private Connectioneer(CreationArguments arguments) {
    this.context = arguments.applicationContext;
    this.deviceNameFilter = arguments.deviceNameFilter;
    screenOnOffReceiver = new ScreenOnOffReceiver(context, screenOnOffReceiverCallback);
    usbAttachedReceiver = new UsbAttachedReceiver(context, usbAttachedReceiverCallback);
    connectManagerCallback = new ConnectManagerCallback();
    connectManagerProxy = new ConnectManagerProxy(context, connectManagerCallback);
    // We knowingly register this listener with no intention of deregistering it.  As this
    // registration happens in the constructor, and the constructor runs only once per process,
    // because we are a singleton, the lack of deregistration is okay.
    PersistentStorage.registerListener(context, preferencesListener);
  }

  /** Set accessibility service context provider. */
  public void setAccessibilityServiceContextProvider(
      AccessibilityServiceContextProvider accessibilityServiceContextProvider) {
    connectManagerProxy.setAccessibilityServiceContextProvider(accessibilityServiceContextProvider);
  }

  /** Informs that the controlling service has changed its enabled status. */
  public void onServiceEnabledChanged(boolean serviceEnabled) {
    this.controllingServiceEnabled = serviceEnabled;
    figureEnablement(serviceEnabled, PersistentStorage.isConnectionEnabledByUser(context));
  }

  private boolean shouldUseUsbConnection() {
    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    return usbManager.getDeviceList().values().stream()
            .filter(device -> allowDevice(device.getProductName()))
            .count()
        > 0;
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
    // Connect type may change before braille display toggle is enabled so update the manager before
    // it starts.
    connectManagerProxy.switchTo(
        shouldUseUsbConnection() ? ConnectType.USB : ConnectType.BLUETOOTH);
    // Enable the Connectioneer as long as the controlling service is on and user-controlled
    // enablement is true; we purposely do not burden ourselves with other concerns such as
    // bluetooth radio being on or permissions are granted.
    if (enable) {
      autoConnectIfPossibleToBondedDevice(ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_BD_ENABLED);
      screenOnOffReceiver.registerSelf();
      usbAttachedReceiver.registerSelf();
      connectManagerProxy.onStart();
    } else {
      screenOnOffReceiver.unregisterSelf();
      usbAttachedReceiver.unregisterSelf();
      connectManagerProxy.onStop();
      // Switch back to bt by default.
      connectManagerProxy.switchTo(ConnectType.BLUETOOTH);
      // Clear auto connect restricted devices list.
      userDisconnectedDevices.clear();
      userDeniedDevices.clear();
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
    connectManagerProxy.sendOutgoingPacket(packet);
  }

  private void autoConnectIfPossibleToBondedDevice(ConnectReason reason) {
    Set<ConnectableDevice> bondedDevices = connectManagerProxy.getBondedDevices();
    autoConnectIfPossible(bondedDevices, reason);
  }

  @SuppressLint("DefaultLocale")
  private void autoConnectIfPossible(Collection<ConnectableDevice> devices, ConnectReason reason) {
    BrailleDisplayLog.d(
        TAG,
        "autoConnectIfPossible; reason: " + reason + "; examining " + devices.size() + " devices");
    if (isConnectingOrConnected()) {
      BrailleDisplayLog.d(TAG, "isConnectingOrConnected(): " + isConnectingOrConnected());
      return;
    }
    if (PersistentStorage.isAutoConnect(context)) {
      Optional<ConnectableDevice> device =
          devices.stream()
              .filter(
                  connectableDevice -> {
                    if (!acceptAutoConnect(connectableDevice)) {
                      return false;
                    }
                    if (connectManagerProxy.getType() == ConnectType.BLUETOOTH) {
                      return PersistentStorage.getRememberedDevices(context).stream()
                          .anyMatch(
                              stringStringPair ->
                                  stringStringPair.first.equals(connectableDevice.name()));
                    }
                    // We don't remember usb devices. Connect the first one directly.
                    return true;
                  })
              .findFirst();
      if (device.isPresent()) {
        BrailleDisplayLog.d(
            TAG, "autoConnectIfPossible; found bonded remembered device " + device.get());
        submitConnectionRequest(device.get(), reason);
      }
    }
  }

  private boolean acceptAutoConnect(ConnectableDevice device) {
    return allowDevice(device.name())
        && !userDisconnectedDevices.contains(device.address())
        && !userDeniedDevices.contains(device.address());
  }

  private boolean allowDevice(String name) {
    if (name == null) {
      return false;
    }
    return deviceNameFilter.test(name);
  }

  private boolean isConnecting() {
    return connectManagerProxy.isConnecting();
  }

  private boolean isConnected() {
    return connectManagerProxy.isConnected();
  }

  private boolean isConnectingOrConnected() {
    return isConnecting() || isConnected();
  }

  private void submitConnectionRequest(ConnectableDevice device, ConnectReason reason) {
    BrailleDisplayLog.d(TAG, "submitConnectionRequest to " + device + ", reason:" + reason);
    if (aspectConnection.isConnectedTo(device.name())
        || aspectConnection.isConnectingTo(device.name())) {
      BrailleDisplayLog.d(
          TAG,
          "submitConnectionRequest ignored because already connecting or connected to "
              + device.name());
      return;
    }
    connectManagerProxy.disconnect();
    aspectConnection.notifyConnectionStatusChanged(ConnectStatus.CONNECTING, device);
    connectManagerProxy.connect(device);
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

      /** Status of connection. */
      enum ConnectStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING
      }
      /** Callbacks when scanning changed. */
      void onScanningChanged();

      /** Callbacks when stored device list cleared. */
      void onDeviceListCleared();

      /** Callbacks when starting a device connection. */
      void onConnectStarted();

      /** Callbacks when connectable device seen or updated. */
      void onConnectableDeviceSeenOrUpdated(ConnectableDevice device);

      /** Callbacks when a device connection status changed. */
      void onConnectionStatusChanged(ConnectStatus status, ConnectableDevice device);

      /** Callbacks when a device connection failed. */
      void onConnectFailed(@Nullable String deviceName);
    }

    private void notifyScanningChanged() {
      notifyListeners(AspectConnection.Callback::onScanningChanged);
    }

    private void notifyDeviceListCleared() {
      notifyListeners(AspectConnection.Callback::onDeviceListCleared);
    }

    private void notifyConnectableDeviceSeen(ConnectableDevice device) {
      notifyListeners(callback -> callback.onConnectableDeviceSeenOrUpdated(device));
    }

    private void notifyConnectStarted() {
      notifyListeners(callback -> callback.onConnectStarted());
    }

    private void notifyConnectionStatusChanged(
        ConnectStatus status, @Nullable ConnectableDevice device) {
      notifyListeners(callback -> callback.onConnectionStatusChanged(status, device));
    }

    private void notifyConnectFailed(@Nullable String deviceName) {
      notifyListeners(callback -> callback.onConnectFailed(deviceName));
    }

    /** Informs that the user has requested a rescan. */
    public void onUserSelectedRescan() {
      connectioneer.connectManagerProxy.startSearch(Reason.START_USER_SELECTED_RESCAN);
      notifyListeners(AspectConnection.Callback::onScanningChanged);
    }

    /** Informs that the user has entered the Settings UI. */
    public void onSettingsEntered() {
      if (connectioneer.controllingServiceEnabled
          && PersistentStorage.isConnectionEnabledByUser(connectioneer.context)) {
        connectioneer.connectManagerProxy.startSearch(Reason.START_SETTINGS);
      }
    }

    /** Informs that the user has chosen to connect to a device. */
    public void onUserChoseConnectDevice(ConnectableDevice device) {
      connectioneer.userDisconnectedDevices.remove(device.name());
      connectioneer.userDeniedDevices.remove(device.name());
      connectioneer.submitConnectionRequest(device, ConnectReason.USER_CHOSE_CONNECT_DEVICE);
    }

    /** Informs that the user has chosen to disconnect from a device. */
    public void onUserChoseDisconnectFromDevice(String deviceAddress) {
      connectioneer.userDisconnectedDevices.add(deviceAddress);
      disconnectFromDevice(deviceAddress);
    }

    /** Informs that displayer failed to start. */
    public void onDisplayerStartFailed(String deviceAddress) {
      disconnectFromDevice(deviceAddress);
    }

    /** Asks if the device's bluetooth radio is on. */
    public boolean isBluetoothOn() {
      BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
      return adapter != null && adapter.isEnabled();
    }

    /** Returns whether is using usb connection. */
    public boolean useUsbConnection() {
      return connectioneer.connectManagerProxy.getType() == ConnectType.USB;
    }

    /** Asks if the device's bluetooth radio is currently scanning. */
    public boolean isScanning() {
      return connectioneer.connectManagerProxy.isScanning();
    }

    /** Gets a copy list of currently visible devices. */
    public Collection<ConnectableDevice> getScannedDevicesCopy() {
      return connectioneer.connectManagerProxy.getConnectableDevices().stream()
          .filter(device -> connectioneer.allowDevice(device.name()))
          .collect(Collectors.toList());
    }

    /** Asks if connecting is in progress. */
    public boolean isConnecting() {
      return connectioneer.isConnecting();
    }

    /** Asks for the optional connecting-in-progress device. */
    public Optional<ConnectableDevice> getCurrentlyConnectingDevice() {
      return connectioneer.connectManagerProxy.getCurrentlyConnectingDevice();
    }

    /** Asks if the given device name is connecting-in-progress. */
    public boolean isConnectingTo(String candidateName) {
      return getCurrentlyConnectingDevice()
          .filter(
              device ->
                  connectioneer.allowDevice(device.name()) && device.name().equals(candidateName))
          .isPresent();
    }

    /** Asks if a connection is currently active. */
    public boolean isConnected() {
      return connectioneer.isConnected();
    }

    /** Asks for the optional currently connected device. */
    public Optional<ConnectableDevice> getCurrentlyConnectedDevice() {
      return connectioneer.connectManagerProxy.getCurrentlyConnectedDevice();
    }

    /** Asks if the given device name is currently connected. */
    public boolean isConnectedTo(String candidateName) {
      return getCurrentlyConnectedDevice()
          .filter(
              device ->
                  connectioneer.allowDevice(device.name()) && device.name().equals(candidateName))
          .isPresent();
    }

    /** Asks if either connecting is in progress or if connection is active. */
    public boolean isConnectingOrConnected() {
      return connectioneer.isConnectingOrConnected();
    }

    private void disconnectFromDevice(String deviceAddress) {
      if (getCurrentlyConnectingDevice()
              .filter(device -> device.address().equals(deviceAddress))
              .isPresent()
          || getCurrentlyConnectedDevice()
              .filter(device -> device.address().equals(deviceAddress))
              .isPresent()) {
        connectioneer.connectManagerProxy.disconnect();
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

      void onRead();
    }

    private void notifyPacketArrived(byte[] buffer) {
      notifyListeners(callback -> callback.onPacketArrived(buffer));
    }

    private void notifyRead() {
      notifyListeners(Callback::onRead);
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
            // Don't callback when the braille display is disabled.
            if (PersistentStorage.isConnectionEnabledByUser(context)) {
              onAutoConnectChanged(PersistentStorage.isAutoConnect(context));
            }
          }
        }
      };

  private final ScreenOnOffReceiver.Callback screenOnOffReceiverCallback =
      new ScreenOnOffReceiver.Callback() {
        @Override
        public void onScreenOn() {
          BrailleDisplayLog.d(TAG, "onScreenOn");
          connectManagerProxy.startSearch(Reason.START_SCREEN_ON);
          autoConnectIfPossibleToBondedDevice(
              ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_SCREEN_ON);
        }

        @Override
        public void onScreenOff() {
          BrailleDisplayLog.d(TAG, "onScreenOff");
          // TODO consider disconnecting
          connectManagerProxy.stopSearch(Reason.STOP_SCREEN_OFF);
        }
      };

  private final UsbAttachedReceiver.Callback usbAttachedReceiverCallback =
      new UsbAttachedReceiver.Callback() {
        @Override
        public void onUsbAttached(ConnectableDevice device) {
          BrailleDisplayLog.d(TAG, "onUsbAttached");
          if (shouldUseUsbConnection()) {
            if (isUsingUsbConnection()) {
              // Refresh device list.
              connectManagerProxy.startSearch(Reason.START_USB_ATTACH_DETACH);
              if (!isConnectingOrConnected()) {
                submitConnectionRequest(device, ConnectReason.AUTO_CONNECT_USB_PLUGGED);
              }
            } else {
              connectManagerProxy.switchTo(ConnectType.USB);
              connectManagerProxy.onStart();
              submitConnectionRequest(device, ConnectReason.AUTO_CONNECT_USB_PLUGGED);
            }
          }
        }

        @Override
        public void onUsbDetached(ConnectableDevice device) {
          BrailleDisplayLog.d(TAG, "onUsbDetached");
          if (shouldUseUsbConnection()) {
            if (connectManagerProxy.getCurrentlyConnectedDevice().isPresent()
                && connectManagerProxy.getCurrentlyConnectedDevice().get().equals(device)) {
              connectManagerProxy.disconnect();
            }
            // Refresh device list.
            connectManagerProxy.startSearch(Reason.START_USB_ATTACH_DETACH);
            if (!isConnectingOrConnected()) {
              autoConnectIfPossibleToBondedDevice(ConnectReason.AUTO_CONNECT_USB_UNPLUGGED);
            }
          } else if (isUsingUsbConnection()) {
            connectManagerProxy.switchTo(ConnectType.BLUETOOTH);
            connectManagerProxy.onStart();
            autoConnectIfPossibleToBondedDevice(ConnectReason.AUTO_CONNECT_USB_UNPLUGGED);
          }
        }

        private boolean isUsingUsbConnection() {
          return connectManagerProxy.getType() == ConnectType.USB;
        }
      };

  private class ConnectManagerCallback implements ConnectManager.Callback {
    @Override
    public void onDeviceListCleared() {
      BrailleDisplayLog.d(TAG, "onDeviceListCleared");
      aspectConnection.notifyDeviceListCleared();
    }

    @Override
    public void onDeviceSeen(ConnectableDevice device) {
      BrailleDisplayLog.d(TAG, "onDeviceSeen");
      if (allowDevice(device.name())) {
        BrailleDisplayLog.d(TAG, "onDeviceSeen allow device seen: " + device.name());
        autoConnectIfPossible(ImmutableSet.of(device), ConnectReason.AUTO_CONNECT_DEVICE_SEEN);
        aspectConnection.notifyConnectableDeviceSeen(device);
      }
    }

    @Override
    public void onConnectivityEnabled(boolean enabled) {
      BrailleDisplayLog.d(TAG, "onConnectivityEnabled: " + enabled);
      if (enabled) {
        connectManagerProxy.startSearch(Reason.START_BLUETOOTH_TURNED_ON);
        autoConnectIfPossibleToBondedDevice(
            ConnectReason.AUTO_CONNECT_BONDED_REMEMBERED_BT_TURNED_ON);
      } else {
        connectManagerProxy.stopSearch(Reason.START_BLUETOOTH_TURNED_OFF);
        connectManagerProxy.disconnect();
        aspectConnection.notifyDeviceListCleared();
      }
    }

    @Override
    public void onSearchStatusChanged() {
      BrailleDisplayLog.d(TAG, "onSearchStatusChanged");
      aspectConnection.notifyScanningChanged();
    }

    @Override
    public void onSearchFailure() {
      BrailleDisplayLog.d(TAG, "onSearchFailure");
      aspectConnection.notifyScanningChanged();
    }

    @Override
    public void onConnectStarted() {
      aspectConnection.notifyConnectStarted();
    }

    @Override
    public void onDisconnected() {
      BrailleDisplayLog.d(TAG, "onDisconnected");
      displayProperties = null;
      aspectConnection.notifyConnectionStatusChanged(
          ConnectStatus.DISCONNECTED, /* device= */ null);
    }

    @Override
    public void onDenied(ConnectableDevice device) {
      userDeniedDevices.add(device.address());
    }

    @Override
    public void onConnected(D2dConnection connection) {
      BrailleDisplayLog.d(TAG, "onConnectSuccess");
      ConnectableDevice device = connection.getDevice();
      if (device != null
          && device.name() != null
          && connectManagerProxy.getType() == ConnectType.BLUETOOTH) {
        // We don't remember usb devices.
        PersistentStorage.addRememberedDevice(context, new Pair<>(device.name(), device.address()));
      }
      // In case you are wondering what happens if the call to open() leads to failure... such
      // a failure will NOT be handled on the current tick (see the docs for
      // BtConnection.open()). Therefore, any code that follows the call to open() will
      // execute BEFORE any failure callback gets invoked, which is reasonable and consistent.
      connection.open(mD2dConnectionCallback);

      aspectConnection.notifyConnectionStatusChanged(
          ConnectStatus.CONNECTED, connection.getDevice());
    }

    @Override
    public void onConnectFailure(ConnectableDevice device, Exception exception) {
      BrailleDisplayLog.d(TAG, "onConnectFailure: " + exception.getMessage());
      connectManagerProxy.disconnect();
      aspectConnection.notifyConnectFailed(device == null ? null : device.name());
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
        public void onRead() {
          aspectTraffic.notifyRead();
        }

        @Override
        public void onFatalError(Exception exception) {
          BrailleDisplayLog.e(TAG, "onFatalError: " + exception.getMessage());
          connectManagerProxy.disconnect();
          Toast.makeText(
                  context,
                  context.getString(R.string.bd_bt_connection_disconnected_message),
                  Toast.LENGTH_LONG)
              .show();
        }
      };

  @VisibleForTesting
  ConnectManager.Callback testing_getConnectManagerCallback() {
    return connectManagerCallback;
  }

  @VisibleForTesting
  ConnectManagerProxy testing_getConnectManagerProxy() {
    return connectManagerProxy;
  }
}
