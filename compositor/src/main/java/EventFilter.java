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
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.TextCursorManager;

/**
 * Determines whether events should be passed on to the compositor. Also interprets events into more
 * specific event types, and extracts data from events.
 */
public class EventFilter {

  /** Used to query status of media and microphone actions. */
  public interface VoiceActionDelegate {
    boolean isVoiceRecognitionActive();
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

  private int mKeyboardEcho = PREF_ECHO_ALWAYS;

  ///////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Context mContext;
  private final Compositor mCompositor;
  private NotificationHistory mNotificationHistory = new NotificationHistory();
  private VoiceActionDelegate mVoiceActionDelegate;

  private final TextEventHistory mTextEventHistory;
  private final TextEventInterpreter mTextEventInterpreter;
  private final GlobalVariables mGlobalVariables;
  private long mLastScrollEventTime = -1;
  private boolean mIsUserTouchingOnScreen = false;

  // /////////////////////////////////////////////////////////////////////////////////
  // Construction

  public EventFilter(
      Compositor compositor,
      Context context,
      TextCursorManager textCursorManager,
      CursorController cursorController,
      InputModeManager inputModeManager,
      EditTextActionHistory editTextActionHistory,
      GlobalVariables globalVariables) {
    mCompositor = compositor;
    mContext = context;
    mTextEventHistory = new TextEventHistory(editTextActionHistory);
    mTextEventInterpreter =
        new TextEventInterpreter(
            context,
            textCursorManager,
            cursorController,
            inputModeManager,
            mTextEventHistory,
            globalVariables);
    mGlobalVariables = globalVariables;
  }

  ///////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void setVoiceActionDelegate(VoiceActionDelegate delegate) {
    mVoiceActionDelegate = delegate;
  }

  public void setKeyboardEcho(int value) {
    mKeyboardEcho = value;
  }

  public void sendEvent(AccessibilityEvent event, Performance.EventId eventId) {
    // Update persistent state.
    mGlobalVariables.updateStateFromEvent(event);

    // Interpret event more specifically, and extract data from event.
    // TODO: Run more event interpreters, like WindowEventInterpreter.
    // TODO: Move interpretation forward in the pipeline, to AccessibilityEventProcessor.
    EventInterpretation eventInterpreted = new EventInterpretation(event.getEventType());
    TextEventInterpretation textEventInterpreted = mTextEventInterpreter.interpret(event);
    if (textEventInterpreted != null) {
      eventInterpreted.setEvent(textEventInterpreted.getEvent());
      eventInterpreted.setTextEventInterpretation(textEventInterpreted);
    }
    eventInterpreted.setReadOnly();

    switch (eventInterpreted.getEvent()) {
        // Drop accessibility-focus events based on EventState.
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        {
          if (mGlobalVariables.checkAndClearRecentFlag(
                  GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE)
              || mGlobalVariables.checkAndClearRecentFlag(
                  GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL)
              || mGlobalVariables.checkAndClearRecentFlag(
                  GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_IME_CLOSED)) {
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
            mTextEventHistory.setLastFromIndex(0);
            mTextEventHistory.setLastToIndex(0);
            mTextEventHistory.setLastNode(AccessibilityNodeInfoUtils.obtain(source.unwrap()));
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
          // If notification is a duplicate of recent notifications, or if the user is touching on
          // screen, skip event.
          // For toast events, the notification parcel is null. (Use event text instead.)
          Notification notification = AccessibilityEventUtils.extractNotification(event);
          if (notification != null
              && (mIsUserTouchingOnScreen || mNotificationHistory.isRecent(notification))) {
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
          mTextEventHistory.setTextChangesAwaitingSelection(1);
          mTextEventHistory.setLastTextChangeTime(event.getEventTime());
          mTextEventHistory.setLastTextChangePackageName(event.getPackageName());

          if (!shouldEchoKeyboard(eventInterpreted.getEvent())) {
            return;
          }
          if ((mVoiceActionDelegate != null) && mVoiceActionDelegate.isVoiceRecognitionActive()) {
            LogUtils.log(
                this, Log.DEBUG, "Drop TYPE_VIEW_TEXT_CHANGED event: Voice recognition is active.");
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
          mTextEventHistory.setLastKeptTextSelection(event);
          if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            mTextEventHistory.setLastFromIndex(event.getFromIndex());
            mTextEventHistory.setLastToIndex(event.getToIndex());
          }
          if ((mVoiceActionDelegate != null) && mVoiceActionDelegate.isVoiceRecognitionActive()) {
            LogUtils.log(
                this,
                Log.DEBUG,
                "Drop TYPE_VIEW_TEXT_SELECTION_CHANGED event: Voice recognition is active.");
            return;
          }
        }
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        {
          final long currentTime = SystemClock.uptimeMillis();
          if (shouldDropScrollEvent(event, currentTime)) {
            return;
          }
          mLastScrollEventTime = currentTime;
        }
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        // TODO: When double tapping on screen, touch interaction start/end events might
        // be sent in reversed order. We might need a more reliable way to detect touch start/end
        // actions.
        mIsUserTouchingOnScreen = true;
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        mIsUserTouchingOnScreen = false;
        break;
      default:
        /* Do Nothing */
    }

    mCompositor.sendEvent(event, eventId, eventInterpreted);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for filtering text input events

  private boolean dropTextChangeEvent(AccessibilityEvent event) {
    // Drop text change event if we're still waiting for a select event and
    // the change occurred too soon after the previous change.
    final long eventTime = event.getEventTime();
    if (mTextEventHistory.getTextChangesAwaitingSelection() > 0) {

      // If the state is still consistent, update the count and drop
      // the event, except when running on locales that don't support
      // text replacement due to character combination complexity.
      final boolean hasDelayElapsed =
          ((eventTime - mTextEventHistory.getLastTextChangeTime()) >= TEXT_CHANGED_DELAY);
      final boolean hasPackageChanged =
          !TextUtils.equals(
              event.getPackageName(), mTextEventHistory.getLastTextChangePackageName());
      boolean canReplace = mContext.getResources().getBoolean(R.bool.supports_text_replacement);
      if (!hasDelayElapsed && !hasPackageChanged && canReplace) {
        mTextEventHistory.incrementTextChangesAwaitingSelection(1);
        mTextEventHistory.setLastTextChangeTime(eventTime);
        return true;
      }

      // The state became inconsistent, so reset the counter.
      mTextEventHistory.setTextChangesAwaitingSelection(0);
    }

    return false;
  }

  private boolean shouldEchoKeyboard(int changeType) {
    // Always echo text removal events.
    if (changeType == Compositor.EVENT_TYPE_INPUT_TEXT_REMOVE) {
      return true;
    }

    final Resources res = mContext.getResources();

    switch (mKeyboardEcho) {
      case PREF_ECHO_ALWAYS:
        return true;
      case PREF_ECHO_SOFTKEYS:
        final Configuration config = res.getConfiguration();
        return (config.keyboard == Configuration.KEYBOARD_NOKEYS)
            || (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);
      case PREF_ECHO_NEVER:
        return false;
      default:
        LogUtils.log(this, Log.ERROR, "Invalid keyboard echo preference value: %d", mKeyboardEcho);
        return false;
    }
  }

  private boolean shouldSkipCursorMovementEvent(AccessibilityEvent event) {
    if (mTextEventHistory.getLastKeptTextSelection() == null) {
      return false;
    }

    // If event is at least X later than previous event, then keep it.
    if (event.getEventTime() - mTextEventHistory.getLastKeptTextSelection().getEventTime()
        > CURSOR_MOVEMENT_EVENTS_DELAY) {
      mTextEventHistory.setLastKeptTextSelection(null);
      return false;
    }

    // If event has the same type as previous, it is from a different action, so keep it.
    if (event.getEventType() == mTextEventHistory.getLastKeptTextSelection().getEventType()) {
      return false;
    }

    // If text-selection-change is followed by text-move-with-granularity, skip movement.
    if (mTextEventHistory.getLastKeptTextSelection().getEventType()
            == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
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
    if (mTextEventHistory.getTextChangesAwaitingSelection() > 0) {
      final boolean hasDelayElapsed =
          ((event.getEventTime() - mTextEventHistory.getLastTextChangeTime())
              >= TEXT_SELECTION_DELAY);
      final boolean hasPackageChanged =
          !TextUtils.equals(
              event.getPackageName(), mTextEventHistory.getLastTextChangePackageName());

      // If the state is still consistent, update the count and drop the event.
      if (!hasDelayElapsed && !hasPackageChanged) {
        mTextEventHistory.incrementTextChangesAwaitingSelection(-1);
        mTextEventHistory.setLastFromIndex(event.getFromIndex());
        mTextEventHistory.setLastToIndex(event.getToIndex());
        mTextEventHistory.setLastNode(event.getSource());
        return true;
      }

      // The state became inconsistent, so reset the counter.
      mTextEventHistory.setTextChangesAwaitingSelection(0);
    }

    // Drop selection events from views that don't have input focus.
    final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
    final AccessibilityNodeInfoCompat source = record.getSource();
    boolean isFocused = source != null && source.isFocused();
    AccessibilityNodeInfoUtils.recycleNodes(source);
    if (!isFocused) {
      LogUtils.log(this, Log.VERBOSE, "Dropped text-selection event from non-focused field");
      return true;
    }

    return false;
  }

  private boolean shouldDropScrollEvent(AccessibilityEvent event, long currentTime) {
    if ((currentTime - mLastScrollEventTime) < MIN_SCROLL_INTERVAL) {
      // TODO: We shouldn't just reject events, since we'll get weird
      // rhythms when scrolling, e.g. if we're getting an event every
      // 300ms and we reject at the 250ms mark.
      return true;
    }

    return !isValidScrollEvent(event);
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
