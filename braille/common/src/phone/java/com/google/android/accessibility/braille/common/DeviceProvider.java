package com.google.android.accessibility.braille.common;

/** Provides a device. */
public class DeviceProvider<T> {
  private final T device;

  public DeviceProvider(T device) {
    this.device = device;
  }

  /** Returns stored device. */
  public T getDevice() {
    return device;
  }
}
