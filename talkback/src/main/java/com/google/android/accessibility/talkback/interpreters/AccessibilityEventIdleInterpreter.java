/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.accessibility.talkback.interpreters;

import static com.google.android.accessibility.talkback.Interpretation.ID.Value.ACCESSIBILITY_EVENT_IDLE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import com.google.android.accessibility.talkback.Interpretation.ID;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor.AccessibilityEventIdleListener;

/** Interprets accessibility event idle state, and sends interpretations to the pipeline. */
public class AccessibilityEventIdleInterpreter implements AccessibilityEventIdleListener {
  private InterpretationReceiver pipeline;

  public void setPipeline(InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public void onIdle() {
    pipeline.input(EVENT_ID_UNTRACKED, new ID(ACCESSIBILITY_EVENT_IDLE));
  }
}
