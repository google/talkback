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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.talkback.actor.AutoScrollActor.AutoScrollRecord;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchManager;
import com.google.android.accessibility.utils.DelayHandler;
import com.google.android.accessibility.utils.Performance.EventId;

/** Auto-scroll event interpreter, sending interpretations to pipeline. */
public class AutoScrollInterpreter implements ScrollEventHandler {

  private static final String TAG = "AutoScrollInterpreter";

  private static final int TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE = 150;

  private final DelayHandler<EventId> postDelayHandler;

  private ActorState actorState;
  private long handledAutoScrollUptimeMs = 0;

  private Pipeline.InterpretationReceiver pipeline;
  private DirectionNavigationActor directionNavigationActor;
  private UniversalSearchManager searchManager;

  public AutoScrollInterpreter() {
    postDelayHandler =
        new DelayHandler<EventId>() {
          @Override
          public void handle(EventId eventId) {
            handleAutoScrollSuccess(eventId);
          }
        };
  }

  public void setDirectionNavigationActor(DirectionNavigationActor directionNavigationActor) {
    this.directionNavigationActor = directionNavigationActor;
  }

  public void setSearchManager(UniversalSearchManager searchManager) {
    this.searchManager = searchManager;
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

      // Both TYPE_WINDOW_CONTENT_CHANGED and TYPE_VIEW_SCROLLED events could result from scroll
      // action. We react to the first TYPE_VIEW_SCROLLED event and then clear the record.
      if (interpretation.userAction == ScrollEventInterpreter.ACTION_SCROLL_SHORTCUT) {
        // In P, scroll action is animated, and might trigger several scroll events. Since this
        // animation behavior is supported in AndroidX it's available for before P as well. For the
        // use case of scroll by gesture, we have to wait until the scroll action finishes before
        // searching for next node.
        postDelayHandler.removeMessages();
        postDelayHandler.delay(TIMEOUT_MS_HANDLE_SCROLL_BY_GESTURE, eventId);
      } else {
        handleAutoScrollSuccess(eventId);
      }
    }
  }

  public void handleAutoScrollFailed() {
    @Nullable AutoScrollRecord record = getUnhandledAutoScrollFailRecord();
    if (record == null) {
      return;
    }

    handledAutoScrollUptimeMs = record.autoScrolledTime;

    record.refresh();
    if (record.scrollSource == AutoScrollRecord.Source.FOCUS && record.scrolledNodeCompat != null) {
      // TODO: Use pipeline instead, after focus-interpreter moves to pipeline.
      directionNavigationActor.onAutoScrollFailed(record.scrolledNodeCompat);
    } else if (record.scrollSource == AutoScrollRecord.Source.SEARCH
        && record.scrolledNode != null) {
      searchManager.onAutoScrollFailed(record.scrolledNode);
    }
  }

  @VisibleForTesting
  void handleAutoScrollSuccess(EventId eventId) {
    @Nullable AutoScrollRecord record = getUnhandledAutoScrollRecord();
    if (record == null) {
      return;
    }

    postDelayHandler.removeMessages();
    handledAutoScrollUptimeMs = record.autoScrolledTime;

    record.refresh();
    if (record.scrollSource == AutoScrollRecord.Source.FOCUS && record.scrolledNodeCompat != null) {
      // TODO: Use pipeline instead, after focus-interpreter moves to pipeline.
      directionNavigationActor.onAutoScrolled(record.scrolledNodeCompat, eventId);
    } else if (record.scrollSource == AutoScrollRecord.Source.SEARCH
        && record.scrolledNode != null) {
      searchManager.onAutoScrolled(record.scrolledNode, eventId);
    }
  }

  private int autoScrollRecordId() {
    @Nullable AutoScrollRecord record = getUnhandledAutoScrollRecord();
    return (record == null) ? UNKNOWN_SCROLL_INSTANCE_ID : record.scrollInstanceId;
  }

  /** Returns AutoScrollRecord from actor-state, only if that record has not yet been handled. */
  private @Nullable AutoScrollRecord getUnhandledAutoScrollRecord() {
    @Nullable AutoScrollRecord record = actorState.getScrollerState().getAutoScrollRecord();
    if ((record == null) || (record.autoScrolledTime <= handledAutoScrollUptimeMs)) {
      return null;
    }
    return record;
  }

  /**
   * Returns AutoScrollRecord from actor-state for failed auto-scroll, only if the record hasn't
   * been handled yet.
   */
  private @Nullable AutoScrollRecord getUnhandledAutoScrollFailRecord() {
    @Nullable AutoScrollRecord record = actorState.getScrollerState().getFailedAutoScrollRecord();
    if ((record == null) || (record.autoScrolledTime <= handledAutoScrollUptimeMs)) {
      return null;
    }
    return record;
  }
}
