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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;

/** Manages state related to reading the screen from top */
public class FullScreenReadControllerApp
    implements FullScreenReadController, AccessibilityEventListener {
  /** Tag used for log output and wake lock */
  private static final String TAG = "FullScreenReadController";

  /** The possible states of the controller. */
  private static final int STATE_STOPPED = 0;

  private static final int STATE_READING_FROM_BEGINNING = 1;
  private static final int STATE_READING_FROM_NEXT = 2;

  /** Event types that should interrupt continuous reading, if active. */
  private static final int MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
          | AccessibilityEventCompat.TYPE_ANNOUNCEMENT
          | AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START
          | AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEventCompat.TYPE_VIEW_TEXT_SELECTION_CHANGED;

  /**
   * The current state of the controller. Should only be updated through {@link
   * FullScreenReadControllerApp#setReadingState(int)}
   */
  private int mCurrentState = STATE_STOPPED;

  /** The parent service */
  private final TalkBackService mService;

  /** Controller for linearly navigating the view hierarchy tree */
  private CursorController mCursorController;

  /** Feedback controller for audio feedback */
  private final FeedbackController mFeedbackController;

  /** Wake lock for keeping the device unlocked while reading */
  private PowerManager.WakeLock mWakeLock;

  private final RetryReadingHandler mRetryReadingHandler = new RetryReadingHandler();

  @SuppressWarnings("deprecation")
  public FullScreenReadControllerApp(
      FeedbackController feedbackController,
      CursorController cursorController,
      TalkBackService service) {
    if (cursorController == null) throw new IllegalStateException();
    if (feedbackController == null) throw new IllegalStateException();

    mCursorController = cursorController;
    mFeedbackController = feedbackController;
    mService = service;
    mWakeLock =
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
    if (isActive()) {
      return;
    }

    final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
    if (currentNode == null) {
      return;
    }

    setReadingState(STATE_READING_FROM_NEXT);

    mCursorController.setGranularity(CursorGranularity.DEFAULT, false /* fromUser */, eventId);

    if (!mWakeLock.isHeld()) {
      mWakeLock.acquire();
    }

    moveForward();

    currentNode.recycle();
  }

  /** Starts linearly reading from the top of the view hierarchy. */
  @Override
  public void startReadingFromBeginning(EventId eventId) {
    startReadingFromBeginningInternal(eventId, 0);
  }

  private void startReadingFromBeginningInternal(EventId eventId, int attemptCount) {
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat currentNode = null;

    if (isActive()) {
      return;
    }

    try {
      rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
      if (rootNode == null) {
        if (!mRetryReadingHandler.tryReadFromTopLater(eventId, attemptCount)) {
          LogUtils.log(
              FullScreenReadControllerApp.this,
              Log.WARN,
              "Fail to read from top: No active window.");
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

      mCursorController.setGranularity(CursorGranularity.DEFAULT, false /* fromUser */, eventId);

      if (!mWakeLock.isHeld()) {
        mWakeLock.acquire();
      }

      // This is potentially a refocus, so we should set the refocus flag just in case.
      EventState.getInstance().setFlag(EventState.EVENT_NODE_REFOCUSED);
      mCursorController.clearCursor(eventId);
      mCursorController.setCursor(currentNode, eventId); // Will automatically move forward.
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(rootNode, currentNode);
    }
  }

  /** Stops speech output and view traversal at the current position. */
  @Override
  public void interrupt() {
    setReadingState(STATE_STOPPED);

    if (mWakeLock.isHeld()) {
      mWakeLock.release();
    }
  }

  private void moveForward() {
    EventId eventId = EVENT_ID_UNTRACKED; // First node's speech is already performance tracked.
    if (!mCursorController.next(
        false /* shouldWrap */,
        false /* shouldScroll */,
        false /*useInputFocusAsPivotIfEmpty*/,
        InputModeManager.INPUT_MODE_UNKNOWN,
        eventId)) {
      mFeedbackController.playAuditory(R.raw.complete, 1.3f, 1);
      interrupt();
    }
  }

  private void setReadingState(int newState) {
    LogUtils.log(TAG, Log.VERBOSE, "Continuous reading switching to mode: %s", newState);

    mCurrentState = newState;

    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service
          .getSpeechController()
          .setShouldInjectAutoReadingCallbacks(isActive(), mNodeSpokenRunnable);
    }
  }

  /**
   * Returns whether full-screen reading is currently active. Equivalent to calling {@code
   * mCurrentState != STATE_STOPPED}.
   *
   * @return Whether full-screen reading is currently active.
   */
  @Override
  public boolean isActive() {
    return mCurrentState != STATE_STOPPED;
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
  private final SpeechController.UtteranceCompleteRunnable mNodeSpokenRunnable =
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
