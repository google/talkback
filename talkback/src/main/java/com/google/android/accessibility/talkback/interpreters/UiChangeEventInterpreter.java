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

import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Interpretation.UiChange.UiChangeType;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.WindowEventHandler;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Interprets UI change event, and sends interpretations to the pipeline. */
public class UiChangeEventInterpreter implements WindowEventHandler, AccessibilityEventListener {

  private InterpretationReceiver pipeline;

  public void setPipeline(InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public void handle(EventInterpretation interpretation, @Nullable EventId eventId) {
    int eventType = interpretation.getEventType();
    if (interpretation.getMainWindowsChanged()
        || (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        || (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
      pipeline.input(eventId, Interpretation.UiChange.createWholeScreenUiChange());
    } else {
      if ((eventType
              & (AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                  | AccessibilityEvent.TYPE_VIEW_SCROLLED))
          == 0) {
        return;
      }

      Rect sourceNodeBounds = interpretation.getSourceBoundsInScreen();
      if (sourceNodeBounds != null) {
        pipeline.input(
            eventId,
            Interpretation.UiChange.createPartialScreenUiChange(
                sourceNodeBounds,
                (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                    ? UiChangeType.VIEW_SCROLLED
                    : UiChangeType.WINDOW_CONTENT_CHANGED));
      }
    }
  }

  @Override
  public int getEventTypes() {
    // This is the only event type of interest to onAccessibilityEvent
    return AccessibilityEvent.TYPE_VIEW_CLICKED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    // Clicking a view may result in content change inside that view
    AccessibilityNodeInfo sourceNode = event.getSource();
    if (sourceNode != null) {
      Rect sourceNodeBounds = new Rect();
      sourceNode.getBoundsInScreen(sourceNodeBounds);
      pipeline.input(
          eventId,
          Interpretation.UiChange.createPartialScreenUiChange(
              sourceNodeBounds, UiChangeType.VIEW_CLICKED));
    }
  }
}
