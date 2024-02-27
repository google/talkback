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

package com.google.android.accessibility.braille.brltty;

import android.content.Context;
import java.util.Optional;
import java.util.function.Predicate;

/** Encodes messages to, and decodes messages from, a braille display. */
public interface Encoder {

  /** Factory for creating Encoder. */
  interface Factory {
    Encoder createEncoder(Context context, Callback callback);

    Predicate<String> getDeviceNameFilter();
  }

  /** The callback. */
  interface Callback {
    void sendPacketToDevice(byte[] packet);

    void readAfterDelay(int delayMs);
  }

  /**
   * Starts this instance.
   *
   * <p>The implementation of this method is expected to block while it performs cross-device
   * handshaking, which will involve packets being sent back and forth.
   */
  Optional<BrailleDisplayProperties> start(String name, String parameters);

  /** Stops this instance. */
  void stop();

  /** Delivers a packet from the remote device for consumption. */
  void consumePacketFromDevice(byte[] packet);

  /** Delivers an unencoded list of braille dots for encoding and eventual cross-device sending. */
  void writeBrailleDots(byte[] brailleDotBytes);

  /** Reads the current command from the remote device, if any; otherwise returns -1. */
  int readCommand();
}
