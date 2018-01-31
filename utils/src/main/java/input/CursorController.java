/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.utils.input;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;

/** Handles screen reader cursor management. */
public interface CursorController
    extends AccessibilityEventListener, KeyComboManager.KeyComboListener {

  /**
   * Add a new listener for granularity change events.
   *
   * @param listener The new listener.
   */
  void addGranularityListener(GranularityChangeListener listener);

  void removeGranularityListener(GranularityChangeListener listener);

  void addScrollListener(ScrollListener listener);

  void addCursorListener(CursorListener listener);

  void setNavigationEnabled(boolean value);

  /** Releases all resources held by this controller and save any persistent preferences. */
  void shutdown();

  /**
   * Clears and replaces focus for the currently focused node.
   *
   * @return Whether the current node was refocused.
   */
  boolean refocus(EventId eventId);

  /**
   * Attempts to move to the next item using the current navigation mode.
   *
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean next(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /**
   * Attempts to move to the previous item using the current navigation mode.
   *
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean previous(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /**
   * Attempts to move to the item leftwards from the current item if the current SDK version
   * supports it. Otherwise, does nothing and returns {@code false}.
   *
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean left(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /**
   * Attempts to move to the item rightwards from the current item if the current SDK version
   * supports it. Otherwise, does nothing and returns {@code false}.
   *
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean right(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /**
   * Attempts to move to the item up from the current item if the current SDK version supports it.
   * Otherwise, does nothing and returns {@code false}.
   *
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean up(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /**
   * Attempts to move to the item down from the current item if the current SDK version supports it.
   * Otherwise, does nothing and returns {@code false}.
   *
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean down(
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /**
   * Attempts to jump to the first item that appears on the screen.
   *
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean jumpToTop(int inputMode, EventId eventId);

  /**
   * Attempts to jump to the last item that appears on the screen.
   *
   * @param inputMode input mode used for this navigation. See InputModeManager.
   * @return {@code true} if successful.
   */
  boolean jumpToBottom(int inputMode, EventId eventId);

  /**
   * Attempts to scroll forward within the current cursor.
   *
   * @return {@code true} if successful.
   */
  boolean more(EventId eventId);

  /**
   * Attempts to scroll backward within the current cursor.
   *
   * @return {@code true} if successful.
   */
  boolean less(EventId eventId);

  /** Attempts to navigate to next item with specified granularity. */
  boolean nextWithSpecifiedGranularity(
      CursorGranularity granularity,
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /** Attempts to navigate to previous item with specified granularity. */
  boolean previousWithSpecifiedGranularity(
      CursorGranularity granularity,
      boolean shouldWrap,
      boolean shouldScroll,
      boolean useInputFocusAsPivotIfEmpty,
      int inputMode,
      EventId eventId);

  /** Attempts to navigate to next html element. */
  boolean nextHtmlElement(String htmlElement, int inputMode, EventId eventId);

  /** Attempts to navigate to previous html element. */
  boolean previousHtmlElement(String htmlElement, int inputMode, EventId eventId);

  /**
   * Attempts to click on the center of the current cursor.
   *
   * @return {@code true} if successful.
   */
  boolean clickCurrent(EventId eventId);

  /**
   * Attempts to click on the current cursor, or its first clickable ancestor. This is useful in
   * approximating the same behavior you get when double-tapping in touch exploration.
   */
  boolean clickCurrentHierarchical(EventId eventId);

  /** Attempts to long click on the current cursor. */
  boolean longClickCurrent(EventId eventId);

  /**
   * Attempts to move to the next reading level.
   *
   * @return {@code true} if successful.
   */
  boolean nextGranularity(EventId eventId);

  /**
   * Attempts to move to the previous reading level.
   *
   * @return {@code true} if successful.
   */
  public boolean previousGranularity(EventId eventId);

  /**
   * Adjust the cursor's granularity by moving it directly to the specified granularity. If the
   * granularity is {@link CursorGranularity#DEFAULT}, unlocks navigation; otherwise, locks
   * navigation to the current cursor.
   *
   * @param granularity The {@link CursorGranularity} to request.
   * @return {@code true} if the granularity change was successful, {@code false} otherwise.
   */
  boolean setGranularity(CursorGranularity granularity, boolean fromUser, EventId eventId);

  boolean setGranularity(
      CursorGranularity granularity,
      AccessibilityNodeInfoCompat node,
      boolean fromUser,
      EventId eventId);

  void setGranularityToDefault();

  void resetLastFocusedInfo();

  /**
   * Sets the current cursor position.
   *
   * @param node The node to set as the cursor.
   * @return {@code true} if successful.
   */
  boolean setCursor(AccessibilityNodeInfoCompat node, EventId eventId);

  /**
   * Sets the current state of selection mode for navigation within text content. When enabled, the
   * manager will attempt to extend selection during navigation. If the target node of selection
   * mode is not locked to a granularity, calling this method will switch to {@link
   * CursorGranularity#CHARACTER}
   *
   * @param node The node on which selection mode should be enabled.
   * @param active {@code true} to activate selection mode, {@code false} to deactivate.
   */
  void setSelectionModeActive(AccessibilityNodeInfoCompat node, boolean active, EventId eventId);

  /** @return {@code true} if selection mode is active, {@code false} otherwise. */
  boolean isSelectionModeActive();

  /** Clears the current cursor position. */
  void clearCursor(EventId eventId);

  /**
   * Clears the given current cursor position. Caller have to make sure currentNode get recycled
   *
   * @param currentNode given node to clear focus
   */
  void clearCursor(AccessibilityNodeInfoCompat currentNode, EventId eventId);

  void repeatLastNavigationAction();

  boolean isNativeMacroGranularity();

  /* It is used to save the node we are scrolling while navigating with native macro granularity */
  void setLastFocusedNodeParent(AccessibilityNodeInfoCompat scrolledNode);

  /**
   * Returns the node in the active window that has accessibility focus. If no node has focus, or if
   * the focused node is invisible, returns the root node.
   *
   * <p>The client is responsible for recycling the resulting node.
   *
   * @return The node in the active window that has accessibility focus.
   */
  AccessibilityNodeInfoCompat getCursor();

  /**
   * Returns a node satisfying one of the following criteria, in descending order: (1) the current
   * accessibility-focused node in the active window, or (2) the current editable input-focused node
   * in the active window if there is no accessibility focus, or (3) {@code null} if no node meets
   * any of the above criteria.
   *
   * <p>The client is responsible for recycling the resulting node.
   *
   * @return An accessibility-focused or input-focused node in the active window.
   */
  AccessibilityNodeInfoCompat getCursorOrInputCursor();

  /**
   * Find initial edit-text focus. This method gets called in 2 cases: When talkback receives
   * TYPE_VIEW_FOCUSED event or when window changes. If talkback changes window with input focus
   * already in a text input, talkback will not receive TYPE_VIEW_FOCUSED.
   */
  void initLastEditable();

  /**
   * Return the current granularity at the specified node, or {@link CursorGranularity#DEFAULT} if
   * none is set. Always returns {@link CursorGranularity#DEFAULT} if granular navigation is not
   * locked to the specified node.
   *
   * @param node The node to check.
   * @return A cursor granularity.
   */
  CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node);

  CursorGranularity getCurrentGranularity();

  /**
   * Creates filter for nodes depending on the selected granularity. If the selected granularity is
   * default, returns a default filter.
   */
  Filter<AccessibilityNodeInfoCompat> getFilter(
      TraversalStrategy traversalStrategy, boolean useSpeakingNodeCache);

  /** Listener for scroll events. */
  public interface ScrollListener {
    /**
     * Informs of a scroll caused by the CursorControler.
     *
     * @param action Direction of the scroll.
     * @param auto If {@code true}, then the scroll was initiated automatically. If {@code false},
     *     then the user initiated the scroll action.
     * @param isRepeatNavigationForAutoScroll If {@code true}, the scroll action is triggered by
     *     repeating the last navigation action.
     */
    public void onScroll(
        AccessibilityNodeInfoCompat scrolledNode,
        int action,
        boolean auto,
        boolean isRepeatNavigationForAutoScroll);
  }

  /** Listener for granularity changes. */
  public interface GranularityChangeListener {
    /**
     * Informs of a granularity change.
     *
     * @param granularity The new granularity after the change occurred.
     */
    void onGranularityChanged(CursorGranularity granularity);
  }

  /** Listener for cursor change events. */
  public interface CursorListener {
    /** Triggered right before a cursor change caused by the CursorController. */
    void beforeSetCursor(AccessibilityNodeInfoCompat newCursor, int action);

    /** Triggered right after a cursor change caused by the CursorController. */
    void onSetCursor(AccessibilityNodeInfoCompat newCursor, int action);
  }
}
