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

import static com.google.android.accessibility.talkback.Interpretation.ID.Value.SUBTREE_CHANGED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.os.Handler;
import android.os.Message;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Interpretation.ID;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;

/** Interprets subtree-change event, and sends interpretations to the pipeline. */
public class SubtreeChangeEventInterpreter implements AccessibilityEventListener {
  // The delay of interpreting subtree changed events. System may send a series of subtree changed
  // events in a short time, so defer the interpretation until the screen is under a stabler state.
  // The value should be longer than 100ms because system might delay 100ms to aggregate multiple
  // events.
  private static final int SUBTREE_CHANGED_DELAY_MS = 150;

  private final SubtreeChangedHandler subtreeChangedHandler;
  private final ScreenStateMonitor.State screenState;

  public SubtreeChangeEventInterpreter(ScreenStateMonitor.State state) {
    subtreeChangedHandler = new SubtreeChangedHandler();
    screenState = state;
  }

  public void setPipeline(InterpretationReceiver pipeline) {
    subtreeChangedHandler.setPipeline(pipeline);
  }

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if ((event.getEventType() & getEventTypes()) == 0) {
      return;
    }

    if ((event.getContentChangeTypes() & AccessibilityEventCompat.CONTENT_CHANGE_TYPE_SUBTREE)
        == 0) {
      return;
    }

    // Request a focus check after receiving subtree changes. Start with clearing the action queue
    // to ensure only one action will be performed during a series of subtree changes.
    subtreeChangedHandler.removeMessages(SubtreeChangedHandler.MSG_CHECK_ACCESSIBILITY_FOCUS);

    // Do nothing if the window is unstable, since the focus will be requested by the window event
    // when the screen turns to a stable state.
    if (!screenState.areMainWindowsStable()) {
      return;
    }

    Message msg =
        subtreeChangedHandler.obtainMessage(
            SubtreeChangedHandler.MSG_CHECK_ACCESSIBILITY_FOCUS, eventId);
    subtreeChangedHandler.sendMessageDelayed(msg, SUBTREE_CHANGED_DELAY_MS);
  }

  /** A handler to delay the interpretation of subtree change event. */
  private static class SubtreeChangedHandler extends Handler {
    static final int MSG_CHECK_ACCESSIBILITY_FOCUS = 1;
    InterpretationReceiver pipeline;

    @Override
    public void handleMessage(Message msg) {
      if (pipeline == null) {
        return;
      }

      if (msg.what == MSG_CHECK_ACCESSIBILITY_FOCUS) {
        EventId eventId = EVENT_ID_UNTRACKED;
        if (msg.obj != null && msg.obj instanceof EventId) {
          eventId = (EventId) msg.obj;
        }
        pipeline.input(eventId, new ID(SUBTREE_CHANGED));
      }
    }

    void setPipeline(InterpretationReceiver pipeline) {
      this.pipeline = pipeline;
    }
  }
}
