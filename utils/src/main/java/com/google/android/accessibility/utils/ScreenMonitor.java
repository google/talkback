/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.utils;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

/** {@link BroadcastReceiver} for receiving updates for the screen state. */
public final class ScreenMonitor extends BroadcastReceiver {
  /** The intent filter to match phone and screen state changes. */
  private static final IntentFilter SCREEN_CHANGE_FILTER = new IntentFilter();

  static {
    SCREEN_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_ON);
    SCREEN_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_OFF);
  }

  private final PowerManager powerManager;
  private final ScreenStateChangeListener screenStateListener;
  private boolean isScreenOn;

  /** Listens to changes when screen is turned off. */
  public interface ScreenStateChangeListener {
    void screenTurnedOff();
  }

  /**
   * Returns whether the device is currently locked.
   *
   * @return {@code true} if device is locked.
   */
  public static boolean isDeviceLocked(Context context) {
    KeyguardManager keyguardManager =
        (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    return ((keyguardManager != null) && keyguardManager.isKeyguardLocked());
  }

  public ScreenMonitor(PowerManager powerManager) {
    this(powerManager, null);
  }

  public ScreenMonitor(PowerManager powerManager, ScreenStateChangeListener screenStateListener) {
    this.powerManager = powerManager;
    this.screenStateListener = screenStateListener;
    updateScreenState();
  }

  public boolean isScreenOn() {
    return isScreenOn;
  }

  public IntentFilter getFilter() {
    return SCREEN_CHANGE_FILTER;
  }

  public void updateScreenState() {
    isScreenOn = (powerManager != null) && powerManager.isInteractive();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (action == null) {
      return;
    }

    switch (action) {
      case Intent.ACTION_SCREEN_ON:
        isScreenOn = true;
        break;
      case Intent.ACTION_SCREEN_OFF:
        isScreenOn = false;
        if (screenStateListener != null) {
          screenStateListener.screenTurnedOff();
        }
        break;
      default: // fall out
    }
  }
}
