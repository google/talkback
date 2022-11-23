package com.google.android.accessibility.braille.brltty;

import android.content.Context;
import java.util.Optional;
import java.util.function.Predicate;

/** Stub Brltty encoder. */
public class BrlttyEncoder implements Encoder {

  /** Stub brltty factory. */
  public static class BrlttyFactory implements Factory {

    @Override
    public Encoder createEncoder(
        Context context, Callback callback, String deviceName, String address) {
      return new BrlttyEncoder(context, callback, deviceName, address);
    }

    @Override
    public Predicate<String> getDeviceNameFilter() {
      return null;
    }
  }

  public BrlttyEncoder(Context context, Callback callback, String deviceName, String address) {}

  @Override
  public Optional<BrailleDisplayProperties> start() {
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
