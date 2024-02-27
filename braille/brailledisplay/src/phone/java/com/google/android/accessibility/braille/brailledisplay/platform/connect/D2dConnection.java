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

package com.google.android.accessibility.braille.brailledisplay.platform.connect;

import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;

/** Interface that allows two remote devices to send packets back and forth. */
public interface D2dConnection {

  /** Callback. */
  interface Callback {

    /**
     * Invoked when a packet is received from the other device and is ready to be consumed.
     *
     * <p>This method runs on a background thread.
     *
     * @param packet the byte[] containing the payload
     */
    void onPacketArrived(byte[] packet);

    /** Invoked when should read commands. */
    void onRead();

    /**
     * Invoked when a fatal connection error has occurred.
     *
     * <p>This method runs on the main thread. It will be invoked if and only if the connection has
     * not been shutdown by the client.
     *
     * @param exception the Exception
     */
    void onFatalError(Exception exception);
  }

  /**
   * Open the connection for reading and writing. This does not block, and must be invoked on the
   * main thread.
   *
   * <p>Client invokes this when in a position to consume incoming data. This must be invoked in
   * order to receive incoming traffic callbacks, and also must be invoked before sending any
   * messages or an error will occur.
   *
   * <p>If an error occurs due to this invocation, {@link Callback#onFatalError(Exception)} will be
   * invoked; that invocation item will be appended to the main thread's work queue, and thus will
   * not be invoked on the current tick. This delaying until the next tick is unavoidable because
   * the opening of the read channel will involve background threads.
   */
  void open(Callback callback);

  /**
   * Instructs the connection to send an outgoing packet.
   *
   * <p>This should be invoked by a non-main thread owned by the client of this class.
   *
   * <p>Any failure will generally invoke {@link Callback#onFatalError(Exception)}, but nothing
   * occurs if the connection has already been externally shutdown or if it has internally failed
   * before this method is invoked.
   *
   * @param packet the packet message
   */
  void sendOutgoingPacket(byte[] packet);

  /** Instructs the connection to be shutdown (release any resources we own). */
  void shutdown();

  /** Returns connected device. */
  ConnectableDevice getDevice();
}
