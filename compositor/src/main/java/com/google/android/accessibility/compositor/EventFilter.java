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

package com.google.android.accessibility.compositor;

import android.app.Notification;
import android.content.Context;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.input.TextEventInterpreter;
import com.google.android.accessibility.utils.monitor.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.monitor.VoiceActionDelegate;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Determines whether events should be passed on to the compositor. Also interprets events into more
 * specific event types, and extracts data from events.
 */
public class EventFilter {

  private static final String TAG = "EventFilter";

  ///////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** The minimum interval (in milliseconds) between scroll feedback events. */
  private static final long MIN_SCROLL_INTERVAL = 250;

  ///////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Compositor compositor;
  private final @Nullable AudioPlaybackMonitor audioPlaybackMonitor;
  private VoiceActionDelegate voiceActionDelegate;

  private AccessibilityFocusEventInterpreter accessibilityFocusEventInterpreter;
  private final GlobalVariables globalVariables;

  /**
   * Time in milliseconds of last non-dropped scroll event, based on system uptime. A negative value
   * indicates no previous scroll events have occurred.
   */
  private long lastScrollEventTimeInMillis = -1;

  private boolean isUserTouchingOnScreen = false;


  // /////////////////////////////////////////////////////////////////////////////////
  // Construction

  public EventFilter(
      Compositor compositor,
      Context context,
      @Nullable AudioPlaybackMonitor audioPlaybackMonitor,
      GlobalVariables globalVariables) {
    this.compositor = compositor;
    this.audioPlaybackMonitor = audioPlaybackMonitor;
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
    EventInterpretation eventInterpreted = new EventInterpretation(event.getEventType());
    AccessibilityFocusEventInterpretation a11yFocusEventInterpreted =
        (accessibilityFocusEventInterpreter == null)
            ? null
            : accessibilityFocusEventInterpreter.interpret(event);
    if (a11yFocusEventInterpreted != null) {
      eventInterpreted.setEvent(a11yFocusEventInterpreted.getEvent());
      eventInterpreted.setAccessibilityFocusInterpretation(a11yFocusEventInterpreted);
    }

    eventInterpreted.setReadOnly();

    switch (eventInterpreted.getEvent()) {
        // Drop accessibility-focus events based on EventState.
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        // TODO: Remove this when focus management is done.
        {
          if (globalVariables.checkAndClearRecentFlag(
                  GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE)
              || globalVariables.checkAndClearRecentFlag(
                  GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL)
              || globalVariables.checkAndClearRecentFlag(
                  GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_IME_CLOSED)) {
            return;
          }
          if ((a11yFocusEventInterpreted != null)
              && a11yFocusEventInterpreted.getShouldMuteFeedback()) {
            return;
          }
        }
        break;

        // For focus fallback events, drop events with a source node.
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
        {
          if (AccessibilityEventUtils.sourceCompat(event) != null) {
            return;
          }
        }
        break;

        // Event notification
      case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
        {
          // REFERTO. If the user is touching on screen, skip event.
          // For toast events, the notification parcel is null. (Use event text instead.)
          Notification notification = AccessibilityEventUtils.extractNotification(event);
          if ((notification != null) && isUserTouchingOnScreen) {
            return;
          }
          if ((voiceActionDelegate != null)
              && voiceActionDelegate.isVoiceRecognitionActive()
              && (Role.getSourceRole(event) == Role.ROLE_TOAST)) {
            LogUtils.d(TAG, "Do not announce the toast: Voice recognition is active.");
            return;
          }
        }
        break;

      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        {
          final long currentTimeInMillis = SystemClock.uptimeMillis();
          if (shouldDropScrollEvent(event, currentTimeInMillis)) {
            return;
          }
          lastScrollEventTimeInMillis = currentTimeInMillis;
        }
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        // TODO: When double tapping on screen, touch interaction start/end events might
        // be sent in reversed order. We might need a more reliable way to detect touch start/end
        // actions.
        isUserTouchingOnScreen = true;
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        isUserTouchingOnScreen = false;
        break;

      case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
      case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
        return; // Text-events only travel through TextEventInterpreter.

      default:
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
            ? event.getEventType()
            : Compositor.toCompositorEvent(textEventInterpreted.interpretation.getEvent());
    EventInterpretation eventInterpreted = new EventInterpretation(eventType);
    eventInterpreted.setTextEventInterpretation(textEventInterpreted.interpretation);
    eventInterpreted.setPackageName(event.getPackageName());
    eventInterpreted.setReadOnly();

    compositor.handleEvent(event, textEventInterpreted.eventId, eventInterpreted);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for filtering scroll events

  /**
   * Whether a scroll event should not be sent to the Compositor.
   *
   * <p>TODO: Consider adding a flag to ScrollEventInterpretation for not playing
   * earcons if dropping the events here causes issues or if more flexibility is needed.
   */
  private boolean shouldDropScrollEvent(AccessibilityEvent event, long currentTimeInMillis) {
    // Dropping events may cause weird rhythms when scrolling, e.g. if we're getting an event every
    // 300ms and we reject at the 250ms mark.
    return lastScrollEventWasRecent(currentTimeInMillis)
        || isAutomaticMediaScrollEvent()
        || !isValidScrollEvent(event);
  }

  private boolean lastScrollEventWasRecent(long currentTimeInMillis) {
    // Return false if there are no previous scroll events.
    if (lastScrollEventTimeInMillis < 0) {
      return false;
    }
    long diff = currentTimeInMillis - lastScrollEventTimeInMillis;
    return diff < MIN_SCROLL_INTERVAL;
  }

  /**
   * Whether the scroll event was probably generated automatically by playing media.
   *
   * <p>Particularly for videos with captions enabled, new scroll events fire every second or so.
   * Earcons should not play for these events because they don't provide useful information and are
   * distracting.
   */
  private boolean isAutomaticMediaScrollEvent() {
    // TODO: Investigate a more accurate way to determine whether a scroll event was
    // user initiated or initiated by playing media.
    return audioPlaybackMonitor != null
        && audioPlaybackMonitor.isPlaybackSourceActive(
            AudioPlaybackMonitor.PlaybackSource.USAGE_MEDIA)
        && !isUserTouchingOnScreen;
  }

  private boolean isValidScrollEvent(AccessibilityEvent event) {
    AccessibilityNodeInfo source = event.getSource();
    if (source == null) {
      return true; // Cannot check source validity, so assume that it's scrollable.
    }
    return source.isScrollable() || event.getMaxScrollX() != -1 || event.getMaxScrollY() != -1;
  }
}
