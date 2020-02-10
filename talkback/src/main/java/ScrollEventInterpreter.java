/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.utils.AccessibilityEventUtils.DELTA_UNDEFINED;

import android.annotation.TargetApi;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.focusmanagement.AutoScrollActor.AutoScrollRecord;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.AutoScrollInterpreter;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interprets {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} and {@link
 * AccessibilityEvent#TYPE_WINDOW_CONTENT_CHANGED} with scroll position information.
 *
 * <p><b>Usage: Register as a {@link ScrollEventHandler} to listen to interpreted scroll events.</b>
 */
public class ScrollEventInterpreter implements AccessibilityEventListener {
  public static final int ACTION_UNKNOWN = 0;
  public static final int ACTION_AUTO_SCROLL = 1;
  public static final int ACTION_SCROLL_SHORTCUT = 2;
  public static final int ACTION_MANUAL_SCROLL = 3;

  /** Source action types that result in scroll events. */
  @IntDef({ACTION_UNKNOWN, ACTION_AUTO_SCROLL, ACTION_SCROLL_SHORTCUT, ACTION_MANUAL_SCROLL})
  @Retention(RetentionPolicy.SOURCE)
  public @interface UserAction {}

  public static String userActionToString(@UserAction int action) {
    switch (action) {
      case ACTION_AUTO_SCROLL:
        return "ACTION_AUTO_SCROLL";
      case ACTION_SCROLL_SHORTCUT:
        return "ACTION_SCROLL_SHORTCUT";
      case ACTION_MANUAL_SCROLL:
        return "ACTION_MANUAL_SCROLL";
      default:
        return "ACTION_UNKNOWN";
    }
  }

  /** Contains interpreted information in addition to {@link AccessibilityEvent}. */
  public static class ScrollEventInterpretation {

    public static final ScrollEventInterpretation DEFAULT_INTERPRETATION =
        new ScrollEventInterpretation(
            ACTION_UNKNOWN,
            TraversalStrategy.SEARCH_FOCUS_UNKNOWN,
            /* hasValidIndex= */ false,
            /* isDuplicateEvent= */ false,
            SCROLL_INSTANCE_ID_UNDEFINED);

    /** Source {@link UserAction} that leads to the scroll event. */
    public final @UserAction int userAction;

    public final @SearchDirectionOrUnknown int scrollDirection;

    /**
     * Sets to {@code true} if the event has valid AdapterView index(fromIndex, toIndex) or valid
     * ScrollView index(scrollX, scrollY).
     */
    public final boolean hasValidIndex;

    /** Sets to {@code true} if the event reports the same scroll position as the previous event. */
    public final boolean isDuplicateEvent;

    /**
     * Created at {@link #onScrollActionCompat(int, AccessibilityNodeInfoCompat, long)} to identify
     * auto scroll action.
     */
    public final int scrollInstanceId;

    @VisibleForTesting
    public ScrollEventInterpretation(
        @UserAction int userAction,
        @SearchDirectionOrUnknown int scrollDirection,
        boolean hasValidIndex,
        boolean isDuplicateEvent,
        int scrollInstanceId) {
      this.userAction = userAction;
      this.scrollDirection = scrollDirection;
      this.hasValidIndex = hasValidIndex;
      this.isDuplicateEvent = isDuplicateEvent;
      this.scrollInstanceId = scrollInstanceId;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ScrollEventInterpretation{");
      sb.append("userAction=").append(userActionToString(userAction));
      sb.append(", scrollDirection=")
          .append(TraversalStrategyUtils.directionToString(scrollDirection));
      sb.append(", hasValidIndex=").append(hasValidIndex);
      sb.append(", isDuplicateEvent=").append(isDuplicateEvent);
      sb.append(", scrollInstanceId=").append(scrollInstanceId);
      sb.append('}');
      return sb.toString();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ScrollEventInterpretation)) {
        return false;
      }

      ScrollEventInterpretation that = (ScrollEventInterpretation) o;

      return (userAction == that.userAction)
          && (scrollDirection == that.scrollDirection)
          && (hasValidIndex == that.hasValidIndex)
          && (isDuplicateEvent == that.isDuplicateEvent)
          && (scrollInstanceId == that.scrollInstanceId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          userAction, scrollDirection, hasValidIndex, isDuplicateEvent, scrollInstanceId);
    }
  }

  /**
   * Listens to interpreted {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} and {@link
   * AccessibilityEvent#TYPE_WINDOW_CONTENT_CHANGED} with scroll position information.
   */
  public interface ScrollEventHandler {
    void onScrollEvent(
        AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId);
  }

  /** Timeout to determine whether a scroll event could be resulted from the last scroll action. */
  public static final int SCROLL_TIMEOUT_MS = 500;

  /** Undefined scroll position index. */
  private static final int INDEX_UNDEFINED = -1;

  /** Undefined auto-scroll action scrollInstanceId. */
  private static final int SCROLL_INSTANCE_ID_UNDEFINED = -1;

  /** Event types that are handled by ScrollEventInterpreter. */
  private static final int MASK_EVENTS =
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

  /** Maps from scrollable node id to scroll position information. */
  private final HashMap<NodeIdentifier, PositionInfo> cachedPositionInfo = new HashMap<>();

  private final List<ScrollEventHandler> listeners = new ArrayList<>();

  private final ActorState actorState;

  public ScrollEventInterpreter(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setAutoScrollInterpreter(AutoScrollInterpreter autoScrollInterpreter) {
    addListener(autoScrollInterpreter);
  }

  public void addListener(ScrollEventHandler listener) {
    listeners.add(listener);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        // Window state changes clear the cache.
        cachedPositionInfo.clear();
        break;
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        if (((event.getContentChangeTypes() & AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) == 0)
            || !hasValidIndex(event)) {
          break;
        }
        // fall through
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        ScrollEventInterpretation interpretation = interpret(event);

        if (interpretation.hasValidIndex && !interpretation.isDuplicateEvent) {
          cacheScrollPositionInfo(event);
        }

        notifyListenersWithInterpretation(event, interpretation, eventId);
        break;
      default:
        break;
    }
  }

  @VisibleForTesting
  void notifyListenersWithInterpretation(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {
    for (ScrollEventHandler listener : listeners) {
      listener.onScrollEvent(event, interpretation, eventId);
    }
  }

  private ScrollEventInterpretation interpret(AccessibilityEvent event) {
    AccessibilityNodeInfo sourceNode = event.getSource();
    if (sourceNode == null) {
      return ScrollEventInterpretation.DEFAULT_INTERPRETATION;
    }

    // . We get scroll position WINDOW_CONTENT_CHANGED events to provide more fine-grained
    // scroll action detection. We need to carefully handle these events. Event if the position
    // changes, it might not result from scroll action. We need a solid strategy to protect against
    // this issue. It's difficult. The least effort we can do is to filter out non-scrollable node.
    if ((event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        && !AccessibilityNodeInfoUtils.isScrollable(AccessibilityNodeInfoCompat.wrap(sourceNode))) {
      return ScrollEventInterpretation.DEFAULT_INTERPRETATION;
    }

    final NodeIdentifier sourceNodeIdentifier = new NodeIdentifier(sourceNode);
    AccessibilityNodeInfoUtils.recycleNodes(sourceNode);

    @SearchDirectionOrUnknown
    final int scrollDirection = getScrollDirection(sourceNodeIdentifier, event);
    @UserAction final int userAction;
    final int scrollInstanceId;

    @Nullable AutoScrollRecord autoScrollRecord = isFromAutoScrollAction(event);
    if (autoScrollRecord == null) {
      scrollInstanceId = SCROLL_INSTANCE_ID_UNDEFINED;
      // Note that TYPE_WINDOW_CONTENT_CHANGED events can also be interpreted as manual scroll
      // action. TYPE_VIEW_SCROLLED events are filed at very coarse granularity. If we rely only on
      // TYPE_VIEW_SCROLLED events to detect manual scroll action, it happens when the user slightly
      // scroll a list and accessibility focus goes off screen, we don't receive
      // TYPE_VIEW_SCROLLED event thus we don't update accessibility focus. If user performs linear
      // navigation after that, accessibility focus might go to the beginning of screen.
      // We take into account TYPE_WINDOW_CONTENT_CHANGED events to provide more
      // fine-grained manual scroll callback.
      userAction =
          scrollDirection == TraversalStrategy.SEARCH_FOCUS_UNKNOWN
              ? ACTION_UNKNOWN
              : ACTION_MANUAL_SCROLL;
    } else {
      scrollInstanceId = autoScrollRecord.scrollInstanceId;
      userAction = autoScrollRecord.userAction;
    }

    final boolean hasValidIndex = hasValidIndex(event);
    final boolean isDuplicateEvent = hasValidIndex && isDuplicateEvent(sourceNodeIdentifier, event);

    return new ScrollEventInterpretation(
        userAction, scrollDirection, hasValidIndex, isDuplicateEvent, scrollInstanceId);
  }

  /** Returns AutoScrollRecord if the {@code event} is resulted from cached auto-scroll action. */
  private @Nullable AutoScrollRecord isFromAutoScrollAction(AccessibilityEvent event) {
    @Nullable
    AutoScrollRecord autoScrollRecord = actorState.getScrollerState().getAutoScrollRecord();
    if (autoScrollRecord == null) {
      return null;
    }

    // Note that both TYPE_VIEW_SCROLLED and TYPE_WINDOW_CONTENT_CHANGED events could result from
    // auto-scroll action. And a single scroll action can trigger multiple scroll events, we'll keep
    // the autoScrollRecord until next auto scroll happened and use the time diff to distinguish if
    // the current scroll is from the same scroll action.
    long timeDiff = event.getEventTime() - autoScrollRecord.autoScrolledTime;
    if ((timeDiff < 0L) || (timeDiff > SCROLL_TIMEOUT_MS)) {
      return null;
    }

    AccessibilityNodeInfoCompat node = null;
    try {
      node = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      return autoScrollRecord.scrolledNodeMatches(node) ? autoScrollRecord : null;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  @SearchDirectionOrUnknown
  private int getScrollDirection(NodeIdentifier sourceNodeIdentifier, AccessibilityEvent event) {
    PositionInfo positionInfo = cachedPositionInfo.get(sourceNodeIdentifier);
    if (positionInfo == null) {
      if (BuildVersionUtils.isAtLeastR()) {
        return getScrollDirectionFromDeltas(event);
      }
      return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
    }

    // Check scroll of AdapterViews
    if (event.getFromIndex() > positionInfo.fromIndex
        || event.getToIndex() > positionInfo.toIndex) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getFromIndex() < positionInfo.fromIndex
        || event.getToIndex() < positionInfo.toIndex) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    // Check scroll of ScrollViews.
    if (event.getScrollX() > positionInfo.scrollX || event.getScrollY() > positionInfo.scrollY) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getScrollX() < positionInfo.scrollX
        || event.getScrollY() < positionInfo.scrollY) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    if (BuildVersionUtils.isAtLeastR()) {
      return getScrollDirectionFromDeltas(event);
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  @TargetApi(BuildVersionUtils.API_R)
  @SearchDirectionOrUnknown
  private int getScrollDirectionFromDeltas(AccessibilityEvent event) {
    int scrollDeltaX = event.getScrollDeltaX();
    int scrollDeltaY = event.getScrollDeltaY();
    if (scrollDeltaX == DELTA_UNDEFINED && scrollDeltaY == DELTA_UNDEFINED) {
      return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
    }
    if (scrollDeltaX > 0 || scrollDeltaY > 0) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (scrollDeltaX < 0 || scrollDeltaY < 0) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }
    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  private boolean isDuplicateEvent(NodeIdentifier sourceNodeIdentifier, AccessibilityEvent event) {
    final PositionInfo positionInfo = cachedPositionInfo.get(sourceNodeIdentifier);
    return (positionInfo != null) && positionInfo.equals(new PositionInfo(event));
  }

  private boolean hasValidIndex(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        return (event.getFromIndex() != INDEX_UNDEFINED)
            && (event.getToIndex() != INDEX_UNDEFINED)
            && (event.getItemCount() > 0);
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        boolean validScrollDelta = AccessibilityEventUtils.hasValidScrollDelta(event);
        return (event.getFromIndex() != INDEX_UNDEFINED)
            || (event.getToIndex() != INDEX_UNDEFINED)
            || (event.getScrollX() != INDEX_UNDEFINED)
            || (event.getScrollY() != INDEX_UNDEFINED)
            || validScrollDelta;
      default:
        return false;
    }
  }

  private void cacheScrollPositionInfo(AccessibilityEvent event) {
    AccessibilityNodeInfo sourceNode = event.getSource();
    if (sourceNode == null) {
      return;
    }

    cachedPositionInfo.put(new NodeIdentifier(sourceNode), new PositionInfo(event));
    AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
  }

  /** Caches scroll position from {@link AccessibilityEvent}. */
  private static class PositionInfo {
    private final int fromIndex;
    private final int toIndex;
    private final int scrollX;
    private final int scrollY;
    private final int itemCount;
    private final int scrollDeltaX;
    private final int scrollDeltaY;

    PositionInfo(AccessibilityEvent event) {
      fromIndex = event.getFromIndex();
      toIndex = event.getToIndex();
      scrollX = event.getScrollX();
      scrollY = event.getScrollY();
      itemCount = event.getItemCount();
      scrollDeltaX = AccessibilityEventUtils.getScrollDeltaX(event);
      scrollDeltaY = AccessibilityEventUtils.getScrollDeltaY(event);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PositionInfo)) {
        return false;
      }

      PositionInfo that = (PositionInfo) o;

      return (fromIndex == that.fromIndex)
          && (toIndex == that.toIndex)
          && (scrollX == that.scrollX)
          && (scrollY == that.scrollY)
          && (itemCount == that.itemCount)
          && (scrollDeltaY == that.scrollDeltaY)
          && (scrollDeltaX == that.scrollDeltaX);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          fromIndex, toIndex, scrollX, scrollY, itemCount, scrollDeltaX, scrollDeltaY);
    }
  }

  /** Light-weight identifier for {@link AccessibilityNodeInfo}. */
  private static class NodeIdentifier {
    private final int nodeHashCode;
    private final int windowId;

    NodeIdentifier(AccessibilityNodeInfo node) {
      nodeHashCode = node.hashCode();
      windowId = node.getWindowId();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof NodeIdentifier)) {
        return false;
      }

      NodeIdentifier that = (NodeIdentifier) o;

      return (nodeHashCode == that.nodeHashCode) && (windowId == that.windowId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeHashCode, windowId);
    }
  }
}
