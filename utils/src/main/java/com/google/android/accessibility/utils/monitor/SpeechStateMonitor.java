/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.utils.monitor;

import static android.view.accessibility.AccessibilityEvent.SPEECH_STATE_LISTENING_END;
import static android.view.accessibility.AccessibilityEvent.SPEECH_STATE_LISTENING_START;
import static android.view.accessibility.AccessibilityEvent.SPEECH_STATE_SPEAKING_END;
import static android.view.accessibility.AccessibilityEvent.SPEECH_STATE_SPEAKING_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_SPEECH_STATE_CHANGE;

import android.annotation.TargetApi;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Monitors the event TYPE_SPEECH_STATE_CHANGE from the other applications to start or stop Talkback
 * speaking or listening.
 */
@TargetApi(33)
public class SpeechStateMonitor {
  /** The possible states from the other applications which SpeechStateMonitor keeps. */
  @IntDef({SPEECH_STATE_SPEAKING_START, SPEECH_STATE_LISTENING_START})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ReadSpeechState {}

  // The max time of valid speech state. After that, the speech state will be regarded as invalid.
  private static final int MAX_CHECK_STATE_MILLISEC = 300000;

  private final HashMap<String, StateTimeStamp> packageToLastState = new HashMap<>();

  // The last up time of speech state from the same package name.
  private long lastSpeechUptimeMillisec = 0;

  /** Read-only interface for reading speech state. */
  // public final State state = new State();
  public int getEventTypes() {
    return TYPE_SPEECH_STATE_CHANGE;
  }

  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event.getEventType() != TYPE_SPEECH_STATE_CHANGE) {
      return;
    }
    String senderPackage = event.getPackageName().toString();
    if (TextUtils.isEmpty(senderPackage)) {
      return;
    }
    int changeTypes = event.getSpeechStateChangeTypes();

    @Nullable StateTimeStamp oldState = packageToLastState.get(senderPackage);

    if ((oldState != null)
        && (((changeTypes == SPEECH_STATE_SPEAKING_END)
                && (oldState.getState() == SPEECH_STATE_SPEAKING_START))
            || ((changeTypes == SPEECH_STATE_LISTENING_END)
                && (oldState.getState() == SPEECH_STATE_LISTENING_START)))) {
      packageToLastState.remove(senderPackage);
    }
    if ((changeTypes == SPEECH_STATE_SPEAKING_START)
        || (changeTypes == SPEECH_STATE_LISTENING_START)) {
      lastSpeechUptimeMillisec = SystemClock.uptimeMillis();
      packageToLastState.put(
          senderPackage, new StateTimeStamp(changeTypes, lastSpeechUptimeMillisec));
    }
  }

  public boolean isSpeaking() {
    return isStateValid(SPEECH_STATE_SPEAKING_START);
  }

  public boolean isListening() {
    return isStateValid(SPEECH_STATE_LISTENING_START);
  }

  private boolean isAnyStateValid() {
    long currentSpeechMillisec;
    for (Map.Entry<String, StateTimeStamp> s : packageToLastState.entrySet()) {
      currentSpeechMillisec = SystemClock.uptimeMillis() - s.getValue().getTimestamp();
      if ((currentSpeechMillisec < MAX_CHECK_STATE_MILLISEC)) {
        return true;
      }
    }
    return false;
  }

  private boolean isStateValid(@ReadSpeechState int state) {
    long currentTimeGapMillisec;
    for (Map.Entry<String, StateTimeStamp> s : packageToLastState.entrySet()) {
      currentTimeGapMillisec = SystemClock.uptimeMillis() - s.getValue().getTimestamp();
      if ((currentTimeGapMillisec < MAX_CHECK_STATE_MILLISEC)
          && (s.getValue().getState() == state)) {
        return true;
      }
    }

    if (!packageToLastState.isEmpty() && !isAnyStateValid()) {
      packageToLastState.clear();
    }

    return false;
  }

  private static class StateTimeStamp {

    @ReadSpeechState private final int state;
    private final long uptimeMillisec;

    StateTimeStamp(@ReadSpeechState int state, long uptimeMillisec) {
      this.state = state;
      this.uptimeMillisec = uptimeMillisec;
    }

    int getState() {
      return state;
    }

    long getTimestamp() {
      return uptimeMillisec;
    }
  }
}
