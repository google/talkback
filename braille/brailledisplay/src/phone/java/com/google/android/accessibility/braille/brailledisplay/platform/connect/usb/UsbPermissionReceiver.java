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

package com.google.android.accessibility.braille.brailledisplay.platform.connect.usb;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.ActionReceiver;

/** A registered BroadcastReceiver that informs us of USB state changes. */
public class UsbPermissionReceiver
    extends ActionReceiver<UsbPermissionReceiver, UsbPermissionReceiver.Callback> {

  private static final String ACTION_USB_PERMISSION = ".USB_PERMISSION";

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onPermissionGranted(UsbDevice device);

    void onPermissionDenied(UsbDevice device);
  }

  public UsbPermissionReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    if ((context.getPackageName() + ACTION_USB_PERMISSION).equals(action)) {
      UsbDevice device = extras.getParcelable(UsbManager.EXTRA_DEVICE);
      if (extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED, /* defaultValue= */ false)) {
        callback.onPermissionGranted(device);
      } else {
        callback.onPermissionDenied(device);
      }
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {context.getPackageName() + ACTION_USB_PERMISSION};
  }

  /** Creates a pending intent with usb device. */
  public PendingIntent createPendingIntent(UsbDevice device) {
    Intent intent = new Intent(context.getPackageName() + ACTION_USB_PERMISSION);
    intent.putExtra(UsbManager.EXTRA_DEVICE, device);
    intent.setPackage(context.getPackageName());
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      pendingFlags |= PendingIntent.FLAG_MUTABLE;
    }
    return PendingIntent.getBroadcast(context, /* requestCode= */ 0, intent, pendingFlags);
  }
}
