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

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.INITIAL_FOCUS_FIRST_CONTENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.INITIAL_FOCUS_FOLLOW_INPUT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.INITIAL_FOCUS_RESTORE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorStateWritable;
import com.google.android.accessibility.talkback.CursorGranularityManager;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Focus;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TvNavigation;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;

/** Helps FocusActor execute focus actions. */
// TODO: Merge FocusActor with FocusManagerInternal.
class FocusManagerInternal {

  private static final String TAG = "FocusManagerInternal";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final AccessibilityService service;
  private final FocusFinder focusFinder;
  private final ScreenStateMonitor.State screenState;

  /** Whether we should drive input focus instead of accessibility focus where possible. */
  private final boolean controlInputFocus;

  @VisibleForTesting protected boolean muteNextFocus = false;

  /** Writable focus-history. */
  private final AccessibilityFocusActionHistory history;

  /** Actor-state passed in from pipeline, which encapsulates {@code history}. */
  private ActorStateWritable actorState;

  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  private Pipeline.FeedbackReturner pipeline;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public FocusManagerInternal(
      AccessibilityService service,
      FocusFinder focusFinder,
      ScreenStateMonitor.State screenState,
      AccessibilityFocusActionHistory history,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.service = service;
    this.focusFinder = focusFinder;
    this.screenState = screenState;
    this.history = history;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;

    controlInputFocus = FeatureSupport.isTv(service);
  }

  public void setActorState(ActorStateWritable actorState) {
    this.actorState = actorState;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
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
        pipeline.returnFeedback(
            eventId,
            Feedback.nodeAction(
                node, AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS));
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
      long currentTime = SystemClock.uptimeMillis();
      boolean result =
          pipeline.returnFeedback(
              eventId, Feedback.nodeAction(node, AccessibilityNodeInfo.ACTION_FOCUS));
      LogUtils.d(
          TAG,
          "Perform input focus action:result=%s\n" + " eventId=%s," + " Node=%s",
          result,
          eventId,
          node);
      if (result) {
        actorState.setInputFocus(node, currentTime);
      }
    }
    return performAccessibilityFocusActionInternal(node, focusActionInfo, eventId);
  }

  void updateFocusHistory(AccessibilityNodeInfoCompat pivot, FocusActionInfo focusActionInfo) {
    // Cache the accessibility focus action history.
    long currentTime = SystemClock.uptimeMillis();
    AccessibilityNodeInfoCompat newFocus = null;

    newFocus = focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY);

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
          updatedFocusActionInfo, currentTime, screenState.getStableScreenState());
    } else {
      history.onAccessibilityFocusAction(
          newFocus, updatedFocusActionInfo, currentTime, screenState.getStableScreenState());
    }
  }

  /**
   * Checks the accessibility focus on the current screen. If no focused is found, requests the
   * initial focus by the following order:
   *
   * <ol>
   *   <li>Restore focus.
   *   <li>Input focus on editable view.
   *   <li>The first content on the window.
   * </ol>
   */
  boolean ensureAccessibilityFocusOnScreen(EventId eventId) {
    if (pipeline == null
        || !screenState.areMainWindowsStable()
        || screenState.getStableScreenState() == null) {
      return false;
    }

    if (focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY) != null) {
      // Has focus on screen.
      return true;
    }

    @Nullable FocusActionRecord record = history.getLastFocusActionRecord();
    if (record != null && record.getExtraInfo().isSourceEnsureOnScreen()) {
      // If the last focus record is also from the source ENSURE_ON_SCREEN, doesn't request to
      // ensure the focus repeatedly.
      return false;
    }

    // TODO: Consider to use the information of subtree change event to find the
    // initial focus.

    // Try to generate the focus on the same window with the last focused node. It avoids the focus
    // jumping to another window after a node tree changed. (Especially on IME windows.)
    @Nullable AccessibilityNodeInfoCompat nodeToFocus = findFocusableNodeFromFocusRecord(record);
    if (nodeToFocus != null) {
      FocusActionInfo focusActionInfo =
          FocusActionInfo.builder().setSourceAction(FocusActionInfo.ENSURE_ON_SCREEN).build();
      return pipeline.returnFeedback(
          eventId,
          Focus.builder()
              .setAction(Focus.Action.FOCUS)
              .setFocusActionInfo(focusActionInfo)
              .setTarget(nodeToFocus));
    }

    ArrayList<Feedback.Part> feedbackFailovers = new ArrayList<>();
    // If couldn't get the last focus record, find the initial focus in the same way with handling
    // window changes. Initial focus can be enabled in TV for feature parity with mobile
    // and consistency of input and accessibility focus.
    boolean isInitialFocusEnabled =
        !FeatureSupport.isTv(service) || TvNavigation.isInitialFocusEnabled(service);
    if (isInitialFocusEnabled) {
      feedbackFailovers.add(
          toFeedbackPart(INITIAL_FOCUS_RESTORE, screenState.getStableScreenState()));
    }
    feedbackFailovers.add(
        toFeedbackPart(INITIAL_FOCUS_FOLLOW_INPUT, screenState.getStableScreenState()));
    if (isInitialFocusEnabled) {
      feedbackFailovers.add(
          toFeedbackPart(INITIAL_FOCUS_FIRST_CONTENT, screenState.getStableScreenState()));
    }

    return pipeline.returnFeedback(Feedback.create(eventId, feedbackFailovers));
  }

  /**
   * Finds a focusable node from a {@link FocusActionRecord}. At first it will try to restore the
   * last focused node from the record. If the last focused node is no longer focusable, then
   * reports the first focusable node under the same root.
   *
   * @return a focusable node, null if none exists
   */
  @Nullable
  private AccessibilityNodeInfoCompat findFocusableNodeFromFocusRecord(FocusActionRecord record) {
    if (record == null) {
      return null;
    }

    AccessibilityNodeInfoCompat lastFocus = record.getFocusedNode();

    if (lastFocus == null) {
        return null;
      }

    AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(lastFocus);
      if (root == null || !root.refresh()) {
        return null;
      }

    // Try to restore focus by the focus record.
    AccessibilityNodeInfoCompat nodeToFocus =
        FocusActionRecord.getFocusableNodeFromFocusRecord(root, focusFinder, record);

      // If couldn't restore the focus from the record directly, then try to find focus node
      // on the same root.
      if (nodeToFocus == null) {
      OrderedTraversalStrategy strategy = new OrderedTraversalStrategy(root);
        Filter.NodeCompat nodeFilter =
            new Filter.NodeCompat((node) -> AccessibilityNodeInfoUtils.shouldFocusNode(node));
        nodeToFocus =
            TraversalStrategyUtils.findInitialFocusInNodeTree(
                strategy, root, SEARCH_FOCUS_FORWARD, nodeFilter);
      }

    return nodeToFocus;
  }

  /**
   * Creates {@link Feedback.Part} for focus actions.
   *
   * @param action The focus action
   * @param state Current screen state, it can't be null
   * @return Feedback.Part for the pipeline
   */
  private Feedback.Part toFeedbackPart(Focus.Action action, ScreenState state) {
    return Feedback.part().setFocus(Feedback.focus(action).setScreenState(state).build()).build();
  }

  /**
   * Returns whether the node is accessibility focused.
   *
   * <p><strong>Note:</strong> {@link #setAccessibilityFocus(AccessibilityNodeInfoCompat, boolean,
   * FocusActionInfo, EventId)} should use this method instead of directly invoking {@link
   * AccessibilityNodeInfoCompat#isAccessibilityFocused()}. This is in case that if we want to
   * bypass framework's touch exploration and maintain our own accessibility focus, we can easily
   * override this method.
   */
  private boolean isAccessibilityFocused(AccessibilityNodeInfoCompat node) {
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
        pipeline.returnFeedback(
            eventId,
            Feedback.nodeAction(node, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS));
    if (result) {
      focusActionInfo = updateFocusActionInfoIfNecessary(focusActionInfo, node);
      // AccessibilityFocusActionHistory makes copy of the node, no need to obtain() here.
      history.onAccessibilityFocusAction(
          node, focusActionInfo, currentTime, screenState.getStableScreenState());
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
      try {
        if (focusActionInfo.sourceAction == FocusActionInfo.SCREEN_STATE_CHANGE) {
          FocusActionInfo modifiedFocusActionInfo =
              new FocusActionInfo.Builder(focusActionInfo).forceMuteFeedback().build();

          if (focusActionInfo != modifiedFocusActionInfo) {
            LogUtils.d(TAG, "FocusActionInfo modified.");
            return modifiedFocusActionInfo;
          }
        }
      } finally {
        // Reset mute option regardless of sourceAction. Sometimes it doesn't mute because the
        // source action does not come from SCREEN_STATE_CHANGE, so mute focus from next screen
        // state change.
        muteNextFocus = false;
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
   * @return {@code true} if successfully perform {@link
   *     AccessibilityNodeInfo#ACTION_CLEAR_ACCESSIBILITY_FOCUS} on the given node.
   */
  boolean clearAccessibilityFocus(AccessibilityNodeInfoCompat currentNode, EventId eventId) {
    return pipeline.returnFeedback(
        eventId,
        Feedback.nodeAction(currentNode, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS));
  }

  void clearAccessibilityFocus(EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus = null;

    currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (currentFocus != null) {
      clearAccessibilityFocus(currentFocus, eventId);
    }
  }
}
