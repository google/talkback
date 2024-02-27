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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.FOCUS_INPUT;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.PrimesController.TimerAction;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory.WindowIdentifier;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.monitor.InputMethodMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** The {@link FocusProcessor} to select initial accessibility focus when window state changes. */
public class FocusActorForScreenStateChange {

  protected static final String TAG = "FocusActorForScreen";

  protected static final FocusActionInfo.Builder FOCUS_ACTION_INFO_RESTORED_BUILDER =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.RESTORED_LAST_FOCUS);

  protected static final FocusActionInfo.Builder FOCUS_ACTION_INFO_SYNCED_INPUT_FOCUS_BUILDER =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.SYNCED_INPUT_FOCUS);

  protected static final FocusActionInfo.Builder FOCUS_ACTION_INFO_FIRST_FOCUSABLE_NODE_BUILDER =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.FIRST_FOCUSABLE_NODE);

  protected static final FocusActionInfo.Builder FOCUS_ACTION_INFO_REQUEST_INITIAL_NODE_BUILDER =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.SCREEN_STATE_CHANGE)
          .setInitialFocusType(FocusActionInfo.REQUESTED_INITIAL_NODE);

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;
  private final PrimesController primesController;

  private final InputMethodMonitor inputMethodMonitor;
  private final FocusFinder focusFinder;
  private final AccessibilityService service;
  private final FormFactorUtils formFactorUtils;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public FocusActorForScreenStateChange(
      @NonNull AccessibilityService service,
      @NonNull InputMethodMonitor inputMethodMonitor,
      @NonNull FocusFinder focusFinder,
      @NonNull PrimesController primesController) {
    this.primesController = primesController;
    this.focusFinder = focusFinder;
    this.service = service;
    this.inputMethodMonitor = inputMethodMonitor;
    formFactorUtils = FormFactorUtils.getInstance();
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  // The candidate window cannot be covered by the current active window; otherwise the focus will
  // jump to the hidden window.
  private boolean isOnTopWindow(
      AccessibilityWindowInfo currentActive, AccessibilityWindowInfo lastWindow) {
    if (currentActive == null) {
      return false;
    }
    Rect activeRect = new Rect();
    Rect lastRect = new Rect();
    currentActive.getBoundsInScreen(activeRect);
    lastWindow.getBoundsInScreen(lastRect);
    return (currentActive.getLayer() > lastWindow.getLayer()) && activeRect.intersect(lastRect);
  }

  /** Restores last focus from {@link AccessibilityFocusActionHistory} to the active window. */
  public boolean restoreLastFocusedNode(ScreenState screenState, EventId eventId) {

    AccessibilityWindowInfo currentActiveWindow = screenState.getActiveWindow();
    AccessibilityFocusActionHistory.Reader history = actorState.getFocusHistory();
    // When locating the last focused node, TalkBack prefers to the one in the latest active window.
    List<AccessibilityWindowInfo> windows = service.getWindows();
    FocusActionRecord lastFocusAction = null;
    long latestTime = 0;

    AccessibilityWindowInfo fallBackWindow = null;
    for (AccessibilityWindowInfo window : windows) {
      final WindowIdentifier windowIdentifier =
          WindowIdentifier.create(window.getId(), screenState);
      FocusActionRecord focusActionRecord =
          history.getLastFocusActionRecordInWindow(windowIdentifier);
      if (focusActionRecord != null) {
        // For IME window, which would not be the active window, we should consider to restore focus
        // on it from the focus history; unless the current active window is in the higher z-order
        // over it.
        if (focusActionRecord.getActionTime() > latestTime) {
          latestTime = focusActionRecord.getActionTime();
          if (window.getType() == TYPE_INPUT_METHOD
              && !isOnTopWindow(currentActiveWindow, window)) {
            fallBackWindow = window;
            lastFocusAction = focusActionRecord;
          } else {
            fallBackWindow = null;
          }
        }
      }
    }

    if (fallBackWindow != null) {
      currentActiveWindow = fallBackWindow;
    }
    if (currentActiveWindow == null) {
      return false;
    }

    AccessibilityNodeInfoCompat root =
        AccessibilityWindowInfoUtils.getRootCompat(currentActiveWindow);
    if (root == null) {
      return false;
    }

    int windowId = currentActiveWindow.getId();
    int windowType = AccessibilityWindowInfoUtils.getType(currentActiveWindow);
    WindowIdentifier windowIdentifier = WindowIdentifier.create(windowId, screenState);

    if (windowType == AccessibilityWindowInfo.TYPE_SYSTEM) {
      // Don't restore focus in system window. A exemption is when context menu closes, we might
      // restore focus in a system window in restoreFocusForContextMenu().
      LogUtils.d(TAG, "Do not restore focus in system ui window.");
      return false;
    }

    if (lastFocusAction == null) {
      lastFocusAction = history.getLastFocusActionRecordInWindow(windowIdentifier);
    }
    if (lastFocusAction == null) {
      return false;
    }
    long startTime = primesController.getTime();

    AccessibilityNodeInfoCompat nodeToRestoreFocus =
        FocusActionRecord.getFocusableNodeFromFocusRecord(root, focusFinder, lastFocusAction);

    boolean firstTime = screenState.isInterpretFirstTimeWhenWakeUp();
    boolean forceMuteFeedback = formFactorUtils.isAndroidWear() && firstTime;
    FocusActionInfo focusActionInfo =
        FOCUS_ACTION_INFO_RESTORED_BUILDER.setForceMuteFeedback(forceMuteFeedback).build();

    boolean success =
        (nodeToRestoreFocus != null)
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
                eventId, Feedback.focus(nodeToRestoreFocus, focusActionInfo).setForceRefocus(true));

    if (firstTime && success) {
      screenState.consumeInterpretFirstTimeWhenWakeUp();
    }
    if (success) {
      primesController.recordDuration(
          TimerAction.INITIAL_FOCUS_RESTORE, startTime, primesController.getTime());
    }

    return success;
  }

  /**
   * Synchronizes accessibility focus to the input focus in the active window by sending a feedback
   * to the pipeline.
   *
   * <p>On TV, the feedback triggers both input-focus and accessibility-focus to be (re-)set. If the
   * input-focused node is not suitable for accessibility focus as per {@link
   * AccessibilityNodeInfoUtils#shouldFocusNode}, a child of the input-focused node may be given
   * focus instead.
   *
   * <p>On non-TV form factors, only accessibility-focus is set. Further, this is limited to the
   * case that the input-focus is on an EditText.
   */
  public boolean syncAccessibilityFocusAndInputFocus(ScreenState screenState, EventId eventId) {

    if (screenState.getActiveWindow() == null) {
      return false;
    }

    AccessibilityNodeInfoCompat nodeForSync = getNodeForFocusSync(screenState);
    if (nodeForSync == null) {
      return false;
    }
    long startTime = primesController.getTime();

    boolean firstTime = screenState.isInterpretFirstTimeWhenWakeUp();
    boolean forceMuteFeedback = formFactorUtils.isAndroidWear() && firstTime;
    FocusActionInfo focusActionInfo =
        FOCUS_ACTION_INFO_SYNCED_INPUT_FOCUS_BUILDER
            .setForceMuteFeedback(forceMuteFeedback)
            .build();

    boolean success =
        pipeline.returnFeedback(eventId, Feedback.focus(nodeForSync, focusActionInfo));

    if (firstTime && success) {
      screenState.consumeInterpretFirstTimeWhenWakeUp();
    }
    if (success) {
      primesController.recordDuration(
          TimerAction.INITIAL_FOCUS_FOLLOW_INPUT, startTime, primesController.getTime());
    }

    return success;
  }

  /**
   * Returns node with input-focus that accessibility-focus should be synced to, or {@code null} if
   * no such node exists.
   */
  private @Nullable AccessibilityNodeInfoCompat getNodeForFocusSync(ScreenState screenState) {
    // Try to focus on input method window first.
    if (inputMethodMonitor.useInputWindowAsActiveWindow()
        && inputMethodMonitor.getRootInActiveInputWindow() != null) {
      return inputMethodMonitor.findFocusedNodeInActiveInputWindow();
    }

    // Otherwise search for focused node in active window.
    AccessibilityNodeInfoCompat candidate = focusFinder.findFocusCompat(FOCUS_INPUT);
    AccessibilityWindowInfo activeWindow = screenState.getActiveWindow();
    if (!isFocusSyncCandidateValid(candidate, activeWindow)) {
      return null;
    }

    if (!AccessibilityNodeInfoUtils.shouldFocusNode(candidate)) {
      AccessibilityNodeInfoCompat replacement = findReplacementForBadCandidate(candidate);
      String logMessage =
          "Input-focused node is not suitable for accessibility-focus and "
              + (replacement != null
                  ? "no suitable child has been found"
                  : "has been replaced by a suitable child");
      LogUtils.v(TAG, logMessage);
      return replacement;
    }

    return candidate;
  }

  /** Tries to find a suitable child node to give focus to. */
  private static @Nullable AccessibilityNodeInfoCompat findReplacementForBadCandidate(
      @NonNull AccessibilityNodeInfoCompat badCandidate) {
    if (badCandidate.getChildCount() == 0) {
      return null;
    }
    // If there is a child node that is selected, choose that.
    AccessibilityNodeInfoCompat candidate =
        AccessibilityNodeInfoUtils.getMatchingDescendant(
            badCandidate, Filter.node(AccessibilityNodeInfoCompat::isSelected));
    if (AccessibilityNodeInfoUtils.shouldFocusNode(candidate)) {
      return candidate;
    }
    // Otherwise, choose the first child node that takes focus. This could be null.
    return AccessibilityNodeInfoUtils.getMatchingDescendant(
        badCandidate, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
  }

  private boolean isFocusSyncCandidateValid(
      AccessibilityNodeInfoCompat candidate, AccessibilityWindowInfo activeWindow) {
    if (candidate == null) {
      return false;
    }
    if (candidate.getWindowId() != activeWindow.getId()) {
      return false;
    }
    if (restrictFocusSyncToEditable()
        && !candidate.isEditable()
        && Role.getRole(candidate) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    return true;
  }

  private boolean restrictFocusSyncToEditable() {
    return !formFactorUtils.isAndroidTv();
  }

  /**
   * Sets accessibility focus to the node with {@link
   * AccessibilityNodeInfoCompat#hasRequestInitialAccessibilityFocus()}, or the first focusable but
   * not title node in the active window.
   */
  public boolean focusOnRequestInitialNodeOrFirstFocusableNonTitleNode(
      ScreenState screenState, EventId eventId) {

    AccessibilityWindowInfo currentActiveWindow = screenState.getActiveWindow();
    if (currentActiveWindow == null) {
      return false;
    }

    AccessibilityNodeInfoCompat root =
        AccessibilityWindowInfoUtils.getRootCompat(currentActiveWindow);
    if (root == null) {
      return false;
    }
    long startTime = primesController.getTime();

    @Nullable CharSequence windowTitle = screenState.getWindowTitle(currentActiveWindow.getId());

    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            root, focusFinder, TraversalStrategy.SEARCH_FOCUS_FORWARD);
    final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache =
        traversalStrategy.getSpeakingNodesCache();

    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(NavigationTarget.TARGET_DEFAULT, speakingNodesCache);

    if (formFactorUtils.isAndroidWear()) {
      nodeFilter =
          AccessibilityNodeInfoUtils.getFilterExcludingSmallTopAndBottomBorderNode(service)
              .and(nodeFilter);
    }

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
                      node, AccessibilityNodeInfoUtils.getFilterIllegalTitleNodeAncestor(service));
            }
          }.and(nodeFilter);
    }

    boolean firstTime = screenState.isInterpretFirstTimeWhenWakeUp();
    boolean forceMuteFeedback = formFactorUtils.isAndroidWear() && firstTime;

    // Finds focus from the requested node, then the first non-title node.
    AccessibilityNodeInfoCompat nodeToFocus = traversalStrategy.focusInitial(root);
    FocusActionInfo focusActionInfo =
        FOCUS_ACTION_INFO_REQUEST_INITIAL_NODE_BUILDER
            .setForceMuteFeedback(forceMuteFeedback)
            .build();
    if (nodeToFocus == null || !AccessibilityNodeInfoUtils.shouldFocusNode(nodeToFocus)) {
      nodeToFocus =
          TraversalStrategyUtils.findFirstFocusInNodeTree(
              traversalStrategy, root, SEARCH_FOCUS_FORWARD, nodeFilter);
      focusActionInfo =
          FOCUS_ACTION_INFO_FIRST_FOCUSABLE_NODE_BUILDER
              .setForceMuteFeedback(forceMuteFeedback)
              .build();
    }

    boolean success =
        (nodeToFocus != null)
            && pipeline.returnFeedback(eventId, Feedback.focus(nodeToFocus, focusActionInfo));

    if (firstTime && success) {
      screenState.consumeInterpretFirstTimeWhenWakeUp();
    }
    if (success) {
      primesController.recordDuration(
          TimerAction.INITIAL_FOCUS_FIRST_CONTENT, startTime, primesController.getTime());
    }

    return success;
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
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            node, focusFinder, TraversalStrategy.SEARCH_FOCUS_FORWARD);
    AccessibilityNodeInfoCompat nodeToAnnounce =
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
  }
}
