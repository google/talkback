/*
 * Copyright 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.braille.brailledisplay.platform.connect.hid;

import android.content.Context;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.FeatureFlagReader;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.Connector;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.common.FakeBrailleDisplayController;
import java.util.Arrays;

/** Sets up a connection using Hid protocol. */
public abstract class HidConnector extends Connector {
  private static final String TAG = "HidConnector";
  private final Context context;
  private final Connector.Callback connectorCallback;
  private final FakeBrailleDisplayController brailleDisplayController;

  public HidConnector(
      Context context,
      ConnectableDevice device,
      Connector.Callback callback,
      FakeBrailleDisplayController brailleDisplayController) {
    super(device);
    this.context = context;
    this.connectorCallback = callback;
    this.brailleDisplayController = brailleDisplayController;
  }

  /** Returns if a connector for HID available. */
  public boolean isAvailable() {
    return FeatureFlagReader.isBdHidSupported(context);
  }

  /** Get BrailleDisplayController. */
  public FakeBrailleDisplayController getBrailleDisplayController() {
    return brailleDisplayController;
  }

  /** BrailleDisplayController.BrailleDisplayCallback comes from framework. */
  public class BrailleDisplayCallback
      implements FakeBrailleDisplayController.BrailleDisplayCallback {
    private byte[] previousInput;
    private D2dConnection.Callback d2dConnectionCallback;

    @Override
    public void onConnected(byte[] descriptor) {
      BrailleDisplayLog.d(TAG, "BrailleDisplayCallback#onConnected");
      connectorCallback.onConnectSuccess(
          new HidConnection(getDevice(), callback -> d2dConnectionCallback = callback));
    }

    @Override
    public void onConnectionFailed(int error) {
      BrailleDisplayLog.e(TAG, "BrailleDisplayCallback#onConnectionFailed error=" + error);
      connectorCallback.onConnectFailure(getDevice(), new Exception(String.valueOf(error)));
    }

    @Override
    public void onDisconnected() {
      BrailleDisplayLog.d(TAG, "BrailleDisplayCallback#onDisconnected");
      connectorCallback.onDisconnected();
    }

    @Override
    public void onInput(byte[] input) {
      BrailleDisplayLog.d(TAG, "BrailleDisplayCallback#onInput");
      // Ignore repeated input. Not necessary for correctness, but saves wasted effort.
      // BI20X unnecessarily send input reports multiple times.
      if (Arrays.equals(input, previousInput)) {
        return;
      }
      previousInput = input;
      if (d2dConnectionCallback != null) {
        d2dConnectionCallback.onRead();
      }
    }
  }
}
