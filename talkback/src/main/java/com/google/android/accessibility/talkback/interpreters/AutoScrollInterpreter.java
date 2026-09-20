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

package com.google.android.accessibility.talkback.interpreters;

import static com.google.android.accessibility.talkback.Interpretation.ID.Value.SCROLL_CANCEL_TIMEOUT;
import static com.google.android.accessibility.talkback.actor.AutoScrollActor.UNKNOWN_SCROLL_INSTANCE_ID;
import static com.google.android.accessibility.talkback.interpreters.AutoScrollInterpreter.AutoScrollHandler.TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE;

import android.os.Looper;
import android.os.Message;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.search.SearchScreenOverlay;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchActor;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.ScrollActionRecord.AutoScrollSuccessChecker;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Auto-scroll event interpreter, sending interpretations to pipeline. */
public class AutoScrollInterpreter implements ScrollEventHandler {

  private static final String TAG = "AutoScrollInterpreter";

  private final AutoScrollHandler autoScrollHandler;

  private ActorState actorState;
  private long handledAutoScrollUptimeMs = 0;

  private Pipeline.InterpretationReceiver pipeline;
  private DirectionNavigationActor directionNavigationActor;
  private UniversalSearchActor universalSearchActor;

  /** Actor notified of every scroll event so its fail-fast watchdog sees all activity. */
  @Nullable private com.google.android.accessibility.talkback.actor.AutoScrollActor scroller;

  /** Lets the interpreter report scroll activity (matched or not) to the actor. */
  public void setAutoScrollActor(
      @Nullable com.google.android.accessibility.talkback.actor.AutoScrollActor scroller) {
    this.scroller = scroller;
  }

  public AutoScrollInterpreter() {
    autoScrollHandler = new AutoScrollHandler(this);
  }

  public void setDirectionNavigationActor(DirectionNavigationActor directionNavigationActor) {
    this.directionNavigationActor = directionNavigationActor;
  }

  public void setUniversalSearchActor(UniversalSearchActor universalSearchActor) {
    this.universalSearchActor = universalSearchActor;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipelineInterpretationReceiver(Pipeline.InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public void onScrollEvent(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {
    LogUtils.d(TAG, "onScrollEvent, event = %s", event);

    // Any scroll activity counts against fail-fast silence, even events that don't
    // match the current record (e.g. the user's own finger on a still-settling list).
    if (scroller != null) {
      scroller.notifyScrollEvent();
    }

    if ((interpretation.scrollInstanceId != UNKNOWN_SCROLL_INSTANCE_ID)
        && (autoScrollRecordId() == interpretation.scrollInstanceId)
        && (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)) {
      ScrollActionRecord record = getUnhandledAutoScrollRecord();
      record.refresh();

      // Cause AutoScrollActor.onScrollEvent() to cancel failure timeout.
      pipeline.input(eventId, event, new Interpretation.ID(SCROLL_CANCEL_TIMEOUT));

      // In P, scroll action is animated, and might trigger several scroll events. Since this
      // animation behavior is supported in AndroidX it's available for before P as well. For the
      // use case of scroll by gesture, we have to wait until the scroll action finishes before
      // searching for next node.
      autoScrollHandler.removeHandleAutoScrollSuccessMessages();

      AutoScrollSuccessChecker checker = record.getAutoScrollSuccessChecker();
      LogUtils.d(TAG, "onScrollEvent, checker = %s", checker);
      if (checker != null && checker.isAutoScrollSuccess(record.getScrolledNode(), event)) {
        autoScrollHandler.handleAutoScrollSuccess(
            eventId,
            AccessibilityEventUtils.getScrollDeltaX(event),
            AccessibilityEventUtils.getScrollDeltaY(event));
      } else {
        autoScrollHandler.delayHandleAutoScrollSuccess(
            eventId,
            AccessibilityEventUtils.getScrollDeltaX(event),
            AccessibilityEventUtils.getScrollDeltaY(event),
            TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE);
      }
    }
  }

  public void handleAutoScrollFailed() {
    @Nullable ScrollActionRecord record = getUnhandledAutoScrollFailRecord();
    if (record == null) {
      return;
    }

    handledAutoScrollUptimeMs = record.autoScrolledTime;

    record.refresh();
    if (record.scrollSource == ScrollActionRecord.FOCUS && record.scrolledNodeCompat != null) {
      // TODO: Use pipeline instead, after focus-interpreter moves to pipeline.
      directionNavigationActor.onAutoScrollFailed(record.scrolledNodeCompat);
    } else if (record.scrollSource == SearchScreenOverlay.SEARCH && record.scrolledNode != null) {
      universalSearchActor.onAutoScrollFailed(record.scrolledNode);
    }
  }

  @VisibleForTesting
  void handleAutoScrollSuccess(EventId eventId, int scrollDeltaX, int scrollDeltaY) {
    @Nullable ScrollActionRecord record = getUnhandledAutoScrollRecord();
    if (record == null) {
      return;
    }

    autoScrollHandler.removeHandleAutoScrollSuccessMessages();
    handledAutoScrollUptimeMs = record.autoScrolledTime;

    record.refresh();
    if (record.scrollSource == ScrollActionRecord.FOCUS && record.scrolledNodeCompat != null) {
      // TODO: Use pipeline instead, after focus-interpreter moves to pipeline.
      directionNavigationActor.onAutoScrolled(
          record.scrolledNodeCompat, eventId, scrollDeltaX, scrollDeltaY);
    } else if (record.scrollSource == SearchScreenOverlay.SEARCH && record.scrolledNode != null) {
      universalSearchActor.onAutoScrolled(record.scrolledNode, eventId);
    }
  }

  private int autoScrollRecordId() {
    @Nullable ScrollActionRecord record = getUnhandledAutoScrollRecord();
    return (record == null) ? UNKNOWN_SCROLL_INSTANCE_ID : record.scrollInstanceId;
  }

  /** Returns AutoScrollRecord from actor-state, only if that record has not yet been handled. */
  @Nullable
  private ScrollActionRecord getUnhandledAutoScrollRecord() {
    @Nullable ScrollActionRecord record = actorState.getScrollerState().get();
    if ((record == null) || (record.autoScrolledTime <= handledAutoScrollUptimeMs)) {
      return null;
    }
    return record;
  }

  /**
   * Returns AutoScrollRecord from actor-state for failed auto-scroll, only if the record hasn't
   * been handled yet.
   */
  @Nullable
  private ScrollActionRecord getUnhandledAutoScrollFailRecord() {
    @Nullable
    ScrollActionRecord record = actorState.getScrollerState().getFailedScrollActionRecord();
    if ((record == null) || (record.autoScrolledTime <= handledAutoScrollUptimeMs)) {
      return null;
    }
    return record;
  }

  /** Flush a pending delayed scroll-success so no swiped-to item is dropped silently. */
  public void flushPendingAutoScrollSuccess() {
    autoScrollHandler.flushPendingAutoScrollSuccess();
  }

  static class AutoScrollHandler extends WeakReferenceHandler<AutoScrollInterpreter> {

    // We set this delay time bigger than
    // ViewConfiguration#SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS (100ms) and less than
    // SUBTREE_CHANGED_DELAY_MS (150ms).
    // If it is less than SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS, we may not receive
    // the rest TYPE_VIEW_SCROLLED events in the same auto-scroll.
    // If it is bigger than SUBTREE_CHANGED_DELAY_MS, the ensuring method may already put a focus on
    // a node.
    static final int TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE = 110;
    private static final int MSG_HANDLE_AUTO_SCROLL_SUCCESS = 0;

    // Due to animation, these variables are used to accumulate the scroll deltas from several
    // scroll events. Then, we use these variables to represent a single scroll action.
    private int scrollDeltaSumX = 0;
    private int scrollDeltaSumY = 0;

    /** EventId of the currently delayed success, if any. */
    private @Nullable EventId pendingSuccessEventId = null;

    AutoScrollHandler(AutoScrollInterpreter autoScrollInterpreter) {
      super(autoScrollInterpreter, Looper.myLooper());
    }

    public void delayHandleAutoScrollSuccess(
        EventId eventId, int scrollDeltaX, int scrollDeltaY, long delay) {
      scrollDeltaSumX += scrollDeltaX;
      scrollDeltaSumY += scrollDeltaY;
      pendingSuccessEventId = eventId;

      Message message =
          obtainMessage(MSG_HANDLE_AUTO_SCROLL_SUCCESS, scrollDeltaSumX, scrollDeltaSumY, eventId);
      sendMessageDelayed(message, delay);
    }

    /**
     * Complete a pending delayed scroll-success right now instead of dropping it. A new
     * navigation while a scroll is settling (fast swiping) used to discard the in-flight item
     * silently — the delayed handler was reset before it fired, so the swiped-to item never
     * spoke and the user perceived a stall. Flushing speaks it immediately; the new navigation
     * then interrupts as usual, so every swipe is heard.
     */
    public void flushPendingAutoScrollSuccess() {
      if (!hasMessages(MSG_HANDLE_AUTO_SCROLL_SUCCESS)) {
        pendingSuccessEventId = null;
        return;
      }
      removeHandleAutoScrollSuccessMessages();
      EventId eventId = pendingSuccessEventId;
      pendingSuccessEventId = null;
      LogUtils.d(TAG, "Flushing pending auto-scroll success before new navigation.");
      handleAutoScrollSuccess(eventId, /* scrollDeltaX= */ 0, /* scrollDeltaY= */ 0);
    }

    /** Handles auto-scroll success immediately. */
    public void handleAutoScrollSuccess(EventId eventId, int scrollDeltaX, int scrollDeltaY) {
      LogUtils.d(TAG, "handleAutoScrollSuccess");
      AutoScrollInterpreter parent = getParent();
      scrollDeltaSumX += scrollDeltaX;
      scrollDeltaSumY += scrollDeltaY;
      if (parent != null) {
        parent.handleAutoScrollSuccess(eventId, scrollDeltaSumX, scrollDeltaSumY);
      }
      scrollDeltaSumX = 0;
      scrollDeltaSumY = 0;
    }

    public void removeHandleAutoScrollSuccessMessages() {
      removeMessages(MSG_HANDLE_AUTO_SCROLL_SUCCESS);
    }

    @Override
    protected void handleMessage(Message msg, AutoScrollInterpreter parent) {
      if (msg.what == MSG_HANDLE_AUTO_SCROLL_SUCCESS) {
        parent.handleAutoScrollSuccess(
            (EventId) msg.obj, /* scrollDeltaX= */ msg.arg1, /* scrollDeltaY= */ msg.arg2);
        scrollDeltaSumX = 0;
        scrollDeltaSumY = 0;
      }
    }
  }
}
