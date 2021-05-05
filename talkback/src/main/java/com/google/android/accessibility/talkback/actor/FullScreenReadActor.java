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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLEAR;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Manages state related to reading the screen from top or next. */
public class FullScreenReadActor {

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Tag used for log output and wake lock */
  private static final String TAG = "FullScreenReadActor";

  public static final int STATE_STOPPED = 0;
  public static final int STATE_READING_FROM_BEGINNING = 1;
  public static final int STATE_READING_FROM_NEXT = 2;

  /** The possible states of the controller. */
  @IntDef({STATE_STOPPED, STATE_READING_FROM_BEGINNING, STATE_READING_FROM_NEXT})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ReadState {}

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  /**
   * The current state of the controller. Should only be updated through {@link
   * FullScreenReadActor#setReadingState(int)}
   */
  private @ReadState int currentState = STATE_STOPPED;

  private @ReadState int stateWaitingForContentFocus = STATE_STOPPED;

  /** The parent service */
  private final AccessibilityService service;

  private final SpeechController speechController;

  /** Feedback Returner of Pipeline for audio feedback */
  private Pipeline.FeedbackReturner pipeline;

  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  /** Wake lock for keeping the device unlocked while reading */
  private PowerManager.WakeLock wakeLock;

  /** Dialog for continuous reading mode */
  FullScreenReadDialog fullScreenReadDialog;

  private final RetryReadingHandler retryReadingHandler = new RetryReadingHandler();

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // State-reading interface

  /** Read-only interface, for event-interpreters to read this actor's state. */
  public class State {

    public boolean isActive() {
      return FullScreenReadActor.this.isActive();
    }

    public boolean isWaitingForContentFocus() {
      return fullScreenReadDialog.isWaitingForContentFocus();
    }
  }

  public final State state = new State();

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  @SuppressWarnings("deprecation")
  public FullScreenReadActor(
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      TalkBackService service,
      SpeechController speechController) {
    if (accessibilityFocusMonitor == null) {
      throw new IllegalStateException();
    }
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.service = service;
    this.speechController = speechController;
    fullScreenReadDialog = new FullScreenReadDialog(service);
    wakeLock =
        ((PowerManager) service.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    fullScreenReadDialog.setPipeline(pipeline);
  }

  /** Releases all resources held by this controller and save any persistent preferences. */
  public void shutdown() {
    interrupt();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /** Starts linearly reading from the node with accessibility focus. */
  public void startReadingFromNextNode(EventId eventId) {
    if (fullScreenReadDialog.getShouldShowDialogPref()) {
      stateWaitingForContentFocus = STATE_READING_FROM_NEXT;
      fullScreenReadDialog.showDialogBeforeReading(eventId);
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

    // Continuous reading mode (CRM) always uses default granularity.
    pipeline.returnFeedback(eventId, Feedback.granularity(DEFAULT));

    if (!wakeLock.isHeld()) {
      wakeLock.acquire();
    }

    moveForward();
  }

  /** Starts linearly reading from the top of the view hierarchy. */
  public void startReadingFromBeginning(EventId eventId) {
    if (fullScreenReadDialog.getShouldShowDialogPref()) {
      // Show dialog before start reading.
      stateWaitingForContentFocus = STATE_READING_FROM_BEGINNING;
      fullScreenReadDialog.showDialogBeforeReading(eventId);
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
      // Continuous reading mode (CRM) always uses default granularity.
      pipeline.returnFeedback(eventId, Feedback.granularity(DEFAULT));

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

  public void readFocusedContent(EventId eventId) {
    if (!fullScreenReadDialog.isWaitingForContentFocus()) {
      return;
    }
    fullScreenReadDialog.setWaitingForContentFocus(false);

    if (stateWaitingForContentFocus == STATE_READING_FROM_BEGINNING) {
      startReadingFromBeginningInternal(eventId, 0);
    } else if (stateWaitingForContentFocus == STATE_READING_FROM_NEXT) {
      startReadingFromNextNodeInternal(eventId);
    }
  }

  /** Stops speech output and view traversal at the current position. */
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

  private void setReadingState(@ReadState int newState) {
    LogUtils.v(TAG, "Continuous reading switching to mode: %s", newState);

    currentState = newState;

    speechController.setShouldInjectAutoReadingCallbacks(isActive(), nodeSpokenRunnable);
  }

  /**
   * Returns whether full-screen reading is currently active. Equivalent to calling {@code
   * currentState != STATE_STOPPED}.
   *
   * @return Whether full-screen reading is currently active.
   */
  public boolean isActive() {
    return currentState != STATE_STOPPED;
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
