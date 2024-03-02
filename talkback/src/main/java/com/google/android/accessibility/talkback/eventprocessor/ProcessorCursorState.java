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

import android.annotation.SuppressLint;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.NodeBlockingOverlay;
import com.google.android.accessibility.talkback.NodeBlockingOverlay.OnDoubleTapListener;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
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
  private static String TAG = "ProcessorCursorState";

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
          | AccessibilityEvent.TYPE_VIEW_SCROLLED;

  private final Pipeline.FeedbackReturner pipeline;
  private final NodeBlockingOverlay overlay;
  private @Nullable AccessibilityNodeInfoCompat focusedNode = null;
  private boolean registered = false;

  public ProcessorCursorState(TalkBackService service, Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    overlay = new NodeBlockingOverlay(service, this);
    // Set an identifier to the overlay so that we know its added by Talkback.
    overlay.setRootViewClassName(Role.TALKBACK_EDIT_TEXT_OVERLAY_CLASSNAME);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_CURSOR_STATE;
  }

  @SuppressLint("SwitchIntDef") // Only some event-types are filtered out.
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        // Keep track of the accessibility focused EditText.
        overlay.hide();
        saveFocusedNode(AccessibilityEventCompat.asRecord(event));
        break;
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        // Hide the overlay so it doesn't interfere with scrolling.
        overlay.hide();
        break;
      default: // fall out
        break;
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
        Feedback.Part.builder()
            .setInterruptGroup(CURSOR_STATE)
            .setInterruptLevel(1)
            .setSenderName(TAG));
    focusedNode = null;
    overlay.hide();
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

      pipeline.returnFeedback(
          eventId, Feedback.nodeAction(focusedNode, AccessibilityNodeInfoCompat.ACTION_CLICK));

      pipeline.returnFeedback(
          eventId,
          Feedback.nodeAction(
              focusedNode, // Needed for Chrome browser.
              AccessibilityNodeInfoCompat.ACTION_FOCUS));
    }
  }

  private void saveFocusedNode(AccessibilityRecordCompat record) {
    focusedNode = null;
    AccessibilityNodeInfoCompat source = record.getSource();
    if (source != null) {
      if (Role.getRole(source) == Role.ROLE_EDIT_TEXT) {
        focusedNode = source;
      }
    }
  }
}
