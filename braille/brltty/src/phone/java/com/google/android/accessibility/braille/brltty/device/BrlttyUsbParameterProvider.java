package com.google.android.accessibility.braille.brltty.device;

import android.hardware.usb.UsbDevice;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;

/** Usb device that uses BRLTTY to connect. */
public class BrlttyUsbParameterProvider extends ParameterProvider {
  private static final String TAG_USB = "usb:";
  private final UsbDevice device;

  public BrlttyUsbParameterProvider(UsbDevice device) {
    this.device = device;
  }

  @Override
  public String getParameters() {
    return TAG_USB
        + DELIMITER
        + "serialNumber="
        + BrailleCommonUtils.filterNonPrintCharacter(device.getSerialNumber())
        + DELIMITER
        + "vendorIdentifier="
        + device.getVendorId()
        + DELIMITER
        + "productIdentifier="
        + device.getProductId();
  }
}
