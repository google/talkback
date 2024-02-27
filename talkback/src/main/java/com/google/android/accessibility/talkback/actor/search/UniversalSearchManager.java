/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback.actor.search;

import static com.google.android.accessibility.talkback.Feedback.UniversalSearch.Action.CANCEL_SEARCH;
import static com.google.android.accessibility.talkback.Feedback.UniversalSearch.Action.HANDLE_SCREEN_STATE;

import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.WindowEventInterpreter;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.EventInterpretation;

/** Handles keyword search of the nodes on the screen. REFERTO */
public class UniversalSearchManager implements WindowEventInterpreter.WindowEventHandler {
  private static final String TAG = "ScreenSearch";

  private final Pipeline.FeedbackReturner pipeline;

  public UniversalSearchManager(
      Pipeline.FeedbackReturner pipeline,
      RingerModeAndScreenMonitor ringerModeAndScreenMonitor,
      WindowEventInterpreter windowInterpreter) {
    this.pipeline = pipeline;

    // Registers screen state changed listener.
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.addScreenChangedListener(
          (isInteractive, eventId) -> {
            // Cancels search when screen is off (not interactive).
            if (!isInteractive) {
              pipeline.returnFeedback(eventId, Feedback.universalSearch(CANCEL_SEARCH));
            }
          });
    }

    if (windowInterpreter != null) {
      windowInterpreter.addListener(this);
    }
  }

  @Override
  public void handle(EventInterpretation interpretation, EventId eventId) {
    pipeline.returnFeedback(eventId, Feedback.universalSearch(HANDLE_SCREEN_STATE));
  }
}
