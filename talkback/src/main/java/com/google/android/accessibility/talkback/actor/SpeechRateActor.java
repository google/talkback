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

  /** Read-only interface for actor-state data. */
  public class State {
    public int getSpeechRatePercentage() {
      return SpeechRateActor.this.getSpeechRatePercentage();
    }
  }

  private final Context context;
  private final SharedPreferences prefs;
  public final SpeechRateActor.State state = new SpeechRateActor.State();
  // Speech rate is a multiplier to the TTS_DEFAULT_RATE. Here defines the range from 10% to 600%.
  // Each step is increase/decrease 10%.
  private static final float RATE_MINIMUM = 0.1f;
  private static final float RATE_MAXIMUM = 6.0f;
  private static final float RATE_STEP = 1.1f;
  private int speechRatePercent;

  public SpeechRateActor(Context context) {
    this.context = context;
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    speechRatePercent =
        (int)
            (SharedPreferencesUtils.getFloatFromStringPref(
                    prefs,
                    context.getResources(),
                    R.string.pref_speech_rate_key,
                    R.string.pref_speech_rate_default)
                * 100);
  }

  /**
   * changeSpeechRate: utility to change speech rate based on current settings.
   *
   * @param isIncrease to specify speech rate increase (true) or decrease (false).
   * @return true always.
   */
  public boolean changeSpeechRate(boolean isIncrease) {
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

    // Since the speech rate will no longer be a multiple of RATE_STEP after reaching
    // RATE_MAXIMUM or RATE_MINIMUM, the rate cannot get back to TTS_DEFAULT_RATE
    // with further calculation through RATE_STEP. Therefore, forcing the new rate to be
    // TTS_DEFAULT_RATE when the calculated result is close to 1.
    // The boundary for resetting to TTS_DEFAULT_RATE should consider the value of RATE_STEP to
    // avoid falling into a trap that the new rate could never escape.
    if (newRate > 0.95f && newRate < 1.05f) {
      newRate = 1.0f;
    }

    prefs
        .edit()
        .putString(context.getString(R.string.pref_speech_rate_key), Float.toString(newRate))
        .apply();

    speechRatePercent = (int) (newRate * 100);

    return true;
  }

  public int getSpeechRatePercentage() {
    return speechRatePercent;
  }
}
