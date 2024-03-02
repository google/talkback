package com.google.android.accessibility.braille.brailledisplay.platform.connect.device;

import android.hardware.usb.UsbDevice;
import com.google.auto.value.AutoValue;

/** Connectable USB device. */
@AutoValue
public abstract class ConnectableUsbDevice extends ConnectableDevice {

  public abstract UsbDevice usbDevice();

  @Override
  public String name() {
    return usbDevice().getProductName();
  }

  @Override
  public String address() {
    return String.valueOf(usbDevice().getDeviceId());
  }

  public static ConnectableUsbDevice.Builder builder() {
    return new AutoValue_ConnectableUsbDevice.Builder();
  }

  /** Builder for {@code ConnectableUsbDevice}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setUsbDevice(UsbDevice device);

    public abstract ConnectableUsbDevice build();
  }
}
