/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.monitor;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import org.checkerframework.checker.nullness.qual.NonNull;

/** Monitor battery charging status changes. Start charging Stop changing */
public class BatteryMonitor extends BroadcastReceiver {
  private Pipeline.InterpretationReceiver pipeline;

  public static final int UNKNOWN_LEVEL = -1;
  private int batteryLevel = UNKNOWN_LEVEL;

  public BatteryMonitor() {}

  public void setPipeline(@NonNull InterpretationReceiver pipeline) {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    this.pipeline = pipeline;
  }

  public IntentFilter getFilter() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
    intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
    intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
    return intentFilter;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    final String action = intent.getAction();
    if (action == null) {
      return;
    }

    switch (action) {
      case Intent.ACTION_BATTERY_CHANGED:
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        batteryLevel = getBatteryLevel(scale, level);
        break;
      case Intent.ACTION_POWER_DISCONNECTED:
        pipeline.input(
            EVENT_ID_UNTRACKED, new Interpretation.Power(/* connected= */ false, batteryLevel));
        break;
      case Intent.ACTION_POWER_CONNECTED:
        pipeline.input(
            EVENT_ID_UNTRACKED, new Interpretation.Power(/* connected= */ true, batteryLevel));
        break;
      default:
        // Do nothing.
    }
  }

  @VisibleForTesting
  public static int getBatteryLevel(int scale, int level) {
    return (scale > 0 ? Math.round((level / (float) scale) * 100) : UNKNOWN_LEVEL);
  }
}
