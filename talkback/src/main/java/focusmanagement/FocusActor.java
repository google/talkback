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
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorStateWritable;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;

/** FocusActor executes focus-feedback, using FocusManagerInternal. */
// TODO: Merge FocusActor with FocusManagerInternal.
public class FocusActor {

  private static final String TAG = "FocusActor";

  /** The only class in TalkBack which has direct access to accessibility focus from framework. */
  private final FocusManagerInternal focusManagerInternal;

  private final AccessibilityFocusActionHistory history;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  /** Actor-state passed in from pipeline, which encapsulates {@code history}. */
  private ActorStateWritable actorState;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FocusActor(
      AccessibilityService service,
      ScreenStateMonitor screenStateMonitor,
      AccessibilityFocusActionHistory accessibilityFocusActionHistory,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.history = accessibilityFocusActionHistory;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    focusManagerInternal =
        new FocusManagerInternal(service, screenStateMonitor, history, accessibilityFocusMonitor);
  }

  public void setActorState(ActorStateWritable actorState) {
    this.actorState = actorState;
    focusManagerInternal.setActorState(actorState);
  }

  public AccessibilityFocusActionHistory.Reader getHistory() {
    return history.reader;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public boolean clickCurrentFocus(EventId eventId) {
    return performActionOnCurrentFocus(AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
  }

  public boolean longClickCurrentFocus(EventId eventId) {
    return performActionOnCurrentFocus(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, eventId);
  }

  public boolean clickCurrentHierarchical(EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus = null;
    AccessibilityNodeInfoCompat nodeToClick = null;
    try {
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
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus, nodeToClick);
    }
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

  /** Passes through to FocusManagerInternal.navigateToHtmlElement() */
  public boolean navigateToHtmlElement(
      AccessibilityNodeInfoCompat pivot,
      int direction,
      String htmlElement,
      FocusActionInfo focusActionInfo,
      EventId eventId) {
    return focusManagerInternal.navigateToHtmlElement(
        pivot, direction, htmlElement, focusActionInfo, eventId);
  }

  /**
   * Caches current focused node especially for context menu and dialogs, which is used to restore
   * focus when context menu or dialog closes.
   *
   * @return true if cached node is not null, otherwise false
   */
  public boolean cacheNodeToRestoreFocus() {
    AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (currentFocus == null) {
      return false;
    }

    history.cacheNodeToRestoreFocus(currentFocus);
    return true;
  }

  /**
   * Clears node to restore focus after context menu/dialog closes, and returns whether node
   * existed.
   */
  public boolean popCachedNodeToRestoreFocus() {
    @Nullable AccessibilityNodeInfoCompat node = history.popCachedNodeToRestoreFocus();
    try {
      return (node != null);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  /** Restore focus with the node cached before context menu or dialog appeared. */
  public boolean restoreFocus(EventId eventId) {
    AccessibilityNodeInfoCompat nodeToRestoreFocus = history.popCachedNodeToRestoreFocus();
    if (nodeToRestoreFocus == null) {
      return false;
    }

    try {
      if (!nodeToRestoreFocus.refresh() || !nodeToRestoreFocus.isVisibleToUser()) {
        return false;
      }

      return AccessibilityNodeInfoUtils.isInWindow(
              nodeToRestoreFocus, nodeToRestoreFocus.getWindow())
          && focusManagerInternal.setAccessibilityFocus(
              nodeToRestoreFocus,
              /* forceRefocusIfAlreadyFocused= */ false,
              FocusActionInfo.builder()
                  .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
                  .setInitialFocusType(FocusActionInfo.RESTORED_LAST_FOCUS)
                  .build(),
              eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToRestoreFocus);
    }
  }

  /** At next {@link ScreenState} change, precisely restores focus when context menu closes. */
  public void overrideNextFocusRestorationForContextMenu() {
    actorState.setOverrideFocusRestore();
  }

  private boolean performActionOnCurrentFocus(int action, EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus = null;
    try {
      currentFocus =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      return (currentFocus != null)
          && PerformActionUtils.performAction(currentFocus, action, eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
    }
  }

}
