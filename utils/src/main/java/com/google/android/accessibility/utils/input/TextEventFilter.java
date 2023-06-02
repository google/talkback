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

package com.google.android.accessibility.utils.input;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.input.TextEventInterpreter.InterpretationConsumer;
import com.google.android.accessibility.utils.monitor.VoiceActionDelegate;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Determines whether events should be passed on to the compositor. Interprets text events into more
 * specific event types, and extracts data from events.
 */
public class TextEventFilter {

  private static final String TAG = "TextEventFilter";

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

  /**
   * Keyboard echo preferences. These must be synchronized with @array/pref_keyboard_echo_values and
   *
   * @array/pref_keyboard_echo_entries in values/donottranslate.xml.
   */
  public static final int PREF_ECHO_NONE = 2;

  public static final int PREF_ECHO_CHARACTERS = 1;
  public static final int PREF_ECHO_WORDS = 3;
  public static final int PREF_ECHO_CHARACTERS_AND_WORDS = 0;

  /** The options of keyboard echo type. */
  @IntDef({PREF_ECHO_NONE, PREF_ECHO_CHARACTERS, PREF_ECHO_WORDS, PREF_ECHO_CHARACTERS_AND_WORDS})
  @Retention(RetentionPolicy.SOURCE)
  public @interface KeyboardEchoType {}

  @KeyboardEchoType private int onScreenKeyboardEcho = PREF_ECHO_CHARACTERS_AND_WORDS;
  @KeyboardEchoType private int physicalKeyboardEcho = PREF_ECHO_CHARACTERS_AND_WORDS;

  private enum KeyboardType {
    ON_SCREEN,
    PHYSICAL
  }

  private static final int PHYSICAL_KEY_TIMEOUT = 100;

  ///////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Context context;
  private @Nullable VoiceActionDelegate voiceActionDelegate;

  private final TextEventHistory textEventHistory;
  private long lastKeyEventTime = -1;

  @Nullable TextCursorTracker textCursorTracker;

  private final Collection<InterpretationConsumer> listeners = new ArrayList<>();

  // /////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEventFilter(
      Context context,
      @Nullable TextCursorTracker textCursorTracker,
      TextEventHistory textEventHistory) {
    this.context = context;
    this.textCursorTracker = textCursorTracker;
    this.textEventHistory = textEventHistory;
  }

  ///////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void setVoiceActionDelegate(@Nullable VoiceActionDelegate delegate) {
    voiceActionDelegate = delegate;
  }

  public void setOnScreenKeyboardEcho(@KeyboardEchoType int value) {
    onScreenKeyboardEcho = value;
  }

  public void setPhysicalKeyboardEcho(@KeyboardEchoType int value) {
    physicalKeyboardEcho = value;
  }

  public void setLastKeyEventTime(long time) {
    lastKeyEventTime = time;
  }

  public void addListener(InterpretationConsumer listener) {
    listeners.add(listener);
  }

  public void updateTextCursorTracker(AccessibilityEvent event, @Nullable EventId eventId) {
    // TODO: Has to move TextCursorTracker, together with TextEventInterpreter, to
    // pipeline. Also need to prepare a copy of TextEventInterpreter for switch-access.
    if (textCursorTracker != null) {
      // Prevent null check failure:
      // go/nullness-faq#i-checked-that-a-nullable-field-is-non-null-but-the-checker-is-still-complaining-that-it-could-be-null
      TextCursorTracker textCursorTrackerLocal = textCursorTracker;
      if ((textCursorTrackerLocal.getEventTypes() & event.getEventType()) != 0) {
        textCursorTrackerLocal.onAccessibilityEvent(event, eventId);
      }
    }
  }

  /** Filters out some text-events, and sends remaining text-event-interpretations to listeners. */
  @SuppressLint("SwitchIntDef") // Switch includes both text-event and accessibility-event types.
  public void filterAndSendInterpretation(
      AccessibilityEvent event,
      @Nullable EventId eventId,
      @Nullable TextEventInterpretation textEventInterpreted) {

    int eventType =
        (textEventInterpreted == null) ? event.getEventType() : textEventInterpreted.getEvent();

    switch (eventType) {

        // For focus fallback events, drop events with a source node.
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        {
          // Update cursor position when an empty edit text is focused. TalkBack will not receive
          // the initial {@link AccessibilityEvent#TYPE_VIEW_TEXT_SELECTION_CHANGED} event when an
          // empty edit text is focused, in which case we need to manually update the index.
          AccessibilityNodeInfoCompat source =
              AccessibilityNodeInfoUtils.toCompat(event.getSource());
          if (source != null) {
            if (AccessibilityNodeInfoUtils.isEmptyEditTextRegardlessOfHint(source)) {
              textEventHistory.setLastFromIndex(0);
              textEventHistory.setLastToIndex(0);
              textEventHistory.setLastNode(source.unwrap());
            }
          }
        }
        return;

        // Text input change
      case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
      case TextEventInterpretation.TEXT_CLEAR:
      case TextEventInterpretation.TEXT_REMOVE:
      case TextEventInterpretation.TEXT_ADD:
      case TextEventInterpretation.TEXT_REPLACE:
      case TextEventInterpretation.TEXT_PASSWORD_ADD:
      case TextEventInterpretation.TEXT_PASSWORD_REMOVE:
      case TextEventInterpretation.TEXT_PASSWORD_REPLACE:
        {
          if (dropTextChangeEvent(event)) {
            return;
          }
          // Update text change history.
          textEventHistory.setTextChangesAwaitingSelection(1);
          textEventHistory.setLastTextChangeTime(event.getEventTime());
          textEventHistory.setLastTextChangePackageName(event.getPackageName());
          if ((voiceActionDelegate != null) && voiceActionDelegate.isVoiceRecognitionActive()) {
            LogUtils.d(TAG, "Drop TYPE_VIEW_TEXT_CHANGED event: Voice recognition is active.");
            return;
          }
        }
        break;

        // Text input selection change
      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
      case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
      case TextEventInterpretation.SELECTION_FOCUS_EDIT_TEXT:
      case TextEventInterpretation.SELECTION_MOVE_CURSOR_TO_BEGINNING:
      case TextEventInterpretation.SELECTION_MOVE_CURSOR_TO_END:
      case TextEventInterpretation.SELECTION_MOVE_CURSOR_NO_SELECTION:
      case TextEventInterpretation.SELECTION_MOVE_CURSOR_WITH_SELECTION:
      case TextEventInterpretation.SELECTION_MOVE_CURSOR_SELECTION_CLEARED:
      case TextEventInterpretation.SELECTION_CUT:
      case TextEventInterpretation.SELECTION_PASTE:
      case TextEventInterpretation.SELECTION_TEXT_TRAVERSAL:
      case TextEventInterpretation.SELECTION_SELECT_ALL:
      case TextEventInterpretation.SELECTION_SELECT_ALL_WITH_KEYBOARD:
      case TextEventInterpretation.SELECTION_RESET_SELECTION:
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

      default:
        return; // Send only text-events to listeners / compositor.
    }

    TextEventInterpreter.Interpretation interpretation =
        new TextEventInterpreter.Interpretation(event, eventId, textEventInterpreted);
    // Send interpretation to listeners.
    for (InterpretationConsumer listener : listeners) {
      listener.accept(interpretation);
    }
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

  private KeyboardType getKeyboardType(long textEventTime) {
    if (textEventTime - lastKeyEventTime < PHYSICAL_KEY_TIMEOUT) {
      return KeyboardType.PHYSICAL;
    } else {
      return KeyboardType.ON_SCREEN;
    }
  }

  public boolean shouldEchoAddedText(long eventTime) {
    return shouldEchoAddedText(getKeyboardType(eventTime));
  }

  private boolean shouldEchoAddedText(/* int changeType, */ KeyboardType keyboardType) {
    if (keyboardType == KeyboardType.PHYSICAL) {
      return physicalKeyboardEcho == PREF_ECHO_CHARACTERS
          || physicalKeyboardEcho == PREF_ECHO_CHARACTERS_AND_WORDS;
    } else if (keyboardType == KeyboardType.ON_SCREEN) {
      return onScreenKeyboardEcho == PREF_ECHO_CHARACTERS
          || onScreenKeyboardEcho == PREF_ECHO_CHARACTERS_AND_WORDS;
    }
    return false;
  }

  public boolean shouldEchoInitialWords(long eventTime) {
    return shouldEchoInitialWords(getKeyboardType(eventTime));
  }

  private boolean shouldEchoInitialWords(/* int changeType, */ KeyboardType keyboardType) {
    if (keyboardType == KeyboardType.PHYSICAL) {
      return physicalKeyboardEcho == PREF_ECHO_WORDS
          || physicalKeyboardEcho == PREF_ECHO_CHARACTERS_AND_WORDS;
    } else if (keyboardType == KeyboardType.ON_SCREEN) {
      return onScreenKeyboardEcho == PREF_ECHO_WORDS
          || onScreenKeyboardEcho == PREF_ECHO_CHARACTERS_AND_WORDS;
    }
    return false;
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
    if (!isFocused) {
      LogUtils.d(TAG, "Dropped text-selection event from non-focused field");
      return true;
    }

    return false;
  }

}
