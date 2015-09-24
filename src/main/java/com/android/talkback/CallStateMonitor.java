/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.utils.LogUtils;
import com.google.android.marvin.talkback.TalkBackService;

/**
 * {@link BroadcastReceiver} for detecting incoming calls.
 */
public class CallStateMonitor extends BroadcastReceiver {
    private static final IntentFilter STATE_CHANGED_FILTER = new IntentFilter(
            TelephonyManager.ACTION_PHONE_STATE_CHANGED);

    private final TalkBackService mService;
    private int mLastCallState;
    private boolean mIsStarted;

    public CallStateMonitor(TalkBackService context) {
        mService = context;
        mLastCallState = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE))
                .getCallState();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TalkBackService.isServiceActive()) {
            LogUtils.log(CallStateMonitor.class, Log.WARN, "Service not initialized during "
                    + "broadcast.");
            return;
        }

        final String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            mLastCallState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            mLastCallState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            mLastCallState = TelephonyManager.CALL_STATE_RINGING;
        }

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            mService.interruptAllFeedback();
        }

        final ShakeDetector shakeDetector = mService.getShakeDetector();
        if (shakeDetector != null) {
            // Shake detection should be stopped during phone calls.
            if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                shakeDetector.setEnabled(false);
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                shakeDetector.setEnabled(true);
            }
        }
    }

    public boolean isStarted() {
        return mIsStarted;
    }

    public void startMonitor() {
        if (!mIsStarted) {
            mService.registerReceiver(this, STATE_CHANGED_FILTER);
            mIsStarted = true;
        }
    }

    public void stopMonitor() {
        if (mIsStarted) {
            mService.unregisterReceiver(this);
            mIsStarted = false;
        }
    }

    /**
     * Returns the current device call state
     *
     * @return One of the call state constants from {@link TelephonyManager}.
     */
    public int getCurrentCallState() {
        return mLastCallState;
    }
}
