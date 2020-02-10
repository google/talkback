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

package com.google.android.accessibility.talkback.focusmanagement;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.Pipeline.SyntheticEvent;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.UserAction;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.DelayHandler;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Scroll action performer.
 *
 * <p>It provides API {@link #scroll(int, AccessibilityNode, int, AutoScrollCallback, EventId)} to
 * perform auto-scroll action on a node, stores actor state to invoke when the result {@link
 * AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received, or timeout failure occurs.
 */
public class AutoScrollActor {

  private static final String TAG = "AutoScrollActor";

  public static final int UNKNOWN_SCROLL_INSTANCE_ID = -1;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Scroll record classes

  /**
   * Caches information of auto-scroll action, and used to match event to action when {@link
   * AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received.
   */
  public static class AutoScrollRecord {

    /** Types of scroll callers. */
    public static enum Source {
      FOCUS,
      SEARCH;
    }

    public final int scrollInstanceId;
    public @UserAction final int userAction;

    /**
     * During transition from AccessibilityNodeInfoCompat to AccessibilityNode, some callers provide
     * AccessibilityNode, others provide compat -- either works. AutoScrollRecord recyles node.
     */
    public final @Nullable AccessibilityNode scrolledNode;

    // TODO: Switch focus-management to use AccessibilityNode, and remove this
    // redundant field.
    public final @Nullable AccessibilityNodeInfoCompat scrolledNodeCompat;

    // SystemClock.uptimeMillis(), used to compare with AccessibilityEvent.getEventTime().
    public final long autoScrolledTime;
    public final AutoScrollRecord.Source scrollSource;

    /** Creates scroll-record, with copy of node. Caller keeps ownership of scrolledNode/Compat. */
    public AutoScrollRecord(
        int scrollInstanceId,
        @Nullable AccessibilityNode scrolledNode,
        @Nullable AccessibilityNodeInfoCompat scrolledNodeCompat,
        @UserAction int userAction,
        long autoScrolledTime,
        AutoScrollRecord.Source scrollSource) {
      this.scrollInstanceId = scrollInstanceId;
      this.userAction = userAction;
      this.scrolledNode = (scrolledNode == null) ? null : scrolledNode.obtainCopy();
      this.scrolledNodeCompat =
          (scrolledNodeCompat == null)
              ? null
              : AccessibilityNodeInfoUtils.obtain(scrolledNodeCompat);
      this.autoScrolledTime = autoScrolledTime;
      this.scrollSource = scrollSource;
    }

    /** Caller retains ownership of node argument. */
    public boolean scrolledNodeMatches(@Nullable AccessibilityNodeInfoCompat node) {
      if (node == null) {
        return false;
      }
      if (scrolledNodeCompat != null) {
        return scrolledNodeCompat.equals(node);
      } else if (scrolledNode != null) {
        return scrolledNode.equalTo(node);
      } else {
        return false;
      }
    }

    public void refresh() {
      if (scrolledNode != null) {
        scrolledNode.refresh();
      }
      if (scrolledNodeCompat != null) {
        scrolledNodeCompat.refresh();
      }
    }

    public void recycle(String caller) {
      AccessibilityNode.recycle(caller + " -> AutoScrollRecord.recycle()", scrolledNode);
      AccessibilityNodeInfoUtils.recycleNodes(scrolledNodeCompat);
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Read-only interface

  /** Limited read-only interface to pull state data. */
  public class StateReader {
    public AutoScrollRecord getAutoScrollRecord() {
      return AutoScrollActor.this.autoScrollRecord;
    }
  }

  /** Read-only interface for pulling state data. */
  public final StateReader stateReader = new StateReader();

  ///////////////////////////////////////////////////////////////////////////////////////
  // Member data and construction

  // TODO: If more actors require timeout failure delays... move timeout delay to
  // pipeline, with single delay-handler for all actors.
  private final DelayHandler<EventIdAnd<Boolean>> postDelayHandler;

  private Pipeline.EventReceiver pipeline;

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
  }

  @Nullable private AutoScrollRecord autoScrollRecord = null;

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipeline) {
    this.pipeline = pipeline;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Event handling methods

  public void cancelTimeout() {
    postDelayHandler.removeMessages();
  }

  /**
   * Performs scroll action at the given node. Invoke the callback when the result {@link
   * AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the node.
   *
   * @param userAction Source {@link UserAction} that leads to scroll action.
   * @param node Node to scroll
   * @param scrollAccessibilityAction Accessibility scroll action
   * @param callback Callback to be invoked when the result event is received.
   * @param eventId EventId for performance tracking.
   * @return {@code true} If the action is successfully performed.
   */
  public boolean scroll(
      @UserAction int userAction,
      @Nullable AccessibilityNode node,
      @Nullable AccessibilityNodeInfoCompat nodeCompat,
      int scrollAccessibilityAction,
      AutoScrollRecord.Source scrollSource,
      EventId eventId) {
    if (node == null && nodeCompat == null) {
      return false;
    }
    long currentTime = SystemClock.uptimeMillis();
    boolean result =
        ((node != null) && node.performAction(scrollAccessibilityAction, eventId))
            || ((nodeCompat != null)
                && PerformActionUtils.performAction(
                    nodeCompat, scrollAccessibilityAction, eventId));
    if (result) {
      final int scrollInstanceId = createScrollInstanceId();
      setAutoScrollRecord(
          new AutoScrollRecord(
              scrollInstanceId, node, nodeCompat, userAction, currentTime, scrollSource));

      postDelayHandler.removeMessages();
      postDelayHandler.delay(
          ScrollEventInterpreter.SCROLL_TIMEOUT_MS,
          /* handlerArg= */ new EventIdAnd<>(false, null));
    }
    LogUtils.d(
        TAG,
        "Perform scroll action:result=%s\nnode=%s\nnodeCompat=%s\nScrollAction=%s\nUserAction=%s",
        result,
        node,
        nodeCompat,
        AccessibilityNodeInfoUtils.actionToString(scrollAccessibilityAction),
        ScrollEventInterpreter.userActionToString(userAction));
    return result;
  }

  private void setAutoScrollRecord(AutoScrollRecord newRecord) {
    if (autoScrollRecord != null) {
      autoScrollRecord.recycle("AutoScrollActor.setAutoScrollRecord()");
    }
    autoScrollRecord = newRecord;
  }

  private void handleAutoScrollFailed() {
    if (autoScrollRecord == null) {
      return;
    }

    AutoScrollRecord record = autoScrollRecord;
    // Clear cached auto scroll record before invoking callback.
    autoScrollRecord = null;

    try {
      pipeline.input(SyntheticEvent.Type.SCROLL_TIMEOUT);
    } finally {
      record.recycle("AutoScrollActor.handleAutoScrollFailed()");
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  public int createScrollInstanceId() {
    int scrollInstanceId = nextScrollInstanceId;
    nextScrollInstanceId++;
    if (nextScrollInstanceId < 0) {
      nextScrollInstanceId = 0;
    }
    return scrollInstanceId;
  }
}
