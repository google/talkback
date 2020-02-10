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

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.AccessibilityFocusEventInterpretation;
import com.google.android.accessibility.compositor.AccessibilityFocusEventInterpreter;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.controller.FullScreenReadController;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.InputFocusInterpreter;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor.ScreenStateChangeListener;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.TouchExplorationInterpreter.TouchExplorationActionListener;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The entry class for TalkBack accessibility focus management. It serves as a centralized dispatch
 * for interpreted user actions and system changes, and provides facilities for other TalkBack
 * modules to query the state of accessibility focus. Currently, it is a mix of event-interpreter
 * and feedback-mapper.
 *
 * <p><strong>Usage: </strong>
 *
 * <ul>
 *   <li>Event interpreters listen to accessibility events and notify AccessibilityFocusManager of
 *       parsed user actions and system changes.
 *   <li>AccessibilityFocusManager dispatches focus-actions from user actions and systems changes
 *       through Pipeline to FocusActor.
 *   <li>FocusProcessors interpret actions/changes, and use Pipeline to set focus.
 * </ul>
 */
public class AccessibilityFocusManager
    implements AccessibilityFocusEventInterpreter,
        ScreenStateChangeListener,
        InputFocusInterpreter.ViewTargetListener,
        ScrollEventHandler,
        TouchExplorationActionListener {

  public static final String TAG = "FocusManager";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final FocusProcessorForManualScroll focusProcessorForManualScroll;
  private final FocusProcessorForTapAndTouchExploration focusProcessorForTapAndTouchExploration;
  private final FocusProcessorForScreenStateChange focusProcessorForScreenStateChange;

  /** Callback that returns asynchronous focus-feedback to pipeline. */
  private final Pipeline.FeedbackReturner pipeline;

  private final ActorState actorState;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public AccessibilityFocusManager(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      ScreenStateMonitor screenStateMonitor,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      PrimesController primesController) {

    this.pipeline = pipeline;
    this.actorState = actorState;

    focusProcessorForManualScroll =
        new FocusProcessorForManualScroll(pipeline, actorState, accessibilityFocusMonitor);
    focusProcessorForTapAndTouchExploration =
        new FocusProcessorForTapAndTouchExploration(
            pipeline, actorState, service, accessibilityFocusMonitor);
    focusProcessorForScreenStateChange =
        new FocusProcessorForScreenStateChange(
            pipeline, actorState, service, accessibilityFocusMonitor, primesController);
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

  /** Called by ScreenStateMonitor. */
  @Override
  public boolean onScreenStateChanged(
      @Nullable ScreenState oldScreenState,
      @NonNull ScreenState newScreenState,
      long startTime,
      EventId eventId) {
    LogUtils.d(
        TAG,
        "Screen state changed: \nStart time=%s\nDuration=%s\nFrom: %s\nTo: %s",
        startTime,
        SystemClock.uptimeMillis() - startTime,
        oldScreenState,
        newScreenState);
    return focusProcessorForScreenStateChange.onScreenStateChanged(
        oldScreenState, newScreenState, startTime, eventId);
  }

  /** Called by InputFocusInterpreter. */
  @Override
  public boolean onViewTargeted(
      AccessibilityNodeInfoCompat targetedNode, boolean isInputFocus, EventId eventId) {
    LogUtils.d(TAG, "View targeted: IsInputFocus=%s; Node=%s", isInputFocus, targetedNode);
    FocusActionInfo focusActionInfo =
        FocusActionInfo.builder().setSourceAction(FocusActionInfo.FOCUS_SYNCHRONIZATION).build();
    return targetedNode.refresh()
        && pipeline.returnFeedback(eventId, Feedback.focus(targetedNode, focusActionInfo));
  }

  /** Called by ScrollEventInterpreter. */
  @Override
  public void onScrollEvent(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {
    LogUtils.d(TAG, "On scroll: Interpretation=%s; Event=%s", interpretation, event);
    if ((interpretation.userAction != ScrollEventInterpreter.ACTION_MANUAL_SCROLL)
        || (interpretation.scrollDirection == TraversalStrategy.SEARCH_FOCUS_UNKNOWN)) {
      return;
    }
    AccessibilityNodeInfoCompat scrolledNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (scrolledNode != null) {
      focusProcessorForManualScroll.onNodeManuallyScrolled(
          scrolledNode, interpretation.scrollDirection, eventId);
      AccessibilityNodeInfoUtils.recycleNodes(scrolledNode);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // APIs used by other TalkBack components

  /** Called by DirectionNavigationController, ProcessorAccessibilityHints. */
  @Nullable
  public FocusActionInfo getFocusActionInfoFromEvent(AccessibilityEvent event) {
    FocusActionRecord record = actorState.getFocusHistory().matchFocusActionRecordFromEvent(event);
    if (record == null) {
      return null;
    }
    return record.getExtraInfo();
  }

  /** Called by EventFilter. */
  @Nullable
  @Override
  public AccessibilityFocusEventInterpretation interpret(AccessibilityEvent event) {
    FocusActionInfo info = getFocusActionInfoFromEvent(event);
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

  /** Called by AccessibilityEventProcessor. */
  public boolean isEventFromFocusManagement(AccessibilityEvent event) {
    return actorState.getFocusHistory().matchFocusActionRecordFromEvent(event) != null;
  }

  /**
   * Inject {@link FullScreenReadController} to {@link FocusProcessorForTapAndTouchExploration} .
   */
  public void setFullScreenReadController(FullScreenReadController fullScreenReadController) {
    focusProcessorForTapAndTouchExploration.setFullScreenReadController(fullScreenReadController);
  }
}
