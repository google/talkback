/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import android.app.Notification;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.input.TextEventInterpreter;
import com.google.android.accessibility.utils.monitor.TouchMonitor;
import com.google.android.accessibility.utils.monitor.VoiceActionDelegate;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Determines whether events should be passed on to the compositor. Also interprets events into more
 * specific event types, and extracts data from events.
 */
public class EventFilter {

  private static final String TAG = "EventFilter";

  ///////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Compositor compositor;
  private VoiceActionDelegate voiceActionDelegate;

  private AccessibilityFocusEventInterpreter accessibilityFocusEventInterpreter;
  private final GlobalVariables globalVariables;

  private final @NonNull TouchMonitor touchMonitor;

  // /////////////////////////////////////////////////////////////////////////////////
  // Construction

  public EventFilter(
      Compositor compositor,
      @NonNull TouchMonitor touchMonitor,
      GlobalVariables globalVariables) {
    this.compositor = compositor;
    this.touchMonitor = touchMonitor;
    this.globalVariables = globalVariables;
  }

  ///////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void setVoiceActionDelegate(VoiceActionDelegate delegate) {
    voiceActionDelegate = delegate;
  }

  public void setAccessibilityFocusEventInterpreter(
      AccessibilityFocusEventInterpreter interpreter) {
    accessibilityFocusEventInterpreter = interpreter;
  }

  public void sendEvent(AccessibilityEvent event, @Nullable EventId eventId) {
    // Update persistent state.
    globalVariables.updateStateFromEvent(event);

    // Interpret event more specifically, and extract data from event.
    EventInterpretation eventInterpreted =
        new EventInterpretation(Compositor.toCompositorEvent(event));
    AccessibilityFocusEventInterpretation a11yFocusEventInterpreted =
        (accessibilityFocusEventInterpreter == null)
            ? null
            : accessibilityFocusEventInterpreter.interpret(event);
    if (a11yFocusEventInterpreted != null) {
      eventInterpreted.setEvent(a11yFocusEventInterpreted.getEvent());
      eventInterpreted.setAccessibilityFocusInterpretation(a11yFocusEventInterpreted);
    }

    eventInterpreted.setReadOnly();

    int eventType = eventInterpreted.getEvent();
    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      // Drop accessibility-focus events based on EventState.
      // TODO: Remove this when focus management is done.
      {
        if (globalVariables.resettingSkipFocusProcessing()) {
          return;
        }
        if ((a11yFocusEventInterpreted != null)
            && a11yFocusEventInterpreted.getShouldMuteFeedback()) {
          return;
        }
      }
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
      // For focus fallback events, drop events with a source node.
      if (AccessibilityEventUtils.sourceCompat(event) != null) {
        return;
      }
    } else if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
      // Event notification
      // REFERTO. If the user is touching on screen, skip event.
      // For toast events, the notification parcel is null. (Use event text instead.)
      Notification notification = AccessibilityEventUtils.extractNotification(event);
      if ((notification != null) && touchMonitor.isUserTouchingScreen()) {
        return;
      }
      if ((voiceActionDelegate != null)
          && voiceActionDelegate.isVoiceRecognitionActive()
          && (Role.getSourceRole(event) == Role.ROLE_TOAST)) {
        LogUtils.d(TAG, "Do not announce the toast: Voice recognition is active.");
        return;
      }
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {
      return; // Text-events only travel through TextEventInterpreter.
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
      LogUtils.d(
          TAG,
          "Do not handle TYPE_VIEW_SELECTED by ProcessEventQueue. "
              + "Handled in SelectionEventInterpreter.");
      return;
    } else {
      // Let event go to compositor.
    }

    compositor.handleEvent(event, eventId, eventInterpreted);
  }

  /** Passes text-event-interpretation to compositor. */
  public void accept(TextEventInterpreter.Interpretation textEventInterpreted) {
    AccessibilityEvent event = textEventInterpreted.event;
    globalVariables.setLastTextEditIsPassword(event.isPassword());

    int eventType =
        (textEventInterpreted.interpretation == null)
            ? Compositor.toCompositorEvent(event)
            : Compositor.toCompositorEvent(textEventInterpreted.interpretation.getEvent());
    EventInterpretation eventInterpreted = new EventInterpretation(eventType);
    eventInterpreted.setTextEventInterpretation(textEventInterpreted.interpretation);
    eventInterpreted.setPackageName(event.getPackageName());
    eventInterpreted.setReadOnly();

    compositor.handleEvent(event, textEventInterpreted.eventId, eventInterpreted);
  }

}
