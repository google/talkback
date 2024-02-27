/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.platform.connect.bt;

import android.os.Handler;
import android.os.Looper;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.D2dConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * A bluetooth connection traffic handler.
 *
 * <p>Outgoing packets are sent to this instance from a background thread owned by the client;
 * incoming packets received from the remote device are forwarded to a callback on a background
 * thread started by this class.
 *
 * <p>Generally speaking there will be some 'connector' code that establishes the connection and
 * gives rise to an instance of this class. After the instance exists, it needs to be opened by
 * invoking the open() method with a Callback as method argument.
 *
 * <p>In the event of an error happening on some bg thread (such as the radio being turned off), the
 * Callback will be informed of the failure on the main thread via {@link Callback#onFatalError}.
 *
 * <p>The notion of getting 'shutdown' is owned by the client; a D2dConnection does not shut itself
 * down, but it can fail, which is similar to being shutdown. The main use case for shutdown is that
 * the user has no more use for the connection (because the user has elected to bail out, or the
 * user has successfully completed their goal).
 */
public class BtConnection implements D2dConnection {

  private static final String TAG = "BtConnection";

  private final ConnectableDevice device;
  private final InputStream inputStream;
  private final OutputStream outputStream;

  private final byte[] readBuffer = new byte[16384];
  private boolean readThreadAlive;

  private Callback callback;

  // Whether or not a failure has occurred and the client has been informed. If this is true, then
  // all subsequent operations should be permanently suspended.
  private boolean isFailed;

  // Shutdown is an operation reserved for the client. We do not shut our self down, but we
  // do release resources in case a failure occurs, which is called failure.
  private boolean isShutdown;

  public BtConnection(
      ConnectableDevice device,
      InputStream inputStream,
      OutputStream outputStream) {
    this.device = device;
    this.inputStream = inputStream;
    this.outputStream = outputStream;
  }

  @Override
  public void open(Callback callback) {
    Utils.assertMainThread();
    this.callback = callback;
    readThreadAlive = true;
    new ReadThread().start();
  }

  // Outgoing traffic
  @Override
  public void sendOutgoingPacket(byte[] packet) {
    if (isShutdown || isFailed) {
      BrailleDisplayLog.e(TAG, "sendOutgoingMessage ignored");
      return;
    }
    try {
      outputStream.write(packet);
      outputStream.flush();
    } catch (IOException e) {
      postExceptionToMain(e);
    }
  }

  @Override
  public void shutdown() {
    BrailleDisplayLog.d(TAG, "shutdown");
    isShutdown = true;
    readThreadAlive = false;
  }

  /** Gets the device associated with this connection. */
  @Override
  public ConnectableDevice getDevice() {
    return device;
  }

  // Incoming traffic
  private class ReadThread extends Thread {
    @Override
    public void run() {
      try {
        while (readThreadAlive) {
          int bytesReadCount = inputStream.read(readBuffer, 0, readBuffer.length);
          String line = String.format(Locale.ENGLISH, "<- (%d bytes)", bytesReadCount);
          if (BrailleDisplayLog.DEBUG) {
            line = line + ". " + Utils.bytesToHexString(readBuffer, bytesReadCount);
          }
          BrailleDisplayLog.v(TAG, line);

          // It is important to pass a copy, instead of the readBuffer itself, because
          // downstream clients might defer their consumption of the byte array, and it's better
          // to not turn over our instance field to them.
          byte[] bytes = new byte[bytesReadCount];
          System.arraycopy(readBuffer, 0, bytes, 0, bytesReadCount);
          callback.onPacketArrived(bytes);
          callback.onRead();
        }
      } catch (IOException ioe) {
        readThreadAlive = false;
        postExceptionToMain(ioe);
      }
    }
  }

  private void postExceptionToMain(Exception exception) {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              BrailleDisplayLog.e(TAG, exception.getMessage());
              if (isShutdown || isFailed) {
                BrailleDisplayLog.e(TAG, "ignore failure because already shutdown or failed");
              } else {
                isFailed = true;
                shutdown();
                BrailleDisplayLog.e(TAG, "invoke onFatalError");
                callback.onFatalError(exception);
              }
            });
  }
}
