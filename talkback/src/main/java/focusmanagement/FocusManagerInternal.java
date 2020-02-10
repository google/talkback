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
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.ActorStateWritable;
import com.google.android.accessibility.talkback.CursorGranularityManager;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * A class to manage accessibility focus. The only class in TalkBack which has direct access to
 * accessibility focus from framework.
 */
class FocusManagerInternal {

  private static final String TAG = "FocusManagerInternal";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final AccessibilityService service;

  // TODO: ScreenStateMonitor should be event-interpreter, passing screen-state
  // through pipeline.
  private final ScreenStateMonitor screenStateMonitor;

  /** Whether we should drive input focus instead of accessibility focus where possible. */
  private final boolean controlInputFocus;

  private boolean muteNextFocus = false;

  /** Writable focus-history. */
  private final AccessibilityFocusActionHistory history;

  /** Actor-state passed in from pipeline, which encapsulates {@code history}. */
  private ActorStateWritable actorState;

  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public FocusManagerInternal(
      AccessibilityService service,
      ScreenStateMonitor screenStateMonitor,
      AccessibilityFocusActionHistory history,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.service = service;
    this.screenStateMonitor = screenStateMonitor;
    this.history = history;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;

    controlInputFocus = FeatureSupport.isTv(service);
  }

  public void setActorState(ActorStateWritable actorState) {
    this.actorState = actorState;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void setMuteNextFocus() {
    muteNextFocus = true;
  }

  /**
   * Tries to set accessibility focus on the given node. It's used by {@link FocusActor} to set
   * accessibility focus.
   *
   * <p>This method attempts to focus the node only when the node is not accessibility focus or when
   * {@code forceRefocusIfAlreadyFocused} is {@code true}.
   *
   * <p><strong>Note: </strong> Caller is responsible to recycle {@code node}.
   *
   * @param node Node to be focused.
   * @param forceRefocusIfAlreadyFocused Whether we should perform ACTION_ACCESSIBILITY_FOCUS if the
   *     node is already accessibility focused.
   * @param eventId The EventId for performance tracking.
   * @return Whether the node is already accessibility focused or we successfully put accessibility
   *     focus on the node.
   */
  boolean setAccessibilityFocus(
      AccessibilityNodeInfoCompat node,
      boolean forceRefocusIfAlreadyFocused,
      final FocusActionInfo focusActionInfo,
      EventId eventId) {
    if (isAccessibilityFocused(node)) {
      if (forceRefocusIfAlreadyFocused) {
        PerformActionUtils.performAction(
            node, AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId);
      } else {
        return true;
      }
    }

    // Accessibility focus follows input focus on TVs, we want to set both simultaneously,
    // so we change the input focus if possible.
    // Instead of syncing a11y focus when TYPE_VIEW_FOCUSED event is received, we immediately
    // perform a11y focus action after input focus action, in case that we don't receive the result
    // TYPE_VIEW_FOCUSED in some weird cases.
    if (controlInputFocus && node.isFocusable() && !node.isFocused()) {
      boolean result =
          PerformActionUtils.performAction(node, AccessibilityNodeInfo.ACTION_FOCUS, eventId);
      LogUtils.d(
          TAG,
          "Perform input focus action:result=%s\n" + " eventId=%s," + " Node=%s",
          result,
          eventId,
          node);
      if (result) {
        actorState.setInputFocus(node);
      }
    }
    return performAccessibilityFocusActionInternal(node, focusActionInfo, eventId);
  }

  /**
   * Navigate directional from pivot to target html element.
   *
   * <p><strong>Note: </strong> Caller is responsible to recycle {@code pivot}.
   */
  boolean navigateToHtmlElement(
      AccessibilityNodeInfoCompat pivot,
      int direction,
      String htmlElement,
      FocusActionInfo focusActionInfo,
      EventId eventId) {
    long currentTime = SystemClock.uptimeMillis();
    boolean result =
        WebInterfaceUtils.performNavigationToHtmlElementAction(
            pivot, direction, htmlElement, eventId);
    if (!result) {
      return false;
    }

    // Cache the accessibility focus action history.

    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat newFocus = null;

    try {
      root = AccessibilityNodeInfoUtils.getRoot(pivot);
      newFocus = (root == null) ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

      LogUtils.d(
          TAG,
          "Navigate in web:result=%s\nNode:%s\nFocusActionInfo:%s",
          newFocus,
          pivot,
          focusActionInfo);

      FocusActionInfo updatedFocusActionInfo =
          updateFocusActionInfoIfNecessary(focusActionInfo, newFocus);

      if (newFocus == null || pivot.equals(newFocus)) {
        // The focus should have been changed, otherwise we have to wait for the next
        // TYPE_VIEW_ACCESSIBILITY_FOCUSED event to get the correct focused node.
        // Usually this logic will not be invoked. A known case for this is navigating in Firefox.
        history.onPendingAccessibilityFocusActionOnWebElement(
            updatedFocusActionInfo, currentTime, screenStateMonitor.getCurrentScreenState());
      } else {
        history.onAccessibilityFocusAction(
            newFocus,
            updatedFocusActionInfo,
            currentTime,
            screenStateMonitor.getCurrentScreenState());
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, newFocus);
    }
    return true;
  }

  /**
   * Returns whether the node is accessibility focused.
   *
   * <p><strong>Note:</strong> {@link FocusProcessor} should use this method instead of directly
   * invoking {@link AccessibilityNodeInfoCompat#isAccessibilityFocused()}. This is in case that if
   * we want to bypass framework's touch exploration and maintain our own accessibility focus, we
   * can easily override this method.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the node.
   */
  boolean isAccessibilityFocused(AccessibilityNodeInfoCompat node) {
    return node != null && node.isAccessibilityFocused();
  }

  /**
   * Performs {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS} on the node. Saves the
   * record if the action is successfully performed.
   */
  @VisibleForTesting
  protected boolean performAccessibilityFocusActionInternal(
      AccessibilityNodeInfoCompat node, FocusActionInfo focusActionInfo, EventId eventId) {
    long currentTime = SystemClock.uptimeMillis();
    boolean result =
        PerformActionUtils.performAction(
            node, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
    if (result) {
      focusActionInfo = updateFocusActionInfoIfNecessary(focusActionInfo, node);
      // AccessibilityFocusActionHistory makes copy of the node, no need to obtain() here.
      history.onAccessibilityFocusAction(
          node, focusActionInfo, currentTime, screenStateMonitor.getCurrentScreenState());
    }
    LogUtils.d(
        TAG,
        "Set accessibility focus:result=%s\nNode:%s\nFocusActionInfo:%s",
        result,
        node,
        focusActionInfo);
    return result;
  }

  private FocusActionInfo updateFocusActionInfoIfNecessary(
      FocusActionInfo focusActionInfo, AccessibilityNodeInfoCompat node) {
    if (shouldMuteFeedbackForMicroGranularityNavigation(focusActionInfo, node)) {
      LogUtils.d(TAG, "Mute node feedback for micro granularity navigation.");
      focusActionInfo = new FocusActionInfo.Builder(focusActionInfo).forceMuteFeedback().build();
    }
    if (muteNextFocus) {
      if (focusActionInfo.sourceAction == FocusActionInfo.SCREEN_STATE_CHANGE) {
        FocusActionInfo modifiedFocusActionInfo =
            new FocusActionInfo.Builder(focusActionInfo).forceMuteFeedback().build();

        if (focusActionInfo != modifiedFocusActionInfo) {
          LogUtils.d(TAG, "FocusActionInfo modified.");
          muteNextFocus = false;
          return modifiedFocusActionInfo;
        }
      }
    }

    return focusActionInfo;
  }

  /**
   * Checks whether we should mute node feedback for micro granularity navigation.
   *
   * <p>When navigating with micro granularity(character, word, line, etc) across nodes, we don't
   * announce the entire node description from accessibility focus event. There is an exception: If
   * the next node doesn't support target granularity.
   */
  private boolean shouldMuteFeedbackForMicroGranularityNavigation(
      FocusActionInfo info, AccessibilityNodeInfoCompat node) {
    if (info.navigationAction == null) {
      return false;
    }
    CursorGranularity originalNavigationGranularity =
        info.navigationAction.originalNavigationGranularity;

    if ((originalNavigationGranularity == null)
        || !originalNavigationGranularity.isMicroGranularity()) {
      return false;
    }

    return CursorGranularityManager.getSupportedGranularities(service, node, /* eventId= */ null)
        .contains(originalNavigationGranularity);
  }

  /**
   * Clears accessibility focus on the given node.
   *
   * <p><strong>Note: </strong> Caller is responsible to recycle the node.
   *
   * @return {@code true} if successfully perform {@link
   *     AccessibilityNodeInfo#ACTION_CLEAR_ACCESSIBILITY_FOCUS} on the given node.
   */
  boolean clearAccessibilityFocus(AccessibilityNodeInfoCompat currentNode, EventId eventId) {
    return PerformActionUtils.performAction(
        currentNode, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId);
  }

  void clearAccessibilityFocus(EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus = null;
    try {
      currentFocus =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      if (currentFocus != null) {
        clearAccessibilityFocus(currentFocus, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
    }
  }
}
