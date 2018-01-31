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

package com.google.android.accessibility.talkback.focusmanagement;

import android.accessibilityservice.AccessibilityService;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScrollController;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.TouchExplorationInterpreter;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * The entry class for TalkBack accessibility focus management. It serves as a centralized dispatch
 * for interpreted user actions and system changes, and provides facilities for other TalkBack
 * modules to query the state of accessibility focus.
 *
 * <p><strong>Usage: </strong>
 *
 * <ul>
 *   <li>AccessibilityFocusManager is the only class visible to other TalkBack components. Event
 *       interpreters and focus processors are internal modules.
 *   <li>Event interpreters listen to accessibility events and notify AccessibilityFocusManager of
 *       parsed user actions and system changes.
 *   <li>AccessibilityFocusManager dispatches user actions and systems changes to FocusProcessors.
 *   <li>FocusProcessors reacts to actions/changes, and use FocusManagerInternal to set focus.
 * </ul>
 */
public class AccessibilityFocusManager implements AccessibilityEventListener {
  /** Event types that are handled by A11yFocusManager. */
  private final int mEventMask;

  /** The only class in TalkBack who has direct access to accessibility focus from framework. */
  private final FocusManagerInternal mFocusManagerInternal;

  private final List<AccessibilityEventListener> mAccessibilityEventListeners;

  // Event interpreters and fundamental components.
  private final ScreenStateMonitor mScreenStateMonitor;
  private final ScrollController mScrollController;
  private final TouchExplorationInterpreter mTouchExplorationInterpreter;

  private final FocusProcessorForManualScroll mFocusProcessorForManualScroll;
  private final FocusProcessorForTapAndTouchExploration mFocusProcessorForTapAndTouchExploration;

  public AccessibilityFocusManager(
      AccessibilityService service,
      SpeechController speechController,
      FeedbackController feedbackController,
      SpeechController.Delegate speechControllerDelegate) {
    mFocusManagerInternal = new FocusManagerInternal(service);

    // Initialize interpreters.
    mScreenStateMonitor = new ScreenStateMonitor(service, this);
    mScrollController = new ScrollController(this);
    mTouchExplorationInterpreter = new TouchExplorationInterpreter(this);

    mEventMask =
        mScreenStateMonitor.getEventTypes()
            | mScrollController.getEventTypes()
            | mTouchExplorationInterpreter.getEventTypes();

    // TODO: Move interpreters up to TalkBack service level to avoid additional event
    // dispatch.
    mAccessibilityEventListeners = new ArrayList<>();
    mAccessibilityEventListeners.add(mScreenStateMonitor);
    mAccessibilityEventListeners.add(mScrollController);
    mAccessibilityEventListeners.add(mTouchExplorationInterpreter);

    // TODO: Initialize FocusProcessors.
    mFocusProcessorForManualScroll = new FocusProcessorForManualScroll(mFocusManagerInternal);
    mFocusProcessorForTapAndTouchExploration =
        new FocusProcessorForTapAndTouchExploration(
            mFocusManagerInternal, speechControllerDelegate, speechController, feedbackController);
  }

  @Override
  public int getEventTypes() {
    return mEventMask;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    for (AccessibilityEventListener listener : mAccessibilityEventListeners) {
      if (AccessibilityEventUtils.eventMatchesAnyType(event, listener.getEventTypes())) {
        listener.onAccessibilityEvent(event, eventId);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Dispatches user actions

  /**
   * Dispatches {@link NavigationAction} to {@link FocusProcessor}s.
   *
   * @param navigationAction The navigation action instance.
   * @param eventId EventId for performance tracking.
   */
  public void sendNavigationAction(NavigationAction navigationAction, final EventId eventId) {
    // TODO: Implement FocusProcessorForNavigationAction.
  }

  /**
   * Dispatches {@link TouchExplorationAction} to {@link FocusProcessor}s.
   *
   * @param action The TouchExplorationAction instance.
   * @param eventId EventId for performance tracking.
   */
  public void sendTouchExplorationAction(TouchExplorationAction action, final EventId eventId) {
    mFocusProcessorForTapAndTouchExploration.onTouchExplorationAction(action, eventId);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Notifies changes from framework

  /**
   * Notifies when {@link ScreenState} changes.
   *
   * @param oldScreenState The last screen state.
   * @param newScreenState Current screen state.
   * @param eventId The EventId for performance tracking.
   */
  public void notifyScreenStateChanged(
      @Nullable ScreenState oldScreenState, @NonNull ScreenState newScreenState, EventId eventId) {
    // TODO: Implement FocusProcessorForScreenStateChanges.
  }

  /**
   * Notifies when a view is input focused.
   *
   * @param inputFocus The input focused node.
   * @param eventId EventId for performance tracking.
   */
  public void notifyViewInputFocused(AccessibilityNodeInfoCompat inputFocus, EventId eventId) {
    // TODO: Implement FocusProcessorForSynchronization.
  }

  /**
   * Notifies when content changes inside window with the given {@code windowId}.
   *
   * @param windowId Id of the window whose content has changed.
   * @param eventId EventId for performance tracking.
   */
  public void notifyWindowContentChanged(int windowId, EventId eventId) {
    // TODO: Implement FocusProcessorForConsistency.
  }

  /**
   * Notifies when a node is scrolled by the user by dragging with two fingers.
   *
   * <p>A node can be scrolled by dragging two fingers on screen. In this use case, we cannot
   * capture nor intercept the user action. We can only react to the result {@link
   * AccessibilityEvent#TYPE_VIEW_SCROLLED} events.
   *
   * @param scrolledNode The node being scrolled.
   * @param direction The scroll direction.
   * @param eventId EventId for performance tracking.
   */
  public void notifyNodeManuallyScrolled(
      AccessibilityNodeInfoCompat scrolledNode,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {
    mFocusProcessorForManualScroll.onNodeManuallyScrolled(scrolledNode, direction, eventId);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // APIs used by other TalkBack components

  /**
   * Gets the current accessibility focus on screen. This method is used by other TalkBack modules
   * to query the current accessibility focus.
   *
   * <p><strong>Note: </strong> This method returns a node only when there is a node being
   * accessibility focused and it's validated with {@link
   * com.google.android.accessibility.utils.AccessibilityNodeInfoUtils#shouldFocusNode(AccessibilityNodeInfoCompat)}
   *
   * @return the valid accessibility focus on screen or null.
   */
  @Nullable
  public AccessibilityNodeInfoCompat getAccessibilityFocus() {
    return mFocusManagerInternal.getAccessibilityFocus(
        null, /* rootCompat */
        false, /* returnRootIfA11yFocusIsNull */
        false /* returnInputFocusedEditableNodeIfA11yFocusIsNull */);
  }

  /**
   * Sets whether single-tap activation is enabled.
   *
   * @param enabled Whether single-tap activation is enabled.
   */
  public void setSingleTapEnabled(boolean enabled) {
    mFocusProcessorForTapAndTouchExploration.setSingleTapEnabled(enabled);
  }

  /**
   * Gets whether single-tap activation is enabled.
   *
   * @return Whether single-tap activation is enabled.
   */
  public boolean getSingleTapEnabled() {
    return mFocusProcessorForTapAndTouchExploration.getSingleTapEnabled();
  }

  public boolean isEventFromFocusManagement(AccessibilityEvent event) {
    return AccessibilityFocusActionHistory.getInstance().matchFocusActionRecordFromEvent(event)
        != null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // APIs used by FocusProcessors

  /**
   * Tries to put accessibility focus onto the given {@code node}. It's used by {@link
   * FocusProcessor} to set a11y focus.
   *
   * <p>If the {@code node} does not have accessibility focus or {@code force} is {@code true},
   * attempts to focus the source node. Returns {@code true} if the node was successfully focused or
   * already had accessibility focus.
   *
   * @param node Node to be focused.
   * @param force Whether we should perform ACTION_ACCESSIBILITY_FOCUS if the node is already
   *     a11y-focused.
   * @param eventId The EventId for performance tracking.
   * @return Whether the node is already accessibility focused or we successfully put a11y focus on
   *     the node.
   */
  // TODO: Remove this method when FocusProcessors are refactored.
  boolean tryFocusing(AccessibilityNodeInfoCompat node, boolean force, EventId eventId) {
    // Unused. Remove this method later.
    return true;
  }
}
