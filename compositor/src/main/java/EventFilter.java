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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.compositor.TextEventInterpreter.SelectionStateReader;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Determines whether events should be passed on to the compositor. Also interprets events into more
 * specific event types, and extracts data from events.
 */
public class EventFilter {

  private static final String TAG = "EventFilter";

  /** Used to query status of media and microphone actions. */
  public interface VoiceActionDelegate {
    boolean isVoiceRecognitionActive();

    boolean isMicrophoneActive();
  }

  ///////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Minimum delay between change and selection events. */
  private static final long TEXT_SELECTION_DELAY = 150;

  /** Minimum delay between change events without an intervening selection. */
  private static final long TEXT_CHANGED_DELAY = 150;

  /**
   * Minimum delay between selection and movement at granularity events that could reflect the same
   * cursor movement information.
   */
  private static final long CURSOR_MOVEMENT_EVENTS_DELAY = 150;

  /** The minimum interval (in milliseconds) between scroll feedback events. */
  private static final long MIN_SCROLL_INTERVAL = 250;

  /**
   * Keyboard echo preferences. These must be synchronized with @array/pref_keyboard_echo_values and
   *
   * @array/pref_keyboard_echo_entries in values/donottranslate.xml.
   */
  private static final int PREF_ECHO_ALWAYS = 0;

  private static final int PREF_ECHO_SOFTKEYS = 1;
  private static final int PREF_ECHO_NEVER = 2;

  private int keyboardEcho = PREF_ECHO_ALWAYS;

  ///////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Context context;
  private final Compositor compositor;
  @Nullable private final AudioPlaybackMonitor audioPlaybackMonitor;
  private VoiceActionDelegate voiceActionDelegate;

  private final TextEventHistory textEventHistory;
  private final TextEventInterpreter textEventInterpreter;
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
      @Nullable TextCursorManager textCursorManager,
      @Nullable SelectionStateReader selectionStateReader,
      InputModeManager inputModeManager,
      @Nullable EditTextActionHistory editTextActionHistory,
      GlobalVariables globalVariables) {
    this(
        compositor,
        context,
        textCursorManager,
        selectionStateReader,
        inputModeManager,
        editTextActionHistory,
        /* audioPlaybackMonitor= */ null,
        globalVariables);
  }

  public EventFilter(
      Compositor compositor,
      Context context,
      @Nullable TextCursorManager textCursorManager,
      @Nullable SelectionStateReader selectionStateReader,
      InputModeManager inputModeManager,
      @Nullable EditTextActionHistory editTextActionHistory,
      @Nullable AudioPlaybackMonitor audioPlaybackMonitor,
      GlobalVariables globalVariables) {
    this.compositor = compositor;
    this.context = context;
    this.textEventHistory = new TextEventHistory(editTextActionHistory);
    this.textEventInterpreter =
        new TextEventInterpreter(
            context,
            textCursorManager,
            selectionStateReader,
            inputModeManager,
            textEventHistory,
            globalVariables);
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

  public void setKeyboardEcho(int value) {
    keyboardEcho = value;
  }

  public void sendEvent(AccessibilityEvent event, @Nullable EventId eventId) {
    // Update persistent state.
    globalVariables.updateStateFromEvent(event);

    // Interpret event more specifically, and extract data from event.
    // TODO: Run more event interpreters, like WindowEventInterpreter.
    // TODO: Move interpretation forward in the pipeline, to AccessibilityEventProcessor.
    EventInterpretation eventInterpreted = new EventInterpretation(event.getEventType());
    TextEventInterpretation textEventInterpreted = textEventInterpreter.interpret(event);
    if (textEventInterpreted != null) {
      eventInterpreted.setEvent(textEventInterpreted.getEvent());
      eventInterpreted.setTextEventInterpretation(textEventInterpreted);
      eventInterpreted.setPackageName(event.getPackageName());
    }

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
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        {
          // Update cursor position when an empty edit text is focused. TalkBack will not receive
          // the initial {@link AccessibilityEvent#TYPE_VIEW_TEXT_SELECTION_CHANGED} event when an
          // empty edit text is focused, in which case we need to manually update the index.
          AccessibilityNodeInfoCompat source =
              AccessibilityNodeInfoUtils.toCompat(event.getSource());
          if ((source != null)
              && AccessibilityNodeInfoUtils.isEmptyEditTextRegardlessOfHint(source)) {
            textEventHistory.setLastFromIndex(0);
            textEventHistory.setLastToIndex(0);
            textEventHistory.setLastNode(AccessibilityNodeInfoUtils.obtain(source.unwrap()));
          }
          AccessibilityNodeInfoUtils.recycleNodes(source);
          // fall through
        }
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
        {
          AccessibilityNodeInfo source = event.getSource();
          if (source != null) {
            source.recycle();
            return;
          }
        }
        break;

        // Event notification
      case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
        {
          // . If the user is touching on screen, skip event.
          // For toast events, the notification parcel is null. (Use event text instead.)
          Notification notification = AccessibilityEventUtils.extractNotification(event);
          if ((notification != null) && isUserTouchingOnScreen) {
            return;
          }
          if (voiceActionDelegate.isVoiceRecognitionActive()
              && (Role.getSourceRole(event) == Role.ROLE_TOAST)) {
            LogUtils.d(TAG, "Do not announce the toast: Voice recognition is active.");
            return;
          }
        }
        break;

        // Text input change
      case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
      case Compositor.EVENT_TYPE_INPUT_TEXT_CLEAR:
      case Compositor.EVENT_TYPE_INPUT_TEXT_REMOVE:
      case Compositor.EVENT_TYPE_INPUT_TEXT_ADD:
      case Compositor.EVENT_TYPE_INPUT_TEXT_REPLACE:
      case Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD:
      case Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE:
      case Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE:
        {
          if (dropTextChangeEvent(event)) {
            return;
          }
          // Update text change history.
          textEventHistory.setTextChangesAwaitingSelection(1);
          textEventHistory.setLastTextChangeTime(event.getEventTime());
          textEventHistory.setLastTextChangePackageName(event.getPackageName());

          if (!shouldEchoKeyboard(eventInterpreted.getEvent())) {
            return;
          }
          if ((voiceActionDelegate != null) && voiceActionDelegate.isVoiceRecognitionActive()) {
            LogUtils.d(TAG, "Drop TYPE_VIEW_TEXT_CHANGED event: Voice recognition is active.");
            return;
          }
        }
        break;

      case Compositor.EVENT_TYPE_INPUT_CHANGE_INVALID:
        return;

        // Text input selection change
      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
      case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_CUT:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_PASTE:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_SELECT_ALL:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD:
      case Compositor.EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION:
        {
          if (shouldSkipCursorMovementEvent(event) || shouldDropTextSelectionEvent(event)) {
            return;
          }
          // Update text selection history.
          textEventHistory.setLastKeptTextSelection(event);
          if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            textEventHistory.setLastFromIndex(event.getFromIndex());
            textEventHistory.setLastToIndex(event.getToIndex());
          }
          if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
              && (voiceActionDelegate != null)
              && voiceActionDelegate.isVoiceRecognitionActive()) {
            LogUtils.d(
                TAG, "Drop TYPE_VIEW_TEXT_SELECTION_CHANGED event: Voice recognition is active.");
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
      default:
        /* Do Nothing */
    }

    compositor.handleEvent(event, eventId, eventInterpreted);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for filtering text input events

  private boolean dropTextChangeEvent(AccessibilityEvent event) {
    // Drop text change event if we're still waiting for a select event and
    // the change occurred too soon after the previous change.
    final long eventTime = event.getEventTime();
    if (textEventHistory.getTextChangesAwaitingSelection() > 0) {

      // If the state is still consistent, update the count and drop
      // the event, except when running on locales that don't support
      // text replacement due to character combination complexity.
      final boolean hasDelayElapsed =
          ((eventTime - textEventHistory.getLastTextChangeTime()) >= TEXT_CHANGED_DELAY);
      final boolean hasPackageChanged =
          !TextUtils.equals(
              event.getPackageName(), textEventHistory.getLastTextChangePackageName());
      boolean canReplace = context.getResources().getBoolean(R.bool.supports_text_replacement);
      if (!hasDelayElapsed && !hasPackageChanged && canReplace) {
        textEventHistory.incrementTextChangesAwaitingSelection(1);
        textEventHistory.setLastTextChangeTime(eventTime);
        return true;
      }

      // The state became inconsistent, so reset the counter.
      textEventHistory.setTextChangesAwaitingSelection(0);
    }

    return false;
  }

  private boolean shouldEchoKeyboard(int changeType) {
    // Always echo text removal events.
    if (changeType == Compositor.EVENT_TYPE_INPUT_TEXT_REMOVE) {
      return true;
    }

    final Resources res = context.getResources();

    switch (keyboardEcho) {
      case PREF_ECHO_ALWAYS:
        return true;
      case PREF_ECHO_SOFTKEYS:
        final Configuration config = res.getConfiguration();
        return (config.keyboard == Configuration.KEYBOARD_NOKEYS)
            || (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);
      case PREF_ECHO_NEVER:
        return false;
      default:
        LogUtils.e(TAG, "Invalid keyboard echo preference value: %d", keyboardEcho);
        return false;
    }
  }

  private boolean shouldSkipCursorMovementEvent(AccessibilityEvent event) {
    AccessibilityEvent lastKeptTextSelection = textEventHistory.getLastKeptTextSelection();
    if (lastKeptTextSelection == null) {
      return false;
    }

    // If event is at least X later than previous event, then keep it.
    if (event.getEventTime() - lastKeptTextSelection.getEventTime()
        > CURSOR_MOVEMENT_EVENTS_DELAY) {
      textEventHistory.setLastKeptTextSelection(null);
      return false;
    }

    // If event has the same type as previous, it is from a different action, so keep it.
    if (event.getEventType() == lastKeptTextSelection.getEventType()) {
      return false;
    }

    // If text-selection-change is followed by text-move-with-granularity, skip movement.
    if (lastKeptTextSelection.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        && event.getEventType()
            == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {
      return true;
    }

    return false;
  }

  private boolean shouldDropTextSelectionEvent(AccessibilityEvent event) {
    // Keep all events other than text-selection.
    final int eventType = event.getEventType();
    if (eventType != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
      return false;
    }

    // Drop selected events until we've matched the number of changed
    // events. This prevents TalkBack from speaking automatic cursor
    // movement events that result from typing.
    if (textEventHistory.getTextChangesAwaitingSelection() > 0) {
      final boolean hasDelayElapsed =
          ((event.getEventTime() - textEventHistory.getLastTextChangeTime())
              >= TEXT_SELECTION_DELAY);
      final boolean hasPackageChanged =
          !TextUtils.equals(
              event.getPackageName(), textEventHistory.getLastTextChangePackageName());

      // If the state is still consistent, update the count and drop the event.
      if (!hasDelayElapsed && !hasPackageChanged) {
        textEventHistory.incrementTextChangesAwaitingSelection(-1);
        textEventHistory.setLastFromIndex(event.getFromIndex());
        textEventHistory.setLastToIndex(event.getToIndex());
        textEventHistory.setLastNode(event.getSource());
        return true;
      }

      // The state became inconsistent, so reset the counter.
      textEventHistory.setTextChangesAwaitingSelection(0);
    }

    // Drop selection events from views that don't have input focus.
    final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
    final AccessibilityNodeInfoCompat source = record.getSource();
    boolean isFocused = source != null && source.isFocused();
    AccessibilityNodeInfoUtils.recycleNodes(source);
    if (!isFocused) {
      LogUtils.v(TAG, "Dropped text-selection event from non-focused field");
      return true;
    }

    return false;
  }

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

    boolean valid =
        source.isScrollable() || event.getMaxScrollX() != -1 || event.getMaxScrollY() != -1;
    source.recycle();
    return valid;
  }
}
