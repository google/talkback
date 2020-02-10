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

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.RESTORE;

import android.accessibilityservice.AccessibilityService;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.PrimesController.Timer;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.focusmanagement.record.NodePathDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;

/** The {@link FocusProcessor} to select initial accessibility focus when window state changes. */
public class FocusProcessorForScreenStateChange {

  private static final String TAG = "FocusProcForScreenState";

  protected static final FocusActionInfo FOCUS_ACTION_INFO_RESTORED =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.RESTORED_LAST_FOCUS)
          .build();

  protected static final FocusActionInfo FOCUS_ACTION_INFO_SYNCED_EDIT_TEXT =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.SYNCED_EDIT_TEXT)
          .build();

  protected static final FocusActionInfo FOCUS_ACTION_INFO_FIRST_FOCUSABLE_NODE =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.FIRST_FOCUSABLE_NODE)
          .build();

  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final PrimesController primesController;

  private final boolean isTv;

  @VisibleForTesting protected long handledOverrideFocusRestoreUptimeMs = 0;

  public FocusProcessorForScreenStateChange(
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      AccessibilityService service,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      PrimesController primesController) {
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.primesController = primesController;
    isTv = FeatureSupport.isTv(service);
  }

  public boolean onScreenStateChanged(
      @Nullable ScreenState oldScreenState,
      @NonNull ScreenState newScreenState,
      long startTime,
      EventId eventId) {
    try {
      primesController.startTimer(Timer.INITIALIZE_FOCUS);

      if (oldScreenState == null) {
        // We don't have old ScreenState when TalkBack starts.
        return false;
      }

      AccessibilityWindowInfo currentActiveWindow = newScreenState.getActiveWindow();
      if (currentActiveWindow == null) {
        LogUtils.w(TAG, "Cannot find active window.");
        return false;
      }

      int activeWindowId = currentActiveWindow.getId();
      CharSequence activeWindowTitle = newScreenState.getWindowTitle(activeWindowId);

      AccessibilityFocusActionHistory.Reader history = actorState.getFocusHistory();
      // Fix , initial focus will be skipped if user interacts on old window. So we only
      // skip initial focus if the interaction is happened on active window to ensure it can grant
      // focus.
      FocusActionRecord lastRecordOnActiveWindow =
          history.getLastFocusActionRecordInWindow(activeWindowId, activeWindowTitle);
      if ((lastRecordOnActiveWindow != null)
          && (lastRecordOnActiveWindow.getActionTime() > startTime)) {
        int sourceAction = lastRecordOnActiveWindow.getExtraInfo().sourceAction;
        if ((sourceAction == FocusActionInfo.TOUCH_EXPLORATION)
            || (sourceAction == FocusActionInfo.LOGICAL_NAVIGATION)) {
          LogUtils.v(
              TAG,
              "User changes accessibility focus on active window during window transition, "
                  + "don't set initial focus here.");
          return false;
        }
      }

      AccessibilityWindowInfo previousActiveWindow = oldScreenState.getActiveWindow();
      if (AccessibilityWindowInfoUtils.equals(currentActiveWindow, previousActiveWindow)
          && TextUtils.equals(
              newScreenState.getActiveWindowTitle(), oldScreenState.getActiveWindowTitle())) {
        LogUtils.v(TAG, "Do not reassign initial focus when active window is not changed.");
        return false;
      }

      if (isActiveWindowShiftingWithIdenticalWindowSet(oldScreenState, newScreenState)) {
        LogUtils.v(
            TAG,
            "Do not assign initial focus when active window shifts with window set unchanged.");
        return false;
      }

      if (hasValidAccessibilityFocusInWindow(currentActiveWindow)) {
        return false;
      }

      if (actorState.getOverrideFocusRestoreUptimeMs() > handledOverrideFocusRestoreUptimeMs) {
        if (pipeline.returnFeedback(eventId, Feedback.focus(RESTORE))) {
          return true;
        }
      }

      return assignFocusOnWindow(currentActiveWindow, activeWindowTitle, eventId);
    } finally {
      /**
       * Refresh the update-time whenever window transition finished. Sometimes focus won't restore
       * in special cases, like dismiss dialog when screen off. If we don't refresh the flag, it
       * won't restore focus at screen off stage but restore focus at next visible window transition
       * instead.
       */
      handledOverrideFocusRestoreUptimeMs = actorState.getOverrideFocusRestoreUptimeMs();
      primesController.stopTimer(Timer.INITIALIZE_FOCUS);
    }
  }

  /**
   * Returns true if the screen state change is active window shifting and window.
   *
   * <p>When typing with virtual keyboard, or window volume control panel is opened, Active window
   * shifts between floating window and application window. We don't what to assign initial focus in
   * this case.
   */
  private static boolean isActiveWindowShiftingWithIdenticalWindowSet(
      ScreenState previousScreenState, ScreenState currentScreenState) {
    // TODO: Shall we compare window title here?
    boolean hasIdenticalWindowSet =
        currentScreenState.hasIdenticalWindowSetWith(previousScreenState);
    boolean isActiveWindowChanged =
        !AccessibilityWindowInfoUtils.equals(
            previousScreenState.getActiveWindow(), currentScreenState.getActiveWindow());
    return hasIdenticalWindowSet && isActiveWindowChanged;
  }

  private boolean hasValidAccessibilityFocusInWindow(AccessibilityWindowInfo window) {
    AccessibilityNodeInfoCompat currentFocus = null;
    try {
      currentFocus =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      return (currentFocus != null)
          && AccessibilityNodeInfoUtils.isVisible(currentFocus)
          && (currentFocus.getWindowId() == window.getId());
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
    }
  }

  /**
   * Sets initial focus in the active window.
   *
   * @return {@code true} if successfully set accessibility focus on a node.
   */
  private boolean assignFocusOnWindow(
      AccessibilityWindowInfo activeWindow, @Nullable CharSequence windowTitle, EventId eventId) {
    // TODO: Initial focus is set with linear navigation strategy, which is inconsistent
    // with directional navigation strategy on TV, and introduces some bugs. Enable it when we have
    // a solid solution for this issue.
    boolean enabled = !isTv;
    AccessibilityNodeInfoCompat root =
        AccessibilityNodeInfoUtils.toCompat(AccessibilityWindowInfoUtils.getRoot(activeWindow));
    try {
      return (enabled
              && restoreLastFocusedNode(
                  root, activeWindow.getType(), activeWindow.getId(), windowTitle, eventId))
          || syncA11yFocusToInputFocusedEditText(root, eventId)
          || (enabled && focusOnFirstFocusableNonTitleNode(root, windowTitle, eventId));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root);
    }
  }

  /**
   * Restores last focus from {@link AccessibilityFocusActionHistory} to the active window. Caller
   * should recycle {@code root}.
   *
   * @param root root node in the active window
   * @param windowType current active window type
   * @param windowId current active window id
   * @param windowTitle current active window title
   * @param eventId event id
   * @return {@code true} if successfully restore and set accessibility focus on the node.
   */
  protected boolean restoreLastFocusedNode(
      AccessibilityNodeInfoCompat root,
      int windowType,
      int windowId,
      @Nullable CharSequence windowTitle,
      EventId eventId) {
    if (windowType == AccessibilityWindowInfo.TYPE_SYSTEM) {
      // Don't restore focus in system window. A exemption is when context menu closes, we might
      // restore focus in a system window in restoreFocusForContextMenu().
      LogUtils.d(TAG, "Do not restore focus in system ui window.");
      return false;
    }

    AccessibilityFocusActionHistory.Reader history = actorState.getFocusHistory();
    final FocusActionRecord lastFocusAction =
        history.getLastFocusActionRecordInWindow(windowId, windowTitle);
    if (lastFocusAction == null) {
      return false;
    }
    AccessibilityNodeInfoCompat nodeToRestoreFocus = getNodeToRestoreFocus(root, lastFocusAction);
    try {
      return (nodeToRestoreFocus != null)
          && nodeToRestoreFocus.isVisibleToUser()
          // When a pane changes, the nodes not in the pane become out of the window even though
          // they are still visible to user. The window id and title don't change, so the last
          // focused node, which searches from getFocusHistory(), may not be in the window.
          && AccessibilityNodeInfoUtils.isInWindow(nodeToRestoreFocus, root.getWindow())
          && pipeline.returnFeedback(
              eventId, Feedback.focus(nodeToRestoreFocus, FOCUS_ACTION_INFO_RESTORED));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToRestoreFocus);
    }
  }

  /**
   * Returns the last focused node in {@code window} if it's still valid on screen, otherwise
   * returns focusable node with the same position.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the returned node.
   */
  private static AccessibilityNodeInfoCompat getNodeToRestoreFocus(
      AccessibilityNodeInfoCompat root, FocusActionRecord focusActionRecord) {
    AccessibilityNodeInfoCompat lastFocusedNode = focusActionRecord.getFocusedNode();
    if (lastFocusedNode.refresh()) {
      return lastFocusedNode;
    }
    AccessibilityNodeInfoUtils.recycleNodes(lastFocusedNode);
    if (root == null) {
      return null;
    }
    AccessibilityNodeInfoCompat nodeAtSamePosition =
        NodePathDescription.findNode(root, focusActionRecord.getNodePathDescription());
    if ((nodeAtSamePosition != null)
        && AccessibilityNodeInfoUtils.shouldFocusNode(nodeAtSamePosition)) {
      return nodeAtSamePosition;
    }
    AccessibilityNodeInfoUtils.recycleNodes(nodeAtSamePosition);
    return null;
  }

  /**
   * Sets accessibility focus to EditText in the active window. Caller should recycle {@code root}.
   *
   * @param root root node in current active window
   * @param eventId event id
   * @return {@code true} if successfully set accessibility focus on the EditText node.
   */
  protected boolean syncA11yFocusToInputFocusedEditText(
      AccessibilityNodeInfoCompat root, EventId eventId) {
    AccessibilityNodeInfoCompat inputFocusedNode = null;
    try {
      if (root != null) {
        inputFocusedNode = root.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
      }
      return (inputFocusedNode != null)
          && (inputFocusedNode.isEditable()
              || (Role.getRole(inputFocusedNode) == Role.ROLE_EDIT_TEXT))
          && pipeline.returnFeedback(
              eventId, Feedback.focus(inputFocusedNode, FOCUS_ACTION_INFO_SYNCED_EDIT_TEXT));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(inputFocusedNode);
    }
  }

  /**
   * Sets accessibility focus to the first focusable but not title node in the active window. Caller
   * should recycle {@code root}.
   *
   * @param root root node in the current active window
   * @param windowTitle current active window title
   * @param eventId event id
   * @return {@code true} if successfully set accessibility focus on the node.
   */
  protected boolean focusOnFirstFocusableNonTitleNode(
      AccessibilityNodeInfoCompat root, @Nullable CharSequence windowTitle, EventId eventId) {
    AccessibilityNodeInfoCompat nodeToFocus = null;
    TraversalStrategy traversalStrategy = null;
    try {
      if (root == null) {
        return false;
      }
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(root, TraversalStrategy.SEARCH_FOCUS_FORWARD);
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache =
          traversalStrategy.getSpeakingNodesCache();

      Filter<AccessibilityNodeInfoCompat> nodeFilter =
          NavigationTarget.createNodeFilter(NavigationTarget.TARGET_DEFAULT, speakingNodeCache);

      if (!TextUtils.isEmpty(windowTitle)) {
        String windowTitleString = windowTitle.toString();
        // Do not set initial focus on window title node.
        nodeFilter =
            new Filter<AccessibilityNodeInfoCompat>() {
              @Override
              public boolean accept(AccessibilityNodeInfoCompat node) {
                // We wants to set focus on first non-title node. However, we might also accept a
                // node even if its description is identical to window title.
                // This is in case that WINDOW_STATE_CHANGED event is not reliable, and we might
                // cache some fake titles.
                //
                // We do some post-hand validation here: if we know that the node must not be a
                // title node(matches FILTER_ILLEGAL_TITLE_NODE_ANCESTOR), even if the text is
                // identical to window title, we'll accept it.
                CharSequence nodeDescription = getSimpleNodeTreeDescription(node);
                return (nodeDescription == null)
                    || !nodeDescription.toString().equalsIgnoreCase(windowTitleString)
                    || AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(
                        node, AccessibilityNodeInfoUtils.FILTER_ILLEGAL_TITLE_NODE_ANCESTOR);
              }
            }.and(nodeFilter);
      }

      nodeToFocus =
          TraversalStrategyUtils.findInitialFocusInNodeTree(
              traversalStrategy, root, TraversalStrategy.SEARCH_FOCUS_FORWARD, nodeFilter);
      return (nodeToFocus != null)
          && pipeline.returnFeedback(
              eventId, Feedback.focus(nodeToFocus, FOCUS_ACTION_INFO_FIRST_FOCUSABLE_NODE));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToFocus);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }

  /**
   * Lazily gets description of node tree.
   *
   * <p>In most use cases, window title is represented by a text view, or a view group containing
   * some text views. Thus we don't want to apply heavy rules to compose node tree description like
   * Compositor. Instead, we pickup the description of the first node in the node tree with text.
   */
  private static CharSequence getSimpleNodeTreeDescription(AccessibilityNodeInfoCompat node) {
    CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
    if (!TextUtils.isEmpty(nodeText)) {
      return nodeText;
    }
    TraversalStrategy traversalStrategy = null;
    AccessibilityNodeInfoCompat nodeToAnnounce = null;
    try {
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(node, TraversalStrategy.SEARCH_FOCUS_FORWARD);
      nodeToAnnounce =
          TraversalStrategyUtils.searchFocus(
              traversalStrategy,
              node,
              TraversalStrategy.SEARCH_FOCUS_FORWARD,
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(node)
                      && !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
                }
              });
      return AccessibilityNodeInfoUtils.getNodeText(nodeToAnnounce);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToAnnounce);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }
}
