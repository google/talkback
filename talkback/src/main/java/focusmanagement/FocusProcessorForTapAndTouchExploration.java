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

import android.os.Message;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;

/** The {@link FocusProcessor} to handle accessibility focus during touch interaction. */
public class FocusProcessorForTapAndTouchExploration extends FocusProcessor {

  /** The timeout after which an event is no longer considered a tap. */
  private static final long TAP_TIMEOUT_MS = ViewConfiguration.getJumpTapTimeout();

  private static final FocusActionInfo REFOCUS_ACTION_INFO =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.TOUCH_EXPLORATION)
          .setIsFromRefocusAction(true)
          .build();

  private static final FocusActionInfo NON_REFOCUS_ACTION_INFO =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.TOUCH_EXPLORATION)
          .setIsFromRefocusAction(false)
          .build();

  private FocusManagerInternal mFocusManagerInternal;

  // TODO: Consider to break dependency between this class and other TalkBack components.
  private SpeechController.Delegate mSpeechControllerDelegate;
  private SpeechController mSpeechController;
  private FeedbackController mFeedbackController;

  private PostDelayHandler mPostDelayHandler;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Settings

  private boolean mIsSingleTapEnabled = false;
  private boolean mIsLiftToTypeEnabled = false;

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
  private boolean mMayBeRefocusAction = true;

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
  private boolean mMayBeSingleTap = true;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Important nodes during the touch interaction.

  // The first non-null focusable node being touched.
  private AccessibilityNodeInfoCompat mFirstFocusableNodeBeingTouched;
  // The last non-null focusable node being touched.
  private AccessibilityNodeInfoCompat mLastFocusableNodeBeingTouched;

  private long mTouchInteractionStartTime;

  FocusProcessorForTapAndTouchExploration(
      FocusManagerInternal focusManagerInternal,
      SpeechController.Delegate speechControllerDelegate,
      SpeechController speechController,
      FeedbackController feedbackController) {
    mFocusManagerInternal = focusManagerInternal;
    mSpeechControllerDelegate = speechControllerDelegate;
    mSpeechController = speechController;
    mFeedbackController = feedbackController;
    mPostDelayHandler = new PostDelayHandler(this);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Setters and getters for Setting values.

  public void setSingleTapEnabled(boolean enabled) {
    mIsSingleTapEnabled = enabled;
  }

  public boolean getSingleTapEnabled() {
    return mIsSingleTapEnabled;
  }

  public void setLiftToTypeEnabled(boolean enabled) {
    mIsLiftToTypeEnabled = enabled;
  }

  public boolean getLiftToTypeEnabled() {
    return mIsLiftToTypeEnabled;
  }

  /** Called when the user performs a {@link TouchExplorationAction}. */
  @Override
  public void onTouchExplorationAction(
      TouchExplorationAction touchExplorationAction, EventId eventId) {
    switch (touchExplorationAction.type) {
      case TouchExplorationAction.TOUCH_INTERACTION_START:
        handleTouchInteractionStart();
        break;
      case TouchExplorationAction.HOVER_ENTER:
        handleHoverEnterNode(touchExplorationAction.touchedNode, eventId);
        break;
      case TouchExplorationAction.TOUCH_INTERACTION_END:
        handleTouchInteractionEnd(eventId);
        break;
      default:
        // Do nothing.
        break;
    }
  }

  /**
   * Handles the beginning of a new touch interaction event. It cancels the ongoing speech and reset
   * variables.
   */
  private void handleTouchInteractionStart() {
    // Reset cached information at the beginning of a new touch interaction cycle.
    reset();

    mTouchInteractionStartTime = SystemClock.uptimeMillis();

    if (mSpeechController.isSpeaking()) {
      // We'll not refocus nor re-announce a node if TalkBack is currently speaking.
      mMayBeRefocusAction = false;
      legacyInterruptTalkBackFeedback();
    }
  }

  /** Resets the cached information of the current touch interaction. */
  private void reset() {
    AccessibilityNodeInfoUtils.recycleNodes(
        mFirstFocusableNodeBeingTouched, mLastFocusableNodeBeingTouched);
    mFirstFocusableNodeBeingTouched = null;
    mLastFocusableNodeBeingTouched = null;

    // Everything is possible at the beginning.
    mMayBeRefocusAction = true;
    mMayBeSingleTap = true;
  }

  // TODO: This logic is irrelevant to focus. Think about to move it somewhere else.
  private void legacyInterruptTalkBackFeedback() {
    // If TalkBack is talking, interrupt the ongoing feedback when the user touches down on the
    // screen.
    // Except for:
    // 1. When the tutorial is active. TODO: Check if it's a unnecessary legacy workaround.
    // 2. When the WebView is active.
    // This works around an issue where the IME is unintentionally dismissed by WebView's
    // performAction implementation.

    final AccessibilityNodeInfoCompat currentFocus = mFocusManagerInternal.getAccessibilityFocus();
    // Don't silence speech on first touch if the tutorial is active
    // or if a WebView is active. This works around an issue where
    // the IME is unintentionally dismissed by WebView's
    // performAction implementation.
    if (!AccessibilityTutorialActivity.isTutorialActive()
        && Role.getRole(currentFocus) != Role.ROLE_WEB_VIEW) {
      mSpeechControllerDelegate.interruptAllFeedback(false /* stopTtsSpeechCompletely */);
    }
    AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
  }

  /** Handles hover enter events. */
  private void handleHoverEnterNode(AccessibilityNodeInfoCompat touchedNode, EventId eventId) {
    if (touchedNode == null) {
      // Invalid hover enter event.
      return;
    }

    AccessibilityNodeInfoCompat focusableNode =
        AccessibilityNodeInfoUtils.findFocusFromHover(touchedNode);
    if (focusableNode == null) {
      // When focusableNode is null, there could be two cases:
      // 1. The user is touching on a non-focusable area.
      // 2. The user is touching on a focusable area, but framework sends redundant HOVER_ENTER
      // event from the container node, from witch the calculated focusableNode is null.
      // It's hard to distinguish between the two cases, we don't do anything if the focusableNode
      // is null.

      // TODO: Move the this delay into TouchExplorationInterpreter. Don't send this hover
      // enter action if it's flushed by other actions.
      mPostDelayHandler.playEmptyTouchFeedbackAfterTimeout();
      return;
    } else {
      mPostDelayHandler.cancelEmptyTouchFeedback();
    }

    mPostDelayHandler.cancelLongPress();
    mPostDelayHandler.cancelRefocusTimeout();

    if (mFirstFocusableNodeBeingTouched == null) {
      mFirstFocusableNodeBeingTouched = AccessibilityNodeInfoUtils.obtain(focusableNode);
      // Handle the first focusable node being touched.
      onHoverEnterFirstFocusableNode(focusableNode, eventId);
    } else {
      onHoverEnterGeneralFocusableNode(focusableNode, eventId);
    }
    mLastFocusableNodeBeingTouched = AccessibilityNodeInfoUtils.obtain(focusableNode);
    AccessibilityNodeInfoUtils.recycleNodes(focusableNode);
  }

  private void onHoverEnterFirstFocusableNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (!node.isAccessibilityFocused()) {
      onHoverEnterGeneralFocusableNode(node, eventId);
      return;
    }

    if (mIsSingleTapEnabled) {
      // Post delay to refocus on it.
      // If user hovers enter another node before timeout, cancel refocus action.
      // If user lifts finger before timeout, cancel refocus action and click(single-tap) on the
      // node.
      mPostDelayHandler.refocusAfterTimeout();
    } else if (mMayBeRefocusAction) {
      attemptRefocusNode(node, eventId);
    }
  }

  private void onHoverEnterGeneralFocusableNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    // We'll try to focus on some node, it must not be a single tap action, nor a refocus action.
    mMayBeSingleTap = false;
    mMayBeRefocusAction = false;

    setAccessibilityFocus(node, /* forceRefocusIfAlreadyFocused= */ false, eventId);
  }

  /**
   * Handles the end of an ongoing touch interaction event. Tries to perform click action in
   * single-tap mode or lift-to-type mode.
   */
  private void handleTouchInteractionEnd(EventId eventId) {
    // Touch interaction end, clear all the post-delayed actions.
    // TODO: Shall we also cancel empty touch feedback?
    mPostDelayHandler.cancelRefocusTimeout();
    mPostDelayHandler.cancelLongPress();

    long currentTime = SystemClock.uptimeMillis();

    if (mIsSingleTapEnabled
        && mMayBeSingleTap
        && (currentTime - mTouchInteractionStartTime < TAP_TIMEOUT_MS)) {
      // Perform click for single-tap mode.
      performClick(mLastFocusableNodeBeingTouched, eventId);
    } else if (mIsLiftToTypeEnabled
        && (Role.getRole(mLastFocusableNodeBeingTouched) == Role.ROLE_KEYBOARD_KEY)) {
      // Perform click action for lift-to-type mode.
      performClick(mLastFocusableNodeBeingTouched, eventId);
    }

    reset();
  }

  private boolean attemptLongPress(AccessibilityNodeInfoCompat node, EventId eventId) {
    return PerformActionUtils.performAction(node, AccessibilityNodeInfo.ACTION_LONG_CLICK, eventId);
  }

  private boolean attemptRefocusNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (!mMayBeRefocusAction || node == null) {
      return false;
    }

    return mFocusManagerInternal.clearAccessibilityFocus(node, eventId)
        && setAccessibilityFocus(node, /* forceRefocusIfAlreadyFocused= */ true, eventId);
  }

  private boolean setAccessibilityFocus(
      AccessibilityNodeInfoCompat node, boolean forceRefocusIfAlreadyFocused, EventId eventId) {
    final FocusActionInfo info =
        forceRefocusIfAlreadyFocused ? REFOCUS_ACTION_INFO : NON_REFOCUS_ACTION_INFO;
    boolean result =
        mFocusManagerInternal.setAccessibilityFocus(
            node, forceRefocusIfAlreadyFocused, info, eventId);

    if (result && mIsLiftToTypeEnabled && (Role.getRole(node) == Role.ROLE_KEYBOARD_KEY)) {
      mPostDelayHandler.longPressAfterTimeout();
    }
    return result;
  }

  private void performClick(AccessibilityNodeInfoCompat node, EventId eventId) {
    // Performing a click on an EditText does not show the IME, so we need
    // to place input focus on it. If the IME was already connected and is
    // hidden, there is nothing we can do.
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS, eventId);
      return;
    }

    // If a user quickly touch explores in web content (event stream <
    // TAP_TIMEOUT_MS), we'll send an unintentional ACTION_CLICK. Switch
    // off clicking on web content for now.
    // TODO: Verify if it's a legacy feature.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      return;
    }

    PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK, eventId);
  }

  private static class PostDelayHandler
      extends WeakReferenceHandler<FocusProcessorForTapAndTouchExploration> {
    private static final int MSG_REFOCUS = 1;
    private static final int MSG_FEEDBACK_EMPTY_TOUCH_AREA = 2;
    private static final int MSG_LONG_CLICK_LAST_NODE = 3;

    /** Delay for indicating the user has explored into an un-focusable area. */
    private static final long EMPTY_TOUCH_AREA_DELAY_MS = 100;

    // TODO: ViewConfiguration.getLongPressTimeout() is too short in this case, we need to
    // manually extend the timeout.
    private static final long LONG_CLICK_DELAY_MS = 1000;

    private PostDelayHandler(FocusProcessorForTapAndTouchExploration parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, FocusProcessorForTapAndTouchExploration parent) {
      if (parent == null) {
        return;
      }
      switch (msg.what) {
        case MSG_REFOCUS:
          parent.attemptRefocusNode(parent.mLastFocusableNodeBeingTouched, null);

          break;
        case MSG_FEEDBACK_EMPTY_TOUCH_AREA:
          // TODO: We should provide this feedback by sending Compositor event, and break
          // dependency on FeedbackController.
          parent.mFeedbackController.playHaptic(R.array.view_hovered_pattern);
          parent.mFeedbackController.playAuditory(R.raw.view_entered, 1.3f, 1);
          break;
        case MSG_LONG_CLICK_LAST_NODE:
          if (Role.getRole(parent.mLastFocusableNodeBeingTouched) == Role.ROLE_KEYBOARD_KEY) {
            parent.attemptLongPress(parent.mLastFocusableNodeBeingTouched, /* eventId= */ null);
          }
          break;
        default:
          break;
      }
    }

    private void longPressAfterTimeout() {
      removeMessages(MSG_LONG_CLICK_LAST_NODE);
      sendEmptyMessageDelayed(MSG_LONG_CLICK_LAST_NODE, LONG_CLICK_DELAY_MS);
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

    /** Provides feedback indicating an empty or unfocusable area after a delay. */
    private void playEmptyTouchFeedbackAfterTimeout() {
      cancelEmptyTouchFeedback();

      final Message msg = obtainMessage(MSG_FEEDBACK_EMPTY_TOUCH_AREA);
      sendMessageDelayed(msg, EMPTY_TOUCH_AREA_DELAY_MS);
    }

    /**
     * Cancel any pending messages for delivering feedback indicating an empty or unfocusable area.
     */
    private void cancelEmptyTouchFeedback() {
      removeMessages(MSG_FEEDBACK_EMPTY_TOUCH_AREA);
    }
  }

  // TODO: Remove the legacy implementation of AccessibilityEventListener.
  @Override
  public int getEventTypes() {
    return 0;
  }

  // TODO: Remove the legacy implementation of AccessibilityEventListener.
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {}
}
