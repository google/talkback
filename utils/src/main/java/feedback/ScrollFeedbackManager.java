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

package com.google.android.accessibility.utils.feedback;

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FailoverTextToSpeech.SpeechParam;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages scroll position feedback. If a VIEW_SCROLLED event passes through this processor and no
 * further events are received for a specified duration, a "scroll position" message is spoken.
 */
public class ScrollFeedbackManager implements AccessibilityEventListener {

  private static final String TAG = "ScrollFeedbackManager";

  /** Default pitch adjustment for text event feedback. */
  private static final float DEFAULT_PITCH = 1.2f;

  /** Default rate adjustment for text event feedback. */
  private static final float DEFAULT_RATE = 1.0f;

  /** Delay before reading a scroll position notification. */
  @VisibleForTesting public static final long DELAY_SCROLL_FEEDBACK = 1000;

  /** Delay before reading a page position notification. */
  @VisibleForTesting public static final long DELAY_PAGE_FEEDBACK = 500;

  /** Event types that are handled by ProcessorScrollPosition. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_SCROLL_POSITION =
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;

  private final HashMap<EventId, Integer> cachedFromValues = new HashMap<>();
  private final HashMap<EventId, Integer> cachedItemCounts = new HashMap<>();
  private final Bundle speechParams = new Bundle();
  private final ScrollPositionHandler handler = new ScrollPositionHandler(this);

  private final Context context;
  private final SpeechController speechController;

  public ScrollFeedbackManager(SpeechController speechController, Context context) {
    if (speechController == null) {
      throw new IllegalStateException();
    }
    this.context = context;
    this.speechController = speechController;
    speechParams.putFloat(SpeechParam.PITCH, DEFAULT_PITCH);
    speechParams.putFloat(SpeechParam.RATE, DEFAULT_RATE);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_SCROLL_POSITION;
  }

  @Override
  public void onAccessibilityEvent(
      AccessibilityEvent event, Performance.@Nullable EventId eventId) {
    if (shouldIgnoreEvent(event)) {
      return;
    }

    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        // Window state changes clear the cache.
        cachedFromValues.clear();
        cachedItemCounts.clear();
        handler.cancelScrollFeedback();
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        handler.postScrollFeedback(event, eventId);
        break;
      default: // fall out
    }
  }

  private boolean shouldIgnoreEvent(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
      case AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
        return true;
      case AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED:
      case AccessibilityEventCompat.TYPE_VIEW_SCROLLED:
        return shouldIgnoreWindowContentChangedOrViewScrolledEvent(event);
      default:
        return false;
    }
  }

  /**
   * Returns whether a WINDOW_CONTENT_CHANGED or VIEW_SCROLLED event should be ignored when
   * generating scroll position feedback.
   *
   * @param event The event from which information about the scroll position will be retrieved
   * @return {@code true} if the event should be ignored
   */
  protected boolean shouldIgnoreWindowContentChangedOrViewScrolledEvent(AccessibilityEvent event) {
    return isDuplicateScrollEventOrAutoScroll(event);
  }

  /**
   * Returns whether the event is a duplicate of the previous event, or the event is triggered by
   * auto-scroll.
   *
   * @param event The event from which information about the scroll position will be retrieved
   * @return {@code true} if the event is a duplicate of the previous event, or triggered by
   *     auto-scroll
   */
  protected boolean isDuplicateScrollEventOrAutoScroll(AccessibilityEvent event) {
    int eventType = event.getEventType();
    if ((eventType != AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED)
        && (eventType != AccessibilityEventCompat.TYPE_VIEW_SCROLLED)) {
      return false;
    }

    final int fromIndex = event.getFromIndex() + 1;
    final int itemCount = event.getItemCount();
    if (itemCount <= 0 || fromIndex <= 0) {
      return true;
    }

    EventId eventId;
    try {
      eventId = new EventId(event);
    } catch (Exception e) {
      return true;
    }

    final Integer cachedFromIndex = cachedFromValues.get(eventId);
    final Integer cachedItemCount = cachedItemCounts.get(eventId);

    if ((cachedFromIndex != null)
        && (cachedFromIndex == fromIndex)
        && (cachedItemCount != null)
        && (cachedItemCount == itemCount)) {
      // The from index hasn't changed, which means the event is coming
      // from a re-layout or resize and should not be spoken.
      return true;
    }

    // The behavior of put() for an existing key is unspecified, so we can't
    // recycle the old or new key nodes.
    cachedFromValues.put(eventId, fromIndex);
    cachedItemCounts.put(eventId, itemCount);

    return false;
  }

  /**
   * Given an {@link AccessibilityEvent}, speaks a scroll position.
   *
   * @param event The source event.
   */
  private void handleScrollFeedback(AccessibilityEvent event, Performance.EventId eventId) {
    final CharSequence text;
    final int flags;
    AccessibilityNodeInfo source = event.getSource();

    boolean isVisibleToUser = source != null && source.isVisibleToUser();

    if (Role.getRole(source) == Role.ROLE_PAGER) {
      text = getDescriptionForPageEvent(event, source);
      flags = FeedbackItem.FLAG_FORCED_FEEDBACK;
    } else {
      text = getDescriptionForScrollEvent(event);
      flags = 0;
    }

    if (source != null) {
      source.recycle();
    }

    if (TextUtils.isEmpty(text)) {
      return;
    }

    // don't pronounce non-visible nodes
    if (!isVisibleToUser) {
      return;
    }

    // Use QUEUE mode so that we don't interrupt more important messages.
    speechController.speak(
        text, /* Text */
        SpeechController.QUEUE_MODE_QUEUE, /* QueueMode */
        flags, /* Flags */
        speechParams, /* SpeechParams */
        eventId);
  }

  private CharSequence getDescriptionForScrollEvent(AccessibilityEvent event) {
    // If the from index or item count are invalid, don't announce anything.
    final int fromIndex = (event.getFromIndex() + 1);
    final int itemCount = event.getItemCount();
    if ((fromIndex <= 0) || (itemCount <= 0)) {
      return null;
    }

    // If the to and from indices are the same, or if the to index is
    // invalid, only announce the item at the from index.
    final int toIndex = event.getToIndex() + 1;
    if ((fromIndex == toIndex) || (toIndex <= 0) || (toIndex > itemCount)) {
      return context.getString(R.string.template_scroll_from_count, fromIndex, itemCount);
    }

    // Announce the range of visible items.
    return context.getString(R.string.template_scroll_from_to_count, fromIndex, toIndex, itemCount);
  }

  private CharSequence getDescriptionForPageEvent(
      AccessibilityEvent event, AccessibilityNodeInfo source) {
    final int fromIndex = (event.getFromIndex() + 1);
    final int itemCount = event.getItemCount();
    if ((fromIndex <= 0) || (itemCount <= 0)) {
      return null;
    }

    CharSequence pageTitle = getSelectedPageTitle(source);
    if (!TextUtils.isEmpty(pageTitle)) {
      CharSequence count =
          context.getString(R.string.template_viewpager_index_count_short, fromIndex, itemCount);

      SpannableStringBuilder output = new SpannableStringBuilder();
      StringBuilderUtils.appendWithSeparator(output, pageTitle, count);
      return output;
    }

    return context.getString(R.string.template_viewpager_index_count, fromIndex, itemCount);
  }

  private static CharSequence getSelectedPageTitle(AccessibilityNodeInfo node) {
    // We need to refresh() after the scroll to get an accurate page title
    if (node == null) {
      return null;
    }

    AccessibilityNodeInfoCompat nodeCompat = AccessibilityNodeInfoUtils.toCompat(node);
    nodeCompat.refresh();

    int numChildren = nodeCompat.getChildCount(); // Not the number of pages!
    CharSequence title = null;
    for (int i = 0; i < numChildren; ++i) {
      AccessibilityNodeInfoCompat child = nodeCompat.getChild(i);
      if (child != null) {
        try {
          if (child.isVisibleToUser()) {
            if (title == null) {
              // Try to roughly match RulePagerPage, which uses getNodeText
              // (but completely matching all the time is not critical).
              title = AccessibilityNodeInfoUtils.getNodeText(child);
            } else {
              // Multiple visible children, abort.
              return null;
            }
          }
        } finally {
          child.recycle();
        }
      }
    }

    return title;
  }

  /** A handler for initializing and canceling feedback for scrolling. */
  private static class ScrollPositionHandler extends WeakReferenceHandler<ScrollFeedbackManager> {
    /** Message identifier for a scroll position notification. */
    private static final int SCROLL_FEEDBACK = 1;

    public ScrollPositionHandler(ScrollFeedbackManager parent) {
      super(parent);
    }

    @Override
    public void handleMessage(Message msg, ScrollFeedbackManager parent) {
      @SuppressWarnings("unchecked")
      final EventIdAnd<AccessibilityEvent> eventAndId = (EventIdAnd<AccessibilityEvent>) msg.obj;
      final AccessibilityEvent event = eventAndId.object;
      switch (msg.what) {
        case SCROLL_FEEDBACK:
          parent.handleScrollFeedback(event, eventAndId.eventId);
          break;
        default: // fall out
      }

      event.recycle();
    }

    /** Posts the delayed scroll position feedback. Call this for every VIEW_SCROLLED event. */
    private void postScrollFeedback(
        AccessibilityEvent event, Performance.@Nullable EventId eventId) {
      cancelScrollFeedback();
      AccessibilityEvent eventClone;
      try {
        eventClone = AccessibilityEvent.obtain(event);
      } catch (NullPointerException e) {
        LogUtils.i(
            TAG,
            "A NullPointerException is expected to be thrown in the Robolectric tests when we try"
                + " to create a clone of the mocking AccessibilityEvent instance. This exception"
                + " should never occur when the program is running on actual Android devices.");
        eventClone = event;
      }

      final EventIdAnd<AccessibilityEvent> eventAndId =
          new EventIdAnd<AccessibilityEvent>(eventClone, eventId);
      final Message msg = obtainMessage(SCROLL_FEEDBACK, eventAndId);

      AccessibilityNodeInfo source = event.getSource();
      if (Role.getRole(source) == Role.ROLE_PAGER) {
        sendMessageDelayed(msg, DELAY_PAGE_FEEDBACK);
      } else {
        sendMessageDelayed(msg, DELAY_SCROLL_FEEDBACK);
      }
      if (source != null) {
        source.recycle();
      }
    }

    /** Removes any pending scroll position feedback. Call this for every event. */
    private void cancelScrollFeedback() {
      removeMessages(SCROLL_FEEDBACK);
    }
  }

  private static class EventId {
    public long nodeId;
    public int windowId;
    private final int hashcode;

    private static Method getSourceNodeIdMethod;
    private static final String TAG = "EventId";

    static {
      try {
        getSourceNodeIdMethod = AccessibilityRecord.class.getDeclaredMethod("getSourceNodeId");
        getSourceNodeIdMethod.setAccessible(true);
      } catch (NoSuchMethodException e) {
        LogUtils.d(TAG, "Error setting up fields: " + e.toString());
        e.printStackTrace();
      }
    }

    public EventId(long nodeId, int windowId) {
      this.nodeId = nodeId;
      this.windowId = windowId;
      hashcode = (int) (nodeId ^ (nodeId >>> 32)) + windowId * 7;
    }

    public EventId(AccessibilityEvent event)
        throws InvocationTargetException, IllegalAccessException {
      this((long) getSourceNodeIdMethod.invoke(event), event.getWindowId());
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof EventId)) {
        return false;
      }

      EventId otherId = (EventId) other;
      return windowId == otherId.windowId && nodeId == otherId.nodeId;
    }

    @Override
    public int hashCode() {
      return hashcode;
    }
  }
}
