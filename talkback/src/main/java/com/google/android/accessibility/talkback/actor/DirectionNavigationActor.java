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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.utils.input.CursorGranularity.CONTAINER;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.CursorGranularity.WINDOWS;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_UNKNOWN;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.CursorGranularityManager;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.UserInterface;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchActor;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForLogicalNavigation;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction.ActionType;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.monitor.InputModeTracker;
import com.google.android.accessibility.utils.monitor.InputModeTracker.InputMode;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SelectionStateReader;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
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

    public boolean supportedGranularity(CursorGranularity granularity, EventId eventId) {
      return DirectionNavigationActor.this.supportedGranularity(granularity, eventId);
    }
  }

  public final StateReader state = new StateReader();

  //////////////////////////////////////////////////////////////////////////////////////////////////

  private static final String TAG = "DirectionNavigationActor";

  private final AccessibilityService service;
  private final InputModeTracker inputModeTracker;
  private final TalkBackAnalytics analytics;
  private final CursorGranularityManager cursorGranularityManager;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private Pipeline.FeedbackReturner pipeline;

  /** Converts direction-actions to focus-actions. */
  private final FocusProcessorForLogicalNavigation focusProcessorForLogicalNavigation;

  public DirectionNavigationActor(
      InputModeTracker inputModeTracker,
      GlobalVariables globalVariables,
      TalkBackAnalytics analytics,
      AccessibilityService service,
      FocusFinder focusFinder,
      ProcessorPhoneticLetters processorPhoneticLetters,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ScreenStateMonitor.State screenState,
      UniversalSearchActor.State searchState) {
    this.service = service;
    this.inputModeTracker = inputModeTracker;
    this.analytics = analytics;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;

    this.cursorGranularityManager =
        new CursorGranularityManager(
            globalVariables, accessibilityFocusMonitor, processorPhoneticLetters);

    focusProcessorForLogicalNavigation =
        new FocusProcessorForLogicalNavigation(
            service, focusFinder, accessibilityFocusMonitor, screenState, searchState);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    focusProcessorForLogicalNavigation.setPipeline(pipeline);
    cursorGranularityManager.setPipelineFeedbackReturner(pipeline);
  }

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipeline) {
    cursorGranularityManager.setPipelineEventReceiver(pipeline);
  }

  public void setUserInterface(UserInterface userInterface) {
    this.cursorGranularityManager.setUserInterface(userInterface);
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

  public boolean scrollDirection(EventId eventId, @ActionType int actionType) {
    NavigationAction action = new NavigationAction.Builder().setAction(actionType).build();
    return sendNavigationAction(action, eventId);
  }

  private boolean sendNavigationAction(NavigationAction action, EventId eventId) {
    boolean result = focusProcessorForLogicalNavigation.onNavigationAction(action, eventId);
    if (result && (action.inputMode != INPUT_MODE_UNKNOWN)) {
      inputModeTracker.setInputMode(action.inputMode);
    }
    return result;
  }

  /** Determines actions after directional-navigation auto-scrolls. */
  public void onAutoScrolled(
      @NonNull AccessibilityNodeInfoCompat scrolledNodeCompat,
      EventId eventId,
      int scrollDeltaX,
      int scrollDeltaY) {
    focusProcessorForLogicalNavigation.onAutoScrolled(
        scrolledNodeCompat, eventId, scrollDeltaX, scrollDeltaY);
  }

  /** Determines actions after directional-navigation fails to auto-scroll. */
  public void onAutoScrollFailed(@NonNull AccessibilityNodeInfoCompat scrolledNodeCompat) {
    focusProcessorForLogicalNavigation.onAutoScrollFailed(scrolledNodeCompat);
  }

  /**
   * Pass-through to {@link FocusProcessorForLogicalNavigation#searchAndFocus(boolean,
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
    CursorGranularity granularity = cursorGranularityManager.getCurrentGranularity();
    // Navigate with character, word, line or paragraph granularity.
    if (isNavigatingWithMicroGranularity()) {
      final int result = navigateWithMicroGranularity(direction, eventId);
      if (result == CursorGranularityManager.SUCCESS) {
        inputModeTracker.setInputMode(inputMode);
        analytics.onMoveWithGranularity(granularity);
        return true;
      } else if (result == CursorGranularityManager.HIT_EDGE) {
        return false;
      } else if (result == CursorGranularityManager.PRE_HIT_EDGE) {
        return false;
      }
    }

    // Update input mode in sendNavigationAction() when it returns true.
    boolean success =
        navigateWithMacroOrDefaultGranularity(
            direction, shouldWrap, shouldScroll, useInputFocusAsPivotIfEmpty, inputMode, eventId);
    if (success) {
      analytics.onMoveWithGranularity(granularity);
    }
    return success;
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
    if (!cursorGranularityManager.isLockedToNodeOrEditingNode(currentFocus)) {
      return false;
    }

    CursorGranularity currentGranularity = cursorGranularityManager.getCurrentGranularity();
    return (currentGranularity != null) && currentGranularity.isMicroGranularity();
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
            direction, WindowUtils.isScreenLayoutRTL(service));

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
    // TODO: Remove savedGranularity.
    // SavedGranularity is used to switch between micro granularity when navigating across node
    // bounds. Since we separate node navigation in Focus Management, we don't need to cache it
    // anymore.
    CursorGranularity currentGranularity = cursorGranularityManager.getSavedGranularity();
    if (currentGranularity == null) {
      currentGranularity = cursorGranularityManager.getCurrentGranularity();
    }

    return navigateWithMacroOrDefaultGranularity(
        direction,
        currentGranularity,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        inputMode,
        eventId);
  }

  private boolean navigateWithMacroOrDefaultGranularity(
      @TraversalStrategy.SearchDirection final int direction,
      CursorGranularity granularity,
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

    NavigationAction navigationAction =
        new NavigationAction.Builder()
            .setAction(NavigationAction.DIRECTIONAL_NAVIGATION)
            .setDirection(direction)
            .setShouldWrap(shouldWrap)
            .setShouldScroll(shouldScroll)
            .setUseInputFocusAsPivotIfEmpty(useInputFocusAsPivotIfEmpty)
            .setInputMode(inputMode)
            .setTarget(granularityToTargetType(granularity))
            .setOriginalNavigationGranularity(granularity)
            .build();

    return sendNavigationAction(navigationAction, eventId);
  }

  /**
   * Moves focus in some direction, with some granularity step-size.
   *
   * @param direction the navigation direction
   * @param granularity the target granularity used to navigate
   * @param shouldWrap shouldWrap only applies to default-granularity to warp around the first item,
   *     otherwise it is false
   * @param inputMode Identifies source action.
   * @param eventId EventId for performance tracking.
   * @return true on success, false on failure.
   */
  public boolean navigateWithSpecifiedGranularity(
      @TraversalStrategy.SearchDirection int direction,
      CursorGranularity granularity,
      final boolean shouldWrap,
      int inputMode,
      EventId eventId) {
    // From talkback 9.0 the default granularity had been separated from other granularities. After
    // this change, talkback supports two granularities(default + 1) to be activated at the same
    // time. To avoid the time wasting by switching the configuration between "default" and
    // "currentGranularity", navigating with the default granularity should be handled separately
    // with others.
    if (granularity == CursorGranularity.DEFAULT) {
      return navigateWithMacroOrDefaultGranularity(
          direction,
          granularity,
          /* shouldWrap= */ shouldWrap,
          /* shouldScroll= */ true,
          /* useInputFocusAsPivotIfEmpty= */ true,
          inputMode,
          eventId);
    }

    // Keep current granularity to set it back after this operation.
    CursorGranularity currentGranularity = cursorGranularityManager.getCurrentGranularity();
    boolean sameGranularity = currentGranularity == granularity;

    // Navigate with specified granularity.
    if (!sameGranularity) {
      setGranularity(granularity, /* node= */ null, /* isFromUser= */ false, eventId);
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
      setGranularity(currentGranularity, /* node= */ null, /* isFromUser= */ false, eventId);
    }

    return result;
  }

  /** Moves focus in some direction, with some HTML-element-type step-size. */
  public boolean navigateToHtmlElement(
      @TargetType int targetType,
      @TraversalStrategy.SearchDirection int direction,
      int inputMode,
      EventId eventId) {

    if (!NavigationTarget.isHtmlTarget(targetType)) {
      return false;
    }

    NavigationAction action =
        new NavigationAction.Builder()
            .setDirection(direction)
            .setTarget(targetType)
            .setInputMode(inputMode)
            .build();
    AccessibilityNodeInfoCompat pivot =
        accessibilityFocusMonitor.getAccessibilityFocus(action.useInputFocusAsPivotIfEmpty);
    // If we cannot find a pivot, or the pivot is not accessible, choose the root node if the
    // active window.
    if (pivot == null || !pivot.refresh()) {
      pivot = AccessibilityServiceCompatUtils.getRootInActiveWindow(service);
    }

    boolean result = pipeline.returnFeedback(eventId, Feedback.webDirectionHtml(pivot, action));

    if (result) {
      analytics.onMoveWithGranularity(NavigationTarget.targetTypeToGranularity(action.targetType));
    }
    if (result && (inputMode != INPUT_MODE_UNKNOWN)) {
      inputModeTracker.setInputMode(inputMode);
    }

    return result;
  }

  /** Jumps focus to next container, example: {list, grid, pager, recycler-view, scrollable...} */
  public boolean nextContainer(@SearchDirection int direction, int inputMode, EventId eventId) {
    boolean success =
        sendNavigationAction(
            new NavigationAction.Builder()
                .setAction(NavigationAction.DIRECTIONAL_NAVIGATION)
                .setDirection(direction)
                .setTarget(NavigationTarget.TARGET_CONTAINER)
                .setInputMode(inputMode)
                .build(),
            eventId);
    if (success) {
      analytics.onMoveWithGranularity(CONTAINER);
    }
    return success;
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
    boolean success = sendNavigationAction(action, eventId);

    if (success) {
      analytics.onMoveWithGranularity(WINDOWS);
    }
    return success;
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

  /**
   * Set granularity to current node or specific node
   *
   * @param granularity Granularities to set to node
   * @param node The node which to set granularity.
   * @param isFromUser This value is used if this function is called by user's action. It is used to
   *     announce result of update if true.
   * @param eventId Key for looking up EventData
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean setGranularity(
      CursorGranularity granularity,
      @Nullable AccessibilityNodeInfoCompat node,
      boolean isFromUser,
      EventId eventId) {
    AccessibilityNodeInfoCompat current =
        (node == null)
            ? accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true)
            : node;

    if (current == null) {
      // Even if there's no focused node on screen, DEFAULT granularity should be acceptable.
      if (granularity == DEFAULT) {
        cursorGranularityManager.setGranularityToDefault();
        return true;
      }

      return false;
    }

    if (cursorGranularityManager.setGranularityAt(current, granularity, eventId)) {
      granularityUpdatedAnnouncement(
          service.getString(granularity.resourceId), isFromUser, eventId);
      return true;
    } else {
      granularityUpdatedAnnouncement(
          service.getString(
              R.string.set_granularity_fail, service.getString(granularity.resourceId)),
          isFromUser,
          eventId);
      return false;
    }
  }

  // Usage: ProcessorVolumeStream, RuleGranularity, TalkBackService
  public CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node) {
    if (cursorGranularityManager.isLockedToNodeOrEditingNode(node)) {
      return cursorGranularityManager.getCurrentGranularity();
    }

    return CursorGranularity.DEFAULT;
  }

  // Usage: GestureController, SelectorController
  public CursorGranularity getCurrentGranularity() {
    return cursorGranularityManager.getCurrentGranularity();
  }

  // Usage: SelectorController
  public boolean supportedGranularity(CursorGranularity granularity, EventId eventId) {
    AccessibilityNodeInfoCompat current =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    return cursorGranularityManager.supportedGranularity(current, granularity, eventId);
  }

  /**
   * Notifies if the granularity change or not. Speaks out the granularity if it's changed by user
   * action.
   */
  private void granularityUpdatedAnnouncement(
      String resultText, boolean isFromUser, EventId eventId) {
    // No need to message analytics, because now tracked in user-input handlers.

    if (isFromUser) {
      // TODO: Think about using Compositor to announce the granularity change.
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              resultText,
              SpeakOptions.create()
                  .setQueueMode(QUEUE_MODE_INTERRUPT)
                  .setFlags(
                      FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE)));
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

    AccessibilityNodeInfoCompat currentNode =
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
          || currentGranularity == CursorGranularity.WINDOWS
          || currentNode != null) {
        granularityUpdatedAnnouncement(
            service.getString(currentGranularity.resourceId), /* isFromUser= */ true, eventId);
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
  }

  // Usage: TextEditActor
  public void setSelectionModeActive(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (!cursorGranularityManager.isLockedToNodeOrEditingNode(node)) {
      // If we're not navigating with micro granularity at node, force set granularity to CHARACTER.
      // And pass in the node to clear the lockedNode, if it is not null. This means the next action
      // of iterating over text won't turn off selection mode.
      setGranularity(
          CursorGranularity.CHARACTER, /* node= */ node, /* isFromUser= */ false, eventId);
    }

    cursorGranularityManager.setSelectionModeActive(/* active= */ true);
  }

  public void setSelectionModeInactive() {
    cursorGranularityManager.setSelectionModeActive(/* active= */ false);
  }

  // Usage: ProcessorVolumeStream, RuleCustomAction, TextEventInterpreter
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
}
