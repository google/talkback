package com.google.android.accessibility.braille.common;

import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;
import java.io.IOException;

/** This class is not visible to public yet so we put a fake one temporarily. */
public class FakeBrailleDisplayController {
  /** Callback interface for BrailleDisplayController. */
  public interface BrailleDisplayCallback {
    void onConnected(byte[] descriptor);

    void onConnectionFailed(int error);

    void onDisconnected();

    void onInput(byte[] input);
  }

  public void connect(BluetoothDevice device, BrailleDisplayCallback callback) {}

  public void connect(UsbDevice device, BrailleDisplayCallback callback) {}

  public void write(byte[] data) throws IOException {}

  public void disconnect() {}

  public boolean isConnected() {
    return false;
  }
}
