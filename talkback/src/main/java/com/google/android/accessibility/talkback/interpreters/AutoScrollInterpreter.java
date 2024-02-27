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

/** Auto-scroll event interpreter, sending interpretations to pipeline. */
public class AutoScrollInterpreter implements ScrollEventHandler {

  private static final String TAG = "AutoScrollInterpreter";

  private final AutoScrollHandler autoScrollHandler;

  private ActorState actorState;
  private long handledAutoScrollUptimeMs = 0;

  private Pipeline.InterpretationReceiver pipeline;
  private DirectionNavigationActor directionNavigationActor;
  private UniversalSearchActor universalSearchActor;

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

    if ((autoScrollRecordId() == interpretation.scrollInstanceId)
        && (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)) {

      // Cause AutoScrollActor.onScrollEvent() to cancel failure timeout.
      pipeline.input(eventId, event, new Interpretation.ID(SCROLL_CANCEL_TIMEOUT));

      // In P, scroll action is animated, and might trigger several scroll events. Since this
      // animation behavior is supported in AndroidX it's available for before P as well. For the
      // use case of scroll by gesture, we have to wait until the scroll action finishes before
      // searching for next node.
      autoScrollHandler.removeHandleAutoScrollSuccessMessages();
      autoScrollHandler.delayHandleAutoScrollSuccess(
          eventId,
          AccessibilityEventUtils.getScrollDeltaX(event),
          AccessibilityEventUtils.getScrollDeltaY(event));
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

  private static class AutoScrollHandler extends WeakReferenceHandler<AutoScrollInterpreter> {

    // We set this delay time bigger than
    // ViewConfiguration#SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS (100ms) and less than
    // SUBTREE_CHANGED_DELAY_MS (150ms).
    // If it is less than SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS, we may not receive
    // the rest TYPE_VIEW_SCROLLED events in the same auto-scroll.
    // If it is bigger than SUBTREE_CHANGED_DELAY_MS, the ensuring method may already put a focus on
    // a node.
    private static final int TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE = 110;
    private static final int MSG_HANDLE_AUTO_SCROLL_SUCCESS = 0;

    // Due to animation, these variables are used to accumulate the scroll deltas from several
    // scroll events. Then, we use these variables to represent a single scroll action.
    private int scrollDeltaSumX = 0;
    private int scrollDeltaSumY = 0;

    AutoScrollHandler(AutoScrollInterpreter autoScrollInterpreter) {
      super(autoScrollInterpreter, Looper.myLooper());
    }

    public void delayHandleAutoScrollSuccess(EventId eventId, int scrollDeltaX, int scrollDeltaY) {
      scrollDeltaSumX += scrollDeltaX;
      scrollDeltaSumY += scrollDeltaY;

      Message message =
          obtainMessage(MSG_HANDLE_AUTO_SCROLL_SUCCESS, scrollDeltaSumX, scrollDeltaSumY, eventId);
      sendMessageDelayed(message, TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE);
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
