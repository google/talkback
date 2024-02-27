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

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Interpretation.ManualScroll;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.compositor.AccessibilityFocusEventInterpretation;
import com.google.android.accessibility.talkback.compositor.AccessibilityFocusEventInterpreter;
import com.google.android.accessibility.talkback.compositor.Compositor;
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
import com.google.android.accessibility.utils.DisplayUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
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
  private final Context context;

  private Pipeline.InterpretationReceiver pipelineInterpretations;
  private ActorState actorState;
  private final FormFactorUtils formFactorUtils;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public AccessibilityFocusInterpreter(
      Context context,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ScreenStateMonitor.State screenState,
      TalkBackAnalytics analytics) {
    this.context = context;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.screenState = screenState;
    focusProcessorForTapAndTouchExploration =
        new FocusProcessorForTapAndTouchExploration(analytics);
    focusProcessorForScreenStateChange =
        new FocusProcessorForScreenStateChange(accessibilityFocusMonitor);
    formFactorUtils = FormFactorUtils.getInstance();
  }

  public void performSplitTap(EventId eventId) {
    if (!focusProcessorForTapAndTouchExploration.performSplitTap(eventId)) {
      // Check whether the FocusProcessorForTapAndTouchExploration#performSplitTap succeeds.
      // TODO: Split-tap should be activated everywhere, not just IME.
      // If FocusProcessorForTapAndTouchExploration#performSplitTap returns false (
      // support the Text-Entry-Key for lift-to-type, we should enable this.
      // performClick(eventId);
    }
  }
  // TODO: Split-tap should be activated everywhere, not just IME.
  //  private void performClick(EventId eventId) {
  //    AccessibilityNodeInfoCompat currentA11yFocusedNode =
  //        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
  //    if (currentA11yFocusedNode != null) {
  //      pipelineInterpretations.input(
  //          eventId, /* event= */ null, Interpretation.Touch.create(LIFT,
  // currentA11yFocusedNode));
  //    }
  //  }

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

    AccessibilityNodeInfoCompat currentA11yFocusedNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (shouldMoveFocus(currentA11yFocusedNode, interpretation.direction())) {

      ManualScroll.Builder builder =
          ManualScroll.builder()
              .setDirection(interpretation.direction())
              .setScreenState(screenState.getStableScreenState());
      if (formFactorUtils.isAndroidWear()) {
        builder.setCurrentFocusedNode(currentA11yFocusedNode);
      }

      pipelineInterpretations.input(
          interpretation.eventId(), interpretation.event(), builder.build());
    }
  }

  private boolean shouldMoveFocus(
      AccessibilityNodeInfoCompat currentA11yFocusedNode, @SearchDirection int direction) {
    if (formFactorUtils.isAndroidWear()) {
      // In a Watch device, especially for a rounded screen, we have to move focus onto the next
      // node before current focused node being invisible to mitigate the fighting between TB and
      // a scrolling list view. To do so, we need to focus onto another node when the current
      // focused node is close to borders and there is enough space for the new focused node.
      return shouldFocusNextNodeForWatch(context, currentA11yFocusedNode, direction);
    } else {
      return !AccessibilityNodeInfoUtils.shouldFocusNode(currentA11yFocusedNode);
    }
  }

  private static final float PARTIAL_INVISIBLE_TOP_RATIO = 0.15f;
  private static final float PARTIAL_INVISIBLE_MID_RATIO = 0.5f;
  private static final float PARTIAL_INVISIBLE_BOTTOM_RATIO = 0.85f;

  private boolean shouldFocusNextNodeForWatch(
      Context context, AccessibilityNodeInfoCompat node, @SearchDirection int direction) {
    if (!formFactorUtils.isAndroidWear()) {
      return false;
    }

    if (node == null) {
      return true;
    }

    Rect nodeRect = new Rect();
    node.getBoundsInScreen(nodeRect);

    Point screenPxSize = DisplayUtils.getScreenPixelSizeWithoutWindowDecor(context);
    // When we scroll up the list, the screen will go down and the top content will be disappeared,
    // and vice versa for scrolling down. Therefore, when we scroll up the list by gesture, we need
    // to search focus forward.
    return closeToBorder(direction, nodeRect, screenPxSize)
        && hasEnoughSpaceForNextNode(direction, nodeRect, screenPxSize);
  }

  private boolean closeToBorder(@SearchDirection int direction, Rect nodeRect, Point screenPxSize) {
    if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return (float) nodeRect.top < (float) screenPxSize.y * PARTIAL_INVISIBLE_TOP_RATIO;
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return (float) nodeRect.bottom > (float) screenPxSize.y * PARTIAL_INVISIBLE_BOTTOM_RATIO;
    } else {
      return false;
    }
  }

  private boolean hasEnoughSpaceForNextNode(
      @SearchDirection int direction, Rect nodeRect, Point screenPxSize) {
    if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return (float) nodeRect.bottom < (float) screenPxSize.y * PARTIAL_INVISIBLE_MID_RATIO;
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return (float) nodeRect.top > (float) screenPxSize.y * PARTIAL_INVISIBLE_MID_RATIO;
    } else {
      return false;
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
  @Override
  public @Nullable AccessibilityFocusEventInterpretation interpret(AccessibilityEvent event) {
    FocusActionInfo info = actorState.getFocusHistory().getFocusActionInfoFromEvent(event);

    // For user interface interaction (such as quick menu to handle slider/number-picker) and image
    // caption.
    if (event.getEventType() == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      AccessibilityNodeInfoCompat node = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      // Skips caption if the view has already been labeled.
      boolean needsCaption =
          ImageCaptioner.supportsImageCaption(context)
              && ImageCaptionUtils.needImageCaption(context, node)
              && actorState.getLabelManagerState().getLabelIdForNode(node) == Label.NO_ID;
      pipelineInterpretations.input(
          Performance.getInstance().onEventReceived(event),
          event,
          Interpretation.AccessibilityFocused.create(info, needsCaption),
          node);
    }

    if (info == null) {
      return null;
    }

    AccessibilityFocusEventInterpretation interpretation =
        new AccessibilityFocusEventInterpretation(Compositor.toCompositorEvent(event));
    interpretation.setForceFeedbackEvenIfAudioPlaybackActive(
        info.forceFeedbackEvenIfAudioPlaybackActive());
    interpretation.setForceFeedbackEvenIfMicrophoneActive(
        info.forceFeedbackEvenIfMicrophoneActive());
    interpretation.setForceFeedbackEvenIfSsbActive(info.forceFeedbackEvenIfSsbActive());
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

  /**
   * Sets long press duration. It's only applicable when the typing method is not double-tap.
   *
   * @param duration in milliseconds
   */
  public void setTypingLongPressDurationMs(int duration) {
    focusProcessorForTapAndTouchExploration.setTypingLongPressDurationMs(duration);
  }

  /**
   * Gets whether single-tap activation is enabled.
   *
   * @return Whether single-tap activation is enabled.
   */
  @TypingMethod
  public int getTypingMethod() {
    return focusProcessorForTapAndTouchExploration.getTypingMethod();
  }
}
