package com.google.android.accessibility.braille.brltty.device;

import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;
import com.google.android.accessibility.braille.common.DeviceProvider;

/** A Factory to generate object of BRLTTY device based on given information. */
public class BrlttyParameterProviderFactory {

  /** Returns a BrlttyDevice according to ConnectableDevice. */
  public ParameterProvider getParameterProvider(DeviceProvider<?> provider) {
    if (provider.getDevice() instanceof BluetoothDevice) {
      return new BrlttyBluetoothParameterProvider((BluetoothDevice) provider.getDevice());
    } else if (provider.getDevice() instanceof UsbDevice) {
      return new BrlttyUsbParameterProvider((UsbDevice) provider.getDevice());
    }
    throw new IllegalArgumentException("No matching connect type.");
  }
}
