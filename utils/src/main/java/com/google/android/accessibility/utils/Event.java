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

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A wrapper around AccessibilityEvent, to help with:
 *
 * <ul>
 *   <li>handling null events
 *   <li>using compat vs bare methods
 *   <li>using correct methods for various android versions
 * </ul>
 *
 * <p>Also wraps a single instance of AccessibilityNodeInfo/Compat, to help with:
 *
 * <ul>
 *   <li>reduce duplication of source node
 * </ul>
 *
 * <p>The event-wrapper contains both an event, and a source node. This way, we don't generate so
 * many copies of source node. And similarly, node contains window info. There are a lot of
 * pass-through functions which handle null-checking intermediate objects. As a result, there is
 * little reason for callers directly use a node or window-info. Just call top-level event
 * functions.
 */
public class Event {

  private static final String TAG = "Event";

  /**
   * The wrapped event. There is no compat object, because AccessibilityEventCompat only contains
   * static methods. Do not expose this object.
   */
  private AccessibilityEvent eventBare;

  private @Nullable AccessibilityNode source;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /** Takes ownership of eventArg. */
  public static @Nullable Event takeOwnership(@Nullable AccessibilityEvent eventArg) {
    return construct(eventArg, /* copy= */ false, FACTORY);
  }

  /** Caller keeps ownership of eventArg. */
  public static @Nullable Event obtainCopy(@Nullable AccessibilityEvent eventArg) {
    return construct(eventArg, /* copy= */ true, FACTORY);
  }

  /** Caller keeps ownership of eventArg. */
  public static @Nullable Event reference(@Nullable AccessibilityEvent eventArg) {
    return construct(eventArg, /* copy= */ false, FACTORY);
  }

  /**
   * Returns an Event instance, or null. Should only be called by this class and sub-classes.
   *
   * <p>Uses factory argument to create sub-class instances, without creating unnecessary instances
   * when result should be null. Method is protected so that it can be called by sub-classes without
   * duplicating null-checking logic.
   *
   * @param eventArg wrapped event info
   * @param copy flag whether to wrap a copy of eventArg
   * @param factory creates instances of Event or sub-classes
   */
  protected static <T extends Event> @Nullable T construct(
      @Nullable AccessibilityEvent eventArg, boolean copy, Factory<T> factory) {
    if (eventArg == null) {
      return null;
    }

    T instance = factory.create();
    Event instanceBase = instance;
    instanceBase.eventBare = copy ? AccessibilityEvent.obtain(eventArg) : eventArg;
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
  // AccessibilityEvent/Compat methods. Also see:
  // https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent

  /** Returns a bitmap of custom actions. See public documentation of AccessibilityEvent. */
  public final int getAction() {
    return eventBare.getAction();
  }

  /** Returns a bitmap of content changes. See public documentation of AccessibilityEvent. */
  public final int getContentChangeTypes() {
    return AccessibilityEventCompat.getContentChangeTypes(eventBare);
  }

  /** Returns an enum of event type. See public documentation of AccessibilityEvent. */
  public final int getEventType() {
    return eventBare.getEventType();
  }

  // TODO: Add more methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityNodeInfo methods on source node.

  /** Returns an instance of source node, kept inside this event wrapper. */
  private final @Nullable AccessibilityNode getSource() {
    if (source == null) {
      source = AccessibilityNode.takeOwnership(eventBare.getSource());
    }
    return source;
  }

  /** Returns source node's class name. See public documentation of AccessibilityNodeInfo. */
  public final @Nullable CharSequence sourceGetClassName() {
    @Nullable AccessibilityNode sourceNode = getSource();
    return (sourceNode == null) ? null : sourceNode.getClassName();
  }

  /** Returns source node's custom actions. See public documentation of AccessibilityNodeInfo. */
  public final @Nullable List<AccessibilityNodeInfo.AccessibilityAction> sourceGetActionList() {
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
    return AccessibilityEventUtils.eventMatchesAnyType(eventBare, typeMask);
  }

  /** Returns event description, or falls back to event text. See AccessibilityEventUtils. */
  public final CharSequence getEventTextOrDescription() {
    return AccessibilityEventUtils.getEventTextOrDescription(eventBare);
  }

  // TODO: Add methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Error methods

  /** Overridable for testing. */
  protected boolean isDebug() {
    return BuildConfig.DEBUG;
  }

  protected void logError(String format, Object... parameters) {
    LogUtils.e(TAG, format, parameters);
  }
}
