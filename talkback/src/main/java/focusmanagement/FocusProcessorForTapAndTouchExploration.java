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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_FOCUS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK;

import android.accessibilityservice.AccessibilityService;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.ViewConfiguration;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.controller.FullScreenReadController;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.output.SpeechController;

/** The {@link FocusProcessor} to handle accessibility focus during touch interaction. */
public class FocusProcessorForTapAndTouchExploration {

  /** The timeout after which an event is no longer considered a tap. */
  private static final long TAP_TIMEOUT_MS = ViewConfiguration.getJumpTapTimeout();

  // It is copied from  Gboard's code.
  private static final long LIFT_TO_TYPE_LONG_PRESS_DELAY_MS = 3000;

  @VisibleForTesting
  protected static final FocusActionInfo REFOCUS_ACTION_INFO =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.TOUCH_EXPLORATION)
          .setIsFromRefocusAction(true)
          .build();

  @VisibleForTesting
  protected static final FocusActionInfo NON_REFOCUS_ACTION_INFO =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.TOUCH_EXPLORATION)
          .setIsFromRefocusAction(false)
          .build();

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  private PostDelayHandler postDelayHandler;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Settings

  private boolean isSingleTapEnabled = false;

  /** This feature doesn't need UI option. We enable/disable it by the flag */
  private boolean isLiftToTypeEnabled = true;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Boolean values representing states in the state machine.

  /**
   * Whether the interaction might lead to refocus action.
   *
   * <p>It's initially set to {@code true}. It's set to {@code false} if:
   *
   * <ul>
   *   <li>{@link SpeechController} is speaking on {@link
   *       TouchExplorationAction#TOUCH_INTERACTION_START} action
   *   <li>When the first non-null focusable node is touched, the node is not accessibility focused.
   * </ul>
   */
  private boolean mayBeRefocusAction = true;

  /**
   * Whether the interaction might lead to single tap(click) action.
   *
   * <p>It's initially set to {@code true}. It's set to {@code false} if:
   *
   * <ul>
   *   <li>The first focusable node being touched is not already accessibilityy focused.
   *   <li>The user touches on more than on focusable node during the interaction.
   * </ul>
   */
  private boolean mayBeSingleTap = true;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Important nodes during the touch interaction.

  // Whether the user hovers enter any node during touch exploration.
  private boolean hasHoveredEnterNode = false;

  // The first focusable node being touched.
  @Nullable private AccessibilityNodeInfoCompat firstFocusableNodeBeingTouched;
  // The last focusable node being touched.
  @Nullable private AccessibilityNodeInfoCompat lastFocusableNodeBeingTouched;

  /**
   * Whether the interaction might lead to a click action.
   *
   * <p>It's initially set to {@code true}. It's set to {@code false} if the last touched node
   * performs a long click action:
   */
  private boolean mayBeLiftToType = true;

  private long touchInteractionStartTime;
  private FullScreenReadController fullScreenReadController;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Contstructor methods

  FocusProcessorForTapAndTouchExploration(
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      AccessibilityService service,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    postDelayHandler = new PostDelayHandler(this, LIFT_TO_TYPE_LONG_PRESS_DELAY_MS);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Setters and getters for Setting values.

  public void setSingleTapEnabled(boolean enabled) {
    isSingleTapEnabled = enabled;
  }

  public boolean getSingleTapEnabled() {
    return isSingleTapEnabled;
  }

  public void setLiftToTypeEnabled(boolean enabled) {
    isLiftToTypeEnabled = enabled;
  }

  public boolean getLiftToTypeEnabled() {
    return isLiftToTypeEnabled;
  }

  public boolean onTouchExplorationAction(
      TouchExplorationAction touchExplorationAction, EventId eventId) {
    switch (touchExplorationAction.type) {
      case TouchExplorationAction.TOUCH_INTERACTION_START:
        return handleTouchInteractionStart(eventId);
      case TouchExplorationAction.HOVER_ENTER:
        return handleHoverEnterNode(touchExplorationAction.touchedFocusableNode, eventId);
      case TouchExplorationAction.TOUCH_INTERACTION_END:
        return handleTouchInteractionEnd(eventId);
      default:
        // Do nothing.
        return false;
    }
  }

  /**
   * Handles the beginning of a new touch interaction event. It cancels the ongoing speech and reset
   * variables.
   *
   * @return {@code true} if successfully performs an accessibility action.
   */
  private boolean handleTouchInteractionStart(EventId eventId) {
    // Reset cached information at the beginning of a new touch interaction cycle.
    reset();

    touchInteractionStartTime = SystemClock.uptimeMillis();

    if (actorState.getSpeechState().isSpeaking()) {
      // We'll not refocus nor re-announce a node if TalkBack is currently speaking.
      mayBeRefocusAction = false;
      legacyInterruptTalkBackFeedback(eventId);
    }
    // Always return false because no accessibility action is performed.
    return false;
  }

  /** Resets the cached information of the current touch interaction. */
  private void reset() {
    AccessibilityNodeInfoUtils.recycleNodes(
        firstFocusableNodeBeingTouched, lastFocusableNodeBeingTouched);
    firstFocusableNodeBeingTouched = null;
    lastFocusableNodeBeingTouched = null;

    hasHoveredEnterNode = false;

    // Everything is possible at the beginning.
    mayBeRefocusAction = true;
    mayBeSingleTap = true;
    mayBeLiftToType = true;
  }

  // TODO: This logic is irrelevant to focus. Think about to move it somewhere else.
  private void legacyInterruptTalkBackFeedback(EventId eventId) {
    // If TalkBack is talking, interrupt the ongoing feedback when the user touches down on the
    // screen.
    // Except for:
    // 1. When the tutorial is active. TODO: Check if it's a unnecessary legacy workaround.
    // 2. When the WebView is active.
    // This works around an issue where the IME is unintentionally dismissed by WebView's
    // performAction implementation.

    final AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    // Don't silence speech on first touch if the tutorial is active
    // or if a WebView is active. This works around an issue where
    // the IME is unintentionally dismissed by WebView's
    // performAction implementation.
    if (!AccessibilityTutorialActivity.isTutorialActive()
        && Role.getRole(currentFocus) != Role.ROLE_WEB_VIEW) {
      if (fullScreenReadController != null && fullScreenReadController.isActive()) {
        pipeline.returnFeedback(eventId, Feedback.part().setInterruptSoundAndVibration(true));
      } else {
        pipeline.returnFeedback(eventId, Feedback.part().setInterruptAllFeedback(true));
      }
    }
    AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
  }

  /**
   * Handles hover enter events.
   *
   * @return {@code true} if successfully performs an accessibility action.
   */
  private boolean handleHoverEnterNode(
      @Nullable AccessibilityNodeInfoCompat touchedFocusableNode, EventId eventId) {
    postDelayHandler.cancelLongPress();
    postDelayHandler.cancelRefocusTimeout();

    boolean result;
    if (!hasHoveredEnterNode) {
      firstFocusableNodeBeingTouched = AccessibilityNodeInfoUtils.obtain(touchedFocusableNode);
      hasHoveredEnterNode = true;
      // Handle the first node being touched.
      result = onHoverEnterFirstNode(touchedFocusableNode, eventId);
    } else {
      result = onHoverEnterGeneralNode(touchedFocusableNode, eventId);
    }

    // Reset it when last touched node is changed.
    mayBeLiftToType = true;
    lastFocusableNodeBeingTouched = AccessibilityNodeInfoUtils.obtain(touchedFocusableNode);
    return result;
  }

  /**
   * Handles the first node being touched during touch exploration.
   *
   * @return {@code true} if successfully performs an accessibility action.
   */
  private boolean onHoverEnterFirstNode(
      @Nullable AccessibilityNodeInfoCompat touchedFocusableNode, EventId eventId) {
    if (touchedFocusableNode == null || !touchedFocusableNode.isAccessibilityFocused()) {
      return onHoverEnterGeneralNode(touchedFocusableNode, eventId);
    }

    if (!mayBeRefocusAction) {
      // If the first node is already a11y focused and we'll not refocus on that, schedule
      // long press action if it's a keyboard key.
      if (isLiftToTypeEnabled
          && supportsLiftToType(touchedFocusableNode)
          && AccessibilityNodeInfoUtils.isLongClickable(touchedFocusableNode)) {
        postDelayHandler.longPressAfterTimeout();
      }
      return false;
    }

    if (isSingleTapEnabled) {
      // Post delay to refocus on it.
      // If user hovers enter another node before timeout, cancel refocus action.
      // If user lifts finger before timeout, cancel refocus action and click(single-tap) on the
      // node.
      postDelayHandler.refocusAfterTimeout();
    } else {
      return attemptRefocusNode(touchedFocusableNode, eventId);
    }
    return false;
  }

  /** @return {@code true} if the role of node support lift-to-type functionality. */
  private boolean supportsLiftToType(AccessibilityNodeInfoCompat accessibilityNodeInfoCompat) {
    return (Role.getRole(accessibilityNodeInfoCompat) == Role.ROLE_TEXT_ENTRY_KEY);
  }

  /** @return {@code true} if successfully performs an accessibility action. */
  private boolean onHoverEnterGeneralNode(
      AccessibilityNodeInfoCompat touchedFocusableNode, EventId eventId) {
    // We'll try to focus on some node, it must not be a single tap action, nor a refocus action.
    mayBeSingleTap = false;
    mayBeRefocusAction = false;
    if (touchedFocusableNode == null) {
      pipeline.returnFeedback(
          eventId, Feedback.sound(R.raw.view_entered).vibration(R.array.view_hovered_pattern));
      return false;
    } else {
      // Fix . Force focus the node if it is not the last focused node. The accessibility
      // focus may not update immediately after {@link
      // AccessibilityNodeInfoCompat#performAction(int)}, this may easily happened when receiving
      // multiple hover events in a short time. so we check last focused node is equal to current
      // touched node by calling {@link
      // FocusManagerInternal#lastAccessibilityFocusedNodeEquals(AccessibilityNodeInfoCompat)}.
      boolean forceRefocusIfAlreadyFocused =
          !actorState.getFocusHistory().lastAccessibilityFocusedNodeEquals(touchedFocusableNode);
      return setAccessibilityFocus(touchedFocusableNode, forceRefocusIfAlreadyFocused, eventId);
    }
  }

  /**
   * Handles the end of an ongoing touch interaction event. Tries to perform click action in
   * single-tap mode or lift-to-type mode.
   *
   * @return {@code true} if successfully performs an accessibility action.
   */
  private boolean handleTouchInteractionEnd(EventId eventId) {
    // Touch interaction end, clear all the post-delayed actions.
    // TODO: Shall we also cancel empty touch feedback?
    postDelayHandler.cancelRefocusTimeout();
    postDelayHandler.cancelLongPress();

    long currentTime = SystemClock.uptimeMillis();

    boolean result = false;
    if (isSingleTapEnabled
        && mayBeSingleTap
        && (currentTime - touchInteractionStartTime < TAP_TIMEOUT_MS)) {
      // Perform click for single-tap mode.
      result = performClick(lastFocusableNodeBeingTouched, eventId);
    } else if (isLiftToTypeEnabled
        && supportsLiftToType(lastFocusableNodeBeingTouched)
        && mayBeLiftToType) {
      // Perform click action for lift-to-type mode.
      result = performClick(lastFocusableNodeBeingTouched, eventId);
    }

    reset();
    return result;
  }

  private boolean attemptLongPress(AccessibilityNodeInfoCompat node, EventId eventId) {
    return pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_LONG_CLICK.getId()));
  }

  private boolean attemptRefocusNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    return mayBeRefocusAction
        && (node != null)
        && setAccessibilityFocus(node, /* forceRefocusIfAlreadyFocused= */ true, eventId);
  }

  private boolean setAccessibilityFocus(
      AccessibilityNodeInfoCompat node, boolean forceRefocusIfAlreadyFocused, EventId eventId) {
    final FocusActionInfo info =
        forceRefocusIfAlreadyFocused ? REFOCUS_ACTION_INFO : NON_REFOCUS_ACTION_INFO;
    boolean result =
        pipeline.returnFeedback(
            eventId, Feedback.focus(node, info).setForceRefocus(forceRefocusIfAlreadyFocused));
    if (result
        && isLiftToTypeEnabled
        && supportsLiftToType(node)
        && AccessibilityNodeInfoUtils.isLongClickable(node)) {
      postDelayHandler.longPressAfterTimeout();
    }
    return result;
  }

  private boolean performClick(AccessibilityNodeInfoCompat node, EventId eventId) {
    // Performing a click on an EditText does not show the IME, so we need
    // to place input focus on it. If the IME was already connected and is
    // hidden, there is nothing we can do.
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_FOCUS.getId()));
    }

    // If a user quickly touch explores in web content (event stream <
    // TAP_TIMEOUT_MS), we'll send an unintentional ACTION_CLICK. Switch
    // off clicking on web content for now.
    // TODO: Verify if it's a legacy feature.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      return false;
    }

    return pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_CLICK.getId()));
  }

  public void setFullScreenReadController(FullScreenReadController fullScreenReadController) {
    this.fullScreenReadController = fullScreenReadController;
  }

  private static class PostDelayHandler
      extends WeakReferenceHandler<FocusProcessorForTapAndTouchExploration> {
    private static final int MSG_REFOCUS = 1;
    private static final int MSG_LONG_CLICK_LAST_NODE = 2;

    private long longPressDelayMs;

    private PostDelayHandler(
        FocusProcessorForTapAndTouchExploration parent, long longPressDelayMs) {
      super(parent);
      this.longPressDelayMs = longPressDelayMs;
    }

    @Override
    protected void handleMessage(Message msg, FocusProcessorForTapAndTouchExploration parent) {
      if (parent == null) {
        return;
      }
      switch (msg.what) {
        case MSG_REFOCUS:
          parent.attemptRefocusNode(parent.lastFocusableNodeBeingTouched, null);
          break;
        case MSG_LONG_CLICK_LAST_NODE:
          if (parent.supportsLiftToType(parent.lastFocusableNodeBeingTouched)) {
            parent.attemptLongPress(parent.lastFocusableNodeBeingTouched, /* eventId= */ null);
            parent.mayBeLiftToType = false;
          }
          break;
        default:
          break;
      }
    }

    private void longPressAfterTimeout() {
      removeMessages(MSG_LONG_CLICK_LAST_NODE);
      sendEmptyMessageDelayed(MSG_LONG_CLICK_LAST_NODE, longPressDelayMs);
    }

    private void cancelLongPress() {
      removeMessages(MSG_LONG_CLICK_LAST_NODE);
    }

    private void refocusAfterTimeout() {
      removeMessages(MSG_REFOCUS);

      final Message msg = obtainMessage(MSG_REFOCUS);
      sendMessageDelayed(msg, TAP_TIMEOUT_MS);
    }

    private void cancelRefocusTimeout() {
      removeMessages(MSG_REFOCUS);
    }
  }
}
