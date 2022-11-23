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

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY;
import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;

import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor.ScreenStateChangeListener;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Monitors system targets which we might need to sync accessibility focus onto.
 *
 * <p>A view can be targeted in two ways:
 *
 * <ul>
 *   <li>An item in {@link android.widget.AdapterView} being selected, which files {@link
 *       AccessibilityEvent#TYPE_VIEW_SELECTED} events.
 *   <li>An item obtaining input focus, which files {@link AccessibilityEvent#TYPE_VIEW_FOCUSED}
 *       events.
 * </ul>
 *
 * <p>This class parses input focused events and view selected events, and sends target-focus-change
 * event-interpretations to the {@code InputFocusChangeListener} to process.
 */
public class InputFocusInterpreter
    implements AccessibilityEventListener, ScreenStateChangeListener {
  private static final String TAG = "InputFocusInterpreter";
  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_VIEW_SELECTED;

  /**
   * Timeout to determine whether an input focus event could be resulted from the last input focus
   * action.
   */
  private static final int INPUT_FOCUS_ACTION_TIMEOUT = 1000;

  /** Listens to target view changes. */
  public interface TargetViewChangeListener {

    /** Callback when the target view is changed. */
    void onViewTargeted(
        @Nullable EventId eventId,
        AccessibilityEvent event,
        AccessibilityNodeInfoCompat targetedNode);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final FocusFinder focusFinder;
  private final GlobalVariables globalVariables;
  private ActorState actorState;
  private TargetViewChangeListener targetViewChangeListener;

  /**
   * Time of last handled focus-action. Allows interpreter to handle focus-action 1 time only,
   * without writing to actor-state.
   */
  private long lastFocusActionHandleUptimeMs = 0;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for construction

  public InputFocusInterpreter(
      TargetViewChangeListener targetViewChangeListener,
      FocusFinder focusFinder,
      GlobalVariables globalVariables) {
    this.focusFinder = focusFinder;
    this.globalVariables = globalVariables;
    this.targetViewChangeListener = targetViewChangeListener;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        handleViewInputFocusedEvent(event, eventId);
        break;
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        // Update focused editable immediately, in case screen-change event comes too late.
        initLastEditableFocusForGlobalVariables();
        break;
      case AccessibilityEvent.TYPE_VIEW_SELECTED:
        handleViewSelectedEvent(event, eventId);
        break;
      default:
        break;
    }
  }

  /**
   * Updates input focused node in {@link GlobalVariables} when {@link ScreenState} changes.
   *
   * <p>It happens sometimes after screen state changes, the input focus changes but we don't
   * receive {@link AccessibilityEvent#TYPE_VIEW_FOCUSED} event. e.g. Edit an edit field, lock and
   * unlock the phone. In that case we should manually grab the input focused node from the node
   * tree and notify {@link GlobalVariables}.
   */
  @Override
  public boolean onScreenStateChanged(ScreenState screenState, EventId eventId) {
    initLastEditableFocusForGlobalVariables();
    return false;
  }

  /**
   * Finds input focused edit text for {@link GlobalVariables}.
   *
   * <p>This method handles the case when input focus changes but TalkBack doesn't receive
   * TYPE_VIEW_FOCUSED events. It's is used in the following two use cases:
   *
   * <ul>
   *   <li>When TalkBack is turned on with an edit text already input focused.
   *   <li>When a window with input focused edit text is brought to foreground.
   * </ul>
   */
  public void initLastEditableFocusForGlobalVariables() {
    AccessibilityNodeInfoCompat currentInputFocus = focusFinder.findFocusCompat(FOCUS_INPUT);
    LogUtils.v(
        TAG,
        "initLastEditableFocusForGlobalVariables() : currentInputFocus: %s",
        currentInputFocus);
    updateInputFocusedNodeInGlobalVariables(currentInputFocus);
  }

  private void handleViewInputFocusedEvent(AccessibilityEvent event, EventId eventId) {
    AccessibilityNodeInfoCompat sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (sourceNode == null) {
      // Invalid TYPE_VIEW_FOCUSED event.
      return;
    }

    updateInputFocusedNodeInGlobalVariables(sourceNode);

    if (isFromSavedFocusAction(event)) {
      clearFocusActionRecord();
    } else if (!conflictWithFocusActionRecord(event)) {
      // In case when the user navigate very fast on android TV devices for multiple times, the
      // result TYPE_VIEW_FOCUSED events might come delayed. At the meantime,
      // inputFocusActionRecord will be overridden with the last navigation action. If performing
      // all the navigation actions, we receive the events result from first navigation action, we
      // might incorrectly sync accessibility focus to it, which leads to focus jumping back
      // around. Thus we don't try to sync focus if the event might have conflicts with cached
      // inputFocusActionRecord.

      AccessibilityNodeInfoCompat a11yFocusableNode =
          getA11yFocusableNodeFromInputFocusedNode(sourceNode, focusFinder);
      if (a11yFocusableNode != null) {
        targetViewChangeListener.onViewTargeted(eventId, event, a11yFocusableNode);
      }
    }
  }

  /**
   * Returns {@code true} if the inputFocusActionRecord is valid, and we're still trying to match it
   * with TYPE_VIEW_FOCUSED event.
   */
  private boolean conflictWithFocusActionRecord(AccessibilityEvent event) {
    return !isLastFocusActionHandled()
        && (event.getEventTime() - actorState.getInputFocusActionRecord().actionTime
            < INPUT_FOCUS_ACTION_TIMEOUT);
  }

  // TODO: This method is majorly from ProcessorFocusAndSingleTap.setFocusOnView().
  // Some logic is deleted/changed. We need to keep an eye on TalkBack behavior, especially for
  // REFERTO and REFERTO. We need to test this feature on TV because TV is
  // very sensitive to input focus.
  // TODO: Move the logic below into FocusProcessorForSynchronization.
  private static @Nullable AccessibilityNodeInfoCompat getA11yFocusableNodeFromInputFocusedNode(
      AccessibilityNodeInfoCompat eventSourceNode, FocusFinder focusFinder) {
    /* Ignore it when the event is sent from a collection container.
     *
     * There are three common TYPE_VIEW_FOCUSED events that are not under TalkBack's control:
     *   1. Input focus changes when navigating with D-pad on bluetooth keyboard.
     *   2. When opening a new window or fragment, the collection(list/grid) gains input focus.
     *   3. Developers manually set input focus onto some node.
     *
     * The 2nd case is very annoying because it disrupts the "initial focus" feature.
     * We should prevent FocusManager from syncing focus when the event is sent from a collection.
     * Logically this won't affect use case 1 and 3 too much: In use case 1, TalkBack usually
     * receives TYPE_VIEW_SELECTED event from collection, or directly receive TYPE_VIEW_FOCUSED
     * event from collection  item. In use case 3, developer should sent TYPE_VIEW_FOCUSED event
     * from collection item to indicate which item to be focused.
     */
    @RoleName int role = Role.getRole(eventSourceNode);
    if (role == Role.ROLE_LIST || role == Role.ROLE_GRID) {
      LogUtils.d(TAG, "Ignore TYPE_VIEW_FOCUSED event from a collection.");
      return null;
    }

    if (AccessibilityNodeInfoUtils.shouldFocusNode(eventSourceNode)) {
      return eventSourceNode;
    }

    // TODO: Doing a BFS looks like searching for a11y focusable node inside a collection.
    // Since we ignore focus event from list or grid, shall we remove this?
    AccessibilityNodeInfoCompat existingFocus = focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY);
    if (existingFocus == null) {
      return AccessibilityNodeInfoUtils.searchFromBfs(
          eventSourceNode, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
    } else {
      return null;
    }
  }

  /**
   * Handles the case when a view in selected.
   *
   * <p>Only items in {@link android.widget.AdapterView} to be selected. When an item is selected,
   * we received multiple events from the AdapterView, item container layout view, and some control
   * widgets. We only care about the event from AdapterView, and ignore other events.
   */
  private void handleViewSelectedEvent(AccessibilityEvent event, EventId eventId) {
    AccessibilityNodeInfoCompat selectedNode = getTargetChildFromAdapterView(event);
    if (selectedNode != null && AccessibilityNodeInfoUtils.shouldFocusNode(selectedNode)) {
      targetViewChangeListener.onViewTargeted(eventId, event, selectedNode);
    }
  }

  /** Gets target child node from the source AdapterView node. */
  private static @Nullable AccessibilityNodeInfoCompat getTargetChildFromAdapterView(
      AccessibilityEvent event) {
    AccessibilityNodeInfoCompat sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (sourceNode == null) {
      return null;
    }

    if (event.getItemCount() <= 0 || event.getFromIndex() < 0 || event.getCurrentItemIndex() < 0) {
      return null;
    }
    int index = event.getCurrentItemIndex() - event.getFromIndex();
    if (index < 0 || index >= sourceNode.getChildCount()) {
      return null;
    }
    AccessibilityNodeInfoCompat targetChildNode = sourceNode.getChild(index);

    // TODO: Think about to replace childNode check with sourceNode check.
    if ((targetChildNode == null)
        || !AccessibilityNodeInfoUtils.isTopLevelScrollItem(targetChildNode)) {
      return null;
    } else {
      return targetChildNode;
    }
  }

  private boolean isFromSavedFocusAction(AccessibilityEvent event) {
    if (isLastFocusActionHandled()) {
      return false;
    }
    AccessibilityNodeInfoCompat node = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (node == null) {
      return false;
    }
    long timeDiff = event.getEventTime() - actorState.getInputFocusActionRecord().actionTime;
    boolean isFromFocusAction =
        ((timeDiff >= 0L) && (timeDiff < INPUT_FOCUS_ACTION_TIMEOUT))
            && node.equals(actorState.getInputFocusActionRecord().inputFocusedNode);

    return isFromFocusAction;
  }

  private boolean isLastFocusActionHandled() {
    return (actorState.getInputFocusActionRecord() == null)
        || (actorState.getInputFocusActionRecord().actionTime <= lastFocusActionHandleUptimeMs);
  }

  /** Marks focus-action as handled, without writing to actor-state. */
  private void clearFocusActionRecord() {
    if (actorState.getInputFocusActionRecord() != null) {
      lastFocusActionHandleUptimeMs = actorState.getInputFocusActionRecord().actionTime;
    }
  }

  /**
   * Updates {@code lastTextEditIsPassword} in {@link GlobalVariables}.
   *
   * @param inputFocusedNode Current input focused node. Set to {@code null} if there is no input
   *     focus on screen.
   */
  private void updateInputFocusedNodeInGlobalVariables(
      @Nullable AccessibilityNodeInfoCompat inputFocusedNode) {
    if ((inputFocusedNode != null)
        && (inputFocusedNode.isEditable()
            || (Role.getRole(inputFocusedNode) == Role.ROLE_EDIT_TEXT))) {
      globalVariables.setLastTextEditIsPassword(inputFocusedNode.isPassword());
    }
    // Don't update the field if non-edittext node is grabbing the input focus.
    // Some on-screen keyboard keys take input focus when tapped, but we still want the last
    // text-edit to remain the same.
  }
}
