package com.google.android.accessibility.braille.brailledisplay.platform.connect.device;

import android.bluetooth.BluetoothDevice;
import com.google.auto.value.AutoValue;

/** Bluetooth connectable device. */
@AutoValue
public abstract class ConnectableBluetoothDevice extends ConnectableDevice {

  public abstract BluetoothDevice bluetoothDevice();

  @Override
  public String name() {
    return bluetoothDevice().getName();
  }

  @Override
  public String address() {
    return bluetoothDevice().getAddress();
  }

  public static ConnectableBluetoothDevice.Builder builder() {
    return new AutoValue_ConnectableBluetoothDevice.Builder();
  }

  /** Builder for {@code ConnectableBluetoothDevice}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBluetoothDevice(BluetoothDevice device);

    public abstract ConnectableBluetoothDevice build();
  }
}
