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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.NodeBlockingOverlay;
import com.google.android.accessibility.talkback.NodeBlockingOverlay.OnDoubleTapListener;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.SpeechController;

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
  private static final long SPEECH_DELAY = 150;

  /** Event types that are handled by ProcessorCursorState. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_CURSOR_STATE =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;

  private final Context mContext;
  private final SpeechController mSpeechController;
  private final NodeBlockingOverlay mOverlay;
  private final GlobalVariables mGlobalVariables;
  private AccessibilityNodeInfoCompat mFocusedNode = null;
  private Handler mHandler = new Handler();
  private boolean mRegistered = false;

  public ProcessorCursorState(TalkBackService service, GlobalVariables globalVariables) {
    mContext = service;
    mSpeechController = service.getSpeechController();
    mOverlay = new NodeBlockingOverlay(service, this);
    mGlobalVariables = globalVariables;
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
        mOverlay.hide();
        saveFocusedNode(AccessibilityEventCompat.asRecord(event));
        break;
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        // Reset the EditText cursor because focusing will snap it to the middle.
        resetNodeCursor(AccessibilityEventCompat.asRecord(event), eventId);
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        // Hide the overlay so it doesn't interfere with scrolling.
        mOverlay.hide();
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
    if (mRegistered && !supported) {
      service.postRemoveEventListener(this);
      reset();
      mRegistered = false;
    } else if (!mRegistered && supported) {
      service.addEventListener(this);
      mRegistered = true;
    }
  }

  private void reset() {
    mHandler.removeCallbacksAndMessages(null);
    mFocusedNode = null;
    mOverlay.hide();
  }

  private void touchStart(AccessibilityEvent event, EventId eventId) {
    // Detect if the node is visible and editing; if so, then show the overlay with a delay.
    AccessibilityNodeInfoCompat refreshedNode =
        AccessibilityNodeInfoUtils.refreshNode(mFocusedNode);
    if (refreshedNode != null) {
      boolean focused;
      if (BuildVersionUtils.isAtLeastLMR1()) {
        AccessibilityWindowInfoCompat window = refreshedNode.getWindow();
        focused =
            refreshedNode.isVisibleToUser()
                && refreshedNode.isFocused()
                && window != null
                && window.isFocused();
      } else {
        focused = refreshedNode.isVisibleToUser() && refreshedNode.isFocused();
      }
      if (focused) {
        Rect r = new Rect();
        refreshedNode.getBoundsInScreen(r);
        mOverlay.showDelayed(r);
      }
      refreshedNode.recycle();

      mOverlay.onAccessibilityEvent(event, eventId);
    }
  }

  private void touchEnd(AccessibilityEvent event, EventId eventId) {
    AccessibilityNodeInfoCompat refreshedNode =
        AccessibilityNodeInfoUtils.refreshNode(mFocusedNode);
    if (refreshedNode != null) {
      refreshedNode.recycle();
      mOverlay.onAccessibilityEvent(event, eventId);
    }

    // Hide the overlay with a delay if it is visible or is pending visibility.
    if (mOverlay.isVisibleOrShowPending()) {
      mOverlay.hideDelayed();
    }
  }

  @Override
  public void onDoubleTap(EventId eventId) {
    // Show the soft IME (if the user's using a soft IME) when double-clicking.

    // This callback method is invoked when a double-tap action is detected, even if the overlay is
    // not displayed on screen, in which case a11y framework will handle the action and we should
    // not manually perform click action.

    // Logically in this case, mOverlay.isVisibleOrShowPending() is equivalent to
    // (mFocusedNode.isVisibleToUser() && mFocusedNode.isFocused()). The latter one is used in
    // touchStart() to show overlay.
    if (mFocusedNode != null && mOverlay.isVisibleOrShowPending()) {
      // All of the benefits of clicking without the pain of resetting the cursor!
      PerformActionUtils.performAction(
          mFocusedNode, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
      PerformActionUtils.performAction(
          mFocusedNode, // Needed for Chrome browser.
          AccessibilityNodeInfoCompat.ACTION_FOCUS,
          eventId);
    }
  }

  private void saveFocusedNode(AccessibilityRecordCompat record) {
    if (mFocusedNode != null) {
      mFocusedNode.recycle();
      mFocusedNode = null;
    }

    AccessibilityNodeInfoCompat source = record.getSource();
    if (source != null) {
      if (Role.getRole(source) == Role.ROLE_EDIT_TEXT) {
        mFocusedNode = source;
      } else {
        source.recycle();
      }
    }
  }

  private void resetNodeCursor(AccessibilityRecordCompat record, EventId eventId) {
    AccessibilityNodeInfoCompat source = record.getSource();
    if (source != null) {
      if (source.equals(mFocusedNode)) {
        // Reset cursor to end if there's text.
        if (!TextUtils.isEmpty(source.getText())) {
          int end = source.getText().length();

          Bundle bundle = new Bundle();
          bundle.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, end);
          bundle.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, end);

          mGlobalVariables.setFlag(GlobalVariables.EVENT_SKIP_SELECTION_CHANGED_AFTER_FOCUSED);
          mGlobalVariables.setFlag(GlobalVariables.EVENT_SKIP_SELECTION_CHANGED_AFTER_CURSOR_RESET);
          PerformActionUtils.performAction(
              source, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, bundle, eventId);

          mHandler.postDelayed(
              new Runnable() {
                @Override
                public void run() {

                  // Not tracking performance because field feedback should already be
                  // provided when field is focused.
                  EventId eventId = EVENT_ID_UNTRACKED;

                  mSpeechController.speak(
                      mContext.getString(R.string.notification_type_end_of_field),
                      SpeechController.QUEUE_MODE_QUEUE,
                      0 /* flags */,
                      null /* bundle */,
                      eventId);
                }
              },
              SPEECH_DELAY);
        }
      }

      source.recycle();
    }
  }
}
