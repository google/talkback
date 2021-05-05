/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** This class supports the changing of speech rate. */
public class SpeechRateActor {

  private final Context context;

  public SpeechRateActor(Context context) {
    this.context = context;
  }

  /**
   * changeSpeechRate: utility to change speech rate based on current settings.
   *
   * @param isIncrease to specify speech rate increase (true) or decrease (false).
   * @return false if the speech rate reaches the upper/lower bound; true otherwise.
   */
  private static final float RATE_MINIMUM = 0.16150558f;

  private static final float RATE_MAXIMUM = 6.191736422f;
  private static final float RATE_STEP = 1.1f;

  public boolean changeSpeechRate(boolean isIncrease) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    float currentRate =
        SharedPreferencesUtils.getFloatFromStringPref(
            prefs,
            context.getResources(),
            R.string.pref_speech_rate_key,
            R.string.pref_speech_rate_default);
    float newRate =
        isIncrease
            ? Math.min(currentRate * RATE_STEP, RATE_MAXIMUM)
            : Math.max(currentRate / RATE_STEP, RATE_MINIMUM);

    prefs
        .edit()
        .putString(context.getString(R.string.pref_speech_rate_key), Float.toString(newRate))
        .apply();

    return isIncrease ? (newRate < RATE_MAXIMUM) : (newRate > RATE_MINIMUM);
  }
}
