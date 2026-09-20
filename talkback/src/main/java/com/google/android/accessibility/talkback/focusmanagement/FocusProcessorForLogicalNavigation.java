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

import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP;
import static com.google.android.accessibility.talkback.Constants.CLANK_PACKAGE_NAME;
import static com.google.android.accessibility.talkback.flags.FeatureFlagReader.getSuccessAutoScrollPercentageThreshold;
import static com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TARGET_CONTAINER;
import static com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TARGET_SEARCH;
import static com.google.android.accessibility.utils.AccessibilityEventUtils.DELTA_UNDEFINED;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_CONTAINER;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_SCROLLABLE_GRID;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.Role.ROLE_FLOATING_ACTION_BUTTON;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.output.ScrollActionRecord.AutoScrollSuccessChecker;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_LEFT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_RIGHT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.FocusManagerInternal;
import com.google.android.accessibility.talkback.actor.search.StringMatcher;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchActor;
import com.google.android.accessibility.talkback.compositor.Compositor.TextComposer;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.google.android.accessibility.talkback.flags.FeatureFlagReader;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.utils.LinkUtils;
import com.google.android.accessibility.talkback.utils.TalkbackFeatureSupport;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.DisplayUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.NodeActionFilter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.ScrollableNodeInfo;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollTimeout;
import com.google.android.accessibility.utils.monitor.CollectionState;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.ScrollActionRecord.UserAction;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.GridTraversalManager;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategyConfig;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Handles the use case of logical navigation actions. */
public class FocusProcessorForLogicalNavigation {

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "FocusProcessor-LogicalNav";

  /**
   * Speak-ahead: when a swipe triggers a scroll, focus and speak the already-computed
   * target immediately instead of waiting for the scroll to settle (~110ms), the post-scroll
   * re-search (~50ms) and the speech queue. The in-flight scroll bookkeeping is untouched, so
   * when the scroll resolves the normal path re-validates focus; if focus is already on the
   * target it is left alone (no double speech), otherwise it corrects as stock would. This is
   * what makes VoiceOver feel instant; the trade-off is a brief window where the spoken item
   * is still scrolling into view, and a fast fling can speak an item the list then carries
   * away (the post-scroll pass still lands focus correctly).
   */
  private static final boolean SPEAK_AHEAD_OF_SCROLL = true;

  private static final Filter<AccessibilityNodeInfoCompat>
      SCROLLABLE_ROLE_FILTER_FOR_DIRECTION_NAVIGATION = FILTER_AUTO_SCROLL;

  private static final Filter<AccessibilityNodeInfoCompat>
      SCROLLABLE_ROLE_FILTER_FOR_SCROLL_GESTURE =
          AccessibilityNodeInfoUtils.FILTER_SCROLLABLE.and(
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  @RoleName int role = Role.getRole(node);
                  return (role != Role.ROLE_SEEK_CONTROL)
                      && (role != Role.ROLE_DATE_PICKER)
                      && (role != Role.ROLE_TIME_PICKER);
                }
              });

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  /** Filters target window when performing window navigation with keyboard shortcuts. */
  @VisibleForTesting public final Filter<AccessibilityWindowInfo> filterWindowForWindowNavigation;

  private final AccessibilityService service;
  private final FocusFinder focusFinder;
  private ActorState actorState;
  private final ScreenStateMonitor.State screenState;
  private final UniversalSearchActor.State searchState;
  private Pipeline.FeedbackReturner pipeline;
  private final boolean isWindowNavigationSupported;

  // Whether the previous navigation action reaches the edge of the window, reset the value after
  // successfully finding the focus.
  private boolean reachEdge = false;

  /** The last node that was scrolled while navigating with native macro granularity. */
  private @Nullable AccessibilityNodeInfoCompat lastScrolledNodeForNativeMacroGranularity;

  /** Callback to handle scroll success or failure. */
  private @Nullable AutoScrollCallback scrollCallback;

  // Target to put focus on on the next window navigation action
  private @Nullable AccessibilityNodeInfoCompat stealWindowNavigationTarget = null;
  private @SearchDirectionOrUnknown int stealWindowNavigationTargetDirection =
      TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

  // Object-wrapper around static-method getAccessibilityFocus(), for test-mocking.
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  @NonNull private final TextComposer compositor;
  private final GlobalVariables globalVariables;

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FocusProcessorForLogicalNavigation(
      @NonNull AccessibilityService service,
      FocusFinder focusFinder,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ScreenStateMonitor.State screenState,
      UniversalSearchActor.State searchState,
      @NonNull TextComposer compositor,
      GlobalVariables globalVariables) {
    this.service = service;
    this.focusFinder = focusFinder;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.screenState = screenState;
    this.searchState = searchState;
    this.compositor = compositor;
    this.globalVariables = globalVariables;
    isWindowNavigationSupported = !FormFactorUtils.isAndroidTv();
    filterWindowForWindowNavigation = new WindowNavigationFilter(service, searchState);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Methods
  public boolean onNavigationAction(NavigationAction navigationAction, EventId eventId) {
    if (navigationAction == null || navigationAction.actionType == NavigationAction.UNKNOWN) {
      LogUtils.w(TAG, "Cannot perform navigation action: action type undefined.");
      return false;
    }
    AccessibilityNodeInfoCompat pivot = getPivotNodeForNavigationAction(navigationAction);
    if (pivot == null) {
      // Ideally the pivot should never be null. Do the null check in case of exception.
      LogUtils.w(TAG, "Cannot find pivot node for %s", navigationAction);
      return false;
    }
    return switch (navigationAction.actionType) {
      case NavigationAction.DIRECTIONAL_NAVIGATION ->
          onDirectionalNavigationAction(
              pivot, /* ignoreDescendantsOfPivot= */ false, navigationAction, eventId);
      case NavigationAction.JUMP_TO_TOP, NavigationAction.JUMP_TO_BOTTOM ->
          onJumpAction(pivot, navigationAction, eventId);
      case NavigationAction.SCROLL_FORWARD, NavigationAction.SCROLL_BACKWARD ->
          onScrollAction(pivot, navigationAction, eventId);
      case NavigationAction.SCROLL_UP,
          NavigationAction.SCROLL_DOWN,
          NavigationAction.SCROLL_LEFT,
          NavigationAction.SCROLL_RIGHT ->
          onScrollOrPageAction(pivot, navigationAction, eventId);
      default -> false;
    };
  }

  @VisibleForTesting
  AccessibilityNodeInfoCompat getStealNextWindowNavigationTarget() {
    return stealWindowNavigationTarget;
  }

  @VisibleForTesting
  @SearchDirectionOrUnknown
  int getStealNextWindowNavigationTargeDirection() {
    return stealWindowNavigationTargetDirection;
  }

  /** Updates the target that should be focused on performing the next window navigation action. */
  public void updateStealNextWindowNavigation(
      @Nullable AccessibilityNodeInfoCompat target, @SearchDirectionOrUnknown int direction) {
    // If the target argument is ever null, reset the tracked node.
    if (target == null) {
      stealWindowNavigationTarget = null;
      stealWindowNavigationTargetDirection = TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
      return;
    }
    if (target.equals(stealWindowNavigationTarget)) {
      return;
    }
    stealWindowNavigationTarget = target;
    stealWindowNavigationTargetDirection = direction;
  }

  /**
   * Moves focus to next node after current focused-node, which matches search-filter. Returns
   * success flag.
   */
  public boolean searchAndFocus(boolean startAtRoot, Filter<AccessibilityNodeInfoCompat> filter) {
    return search(startAtRoot, /* focus= */ true, filter) != null;
  }

  /**
   * Finds next node which matches search-filter. Optionally focuses matching node. Returns matching
   * node.
   */
  private @Nullable AccessibilityNode search(
      boolean startAtRoot, boolean focus, Filter<AccessibilityNodeInfoCompat> filter) {

    AccessibilityNodeInfoCompat start = null;
    AccessibilityNodeInfoCompat rootNode = null;
    // Try to find current accessibility-focused node.
    if (!startAtRoot) {
      start = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    }
    // Find root node, or return failure.
    if (start == null || !start.refresh()) {
      // Start from root node of active window.
      rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(service);
      if (rootNode == null) {
        return null;
      }
      start = rootNode;
    } else {
      // Derive root node from start node.
      rootNode = AccessibilityNodeInfoUtils.getRoot(start);
      if (rootNode == null) {
        return null;
      }
    }

    // Search forward for node satisfying filter.
    @SearchDirection int direction = TraversalStrategy.SEARCH_FOCUS_FORWARD;
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(rootNode, focusFinder, direction);
    AccessibilityNodeInfoCompat target =
        TraversalStrategyUtils.searchFocus(traversalStrategy, start, direction, filter);
    if (target == null) {
      return null;
    }

    // Focus first matching node.
    // Focus is implemented in the same function as searching, because they use the same rootNode
    // and traversalStrategy.
    if (focus) {
      EventId eventId = EVENT_ID_UNTRACKED;
      NavigationAction navigationAction =
          new NavigationAction.Builder()
              .setAction(NavigationAction.DIRECTIONAL_NAVIGATION)
              .setDirection(direction)
              .build();
      boolean focused = setAccessibilityFocusInternal(target, navigationAction, eventId);
      if (!focused) {
        return null;
      }
    }
    return AccessibilityNode.takeOwnership(target);
  }

  /**
   * Returns the pivot node for the given navigation action.
   *
   * <p>Pivot node is the {@link AccessibilityNodeInfoCompat} based on which we search for target
   * focusable node. We apply the following strategy to find pivot node:
   *
   * <ol>
   *   <li>Use the accessibility focused node if it's non-null and refreshable.
   *   <li>Otherwise use input focused node if {@link NavigationAction#useInputFocusAsPivotIfEmpty}
   *       is set to {@code true} and it's refreshable.
   *   <li>Otherwise return the root node of active window.
   * </ol>
   */
  private AccessibilityNodeInfoCompat getPivotNodeForNavigationAction(
      NavigationAction navigationAction) {
    AccessibilityNodeInfoCompat pivot =
        accessibilityFocusMonitor.getAccessibilityFocus(
            navigationAction.useInputFocusAsPivotIfEmpty, /* requireEditable= */ false);

    // If we cannot find a pivot, or the pivot is not accessible, choose the root node if the
    // active window.
    if (pivot == null || !pivot.refresh()) {
      // TODO: We might need to define our own "active window" in TalkBack side.
      pivot = AccessibilityServiceCompatUtils.getRootInActiveWindow(service);
    }
    return pivot;
  }

  /**
   * Navigates to a web or native table. Throws exception if the target type is not related to
   * table.
   */
  private boolean navigateToTableTarget(
      @NonNull AccessibilityNodeInfoCompat pivot,
      @NonNull NavigationAction navigationAction,
      @Nullable EventId eventId) {
    if (WebInterfaceUtils.supportsWebActions(pivot)
        && NavigationTarget.supportsTableNavigation(navigationAction.targetType)) {
      return pipeline.returnFeedback(eventId, Feedback.webDirectionHtml(pivot, navigationAction));
    } else if (TableNavigation.isNativeTableGranularity(navigationAction.targetType)) {
      return navigateToNativeTable(pivot, navigationAction, eventId);
    } else {
      throw new IllegalArgumentException(
          "Not supported table navigation targetType: " + navigationAction.targetType);
    }
  }

  /** Navigates to a native table. Show error messages if navigation fails. */
  private boolean navigateToNativeTable(
      @NonNull AccessibilityNodeInfoCompat pivot,
      @NonNull NavigationAction navigationAction,
      @Nullable EventId eventId) {
    AccessibilityNodeInfoCompat tableRoot = AccessibilityNodeInfoUtils.getTableRoot(pivot);
    if (tableRoot == null) {
      LogUtils.w(TAG, "Cannot perform table navigation: invalid tableRoot.");
      showFailureMessageForInvalidTable(eventId);
      return false;
    }
    AccessibilityNodeInfoCompat pivotCell =
        AccessibilityNodeInfoUtils.getTableCellUnderTable(pivot);
    if (pivotCell == null) {
      LogUtils.w(TAG, "Cannot perform table navigation: invalid pivotCell.");
      showFailureMessageForInvalidTable(eventId);
      return false;
    }
    NavigationAction tableNavigationAction =
        NavigationAction.Builder.copy(navigationAction)
            .setPivotIndex(
                Pair.create(
                    pivotCell.getCollectionItemInfo().getRowIndex(),
                    pivotCell.getCollectionItemInfo().getColumnIndex()))
            .build();
    TableNavigation tableNavigation =
        new TableNavigation(tableNavigationAction, tableRoot, focusFinder, eventId);
    boolean result = navigateToTableCell(tableNavigation, pivot);
    if (!result) {
      showFailureMessageForTableNavigationAction(navigationAction, eventId);
    }
    return result;
  }

  /**
   * Navigates to a table cell. Performs auto scroll if the cell is out of the screen.
   *
   * @param tableNavigation The table navigation action.
   * @param pivot The pivot node.
   * @return {@code true} if the cell is successfully navigated to or scrolled to.
   */
  private boolean navigateToTableCell(
      TableNavigation tableNavigation, AccessibilityNodeInfoCompat pivot) {
    boolean result = false;
    AccessibilityNodeInfoCompat focusableNode = tableNavigation.navigateToTableCell();
    if (focusableNode != null) {
      result =
          setAccessibilityFocusInternal(
              focusableNode, tableNavigation.getNavigationAction(), tableNavigation.getEventId());
    }
    // Try to auto scroll to find the cell out of the screen. It scrolls by {@link
    // AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD} or {@link
    // AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD}
    // instead of {@link AccessibilityNodeInfoCompat.ACTION_SCROLL_TO_POSITION} because scroll to
    // position is unreliable in native GridView.
    int scrollAction =
        TraversalStrategyUtils.convertSearchDirectionToScrollAction(
            tableNavigation.getScrollDirection());
    if (!result && tableNavigation.canScroll(scrollAction)) {
      NavigationAction actionForScroll =
          NavigationAction.Builder.copy(tableNavigation.getNavigationAction())
              .setShouldScroll(false) // Sets to false to avoid scrolling again.
              .build();
      AutoScrollSuccessChecker autoScrollChecker =
          createAutoScrollCheckerIfNeeded(
              tableNavigation.getTableRoot(), actionForScroll, scrollAction);
      result =
          performScrollActionInternal(
              ScrollActionRecord.ACTION_AUTO_SCROLL,
              tableNavigation.getTableRoot(),
              pivot,
              scrollAction,
              actionForScroll,
              ScrollTimeout.SCROLL_TIMEOUT_LONG,
              autoScrollChecker,
              tableNavigation.getEventId());
    }
    return result;
  }

  private void handleViewScrolledForTableNavigationAction(
      TableNavigation tableNavigation, AccessibilityNodeInfoCompat pivot) {
    if (navigateToTableCell(tableNavigation, pivot)) {
      return;
    }
    handleViewAutoScrollFailedForTableNavigationAction(tableNavigation);
  }

  private void handleViewAutoScrollFailedForTableNavigationAction(TableNavigation tableNavigation) {
    showFailureMessageForTableNavigationAction(
        tableNavigation.getNavigationAction(), tableNavigation.getEventId());
    focusInitialTable(tableNavigation);
  }

  private void showFailureMessageForTableNavigationAction(
      NavigationAction sourceAction, EventId eventId) {
    announceNativeElement(sourceAction.searchDirection, sourceAction.targetType, eventId);
  }

  private void showFailureMessageForInvalidTable(EventId eventId) {
    announce(service.getString(R.string.table_navigation_not_supported), eventId);
  }

  /**
   * Focus on the first focusable node in the table.
   *
   * <p>This is a fallback method when the navigation action fails.
   */
  private void focusInitialTable(TableNavigation tableNavigation) {
    AccessibilityNodeInfoCompat curFocusNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (AccessibilityNodeInfoUtils.isVisible(curFocusNode)) {
      return;
    }
    AccessibilityNodeInfoCompat nodeToFocus =
        tableNavigation.getFirstFocusableByDirection(
            tableNavigation.getTableRoot(), TraversalStrategy.SEARCH_FOCUS_FORWARD);
    setAccessibilityFocusInternal(
        nodeToFocus, tableNavigation.getNavigationAction(), tableNavigation.getEventId());
  }

  /**
   * Handles {@link NavigationAction#DIRECTIONAL_NAVIGATION} actions.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  @VisibleForTesting
  boolean onDirectionalNavigationAction(
      @NonNull AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      @NonNull NavigationAction navigationAction,
      @Nullable EventId eventId) {
    if (navigationAction.targetType == NavigationTarget.TARGET_WINDOW) {
      return navigateToWindowTarget(pivot, navigationAction, eventId);
    } else if (NavigationTarget.supportsTableNavigation(navigationAction.targetType)) {
      return navigateToTableTarget(pivot, navigationAction, eventId);
    } else if (NavigationTarget.isWebOnlyTarget(navigationAction.targetType)) {
      return navigateToHtmlTarget(pivot, navigationAction, eventId);
    } else if (navigationAction.targetType == NavigationTarget.TARGET_SEARCH) {
      return navigateToScreenSearchTarget(pivot, navigationAction, eventId);
    } else {
      return navigateToDefaultOrMacroGranularityTarget(
          pivot, ignoreDescendantsOfPivot, navigationAction, eventId);
    }
  }

  /**
   * Handles {@link NavigationAction#JUMP_TO_TOP} and {@link NavigationAction#JUMP_TO_BOTTOM}
   * actions.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean onJumpAction(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    boolean isNodeFromClank =
        FeatureFlagReader.enableNewJumpNavigationForClank(service)
            && pivot != null
            && pivot.getPackageName() != null
            && pivot.getPackageName().toString().equals(CLANK_PACKAGE_NAME);

    AccessibilityNodeInfoCompat rootNode;
    if (isNodeFromClank) {
      // Finds the first child of the Clank root node that is an ancestor of the pivot node.
      // Restricts the potential jump targets to the same Clank UI section as the pivot node.
      rootNode = AccessibilityNodeInfoUtils.getNthAncestorFromRoot(pivot, 1);
    } else {
      rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
    }

    if (rootNode == null) {
      LogUtils.w(TAG, "Cannot perform jump action: unable to find root node.");
      return false;
    }

    @SearchDirection
    int searchDirection =
        navigationAction.actionType == NavigationAction.JUMP_TO_TOP
            ? TraversalStrategy.SEARCH_FOCUS_FORWARD
            : TraversalStrategy.SEARCH_FOCUS_BACKWARD;

    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(rootNode, focusFinder, searchDirection);

    // Always use default granularity when jumping to the beginning/end of the window.
    AccessibilityNodeInfoCompat target =
        TraversalStrategyUtils.findFirstFocusInNodeTree(
            traversalStrategy,
            rootNode,
            searchDirection,
            NavigationTarget.createNodeFilter(
                NavigationTarget.TARGET_DEFAULT, traversalStrategy.getSpeakingNodesCache()));
    if (target != null) {
      // Navigate to the next focusable node if this node is Clank's WebView.
      if (isNodeFromClank && Role.getRole(target) == Role.ROLE_WEB_VIEW) {
        NavigationAction nextNavigationAction =
            new NavigationAction.Builder()
                .setAction(NavigationAction.JUMP_TO_BOTTOM)
                .setDirection(searchDirection)
                .setTarget(NavigationTarget.TARGET_DEFAULT)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setOriginalNavigationGranularity(CursorGranularity.DEFAULT)
                .setShouldWrap(true)
                .setShouldScroll(true)
                .build();

        return onDirectionalNavigationAction(
            target, /* ignoreDescendantsOfPivot= */ false, nextNavigationAction, eventId);
      }

      // JUMP_TO_TOP makes the focus on the top, so the top-half of the target node may be covered
      // if it's inside a scrollable container. In this case the checking direction should be
      // SEARCH_FOCUS_BACKWARD(and vice versa).
      @SearchDirection
      int ensureOnScreenDirection =
          navigationAction.actionType == NavigationAction.JUMP_TO_TOP
              ? TraversalStrategy.SEARCH_FOCUS_BACKWARD
              : TraversalStrategy.SEARCH_FOCUS_FORWARD;
      ensureOnScreen(target, ensureOnScreenDirection, eventId);
      return setAccessibilityFocusInternal(target, navigationAction, eventId);
    }
    return false;
  }

  /**
   * Handles {@link NavigationAction#SCROLL_FORWARD} and {@link NavigationAction#SCROLL_BACKWARD}
   * actions.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean onScrollAction(
      @NonNull AccessibilityNodeInfoCompat pivot,
      NavigationAction navigationAction,
      EventId eventId) {
    AccessibilityNodeInfoCompat scrollableNode = null;
    AccessibilityNodeInfoCompat rootNode = null;
    final int scrollAction;
    if (navigationAction.actionType == NavigationAction.SCROLL_FORWARD) {
      scrollAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
    } else if (navigationAction.actionType == NavigationAction.SCROLL_BACKWARD) {
      scrollAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
    } else {
      throw new IllegalArgumentException("Unknown scroll action.");
    }

    final Filter<AccessibilityNodeInfoCompat> nodeFilter = getScrollFilter(navigationAction);
    if (nodeFilter == null) {
      return false;
    }

    if (AccessibilityNodeInfoUtils.supportsAction(pivot, scrollAction)) {
      // Try to scroll the node itself first. It's useful when focusing on a SeekBar.
      scrollableNode = pivot;
    } else if (pivot.isAccessibilityFocused()) {
      scrollableNode = AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(pivot, nodeFilter);
    }

    if (scrollableNode == null) {
      rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
      if (rootNode != null) {
        scrollableNode = AccessibilityNodeInfoUtils.searchFromBfs(rootNode, nodeFilter);
      }
    }
    return (scrollableNode != null)
        && performScrollActionInternal(
            ScrollActionRecord.ACTION_SCROLL_COMMAND,
            scrollableNode,
            pivot,
            scrollAction,
            navigationAction,
            eventId);
  }

  /**
   * Handles {@link NavigationAction#SCROLL_UP}, {@link NavigationAction#SCROLL_DOWN}, {@link
   * NavigationAction#SCROLL_LEFT} and {@link NavigationAction#SCROLL_RIGHT} actions.
   *
   * <p>Checks if the pivot node or its ancestors by return value of
   * {@link getScrollOrPageActionFilter), handles pivot node's supported action accordingly.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean onScrollOrPageAction(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    AccessibilityNodeInfoCompat pageOrScrollNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            pivot, getScrollOrPageActionFilter(navigationAction));
    if (pageOrScrollNode != null) {
      if (Role.getRole(pageOrScrollNode) == Role.ROLE_PAGER) {
        // Scroll the pager node only if the swiping direction is left or right. For swiping up or
        // down, we only scroll the pager node if it's the only scrollable node on the screen. In
        // that case the node will be found via BFS traversal.
        if (navigationAction.actionType == NavigationAction.SCROLL_LEFT
            || navigationAction.actionType == NavigationAction.SCROLL_RIGHT) {
          return performPageOrScrollAction(navigationAction, pageOrScrollNode, eventId);
        }
      } else {
        return performPageOrScrollAction(navigationAction, pageOrScrollNode, eventId);
      }
    }

    AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
    AccessibilityNodeInfoCompat nodeFromBfs = null;

    if (navigationAction.actionType == NavigationAction.SCROLL_UP
        || navigationAction.actionType == NavigationAction.SCROLL_DOWN) {
      nodeFromBfs = searchScrollableNodeFromBfs(rootNode, navigationAction, true);
    } else if (navigationAction.actionType == NavigationAction.SCROLL_LEFT
        || navigationAction.actionType == NavigationAction.SCROLL_RIGHT) {
      nodeFromBfs = searchScrollableNodeFromBfs(rootNode, navigationAction, false);
    }

    if (nodeFromBfs != null) {
      return performPageOrScrollAction(navigationAction, nodeFromBfs, eventId);
    }

    return false;
  }

  private boolean performPageOrScrollAction(
      NavigationAction navigationAction,
      @Nullable AccessibilityNodeInfoCompat scrollableNode,
      EventId eventId) {
    if (scrollableNode == null) {
      return false;
    }
    // SCROLL_UP/SCROLL_LEFT action means the content should move "UP/LEFT" for this action and
    // rest actions follow the same rule
    switch (navigationAction.actionType) {
      case NavigationAction.SCROLL_UP -> {
        if (AccessibilityNodeInfoUtils.supportsAction(scrollableNode, ACTION_PAGE_DOWN.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_PAGE_DOWN.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_DOWN.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_DOWN.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_FORWARD.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_FORWARD.getId()));
        }
      }
      case NavigationAction.SCROLL_DOWN -> {
        if (AccessibilityNodeInfoUtils.supportsAction(scrollableNode, ACTION_PAGE_UP.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_PAGE_UP.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_UP.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_UP.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_BACKWARD.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_BACKWARD.getId()));
        }
      }
      case NavigationAction.SCROLL_LEFT -> {
        if (AccessibilityNodeInfoUtils.supportsAction(scrollableNode, ACTION_PAGE_RIGHT.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_PAGE_RIGHT.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_RIGHT.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_RIGHT.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_FORWARD.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_FORWARD.getId()));
        }
      }
      case NavigationAction.SCROLL_RIGHT -> {
        if (AccessibilityNodeInfoUtils.supportsAction(scrollableNode, ACTION_PAGE_LEFT.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_PAGE_LEFT.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_LEFT.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_LEFT.getId()));
        } else if (AccessibilityNodeInfoUtils.supportsAction(
            scrollableNode, ACTION_SCROLL_BACKWARD.getId())) {
          return pipeline.returnFeedback(
              eventId, Feedback.nodeAction(scrollableNode, ACTION_SCROLL_BACKWARD.getId()));
        }
      }
      default -> {
        return false;
      }
    }
    return false;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Internal directional navigation methods based on target types.

  /**
   * Navigates to html target from a web pivot.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToHtmlTarget(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    return pipeline.returnFeedback(eventId, Feedback.webDirectionHtml(pivot, navigationAction));
  }

  /**
   * Finds next node in a different container (list/grid/pager/recycler/scrollable/containerTitle).
   */
  public AccessibilityNodeInfoCompat findContainerTarget(
      @Nullable AccessibilityNodeInfoCompat start, @NonNull NavigationAction navigationAction) {
    if (start == null) {
      return null;
    }

    @SearchDirection int direction = navigationAction.searchDirection;
    @Nullable AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(start);

    // Find current container, searching up from current-focus.
    @Nullable AccessibilityNodeInfoCompat containerOld = currentContainer(start);
    LogUtils.v(
        TAG,
        "FocusProcessorForLogicalNavigation.findContainerTarget() containerOld=%s",
        containerOld);

    // Find next node in traversal order that has a different container.
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(root, focusFinder, direction);
    @Nullable AccessibilityNodeInfoCompat target =
        TraversalStrategyUtils.searchFocus(
            traversalStrategy,
            start,
            direction,
            Filter.node(
                n ->
                    !Objects.equals(containerOld, currentContainer(n))
                        && AccessibilityNodeInfoUtils.shouldFocusNode(
                            n, traversalStrategy.getSpeakingNodesCache())));
    LogUtils.v(
        TAG,
        "FocusProcessorForLogicalNavigation.findContainerTarget() target container=%s",
        currentContainer(target));
    return target;
  }

  /** Finds current container (list/grid/pager/recycler/scrollable/containerTitle...) */
  private @Nullable AccessibilityNodeInfoCompat currentContainer(
      @Nullable AccessibilityNodeInfoCompat start) {
    if (start == null) {
      return null;
    }

    // Search up from start-node.
    @Nullable AccessibilityNodeInfoCompat container =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(start, FILTER_CONTAINER);
    return container;
  }

  /**
   * Navigates into the next or previous window.
   *
   * <p>Called when the user performs window navigation with keyboard shortcuts.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToWindowTarget(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    AccessibilityWindowInfo currentWindow = AccessibilityNodeInfoUtils.getWindow(pivot.unwrap());

    // When stealing the next window gesture - typically for a heads-up notification - try to
    // put focus on the target first. If this fails, use the default window navigation logic.
    if (stealWindowNavigationTarget != null && stealWindowNavigationTarget.refresh()) {
      stealWindowNavigationTarget =
          AccessibilityNodeInfoUtils.getSelfOrMatchingDescendant(
              stealWindowNavigationTarget, FILTER_SHOULD_FOCUS);
      if (stealWindowNavigationTarget != null) {
        boolean stoleFocus = false;
        boolean isScreenRtl = WindowUtils.isScreenLayoutRTL(service);
        int logicalDirection =
            TraversalStrategyUtils.getLogicalDirection(
                navigationAction.searchDirection, isScreenRtl);
        int stealLogicalDirection =
            TraversalStrategyUtils.getLogicalDirection(
                stealWindowNavigationTargetDirection, isScreenRtl);
        if (logicalDirection == stealLogicalDirection) {
          stoleFocus =
              setAccessibilityFocusInternal(stealWindowNavigationTarget, navigationAction, eventId);
        }
        LogUtils.d(
            TAG,
            "Try to steal focus with target=%s, steal direction=%s, NavigationAction direction=%s,"
                + " stoleFocus=%b ",
            stealWindowNavigationTarget,
            TraversalStrategyUtils.directionToString(stealWindowNavigationTargetDirection),
            TraversalStrategyUtils.directionToString(navigationAction.searchDirection),
            stoleFocus);

        stealWindowNavigationTarget = null;
        stealWindowNavigationTargetDirection = TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
        if (stoleFocus) {
          return true;
        }
        // Stealing focus didn't work, continue with default logic.
      }
    }

    if (!filterWindowForWindowNavigation.accept(currentWindow)) {
      return false;
    }

    // Navigate to pane window target if it is available.
    if (navigateToWindowPaneTarget(pivot, navigationAction, eventId)) {
      return true;
    }

    Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache = new HashMap<>();
    WindowTraversal windowTraversal = new WindowTraversal(service);
    boolean isScreenRtl = WindowUtils.isScreenLayoutRTL(service);
    AccessibilityNodeInfoCompat target =
        searchTargetInNextOrPreviousWindow(
            screenState.getStableScreenState(),
            windowTraversal,
            isScreenRtl,
            /* currentFocus= */ null,
            currentWindow,
            navigationAction.searchDirection,
            focusFinder,
            /* isInitialFocus= */ true,
            filterWindowForWindowNavigation,
            NavigationTarget.createNodeFilter(NavigationTarget.TARGET_DEFAULT, speakingNodesCache));
    return (target != null) && setAccessibilityFocusInternal(target, navigationAction, eventId);
  }

  /**
   * Navigates into the next or previous window pane.
   *
   * <p>Called when the user performs window navigation with keyboard shortcuts.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToWindowPaneTarget(
      @Nullable AccessibilityNodeInfoCompat pivot,
      NavigationAction navigationAction,
      EventId eventId) {
    if (pivot == null) {
      return false;
    }
    if (screenState.getStableScreenState() == null
        || !screenState.getStableScreenState().hasAccessibilityPane(pivot.getWindowId())) {
      // Return if the active window doesn't contain window pane.
      return false;
    }

    @SearchDirection int direction = navigationAction.searchDirection;
    @Nullable AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(pivot);
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(root, focusFinder, direction);
    @Nullable AccessibilityNodeInfoCompat pivotPaneContainer = currentPaneContainer(pivot);
    // If starting pivot is inside a pane...
    if (pivotPaneContainer != null) {
      // Move focus to the next/previous node outside of this pane container.
      AccessibilityNodeInfoCompat target =
          TraversalStrategyUtils.searchFocus(
              traversalStrategy,
              pivot,
              direction,
              Filter.node(
                  n ->
                      !Objects.equals(pivotPaneContainer, currentPaneContainer(n))
                          && AccessibilityNodeInfoUtils.shouldFocusNode(
                              n, traversalStrategy.getSpeakingNodesCache())));
      if (target != null) {
        return setAccessibilityFocusInternal(target, navigationAction, eventId);
      }
    } else {
      @Nullable AccessibilityNodeInfoCompat targetInWindowPane =
          TraversalStrategyUtils.searchFocus(
              traversalStrategy,
              pivot,
              direction,
              Filter.node(n -> !TextUtils.isEmpty(n.getPaneTitle())));
      if (targetInWindowPane == null) {
        return false;
      }
      // Navigates to the target in window pane.
      AccessibilityNodeInfoCompat target =
          TraversalStrategyUtils.findFirstFocusInNodeTree(
              traversalStrategy,
              targetInWindowPane,
              direction,
              NavigationTarget.createNodeFilter(
                  NavigationTarget.TARGET_DEFAULT, traversalStrategy.getSpeakingNodesCache()));
      if (target != null) {
        return setAccessibilityFocusInternal(target, navigationAction, eventId);
      }
    }

    return false;
  }

  /** Finds current window-pane container. */
  private @Nullable AccessibilityNodeInfoCompat currentPaneContainer(
      @Nullable AccessibilityNodeInfoCompat start) {
    return (start == null)
        ? null
        : AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            start, Filter.node(n -> !TextUtils.isEmpty(n.getPaneTitle())));
  }

  /**
   * Navigates to screen-search target.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToScreenSearchTarget(
      @NonNull AccessibilityNodeInfoCompat pivot,
      NavigationAction navigationAction,
      EventId eventId) {
    CharSequence keyword = searchState.getLastKeyword();
    if (TextUtils.isEmpty(keyword)) {
      LogUtils.d(TAG, "navigateToScreenSearchTarget  keyword empty");
      announce(
          FeatureSupport.isMultiFingerGestureSupported()
              ? service.getString(R.string.screen_search_no_keyword_hint)
              : service.getString(R.string.screen_search_no_keyword_hint_pre_r),
          eventId);
      return false;
    }

    AccessibilityNodeInfoCompat target = findSearchTarget(pivot, navigationAction, keyword);
    ScrollableNodeInfo scrollableNodeInfo =
        ScrollableNodeInfo.findScrollableNodeForDirection(
            navigationAction.searchDirection,
            pivot,
            /* includeSelf= */ true,
            WindowUtils.isScreenLayoutRTL(service));
    if (scrollableNodeInfo != null
        && (target == null
            || (!Objects.equals(
                findContainerTarget(pivot, navigationAction),
                findContainerTarget(target, navigationAction))))) {
      // Auto-scroll then search again if no target found on current page or container changed.
      if (autoScroll(scrollableNodeInfo, pivot, navigationAction, eventId)) {
        return true;
      }
    }

    if (target == null) {
      announceNoSearchResult(navigationAction.searchDirection, eventId);
      return false;
    }
    return setAccessibilityFocusInternal(target, navigationAction, eventId);
  }

  /** Finds next or previous screen search target. */
  private @Nullable AccessibilityNodeInfoCompat findSearchTarget(
      @Nullable AccessibilityNodeInfoCompat pivot,
      @NonNull NavigationAction navigationAction,
      CharSequence keyword) {
    if (pivot == null || TextUtils.isEmpty(keyword)) {
      return null;
    }

    @SearchDirection int direction = navigationAction.searchDirection;
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            AccessibilityNodeInfoUtils.getRoot(pivot),
            focusFinder,
            OrderedTraversalStrategyConfig.builder()
                .setSearchDirection(direction)
                .setIncludeChildrenOfNodesWithWebActions(true)
                .build());
    AccessibilityNodeInfoCompat focusablePivot =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            pivot, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
    @Nullable AccessibilityNodeInfoCompat target =
        TraversalStrategyUtils.searchFocus(
            traversalStrategy,
            pivot,
            direction,
            Filter.node(
                node -> {
                  if (node == null) {
                    return false;
                  }
                  if (!AccessibilityNodeInfoUtils.isVisible(node)) {
                    return false;
                  }
                  // Skip the same focusable parent. The search target that should not focus may
                  // have the same focusable parent with the nodes inside the focusable parent. It
                  // will help navigation across different focusable screen-search target.
                  if (Objects.equals(
                      focusablePivot,
                      AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
                          node, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS))) {
                    return false;
                  }
                  CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
                  if (TextUtils.isEmpty(nodeText)) {
                    return false;
                  }
                  return !StringMatcher.findMatches(nodeText.toString(), keyword.toString())
                      .isEmpty();
                }));

    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
        target, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
  }

  /**
   * Navigates to default target or macro granularity target.
   *
   * <p>This navigation action happens in four use cases:
   *
   * <ul>
   *   <li>The user is navigating with default granularity
   *   <li>The use is navigating with native macro granularity.
   *   <li>The use is navigating with html macro granularity.
   *   <li>The user is navigating with micro granularity, but reaches edge of current node, and
   *       needs to move focus to another node. In this case {@link
   *       NavigationAction#originalNavigationGranularity} is not {@code null} and {link
   *       CursorGranularity#isMicroGranularity} is {@code true}.
   * </ul>
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToDefaultOrMacroGranularityTarget(
      @NonNull AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      EventId eventId) {
    int searchDirection = navigationAction.searchDirection;
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            searchDirection, WindowUtils.isScreenLayoutRTL(service));

    AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
    if (rootNode == null) {
      LogUtils.w(TAG, "Cannot perform navigation action: unable to find root node.");
      return false;
    }

    // Perform auto-scroll action if necessary.
    if (autoScrollAtEdge(pivot, ignoreDescendantsOfPivot, navigationAction, eventId)) {
      return true;
    }

    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(rootNode, focusFinder, searchDirection);

    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            navigationAction.targetType, traversalStrategy.getSpeakingNodesCache());
    if (ignoreDescendantsOfPivot) {
      final AccessibilityNodeInfoCompat pivotCopy = pivot;
      nodeFilter =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return !AccessibilityNodeInfoUtils.hasAncestor(node, pivotCopy);
            }
          }.and(nodeFilter);
    }

    NavigationResult navigationResult = NavigationResult.create(NavigationResult.Type.EMPTY);

    // Search for target node within current window.
    // Consider the following linear order when navigating between web elements and native elements
    // assuming that a WebView container is positioned between native elements:
    // - Forward: Native elements -> WebView container -> web elements -> native elements.
    // - Backward: Native elements -> web elements -> WebView container -> native elements.
    // Note: the design assumes there is only one WebView and multiple WebView should be a corner
    // case which is out of the scope.
    if (WebInterfaceUtils.supportsWebActions(pivot)) {
      navigationResult =
          findTargetFromWebElement(pivot, navigationAction, nodeFilter, traversalStrategy, eventId);
    } else if (navigationAction.targetType == TARGET_CONTAINER) {
      navigationResult = NavigationResult.create(findContainerTarget(pivot, navigationAction));
    } else {
      int linkIndex = getLinkIndexFromPivot(pivot, navigationAction);
      if (linkIndex >= 0) {
        navigationResult = NavigationResult.create(pivot);
        navigationResult.setLinkIndex(linkIndex);
      } else {
        navigationResult =
            findTargetFromNativeElement(
                pivot, navigationAction, nodeFilter, traversalStrategy, eventId);
      }
    }
    if (navigationResult.shouldSkipNavigation()) {
      if (navigationResult.isWebElement()) {
        reachEdge = false;
        LogUtils.d(
            TAG, "Return and reset reachEdge, web element focus will be handled by the framework.");
      }
      return navigationResult.isFocusAvailable();
    }

    if (scrollAfterFindTarget(
        pivot, navigationResult.getNode(), ignoreDescendantsOfPivot, navigationAction, eventId)) {
      return true;
    }

    // Special rule to optimize the traversal order of AutoCompleteTextView suggestions.
    NavigationResult suggestedResult =
        findTargetForEditTextSuggestions(
            pivot, navigationAction, nodeFilter, navigationResult.isEmpty());
    if (!suggestedResult.isEmpty()) {
      navigationResult = suggestedResult;
      LogUtils.d(TAG, "Target is overrided by findSuggestedTargetForEditText");
      if (navigationResult.shouldSkipNavigation()) {
        return navigationResult.isFocusAvailable();
      }
    }

    // No target available in the current window, so navigate across windows.
    if (isWindowNavigationSupported && navigationResult.isEmpty()) {
      navigationResult = findTargetAcrossWindows(pivot, navigationAction, eventId);
      if (navigationResult.shouldSkipNavigation()) {
        return navigationResult.isFocusAvailable();
      }
    }

    // Try to wrap around inside current window if reaching the edge.
    if (reachEdge && navigationAction.shouldWrap && navigationResult.isEmpty()) {
      navigationResult =
          findTargetForWrapAround(rootNode, navigationAction, traversalStrategy, eventId);
      if (navigationResult.shouldSkipNavigation()) {
        if (navigationResult.isWebElement()) {
          reachEdge = false;
          LogUtils.d(
              TAG,
              "Return and reset reachEdge, web element focus will be handled by the framework.");
        }
        return navigationResult.isFocusAvailable();
      }
    }

    // Special rule to search focus for directional traversal.
    if ((navigationAction.targetType == NavigationTarget.TARGET_DEFAULT)
        && TraversalStrategyUtils.isSpatialDirection(navigationAction.searchDirection)) {
      suggestedResult =
          findTargetForDirectionalTraveral(
              pivot, navigationResult.getNode(), navigationAction.searchDirection);
      if (!suggestedResult.isEmpty()) {
        navigationResult = suggestedResult;
        LogUtils.d(TAG, "Target is overrided by findTargetForDirectionalTraveral");
        if (navigationResult.shouldSkipNavigation()) {
          return navigationResult.isFocusAvailable();
        }
      }
    }

    AccessibilityNodeInfoCompat target = navigationResult.getNode();

    if ((target != null) && navigationAction.shouldScroll) {
      boolean scrolled = ensureOnScreen(target, navigationAction.searchDirection, eventId);
      // REFERTO If ensureOnScreen caused scrolling, we need use the scroll callback
      // to set focus on the next node (from the pivot) inside scrollable parent. This is helpful
      // to find focus that was invisible before scrolling.
      if (scrolled && (scrollCallback == null || !scrollCallback.assumeScrollSuccess())) {
        // REFERTO Framework might not send TYPE_VIEW_SCROLLED event back after
        // ensureOnScreen. Register a scrollCallBack may make the scroll timeout and thus
        // searching nodes outside the scrollable and fail. So we ignore scroll fail in this case
        // by setting assumeScrollSuccess to true. To prevent recursively searching focus after
        // scroll fail, we also check whether the caller comes from assumeScrollSuccess to ensure
        // it only bypass once.
        // TODO: remove the workaround after fixing the bug in framework and a11y
        // event is ready.
        scrollCallback =
            new AutoScrollCallback(this, navigationAction, pivot, /* assumeScrollSuccess= */ true);
        if (SPEAK_AHEAD_OF_SCROLL) {
          speakAheadOfScroll(target, navigationAction, eventId);
        }
        return true;
      }
    }

    if (target != null) {
      int linkIndexToFocus = getLinkIndexForFocus(pivot, navigationResult, navigationAction);
      if (linkIndexToFocus >= 0) {
        // Announce the single link feedback instead of a11y focus feedback.
        announceSingleLinkFocused(LinkUtils.getLinkText(target, linkIndexToFocus), eventId);
        return setAccessibilityFocusInternal(target, navigationAction, linkIndexToFocus, eventId);
      } else {
        return setAccessibilityFocusInternal(target, navigationAction, eventId);
      }
    }

    // No target found.
    announceNativeElement(logicalDirection, navigationAction.targetType, eventId);
    return false;
  }

  /** Generates the speech and hint for the current focused single link. */
  private void announceSingleLinkFocused(String text, EventId eventId) {
    if (TextUtils.isEmpty(text)) {
      LogUtils.w(TAG, "Empty link text");
      return;
    }
    SpeechController.SpeakOptions speakOptions =
        SpeechController.SpeakOptions.create()
            .setQueueMode(SpeechController.QUEUE_MODE_FLUSH_ALL)
            .setFlags(FeedbackItem.FLAG_FORCE_FEEDBACK);
    pipeline.returnFeedback(
        eventId, ProcessorAccessibilityHints.singleLinkToHint(service, compositor));
    pipeline.returnFeedback(eventId, Feedback.speech(text, speakOptions));
  }

  /**
   * Perform auto-scroll action after finding the target. Sometimes the default target might not be
   * the appropriate target so that it needs to do the extra scroll to find the appropriate target
   * later.
   *
   * <p>Note: For Grid, position-to-scroll action helps to scroll grid page while TalkBack needs
   * handling the focus target additionally.
   */
  private boolean scrollAfterFindTarget(
      AccessibilityNodeInfoCompat pivot,
      AccessibilityNodeInfoCompat target,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      EventId eventId) {
    if (WebInterfaceUtils.supportsWebActions(pivot)) {
      // Do not scrollAfterFindTarget for web element due to web already handle this.
      LogUtils.v(TAG, "scrollAfterFindTarget returns due to web element");
      return false;
    }
    // Scrolls for GRID traversal if necessary.
    int searchDirection = navigationAction.searchDirection;
    if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT
        && TraversalStrategyUtils.isLogicalDirection(searchDirection)) {
      AccessibilityNodeInfoCompat grid =
          AccessibilityNodeInfoUtils.getMatchingAncestor(pivot, FILTER_SCROLLABLE_GRID);
      if (grid != null && target != null) {
        int logicalDirection =
            TraversalStrategyUtils.getLogicalDirection(
                searchDirection, WindowUtils.isScreenLayoutRTL(service));
        // For horizontal grids, the logical target may lie outside the current screen and the
        // target provided by the framework may be incorrect. We use GridTraversalManager, which
        // uses the grid's CollectionInfo and the pivot node and target node's CollectionItemInfo to
        // suggest an alternate target. If performing the scroll action failed, fallback to the
        // original searching focus strategy.
        Pair<Integer, Integer> targetPositionForScroll =
            GridTraversalManager.suggestOffScreenTarget(grid, pivot, target, logicalDirection);
        if (performActionScrollToPosition(
            grid,
            targetPositionForScroll,
            NavigationAction.Builder.copy(navigationAction)
                .setPositionForScroll(targetPositionForScroll)
                .build(),
            eventId)) {
          return true;
        }
      }
    }

    // Scrolls for macro granularity navigation if necessary. Attempts to scroll is made only
    // if ignoreDescendantsOfPivot is set to false to prevent an infinite loop.
    if ((NavigationTarget.isMacroGranularity(navigationAction.targetType))
        && !ignoreDescendantsOfPivot) {
      boolean scrolled =
          scrollForNativeMacroGranularity(
              target, navigationAction, getScrollFilter(navigationAction), eventId);
      if (scrolled) {
        return true;
      }
    }
    return false;
  }

  /**
   * If the pivot is native view, searches for the middlePivot node first and then determines to
   * navigate to native or web element.
   */
  private @NonNull NavigationResult findTargetFromNativeElement(
      AccessibilityNodeInfoCompat nativePivot,
      NavigationAction navigationAction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter,
      TraversalStrategy traversalStrategy,
      EventId eventId) {
    // Filter to find the native node with target type or WebView, returns WebView if find it first.
    Filter<AccessibilityNodeInfoCompat> nodeFilterOrWebView =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return (Role.getRole(node) == Role.ROLE_WEB_VIEW)
                && WebInterfaceUtils.supportsWebActions(node);
          }
        }.or(nodeFilter);

    AccessibilityNodeInfoCompat middlePivot =
        TraversalStrategyUtils.searchFocus(
            traversalStrategy, nativePivot, navigationAction.searchDirection, nodeFilterOrWebView);
    return findTargetFromMiddlePivot(
        middlePivot, navigationAction, nodeFilter, traversalStrategy, eventId);
  }

  /**
   * Returns the valid next/previous link index from the pivot if the pivot is the current focused
   * node containing the focused link. Otherwise returns -1.
   */
  private int getLinkIndexFromPivot(
      AccessibilityNodeInfoCompat nativePivot, NavigationAction navigationAction) {
    if (!supportClickableLinks(navigationAction.targetType) || reachEdge) {
      return FocusActionInfo.LINK_INDEX_NOT_SET;
    }
    int linkSpanCount = LinkUtils.getLinkSpansInNodeGroup(nativePivot).size();
    if (linkSpanCount == 0) {
      return FocusActionInfo.LINK_INDEX_NOT_SET;
    }
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            navigationAction.searchDirection, WindowUtils.isScreenLayoutRTL(service));
    FocusActionRecord record = actorState.getFocusHistory().getLastFocusActionRecord();
    int oldIndex = record.getExtraInfo().getFocusedLinkIndex();
    int newIndex = -1;
    if (oldIndex >= 0 && nativePivot.equals(record.getFocusedNode())) {
      if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
        newIndex = oldIndex + 1;
      } else {
        newIndex = oldIndex - 1;
      }
    }
    if (newIndex >= 0 && newIndex < linkSpanCount) {
      LogUtils.v(TAG, "getLinkIndexFromPivot=" + newIndex);
      return newIndex;
    }
    return FocusActionInfo.LINK_INDEX_NOT_SET;
  }

  /**
   * Returns the valid previous/next initial link index from the target which is going to gain the
   * focus. Otherwise returns -1.
   */
  private int getLinkIndexFromTarget(
      AccessibilityNodeInfoCompat nativeTarget, NavigationAction navigationAction) {
    if (!supportClickableLinks(navigationAction.targetType)) {
      return FocusActionInfo.LINK_INDEX_NOT_SET;
    }
    int linkSpanCount = LinkUtils.getLinkSpansInNodeGroup(nativeTarget).size();
    if (linkSpanCount == 0) {
      return FocusActionInfo.LINK_INDEX_NOT_SET;
    }
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            navigationAction.searchDirection, WindowUtils.isScreenLayoutRTL(service));
    int newIndex;
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      newIndex = 0;
    } else {
      newIndex = linkSpanCount - 1;
    }
    LogUtils.v(TAG, "getLinkIndexFromTarget=" + newIndex);
    return newIndex;
  }

  /**
   * Returns the next/previous link index for focus from the pivot or the target. Otherwise returns
   * -1.
   */
  private int getLinkIndexForFocus(
      AccessibilityNodeInfoCompat pivot,
      NavigationResult navigationResult,
      NavigationAction navigationAction) {
    int linkIndex;
    if (navigationResult.getLinkIndex() >= 0 && pivot.equals(navigationResult.getNode())) {
      linkIndex = navigationResult.getLinkIndex();
    } else {
      linkIndex = getLinkIndexFromTarget(navigationResult.getNode(), navigationAction);
    }
    return linkIndex;
  }

  private boolean supportClickableLinks(int targetType) {
    return globalVariables.supportClickableLinks() && targetType == NavigationTarget.TARGET_LINK;
  }

  /**
   * The middle-pivot must be WebView container or native element. If it is WebView container,
   * navigates to WebView container inside. Otherwise returns the native element.
   */
  private @NonNull NavigationResult findTargetFromMiddlePivot(
      AccessibilityNodeInfoCompat middlePivot,
      NavigationAction navigationAction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter,
      TraversalStrategy traversalStrategy,
      EventId eventId) {
    if (middlePivot == null) {
      return NavigationResult.create(NavigationResult.Type.EMPTY);
    }
    if (!WebInterfaceUtils.supportsWebActions(middlePivot)) {
      // Middle-pivot is the native element and should be the target.
      return NavigationResult.create(middlePivot);
    }

    // Middle-pivot must be WebView container
    if (Role.getRole(middlePivot) != Role.ROLE_WEB_VIEW) {
      throw new IllegalArgumentException(
          "Middle-pivot must be either native or WebView container!");
    }
    if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT) {
      int searchDirection = navigationAction.searchDirection;
      int logicalDirection =
          TraversalStrategyUtils.getLogicalDirection(
              searchDirection, WindowUtils.isScreenLayoutRTL(service));
      if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
        // If the default previous native element is the WebView container,
        // navigate to html directly to find the last element in the WebView.
        return navigateToHtmlTargetWithFallBack(
            middlePivot, navigationAction, nodeFilter, traversalStrategy, eventId);
      } else {
        // If the default next native element is the WebView container,
        // returns the WebView container directly.
        return NavigationResult.create(middlePivot);
      }
    } else {
      return findTargetFromWebElement(
          middlePivot, navigationAction, nodeFilter, traversalStrategy, eventId);
    }
  }

  /**
   * If the pivot is WebView container to find the default previous node, it should looks for the
   * previous native element. For other web pivot, navigate to web elements.
   */
  private @NonNull NavigationResult findTargetFromWebElement(
      AccessibilityNodeInfoCompat webPivot,
      NavigationAction navigationAction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter,
      TraversalStrategy traversalStrategy,
      EventId eventId) {
    boolean isNodeFromClank =
        FeatureFlagReader.enableNewJumpNavigationForClank(service)
            && webPivot != null
            && webPivot.getPackageName() != null
            && webPivot.getPackageName().toString().equals(CLANK_PACKAGE_NAME);
    // If a JUMP_TO_BOTTOM action is being performed inside Clank container, let it traverse the
    // WebView so it can focus the last HTML item.
    boolean allowClankWebViewTraversal =
        isNodeFromClank && navigationAction.actionType == NavigationAction.JUMP_TO_BOTTOM;

    int searchDirection = navigationAction.searchDirection;
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            searchDirection, WindowUtils.isScreenLayoutRTL(service));
    // Prevent navigating to html target if the pivot is WebView container to find the default
    // previous node because it will find the wrong last item in the WebView.
    if (!allowClankWebViewTraversal
        && (Role.getRole(webPivot) == Role.ROLE_WEB_VIEW)
        && (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT)
        && (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD)) {
      AccessibilityNodeInfoCompat target =
          TraversalStrategyUtils.searchFocus(
              traversalStrategy, webPivot, searchDirection, nodeFilter);
      return NavigationResult.create(target);
    }
    return navigateToHtmlTargetWithFallBack(
        webPivot, navigationAction, nodeFilter, traversalStrategy, eventId);
  }

  /**
   * Finds the next or previous web element. If it fails, jump out of the WebView container to find
   * the next or previous native element as the fallback.
   */
  private NavigationResult navigateToHtmlTargetWithFallBack(
      AccessibilityNodeInfoCompat webPivot,
      NavigationAction navigationAction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter,
      TraversalStrategy traversalStrategy,
      EventId eventId) {
    if (navigateToHtmlTarget(webPivot, navigationAction, eventId)) {
      return NavigationResult.create(NavigationResult.Type.WEB_ELEMENT);
    }
    // Navigate out of WebView with normal navigation
    AccessibilityNodeInfoCompat webContainer = webPivot;
    if (Role.getRole(webPivot) != Role.ROLE_WEB_VIEW) {
      webContainer = WebInterfaceUtils.ascendToWebView(webPivot);
    }
    AccessibilityNodeInfoCompat target =
        TraversalStrategyUtils.searchFocus(
            traversalStrategy, webContainer, navigationAction.searchDirection, nodeFilter);
    return NavigationResult.create(target);
  }

  /**
   * Optimize the traversal order of AutoCompleteTextView suggestions to make the suggestions
   * navigable immediately after AutoCompleteTextView.
   */
  private @NonNull NavigationResult findTargetForEditTextSuggestions(
      AccessibilityNodeInfoCompat pivot,
      NavigationAction navigationAction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter,
      boolean isNavigationResultEmpty) {
    TraversalStrategy traversalStrategy = null;
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat target = null;
    int searchDirection = navigationAction.searchDirection;
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            searchDirection, WindowUtils.isScreenLayoutRTL(service));
    AccessibilityWindowInfo currentWindow = AccessibilityNodeInfoUtils.getWindow(pivot.unwrap());
    AccessibilityNodeInfoCompat anchorNode = AccessibilityWindowInfoUtils.getAnchor(currentWindow);
    if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT
        && logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD
        && Role.getRole(pivot) == Role.ROLE_EDIT_TEXT) {
      // Set the target to the initial focus node of the anchored window.
      AccessibilityWindowInfo anchoredWindow =
          AccessibilityWindowInfoUtils.getAnchoredWindow(pivot.unwrap());
      if (anchoredWindow != null) {
        rootNode = AccessibilityWindowInfoUtils.getRootCompat(anchoredWindow);
        traversalStrategy = createTraversal(rootNode, searchDirection);
        target =
            TraversalStrategyUtils.findFirstFocusInNodeTree(
                traversalStrategy, rootNode, searchDirection, nodeFilter);
      }
    } else if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT
        && isNavigationResultEmpty
        && anchorNode != null
        && Role.getRole(anchorNode) == Role.ROLE_EDIT_TEXT) {
      // Return talkback-focus to the parent-window.
      if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
        // Set the target back to the anchor node.
        target = AccessibilityWindowInfoUtils.getAnchor(currentWindow);
      } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
        // Set the target to the next traversal node of the anchor node.
        rootNode = AccessibilityNodeInfoUtils.getRoot(anchorNode);
        if (rootNode != null) {
          traversalStrategy = createTraversal(rootNode, searchDirection);
          target =
              TraversalStrategyUtils.searchFocus(
                  traversalStrategy, anchorNode, searchDirection, nodeFilter);
        }
      }
    }
    return NavigationResult.create(target);
  }

  /**
   * Finds the target node in the next or previous window, and the target window is determined by
   * {@code DirectionalNavigationWindowFilter}.
   *
   * <p>It assumes there is no WebView across windows and always find the native elements first.
   */
  private @NonNull NavigationResult findTargetAcrossWindows(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    WindowTraversal windowTraversal = new WindowTraversal(service);
    boolean isScreenRtl = WindowUtils.isScreenLayoutRTL(service);
    DirectionalNavigationWindowFilter windowFilter =
        new DirectionalNavigationWindowFilter(service, searchState);
    int searchDirection = navigationAction.searchDirection;
    int logicalDirection = TraversalStrategyUtils.getLogicalDirection(searchDirection, isScreenRtl);
    @Nullable AccessibilityWindowInfo currentWindow =
        AccessibilityNodeInfoUtils.getWindow(pivot.unwrap());

    if (currentWindow == null) {
      // Ideally currentWindow should never be null. Do the null check in case of exception.
      LogUtils.w(TAG, "Cannot navigate across window: unable to identify current window");
      return NavigationResult.create(NavigationResult.Type.EXCEPTION);
    }

    // Skip one swipe if it's the last element in the last window.
    if (!reachEdge
        && (!windowFilter.accept(currentWindow)
            || needPauseWhenTraverseAcrossWindow(
                windowTraversal, isScreenRtl, currentWindow, searchDirection, windowFilter))) {
      reachEdge = true;
      announceNativeElement(logicalDirection, navigationAction.targetType, eventId);
      LogUtils.v(TAG, "Reach edge before searchTargetInNextOrPreviousWindow in:" + currentWindow);
      return NavigationResult.create(NavigationResult.Type.REACH_EDGE);
    }

    AccessibilityNodeInfoCompat target = null;
    if (windowFilter.accept(currentWindow)) {
      // By default, when navigating across windows, the focus is placed on the first/last element
      // of the new window. However, if transitioning from an IME window, it may cause many
      // exceptions if there are many items between the editing node and the last element in the
      // window. So we'll prefer to use the editing node when performing backward navigation cross
      // windows.
      if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD
          && AccessibilityWindowInfoUtils.isImeWindow(currentWindow)) {
        target = accessibilityFocusMonitor.getInputFocus();
      }

      if (!AccessibilityNodeInfoUtils.shouldFocusNode(target)) {
        boolean reachEdgeBeforeSearch = reachEdge;
        Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache = new HashMap<>();
        target =
            searchTargetInNextOrPreviousWindow(
                screenState.getStableScreenState(),
                windowTraversal,
                isScreenRtl,
                pivot,
                currentWindow,
                searchDirection,
                focusFinder,
                /* isInitialFocus= */ false,
                windowFilter,
                NavigationTarget.createNodeFilter(navigationAction.targetType, speakingNodesCache));
        if (reachEdgeBeforeSearch != reachEdge) {
          // Skip one swipe if reaching edge while searching windows in loop.
          announceNativeElement(logicalDirection, navigationAction.targetType, eventId);
          return NavigationResult.create(NavigationResult.Type.REACH_EDGE);
        }
      }
    }
    return NavigationResult.create(target);
  }

  /**
   * Try to wrap around inside current window, which is equivalent to find the first native or web
   * element in the current window with the given search direction.
   */
  private NavigationResult findTargetForWrapAround(
      AccessibilityNodeInfoCompat root,
      NavigationAction navigationAction,
      TraversalStrategy traversalStrategy,
      EventId eventId) {
    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            navigationAction.targetType, traversalStrategy.getSpeakingNodesCache());
    // Filter to find the native node with target type or WebView, returns WebView if find it first.
    Filter<AccessibilityNodeInfoCompat> nodeFilterOrWebView =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return (Role.getRole(node) == Role.ROLE_WEB_VIEW)
                && WebInterfaceUtils.supportsWebActions(node);
          }
        }.or(nodeFilter);

    AccessibilityNodeInfoCompat middlePivot =
        TraversalStrategyUtils.findFirstFocusInNodeTree(
            traversalStrategy, root, navigationAction.searchDirection, nodeFilterOrWebView);
    return findTargetFromMiddlePivot(
        middlePivot, navigationAction, nodeFilter, traversalStrategy, eventId);
  }

  /**
   * When searching the next focus, potentially consider the result of View.focusSearch() which
   * defines the next input focus in the given direction in the absence of an accessibility service.
   * If only one of the TalkBack target and the focusSearch target is accessibility-focusable, or
   * exists in the first place, prefer that one. If only one of them is input-focusable (or
   * enabled), prefer the one that is not. If both are input-focusable (and enabled), prefer the
   * focusSearch target.
   */
  private NavigationResult findTargetForDirectionalTraveral(
      AccessibilityNodeInfoCompat pivot, AccessibilityNodeInfoCompat target, int searchDirection) {
    int focusDirection =
        TraversalStrategyUtils.nodeSearchDirectionToViewSearchDirection(searchDirection);
    AccessibilityNodeInfoCompat focusSearchTarget = pivot.focusSearch(focusDirection);
    // Potentially allow the currently focused node to keep focus even if not
    // accessibility-focusable.
    // The reason is that per default it is marked as not accessibility-focusable if it has focus.
    if ((focusSearchTarget != null)
        && focusSearchTarget.isAccessibilityFocused()
        && allowFocusResting(target)) {
      LogUtils.d(TAG, "Using focusSearch() target which is the already focused node.");
      return NavigationResult.create(NavigationResult.Type.EXISTING_FOCUS);
    }
    if ((focusSearchTarget != null)
        && !focusSearchTarget.equals(target)
        && (!focusSearchTarget.isFocusable() || (target == null) || target.isFocusable())
        && (!focusSearchTarget.isEnabled() || (target == null) || target.isEnabled())
        && AccessibilityNodeInfoUtils.shouldFocusNode(focusSearchTarget)
        && AccessibilityNodeInfoUtils.supportsAction(
            focusSearchTarget, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)) {
      LogUtils.d(
          TAG,
          (target == null)
              ? "Using focusSearch() target because TalkBack navigation target was null."
              : "Using focusSearch() target instead of TalkBack navigation target.");
      target = focusSearchTarget;
    }
    return NavigationResult.create(target);
  }

  private boolean allowFocusResting(@Nullable AccessibilityNodeInfoCompat talkBackTarget) {
    if (!FeatureFlagReader.allowFocusResting(/* context= */ service)) {
      return false;
    }
    if (talkBackTarget == null) {
      return true;
    }
    return talkBackTarget.isFocusable() && talkBackTarget.isEnabled();
  }

  private boolean performActionScrollToPosition(
      AccessibilityNodeInfoCompat nodeInfo,
      @Nullable Pair<Integer, Integer> targetPosition,
      NavigationAction navigationAction,
      EventId eventId) {
    if (targetPosition == null) {
      return false;
    }
    scrollCallback = new AutoScrollCallback(this, navigationAction, nodeInfo);
    Bundle arguments = new Bundle();
    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, targetPosition.first);
    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, targetPosition.second);
    return pipeline.returnFeedback(
        eventId,
        Feedback.scrollToPosition(
            nodeInfo,
            ScrollActionRecord.ACTION_AUTO_SCROLL,
            AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION.getId(),
            arguments,
            ScrollActionRecord.FOCUS,
            ScrollTimeout.SCROLL_TIMEOUT_LONG,
            navigationAction.autoScrollAttempt));
  }

  /** Returns {@code true} if current window is the last window on screen in traversal order. */
  private boolean needPauseWhenTraverseAcrossWindow(
      WindowTraversal windowTraversal,
      boolean isScreenRtl,
      AccessibilityWindowInfo currentWindow,
      @SearchDirection int searchDirection,
      Filter<AccessibilityWindowInfo> windowFilter) {
    @TraversalStrategy.SearchDirection
    int logicalDirection = TraversalStrategyUtils.getLogicalDirection(searchDirection, isScreenRtl);
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return windowTraversal.isLastWindow(currentWindow, windowFilter);
    } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return windowTraversal.isFirstWindow(currentWindow, windowFilter);
    } else {
      throw new IllegalStateException("Unknown logical direction");
    }
  }

  private boolean scrollForNativeMacroGranularity(
      AccessibilityNodeInfoCompat target,
      NavigationAction navigationAction,
      Filter<AccessibilityNodeInfoCompat> filterScrollable,
      EventId eventId) {

    AccessibilityNodeInfoCompat referenceNode = null;
    AccessibilityNodeInfoCompat focusedOrReferenceNodeParent = null;
    AccessibilityNodeInfoCompat a11yFocusedNode =
        accessibilityFocusMonitor.getAccessibilityFocus(
            navigationAction.useInputFocusAsPivotIfEmpty);
    boolean hasValidAccessibilityFocus =
        (a11yFocusedNode != null) && AccessibilityNodeInfoUtils.isVisible(a11yFocusedNode);

    if (hasValidAccessibilityFocus) {
      referenceNode = a11yFocusedNode;
      focusedOrReferenceNodeParent =
          AccessibilityNodeInfoUtils.getMatchingAncestor(a11yFocusedNode, filterScrollable);
    } else {
      // If a11y focus is not valid, we try to get the first child of the last scrolled container
      // to keep as a reference for scrolling. A visibility check is not required as it is just a
      // reference to start the scroll.
      referenceNode =
          refreshAndGetFirstOrLastChild(
              lastScrolledNodeForNativeMacroGranularity, /* firstChild= */ true);
      focusedOrReferenceNodeParent = lastScrolledNodeForNativeMacroGranularity;
    }

    // If we are navigating within a scrollable container with native macro granularity, we want
    // to make sure we have traversed the scrollable list at least once by auto-scroll before
    // jumping to an element that is on screen but out of the scrollable container. If the target
    // inside the scrollable view not found, it would fallback to use the target outside of the
    // scrollable container.
    if ((focusedOrReferenceNodeParent != null)
        && (target != null)
        && !AccessibilityNodeInfoUtils.hasAncestor(target, focusedOrReferenceNodeParent)) {
      navigationAction =
          NavigationAction.Builder.copy(navigationAction).setFallbackTarget(target).build();
      target = null;
    }

    // If we find no target on screen for native macro granularity, we do our best attempt to
    // scroll to the next screen and place the focus on the new screen if it exists.
    if (target == null && referenceNode != null) {
      ScrollableNodeInfo scrollableNodeInfo =
          ScrollableNodeInfo.findScrollableNodeForDirection(
              navigationAction.searchDirection,
              referenceNode,
              /* includeSelf= */ true,
              /* rtl= */ WindowUtils.isScreenLayoutRTL(service));
      if (scrollableNodeInfo != null
          && autoScroll(scrollableNodeInfo, referenceNode, navigationAction, eventId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the first or the last child of a refreshed scrollable node.
   *
   * @param node The parent node whose first or last child is returned.
   * @param firstChild If {@code true} indicates first child, else last child.
   * @return First or last child of the {@code node}
   */
  private static @Nullable AccessibilityNodeInfoCompat refreshAndGetFirstOrLastChild(
      @Nullable AccessibilityNodeInfoCompat node, boolean firstChild) {
    // In this condition, we should successfully refresh the scrollable node to ensure that it
    // doesn't contain stale children.
    if (node != null && node.refresh() && node.getChildCount() > 0) {
      int childNumber = 0;
      if (!firstChild) {
        childNumber = node.getChildCount() - 1;
      }
      return node.getChild(childNumber);
    }
    return null;
  }

  /**
   * Searches for initial target node in the next or previous window.
   *
   * <p>It's used in two use cases:
   *
   * <ul>
   *   <li>Users performs window navigation with keyboard shortcuts, in which case {@code
   *       shouldRestoreLastFocus} is set to {@code true}, and we only accept windows from {@code
   *       FILTER_WINDOW_FOR_WINDOW_NAVIGATION}.
   *   <li>Users performs directional navigation across the first/last element of a window, in which
   *       case {@code shouldRestoreLastFocus} is set to {@code false}, and we only accept windows
   *       from {@code DirectionalNavigationWindowFilter}.
   * </ul>
   *
   * @param windowTraversal windowTraversal used to iterate though sorted window list.
   * @param isScreenRtl Whether it's in RTL mode.
   * @param pivot The node to check if it is an anchor node.
   * @param currentWindow Current {@link AccessibilityWindowInfo} which we start searching from.
   * @param direction Search direction.
   * @param focusFinder The {@link FocusFinder} instance to find focus in a given window.
   * @param isInitialFocus Whether it's for initial focus, if {@code true}, it will try to restore
   *     the focus from the focus history.
   * @param windowFilter Filters {@link AccessibilityWindowInfo}.
   * @param nodeFilter Filters for target node.
   * @return Accepted target node in the previous or next accepted window.
   */
  @VisibleForTesting
  public @Nullable AccessibilityNodeInfoCompat searchTargetInNextOrPreviousWindow(
      @Nullable ScreenState currentScreenState,
      WindowTraversal windowTraversal,
      boolean isScreenRtl,
      @Nullable AccessibilityNodeInfoCompat currentFocus,
      AccessibilityWindowInfo currentWindow,
      @TraversalStrategy.SearchDirection int direction,
      FocusFinder focusFinder,
      boolean isInitialFocus,
      Filter<AccessibilityWindowInfo> windowFilter,
      Filter<AccessibilityNodeInfoCompat> nodeFilter) {
    AccessibilityWindowInfo targetWindow = currentWindow;
    @TraversalStrategy.SearchDirection
    int logicalDirection = TraversalStrategyUtils.getLogicalDirection(direction, isScreenRtl);
    if (logicalDirection != TraversalStrategy.SEARCH_FOCUS_FORWARD
        && logicalDirection != TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return null;
    }

    while (true) {
      // Although we already check last window before searching, but sometimes we may find out the
      // window is empty so it searches next window repeatly, in this case we should check last
      // window again to prevent traversing in loops.
      if (!reachEdge
          && needPauseWhenTraverseAcrossWindow(
              windowTraversal, isScreenRtl, targetWindow, direction, windowFilter)) {
        LogUtils.v(TAG, "Reach edge while searchTargetInNextOrPreviousWindow in:" + targetWindow);
        reachEdge = true;
        return null;
      }
      // Search for a target window.
      targetWindow =
          (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD)
              ? windowTraversal.getNextWindow(targetWindow)
              : windowTraversal.getPreviousWindow(targetWindow);
      if ((targetWindow == null) || currentWindow.equals(targetWindow)) {
        return null;
      }
      if (!windowFilter.accept(targetWindow)) {
        continue;
      }

      // Skip the suggestions window when the current focus is not the same as the anchor node
      // to prevent the suggestions window navigable repeatedly.
      AccessibilityNodeInfoCompat anchorNode = AccessibilityWindowInfoUtils.getAnchor(targetWindow);
      if (currentFocus != null
          && anchorNode != null
          && Role.getRole(anchorNode) == Role.ROLE_EDIT_TEXT
          && !currentFocus.equals(anchorNode)) {
        continue;
      }

      // Try to find the initial focus in the target window. Note this doesn't care about the
      // direction.
      if (isInitialFocus) {
        AccessibilityNodeInfoCompat initialFocus =
            FocusManagerInternal.findInitialFocusFromTargetWindowRoot(
                service,
                AccessibilityNodeInfoUtils.toCompat(targetWindow.getRoot()),
                currentScreenState,
                actorState.getFocusHistory(),
                focusFinder,
                new AtomicInteger(FocusActionInfo.UNDEFINED));
        if ((initialFocus != null) && initialFocus.refresh()) {
          return initialFocus;
        }
      }

      // Search for the first node in the direction in the target window.
      AccessibilityNodeInfoCompat rootCompat =
          AccessibilityNodeInfoUtils.toCompat(targetWindow.getRoot());
      if (rootCompat != null) {
        TraversalStrategy traversalStrategy =
            TraversalStrategyUtils.getTraversalStrategy(rootCompat, focusFinder, direction);

        AccessibilityNodeInfoCompat focus =
            TraversalStrategyUtils.findFirstFocusInNodeTree(
                traversalStrategy, rootCompat, direction, nodeFilter);
        if (focus != null) {
          return focus;
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Logic related to accessibility focus action.

  @CanIgnoreReturnValue
  private boolean ensureOnScreen(
      @NonNull AccessibilityNodeInfoCompat node,
      @SearchDirection int searchDirection,
      EventId eventId) {
    boolean isRtl = WindowUtils.isScreenLayoutRTL(service);
    ScrollableNodeInfo scrollableNodeInfo =
        ScrollableNodeInfo.findScrollableNodeForDirection(
            searchDirection, /* pivot= */ node, /* includeSelf= */ false, isRtl);

    if (scrollableNodeInfo == null) {
      return false;
    }
    AccessibilityNodeInfoCompat scrollableNode = scrollableNodeInfo.getNode();

    boolean needToEnsureOnScreen =
        TraversalStrategyUtils.isAutoScrollEdgeListItem(
                node,
                scrollableNodeInfo,
                /* ignoreDescendantsOfPivot= */ false,
                searchDirection,
                focusFinder)
            || isPositionAtEdge(service, node, scrollableNode, searchDirection);

    if (!needToEnsureOnScreen) {
      LogUtils.d(TAG, "check ensureOnScreen again for outer scrollable boundary");
      ScrollableNodeInfo outerScrollableNodeInfo =
          ScrollableNodeInfo.findScrollableNodeForDirection(
              searchDirection, /* pivot= */ scrollableNode, /* includeSelf= */ false, isRtl);

      if (outerScrollableNodeInfo == null) {
        return false;
      }
      needToEnsureOnScreen =
          isPositionAtEdge(service, node, outerScrollableNodeInfo.getNode(), searchDirection);
    }
    boolean scrolled = false;
    if (needToEnsureOnScreen) {
      // ScrollableNode may not be the one actually scrolled to move node onto screen. Refer to
      // requestRectangleOnScreen() in View.java fore more details.
      scrolled = ensureOnScreenInternal(scrollableNode, node, eventId);
    }

    return scrolled;
  }

  private static boolean isPositionAtEdge(
      Context context,
      @Nullable AccessibilityNodeInfoCompat node,
      @Nullable AccessibilityNodeInfoCompat scrollableNode,
      @SearchDirection int searchDirection) {
    if (node == null || scrollableNode == null) {
      return false;
    }

    // Checks the scroll orientation, it is by default vertical.
    boolean isHorizontal = false;
    if (searchDirection == SEARCH_FOCUS_LEFT || searchDirection == SEARCH_FOCUS_RIGHT) {
      isHorizontal = true;
    } else if (searchDirection == SEARCH_FOCUS_UP || searchDirection == SEARCH_FOCUS_DOWN) {
      isHorizontal = false;
    } else {
      CollectionInfoCompat collectionInfo = scrollableNode.getCollectionInfo();
      if (collectionInfo == null
          || collectionInfo.getRowCount() <= 0
          || collectionInfo.getColumnCount() <= 0) {
        // Cannot get scroll direction by the collectionInfo, use node position to check.
        for (int i = 0; i < scrollableNode.getChildCount() - 1; i++) {
          // Compare child i and i+1 to check the scroll direction.
          Rect childBounds =
              AccessibilityNodeInfoUtils.getNodeBoundsInScreen(scrollableNode.getChild(i));
          Rect nextChildBounds =
              AccessibilityNodeInfoUtils.getNodeBoundsInScreen(scrollableNode.getChild(i + 1));

          if (!childBounds.isEmpty() && !nextChildBounds.isEmpty()) {
            if (childBounds.centerX() == nextChildBounds.centerX()) {
              isHorizontal = false;
              break;
            } else if (childBounds.centerY() == nextChildBounds.centerY()) {
              isHorizontal = true;
              break;
            }
          }
        }
      } else {
        isHorizontal =
            CollectionState.getCollectionAlignmentInternal(collectionInfo)
                == CollectionState.ALIGNMENT_HORIZONTAL;
      }
    }

    Rect scrollableNodeBounds = AccessibilityNodeInfoUtils.getNodeBoundsInScreen(scrollableNode);
    Rect nodeBounds = AccessibilityNodeInfoUtils.getNodeBoundsInScreen(node);
    boolean isRtl = WindowUtils.isScreenLayoutRTL(context);

    if (TraversalStrategyUtils.isSpatialDirection(searchDirection)) {
      searchDirection = TraversalStrategyUtils.getLogicalDirection(searchDirection, isRtl);
    }

    // Returns true if the node's bounds is beyond the scrollable's bounds in the search direction.
    switch (searchDirection) {
      case TraversalStrategy.SEARCH_FOCUS_FORWARD -> {
        if (isHorizontal) {
          if (isRtl) {
            if (scrollableNodeBounds.left >= nodeBounds.left) {
              return true;
            }
          } else {
            if (scrollableNodeBounds.right <= nodeBounds.right) {
              return true;
            }
          }
        } else if (scrollableNodeBounds.bottom <= nodeBounds.bottom) {
          return true;
        }
      }
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD -> {
        if (isHorizontal) {
          if (isRtl) {
            if (scrollableNodeBounds.right <= nodeBounds.right) {
              return true;
            }
          } else {
            if (scrollableNodeBounds.left >= nodeBounds.left) {
              return true;
            }
          }
        } else if (scrollableNodeBounds.top >= nodeBounds.top) {
          return true;
        }
      }
      default -> {
        // Do nothing.
      }
    }

    return false;
  }

  private boolean ensureOnScreenInternal(
      AccessibilityNodeInfoCompat scrollableNode,
      AccessibilityNodeInfoCompat nodeToFocus,
      EventId eventId) {
    return pipeline.returnFeedback(
        eventId, Feedback.scrollEnsureOnScreen(scrollableNode, nodeToFocus));
  }

  private boolean setAccessibilityFocusInternal(
      AccessibilityNodeInfoCompat target, NavigationAction navigationAction, EventId eventId) {
    return setAccessibilityFocusInternal(target, navigationAction, /* linkIndex= */ -1, eventId);
  }

  private boolean setAccessibilityFocusInternal(
      AccessibilityNodeInfoCompat target,
      NavigationAction navigationAction,
      int linkIndex,
      EventId eventId) {
    // Clear the "reachEdge" flag.
    reachEdge = false;
    resetLastScrolledNodeForNativeMacroGranularity();

    // Once we find the next focused node, the auto scrolling is completed and we should reset the
    // scroll record.
    resetScrollRecord(eventId);

    return pipeline.returnFeedback(
        eventId,
        Feedback.focus(
                target,
                FocusActionInfo.builder()
                    .setSourceAction(FocusActionInfo.LOGICAL_NAVIGATION)
                    .setNavigationAction(navigationAction)
                    .setFocusedLinkIndex(linkIndex)
                    // Mute the a11y focused feedback if we are focusing a link.
                    .setForceMuteFeedback(linkIndex >= 0)
                    .build())
            .setForceRefocus(true));
  }

  /**
   * Speak-ahead: focus (and therefore speak) the already-computed scroll target right
   * away, while the requested scroll is still in flight. Deliberately unlike {@link
   * #setAccessibilityFocusInternal}, this does NOT reset the scroll record and does NOT force
   * refocus: the pending scroll bookkeeping stays alive so the normal post-scroll pass still
   * re-validates focus, and if focus is already on the target nothing is repeated. If the
   * framework refuses focus on the off-screen node, the normal deferred path proceeds unchanged.
   */
  private void speakAheadOfScroll(
      AccessibilityNodeInfoCompat target, NavigationAction navigationAction, EventId eventId) {
    reachEdge = false;
    LogUtils.d(TAG, "Speak-ahead: focusing scroll target before scroll settles.");
    pipeline.returnFeedback(
        eventId,
        Feedback.focus(
            target,
            FocusActionInfo.builder()
                .setSourceAction(FocusActionInfo.LOGICAL_NAVIGATION)
                .setNavigationAction(navigationAction)
                .setFocusedLinkIndex(/* linkIndex= */ -1)
                .setForceMuteFeedback(false)
                .build()));
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Logic related to auto-scroll.
  /**
   * Returns scrollable node filter for given {@link NavigationAction}.
   *
   * <p>This is consistent with what we used in {@link
   * TraversalStrategyUtils#isAutoScrollEdgeListItem(AccessibilityNodeInfoCompat,
   * AccessibilityNodeInfoCompat, boolean, int, TraversalStrategy)}. It consists of {@link
   * NodeActionFilter} to check supported scroll action, and {@link
   * AccessibilityNodeInfoUtils#FILTER_AUTO_SCROLL} to match white-listed {@link Role}.
   */
  private static @Nullable Filter<AccessibilityNodeInfoCompat> getScrollFilter(
      NavigationAction navigationAction) {
    final int scrollAction =
        switch (navigationAction.actionType) {
          case NavigationAction.SCROLL_UP,
              NavigationAction.SCROLL_LEFT,
              NavigationAction.SCROLL_FORWARD ->
              AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
          case NavigationAction.SCROLL_DOWN,
              NavigationAction.SCROLL_RIGHT,
              NavigationAction.SCROLL_BACKWARD ->
              AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
          case NavigationAction.DIRECTIONAL_NAVIGATION ->
              TraversalStrategyUtils.convertSearchDirectionToScrollAction(
                  navigationAction.searchDirection);
          default -> 0;
        };

    if (scrollAction == 0) {
      return null;
    }

    if ((navigationAction.actionType == NavigationAction.SCROLL_FORWARD)
        || (navigationAction.actionType == NavigationAction.SCROLL_BACKWARD)
        || (navigationAction.actionType == NavigationAction.SCROLL_UP)
        || (navigationAction.actionType == NavigationAction.SCROLL_DOWN)
        || (navigationAction.actionType == NavigationAction.SCROLL_LEFT)
        || (navigationAction.actionType == NavigationAction.SCROLL_RIGHT)) {
      return new NodeActionFilter(scrollAction).and(SCROLLABLE_ROLE_FILTER_FOR_SCROLL_GESTURE);
    } else {
      return new NodeActionFilter(scrollAction)
          .and(SCROLLABLE_ROLE_FILTER_FOR_DIRECTION_NAVIGATION);
    }
  }

  /**
   * Returns filter that supports page actions or scroll action for given {@link NavigationAction}.
   */
  private static @Nullable Filter<AccessibilityNodeInfoCompat> getScrollOrPageActionFilter(
      NavigationAction navigationAction) {
    int pageAction = 0;
    int scrollAction = 0;
    switch (navigationAction.actionType) {
      case NavigationAction.SCROLL_UP -> {
        pageAction = ACTION_PAGE_DOWN.getId();
        scrollAction = ACTION_SCROLL_FORWARD.getId();
      }
      case NavigationAction.SCROLL_DOWN -> {
        pageAction = ACTION_PAGE_UP.getId();
        scrollAction = ACTION_SCROLL_BACKWARD.getId();
      }
      case NavigationAction.SCROLL_LEFT -> {
        pageAction = ACTION_PAGE_RIGHT.getId();
        scrollAction = ACTION_SCROLL_FORWARD.getId();
      }
      case NavigationAction.SCROLL_RIGHT -> {
        pageAction = ACTION_PAGE_LEFT.getId();
        scrollAction = ACTION_SCROLL_BACKWARD.getId();
      }
      default -> {
        pageAction = 0;
        scrollAction = 0;
      }
    }

    if (pageAction == 0 || scrollAction == 0) {
      return null;
    }
    return new NodeActionFilter(pageAction).or(new NodeActionFilter(scrollAction));
  }

  /**
   * Tries to perform scroll if the pivot is at the edge of a scrollable container and suitable
   * autoscroll.
   */
  private boolean autoScrollAtEdge(
      @NonNull AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      EventId eventId) {
    if (!navigationAction.shouldScroll) {
      LogUtils.v(TAG, "autoScrollAtEdge returns due to shouldScroll is false");
      return false;
    }
    if (WebInterfaceUtils.supportsWebActions(pivot)) {
      // Do not autoScrollAtEdge on web node because autoScrollAtEdge relies on TraversalStrategy
      // but web nodes aren't in the traversal tree.
      LogUtils.v(TAG, "autoScrollAtEdge returns due to pivot is web node");
      return false;
    }

    // Allow the pivot node itself being the scrollable container. This may happen when the
    // scrollable container is at the edge of the screen and contains no focusable item before
    // scrolling.
    ScrollableNodeInfo scrollableNodeInfo =
        ScrollableNodeInfo.findScrollableNodeForDirection(
            navigationAction.searchDirection,
            pivot,
            /* includeSelf= */ true,
            WindowUtils.isScreenLayoutRTL(service));
    if (scrollableNodeInfo == null) {
      return false;
    }
    AccessibilityNodeInfoCompat scrollableNode = scrollableNodeInfo.getNode();

    // Don't try to scroll the pivot when ignoring all descendants from the pivot.
    if (scrollableNode.equals(pivot) && ignoreDescendantsOfPivot) {
      return false;
    }

    // No need to scroll if the pivot is not at the edge of the scrollable container.
    if (!TraversalStrategyUtils.isAutoScrollEdgeListItem(
        pivot,
        scrollableNodeInfo,
        ignoreDescendantsOfPivot,
        navigationAction.searchDirection,
        focusFinder)) {
      return false;
    }

    return autoScroll(scrollableNodeInfo, pivot, navigationAction, eventId);
  }

  /**
   * Checks if the focused item is the last in scroll direction. Returns {@code false} if not sure,
   * namely if there is no {@link CollectionInfoCompat} associated with the container, no {@link
   * CollectionItemInfoCompat} associated with the focused node, or the search direction is logical
   * and the collection has two axes.
   */
  private boolean isCollectionItemLastInDirection(
      @NonNull AccessibilityNodeInfoCompat pivot,
      @NonNull AccessibilityNodeInfoCompat scrollable,
      @SearchDirectionOrUnknown int searchDirection) {

    if (pivot.getCollectionItemInfo() == null || scrollable.getCollectionInfo() == null) {
      return false;
    }

    if (!scrollable.equals(AccessibilityNodeInfoUtils.getCollectionRoot(pivot))) {
      // The collection info relates to a different collection than scrollable.
      // This may happen in the case of nested scrollables.
      return false;
    }

    CollectionItemInfoCompat item = pivot.getCollectionItemInfo();
    CollectionInfoCompat container = scrollable.getCollectionInfo();

    return switch (searchDirection) {
      case SEARCH_FOCUS_UP -> item.getRowIndex() == 0;
      case SEARCH_FOCUS_DOWN -> item.getRowIndex() + item.getRowSpan() == container.getRowCount();
      case SEARCH_FOCUS_LEFT -> item.getColumnIndex() == 0;
      case SEARCH_FOCUS_RIGHT ->
          item.getColumnIndex() + item.getColumnSpan() == container.getColumnCount();
      case SEARCH_FOCUS_BACKWARD ->
          (container.getColumnCount() == 1 && item.getRowIndex() == 0)
              || (container.getRowCount() == 1 && item.getColumnIndex() == 0);
      case SEARCH_FOCUS_FORWARD ->
          (container.getColumnCount() == 1
                  && item.getRowIndex() + item.getRowSpan() == container.getRowCount())
              || (container.getRowCount() == 1
                  && item.getColumnIndex() + item.getColumnSpan() == container.getColumnCount());
      default -> false;
    };
  }

  /** Attempts to scroll based on the specified {@link NavigationAction}. */
  private boolean autoScroll(
      @NonNull ScrollableNodeInfo scrollableNodeInfo,
      @NonNull AccessibilityNodeInfoCompat pivot,
      NavigationAction navigationAction,
      EventId eventId) {

    // Adjust navigationAction for potential fallback direction.
    Integer supportedDirection =
        scrollableNodeInfo.getSupportedScrollDirection(navigationAction.searchDirection);
    if (supportedDirection == null) {
      return false;
    }
    NavigationAction supportedNavigationAction =
        NavigationAction.Builder.copy(navigationAction).setDirection(supportedDirection).build();

    int scrollAction =
        TraversalStrategyUtils.convertSearchDirectionToScrollAction(
            supportedNavigationAction.searchDirection);

    AutoScrollSuccessChecker autoScrollChecker =
        createAutoScrollCheckerIfNeeded(
            scrollableNodeInfo.getNode(), navigationAction, scrollAction);
    // Use SCROLL_TIMEOUT_LONG_MS since auto scroll may find some scrollable containers that request
    // a longer time to finish the scrolling action(like home screen), a short timeout will make
    // TalkBack detects the scroll action always fail, even through it's actually success.
    return performScrollActionInternal(
        ScrollActionRecord.ACTION_AUTO_SCROLL,
        scrollableNodeInfo.getNode(),
        pivot,
        scrollAction,
        navigationAction,
        ScrollTimeout.SCROLL_TIMEOUT_LONG,
        autoScrollChecker,
        eventId);
  }

  /**
   * Delivers an {@link AutoScrollSuccessChecker} with given navigation action and scroll action if
   * necessary.
   *
   * <p>To simplify the logic, we do it for phone only. For non-default granularity, it might need
   * multiple auto-scrolls to find the target, so we don't create the checker for it.
   */
  @VisibleForTesting
  @Nullable AutoScrollSuccessChecker createAutoScrollCheckerIfNeeded(
      AccessibilityNodeInfoCompat scrollableNode,
      NavigationAction navigationAction,
      int scrollAction) {
    if (FormFactorUtils.isAndroidWear() || FormFactorUtils.isAndroidTv()) {
      return null;
    }

    if (navigationAction.targetType != NavigationTarget.TARGET_DEFAULT) {
      return null;
    }
    if (getSuccessAutoScrollPercentageThreshold(service) == 0f || !BuildVersionUtils.isAtLeastP()) {
      return null;
    }

    if (scrollAction != AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
        && scrollAction != AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
      return null;
    }
    final Rect nodeBounds = new Rect();
    scrollableNode.getBoundsInScreen(nodeBounds);
    return new AutoScrollSuccessCheckerImpl(
        scrollAction, nodeBounds, getSuccessAutoScrollPercentageThreshold(service));
  }

  private boolean performScrollActionInternal(
      @UserAction int userAction,
      @NonNull AccessibilityNodeInfoCompat scrollableNode,
      @NonNull AccessibilityNodeInfoCompat pivotNode,
      int scrollAction,
      NavigationAction sourceAction,
      EventId eventId) {
    return performScrollActionInternal(
        userAction,
        scrollableNode,
        pivotNode,
        scrollAction,
        sourceAction,
        ScrollTimeout.SCROLL_TIMEOUT_SHORT,
        /* autoScrollSuccessChecker= */ null,
        eventId);
  }

  private boolean performScrollActionInternal(
      @UserAction int userAction,
      @NonNull AccessibilityNodeInfoCompat scrollableNode,
      @NonNull AccessibilityNodeInfoCompat pivotNode,
      int scrollAction,
      NavigationAction sourceAction,
      ScrollTimeout scrollTimeout,
      @Nullable AutoScrollSuccessChecker autoScrollSuccessChecker,
      EventId eventId) {
    if ((sourceAction.actionType == NavigationAction.SCROLL_BACKWARD
            || sourceAction.actionType == NavigationAction.SCROLL_FORWARD)
        && !AccessibilityNodeInfoUtils.hasAncestor(pivotNode, scrollableNode)) {
      // Don't update a11y focus in callback if pivot is not a descendant of scrollable node.
      scrollCallback = null;
    } else {
      scrollCallback = new AutoScrollCallback(this, sourceAction, pivotNode);
    }
    return pipeline.returnFeedback(
        eventId,
        Feedback.scroll(
            scrollableNode,
            userAction,
            scrollAction,
            ScrollActionRecord.FOCUS,
            scrollTimeout,
            sourceAction.autoScrollAttempt,
            autoScrollSuccessChecker));
  }

  /** Determines feedback for auto-scroll success after directional-navigation action. */
  public void onAutoScrolled(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      EventId eventId,
      int scrollDeltaX,
      int scrollDeltaY) {
    if (scrollCallback != null) {
      AccessibilityNodeInfoCompat currentFocus =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      final AutoScrollCallback autoScrollCallback = scrollCallback;
      scrollCallback = null;
      if (currentFocus == null || currentFocus.equals(autoScrollCallback.pivot)) {
        // Prevent changing the focus if it has already been changed, e.g., by another actor.
        // In the onAutoScrolled method, we will assign a new scrollCallback.
        autoScrollCallback.onAutoScrolled(scrolledNode, eventId, scrollDeltaX, scrollDeltaY);
      } else {
        LogUtils.w(TAG, "Skip onAutoScrolled because currentFocus is assigned by another actor.");
      }
    }
  }

  /** Determines feedback for auto-scroll failure after directional-navigation action. */
  public void onAutoScrollFailed(@NonNull AccessibilityNodeInfoCompat scrolledNode) {
    if (scrollCallback != null) {
      scrollCallback.onAutoScrollFailed(scrolledNode);
      scrollCallback = null;
    }
  }

  private void handleViewScrolledForScrollNavigationAction(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      NavigationAction sourceAction,
      EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    boolean hasValidA11yFocus = AccessibilityNodeInfoUtils.isVisible(currentFocus);
    if (hasValidA11yFocus && !AccessibilityNodeInfoUtils.hasAncestor(currentFocus, scrolledNode)) {
      // Do nothing if there is a valid focus outside of the scrolled container.
      return;
    }
    // 1. Visible, inside scrolledNode
    // 2. Invisible, no focus.
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            scrolledNode, focusFinder, TraversalStrategy.SEARCH_FOCUS_FORWARD);
    // Use TARGET_DEFAULT regardless of the current granularity.
    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            NavigationTarget.TARGET_DEFAULT, traversalStrategy.getSpeakingNodesCache());
    @SearchDirection
    int direction =
        (sourceAction.actionType == NavigationAction.SCROLL_FORWARD)
            ? TraversalStrategy.SEARCH_FOCUS_FORWARD
            : TraversalStrategy.SEARCH_FOCUS_BACKWARD;

    AccessibilityNodeInfoCompat nodeToFocus;
    if (hasValidA11yFocus) {
      nodeToFocus =
          TraversalStrategyUtils.searchFocus(
              traversalStrategy, currentFocus, direction, nodeFilter);
    } else {
      nodeToFocus =
          TraversalStrategyUtils.findFirstFocusInNodeTree(
              traversalStrategy, scrolledNode, direction, nodeFilter);
    }
    if (nodeToFocus != null) {
      setAccessibilityFocusInternal(nodeToFocus, sourceAction, eventId);
    }
  }

  /**
   * Called when we receive result event for auto-scroll action with macro granularity target.
   *
   * <p><b>Warning:</b> Do not rely too much logic on {@code focusBeforeScroll}. It is possible in
   * RecyclerView when {@code focusBeforeScroll} goes off screen, and after calling {@link
   * AccessibilityNodeInfoCompat#refresh()}, the node is reused and pointed to another emerging list
   * item. This is how RecyclerView "recycles" views and we cannot get rid of it. If using this
   * field, please refresh and validate the node to ensure it is identical with what it was before
   * scroll action.
   */
  private void handleViewAutoScrolledForDirectionalNavigationWithMacroGranularityTarget(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      NavigationAction sourceAction,
      EventId eventId) {

    NavigationAction navigationAction =
        NavigationAction.Builder.copy(sourceAction)
            .setAutoScrollAttempt(sourceAction.autoScrollAttempt + 1)
            .build();

    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            navigationAction.searchDirection, WindowUtils.isScreenLayoutRTL(service));

    // Try to find the next focusable node based on current focus.
    // For native macro granularity, we try to find the reference node to start the search from.
    // This workaround is required due to REFERTO, else we can use focusBeforeScroll
    // as the start node even for macro granularity.
    // TODO: Remove this workaround after REFERTO is fixed.
    AccessibilityNodeInfoCompat refNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (!AccessibilityNodeInfoUtils.isVisible(refNode)) {
      refNode = null;
    }
    // Local TraversalStrategy generated in sub-tree of a refreshed scrolledNode.
    TraversalStrategy localTraversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            scrolledNode, focusFinder, sourceAction.searchDirection);
    if (refNode == null) {
      // Start from the first focusable node of the Scrollable by the traversal strategy direction.
      refNode =
          TraversalStrategyUtils.findFirstFocusInNodeTree(
              localTraversalStrategy,
              scrolledNode,
              sourceAction.searchDirection,
              FILTER_SHOULD_FOCUS.and(
                  new Filter<AccessibilityNodeInfoCompat>() {
                    @Override
                    public boolean accept(AccessibilityNodeInfoCompat node) {
                      return !scrolledNode.equals(node);
                    }
                  }));
    }
    if (refNode == null) {
      LogUtils.w(
          TAG,
          "handleViewAutoScrolledForDirectionalNavigationWithMacroGranularityTarget returns due to"
              + " refNode is null");
      return;
    }
    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            sourceAction.targetType, localTraversalStrategy.getSpeakingNodesCache());

    // Only if the refNode does not satisfy the desired macro granularity target type or is
    // default granularity, we look for the next target starting from
    // refNode. Else, just set the focus to refNode.
    AccessibilityNodeInfoCompat nodeToFocus;
    if (nodeFilter.accept(refNode) && !refNode.isAccessibilityFocused()) {
      nodeToFocus = refNode;
    } else if (sourceAction.targetType == TARGET_CONTAINER) {
      nodeToFocus = findContainerTarget(refNode, sourceAction);

      if (currentContainer(nodeToFocus) == null) {
        announceNativeElement(logicalDirection, navigationAction.targetType, eventId);
      }
    } else {
      nodeToFocus =
          TraversalStrategyUtils.searchFocus(
              localTraversalStrategy, refNode, sourceAction.searchDirection, nodeFilter);
      setLastScrolledNodeForNativeMacroGranularity(scrolledNode);
      if (nodeToFocus == null) {
        boolean scrollSuccess = false;
        if (shouldKeepSearch(navigationAction)) {
          scrollSuccess =
              performScrollActionInternal(
                  ScrollActionRecord.ACTION_AUTO_SCROLL,
                  scrolledNode,
                  refNode,
                  TraversalStrategyUtils.convertSearchDirectionToScrollAction(
                      navigationAction.searchDirection),
                  navigationAction,
                  ScrollTimeout.SCROLL_TIMEOUT_LONG,
                  /* autoScrollSuccessChecker= */ null,
                  eventId);
        }

        if (scrollSuccess) {
          // Early return if next scroll action performed.
          return;
        }

        // Fallback to focus on the target outside of scrollable node.
        if (navigationAction.fallbackTarget != null) {
          navigationAction.fallbackTarget.refresh();
          if (FILTER_SHOULD_FOCUS.accept(navigationAction.fallbackTarget)) {
            setAccessibilityFocusInternal(
                navigationAction.fallbackTarget, navigationAction, eventId);
            return;
          }
        }

        nodeToFocus = findInitialFocusAfterScrolled(refNode, sourceAction, localTraversalStrategy);

        announceNativeElement(logicalDirection, navigationAction.targetType, eventId);
      }
    }

    if (nodeToFocus != null) {
      setAccessibilityFocusInternal(nodeToFocus, navigationAction, eventId);
    }
  }

  /** Called when we receive result event for auto-scroll action with screen search target. */
  private void handleViewAutoScrolledForDirectionalNavigationWithScreenSearchTarget(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      NavigationAction sourceAction,
      EventId eventId) {
    AccessibilityNodeInfoCompat refNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (!AccessibilityNodeInfoUtils.isVisible(refNode)) {
      refNode = null;
    }
    if (refNode == null) {
      // Local TraversalStrategy generated in sub-tree of a refreshed scrolledNode.
      TraversalStrategy localTraversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(
              scrolledNode, focusFinder, sourceAction.searchDirection);
      // Start from the first focusable node of the Scrollable by the traversal strategy direction.
      refNode =
          TraversalStrategyUtils.findFirstFocusInNodeTree(
              localTraversalStrategy,
              scrolledNode,
              sourceAction.searchDirection,
              FILTER_SHOULD_FOCUS.and(
                  new Filter<AccessibilityNodeInfoCompat>() {
                    @Override
                    public boolean accept(AccessibilityNodeInfoCompat node) {
                      return !scrolledNode.equals(node);
                    }
                  }));
    }
    AccessibilityNodeInfoCompat nodeToFocus =
        findSearchTarget(refNode, sourceAction, searchState.getLastKeyword());
    if (nodeToFocus == null) {
      announceNoSearchResult(sourceAction.searchDirection, eventId);
    } else {
      setAccessibilityFocusInternal(nodeToFocus, sourceAction, eventId);
    }
  }

  /**
   * Find initial focus if target not found after auto-scroll performed.
   *
   * <p>Note: If no target is found and the scrolled screen doesn't have the accessibility focus(
   * {@code refNode} is not from {@link AccessibilityFocusMonitor#getAccessibilityFocus}), then
   * search for the new initial focus in the scrollable container.
   */
  private @Nullable AccessibilityNodeInfoCompat findInitialFocusAfterScrolled(
      AccessibilityNodeInfoCompat refNode,
      NavigationAction navigationAction,
      TraversalStrategy localTraversalStrategy) {
    if (refNode != null && !refNode.isAccessibilityFocused()) {
      return FILTER_SHOULD_FOCUS.accept(refNode)
          ? refNode
          : TraversalStrategyUtils.searchFocus(
              localTraversalStrategy,
              refNode,
              navigationAction.searchDirection,
              FILTER_SHOULD_FOCUS);
    }
    return null;
  }

  // TODO: Provides an overall experience of focusing on small nodes on both watch and
  //  phone devices.
  // It’s okay that the restrictions are not strict since a user can perform a gesture to stop
  // keeping search. We will add a test to cover this scenario.
  // TODO: Create a test to cover the case of stopping multiple auto scroll.
  public static final int MAX_MULTIPLE_AUTO_SCROLL_ATTEMPT = 100;
  public static final int MAX_MULTIPLE_SCROLL_SCREEN_MULTIPLIER = 100;

  private boolean shouldKeepSearch(NavigationAction navigationAction) {
    if (!TalkbackFeatureSupport.supportMultipleAutoScroll()) {
      return false;
    }

    final Point screenPxSize = DisplayUtils.getScreenPixelSizeWithoutWindowDecor(service);
    return navigationAction.autoScrollAttempt <= MAX_MULTIPLE_AUTO_SCROLL_ATTEMPT
        && (BuildVersionUtils.isAtLeastP() // Only build version at least P supports scroll delta.
            && Math.abs(navigationAction.prevScrollDeltaSumX)
                < MAX_MULTIPLE_SCROLL_SCREEN_MULTIPLIER * screenPxSize.x
            && Math.abs(navigationAction.prevScrollDeltaSumY)
                < MAX_MULTIPLE_SCROLL_SCREEN_MULTIPLIER * screenPxSize.y);
  }

  /**
   * Called when we receive result event for auto-scroll action with default target.
   *
   * <p><b>Warning:</b> Do not rely too much logic on {@code focusBeforeScroll}. It is possible in
   * RecyclerView when {@code focusBeforeScroll} goes off screen, and after calling {@link
   * AccessibilityNodeInfoCompat#refresh()}, the node is reused and pointed to another emerging list
   * item. This is how RecyclerView "recycles" views and we cannot get rid of it. If using this
   * field, please refresh and validate the node to ensure it is identical with what it was before
   * scroll action.
   */
  private void handleViewAutoScrolledForDirectionalNavigationWithDefaultTarget(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      @NonNull AccessibilityNodeInfoCompat focusBeforeScroll,
      NavigationAction sourceAction,
      EventId eventId) {
    // Prior to focus on position for scroll in grid.
    if (sourceAction.positionForScroll != null) {
      AccessibilityNodeInfoCompat grid =
          AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
              scrolledNode, FILTER_SCROLLABLE_GRID);
      LogUtils.v(TAG, "Focus on position for scroll in grid " + sourceAction.positionForScroll);
      if (grid != null) {
        AccessibilityNodeInfoCompat nodeToFocus =
            TableNavigation.getCellFromTargetIndex(grid, sourceAction.positionForScroll);
        if (nodeToFocus != null) {
          setAccessibilityFocusInternal(nodeToFocus, sourceAction, eventId);
          return;
        }
        LogUtils.v(TAG, "Failed to focus on position for scroll");
      }
    }

    // Local TraversalStrategy generated in sub-tree of scrolledNode.
    TraversalStrategy localTraversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            scrolledNode, focusFinder, sourceAction.searchDirection);
    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            sourceAction.targetType, localTraversalStrategy.getSpeakingNodesCache());
    Rect previousRect = new Rect();
    focusBeforeScroll.getBoundsInScreen(previousRect);
    boolean validAccessibilityFocus =
        focusBeforeScroll.refresh() && AccessibilityNodeInfoUtils.isVisible(focusBeforeScroll);

    NavigationAction navigationAction =
        NavigationAction.Builder.copy(sourceAction)
            .setAutoScrollAttempt(sourceAction.autoScrollAttempt + 1)
            .build();

    AccessibilityNodeInfoCompat nodeToFocus;
    if (validAccessibilityFocus) {
      // Try to find the next focusable node based on current focus.
      nodeToFocus =
          TraversalStrategyUtils.searchFocus(
              localTraversalStrategy, focusBeforeScroll, sourceAction.searchDirection, nodeFilter);
      if (nodeToFocus == null) {
        Rect newRect = getBoundsAfterScroll(focusBeforeScroll);
        if (previousRect.equals(newRect) && sourceAction.shouldScroll) {
          if (sourceAction.isPrevScrolled) {
            LogUtils.d(TAG, "Pivot didn't move but has scrolls. Repeat until to find the next.");
          } else {
            LogUtils.d(TAG, "Pivot didn't move and no scrolls, do not repeat.");
            navigationAction =
                NavigationAction.Builder.copy(sourceAction)
                    .setAutoScrollAttempt(sourceAction.autoScrollAttempt + 1)
                    .setShouldScroll(false)
                    .build();
          }
        }
        // Repeat navigation action in hope that eventually a new item will be exposed.
        onDirectionalNavigationAction(
            /* pivot= */ focusBeforeScroll,
            /* ignoreDescendantsOfPivot= */ false,
            navigationAction,
            eventId);
        return;
      }
    } else {
      // Try to find the next focusable node based on current focus.
      nodeToFocus =
          TraversalStrategyUtils.searchFocus(
              localTraversalStrategy, focusBeforeScroll, sourceAction.searchDirection, nodeFilter);
      // Fallback solution: Use the first/last item under scrollable node as the target.
      if (nodeToFocus == null) {
        nodeToFocus =
            TraversalStrategyUtils.findFirstFocusInNodeTree(
                localTraversalStrategy, scrolledNode, sourceAction.searchDirection, nodeFilter);
      }

      if (nodeToFocus == null) {
        // Since there is no visible/valid accessibility focus on screen, we play safe and don't
        // repeat navigation action without a valid pivot node.
        return;
      }
    }

    // If we're moving backward with default target from native views to WebView container node,
    // automatically descend to the last element in the WebView.
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            navigationAction.searchDirection, WindowUtils.isScreenLayoutRTL(service));
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      // We don't need to check role of the last focused node, because auto-scroll always
      // happens on native pivot.
      if (Role.getRole(nodeToFocus) == Role.ROLE_WEB_VIEW) {
        if (navigateToHtmlTarget(/* pivot= */ nodeToFocus, navigationAction, eventId)) {
          return;
        }
      }
    }

    if (shouldReEnsureSmallNodeOnScreen(focusBeforeScroll, nodeToFocus, navigationAction)) {
      ensureOnScreenInternal(scrolledNode, nodeToFocus, eventId);
    }

    if (SPEAK_AHEAD_OF_SCROLL && isAlreadyFocusedOn(nodeToFocus)) {
      // Speak-ahead already focused and spoke this exact node before the scroll settled;
      // re-focusing would force-refocus and repeat the utterance. Focus is correct, done.
      LogUtils.d(TAG, "Speak-ahead: focus already on post-scroll target, skipping re-focus.");
      return;
    }
    setAccessibilityFocusInternal(nodeToFocus, navigationAction, eventId);
  }

  /**
   * speak-ahead dedup: whether accessibility focus is currently on the given node, in
   * which case focusing it again would only repeat speech.
   */
  private boolean isAlreadyFocusedOn(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    AccessibilityNodeInfoCompat focused =
        focusFinder.findFocusCompat(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
    return node.equals(focused);
  }

  @VisibleForTesting
  @NonNull Rect getBoundsAfterScroll(@NonNull AccessibilityNodeInfoCompat node) {
    Rect newBounds = new Rect();
    node.getBoundsInScreen(newBounds);
    return newBounds;
  }

  private boolean shouldReEnsureSmallNodeOnScreen(
      AccessibilityNodeInfoCompat beforeNode,
      AccessibilityNodeInfoCompat nodeToFocus,
      NavigationAction action) {
    final Point screenPxSize = DisplayUtils.getScreenPixelSizeWithoutWindowDecor(service);
    return nodeToFocus != null
        && !beforeNode.equals(nodeToFocus)
        && action != null
        && action.autoScrollAttempt > 0
        && AccessibilityNodeInfoUtils.isSmallNodeInHeight(service, nodeToFocus)
        && AccessibilityNodeInfoUtils.isTopOrBottomBorderNode(screenPxSize, nodeToFocus);
  }

  private void setLastScrolledNodeForNativeMacroGranularity(
      AccessibilityNodeInfoCompat scrolledNode) {
    lastScrolledNodeForNativeMacroGranularity = scrolledNode;
  }

  public void resetLastScrolledNodeForNativeMacroGranularity() {
    lastScrolledNodeForNativeMacroGranularity = null;
  }

  private void resetScrollRecord(EventId eventId) {
    pipeline.returnFeedback(eventId, Feedback.scrollResetScrollActionRecords());
  }

  /**
   * Handles auto-scroll callback when the scroll action is successfully performed but no result
   * {@link android.view.accessibility.AccessibilityEvent#TYPE_VIEW_SCROLLED} event is received.
   *
   * <p>This is a very corner case when "scroll" related metric is not correctly configured for some
   * customized containers. In this case, we should jump out of the container and continue searching
   * for the next target.
   */
  private void handleViewAutoScrollFailedForDirectionalNavigationAction(
      @NonNull AccessibilityNodeInfoCompat nodeToScroll, NavigationAction sourceAction) {
    // When auto-scroll fails, we don't search down the scrolled container, instead, we jump out of
    // it searching for the next target. Thus we use 'nodeToScroll' as the pivot and
    // 'ignoreDescendantsOfPivot' is set to TRUE.
    onDirectionalNavigationAction(
        /* pivot= */ nodeToScroll,
        /* ignoreDescendantsOfPivot= */ true,
        sourceAction,
        /* eventId= */ null);
  }

  private TraversalStrategy createTraversal(
      @NonNull AccessibilityNodeInfoCompat node, @TraversalStrategy.SearchDirection int direction) {
    return TraversalStrategyUtils.getTraversalStrategy(node, focusFinder, direction);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to make announcement.
  // TODO: Think about moving this into Compositor.

  /** Announces if there are no more elements while using native granularity. */
  private void announceNativeElement(int direction, @TargetType int targetType, EventId eventId) {
    boolean forward = (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD);
    int resId = forward ? R.string.end_of_page : R.string.start_of_page;

    String target = NavigationTarget.nativeTargetToDisplayName(/* context= */ service, targetType);
    if (TextUtils.isEmpty(target)) {
      return;
    }

    String text = service.getString(resId, target);
    announce(text, eventId);
  }

  private void announceNoSearchResult(int direction, EventId eventId) {
    boolean forward = (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD);
    int resId = forward ? R.string.search_end_of_page : R.string.search_start_of_page;
    announce(service.getString(resId), eventId);
  }

  private void announce(CharSequence text, EventId eventId) {
    SpeechController.SpeakOptions speakOptions =
        SpeechController.SpeakOptions.create()
            .setQueueMode(SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
            .setFlags(FeedbackItem.FLAG_FORCE_FEEDBACK);
    pipeline.returnFeedback(eventId, Feedback.speech(text, speakOptions));
  }

  /**
   * Returns the biggest scroll view or pager view on the screen, it will traverse the node tree by
   * BFS. If the view hierarchy is complicated, for example, a pager view contains a long scroll
   * view, even though the pager is bigger than the scroll view, we may prefer to select the scroll
   * view since the content user interested should be inside it. So this method also supports to
   * give the pager view the lower priority. In that case the pager will be selected only if there
   * is no other scrollable view on the screen.
   *
   * @param node The root node to traverse from.
   * @param navigationAction The direction of the scrolling action.
   * @param pagerWithLowPriority Give the pager view a lower priority or not.
   * @return The scrollable node reached via BFS traversal.
   */
  public @Nullable AccessibilityNodeInfoCompat searchScrollableNodeFromBfs(
      @Nullable AccessibilityNodeInfoCompat node,
      NavigationAction navigationAction,
      boolean pagerWithLowPriority) {
    if (node == null) {
      return null;
    }

    final Filter<AccessibilityNodeInfoCompat> scrollableFilter =
        getScrollOrPageActionFilter(navigationAction);

    // Do the first BFS search without considering Pager role, no matter if we want to give pager a
    // lower priority or not. For the case pagerWithLowPriority==true, it only allows to get a pager
    // by the first search. The result will be used if we cannot find other scrollable nodes by
    // following steps, that also means the result we find here is the only scrollable node on the
    // screen.
    AccessibilityNodeInfoCompat result =
        AccessibilityNodeInfoUtils.searchFromBfs(node, scrollableFilter);
    if (result == null) {
      // No scrollable view on the screen.
      return null;
    }

    MaxSizeNodeAccumulator maxSizeNodeAccumulator;
    if (pagerWithLowPriority) {
      if (Role.getRole(result) == Role.ROLE_PAGER) {
        // Since we prefer to not select a pager, give null for the node area filter as a initial
        // value.
        maxSizeNodeAccumulator =
            new MaxSizeNodeAccumulator(
                null,
                scrollableFilter.and(Filter.node((item) -> Role.getRole(item) != Role.ROLE_PAGER)));
      } else {
        maxSizeNodeAccumulator =
            new MaxSizeNodeAccumulator(
                result,
                scrollableFilter.and(Filter.node((item) -> Role.getRole(item) != Role.ROLE_PAGER)));
      }
    } else {
      maxSizeNodeAccumulator = new MaxSizeNodeAccumulator(result, scrollableFilter);
    }

    AccessibilityNodeInfoUtils.searchFromBfs(
        node, Filter.node((item) -> false), maxSizeNodeAccumulator);
    if (maxSizeNodeAccumulator.maximumScrollableNode == null) {
      return result;
    }

    return maxSizeNodeAccumulator.maximumScrollableNode;
  }

  /**
   * Callback to be invoked after scroll action is performed in {@link
   * FocusProcessorForLogicalNavigation}. It caches some information to be used when handling the
   * result scroll event.
   */
  private static final class AutoScrollCallback {

    private final FocusProcessorForLogicalNavigation parent;
    private final NavigationAction sourceAction;
    private final AccessibilityNodeInfoCompat pivot;

    private boolean assumeScrollSuccess;

    AutoScrollCallback(
        FocusProcessorForLogicalNavigation parent,
        NavigationAction sourceAction,
        @NonNull AccessibilityNodeInfoCompat pivot) {
      this(parent, sourceAction, pivot, false);
    }

    AutoScrollCallback(
        FocusProcessorForLogicalNavigation parent,
        NavigationAction sourceAction,
        @NonNull AccessibilityNodeInfoCompat pivot,
        boolean assumeScrollSuccess) {
      this.parent = parent;
      this.sourceAction = sourceAction;
      this.pivot = pivot;
      this.assumeScrollSuccess = assumeScrollSuccess;
    }

    public void onAutoScrolled(
        @NonNull AccessibilityNodeInfoCompat scrolledNode,
        EventId eventId,
        int scrollDeltaX,
        int scrollDeltaY) {

      final NavigationAction navigationAction =
          provideNavigationActionByScrollDelta(scrollDeltaX, scrollDeltaY);

      LogUtils.d(
          TAG,
          "AutoScrollCallback onAutoScrolled, eventId="
              + eventId
              + ",navigationAction="
              + navigationAction);

      switch (sourceAction.actionType) {
        case NavigationAction.DIRECTIONAL_NAVIGATION -> {
          if (sourceAction.targetType == NavigationTarget.TARGET_DEFAULT) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithDefaultTarget(
                scrolledNode, pivot, navigationAction, eventId);
          } else if (NavigationTarget.isMacroGranularity(sourceAction.targetType)) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithMacroGranularityTarget(
                scrolledNode, navigationAction, eventId);
          } else if (sourceAction.targetType == TARGET_SEARCH) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithScreenSearchTarget(
                scrolledNode, navigationAction, eventId);
          } else if (TableNavigation.isNativeTableGranularity(sourceAction.targetType)) {
            TableNavigation tableNavigation =
                new TableNavigation(navigationAction, scrolledNode, parent.focusFinder, eventId);
            parent.handleViewScrolledForTableNavigationAction(tableNavigation, pivot);
          }
        }
        case NavigationAction.SCROLL_FORWARD, NavigationAction.SCROLL_BACKWARD ->
            parent.handleViewScrolledForScrollNavigationAction(
                scrolledNode, navigationAction, eventId);
        default -> {}
      }
      clear();
    }

    private NavigationAction provideNavigationActionByScrollDelta(
        int scrollDeltaX, int scrollDeltaY) {
      final NavigationAction.Builder builder = NavigationAction.Builder.copy(sourceAction);
      builder.setIsPrevScrolled(false);
      if (scrollDeltaX != DELTA_UNDEFINED && scrollDeltaX != 0) {
        builder.setPrevScrollDeltaSumX(sourceAction.prevScrollDeltaSumX + scrollDeltaX);
        builder.setIsPrevScrolled(true);
      }
      if (scrollDeltaY != DELTA_UNDEFINED && scrollDeltaY != 0) {
        builder.setPrevScrollDeltaSumY(sourceAction.prevScrollDeltaSumY + scrollDeltaY);
        builder.setIsPrevScrolled(true);
      }
      return builder.build();
    }

    public void onAutoScrollFailed(@NonNull AccessibilityNodeInfoCompat nodeToScroll) {
      LogUtils.d(
          TAG,
          "AutoScrollCallback onAutoScrollFailed, assumeScrollSuccess="
              + assumeScrollSuccess
              + ",actionType="
              + NavigationAction.actionTypeToString(sourceAction.actionType));

      if (assumeScrollSuccess) {
        onAutoScrolled(
            nodeToScroll, EVENT_ID_UNTRACKED, /* scrollDeltaX= */ 0, /* scrollDeltaY*/ 0);
        return;
      }
      switch (sourceAction.actionType) {
        case NavigationAction.DIRECTIONAL_NAVIGATION -> {
          if (TableNavigation.isNativeTableGranularity(sourceAction.targetType)) {
            TableNavigation tableNavigation =
                new TableNavigation(
                    sourceAction, nodeToScroll, parent.focusFinder, EVENT_ID_UNTRACKED);
            parent.handleViewAutoScrollFailedForTableNavigationAction(tableNavigation);
          } else {
            parent.handleViewAutoScrollFailedForDirectionalNavigationAction(
                nodeToScroll, sourceAction);
          }
        }
        default -> {}
      }
      clear();
    }

    public boolean assumeScrollSuccess() {
      return assumeScrollSuccess;
    }

    /** Clears assumeScrollSuccess */
    private void clear() {
      assumeScrollSuccess = false;
    }
  }

  /** A data class to represent the navigation result of logical navigation. */
  private static class NavigationResult {
    /** The type of the navigation result. */
    public enum Type {
      EMPTY, // No element found.
      NATIVE_ELEMENT, // A native element found, calls #getNode to return the native element node.
      WEB_ELEMENT, // A web element found, it should skip the navigation and return success.
      EXISTING_FOCUS, // An existing focus found, it should skip the navigation and return success.
      REACH_EDGE, // An edge found, it should skip the navigation and return failure.
      EXCEPTION, // General exception cases, it should skip the navigation and return failure.
    }

    private Type type;
    private AccessibilityNodeInfoCompat node;
    private int linkIndex;

    private NavigationResult(Type type, AccessibilityNodeInfoCompat node) {
      this.type = type;
      this.node = node;
      this.linkIndex = -1;
    }

    /** Convenient method to create an instance for the certain type without the node. */
    public static NavigationResult create(Type type) {
      if (type == Type.NATIVE_ELEMENT) {
        throw new IllegalArgumentException("create native type without node");
      }
      return new NavigationResult(type, null);
    }

    /**
     * Convenient method to create an instance for the native element, return empty if null node.
     */
    public static NavigationResult create(AccessibilityNodeInfoCompat node) {
      return new NavigationResult((node == null ? Type.EMPTY : Type.NATIVE_ELEMENT), node);
    }

    /** Gets the native element node. */
    public @Nullable AccessibilityNodeInfoCompat getNode() {
      if (shouldSkipNavigation()) {
        throw new IllegalStateException("getNode for skipped type: " + type);
      }
      return node;
    }

    public boolean isEmpty() {
      return Type.EMPTY.equals(type);
    }

    public boolean isWebElement() {
      return Type.WEB_ELEMENT.equals(type);
    }

    public boolean isFocusAvailable() {
      return Type.NATIVE_ELEMENT.equals(type) || isWebElement() || Type.EXISTING_FOCUS.equals(type);
    }

    /** Returns true if it should skip the navigation immediately. */
    public boolean shouldSkipNavigation() {
      return Type.EXISTING_FOCUS.equals(type)
          || isWebElement()
          || Type.REACH_EDGE.equals(type)
          || Type.EXCEPTION.equals(type);
    }

    public void setLinkIndex(int linkIndex) {
      this.linkIndex = linkIndex;
    }

    public int getLinkIndex() {
      return linkIndex;
    }
  }

  /**
   * Filters nodes which are smaller than the temporary scrollable node. The accumulator will update
   * the temporary scrollable node once it finds a bigger scrollable node. Finally it can get the
   * node with maximum area in the node tree.
   */
  private static class MaxSizeNodeAccumulator extends Filter<AccessibilityNodeInfoCompat> {
    final Filter<AccessibilityNodeInfoCompat> scrollableFilter;
    AccessibilityNodeInfoCompat maximumScrollableNode;
    int maximumSize;

    /**
     * @param node Initial node of the max size check.
     */
    MaxSizeNodeAccumulator(
        @Nullable AccessibilityNodeInfoCompat node,
        Filter<AccessibilityNodeInfoCompat> scrollableFilter) {
      this.scrollableFilter = scrollableFilter;
      if (node == null) {
        maximumSize = 0;
      } else {
        maximumScrollableNode = node;
        Rect nodeBounds = new Rect();
        maximumScrollableNode.getBoundsInScreen(nodeBounds);
        maximumSize = nodeBounds.width() * nodeBounds.height();
      }
    }

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node) {
      if (node == null) {
        return true;
      }

      Rect nodeBounds = new Rect();
      node.getBoundsInScreen(nodeBounds);
      int nodeSize = nodeBounds.width() * nodeBounds.height();
      if (nodeSize <= maximumSize) {
        return true;
      } else {
        // Update maximum scrollable node if the node is scrollable.
        if (scrollableFilter.accept(node)) {
          maximumScrollableNode = node;
          maximumSize = nodeSize;
        }
      }

      return false;
    }
  }

  /** Filters target window when performing directional navigation across windows. */
  private static class DirectionalNavigationWindowFilter extends Filter<AccessibilityWindowInfo> {
    final Context context;
    final UniversalSearchActor.State searchState;

    DirectionalNavigationWindowFilter(Context context, UniversalSearchActor.State searchState) {
      this.context = context;
      this.searchState = searchState;
    }

    @Override
    public boolean accept(AccessibilityWindowInfo window) {
      if (window == null) {
        return false;
      }
      int type = AccessibilityWindowInfoUtils.getType(window);
      if (searchState.isUiVisible()) {
        return (isSearchOverlay(context, window)
            || (type == AccessibilityWindowInfo.TYPE_SYSTEM
                && (!WindowUtils.isSystemBar(context, window)
                    || !SettingsUtils.allowLinksOutOfSettings(
                        context))) // System bar in SUW is navigable.
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            || (Role.getRole(window.getRoot()) == ROLE_FLOATING_ACTION_BUTTON));
      } else {
        boolean focusContainedInApplicationWindow =
            FormFactorUtils.isAndroidPc()
                && FeatureFlagReader.focusContainedInApplicationWindow(context);
        // When the focus should be contained in the application window,
        // application window type is filtered out.
        return ((!focusContainedInApplicationWindow
                && type == AccessibilityWindowInfo.TYPE_APPLICATION)
            || (type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER)
            || (type == AccessibilityWindowInfo.TYPE_SYSTEM
                && (!WindowUtils.isSystemBar(context, window)
                    || !SettingsUtils.allowLinksOutOfSettings(
                        context))) // System bar in SUW is navigable.
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            || (Role.getRole(window.getRoot()) == ROLE_FLOATING_ACTION_BUTTON));
      }
    }
  }

  private static class WindowNavigationFilter extends Filter<AccessibilityWindowInfo> {
    final Context context;
    final UniversalSearchActor.State searchState;

    WindowNavigationFilter(Context context, UniversalSearchActor.State searchState) {
      this.context = context;
      this.searchState = searchState;
    }

    @Override
    public boolean accept(AccessibilityWindowInfo window) {
      if (window == null) {
        return false;
      }

      int type = AccessibilityWindowInfoUtils.getType(window);
      if (searchState.isUiVisible()) {
        return isSearchOverlay(context, window)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            || (type == AccessibilityWindowInfo.TYPE_SYSTEM)
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)
            || (Role.getRole(window.getRoot()) == ROLE_FLOATING_ACTION_BUTTON);
      } else {
        return (type == AccessibilityWindowInfo.TYPE_APPLICATION)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            || (type == AccessibilityWindowInfo.TYPE_SYSTEM)
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)
            || (Role.getRole(window.getRoot()) == ROLE_FLOATING_ACTION_BUTTON);
      }
    }
  }

  private static boolean isSearchOverlay(Context context, AccessibilityWindowInfo window) {
    return (AccessibilityWindowInfoUtils.getType(window)
            == AccessibilityWindowInfoCompat.TYPE_ACCESSIBILITY_OVERLAY)
        && TextUtils.equals(window.getTitle(), context.getString(R.string.title_screen_search));
  }
}
