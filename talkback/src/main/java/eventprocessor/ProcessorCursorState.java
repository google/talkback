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

package com.google.android.accessibility.talkback.eventprocessor;

import static com.google.android.accessibility.talkback.Feedback.CURSOR_STATE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.NodeBlockingOverlay;
import com.google.android.accessibility.talkback.NodeBlockingOverlay.OnDoubleTapListener;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Works around an issue with explore-by-touch where the cursor position snaps to the middle of the
 * edit field when the field is focused or double-tapped. The workaround has two parts: (1) Reset
 * the cursor position to the end when it is moved during a focus event. (2) Prevent double-taps on
 * an editing edit field by using a touchable accessibility overlay. Since explore-by-touch
 * double-taps dispatch a click to the center of the accessibility- focused node, an overlay that
 * sits atop an edit field can catch the touch events that are dispatched when the edit field is
 * double-tapped.
 */
public class ProcessorCursorState implements AccessibilityEventListener, OnDoubleTapListener {
  // Starting from Android O, a11y framework perform ACTION_CLICK on double-tap, thus there is no
  // need to block touch down/up event nor to manually perform click action.
  private static final boolean SHOULD_HANDLE_TOUCH_EVENT = !BuildVersionUtils.isAtLeastO();

  /**
   * Delay (in ms) after which to provide cursor position feedback. We want a value that is not too
   * long, but it is OK if the delay is slightly noticeable. Mostly, we just want to avoid
   * preempting more important feedback.
   */
  protected static final int SPEECH_DELAY = 150;

  /** Event types that are handled by ProcessorCursorState. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_CURSOR_STATE =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;

  private final Context context;
  private final Pipeline.FeedbackReturner pipeline;
  private final NodeBlockingOverlay overlay;
  private final GlobalVariables globalVariables;
  @Nullable private AccessibilityNodeInfoCompat focusedNode = null;
  private boolean registered = false;

  public ProcessorCursorState(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      GlobalVariables globalVariables) {
    context = service;
    this.pipeline = pipeline;
    overlay = new NodeBlockingOverlay(service, this);
    // Set an identifier to the overlay so that we know its added by Talkback.
    overlay.setRootViewClassName(Role.TALKBACK_EDIT_TEXT_OVERLAY_CLASSNAME);
    this.globalVariables = globalVariables;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_CURSOR_STATE;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        // Keep track of the accessibility focused EditText.
        overlay.hide();
        saveFocusedNode(AccessibilityEventCompat.asRecord(event));
        break;
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        // On pre Android O devices, double-tap on screen will interpreted as touch down and up
        // action at the center of the focused node, which might set cursor to the middle of text if
        // the text is long enough. TalkBack overrides the cursor position to be the end of the
        // field to avoid the confusion of cursor movement. See  for details.
        if (SHOULD_HANDLE_TOUCH_EVENT) {
          // Reset the EditText cursor because focusing will snap it to the middle.
          resetNodeCursor(AccessibilityEventCompat.asRecord(event), eventId);
        }
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        // Hide the overlay so it doesn't interfere with scrolling.
        overlay.hide();
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        if (SHOULD_HANDLE_TOUCH_EVENT) {
          // Show the overlay if the a11y-focused EditText is editing and we touch the screen.
          touchStart(event, eventId);
        }
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        if (SHOULD_HANDLE_TOUCH_EVENT) {
          // Hide the overlay when we stop touching the screen.
          touchEnd(event, eventId);
        }
        break;
      default: // fall out
    }
  }

  public void onReloadPreferences(TalkBackService service) {
    boolean supported = NodeBlockingOverlay.isSupported(service);
    if (registered && !supported) {
      service.postRemoveEventListener(this);
      reset();
      registered = false;
    } else if (!registered && supported) {
      service.addEventListener(this);
      registered = true;
    }
  }

  private void reset() {
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.Part.builder().setInterruptGroup(CURSOR_STATE).setInterruptLevel(1));
    focusedNode = null;
    overlay.hide();
  }

  private void touchStart(AccessibilityEvent event, EventId eventId) {
    // Detect if the node is visible and editing; if so, then show the overlay with a delay.
    AccessibilityNodeInfoCompat refreshedNode = AccessibilityNodeInfoUtils.refreshNode(focusedNode);
    if (refreshedNode != null) {
      boolean focused;
      AccessibilityWindowInfoCompat window = AccessibilityNodeInfoUtils.getWindow(refreshedNode);
      focused =
          refreshedNode.isVisibleToUser()
              && refreshedNode.isFocused()
              && window != null
              && window.isFocused();
      if (focused) {
        Rect r = new Rect();
        refreshedNode.getBoundsInScreen(r);
        overlay.showDelayed(r);
      }
      refreshedNode.recycle();

      overlay.onAccessibilityEvent(event, eventId);
    }
  }

  private void touchEnd(AccessibilityEvent event, EventId eventId) {
    AccessibilityNodeInfoCompat refreshedNode = AccessibilityNodeInfoUtils.refreshNode(focusedNode);
    if (refreshedNode != null) {
      refreshedNode.recycle();
      overlay.onAccessibilityEvent(event, eventId);
    }

    // Hide the overlay with a delay if it is visible or is pending visibility.
    if (overlay.isVisibleOrShowPending()) {
      overlay.hideDelayed();
    }
  }

  @Override
  public void onDoubleTap(EventId eventId) {
    // Show the soft IME (if the user's using a soft IME) when double-clicking.

    // This callback method is invoked when a double-tap action is detected, even if the overlay is
    // not displayed on screen, in which case a11y framework will handle the action and we should
    // not manually perform click action.

    // Logically in this case, overlay.isVisibleOrShowPending() is equivalent to
    // (focusedNode.isVisibleToUser() && focusedNode.isFocused()). The latter one is used in
    // touchStart() to show overlay.
    if (focusedNode != null && overlay.isVisibleOrShowPending()) {
      // All of the benefits of clicking without the pain of resetting the cursor!
      PerformActionUtils.performAction(
          focusedNode, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
      PerformActionUtils.performAction(
          focusedNode, // Needed for Chrome browser.
          AccessibilityNodeInfoCompat.ACTION_FOCUS,
          eventId);
    }
  }

  private void saveFocusedNode(AccessibilityRecordCompat record) {
    if (focusedNode != null) {
      focusedNode.recycle();
      focusedNode = null;
    }

    AccessibilityNodeInfoCompat source = record.getSource();
    if (source != null) {
      if (Role.getRole(source) == Role.ROLE_EDIT_TEXT) {
        focusedNode = source;
      } else {
        source.recycle();
      }
    }
  }

  private void resetNodeCursor(AccessibilityRecordCompat record, EventId eventId) {
    AccessibilityNodeInfoCompat source = record.getSource();
    if (source != null) {
      if (source.equals(focusedNode)) {
        // Reset cursor to end if there's text.
        if (!TextUtils.isEmpty(source.getText())) {
          int end = source.getText().length();

          Bundle bundle = new Bundle();
          bundle.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, end);
          bundle.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, end);

          globalVariables.setFlag(GlobalVariables.EVENT_SKIP_SELECTION_CHANGED_AFTER_FOCUSED);
          globalVariables.setFlag(GlobalVariables.EVENT_SKIP_SELECTION_CHANGED_AFTER_CURSOR_RESET);
          PerformActionUtils.performAction(
              source, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, bundle, eventId);

          SpeechController.SpeakOptions speakOptions =
              SpeechController.SpeakOptions.create()
                  .setQueueMode(SpeechController.QUEUE_MODE_QUEUE)
                  .setFlags(
                      FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
          Feedback.Part.Builder part =
              Feedback.Part.builder()
                  .speech(context.getString(R.string.notification_type_end_of_field), speakOptions)
                  .setDelayMs(SPEECH_DELAY)
                  .setInterruptGroup(CURSOR_STATE)
                  .setInterruptLevel(1);
          // Not tracking performance because field feedback should already be
          // provided when field is focused.
          pipeline.returnFeedback(EVENT_ID_UNTRACKED, part);
        }
      }

      source.recycle();
    }
  }
}
