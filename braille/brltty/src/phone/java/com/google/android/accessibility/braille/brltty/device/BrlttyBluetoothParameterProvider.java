package com.google.android.accessibility.braille.brltty.device;

import android.bluetooth.BluetoothDevice;

/** Bluetooth device that uses BRLTTY to connect. */
public class BrlttyBluetoothParameterProvider extends ParameterProvider {
  private static final String TAG_BLUETOOTH = "bluetooth:";
  private final BluetoothDevice device;

  public BrlttyBluetoothParameterProvider(BluetoothDevice device) {
    this.device = device;
  }

  @Override
  public String getParameters() {
    return TAG_BLUETOOTH + device.getAddress();
  }
}
