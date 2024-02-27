package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;

/** A BroadcastReceiver for listening to battery change. */
public class BatteryChangeReceiver
    extends ActionReceiver<BatteryChangeReceiver, BatteryChangeReceiver.Callback> {
  private static final int PERCENTAGE = 100;

  public BatteryChangeReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    int level = extras.getInt(BatteryManager.EXTRA_LEVEL, /* defaultValue= */ -1);
    int scale = extras.getInt(BatteryManager.EXTRA_SCALE, /* defaultValue= */ -1);
    callback.onBatteryVolumePercentageChanged((int) (((double) level / scale) * PERCENTAGE));
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {Intent.ACTION_BATTERY_CHANGED};
  }

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onBatteryVolumePercentageChanged(int percentage);
  }
}
