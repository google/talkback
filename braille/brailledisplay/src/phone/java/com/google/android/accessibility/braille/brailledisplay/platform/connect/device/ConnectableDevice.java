package com.google.android.accessibility.braille.brailledisplay.platform.connect.device;

/** Connectable device. */
public abstract class ConnectableDevice {

  /** The name of the connectable device. */
  public abstract String name();

  /** The address of the connectable device. */
  public abstract String address();

  /** Returns a string including both the name and address. */
  @Override
  public String toString() {
    return name() + "(" + address() + ")";
  }
}
