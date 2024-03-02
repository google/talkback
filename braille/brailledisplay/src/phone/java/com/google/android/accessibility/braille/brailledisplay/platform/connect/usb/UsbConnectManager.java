package com.google.android.accessibility.braille.brailledisplay.platform.connect.usb;

import static android.content.Context.BATTERY_SERVICE;

import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.view.WindowManager;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.ConnectManager;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableUsbDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.BatteryChangeReceiver;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ScreenUnlockReceiver;
import com.google.android.accessibility.braille.brailledisplay.settings.BrailleDisplaySettingsActivity;
import com.google.android.accessibility.braille.common.BraillePreferenceUtils;
import com.google.android.accessibility.braille.common.BrailleStringUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Handles usb connection. */
public class UsbConnectManager extends ConnectManager {
  private static final String TAG = "UsbConnectManager";
  private static final int LOW_BATTERY_WARNING_THRESHOLD_PERCENTAGE = 15;
  private static final int INVALID_BATTERY_PERCENTAGE = -1;
  private final UsbPermissionReceiver usbPermissionReceiver;
  private final BatteryChangeReceiver batteryChangeReceiver;
  private final ScreenUnlockReceiver screenUnlockReceiver;
  private final Callback callback;
  private final UsbManager usbManager;
  private final Context context;
  private final BatteryManager batteryManager;
  private final AtomicBoolean askingPermission = new AtomicBoolean();
  private UsbConnection deviceConnection;
  private int batteryVolumePercentage = INVALID_BATTERY_PERCENTAGE;
  private Dialog usbConnectDialog;
  private Dialog batteryLowDialog;

  public UsbConnectManager(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
    batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
    usbPermissionReceiver =
        new UsbPermissionReceiver(
            context,
            new UsbPermissionReceiver.Callback() {
              @Override
              public void onPermissionGranted(UsbDevice device) {
                BrailleDisplayLog.i(TAG, device.getDeviceName() + " usb permission granted.");
                deviceConnection =
                    new UsbConnection(ConnectableUsbDevice.builder().setUsbDevice(device).build());
                callback.onConnected(deviceConnection);
                if (isBatteryLow()) {
                  showBatteryLowDialog();
                } else {
                  showConnectViaUsbDialog();
                }
                askingPermission.set(false);
              }

              @Override
              public void onPermissionDenied(UsbDevice device) {
                BrailleDisplayLog.i(TAG, device.getDeviceName() + " usb permission denied.");
                callback.onDenied(ConnectableUsbDevice.builder().setUsbDevice(device).build());
                askingPermission.set(false);
              }
            });
    batteryChangeReceiver =
        new BatteryChangeReceiver(
            context,
            percentage -> {
              BrailleDisplayLog.i(TAG, "onBatteryChanged: " + percentage);
              showBatteryLowDialog();
            });
    screenUnlockReceiver =
        new ScreenUnlockReceiver(
            context,
            new ScreenUnlockReceiver.Callback() {
              @Override
              public void onUnlock() {
                BrailleDisplayLog.i(TAG, "onUnlock");
                showBatteryLowDialog();
              }

              @Override
              public void onLock() {
                BrailleDisplayLog.i(TAG, "onLock");
                dismissAllDialogs();
              }
            });
    usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
  }

  @Override
  public ConnectType getType() {
    return ConnectType.USB;
  }

  @Override
  public void onStart() {
    callback.onDeviceListCleared();
    usbPermissionReceiver.registerSelf();
    batteryChangeReceiver.registerSelf();
    screenUnlockReceiver.registerSelf();
    startSearch(Reason.START_STARTED);
  }

  @Override
  public void onStop() {
    usbPermissionReceiver.unregisterSelf();
    batteryChangeReceiver.unregisterSelf();
    screenUnlockReceiver.unregisterSelf();
    disconnect();
  }

  @Override
  public void startSearch(Reason reason) {
    for (ConnectableDevice device : getBondedDevices()) {
      callback.onDeviceSeen(device);
    }
  }

  @Override
  public void stopSearch(Reason reason) {}

  @Override
  public void connect(ConnectableDevice device) {
    UsbDevice usbDevice = ((ConnectableUsbDevice) device).usbDevice();
    if (usbManager.hasPermission(usbDevice)) {
      deviceConnection = new UsbConnection(device);
      callback.onConnected(deviceConnection);
      if (isBatteryLow()) {
        showBatteryLowDialog();
      } else {
        showConnectViaUsbDialog();
      }
    } else {
      usbManager.requestPermission(usbDevice, usbPermissionReceiver.createPendingIntent(usbDevice));
      askingPermission.set(true);
    }
  }

  @Override
  public void disconnect() {
    BrailleDisplayLog.i(TAG, "disconnect: " + (deviceConnection != null));
    if (deviceConnection != null) {
      deviceConnection.shutdown();
      deviceConnection = null;
      callback.onDisconnected();
    }
    dismissAllDialogs();
    batteryVolumePercentage = INVALID_BATTERY_PERCENTAGE;
  }

  @Override
  public void sendOutgoingPacket(byte[] packet) {
    if (deviceConnection != null) {
      deviceConnection.sendOutgoingPacket(packet);
    }
  }

  @Override
  public boolean isConnecting() {
    return askingPermission.get();
  }

  @Override
  public boolean isConnected() {
    return deviceConnection != null;
  }

  @Override
  public boolean isScanning() {
    return false;
  }

  @Override
  public Collection<ConnectableDevice> getConnectableDevices() {
    return getBondedDevices();
  }

  @Override
  public Set<ConnectableDevice> getBondedDevices() {
    return usbManager.getDeviceList().values().stream()
        .map(device -> ConnectableUsbDevice.builder().setUsbDevice(device).build())
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectingDevice() {
    return Optional.empty();
  }

  @Override
  public Optional<ConnectableDevice> getCurrentlyConnectedDevice() {
    return Optional.ofNullable(deviceConnection).map(UsbConnection::getDevice);
  }

  private void dismissAllDialogs() {
    if (usbConnectDialog != null) {
      usbConnectDialog.dismiss();
    }
    if (batteryLowDialog != null) {
      batteryLowDialog.dismiss();
    }
  }

  private void showConnectViaUsbDialog() {
    if (!BrailleUserPreferences.readShowUsbConnectDialog(context)
        || (usbConnectDialog != null && usbConnectDialog.isShowing())
        || isScreenLocked()
        || getAccessibilityServiceContextProvider().getAccessibilityServiceContext() == null) {
      // There should be no chance that usb get connected (Need permission approval) when screen is
      // off.
      return;
    }
    usbConnectDialog =
        BraillePreferenceUtils.createTipAlertDialog(
            getAccessibilityServiceContextProvider().getAccessibilityServiceContext(),
            context.getString(R.string.bd_usb_connect_dialog_title),
            context.getString(
                R.string.bd_usb_connect_dialog_message, context.getString(R.string.bd_device)),
            BrailleUserPreferences::writeShowUsbConnectDialog);
    usbConnectDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    usbConnectDialog.show();
  }

  private void showBatteryLowDialog() {
    BrailleDisplayLog.i(TAG, "isConnected: " + isConnected());
    if (!isConnected()
        || isScreenLocked()
        || getAccessibilityServiceContextProvider().getAccessibilityServiceContext() == null) {
      return;
    }
    int newBatteryVolumePercentage = getBatteryPercentage();
    BrailleDisplayLog.i(
        TAG,
        "batteryVolume: "
            + batteryVolumePercentage
            + "; percentage: "
            + newBatteryVolumePercentage);
    boolean showDialog = false;
    if (batteryVolumePercentage != newBatteryVolumePercentage
        && (batteryVolumePercentage > LOW_BATTERY_WARNING_THRESHOLD_PERCENTAGE
            || batteryVolumePercentage == INVALID_BATTERY_PERCENTAGE)
        && newBatteryVolumePercentage <= LOW_BATTERY_WARNING_THRESHOLD_PERCENTAGE) {
      showDialog = true;
    }
    batteryVolumePercentage = newBatteryVolumePercentage;
    if (showDialog && (batteryLowDialog == null || !batteryLowDialog.isShowing())) {
      batteryLowDialog =
          MaterialComponentUtils.alertDialogBuilder(
                  getAccessibilityServiceContextProvider().getAccessibilityServiceContext())
              .setTitle(
                  BrailleStringUtils.toCharacterTitleCase(
                      context.getString(
                          R.string.bd_battery_low_dialog_title,
                          context.getString(R.string.bd_device))))
              .setMessage(
                  context.getString(
                      R.string.bd_battery_low_dialog_message,
                      context.getString(R.string.bd_device)))
              .setPositiveButton(
                  R.string.bd_battery_low_dialog_button,
                  (dialog, which) -> {
                    Intent intent = new Intent(context, BrailleDisplaySettingsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                  })
              .create();
      batteryLowDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
      batteryLowDialog.show();
    }
  }

  private boolean isBatteryLow() {
    return getBatteryPercentage() <= LOW_BATTERY_WARNING_THRESHOLD_PERCENTAGE;
  }

  private boolean isScreenLocked() {
    KeyguardManager keyguardManager =
        (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    BrailleDisplayLog.i(TAG, "screen is locked: " + keyguardManager.isKeyguardLocked());
    return keyguardManager.isKeyguardLocked();
  }

  private int getBatteryPercentage() {
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
  }

  @VisibleForTesting
  public UsbPermissionReceiver testing_getUsbPermissionReceiver() {
    return usbPermissionReceiver;
  }

  @VisibleForTesting
  public BatteryChangeReceiver testing_getBatteryChangeReceiver() {
    return batteryChangeReceiver;
  }
}
