/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.speech;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.HeadphoneStateMonitor.Listener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.compat.provider.SettingsCompatUtils;

/**
 * Manages whether passwords should be spoken on Android O and above by telling the GlobalVariables
 * what to do. This should only be used on API 23+ because it depends on HeadphoneStateMonitor which
 * uses methods added in 23. This is alright because we are using this to support an O feature.
 */
public class SpeakPasswordsManager {

  private final Context context;
  private final HeadphoneStateMonitor headphoneStateMonitor;
  private final GlobalVariables globalVariables;

  private boolean headphonesConnected;

  public SpeakPasswordsManager(
      Context context,
      HeadphoneStateMonitor headphoneStateMonitor,
      GlobalVariables globalVariables) {
    this.context = context;
    this.headphoneStateMonitor = headphoneStateMonitor;
    this.globalVariables = globalVariables;

    headphonesConnected = HeadphoneStateMonitor.isHeadphoneOn(context);

    // Register a listener with the headphone state to keep global variables updated with changes.
    this.headphoneStateMonitor.setHeadphoneListener(headphoneChangeListener);
  }

  /** Handles headphone connection state changes, by updating speak policy in global variables. */
  @VisibleForTesting
  HeadphoneStateMonitor.Listener headphoneChangeListener =
      new Listener() {
        @Override
        public void onHeadphoneStateChanged(boolean isConnected) {
          headphonesConnected = isConnected;
          globalVariables.setSpeakPasswords(shouldSpeakPasswords());
        }
      };

  /**
   * Whether passwords should be spoken out loud, based on the user's set preference and the
   * headphone state if applicable.
   */
  private boolean shouldSpeakPasswords() {
    if (getAlwaysSpeakPasswordsPref(context)) {
      // If the setting is on then the user has selected speaking passwords even without headphones.
      return true;
    } else {
      // Only speak if headphones are present
      return headphonesConnected;
    }
  }

  /** Notify this class that preferences have changed. */
  public void onPreferencesChanged() {
    headphonesConnected = headphoneStateMonitor.hasHeadphones();
    boolean shouldSpeakPasswords = shouldSpeakPasswords();
    globalVariables.setSpeakPasswords(shouldSpeakPasswords);
  }
  /**
   * Gets the current value of the Should Speak Passwords Always (or only with headphones) setting.
   * This takes into account the previous system setting for passwords to be spoken.
   */
  public static boolean getAlwaysSpeakPasswordsPref(Context context) {
    boolean speakSysPref = SettingsCompatUtils.SecureCompatUtils.shouldSpeakPasswords(context);
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getBooleanPref(
        prefs,
        context.getResources(),
        R.string.pref_speak_passwords_without_headphones,
        speakSysPref);
  }
}
