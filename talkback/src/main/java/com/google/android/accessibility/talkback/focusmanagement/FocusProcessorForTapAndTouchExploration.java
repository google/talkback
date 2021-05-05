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

import static com.google.android.accessibility.compositor.Compositor.EVENT_INPUT_DESCRIBE_NODE;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.LIFT;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.LONG_PRESS;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.TAP;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.TOUCH_FOCUSED_NODE;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.TOUCH_NOTHING;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.TOUCH_START;
import static com.google.android.accessibility.talkback.Interpretation.Touch.Action.TOUCH_UNFOCUSED_NODE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.ViewConfiguration;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.SpeechController;

/** Event interpreter to handle accessibility focus during touch interaction. */
public class FocusProcessorForTapAndTouchExploration {

  /** The timeout after which an event is no longer considered a tap. */
  private static final long TAP_TIMEOUT_MS = ViewConfiguration.getJumpTapTimeout();

  // It is copied from  Gboard's code.
  private static final long LIFT_TO_TYPE_LONG_PRESS_DELAY_MS = 3000;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private Pipeline.InterpretationReceiver interpretationReceiver;
  private ActorState actorState;

  private PostDelayHandler postDelayHandler;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Settings

  private boolean isSingleTapEnabled = false;

  /** This feature doesn't need UI option. We enable/disable it by the flag */
  public static boolean ENABLE_LIFT_TO_TYPE = true;

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

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Contstructor methods

  public FocusProcessorForTapAndTouchExploration() {
    postDelayHandler = new PostDelayHandler(this, LIFT_TO_TYPE_LONG_PRESS_DELAY_MS);
  }

  public void setInterpretationReceiver(Pipeline.InterpretationReceiver interpretationReceiver) {
    this.interpretationReceiver = interpretationReceiver;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Setters and getters for Setting values.

  public void setSingleTapEnabled(boolean enabled) {
    isSingleTapEnabled = enabled;
  }

  public boolean getSingleTapEnabled() {
    return isSingleTapEnabled;
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
      interpretationReceiver.input(
          eventId, /* event= */ null, Interpretation.Touch.create(TOUCH_START));
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

    if (ENABLE_LIFT_TO_TYPE && supportsLiftToType(touchedFocusableNode)) {
      if (touchedFocusableNode.isAccessibilityFocused()) {
        mayBeRefocusAction = false;
        if (AccessibilityNodeInfoUtils.isLongClickable(touchedFocusableNode)) {
          postDelayHandler.longPressAfterTimeout();
        }

        interpretationReceiver.input(
            EVENT_ID_UNTRACKED,
            /* event= */ null,
            new Interpretation.CompositorID(EVENT_INPUT_DESCRIBE_NODE),
            touchedFocusableNode);
      } else {
        mayBeRefocusAction = true;
        if (AccessibilityNodeInfoUtils.isLongClickable(touchedFocusableNode)) {
          postDelayHandler.longPressAfterTimeout();
        }
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
      return touchFocusedNode(touchedFocusableNode, eventId);
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
      interpretationReceiver.input(
          eventId, /* event= */ null, Interpretation.Touch.create(TOUCH_NOTHING));
      return false;
    } else {
      return touchNewNode(touchedFocusableNode, eventId);
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
      result =
          interpretationReceiver.input(
              eventId,
              /* event= */ null,
              Interpretation.Touch.create(TAP, lastFocusableNodeBeingTouched));
    } else if (ENABLE_LIFT_TO_TYPE
        && supportsLiftToType(lastFocusableNodeBeingTouched)
        && mayBeLiftToType) {
      // Perform click action for lift-to-type mode.
      result =
          interpretationReceiver.input(
              eventId,
              /* event= */ null,
              Interpretation.Touch.create(LIFT, lastFocusableNodeBeingTouched));
    }

    reset();
    return result;
  }

  private boolean touchFocusedNode(AccessibilityNodeInfoCompat node, @Nullable EventId eventId) {
    return mayBeRefocusAction
        && (node != null)
        && interpretationReceiver.input(
            eventId, /* event= */ null, Interpretation.Touch.create(TOUCH_FOCUSED_NODE, node));
  }

  private boolean touchNewNode(AccessibilityNodeInfoCompat node, EventId eventId) {

    boolean result =
        interpretationReceiver.input(
            eventId, /* event= */ null, Interpretation.Touch.create(TOUCH_UNFOCUSED_NODE, node));

    if (result
        && ENABLE_LIFT_TO_TYPE
        && supportsLiftToType(node)
        && AccessibilityNodeInfoUtils.isLongClickable(node)) {
      postDelayHandler.longPressAfterTimeout();
    }
    return result;
  }

  /** Delays for detecting long-presses and unique touches */
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
          parent.touchFocusedNode(parent.lastFocusableNodeBeingTouched, /* eventId= */ null);
          break;
        case MSG_LONG_CLICK_LAST_NODE:
          if (parent.supportsLiftToType(parent.lastFocusableNodeBeingTouched)) {
            parent.interpretationReceiver.input(
                /* eventId= */ null,
                /* event= */ null,
                Interpretation.Touch.create(LONG_PRESS, parent.lastFocusableNodeBeingTouched));
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
