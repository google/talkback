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
import android.accessibilityservice.TouchInteractionController;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.Display;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.talkback.ActorStateWritable;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TalkBackService.GestureDetectionState;
import com.google.android.accessibility.talkback.WebActor;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  private final GestureDetectionState gestureDetectionState;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FocusActor(
      AccessibilityService service,
      FocusFinder focusFinder,
      ScreenStateMonitor.State screenState,
      AccessibilityFocusActionHistory accessibilityFocusActionHistory,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      GestureDetectionState gestureDetectionState) {
    this.service = service;
    this.history = accessibilityFocusActionHistory;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.gestureDetectionState = gestureDetectionState;
    focusManagerInternal =
        new FocusManagerInternal(
            service, focusFinder, screenState, history, accessibilityFocusMonitor);
    webActor = new WebActor(service, this::updateFocusHistory);
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

    if (PerformActionUtils.isNodeSupportAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK)
        && performActionOnNode(AccessibilityNodeInfoCompat.ACTION_CLICK, node, eventId)) {
      return true;
    }
    if (FeatureSupport.supportGestureDetection() && gestureDetectionState.gestureDetector()) {
      // TODO: For multi-display environment, may need to specify display id from the window info.
      TouchInteractionController controller =
          service.getTouchInteractionController(Display.DEFAULT_DISPLAY);
      if (controller != null) {
        controller.performClick();
        return true;
      }
    }
    // When none of the preceding conditions are met, the last resort is simulating click event.
    return simulateClickOnNode(service, node);
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

  public boolean clickCurrentHierarchical(@Nullable EventId eventId) {
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
    return PerformActionUtils.performAction(
        nodeToClick, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
  }

  public void clearAccessibilityFocus(EventId eventId) {
    focusManagerInternal.clearAccessibilityFocus(eventId);
  }

  /** Allows menus to prevent announcing content-focus event after edit-menu dismissed. */
  public void setMuteNextFocus() {
    focusManagerInternal.setMuteNextFocus();
  }

  public void renewEnsureFocus() {
    focusManagerInternal.renewEnsureFocus();
  }

  /** Passes through to FocusManagerInternal.setAccessibilityFocus() */
  public boolean setAccessibilityFocus(
      @NonNull AccessibilityNodeInfoCompat node,
      boolean forceRefocusIfAlreadyFocused,
      @NonNull FocusActionInfo focusActionInfo,
      EventId eventId) {
    return focusManagerInternal.setAccessibilityFocus(
        node, forceRefocusIfAlreadyFocused, focusActionInfo, eventId);
  }

  public WebActor getWebActor() {
    return webActor;
  }

  /** Passes through to FocusManagerInternal.updateFocusHistory() */
  private void updateFocusHistory(
      @NonNull AccessibilityNodeInfoCompat pivot, @NonNull FocusActionInfo focusActionInfo) {
    focusManagerInternal.updateFocusHistory(pivot, focusActionInfo);
  }

  /**
   * Caches the current focused node especially for context menu and dialogs, which is used to
   * restore focus when context menu or dialog closes. It can work by calling {@code
   * overrideNextFocusRestorationForWindowTransition} before next window transition to invoke {@code
   * restoreFocus} when screen state changes to restore the cached focus.
   *
   * <p>If the cached focused node is null, the current focused node will be the target node for
   * restore focus.
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
  public boolean cacheNodeToRestoreFocus(@Nullable AccessibilityNodeInfoCompat targetNode) {
    if (targetNode != null) {
      history.cacheNodeToRestoreFocus(targetNode);
      return true;
    } else {
      targetNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    }
    if (targetNode == null) {
      return false;
    }

    AccessibilityWindowInfoCompat windowInfoCompat =
        AccessibilityNodeInfoUtils.getWindow(targetNode);

    if (windowInfoCompat != null
        && (!windowInfoCompat.isActive()
            || windowInfoCompat.getType() == AccessibilityWindowInfo.TYPE_SYSTEM)) {
      history.cacheNodeToRestoreFocus(targetNode);
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
  public boolean restoreFocus(@Nullable EventId eventId) {
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

  /** Restores focus precisely at the next {@link ScreenState} change. */
  public void overrideNextFocusRestorationForWindowTransition() {
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
