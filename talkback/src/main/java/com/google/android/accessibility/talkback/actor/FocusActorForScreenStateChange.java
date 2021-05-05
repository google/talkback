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

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.PrimesController.Timer;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.focusmanagement.record.NodePathDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;

/** The {@link FocusProcessor} to select initial accessibility focus when window state changes. */
public class FocusActorForScreenStateChange {

  protected static final String TAG = "FocusActorForScreen";

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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;
  private final PrimesController primesController;

  private final FocusFinder focusFinder;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public FocusActorForScreenStateChange(
      FocusFinder focusFinder, PrimesController primesController) {
    this.primesController = primesController;
    this.focusFinder = focusFinder;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /** Restores last focus from {@link AccessibilityFocusActionHistory} to the active window. */
  public boolean restoreLastFocusedNode(ScreenState screenState, EventId eventId) {

    AccessibilityWindowInfo currentActiveWindow = screenState.getActiveWindow();
    if (currentActiveWindow == null) {
      return false;
    }

    primesController.startTimer(Timer.INITIAL_FOCUS_RESTORE);
    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat nodeToRestoreFocus = null;
    try {
      root = AccessibilityWindowInfoUtils.getRootCompat(currentActiveWindow);
      if (root == null) {
        return false;
      }

      int windowId = currentActiveWindow.getId();
      int windowType = currentActiveWindow.getType();
      @Nullable CharSequence windowTitle = screenState.getWindowTitle(windowId);

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
      nodeToRestoreFocus = getNodeToRestoreFocus(root, lastFocusAction);

      return (nodeToRestoreFocus != null)
          && nodeToRestoreFocus.isVisibleToUser()
          // When a pane changes, the nodes not in the pane become out of the window even though
          // they are still visible to user. The window id and title don't change, so the last
          // focused node, which searches from getFocusHistory(), may not be in the window.
          && AccessibilityNodeInfoUtils.isInWindow(
              nodeToRestoreFocus, AccessibilityNodeInfoUtils.getWindow(root))
          // TODO: Remove workaround solution. Adds setForceRefocus(true) to forces
          // node to clear accessibility focus because nodeToRestoreFocus from
          // lastFocusedNode.refresh() gets wrong status of accessibility focus of node. It is
          // redundant if Chrome fixes this bug.
          && pipeline.returnFeedback(
              eventId,
              Feedback.focus(nodeToRestoreFocus, FOCUS_ACTION_INFO_RESTORED).setForceRefocus(true));

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, nodeToRestoreFocus);
      primesController.stopTimer(Timer.INITIAL_FOCUS_RESTORE);
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

  /** Sets accessibility focus to EditText in the active window. */
  public boolean syncA11yFocusToInputFocusedEditText(ScreenState screenState, EventId eventId) {

    primesController.startTimer(Timer.INITIAL_FOCUS_FOLLOW_INPUT);
    AccessibilityNodeInfoCompat inputFocusedNode = null;
    try {
      inputFocusedNode = focusFinder.findFocusCompat(FOCUS_INPUT);
      return (inputFocusedNode != null)
          && (inputFocusedNode.isEditable()
              || (Role.getRole(inputFocusedNode) == Role.ROLE_EDIT_TEXT))
          && pipeline.returnFeedback(
              eventId, Feedback.focus(inputFocusedNode, FOCUS_ACTION_INFO_SYNCED_EDIT_TEXT));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(inputFocusedNode);
      primesController.stopTimer(Timer.INITIAL_FOCUS_FOLLOW_INPUT);
    }
  }

  /** Sets accessibility focus to the first focusable but not title node in the active window. */
  public boolean focusOnFirstFocusableNonTitleNode(ScreenState screenState, EventId eventId) {

    AccessibilityWindowInfo currentActiveWindow = screenState.getActiveWindow();
    if (currentActiveWindow == null) {
      return false;
    }

    primesController.startTimer(Timer.INITIAL_FOCUS_FIRST_CONTENT);
    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat nodeToFocus = null;
    TraversalStrategy traversalStrategy = null;
    try {
      root = AccessibilityWindowInfoUtils.getRootCompat(currentActiveWindow);
      if (root == null) {
        return false;
      }

      @Nullable CharSequence windowTitle = screenState.getWindowTitle(currentActiveWindow.getId());

      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(
              root, focusFinder, TraversalStrategy.SEARCH_FOCUS_FORWARD);
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
      AccessibilityNodeInfoUtils.recycleNodes(nodeToFocus, root);
      TraversalStrategyUtils.recycle(traversalStrategy);
      primesController.stopTimer(Timer.INITIAL_FOCUS_FIRST_CONTENT);
    }
  }

  /**
   * Lazily gets description of node tree.
   *
   * <p>In most use cases, window title is represented by a text view, or a view group containing
   * some text views. Thus we don't want to apply heavy rules to compose node tree description like
   * Compositor. Instead, we pickup the description of the first node in the node tree with text.
   */
  private CharSequence getSimpleNodeTreeDescription(AccessibilityNodeInfoCompat node) {
    CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
    if (!TextUtils.isEmpty(nodeText)) {
      return nodeText;
    }
    TraversalStrategy traversalStrategy = null;
    AccessibilityNodeInfoCompat nodeToAnnounce = null;
    try {
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(
              node, focusFinder, TraversalStrategy.SEARCH_FOCUS_FORWARD);
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
