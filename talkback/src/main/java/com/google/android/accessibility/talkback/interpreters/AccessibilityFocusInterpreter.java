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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.AccessibilityFocusEventInterpretation;
import com.google.android.accessibility.compositor.AccessibilityFocusEventInterpreter;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForScreenStateChange;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.TypingMethod;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor.ScreenStateChangeListener;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.TouchExplorationInterpreter.TouchExplorationActionListener;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.interpreters.InputFocusInterpreter.TargetViewChangeListener;
import com.google.android.accessibility.talkback.interpreters.ManualScrollInterpreter.ManualScrollInterpretation;
import com.google.android.accessibility.talkback.interpreters.ManualScrollInterpreter.ScrolledViewChangeListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Accessibility focus event interpreter that consumes semi-interpreted events from other
 * event-interpreters, then sends completed interpretations to pipeline.
 */
public class AccessibilityFocusInterpreter
    implements AccessibilityFocusEventInterpreter,
        ScreenStateChangeListener,
        ScrolledViewChangeListener,
        TouchExplorationActionListener,
        TargetViewChangeListener {
  public static final String TAG = "A11yFocusInterp";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final FocusProcessorForTapAndTouchExploration focusProcessorForTapAndTouchExploration;
  private final FocusProcessorForScreenStateChange focusProcessorForScreenStateChange;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final ScreenStateMonitor.State screenState;

  private Pipeline.InterpretationReceiver pipelineInterpretations;
  private ActorState actorState;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public AccessibilityFocusInterpreter(
      AccessibilityFocusMonitor accessibilityFocusMonitor, ScreenStateMonitor.State screenState) {
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.screenState = screenState;
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

  /** Event-interpreter function, called by {@link ManualScrollInterpreter}. */
  @Override
  public void onManualScroll(ManualScrollInterpretation interpretation) {
    if (!screenState.areMainWindowsStable()) {
      LogUtils.w(
          TAG,
          "onScrollEvent return due to windows are not stable and the focus will"
              + " be handled by onScreenStateChanged after main windows are stable.");
      return;
    }

    AccessibilityNodeInfoCompat currentA11yFocusedNode = null;

    try {

      currentA11yFocusedNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      if (AccessibilityNodeInfoUtils.shouldFocusNode(currentA11yFocusedNode)) {
        return;
      }

      pipelineInterpretations.input(
          interpretation.eventId(),
          interpretation.event(),
          Interpretation.ManualScroll.create(
              interpretation.direction(), screenState.getStableScreenState()));

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentA11yFocusedNode);
    }
  }

  /** Event-interpreter function, called by {@link InputFocusInterpreter}. */
  @Override
  public void onViewTargeted(
      @Nullable EventId eventId,
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat targetedNode) {
    if (!screenState.areMainWindowsStable()) {
      LogUtils.w(
          TAG,
          "onViewTargeted return due to windows are not stable and the focus "
              + "will be handled by onScreenStateChanged after main windows are stable.");
      return;
    }
    pipelineInterpretations.input(eventId, event, new Interpretation.InputFocus(targetedNode));
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // APIs used by other TalkBack components

  /** Called by EventFilter. */
  @Nullable
  @Override
  public AccessibilityFocusEventInterpretation interpret(AccessibilityEvent event) {
    // For user interface interaction (such as quick menu to handle slider/number-picker) and image
    // caption.
    if (event.getEventType() == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      AccessibilityNodeInfoCompat node = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      // Skips caption if the view has already been labeled.
      boolean needsCaption =
          ImageCaptioner.supportsImageCaption()
              && ImageCaptionUtils.needImageCaption(event, node)
              && actorState.getCustomLabel().getLabelIdForViewId(node) == Label.NO_ID;
      pipelineInterpretations.input(
          Performance.getInstance().onEventReceived(event),
          event,
          Interpretation.AccessibilityFocused.create(needsCaption),
          node);
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

  /**
   * Sets type confirmation method
   *
   * @param type keyboard confirmation type
   */
  public void setTypingMethod(@TypingMethod int type) {
    focusProcessorForTapAndTouchExploration.setTypingMethod(type);
  }
}
