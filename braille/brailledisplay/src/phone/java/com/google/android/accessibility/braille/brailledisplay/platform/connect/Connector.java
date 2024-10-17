package com.google.android.accessibility.braille.brailledisplay.platform.connect;

import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;

/** Sets up a connection to a remote device. */
public abstract class Connector {
  private final ConnectableDevice device;

  public Connector(ConnectableDevice device) {
    this.device = device;
  }

  /** Establishes connection to a device. */
  public abstract void connect();

  /** Closes the connection to a device. */
  public abstract void disconnect();

  /** Returns device to connect. */
  public ConnectableDevice getDevice() {
    return device;
  }

  /** Callback for {@link Connector}. */
  public interface Callback {
    /** The connection object is ready. */
    void onConnectSuccess(D2dConnection connection);

    /** Called when connection is terminated. */
    void onDisconnected();

    /**
     * An Exception occurred during setup.
     *
     * @param exception the Exception that was thrown
     * @param device the device that failed to connect
     */
    void onConnectFailure(ConnectableDevice device, Exception exception);
  }
}
