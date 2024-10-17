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
