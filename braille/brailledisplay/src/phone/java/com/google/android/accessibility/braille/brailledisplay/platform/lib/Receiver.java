package com.google.android.accessibility.braille.brailledisplay.platform.lib;

/** Interface that listens for changes. */
public interface Receiver<R> {

  /** Registers this receiver. */
  R registerSelf();

  /** Unregisters this receiver. */
  void unregisterSelf();
}
