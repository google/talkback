/*
 * Copyright (C) 2017 Google Inc.
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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.Pipeline.SyntheticEvent;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForLogicalNavigation;
import com.google.android.accessibility.talkback.interpreters.AutoScrollInterpreter;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.DelayHandler;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Supplier;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollTimeout;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.ScrollActionRecord.AutoScrollSuccessChecker;
import com.google.android.accessibility.utils.output.ScrollActionRecord.UserAction;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Scroll action performer.
 *
 * <p>It provides API {@link #scroll(int, AccessibilityNode, AccessibilityNodeInfoCompat, int,
 * AutoScrollRecord.Source, EventId)} to perform auto-scroll action on a node, stores actor state to
 * invoke when the result {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received, or
 * timeout failure occurs.
 */
public class AutoScrollActor {

  private static final String TAG = "AutoScrollActor";

  public static final int UNKNOWN_SCROLL_INSTANCE_ID = -1;

  /**
   * Fail-fast: if no scroll-progress event arrives within this long after performing a
   * scroll action, give up early instead of waiting out the full {@link ScrollTimeout}. Fires
   * only under complete scroll silence (no scroll event of any kind since the action): a working
   * scroll emits {@code TYPE_VIEW_SCROLLED} within a frame or two, which cancels this watchdog
   * via {@link #cancelTimeout()}, and even unmatched scroll activity (e.g. the user's own
   * finger on a still-settling list) suppresses it via {@link #notifyScrollEvent()}. A dead
   * scroll (an app that never performs the action and never reports progress) hits this and
   * proceeds to focus+speech hundreds of ms sooner per swipe.
   */
  private static final long FAIL_FAST_NO_PROGRESS_MS = 150;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Read-only interface

  /** Limited read-only interface to pull state data. */
  public class StateReader implements Supplier<ScrollActionRecord> {

    /** Returns the current scrolling record or the null if we are not in scrolling. */
    @Override
    public ScrollActionRecord get() {
      return AutoScrollActor.this.scrollActionRecord;
    }

    public ScrollActionRecord getFailedScrollActionRecord() {
      return AutoScrollActor.this.failedScrollActionRecord;
    }
  }

  /** Read-only interface for pulling state data. */
  public final StateReader stateReader = new StateReader();

  ///////////////////////////////////////////////////////////////////////////////////////
  // Member data and construction

  // TODO: If more actors require timeout failure delays... move timeout delay to
  // pipeline, with single delay-handler for all actors.
  private final DelayHandler<EventIdAnd<Boolean>> postDelayHandler;

  /**
   * Fail-fast watchdog. Armed alongside {@link #postDelayHandler} in {@link
   * #setScrollRecord}; disarmed by the first scroll-progress event ({@link #cancelTimeout()}) or
   * when the record is otherwise resolved. Fires {@link #handleFailFast()} if the app never starts
   * reporting scroll progress.
   */
  private final DelayHandler<EventIdAnd<Boolean>> failFastHandler;

  /** Scroll instance the pending fail-fast watchdog belongs to, or UNKNOWN if none is armed. */
  private int failFastInstanceId = UNKNOWN_SCROLL_INSTANCE_ID;

  /**
   * Uptime of the last scroll-related event seen for any scroll (not just the current record).
   * Updated via {@link #notifyScrollEvent()}; the fail-fast watchdog only fires under complete
   * scroll silence, so rapid swiping on a still-moving list can never trip it.
   */
  private volatile long lastScrollEventUptimeMs = 0;

  /**
   * Records that some scroll activity happened. Called by the scroll interpreter for every
   * scroll-related event, matched to the current record or not.
   */
  public void notifyScrollEvent() {
    lastScrollEventUptimeMs = SystemClock.uptimeMillis();
  }

  private Pipeline.EventReceiver pipelineReceiver;
  private Pipeline.FeedbackReturner feedbackReturner;

  /**
   * Used as identifier at the next auto-scroll action. Each action is assigned with a unique
   * scrollInstanceId by {@link #createScrollInstanceId()}.
   */
  private int nextScrollInstanceId = 0;

  public AutoScrollActor() {
    postDelayHandler =
        new DelayHandler<EventIdAnd<Boolean>>() {
          @Override
          public void handle(EventIdAnd<Boolean> args) {
            handleAutoScrollFailed();
          }
        };
    failFastHandler =
        new DelayHandler<EventIdAnd<Boolean>>() {
          @Override
          public void handle(EventIdAnd<Boolean> args) {
            handleFailFast();
          }
        };
  }

  // A null scrollActionRecord represents that we are not in scrolling.
  @Nullable private ScrollActionRecord scrollActionRecord = null;
  @Nullable private ScrollActionRecord failedScrollActionRecord = null;

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipeline) {
    this.pipelineReceiver = pipeline;
  }

  public void setPipeline(FeedbackReturner feedbackReturner) {
    this.feedbackReturner = feedbackReturner;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Event handling methods

  public void cancelTimeout() {
    postDelayHandler.removeMessages();
    // First scroll-progress event arrived: the scroll is alive, fail-fast no longer needed.
    failFastHandler.removeMessages();
    failFastInstanceId = UNKNOWN_SCROLL_INSTANCE_ID;
  }

  /**
   * Performs scroll action at the given node. Invoke the callback when the result {@link
   * AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received.
   *
   * @param userAction Source {@link UserAction} that leads to scroll action.
   * @param node Node to scroll
   * @param nodeCompat Node to scroll
   * @param scrollAccessibilityAction Accessibility scroll action
   * @param scrollSource The type of scroll caller
   * @param scrollTimeout Timeout of the scrolling result from framework
   * @param autoScrollAttempt The number of auto scroll attempts
   * @param bundle The bundle to pass to the feedback
   * @param autoScrollChecker The checker to check if the auto scroll is successful
   * @param eventId EventId for performance tracking.
   * @return {@code true} If the action is successfully performed.
   */
  public boolean scroll(
      @UserAction int userAction,
      @Nullable AccessibilityNode node,
      @Nullable AccessibilityNodeInfoCompat nodeCompat,
      int scrollAccessibilityAction,
      String scrollSource,
      ScrollTimeout scrollTimeout,
      int autoScrollAttempt,
      Bundle bundle,
      @Nullable AutoScrollSuccessChecker autoScrollChecker,
      EventId eventId) {
    if (node == null && nodeCompat == null) {
      return false;
    }
    long currentTime = SystemClock.uptimeMillis();

    boolean result =
        (node != null
                && feedbackReturner.returnFeedback(
                    eventId, Feedback.nodeAction(node, scrollAccessibilityAction, bundle)))
            || (nodeCompat != null
                && feedbackReturner.returnFeedback(
                    eventId, Feedback.nodeAction(nodeCompat, scrollAccessibilityAction, bundle)));
    if (result) {
      setScrollRecord(
          userAction,
          node,
          nodeCompat,
          scrollSource,
          currentTime,
          scrollTimeout,
          autoScrollAttempt,
          autoScrollChecker);
    }
    LogUtils.d(
        TAG,
        "Perform scroll action:result=%s\nnode=%s\nnodeCompat=%s\nScrollAction=%s\nUserAction=%s",
        result,
        node,
        nodeCompat,
        AccessibilityNodeInfoUtils.actionToString(scrollAccessibilityAction),
        ScrollActionRecord.userActionToString(userAction));
    return result;
  }

  public boolean ensureOnScreen(
      @UserAction int userAction,
      @NonNull AccessibilityNodeInfoCompat nodeCompat,
      @NonNull AccessibilityNodeInfoCompat actionNodeCompat,
      String scrollSource,
      ScrollTimeout scrollTimeout,
      EventId eventId) {
    if (actionNodeCompat == null || nodeCompat == null) {
      return false;
    }

    long currentTime = SystemClock.uptimeMillis();
    boolean result =
        feedbackReturner.returnFeedback(
            eventId, Feedback.nodeAction(actionNodeCompat, ACTION_SHOW_ON_SCREEN.getId()));
    if (result) {
      setScrollRecord(
          userAction,
          /* node= */ null,
          nodeCompat,
          scrollSource,
          currentTime,
          scrollTimeout,
          /* autoScrollAttempt= */ 0,
          /* autoScrollSuccessChecker= */ null);
    }
    LogUtils.d(
        TAG,
        "Perform ACTION_SHOW_ON_SCREEN:result=%s\n"
            + "nodeCompat=%s\n"
            + "actionNodeCompat=%s\n"
            + "UserAction=%s",
        result,
        nodeCompat,
        actionNodeCompat,
        ScrollActionRecord.userActionToString(userAction));
    return result;
  }

  private void setScrollRecord(
      @UserAction int userAction,
      @Nullable AccessibilityNode node,
      @Nullable AccessibilityNodeInfoCompat nodeCompat,
      String scrollSource,
      long currentTime,
      ScrollTimeout scrollTimeout,
      int autoScrollAttempt,
      @Nullable AutoScrollSuccessChecker autoScrollSuccessChecker) {
    int scrollInstanceId;
    if (autoScrollAttempt > 0 && scrollActionRecord != null) {
      scrollInstanceId = scrollActionRecord.scrollInstanceId;
      LogUtils.i(
          TAG,
          "autoScrollAttempt=%d > 0 so keep scrollActionRecord=%d the same.",
          autoScrollAttempt,
          scrollInstanceId);
    } else {
      scrollInstanceId = createScrollInstanceId();
      LogUtils.i(TAG, "new AutoScrollRecord with scrollActionRecord=%d", scrollInstanceId);
    }

    setAutoScrollRecord(
        new ScrollActionRecord(
            scrollInstanceId,
            node,
            nodeCompat,
            userAction,
            currentTime,
            scrollSource,
            autoScrollSuccessChecker));

    postDelayHandler.removeMessages();
    postDelayHandler.delay(
        scrollTimeout.getTimeoutMillis(), /* handlerArg= */ new EventIdAnd<>(false, null));

    // Fail-fast: never wait longer than FAIL_FAST_NO_PROGRESS_MS for the first sign of
    // life. Capped by the full timeout so SHORT/LONG semantics are unchanged. The silence
    // baseline starts at the action: only events arriving after this point count as progress.
    lastScrollEventUptimeMs = currentTime;
    failFastHandler.removeMessages();
    failFastInstanceId = scrollInstanceId;
    failFastHandler.delay(
        Math.min(FAIL_FAST_NO_PROGRESS_MS, scrollTimeout.getTimeoutMillis()),
        /* handlerArg= */ new EventIdAnd<>(false, null));
  }

  private void setAutoScrollRecord(ScrollActionRecord newRecord) {
    // Ignores previous failed auto-scroll record if there is a new auto-scroll record (when next
    // auto-scroll action performs).
    failedScrollActionRecord = null;
    scrollActionRecord = newRecord;
  }

  /**
   * Resets {@code scrollActionRecord} once the auto-scrolling action is completed. The caller which
   * invokes {@link AutoScrollActor#scroll} should be responsible for resetting it. Currently, only
   * {@link FocusProcessorForLogicalNavigation} in {@link DirectionNavigationActor} can perform
   * auto-scrolling action via {@link AutoScrollInterpreter}. It means that if the caller isn't
   * interested in {@code AutoScrollCallback} for this auto-scrolling action anymore, it should be
   * responsible for calling this method to reset the records.
   */
  public void resetScrollActionRecords() {
    failedScrollActionRecord = null;
    // Once the auto scroll is stopped, we should clear scrollActionRecord. So, if
    // scrollActionRecord is null, it means we are not in auto-scrolling.
    scrollActionRecord = null;
    failFastHandler.removeMessages();
    failFastInstanceId = UNKNOWN_SCROLL_INSTANCE_ID;
  }

  /**
   * Fail-fast: the full scroll timeout has not expired, but no scroll-progress event has
   * arrived since the action was performed. Treat it exactly like the timeout: move the record to
   * failed and let the normal {@code SCROLL_TIMEOUT} path (assume-success retry, focus, speech)
   * run immediately. Safe against races: stale watches (superseded record, or progress that
   * arrived first and disarmed us) are ignored via the instance check and the disarms above.
   */
  private void handleFailFast() {
    if (scrollActionRecord == null
        || scrollActionRecord.scrollInstanceId != failFastInstanceId) {
      return;
    }
    if (SystemClock.uptimeMillis() - lastScrollEventUptimeMs < FAIL_FAST_NO_PROGRESS_MS) {
      // Scroll activity happened since the action (just not for this record): the list is
      // alive, e.g. still settling from a previous swipe. Stand down and let the normal
      // timeout path handle it exactly as stock TalkBack would.
      LogUtils.d(TAG, "Fail-fast suppressed: scroll activity present, waiting out full timeout.");
      failFastInstanceId = UNKNOWN_SCROLL_INSTANCE_ID;
      return;
    }
    LogUtils.d(
        TAG,
        "Fail-fast: no scroll progress in %dms, failing early (full timeout skipped).",
        FAIL_FAST_NO_PROGRESS_MS);
    failFastInstanceId = UNKNOWN_SCROLL_INSTANCE_ID;
    handleAutoScrollFailed();
  }

  private void handleAutoScrollFailed() {
    if (scrollActionRecord == null) {
      return;
    }
    failFastHandler.removeMessages();
    failFastInstanceId = UNKNOWN_SCROLL_INSTANCE_ID;
    // Caches the failed auto-scroll record, which will be used at {@link
    // AutoScrollInterpreter#handleAutoScrollFailed()}.
    failedScrollActionRecord = scrollActionRecord;
    // Clear cached auto scroll record before invoking callback. REFERTO for detail.
    scrollActionRecord = null;

    pipelineReceiver.input(SyntheticEvent.Type.SCROLL_TIMEOUT);
  }

  private int createScrollInstanceId() {
    int scrollInstanceId = nextScrollInstanceId;
    nextScrollInstanceId++;
    if (nextScrollInstanceId < 0) {
      nextScrollInstanceId = 0;
    }
    return scrollInstanceId;
  }
}
