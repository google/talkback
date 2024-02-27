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

import android.os.Looper;
import android.os.Message;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import com.google.android.accessibility.talkback.Interpretation.ID;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.monitor.DisplayMonitor;
import com.google.android.accessibility.utils.monitor.DisplayMonitor.DisplayStateChangedListener;

/** Interprets subtree-change event, and sends interpretations to the pipeline. */
public class SubtreeChangeEventInterpreter
    implements AccessibilityEventListener, DisplayStateChangedListener {
  /**
   * The delay of interpreting subtree changed events. System may send a series of subtree changed
   * events in a short time, so defer the interpretation until the screen is under a stabler state.
   * The value should be longer than ViewConfiguration#getSendRecurringAccessibilityEventsInterval
   * (100ms) because system might delay 100ms to aggregate multiple events.
   */
  static final int SHORT_SUBTREE_CHANGED_DELAY_MS = 150;
  /**
   * The long delay of interpreting subtree changed events. For wear devices, they have multiple
   * attempts for auto scrolling. Ideally, CONTENT_CHANGE_TYPE_SUBTREE and TYPE_VIEW_SCROLLED are
   * sent at most once in every ViewConfiguration#getSendRecurringAccessibilityEventsInterval
   * (100ms). The reason why we extend delay time is that when onAutoScrolled is called, it should
   * spend more times (~350ms) on calculating whether it needs to perform auto-scrolling one more
   * time and performing scrolling action. It is an experimental value and feel free to change it.
   */
  static final int LONG_SUBTREE_CHANGED_DELAY_MS = 350;

  private int subtreeChangedDelayMs = SHORT_SUBTREE_CHANGED_DELAY_MS;

  private final SubtreeChangedHandler subtreeChangedHandler;
  private final ScreenStateMonitor.State screenState;
  // Default is true since we assume when it receive a11y event without callback invocation, the
  // default display should be on.
  private boolean defaultDisplayOn = true;
  private final DisplayMonitor displayMonitor;

  /** Event types that are handled by SubtreeChangeEventInterpreter. */
  private final int maskEventType;

  private final FormFactorUtils formFactorUtils;

  public SubtreeChangeEventInterpreter(
      ScreenStateMonitor.State state, DisplayMonitor displayMonitor) {
    subtreeChangedHandler = new SubtreeChangedHandler(this);
    screenState = state;
    this.displayMonitor = displayMonitor;

    formFactorUtils = FormFactorUtils.getInstance();

    maskEventType =
        formFactorUtils.isAndroidWear()
            ? AccessibilityEvent.TYPE_VIEW_SCROLLED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            : AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
  }

  public void onResumeInfrastructure() {
    displayMonitor.addDisplayStateChangedListener(this);
  }

  public void onSuspendInfrastructure() {
    displayMonitor.removeDisplayStateChangedListener(this);
  }

  public void setPipeline(InterpretationReceiver pipeline) {
    subtreeChangedHandler.setPipeline(pipeline);
  }

  @Override
  public int getEventTypes() {
    return maskEventType;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if ((event.getEventType() & getEventTypes()) == 0) {
      return;
    }

    if ((event.getEventType() & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) != 0
        && (event.getContentChangeTypes() & AccessibilityEventCompat.CONTENT_CHANGE_TYPE_SUBTREE)
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

    // Do nothing if the display is off.
    if (!defaultDisplayOn) {
      return;
    }

    extendSubtreeChangedDelayMsIfNeeded(event);

    Message msg =
        subtreeChangedHandler.obtainMessage(
            SubtreeChangedHandler.MSG_CHECK_ACCESSIBILITY_FOCUS, eventId);
    subtreeChangedHandler.sendMessageDelayed(msg, subtreeChangedDelayMs);
  }

  /**
   * Extends the message delayed time to {@link #LONG_SUBTREE_CHANGED_DELAY_MS} for a wear device's
   * {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} event to check whether their is a subsequent
   * scrolling action. Besides, if the delay time is extended, we don't change the delay time while
   * receiving other events types.
   */
  private void extendSubtreeChangedDelayMsIfNeeded(AccessibilityEvent event) {
    if (formFactorUtils.isAndroidWear()
        && (event.getEventType() & AccessibilityEvent.TYPE_VIEW_SCROLLED) != 0) {
      subtreeChangedDelayMs = LONG_SUBTREE_CHANGED_DELAY_MS;
    }
  }

  /** Resets message delayed time to the default {@link #SHORT_SUBTREE_CHANGED_DELAY_MS}. */
  private void resetSubtreeChangedDelayMs() {
    subtreeChangedDelayMs = SHORT_SUBTREE_CHANGED_DELAY_MS;
  }

  @Override
  public void onDisplayStateChanged(boolean displayOn) {
    defaultDisplayOn = displayOn;
    if (!defaultDisplayOn) {
      // If the display is off, we don't need to do it anymore.
      subtreeChangedHandler.removeMessages(SubtreeChangedHandler.MSG_CHECK_ACCESSIBILITY_FOCUS);
    }
  }

  /** A handler to delay the interpretation of subtree change event. */
  private static class SubtreeChangedHandler
      extends WeakReferenceHandler<SubtreeChangeEventInterpreter> {
    static final int MSG_CHECK_ACCESSIBILITY_FOCUS = 1;
    InterpretationReceiver pipeline;

    SubtreeChangedHandler(SubtreeChangeEventInterpreter interpreter) {
      super(interpreter, Looper.myLooper());
    }

    @Override
    public void handleMessage(Message msg, SubtreeChangeEventInterpreter parent) {
      if (pipeline == null) {
        return;
      }

      if (msg.what == MSG_CHECK_ACCESSIBILITY_FOCUS) {
        EventId eventId = EVENT_ID_UNTRACKED;
        if (msg.obj != null && msg.obj instanceof EventId) {
          eventId = (EventId) msg.obj;
        }
        parent.resetSubtreeChangedDelayMs();
        pipeline.input(eventId, new ID(SUBTREE_CHANGED));
      }
    }

    void setPipeline(InterpretationReceiver pipeline) {
      this.pipeline = pipeline;
    }
  }
}
