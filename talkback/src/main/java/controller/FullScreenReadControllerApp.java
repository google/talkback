/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.talkback.controller;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLEAR;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.APPLY_SAVED_GRANULARITY;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Manages state related to reading the screen from top or next. */
public class FullScreenReadControllerApp
    implements FullScreenReadController, AccessibilityEventListener {
  /** Tag used for log output and wake lock */
  private static final String TAG = "FullScreenReadController";

  /** The possible states of the controller. */
  private static final int STATE_STOPPED = 0;

  private static final int STATE_READING_FROM_BEGINNING = 1;
  private static final int STATE_READING_FROM_NEXT = 2;

  /** Enable enhanced feature if implement completely. */
  public static final boolean ENABLE_CONTINUOUS_READING_MODE_ENHANCE = true;

  /** Event types that should interrupt continuous reading, if active. */
  private static final int MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START
          | AccessibilityEventCompat.TYPE_ANNOUNCEMENT;

  /**
   * The current state of the controller. Should only be updated through {@link
   * FullScreenReadControllerApp#setReadingState(int)}
   */
  private int currentState = STATE_STOPPED;

  /** The parent service */
  private final TalkBackService service;

  /** Feedback Returner of Pipeline for audio feedback */
  private final Pipeline.FeedbackReturner pipeline;

  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  /** Wake lock for keeping the device unlocked while reading */
  private PowerManager.WakeLock wakeLock;

  /** Dialog for continuous reading mode */
  FullScreenReadDialog fullScreenReadDialog;

  private final RetryReadingHandler retryReadingHandler = new RetryReadingHandler();

  @SuppressWarnings("deprecation")
  public FullScreenReadControllerApp(
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      AccessibilityFocusManager accessibilityFocusManager,
      Pipeline.FeedbackReturner pipeline,
      TalkBackService service) {
    if (accessibilityFocusMonitor == null) {
      throw new IllegalStateException();
    }
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    if (ENABLE_CONTINUOUS_READING_MODE_ENHANCE) {
      accessibilityFocusManager.setFullScreenReadController(this);
    }
    this.pipeline = pipeline;
    this.service = service;
    fullScreenReadDialog = new FullScreenReadDialog(this, service, pipeline);
    wakeLock =
        ((PowerManager) service.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
  }

  /** Releases all resources held by this controller and save any persistent preferences. */
  @Override
  public void shutdown() {
    interrupt();
  }

  /** Starts linearly reading from the node with accessibility focus. */
  @Override
  public void startReadingFromNextNode(EventId eventId) {
    startReadingFromNextNode(eventId, false);
  }

  /**
   * Starts linearly reading from the node with accessibility focus.
   *
   * @param fromContextMenu Flag to check if Reading is triggered from Context menu.
   */
  @Override
  public void startReadingFromNextNode(EventId eventId, boolean fromContextMenu) {
    if (fullScreenReadDialog.getShouldShowDialogPref()) {
      fullScreenReadDialog.showDialogBeforeReading(
          STATE_READING_FROM_NEXT, fromContextMenu, eventId);
    } else {
      startReadingFromNextNodeInternal(eventId);
    }
  }

  private void startReadingFromNextNodeInternal(EventId eventId) {
    if (isActive()) {
      return;
    }

    @Nullable
    AccessibilityNodeInfoCompat currentNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    try {
      if (currentNode == null) {
          LogUtils.w(TAG, "Fail to read from next: Current node is null.");
          return;
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentNode);
      currentNode = null;
    }

    setReadingState(STATE_READING_FROM_NEXT);

    // Restore granularity chosen by user. More details are in   and 
    pipeline.returnFeedback(eventId, Feedback.focusDirection(APPLY_SAVED_GRANULARITY));

    if (!wakeLock.isHeld()) {
      wakeLock.acquire();
    }

    moveForward();
  }

  @Override
  public void startReadingFromBeginning(EventId eventId) {
    startReadingFromBeginning(eventId, false);
  }
  /** Starts linearly reading from the top of the view hierarchy. */
  @Override
  public void startReadingFromBeginning(EventId eventId, boolean fromContextMenu) {
    if (fullScreenReadDialog.getShouldShowDialogPref()) {
      // Show dialog before start reading.
      fullScreenReadDialog.showDialogBeforeReading(
          STATE_READING_FROM_BEGINNING, fromContextMenu, eventId);
    } else {
      startReadingFromBeginningInternal(eventId, 0);
    }
  }

  private void startReadingFromBeginningInternal(EventId eventId, int attemptCount) {
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat currentNode = null;

    if (isActive()) {
      return;
    }

    try {
      rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(service);
      if (rootNode == null) {
        if (!retryReadingHandler.tryReadFromTopLater(eventId, attemptCount)) {
          LogUtils.w(TAG, "Fail to read from top: No active window.");
        }
        return;
      }

      TraversalStrategy traversal = new OrderedTraversalStrategy(rootNode);
      try {
        currentNode =
            TraversalStrategyUtils.searchFocus(
                traversal,
                rootNode,
                TraversalStrategy.SEARCH_FOCUS_FORWARD,
                AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
      } finally {
        traversal.recycle();
      }

      if (currentNode == null) {
        return;
      }

      setReadingState(STATE_READING_FROM_BEGINNING);
      // Restore granularity chosen by user. More details are in   and 
      pipeline.returnFeedback(eventId, Feedback.focusDirection(APPLY_SAVED_GRANULARITY));

      if (!wakeLock.isHeld()) {
        wakeLock.acquire();
      }

      // This is potentially a refocus, so we should set the refocus flag just in case.
      EventState.getInstance().setFlag(EventState.EVENT_NODE_REFOCUSED);
      pipeline.returnFeedback(eventId, Feedback.focus(CLEAR));
      moveForward();
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(rootNode, currentNode);
    }
  }

  @Override
  public void startReadingWithoutDialog(EventId eventId, int state) {
    if (state == STATE_READING_FROM_BEGINNING) {
      startReadingFromBeginningInternal(eventId, 0);
    } else if (state == STATE_READING_FROM_NEXT) {
      startReadingFromNextNodeInternal(eventId);
    }
  }

  @Override
  public FullScreenReadDialog getFullScreenReadDialog() {
    if (fullScreenReadDialog == null) {
      fullScreenReadDialog = new FullScreenReadDialog(this, service, pipeline);
    }
    return fullScreenReadDialog;
  }

  /** Stops speech output and view traversal at the current position. */
  @Override
  public void interrupt() {
    setReadingState(STATE_STOPPED);

    if (wakeLock.isHeld()) {
      wakeLock.release();
    }
  }

  private void moveForward() {
    EventId eventId = EVENT_ID_UNTRACKED; // First node's speech is already performance tracked.
    if (!pipeline.returnFeedback(
        eventId, Feedback.focusDirection(SEARCH_FOCUS_FORWARD).setScroll(true))) {
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      interrupt();
    }
  }

  private void setReadingState(int newState) {
    LogUtils.v(TAG, "Continuous reading switching to mode: %s", newState);

    currentState = newState;

    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service
          .getSpeechController()
          .setShouldInjectAutoReadingCallbacks(isActive(), nodeSpokenRunnable);
    }
  }

  /**
   * Returns whether full-screen reading is currently active. Equivalent to calling {@code
   * currentState != STATE_STOPPED}.
   *
   * @return Whether full-screen reading is currently active.
   */
  @Override
  public boolean isActive() {
    return currentState != STATE_STOPPED;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!isActive()) {
      return;
    }

    // Only interrupt full screen reading on events that can't be generated
    // by automated cursor movement or from delayed user interaction.
    if (AccessibilityEventUtils.eventMatchesAnyType(event, MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS)) {
      interrupt();
    }
  }

  /** Runnable executed when a node has finished being spoken */
  private final SpeechController.UtteranceCompleteRunnable nodeSpokenRunnable =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          if (isActive() && status != SpeechController.STATUS_INTERRUPTED) {
            moveForward();
          }
        }
      };

  /**
   * A {@link Handler} to retry ReadFromTop action. When the user performs read from top from Global
   * Context Menu, it is possible that when the GCM is closed, {@link
   * AccessibilityServiceCompatUtils#getRootInActiveWindow(AccessibilityService)} is not updated due
   * to race condition. Then read from top action fails. This class is used to retry the action if
   * the active window is not updated yet.
   */
  private final class RetryReadingHandler extends Handler {
    private static final int MSG_READ_FROM_TOP = 0;
    private static final int MAX_RETRY_COUNT = 10;
    private static final int RETRY_INTERVAL = 50;

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_READ_FROM_TOP) {
        startReadingFromBeginningInternal((EventId) msg.obj, msg.arg1);
      }
    }

    public void clear() {
      removeMessages(MSG_READ_FROM_TOP);
    }

    public boolean tryReadFromTopLater(EventId eventId, int attemptCount) {
      clear();
      if (attemptCount > MAX_RETRY_COUNT) {
        return false;
      }
      final Message msg =
          Message.obtain(
              this,
              MSG_READ_FROM_TOP, /* what */
              attemptCount + 1, /* arg1 */
              0, /* arg2, not necessary*/
              eventId /* obj */);
      sendMessageDelayed(msg, RETRY_INTERVAL);

      return true;
    }
  }
}
