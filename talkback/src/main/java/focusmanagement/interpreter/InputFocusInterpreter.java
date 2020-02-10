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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor.ScreenStateChangeListener;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
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
 * <p>This class parses input focused events and view selected events, and notifies {@link
 * ViewTargetListener} about target change.
 */
public class InputFocusInterpreter
    implements AccessibilityEventListener, ScreenStateChangeListener {

  private static final String TAG = "InputFocusInterpreter";

  /** Listens to target changes. */
  public interface ViewTargetListener {

    /**
     * Callback when a view is targeted.
     *
     * <p><strong>Note:</strong>Do not recycle {@code targetedNode} in callback method.
     *
     * @return {@code true} if any accessibility action is successfully performed.
     */
    boolean onViewTargeted(
        AccessibilityNodeInfoCompat targetedNode, boolean isInputFocus, EventId eventId);
  }

  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  /**
   * Timeout to determine whether an input focus event could be resulted from the last input focus
   * action.
   */
  private static final int INPUT_FOCUS_ACTION_TIMEOUT = 1000;

  private final AccessibilityService service;
  private final GlobalVariables globalVariables;
  private final FirstWindowFocusManager firstWindowFocusManager;

  /** Actor-state read-only interface. */
  private final ActorState focuserState;

  /**
   * Time of last handled focus-action. Allows interpreter to handle focus-action 1 time only,
   * without writing to actor-state.
   */
  private long lastFocusActionHandleUptimeMs = 0;

  private List<ViewTargetListener> listeners = new ArrayList<>();

  public InputFocusInterpreter(
      AccessibilityService service, GlobalVariables globalVariables, ActorState actorState) {
    this.service = service;
    this.globalVariables = globalVariables;
    firstWindowFocusManager = new FirstWindowFocusManager(service);
    focuserState = actorState;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  FirstWindowFocusManager getFirstWindowFocusManager() {
    return firstWindowFocusManager;
  }

  public void addViewTargetListener(ViewTargetListener listener) {
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
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        handleViewInputFocusedEvent(event, eventId);
        break;
      case AccessibilityEvent.TYPE_VIEW_SELECTED:
        handleViewSelectedEvent(event, eventId);
        break;
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        firstWindowFocusManager.registerWindowStateChangeEvent(event);
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
  public boolean onScreenStateChanged(
      @Nullable ScreenState oldScreenState,
      ScreenState newScreenState,
      long startTime,
      EventId eventId) {
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
    AccessibilityNodeInfoCompat currentInputFocus =
        AccessibilityServiceCompatUtils.getInputFocusedNode(service);
    updateInputFocusedNodeInGlobalVariables(currentInputFocus);
    AccessibilityNodeInfoUtils.recycleNodes(currentInputFocus);
  }

  private void handleViewInputFocusedEvent(AccessibilityEvent event, EventId eventId) {

    AccessibilityNodeInfoCompat sourceNode = null;
    AccessibilityNodeInfoCompat a11yFocusableNode = null;

    try {
      sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      if (sourceNode == null) {
        // Invalid TYPE_VIEW_FOCUSED event.
        return;
      }

      updateInputFocusedNodeInGlobalVariables(sourceNode);

      if (!firstWindowFocusManager.shouldProcessFocusEvent(event)) {
        LogUtils.d(TAG, "Dropping the first window focus.");
        return;
      }

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
        a11yFocusableNode = getA11yFocusableNodeFromInputFocusedNode(sourceNode);
        if (a11yFocusableNode != null) {
          notifyViewTargeted(a11yFocusableNode, /* isInputFocus= */ true, eventId);
        }
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(sourceNode, a11yFocusableNode);
    }
  }

  /**
   * Returns {@code true} if the inputFocusActionRecord is valid, and we're still trying to match it
   * with TYPE_VIEW_FOCUSED event.
   */
  private boolean conflictWithFocusActionRecord(AccessibilityEvent event) {
    return !isLastFocusActionHandled()
        && (event.getEventTime() - focuserState.getInputFocusActionRecord().actionTime
            < INPUT_FOCUS_ACTION_TIMEOUT);
  }

  // TODO: This method is majorly from ProcessorFocusAndSingleTap.setFocusOnView().
  // Some logic is deleted/changed. We need to keep an eye on TalkBack behavior, especially for
  //  and . We need to test this feature on TV because TV is very sensitive to
  // input focus.
  // TODO: Move the logic below into FocusProcessorForSynchronization.
  private static @Nullable AccessibilityNodeInfoCompat getA11yFocusableNodeFromInputFocusedNode(
      AccessibilityNodeInfoCompat eventSourceNode) {
    AccessibilityNodeInfoCompat existingFocus = null;
    try {
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
        return AccessibilityNodeInfoUtils.obtain(eventSourceNode);
      }

      // TODO: Doing a BFS looks like searching for a11y focusable node inside a collection.
      // Since we ignore focus event from list or grid, shall we remove this?
      existingFocus = eventSourceNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (existingFocus == null) {
        return AccessibilityNodeInfoUtils.searchFromBfs(
            eventSourceNode, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
      } else {
        return null;
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(existingFocus);
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
      notifyViewTargeted(selectedNode, /* isInputFocus= */ false, eventId);
    }
    AccessibilityNodeInfoUtils.recycleNodes(selectedNode);
  }

  /**
   * Gets target child node from the source AdapterView node.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the returned node.
   */
  private static @Nullable AccessibilityNodeInfoCompat getTargetChildFromAdapterView(
      AccessibilityEvent event) {
    AccessibilityNodeInfoCompat sourceNode = null;
    try {
      sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      if (sourceNode == null) {
        return null;
      }

      if (event.getItemCount() <= 0
          || event.getFromIndex() < 0
          || event.getCurrentItemIndex() < 0) {
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
        AccessibilityNodeInfoUtils.recycleNodes(targetChildNode);
        return null;
      } else {
        return targetChildNode;
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
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
    long timeDiff = event.getEventTime() - focuserState.getInputFocusActionRecord().actionTime;
    boolean isFromFocusAction =
        ((timeDiff >= 0L) && (timeDiff < INPUT_FOCUS_ACTION_TIMEOUT))
            && node.equals(focuserState.getInputFocusActionRecord().inputFocusedNode);

    AccessibilityNodeInfoUtils.recycleNodes(node);
    return isFromFocusAction;
  }

  private boolean isLastFocusActionHandled() {
    return (focuserState.getInputFocusActionRecord() == null)
        || (focuserState.getInputFocusActionRecord().actionTime <= lastFocusActionHandleUptimeMs);
  }

  /** Marks focus-action as handled, without writing to actor-state. */
  private void clearFocusActionRecord() {
    if (focuserState.getInputFocusActionRecord() != null) {
      lastFocusActionHandleUptimeMs = focuserState.getInputFocusActionRecord().actionTime;
    }
  }

  private void notifyViewTargeted(
      AccessibilityNodeInfoCompat node, boolean isInputFocus, EventId eventId) {
    for (ViewTargetListener listener : listeners) {
      listener.onViewTargeted(node, isInputFocus, eventId);
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

  /** Tracks the first TYPE_VIEW_FOCUSED event when a window is opened. */
  // TODO: This class cannot track the first TYPE_VIEW_FOCUSED event after "silent" fragment
  // transition without TYPE_WINDOW_STATE_CHANGED events.
  // TODO: Merge this class with InputFocusInterpreter.
  @VisibleForTesting
  class FirstWindowFocusManager {
    private static final int MISS_FOCUS_DELAY_NORMAL = 300;
    // TODO: Revisit the delay due to TV transitions if  changes.
    private static final int MISS_FOCUS_DELAY_TV = 1200; // Longer transitions on TV.

    private static final String SOFT_INPUT_WINDOW = "android.inputmethodservice.SoftInputWindow";

    private long lastWindowStateChangeEventTime;
    private int lastWindowId;
    private long lastWindowIdFromEventUptimeMs = 0;
    private boolean isFirstFocusInWindow;
    private final Context context;

    FirstWindowFocusManager(Context context) {
      this.context = context;
    }

    void registerWindowStateChangeEvent(AccessibilityEvent event) {
      lastWindowStateChangeEventTime = event.getEventTime();
      if (getLastWindowId() != event.getWindowId() && !shouldIgnoreWindowChangeEvent(event)) {
        setLastWindowIdFromEvent(event.getWindowId());
        isFirstFocusInWindow = true;
      }
    }

    /**
     * Decides whether to ignore an event for purposes of registering the first-focus window change;
     * returns true events that come from non-main windows such as IMEs.
     */
    private boolean shouldIgnoreWindowChangeEvent(AccessibilityEvent event) {
      // The specific SoftInputWindow check seems to be necessary for Android TV.
      return (event.getWindowId() < 0)
          || TextUtils.equals(SOFT_INPUT_WINDOW, event.getClassName())
          || AccessibilityEventUtils.isNonMainWindowEvent(event);
    }

    boolean shouldProcessFocusEvent(AccessibilityEvent event) {
      boolean isFirstFocus = isFirstFocusInWindow;
      isFirstFocusInWindow = false;

      if (getLastWindowId() != event.getWindowId()) {
        setLastWindowIdFromEvent(event.getWindowId());
        return false;
      }

      int focusDelay = FeatureSupport.isTv(context) ? MISS_FOCUS_DELAY_TV : MISS_FOCUS_DELAY_NORMAL;

      return !isFirstFocus || event.getEventTime() - lastWindowStateChangeEventTime > focusDelay;
    }

    private void setLastWindowIdFromEvent(int windowId) {
      lastWindowId = windowId;
      lastWindowIdFromEventUptimeMs = SystemClock.uptimeMillis();
    }

    @VisibleForTesting
    int getLastWindowId() {
      return (lastWindowIdFromEventUptimeMs < focuserState.getLastWindowIdUptimeMs())
          ? focuserState.getLastWindowId()
          : this.lastWindowId;
    }

    @VisibleForTesting
    boolean isFirstFocusInWindow() {
      return isFirstFocusInWindow;
    }
  }
}
