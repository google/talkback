/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.braille.brltty;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;

/** Helps establish usb connection and provide BRLTTY native library requested methods. */
public class UsbHelper {

  /** Returns an iterator over all USB devices currently plugged in, as USBDevice instances. */
  public static Iterator<UsbDevice> getDeviceIterator(Context context) {
    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
    return devices.values().iterator();
  }

  /** Returns next usb device in the iterator. */
  @Nullable
  public static UsbDevice getNextDevice(Iterator<UsbDevice> iterator) {
    return iterator.hasNext() ? iterator.next() : null;
  }

  /** Gets the device interface that matches the identifier. */
  @Nullable
  public static UsbInterface getDeviceInterface(UsbDevice device, int identifier) {
    int count = device.getInterfaceCount();
    for (int index = 0; index < count; index += 1) {
      UsbInterface intf = device.getInterface(index);
      if (identifier == intf.getId()) {
        return intf;
      }
    }

    return null;
  }

  /** Returns the value of an usb endpoint for a given endpoint interface. */
  @Nullable
  public static UsbEndpoint getInterfaceEndpoint(UsbInterface usbInterface, int address) {
    int count = usbInterface.getEndpointCount();

    for (int index = 0; index < count; index += 1) {
      UsbEndpoint endpoint = usbInterface.getEndpoint(index);

      if (address == endpoint.getAddress()) {
        return endpoint;
      }
    }

    return null;
  }

  /** Opens usb connection for the requested device. */
  public static UsbDeviceConnection openDeviceConnection(Context context, UsbDevice device) {
    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    return usbManager.openDevice(device);
  }

  /** Cancels usb request. */
  public static void cancelRequest(UsbRequest request) {
    request.cancel();
    request.close();
  }
}
