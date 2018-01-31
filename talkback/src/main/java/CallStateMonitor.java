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

package com.google.android.accessibility.talkback;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.google.android.accessibility.utils.LogUtils;
import java.util.ArrayList;
import java.util.List;

/** {@link BroadcastReceiver} for detecting incoming calls. */
public class CallStateMonitor extends BroadcastReceiver {

  /** Interface to be notified when phone call state changes. */
  public interface CallStateChangedListener {

    /** Callback to be invoked when phone call state changes. */
    void onCallStateChanged(int oldState, int newState);
  }

  public static final IntentFilter STATE_CHANGED_FILTER =
      new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

  private final TalkBackService mService;
  private final TelephonyManager mTelephonyManager;
  private final List<CallStateChangedListener> mCallStateChangedListeners = new ArrayList<>();

  private int mLastCallState;
  private boolean mIsStarted;

  public CallStateMonitor(TalkBackService context) {
    mService = context;
    mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!TalkBackService.isServiceActive()) {
      LogUtils.log(
          CallStateMonitor.class, Log.WARN, "Service not initialized during " + "broadcast.");
      return;
    }

    int oldState = mLastCallState;
    int newState;

    final String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
    if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
      newState = TelephonyManager.CALL_STATE_IDLE;
    } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
      newState = TelephonyManager.CALL_STATE_OFFHOOK;
    } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
      newState = TelephonyManager.CALL_STATE_RINGING;
    } else {
      return;
    }
    if (newState != oldState) {
      LogUtils.log(
          this,
          Log.VERBOSE,
          "Call state changed: %s -> %s",
          callStateToString(mLastCallState),
          callStateToString(newState));

      mLastCallState = newState;
      for (CallStateChangedListener listener : mCallStateChangedListeners) {
        listener.onCallStateChanged(oldState, newState);
      }
    }
  }

  /** Registers a callback to be invoked when phone call state changes. */
  public void addCallStateChangedListener(CallStateChangedListener listener) {
    if (listener != null) {
      mCallStateChangedListeners.add(listener);
    }
  }

  /**
   * Starts monitoring phone call state by registering a broadcast receiver to listen to
   * ACTION_PHONE_STATE_CHANGED intent. This happens only if READ_PHONE_STATE permission is granted.
   */
  public void startMonitor() {
    if (mIsStarted) {
      return;
    }
    // Starting from M, permission model has changed, so that TalkBack is not granted with
    // READ_PHONE_STATE permission by default. Then there is no need to register the broadcast
    // receiver.
    if (isCallStatePermissionGranted()) {
      LogUtils.log(this, Log.DEBUG, "Start monitoring call state.");
      mLastCallState = mTelephonyManager.getCallState();
      mService.registerReceiver(this, STATE_CHANGED_FILTER);
      mIsStarted = true;
    } else {
      LogUtils.log(
          this,
          Log.WARN,
          "Fail to start monitoring phone state: " + "READ_PHONE_STATE permission is not granted.");
    }
  }

  /** Unregisters broadcast receiver and stop monitoring phone call state. */
  public void stopMonitor() {
    if (mIsStarted) {
      LogUtils.log(this, Log.DEBUG, "Stop monitoring call state.");
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
    return mIsStarted ? mLastCallState : mTelephonyManager.getCallState();
  }

  /**
   * Return {@code true} if there is at least one call ringing/waiting/dialing/active or on hold.
   */
  public boolean isPhoneCallActive() {
    int currentState = getCurrentCallState();
    return currentState == TelephonyManager.CALL_STATE_RINGING
        || currentState == TelephonyManager.CALL_STATE_OFFHOOK;
  }

  public String getStatusSummary() {
    return "[Phone call state: " + callStateToString(getCurrentCallState()) + "]";
  }

  private static String callStateToString(int state) {
    switch (state) {
      case TelephonyManager.CALL_STATE_IDLE:
        return "CALL_STATE_IDLE";
      case TelephonyManager.CALL_STATE_OFFHOOK:
        return "CALL_STATE_OFFHOOK";
      case TelephonyManager.CALL_STATE_RINGING:
        return "CALL_STATE_RINGING";
      default:
        return "(unhandled)";
    }
  }

  /** @return whether the permission READ_PHONE_STATE is granted to TalkBack. */
  private boolean isCallStatePermissionGranted() {
    return ContextCompat.checkSelfPermission(mService, Manifest.permission.READ_PHONE_STATE)
        == PackageManager.PERMISSION_GRANTED;
  }
}
