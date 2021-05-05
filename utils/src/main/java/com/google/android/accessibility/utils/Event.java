/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.utils;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A wrapper around AccessibilityEvent, to help with:
 *
 * <ul>
 *   <li>handling null events
 *   <li>recycling event
 *   <li>using compat vs bare methods
 *   <li>using correct methods for various android versions
 * </ul>
 *
 * <p>Also wraps a single instance of AccessibilityNodeInfo/Compat, to help with:
 *
 * <ul>
 *   <li>reduce duplication of source node
 *   <li>reduce number of recycle() calls needed for source node copies
 * </ul>
 *
 * <p>The event-wrapper contains both an event, and a source node. This way, we don't generate so
 * many copies of source node. Also, we would just recycle event once, instead of generating and
 * recycling many copies of source node. And similarly, node contains window info. There are a lot
 * of pass-through functions, which handle null-checking and recycling intermediate objects. As a
 * result, there is little reason for callers directly use a node or window-info. Just call
 * top-level event functions.
 */
public class Event {

  private static final String TAG = "Event";

  /**
   * The wrapped event. There is no compat object, because AccessibilityEventCompat only contains
   * static methods. Do not expose this object.
   */
  private AccessibilityEvent eventBare;

  /* Event wrapper might own & recycle contained event, or just reference event owned by caller. */
  private boolean isEventOwner;

  @Nullable private AccessibilityNode source;

  /** Name of calling method that recycled this event. */
  private String recycledBy;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /** Takes ownership of eventArg. Caller must recycle returned Event. */
  @Nullable
  public static Event takeOwnership(@Nullable AccessibilityEvent eventArg) {
    return construct(eventArg, /* copy= */ false, /* own= */ true, FACTORY);
  }

  /** Caller keeps ownership of eventArg. Caller must also recycle returned Event. */
  @Nullable
  public static Event obtainCopy(@Nullable AccessibilityEvent eventArg) {
    return construct(eventArg, /* copy= */ true, /* own= */ true, FACTORY);
  }

  /** Caller keeps ownership of eventArg. Caller must also recycle returned Event. */
  @Nullable
  public static Event reference(@Nullable AccessibilityEvent eventArg) {
    return construct(eventArg, /* copy= */ false, /* own= */ false, FACTORY);
  }

  /**
   * Returns an Event instance, or null. Should only be called by this class and sub-classes.
   *
   * <p>Uses factory argument to create sub-class instances, without creating unnecessary instances
   * when result should be null. Method is protected so that it can be called by sub-classes without
   * duplicating null-checking logic.
   *
   * @param eventArg wrapped event info, which caller may need to recycle
   * @param copy flag whether to wrap a copy of eventArg, that caller must recycle
   * @param own flag whether wrapped event will be recycled
   * @param factory creates instances of Event or sub-classes
   */
  @Nullable
  protected static <T extends Event> T construct(
      @Nullable AccessibilityEvent eventArg, boolean copy, boolean own, Factory<T> factory) {

    if (copy && !own) {
      throw new IllegalStateException("Cannot create Event that wraps an un-owned copy.");
    }
    if (eventArg == null) {
      return null;
    }

    T instance = factory.create();
    Event instanceBase = instance;
    instanceBase.eventBare = copy ? AccessibilityEvent.obtain(eventArg) : eventArg;
    instanceBase.isEventOwner = own;
    return instance;
  }

  protected Event() {}

  /** A factory that can create instances of Event or sub-classes. */
  protected interface Factory<T extends Event> {
    T create();
  }

  private static final Factory<Event> FACTORY =
      new Factory<Event>() {
        @Override
        public Event create() {
          return new Event();
        }
      };

  ///////////////////////////////////////////////////////////////////////////////////////
  // Recycling

  /** Returns whether the wrapped event is already recycled. */
  public final synchronized boolean isRecycled() {
    return (recycledBy != null);
  }

  /** Recycles the wrapped node & window. Errors if called more than once. */
  public final synchronized void recycle(String caller) {

    // Check for double-recycling.
    if (recycledBy == null) {
      recycledBy = caller;
    } else {
      logOrThrow("Event already recycled by %s then by %s", recycledBy, caller);
      return;
    }

    // Recycle wrapped AccessibilityEvent only if this wrapper owns the event.
    if (eventBare != null && isEventOwner) {
      try {
        eventBare.recycle();
      } catch (IllegalStateException e) {
        logOrThrow(
            e,
            "Caught IllegalStateException from accessibility framework with %s trying to recycle"
                + " event %s",
            caller,
            eventBare);
      }
    }

    // Recycle source node, if it exists.
    if (source != null && !source.isRecycled()) {
      source.recycle(caller);
    }

    recycledBy = caller;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityEvent/Compat methods. Also see:
  // https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent

  private final AccessibilityEvent getEvent() {
    if (isRecycled()) {
      throwError("getEvent() called on node already recycled by %s", recycledBy);
    }
    return eventBare;
  }

  /** Returns a bitmap of custom actions. See public documentation of AccessibilityEvent. */
  public final int getAction() {
    return getEvent().getAction();
  }

  /** Returns a bitmap of content changes. See public documentation of AccessibilityEvent. */
  public final int getContentChangeTypes() {
    return AccessibilityEventCompat.getContentChangeTypes(getEvent());
  }

  /** Returns an enum of event type. See public documentation of AccessibilityEvent. */
  public final int getEventType() {
    return getEvent().getEventType();
  }

  // TODO: Add more methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityNodeInfo methods on source node.

  /** Returns an instance of source node, kept inside this event wrapper. */
  @Nullable
  private final AccessibilityNode getSource() {
    if (isRecycled()) {
      throwError("getSource() called on node already recycled by %s", recycledBy);
    }
    if (source == null) {
      source = AccessibilityNode.takeOwnership(getEvent().getSource());
    }
    return source;
  }

  /** Returns source node's class name. See public documentation of AccessibilityNodeInfo. */
  @Nullable
  public final CharSequence sourceGetClassName() {
    @Nullable AccessibilityNode sourceNode = getSource();
    return (sourceNode == null) ? null : sourceNode.getClassName();
  }

  /** Returns source node's custom actions. See public documentation of AccessibilityNodeInfo. */
  @Nullable
  public final List<AccessibilityNodeInfo.AccessibilityAction> sourceGetActionList() {
    @Nullable AccessibilityNode sourceNode = getSource();
    return (sourceNode == null) ? null : sourceNode.getActionList();
  }

  // TODO: Add more methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Utility methods.  Call AccessibilityEventUtils methods, do not duplicate them.

  /**
   * Returns whether the wrapped event matches an event type in typeMask bitmask. See
   * AccessibilityEventUtils.
   */
  public final boolean eventMatchesAnyType(int typeMask) {
    return AccessibilityEventUtils.eventMatchesAnyType(getEvent(), typeMask);
  }

  /** Returns event description, or falls back to event text. See AccessibilityEventUtils. */
  public final CharSequence getEventTextOrDescription() {
    return AccessibilityEventUtils.getEventTextOrDescription(getEvent());
  }

  // TODO: Add methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Error methods

  private final void logOrThrow(String format, Object... parameters) {
    if (isDebug()) {
      throwError(String.format(format, parameters));
    } else {
      logError(format, parameters);
    }
  }

  private final void logOrThrow(
      IllegalStateException exception, String format, Object... parameters) {
    if (isDebug()) {
      throw exception;
    } else {
      logError(format, parameters);
      logError("%s", exception);
    }
  }

  /** Overridable for testing. */
  protected boolean isDebug() {
    return BuildConfig.DEBUG;
  }

  protected void logError(String format, Object... parameters) {
    LogUtils.e(TAG, format, parameters);
  }

  private final void throwError(String format, Object... parameters) {
    throw new IllegalStateException(String.format(format, parameters));
  }
}
