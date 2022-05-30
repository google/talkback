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

import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Monitors(BatteryMonitor batteryMonitor, CallStateMonitor callMonitor) {
    this.batteryMonitor = batteryMonitor;
    this.callMonitor = callMonitor;
  }

  public void setPipelineInterpretationReceiver(@NonNull InterpretationReceiver pipeline) {
    batteryMonitor.setPipeline(pipeline);
  }
}
