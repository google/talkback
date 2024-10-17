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
import java.util.Optional;
import java.util.function.Predicate;

/** Stub Brltty encoder. */
public class BrlttyEncoder implements Encoder {

  /** Stub brltty factory. */
  public static class BrlttyFactory implements Factory {

    @Override
    public Encoder createEncoder(Context context, Callback callback) {
      return new BrlttyEncoder(context, callback);
    }

    @Override
    public Predicate<String> getDeviceNameFilter() {
      return null;
    }
  }

  public BrlttyEncoder(Context context, Callback callback) {}

  @Override
  public Optional<BrailleDisplayProperties> start(String deviceName, String parameters) {
    return Optional.empty();
  }

  @Override
  public void stop() {}

  @Override
  public void consumePacketFromDevice(byte[] packet) {}

  @Override
  public void writeBrailleDots(byte[] brailleDotBytes) {}

  @Override
  public int readCommand() {
    return 0;
  }
}
