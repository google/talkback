/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.os.Message;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.input.InputModeManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Interprets {@link AccessibilityEvent} for touch exploration actions.
 *
 * <p>This class handles the events during touch exploration, and works around some unexpected
 * events:
 *
 * <ul>
 *   <li>Filters duplicated {@link AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events from a single
 *       hover enter action.
 *   <li>Filters unexpected {@link AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events from container
 *       node when touching on a child node.
 * </ul>
 */
public class TouchExplorationInterpreter implements AccessibilityEventListener {

  /** Listens to {@link TouchExplorationAction}. */
  public interface TouchExplorationActionListener {

    /**
     * Callback when the user performs a touch exploration action.
     *
     * <p><strong>Note:</strong>Do not recycle {@link TouchExplorationAction#touchedFocusableNode}
     * in callback method.
     *
     * @return {@code true} if any accessibility action is successfully performed.
     */
    boolean onTouchExplorationAction(TouchExplorationAction action, EventId eventId);
  }

  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER;

  private final InputModeManager inputModeManager;
  private final PostDelayHandler postDelayHandler;

  private AccessibilityNodeInfoCompat lastTouchedNode;

  private final List<TouchExplorationActionListener> listeners = new ArrayList<>();

  public TouchExplorationInterpreter(InputModeManager inputModeManager) {
    this.inputModeManager = inputModeManager;
    postDelayHandler = new PostDelayHandler(this);
  }

  public void addTouchExplorationActionListener(TouchExplorationActionListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener must not be null.");
    }
    listeners.add(listener);
  }

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    final boolean result;
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        result = handleTouchInteractionStartEvent(eventId);
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        // . In some case, framework sends a Hover_Enter event enter after
        // User_Interaction_end event. Defer the action for workaround.
        result = false;
        postDelayHandler.postDelayTouchEndAction(eventId);
        break;
      default:
        result = handleHoverEnterEvent(event, eventId);
        break;
    }
    if (result) {
      setInputTouchMode();
    }
  }

  /** @return {@code true} if any accessibility action is successfully performed. */
  private boolean handleTouchInteractionStartEvent(EventId eventId) {
    postDelayHandler.executePendingTouchEndAction();
    setLastTouchedNode(/*touchedNode= */ null);
    postDelayHandler.cancelPendingEmptyTouchAction(/* dispatchPendingActionImmediately= */ false);
    return dispatchAndRecycleTouchExplorationAction(
        new TouchExplorationAction(
            TouchExplorationAction.TOUCH_INTERACTION_START, /* touchedFocusableNode= */ null),
        eventId);
  }

  /** @return {@code true} if any accessibility action is successfully performed. */
  private boolean handleTouchInteractionEndEvent(EventId eventId) {
    setLastTouchedNode(/*touchedNode= */ null);
    // Dispatch pending empty touch action immediately.
    postDelayHandler.cancelPendingEmptyTouchAction(/* dispatchPendingActionImmediately= */ true);
    return dispatchAndRecycleTouchExplorationAction(
        new TouchExplorationAction(
            TouchExplorationAction.TOUCH_INTERACTION_END, /* touchedFocusableNode= */ null),
        eventId);
  }

  /** @return {@code true} if any accessibility action is successfully performed. */
  private boolean handleHoverEnterEvent(AccessibilityEvent event, EventId eventId) {
    final AccessibilityNodeInfoCompat touchedNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (touchedNode == null) {
      // Invalid event.
      return false;
    }
    if (touchedNode.equals(lastTouchedNode)) {
      // If two consecutive Hover_Enter events have the same source node, we won't dispatch it
      // because it doesn't change anything.
      AccessibilityNodeInfoUtils.recycleNodes(touchedNode);
      return false;
    }

    setLastTouchedNode(touchedNode);

    final AccessibilityNodeInfoCompat touchedFocusableNode =
        AccessibilityNodeInfoUtils.findFocusFromHover(touchedNode);

    AccessibilityNodeInfoUtils.recycleNodes(touchedNode);
    if (touchedFocusableNode == null) {
      // . If there is no focusable node being touched, we don't dispatch empty touch
      // event immediately. Instead, we post delay to dispatch it. If we receive hover enter event
      // from other node before timeout, we cancel this post-delayed action.
      postDelayHandler.postDelayEmptyTouchAction(eventId);
    } else {
      postDelayHandler.cancelPendingEmptyTouchAction(/* dispatchPendingActionImmediately= */ false);
      return dispatchAndRecycleTouchExplorationAction(
          new TouchExplorationAction(TouchExplorationAction.HOVER_ENTER, touchedFocusableNode),
          eventId);
    }
    return false;
  }

  /** @return {@code true} if any accessibility action is successfully performed. */
  private boolean dispatchAndRecycleTouchExplorationAction(
      TouchExplorationAction action, EventId eventId) {
    try {
      boolean result = false;
      for (TouchExplorationActionListener listener : listeners) {
        result |= listener.onTouchExplorationAction(action, eventId);
      }
      return result;
    } finally {
      action.recycle();
    }
  }

  private void setInputTouchMode() {
    inputModeManager.setInputMode(InputModeManager.INPUT_MODE_TOUCH);
  }

  /**
   * Saves the last touched node.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the touchedNode.
   */
  private void setLastTouchedNode(@Nullable AccessibilityNodeInfoCompat touchedNode) {
    AccessibilityNodeInfoUtils.recycleNodes(lastTouchedNode);
    lastTouchedNode = AccessibilityNodeInfoUtils.obtain(touchedNode);
  }

  /**
   * A {@link WeakReferenceHandler} to post delay dispatching empty touch action.
   *
   * <p>An empty touch action is defined as hovering enter non accessibility focusable node.
   */
  private static final class PostDelayHandler
      extends WeakReferenceHandler<TouchExplorationInterpreter> {
    private static final int EMPTY_TOUCH_AREA_DELAY_MS = 100;
    private static final int TOUCH_END_DELAY_MS = 50;

    private static final int MSG_EMPTY_TOUCH_ACTION = 0;
    private static final int MSG_TOUCH_END_ACTION = 1;
    /**
     * A temp variable to save {@link EventId} of pending {@link
     * TouchExplorationAction#TOUCH_INTERACTION_END}
     */
    private @Nullable EventId touchEndEventId = null;

    PostDelayHandler(TouchExplorationInterpreter parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, TouchExplorationInterpreter parent) {
      if (msg.what == MSG_EMPTY_TOUCH_ACTION) {
        parent.dispatchAndRecycleTouchExplorationAction(
            new TouchExplorationAction(
                TouchExplorationAction.HOVER_ENTER, /* touchedFocusableNode= */ null),
            (EventId) msg.obj);
      } else if (msg.what == MSG_TOUCH_END_ACTION) {
        handleTouchEndAction();
      }
    }

    void postDelayEmptyTouchAction(EventId eventId) {
      sendMessageDelayed(obtainMessage(MSG_EMPTY_TOUCH_ACTION, eventId), EMPTY_TOUCH_AREA_DELAY_MS);
    }

    void cancelPendingEmptyTouchAction(boolean dispatchPendingActionImmediately) {
      boolean shouldDispatchEmptyTouchAction =
          dispatchPendingActionImmediately && hasMessages(MSG_EMPTY_TOUCH_ACTION);
      removeMessages(MSG_EMPTY_TOUCH_ACTION);
      if (shouldDispatchEmptyTouchAction) {
        getParent()
            .dispatchAndRecycleTouchExplorationAction(
                new TouchExplorationAction(
                    TouchExplorationAction.HOVER_ENTER, /* touchedFocusableNode= */ null),
                /* eventId= */ null);
      }
    }

    void postDelayTouchEndAction(EventId eventId) {
      touchEndEventId = eventId;
      sendMessageDelayed(obtainMessage(MSG_TOUCH_END_ACTION), TOUCH_END_DELAY_MS);
    }

    void executePendingTouchEndAction() {
      if (hasMessages(MSG_TOUCH_END_ACTION)) {
        removeMessages(MSG_TOUCH_END_ACTION);
        handleTouchEndAction();
      }
    }

    private void handleTouchEndAction() {
      TouchExplorationInterpreter parent = getParent();
      if (parent == null || touchEndEventId == null) {
        return;
      }

      boolean handled = parent.handleTouchInteractionEndEvent((touchEndEventId));
      if (handled) {
        parent.setInputTouchMode();
      }
      touchEndEventId = null;
    }
  }
}
