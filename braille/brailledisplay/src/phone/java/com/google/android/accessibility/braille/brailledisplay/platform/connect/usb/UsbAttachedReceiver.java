package com.google.android.accessibility.braille.brailledisplay.platform.connect.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableUsbDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ActionReceiver;

/** Detects usb attach/detach events. */
public class UsbAttachedReceiver
    extends ActionReceiver<UsbAttachedReceiver, UsbAttachedReceiver.Callback> {

  public UsbAttachedReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onUsbAttached(ConnectableDevice device);

    void onUsbDetached(ConnectableDevice device);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    UsbDevice usbDevice = extras.getParcelable(UsbManager.EXTRA_DEVICE);
    ConnectableUsbDevice device = ConnectableUsbDevice.builder().setUsbDevice(usbDevice).build();
    if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
      callback.onUsbAttached(device);
    } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
      callback.onUsbDetached(device);
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {
      UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED
    };
  }
}
