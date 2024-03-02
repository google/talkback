package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** A BroadcastReceiver for listening to screen unlock. */
public class ScreenUnlockReceiver
    extends ActionReceiver<ScreenUnlockReceiver, ScreenUnlockReceiver.Callback> {

  public ScreenUnlockReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    if (action.equals(Intent.ACTION_USER_PRESENT)) {
      callback.onUnlock();
    } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
      callback.onLock();
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_OFF};
  }

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onUnlock();

    void onLock();
  }
}
