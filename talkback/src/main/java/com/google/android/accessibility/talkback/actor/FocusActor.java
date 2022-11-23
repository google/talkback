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

package com.google.android.accessibility.talkback.actor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.talkback.ActorStateWritable;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.WebActor;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;

/** FocusActor executes focus-feedback, using FocusManagerInternal. */
// TODO: Merge FocusActor with FocusManagerInternal.
public class FocusActor {

  private static final String TAG = "FocusActor";
  private static final int STROKE_TIME_GAP_MS = 40;

  /** The only class in TalkBack which has direct access to accessibility focus from framework. */
  private final FocusManagerInternal focusManagerInternal;

  private final AccessibilityService service;
  private final AccessibilityFocusActionHistory history;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  /** Actor-state passed in from pipeline, which encapsulates {@code history}. */
  private ActorStateWritable actorState;

  private final WebActor webActor;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FocusActor(
      AccessibilityService service,
      FocusFinder focusFinder,
      ScreenStateMonitor.State screenState,
      AccessibilityFocusActionHistory accessibilityFocusActionHistory,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.service = service;
    this.history = accessibilityFocusActionHistory;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    focusManagerInternal =
        new FocusManagerInternal(
            service, focusFinder, screenState, history, accessibilityFocusMonitor);
    webActor =
        new WebActor(
            service, (start, focusActionInfo) -> updateFocusHistory(start, focusActionInfo));
  }

  public void setActorState(ActorStateWritable actorState) {
    this.actorState = actorState;
    focusManagerInternal.setActorState(actorState);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    focusManagerInternal.setPipeline(pipeline);
    webActor.setPipeline(pipeline);
  }

  public AccessibilityFocusActionHistory.Reader getHistory() {
    return history.reader;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public boolean clickCurrentFocus(EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    return clickNode(currentFocus, eventId);
  }

  public boolean clickNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (node == null) {
      return false;
    }

    if (PerformActionUtils.isNodeSupportAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK)) {
      return performActionOnNode(AccessibilityNodeInfoCompat.ACTION_CLICK, node, eventId);
    } else {
      return simulateClickOnNode(service, node);
    }
  }

  public boolean longClickCurrentFocus(EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    return longClickNode(currentFocus, eventId);
  }

  public boolean longClickNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (node == null) {
      return false;
    }
    return performActionOnNode(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, node, eventId);
  }

  public boolean clickCurrentHierarchical(EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus = null;
    AccessibilityNodeInfoCompat nodeToClick = null;
    currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (currentFocus == null) {
      return false;
    }
    nodeToClick =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            currentFocus, AccessibilityNodeInfoUtils.FILTER_CLICKABLE);
    return (nodeToClick != null)
        && PerformActionUtils.performAction(
            nodeToClick, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
  }

  public void clearAccessibilityFocus(EventId eventId) {
    focusManagerInternal.clearAccessibilityFocus(eventId);
  }

  /** Allows menus to prevent announcing content-focus event after edit-menu dismissed. */
  public void setMuteNextFocus() {
    focusManagerInternal.setMuteNextFocus();
  }

  /** Passes through to FocusManagerInternal.setAccessibilityFocus() */
  public boolean setAccessibilityFocus(
      AccessibilityNodeInfoCompat node,
      boolean forceRefocusIfAlreadyFocused,
      final FocusActionInfo focusActionInfo,
      EventId eventId) {
    return focusManagerInternal.setAccessibilityFocus(
        node, forceRefocusIfAlreadyFocused, focusActionInfo, eventId);
  }

  public WebActor getWebActor() {
    return webActor;
  }

  /** Passes through to FocusManagerInternal.updateFocusHistory() */
  private void updateFocusHistory(
      AccessibilityNodeInfoCompat pivot, FocusActionInfo focusActionInfo) {
    focusManagerInternal.updateFocusHistory(pivot, focusActionInfo);
  }

  /**
   * Caches current focused node especially for context menu and dialogs, which is used to restore
   * focus when context menu or dialog closes. It can work by calling {@code
   * overrideNextFocusRestorationForContextMenu} before next window transition to invoke {@code
   * restoreFocus} when screen state changes to restore the cached focus.
   *
   * <p>This is a workaround to restore focus when returning from special windows, other cases will
   * fallback to standard flow to assign focus. And it is used for below cases:
   * <li>non-active window: REFERTO, restore focus on non-active window after popup
   *     window close.
   * <li>system window: REFERTO, restore focus on system window after popup window
   *     close.
   *
   * @return true if cached node successfully, otherwise false
   */
  public boolean cacheNodeToRestoreFocus() {
    AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (currentFocus == null) {
      return false;
    }

    AccessibilityWindowInfoCompat windowInfoCompat =
        AccessibilityNodeInfoUtils.getWindow(currentFocus);

    if (windowInfoCompat != null
        && (!windowInfoCompat.isActive()
            || windowInfoCompat.getType() == AccessibilityWindowInfo.TYPE_SYSTEM)) {
      history.cacheNodeToRestoreFocus(currentFocus);
      return true;
    }

    return false;
  }

  /**
   * Clears node to restore focus after context menu/dialog closes, and returns whether node
   * existed.
   */
  public boolean popCachedNodeToRestoreFocus() {
    return (history.popCachedNodeToRestoreFocus() != null);
  }

  /** Restore focus with the node cached before context menu or dialog appeared. */
  public boolean restoreFocus(EventId eventId) {
    AccessibilityNodeInfoCompat nodeToRestoreFocus = history.popCachedNodeToRestoreFocus();
    if (nodeToRestoreFocus == null) {
      return false;
    }

    if (!nodeToRestoreFocus.refresh() || !nodeToRestoreFocus.isVisibleToUser()) {
      return false;
    }

    return AccessibilityNodeInfoUtils.isInWindow(
            nodeToRestoreFocus, AccessibilityNodeInfoUtils.getWindow(nodeToRestoreFocus))
        && focusManagerInternal.setAccessibilityFocus(
            nodeToRestoreFocus,
            /* forceRefocusIfAlreadyFocused= */ false,
            FocusActionInfo.builder()
                .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
                .setInitialFocusType(FocusActionInfo.RESTORED_LAST_FOCUS)
                .build(),
            eventId);
  }

  /** At next {@link ScreenState} change, precisely restores focus when context menu closes. */
  public void overrideNextFocusRestorationForContextMenu() {
    actorState.setOverrideFocusRestore();
  }

  /**
   * Checks the accessibility focused node on the current screen, and requests an initial focus if
   * no focused node is found.
   */
  public boolean ensureAccessibilityFocusOnScreen(EventId eventId) {
    return focusManagerInternal.ensureAccessibilityFocusOnScreen(eventId);
  }

  private boolean performActionOnNode(
      int action, AccessibilityNodeInfoCompat node, EventId eventId) {
    return (node != null) && PerformActionUtils.performAction(node, action, eventId);
  }

  /** Simulates a click on the center of a view. */
  private boolean simulateClickOnNode(
      AccessibilityService accessibilityService, AccessibilityNodeInfoCompat node) {
    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    Path path = new Path();
    path.moveTo(rect.centerX(), rect.centerY());
    int durationMs = ViewConfiguration.getTapTimeout();
    GestureDescription gestureDescription =
        new GestureDescription.Builder()
            .addStroke(new StrokeDescription(path, /* startTime= */ 0, durationMs))
            .addStroke(new StrokeDescription(path, durationMs + STROKE_TIME_GAP_MS, durationMs))
            .build();
    return accessibilityService.dispatchGesture(
        gestureDescription, /* callback= */ null, /* handler= */ null);
  }
}
