/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.talkback.controller;

import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.compositor.TextEventInterpreter.SelectionStateReader;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Analytics;
import com.google.android.accessibility.talkback.CursorGranularityManager;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForLogicalNavigation;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.InputModeManager.InputMode;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Executes directional navigation actions, such as moving accessibility-focus up/down/left/right,
 * forward/backward, top/bottom.
 */
public class DirectionNavigationActor {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // State reader interface

  /** Limited read-only interface returning navigation-action state. */
  public class StateReader implements SelectionStateReader {

    @Override
    public boolean isSelectionModeActive() {
      return DirectionNavigationActor.this.isSelectionModeActive();
    }

    public CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node) {
      return DirectionNavigationActor.this.getGranularityAt(node);
    }

    public CursorGranularity getCurrentGranularity() {
      return DirectionNavigationActor.this.getCurrentGranularity();
    }
  }

  public final StateReader state = new StateReader();

  //////////////////////////////////////////////////////////////////////////////////////////////////

  private static final String TAG = "DirectionNavigationActor";

  private final Context context;
  private final InputModeManager inputModeManager;
  private final Analytics analytics;
  private final CursorGranularityManager cursorGranularityManager;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private Pipeline.FeedbackReturner pipeline;

  /** Converts direction-actions to focus-actions. */
  private final FocusProcessorForLogicalNavigation focusProcessorForLogicalNavigation;

  public DirectionNavigationActor(
      InputModeManager inputModeManager,
      GlobalVariables globalVariables,
      Analytics analytics,
      Compositor compositor,
      AccessibilityService service,
      ProcessorPhoneticLetters processorPhoneticLetters,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ScreenStateMonitor screenStateMonitor) {
    this.context = service;
    this.inputModeManager = inputModeManager;
    this.analytics = analytics;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;

    this.cursorGranularityManager =
        new CursorGranularityManager(
            globalVariables, compositor, service, processorPhoneticLetters);

    focusProcessorForLogicalNavigation =
        new FocusProcessorForLogicalNavigation(
            service, accessibilityFocusMonitor, screenStateMonitor);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    focusProcessorForLogicalNavigation.setPipeline(pipeline);
    cursorGranularityManager.setPipeline(pipeline);
  }

  public void setActorState(ActorState actorState) {
    focusProcessorForLogicalNavigation.setActorState(actorState);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Other navigation actions.

  public boolean jumpToTop(int inputMode, EventId eventId) {
    NavigationAction action =
        new NavigationAction.Builder()
            .setAction(NavigationAction.JUMP_TO_TOP)
            .setInputMode(inputMode)
            .build();
    return sendNavigationAction(action, eventId);
  }

  public boolean jumpToBottom(int inputMode, EventId eventId) {
    NavigationAction action =
        new NavigationAction.Builder()
            .setAction(NavigationAction.JUMP_TO_BOTTOM)
            .setInputMode(inputMode)
            .build();
    return sendNavigationAction(action, eventId);
  }

  public boolean more(EventId eventId) {
    NavigationAction action =
        new NavigationAction.Builder().setAction(NavigationAction.SCROLL_FORWARD).build();
    return sendNavigationAction(action, eventId);
  }

  public boolean less(EventId eventId) {
    NavigationAction action =
        new NavigationAction.Builder().setAction(NavigationAction.SCROLL_BACKWARD).build();
    return sendNavigationAction(action, eventId);
  }

  private boolean sendNavigationAction(NavigationAction action, EventId eventId) {
    boolean result = focusProcessorForLogicalNavigation.onNavigationAction(action, eventId);
    if (result && (action.inputMode != InputModeManager.INPUT_MODE_UNKNOWN)) {
      inputModeManager.setInputMode(action.inputMode);
    }
    return result;
  }

  /** Determines actions after directional-navigation auto-scrolls. */
  public void onAutoScrolled(AccessibilityNodeInfoCompat scrolledNodeCompat, EventId eventId) {
    focusProcessorForLogicalNavigation.onAutoScrolled(scrolledNodeCompat, eventId);
  }

  /** Determines actions after directional-navigation fails to auto-scroll. */
  public void onAutoScrollFailed(AccessibilityNodeInfoCompat scrolledNodeCompat) {
    focusProcessorForLogicalNavigation.onAutoScrollFailed(scrolledNodeCompat);
  }

  /**
   * Pass-through to {@link focusProcessorForLogicalNavigation#searchAndFocus(boolean,
   * Filter<AccessibilityNodeInfoCompat>)}.
   */
  public boolean searchAndFocus(boolean startAtRoot, Filter<AccessibilityNodeInfoCompat> filter) {
    return focusProcessorForLogicalNavigation.searchAndFocus(startAtRoot, filter);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Navigation methods used internally.

  /**
   * Performs directional navigation with current granularity.
   *
   * @param direction The navigation direction.
   * @param shouldWrap Whether navigating past the last item on the screen should wrap around to the
   *     first item on the screen.
   * @param shouldScroll Whether navigating past the last visible item in a scrollable container
   *     should automatically scroll to the next visible item.
   * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
   *     focused editable node if there is no node with accessibility focus.
   * @param inputMode Identifies source action.
   * @param eventId EventId for performance tracking.
   * @return true on success, false on failure.
   */
  public boolean navigate(
      @TraversalStrategy.SearchDirection final int direction,
      final boolean shouldWrap,
      final boolean shouldScroll,
      final boolean useInputFocusAsPivotIfEmpty,
      @InputMode final int inputMode,
      final EventId eventId) {
    // In case when the user changes granularity linearly with gesture, or change setting with
    // selector, we cannot confirm the change until the user performs a navigation action.
    analytics.logPendingChanges();

    // Navigate with character, word, line or paragraph granularity.
    if (isNavigatingWithMicroGranularity()) {
      final int result = navigateWithMicroGranularity(direction, eventId);
      if (result == CursorGranularityManager.SUCCESS) {
        inputModeManager.setInputMode(inputMode);
        return true;
      } else if ((result == CursorGranularityManager.HIT_EDGE)
          && isEditingFocusedNode(useInputFocusAsPivotIfEmpty)) {
        return false;
      }
    }

    // Update input mode in sendNavigationAction() when it returns true.
    return navigateWithMacroOrDefaultGranularity(
        direction, shouldWrap, shouldScroll, useInputFocusAsPivotIfEmpty, inputMode, eventId);
  }

  private boolean isEditingFocusedNode(boolean useInputFocusAsPivotIfEmpty) {
    AccessibilityNodeInfoCompat currentFocus = null;
    try {
      currentFocus = accessibilityFocusMonitor.getAccessibilityFocus(useInputFocusAsPivotIfEmpty);
      return (currentFocus != null)
          && (currentFocus.isEditable() || (Role.getRole(currentFocus) == Role.ROLE_EDIT_TEXT))
          && currentFocus.isFocused();
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
    }
  }

  /**
   * Returns whether the navigation is with any one of the follow granularities:
   *
   * <ul>
   *   <li>{@link CursorGranularity#CHARACTER}
   *   <li>{@link CursorGranularity#WORD}
   *   <li>{@link CursorGranularity#LINE}
   *   <li>{@link CursorGranularity#PARAGRAPH}
   * </ul>
   */
  private boolean isNavigatingWithMicroGranularity() {
    AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    try {
      if (!cursorGranularityManager.isLockedTo(currentFocus)) {
        return false;
      }

      CursorGranularity currentGranularity = cursorGranularityManager.getCurrentGranularity();
      return (currentGranularity != null) && currentGranularity.isMicroGranularity();
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
    }
  }

  /**
   * Navigates with any of the following granularities:
   *
   * <ul>
   *   <li>{@link CursorGranularity#CHARACTER}
   *   <li>{@link CursorGranularity#WORD}
   *   <li>{@link CursorGranularity#LINE}
   *   <li>{@link CursorGranularity#PARAGRAPH}
   * </ul>
   *
   * @return the result code of granularity navigation.
   */
  private int navigateWithMicroGranularity(final int direction, EventId eventId) {
    // Convert 2-D up/down/left/right direction to linear forward/backward direction.
    @SearchDirection
    int linearDirection =
        TraversalStrategyUtils.getLogicalDirection(
            direction, WindowManager.isScreenLayoutRTL(context));

    int granularityNavigationAction = logicalDirectionToNavigationAction(linearDirection);

    return cursorGranularityManager.navigate(granularityNavigationAction, eventId);
  }

  private boolean navigateWithMacroOrDefaultGranularity(
      @TraversalStrategy.SearchDirection final int direction,
      final boolean shouldWrap,
      final boolean shouldScroll,
      final boolean useInputFocusAsPivotIfEmpty,
      @InputMode final int inputMode,
      final EventId eventId) {
    int scrollDirection = TraversalStrategyUtils.convertSearchDirectionToScrollAction(direction);
    if (scrollDirection == 0) {
      // We won't be able to handle scrollable views very well on older SDK versions,
      // so don't allow d-pad navigation.
      return false;
    }

    // TODO: Remove savedGranularity.
    // SavedGranularity is used to switch between micro granularity when navigating across node
    // bounds. Since we separate node navigation in Focus Management, we don't need to cache it
    // anymore.
    CursorGranularity currentGranularity = cursorGranularityManager.getSavedGranularity();
    if (currentGranularity == null) {
      currentGranularity = cursorGranularityManager.getCurrentGranularity();
    }

    NavigationAction navigationAction =
        new NavigationAction.Builder()
            .setAction(NavigationAction.DIRECTIONAL_NAVIGATION)
            .setDirection(direction)
            .setShouldWrap(shouldWrap)
            .setShouldScroll(shouldScroll)
            .setUseInputFocusAsPivotIfEmpty(useInputFocusAsPivotIfEmpty)
            .setInputMode(inputMode)
            .setTarget(granularityToTargetType(currentGranularity))
            .setOriginalNavigationGranularity(currentGranularity)
            .build();

    return sendNavigationAction(navigationAction, eventId);
  }

  /** Moves focus in some direction, with some granularity step-size. */
  public boolean navigateWithSpecifiedGranularity(
      @TraversalStrategy.SearchDirection int direction,
      CursorGranularity granularity,
      int inputMode,
      EventId eventId) {
    // Keep current granularity to set it back after this operation.
    CursorGranularity currentGranularity = cursorGranularityManager.getCurrentGranularity();
    boolean sameGranularity = currentGranularity == granularity;

    // Navigate with specified granularity.
    if (!sameGranularity) {
      setGranularity(granularity, /* isFromUser= */ false, eventId);
    }
    boolean result =
        navigate(
            direction,
            /* shouldWrap= */ false,
            /* shouldScroll= */ true,
            /* useInputFocusAsPivotIfEmpty= */ true,
            inputMode,
            eventId);

    // Set back to the granularity which is used before this operation.
    if (!sameGranularity) {
      setGranularity(currentGranularity, /* isFromUser= */ false, eventId);
    }

    return result;
  }

  /** Moves focus in some direction, with some HTML-element-type step-size. */
  public boolean navigateToHtmlElement(
      @TargetType int targetType,
      @TraversalStrategy.SearchDirection int direction,
      int inputMode,
      EventId eventId) {
    NavigationAction action =
        new NavigationAction.Builder()
            .setAction(NavigationAction.DIRECTIONAL_NAVIGATION)
            .setDirection(direction)
            .setTarget(targetType)
            .setInputMode(inputMode)
            .build();
    return sendNavigationAction(action, eventId);
  }

  /** Used by window navigation with keyboard shortcuts. */
  public boolean navigateToNextOrPreviousWindow(
      @TraversalStrategy.SearchDirection int direction,
      boolean useInputFocusAsPivot,
      int inputMode,
      EventId eventId) {
    NavigationAction action =
        new NavigationAction.Builder()
            .setAction(NavigationAction.DIRECTIONAL_NAVIGATION)
            .setDirection(direction)
            .setUseInputFocusAsPivotIfEmpty(useInputFocusAsPivot)
            .setTarget(NavigationTarget.TARGET_WINDOW)
            .setInputMode(inputMode)
            .build();
    return sendNavigationAction(action, eventId);
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  // Granularity related operations.

  public void followTo(
      @Nullable AccessibilityNodeInfoCompat node, @SearchDirection int direction, EventId eventId) {
    cursorGranularityManager.followTo(node, direction, eventId);
  }

  // Usage: GestureController, Selector, TalkBackService.mKeyComboListener.onComboPerformed()
  public boolean nextGranularity(EventId eventId) {
    return adjustGranularity(/* direction= */ 1, eventId);
  }

  // Usage: GestureController, Selector, TalkBackService.mKeyComboListener.onComboPerformed()
  public boolean previousGranularity(EventId eventId) {
    return adjustGranularity(/* direction= */ -1, eventId);
  }

  // Usage: FullScreenReaderController, RuleGranularity
  public boolean setGranularity(
      CursorGranularity granularity, boolean isFromUser, EventId eventId) {
    AccessibilityNodeInfoCompat current = null;
    try {
      current = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      return setGranularity(granularity, current, isFromUser, eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(current);
    }
  }

  // Usage: TalkBackService.resetFocusedNode
  public boolean setGranularity(
      CursorGranularity granularity,
      AccessibilityNodeInfoCompat node,
      boolean isFromUser,
      EventId eventId) {
    if (node == null) {
      return false;
    }

    if (!cursorGranularityManager.setGranularityAt(node, granularity, eventId)) {
      return false;
    }

    granularityUpdated(granularity, isFromUser, eventId);
    return true;
  }

  // Usage: ProcessorScreen
  public void setGranularityToDefault() {
    cursorGranularityManager.setGranularityToDefault();
    analytics.clearPendingGranularityChange();
  }

  // Usage: ProcessorVolumeStream, RuleGranularity, TalkBackService
  public CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node) {
    if (cursorGranularityManager.isLockedTo(node)) {
      return cursorGranularityManager.getCurrentGranularity();
    }

    return CursorGranularity.DEFAULT;
  }

  // Usage: GestureController, SelectorController
  public CursorGranularity getCurrentGranularity() {
    return cursorGranularityManager.getCurrentGranularity();
  }

  /**
   * Notifies {@link GranularityChangeListener} of the granularity change. Speaks out the
   * granularity if it's changed by user action.
   */
  private void granularityUpdated(
      CursorGranularity granularity, boolean isFromUser, EventId eventId) {
    // No need to message analytics, because now tracked in user-input handlers.

    if (isFromUser) {
      // TODO: Think about using Compositor to announce the granularity change.
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              context.getString(granularity.resourceId),
              SpeakOptions.create()
                  .setQueueMode(QUEUE_MODE_INTERRUPT)
                  .setFlags(
                      FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_PHONE_CALL_ACTIVE)));
    }
  }

  /**
   * Attempts to adjust granularity in the direction indicated.
   *
   * @param direction The direction to adjust granularity. One of {@link
   *     CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or {@link
   *     CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
   * @return true on success, false if no nodes in the current hierarchy support a granularity other
   *     than the default.
   */
  private boolean adjustGranularity(int direction, EventId eventId) {
    AccessibilityNodeInfoCompat currentNode = null;

    try {
      currentNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);

      final boolean wasAdjusted =
          cursorGranularityManager.adjustGranularityAt(currentNode, direction, eventId);

      CursorGranularity currentGranularity = cursorGranularityManager.getCurrentGranularity();

      if (wasAdjusted) {
        // If the current granularity after change is default or native macro granularity
        // (Headings, controls, etc), we want to keep that change even if the currentNode is null.
        // The idea is to relax the constraint for native macro granularity to  always have
        // accessibility focus on screen to switch between them.
        if (currentGranularity.isNativeMacroGranularity()
            || currentGranularity == CursorGranularity.DEFAULT
            || currentNode != null) {
          granularityUpdated(currentGranularity, /* isFromUser= */ true, eventId);
        } else {
          // TODO: Why we need to adjust granularity forth and back? If the current node is
          // null and the granularity after change is not native macro, we want to discard the
          // change as micro granularities (characters, words, etc) are always dependent on the node
          // having accessibility focus.
          cursorGranularityManager.adjustGranularityAt(currentNode, direction * -1, eventId);
          return false;
        }
      }

      return wasAdjusted;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentNode);
    }
  }

  // Usage: RuleEditText
  public void setSelectionModeActive(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (!cursorGranularityManager.isLockedTo(node)) {
      // If we're not navigating with micro granularity at node, force set granularity to CHARACTER.
      setGranularity(CursorGranularity.CHARACTER, /* isFromUser= */ false, eventId);
    }

    cursorGranularityManager.setSelectionModeActive(/* active= */ true);
  }

  public void setSelectionModeInactive() {
    cursorGranularityManager.setSelectionModeActive(/* active= */ false);
  }

  // Usage: ProcessorVolumeStream, RuleEditText, TextEventInterpreter
  public boolean isSelectionModeActive() {
    return cursorGranularityManager.isSelectionModeActive();
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Static util methods.
  // TODO: Consider move these static methods somewhere else.

  /** Converts linear navigation direction to the granularity navigation action. */
  private static int logicalDirectionToNavigationAction(
      @TraversalStrategy.SearchDirection int logicalDirection) {
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
    } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
    } else {
      throw new IllegalStateException("Unknown logical direction");
    }
  }

  @TargetType
  private static int granularityToTargetType(CursorGranularity granularity) {
    switch (granularity) {
      case CONTROL:
        return NavigationTarget.TARGET_CONTROL;
      case LINK:
        return NavigationTarget.TARGET_LINK;
      case HEADING:
        return NavigationTarget.TARGET_HEADING;
      case WEB_CONTROL:
        return NavigationTarget.TARGET_HTML_ELEMENT_CONTROL;
      case WEB_LINK:
        return NavigationTarget.TARGET_HTML_ELEMENT_LINK;
      case WEB_LIST:
        return NavigationTarget.TARGET_HTML_ELEMENT_LIST;
      case WEB_HEADING:
        return NavigationTarget.TARGET_HTML_ELEMENT_HEADING;
      case WEB_LANDMARK:
        return NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK;
      default:
        return NavigationTarget.TARGET_DEFAULT;
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to save/restore granularity during continuous-reading

  public void applySavedGranularityForContinuousReading(EventId eventId) {
    CursorGranularity cacheGranularity =
        cursorGranularityManager.popSavedGranularityForContinuousReading();
    if (cacheGranularity != null) {
      setGranularity(cacheGranularity, false, eventId);
    }
  }

  public void saveGranularityForContinuousReading() {
    cursorGranularityManager.saveGranularityForContinuousReading();
  }

  public void clearSavedGranularityForContinuousReading() {
    cursorGranularityManager.popSavedGranularityForContinuousReading();
  }

}
