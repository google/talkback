/*
 * Copyright (C) 2021 Google Inc.
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

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.talkback.monitor.BatteryMonitor;
import com.google.android.accessibility.talkback.monitor.CallStateMonitor;
import com.google.android.accessibility.utils.monitor.CollectionState;
import com.google.android.accessibility.utils.monitor.SpeechStateMonitor;
import com.google.android.accessibility.utils.monitor.TouchMonitor;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A collection of system-monitors, used by Pipeline to generate event-interpretations, and to
 * provide monitor-state to feedback-mapper-policy.
 */
public class Monitors {

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Inner-classes

  /** Provides monitor-state to feedback-mapper-policy. */
  public interface State {
    boolean isPhoneCallActive();
  }

  public final State state =
      new State() {
        @Override
        public boolean isPhoneCallActive() {
          return callMonitor.isPhoneCallActive();
        }
      };

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private final BatteryMonitor batteryMonitor;
  private final CallStateMonitor callMonitor;
  private final @NonNull TouchMonitor touchMonitor;
  private final SpeechStateMonitor speechStateMonitor;
  private final CollectionState collectionState;

  private final int eventTypeMask; // Union of all monitor masks

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Monitors(
      BatteryMonitor batteryMonitor,
      CallStateMonitor callMonitor,
      @NonNull TouchMonitor touchMonitor,
      SpeechStateMonitor speechStateMonitor,
      CollectionState collectionState) {
    this.batteryMonitor = batteryMonitor;
    this.callMonitor = callMonitor;
    this.touchMonitor = touchMonitor;
    this.speechStateMonitor = speechStateMonitor;
    this.collectionState = collectionState;

    eventTypeMask =
        touchMonitor.getEventTypes()
            | speechStateMonitor.getEventTypes()
            | collectionState.getEventTypes();
  }

  public void setPipelineInterpretationReceiver(@NonNull InterpretationReceiver pipeline) {
    batteryMonitor.setPipeline(pipeline);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods
  public int getEventTypes() {
    return eventTypeMask;
  }

  public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
    collectionState.onAccessibilityEvent(event);
    touchMonitor.onAccessibilityEvent(event);
    speechStateMonitor.onAccessibilityEvent(event);
  }
}
