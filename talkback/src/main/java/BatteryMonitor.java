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

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.telephony.TelephonyManager;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;

/** Monitor battery charging status changes. Start charging Stop changing */
public class BatteryMonitor extends BroadcastReceiver {
  private SpeechController mSpeechController;

  private TelephonyManager mTelephonyManager;

  private Context mContext;

  private int mBatteryLevel = -1;

  public BatteryMonitor(
      Context context, SpeechController speechController, TelephonyManager telephonyManager) {
    if (speechController == null) throw new IllegalStateException();

    mContext = context;
    mSpeechController = speechController;
    mTelephonyManager = telephonyManager;
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
    if ((mTelephonyManager != null)
        && (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
      return;
    }
    final String action = intent.getAction();
    if (action == null) {
      return;
    }

    String announcement = null;
    switch (action) {
      case Intent.ACTION_BATTERY_CHANGED:
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        mBatteryLevel = (scale > 0 ? Math.round(level / (float) scale * 100) : -1);
        break;
      case Intent.ACTION_POWER_DISCONNECTED:
        // Announces the battery level only when we have updated battery level information.
        if (mBatteryLevel == -1) {
          announcement =
              mContext.getString(
                  R.string.template_charging_lite,
                  mContext.getString(R.string.notification_type_status_stopped));
        } else {
          announcement =
              mContext.getString(
                  R.string.template_charging,
                  mContext.getString(R.string.notification_type_status_stopped),
                  String.valueOf(mBatteryLevel));
        }
        break;
      case Intent.ACTION_POWER_CONNECTED:
        if (mBatteryLevel == -1) {
          announcement =
              mContext.getString(
                  R.string.template_charging_lite,
                  mContext.getString(R.string.notification_type_status_started));
        } else {
          announcement =
              mContext.getString(
                  R.string.template_charging,
                  mContext.getString(R.string.notification_type_status_started),
                  String.valueOf(mBatteryLevel));
        }
        break;
    }
    if (announcement != null) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not a user-initiated event.
      mSpeechController.speak(
          announcement, /* Text */
          SpeechController.QUEUE_MODE_INTERRUPT, /* QueueMode */
          FeedbackItem.FLAG_NO_HISTORY | FeedbackItem.FLAG_FORCED_FEEDBACK, /* Flags */
          null, /* SpeechParams */
          eventId);
    }
  }
}
