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

package com.google.android.accessibility.talkback.interpreters;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static com.google.android.accessibility.talkback.Interpretation.ID.Value.ACCESSIBILITY_FOCUSED;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.AccessibilityFocusEventInterpretation;
import com.google.android.accessibility.compositor.AccessibilityFocusEventInterpreter;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForScreenStateChange;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor.ScreenStateChangeListener;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.TouchExplorationInterpreter.TouchExplorationActionListener;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.NodePathDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Accessibility focus event interpreter that consumes semi-interpreted events from other
 * event-interpreters, then sends completed interpretations to pipeline.
 */
public class AccessibilityFocusInterpreter
    implements AccessibilityFocusEventInterpreter,
        ScreenStateChangeListener,
        ScrollEventHandler,
        TouchExplorationActionListener {
  public static final String TAG = "A11yFocusInterp";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final FocusProcessorForTapAndTouchExploration focusProcessorForTapAndTouchExploration;
  private final FocusProcessorForScreenStateChange focusProcessorForScreenStateChange;
  private Pipeline.InterpretationReceiver pipelineInterpretations;
  private ActorState actorState;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public AccessibilityFocusInterpreter(
      FocusFinder focusFinder,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      PrimesController primesController) {

    this.accessibilityFocusMonitor = accessibilityFocusMonitor;

    focusProcessorForTapAndTouchExploration = new FocusProcessorForTapAndTouchExploration();
    focusProcessorForScreenStateChange =
        new FocusProcessorForScreenStateChange(accessibilityFocusMonitor);
  }

  public void setPipeline(Pipeline.InterpretationReceiver pipeline) {
    this.pipelineInterpretations = pipeline;
    this.focusProcessorForTapAndTouchExploration.setInterpretationReceiver(pipeline);
    focusProcessorForScreenStateChange.setPipeline(pipeline);
    focusProcessorForTapAndTouchExploration.setInterpretationReceiver(pipeline);
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
    focusProcessorForTapAndTouchExploration.setActorState(actorState);
    focusProcessorForScreenStateChange.setActorState(actorState);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Dispatches user actions

  /** Called by TouchExplorationInterpreter. */
  @Override
  public boolean onTouchExplorationAction(TouchExplorationAction action, EventId eventId) {
    LogUtils.d(TAG, "User action: %s", action);
    return focusProcessorForTapAndTouchExploration.onTouchExplorationAction(action, eventId);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Notifies changes from framework

  /** Called by {@link ScreenStateMonitor}. */
  @Override
  public boolean onScreenStateChanged(ScreenState screenState, EventId eventId) {
    return focusProcessorForScreenStateChange.onScreenStateChanged(screenState, eventId);
  }

  /** Event-interpreter function, called by ScrollEventInterpreter. */
  @Override
  public void onScrollEvent(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {

    LogUtils.d(TAG, "On scroll: Interpretation=%s; Event=%s", interpretation, event);
    if ((interpretation.userAction != ScrollEventInterpreter.ACTION_MANUAL_SCROLL)
        || (interpretation.scrollDirection == TraversalStrategy.SEARCH_FOCUS_UNKNOWN)) {
      return;
    }
    AccessibilityNodeInfoCompat currentA11yFocusedNode = null;
    AccessibilityNodeInfoCompat scrolledNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (scrolledNode == null) {
      return;
    }
    try {

      currentA11yFocusedNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      if (AccessibilityNodeInfoUtils.shouldFocusNode(currentA11yFocusedNode)) {
        return;
      }

      NodePathDescription lastFocusNodePathDescription =
          actorState.getFocusHistory().getLastFocusNodePathDescription();
      if (lastFocusNodePathDescription == null) {
        return;
      }

      // Match ancestor node.  Before android-OMR1, need refresh to get viewIdResourceName.
      if (!BuildVersionUtils.isAtLeastOMR1()) {
        scrolledNode.refresh();
      }
      if (!lastFocusNodePathDescription.containsNodeByHashAndIdentity(scrolledNode)) {
        return;
      }

      pipelineInterpretations.input(
          eventId, event, new Interpretation.Scroll(interpretation.scrollDirection));

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(scrolledNode, currentA11yFocusedNode);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // APIs used by other TalkBack components

  /** Called by EventFilter. */
  @Nullable
  @Override
  public AccessibilityFocusEventInterpretation interpret(AccessibilityEvent event) {
    // For user interface interaction (such as quick menu to handle slider/number-picker).
    if (event.getEventType() == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      pipelineInterpretations.input(
          Performance.getInstance().onEventReceived(event),
          event,
          new Interpretation.ID(ACCESSIBILITY_FOCUSED));
    }
    FocusActionInfo info = actorState.getFocusHistory().getFocusActionInfoFromEvent(event);
    if (info == null) {
      return null;
    }
    AccessibilityFocusEventInterpretation interpretation =
        new AccessibilityFocusEventInterpretation(event.getEventType());
    interpretation.setForceFeedbackAudioPlaybackActive(info.isForcedFeedbackAudioPlaybackActive());
    interpretation.setForceFeedbackMicrophoneActive(info.isForcedFeedbackMicrophoneActive());
    interpretation.setForceFeedbackSsbActive(info.isForcedFeedbackSsbActive());
    interpretation.setShouldMuteFeedback(info.forceMuteFeedback);
    interpretation.setIsInitialFocusAfterScreenStateChange(
        info.sourceAction == FocusActionInfo.SCREEN_STATE_CHANGE);
    interpretation.setIsNavigateByUser(
        info.sourceAction == FocusActionInfo.TOUCH_EXPLORATION
            || info.sourceAction == FocusActionInfo.LOGICAL_NAVIGATION);
    return interpretation;
  }

  /**
   * Sets whether single-tap activation is enabled.
   *
   * @param enabled Whether single-tap activation is enabled.
   */
  public void setSingleTapEnabled(boolean enabled) {
    focusProcessorForTapAndTouchExploration.setSingleTapEnabled(enabled);
  }

  /**
   * Gets whether single-tap activation is enabled.
   *
   * @return Whether single-tap activation is enabled.
   */
  public boolean getSingleTapEnabled() {
    return focusProcessorForTapAndTouchExploration.getSingleTapEnabled();
  }

}
