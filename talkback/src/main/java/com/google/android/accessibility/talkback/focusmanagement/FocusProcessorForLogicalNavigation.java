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
import static com.google.android.accessibility.utils.AccessibilityEventUtils.DELTA_UNDEFINED;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_SCROLLABLE_GRID;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.Role.ROLE_GRID;
import static com.google.android.accessibility.utils.Role.ROLE_HORIZONTAL_SCROLL_VIEW;
import static com.google.android.accessibility.utils.Role.ROLE_LIST;
import static com.google.android.accessibility.utils.Role.ROLE_PAGER;
import static com.google.android.accessibility.utils.Role.ROLE_SCROLL_VIEW;
import static com.google.android.accessibility.utils.Role.ROLE_WEB_VIEW;
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
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchActor;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory.WindowIdentifier;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.utils.DiagnosticOverlayControllerImpl;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.DisplayUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.NodeActionFilter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.ScrollableNodeInfo;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollTimeout;
import com.google.android.accessibility.utils.monitor.CollectionState;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.ScrollActionRecord.UserAction;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.GridTraversalManager;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Handles the use case of logical navigation actions. */
public class FocusProcessorForLogicalNavigation {

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "FocusProcessor-LogicalNav";

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

  // Whether the previous navigation action reaches the edge of the window.
  private boolean reachEdge = false;

  /** The last node that was scrolled while navigating with native macro granularity. */
  @Nullable private AccessibilityNodeInfoCompat lastScrolledNodeForNativeMacroGranularity;

  /** Callback to handle scroll success or failure. */
  @Nullable private AutoScrollCallback scrollCallback;

  // Object-wrapper around static-method getAccessibilityFocus(), for test-mocking.
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  private final FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FocusProcessorForLogicalNavigation(
      AccessibilityService service,
      FocusFinder focusFinder,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ScreenStateMonitor.State screenState,
      UniversalSearchActor.State searchState) {
    this.service = service;
    this.focusFinder = focusFinder;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.screenState = screenState;
    this.searchState = searchState;
    isWindowNavigationSupported = !formFactorUtils.isAndroidTv();
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
    switch (navigationAction.actionType) {
      case NavigationAction.DIRECTIONAL_NAVIGATION:
        return onDirectionalNavigationAction(
            pivot, /* ignoreDescendantsOfPivot= */ false, navigationAction, eventId);
      case NavigationAction.JUMP_TO_TOP:
      case NavigationAction.JUMP_TO_BOTTOM:
        return onJumpAction(pivot, navigationAction, eventId);
      case NavigationAction.SCROLL_FORWARD:
      case NavigationAction.SCROLL_BACKWARD:
        return onScrollAction(pivot, navigationAction, eventId);
      case NavigationAction.SCROLL_UP:
      case NavigationAction.SCROLL_DOWN:
      case NavigationAction.SCROLL_LEFT:
      case NavigationAction.SCROLL_RIGHT:
        return onScrollOrPageAction(pivot, navigationAction, eventId);
      default:
        return false;
    }
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
  @Nullable
  private AccessibilityNode search(
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
   * Handles {@link NavigationAction#DIRECTIONAL_NAVIGATION} actions.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean onDirectionalNavigationAction(
      @NonNull AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      @NonNull NavigationAction navigationAction,
      @Nullable EventId eventId) {
    if (NavigationTarget.isHtmlTarget(navigationAction.targetType)) {
      // Apply different logic when navigating with html granularity in WebView.
      return navigateToHtmlTarget(pivot, navigationAction, eventId);
    } else if (navigationAction.targetType == NavigationTarget.TARGET_CONTAINER) {
      return navigateToContainer(pivot, navigationAction, eventId);
    } else if (navigationAction.targetType == NavigationTarget.TARGET_WINDOW) {
      return navigateToWindowTarget(pivot, navigationAction, eventId);
    } else {
      return navigateToDefaultOrNativeMacroGranularityTarget(
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
    AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
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
      case NavigationAction.SCROLL_UP:
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
        break;
      case NavigationAction.SCROLL_DOWN:
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
        break;
      case NavigationAction.SCROLL_LEFT:
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
        break;
      case NavigationAction.SCROLL_RIGHT:
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
        break;
      default:
        return false;
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

  /** Finds next node in a different container (list/grid/pager/recycler/scrollable). */
  public boolean navigateToContainer(
      @Nullable AccessibilityNodeInfoCompat start,
      @NonNull NavigationAction navigationAction,
      EventId eventId) {

    if (start == null) {
      return false;
    }

    @SearchDirection int direction = navigationAction.searchDirection;
    @Nullable AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(start);

    // Find current container, searching up from current-focus.
    @Nullable AccessibilityNodeInfoCompat containerOld = currentContainer(start);
    LogUtils.v(
        TAG,
        "FocusProcessorForLogicalNavigation.navigateToContainer() containerOld=%s",
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
                n -> {
                  return !Objects.equals(containerOld, currentContainer(n))
                      && AccessibilityNodeInfoUtils.shouldFocusNode(
                          n, traversalStrategy.getSpeakingNodesCache());
                }));
    LogUtils.v(
        TAG,
        "FocusProcessorForLogicalNavigation.navigateToContainer() target container=%s",
        currentContainer(target));

    // No target found.
    if (target == null) {
      announceNativeMacroElement(direction, navigationAction.targetType, eventId);
      return false;
    }
    return setAccessibilityFocusInternal(target, navigationAction, eventId);
  }

  /** Finds current container (list/grid/pager/recycler/scrollable...) */
  @Nullable
  private AccessibilityNodeInfoCompat currentContainer(
      @Nullable AccessibilityNodeInfoCompat start) {
    if (start == null) {
      return null;
    }

    // Search up from start-node.
    final List<Integer> containerRoles =
        ImmutableList.of(
            ROLE_LIST,
            ROLE_GRID,
            ROLE_PAGER,
            ROLE_SCROLL_VIEW,
            ROLE_HORIZONTAL_SCROLL_VIEW,
            ROLE_WEB_VIEW);
    @Nullable AccessibilityNodeInfoCompat container =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            start,
            Filter.node(
                (n) -> {
                  return containerRoles.indexOf(Role.getRole(n)) >= 0;
                }));
    if (container == null) {
      return null;
    }

    // Find outermost scrollable around container.  Example: ScrollView containing ListView.
    container =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            container,
            Filter.node(
                (n) -> {
                  return (n != null) && (Role.getRole(n.getParent()) != ROLE_SCROLL_VIEW);
                }));
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
            /* shouldRestoreLastFocus= */ true,
            actorState.getFocusHistory(),
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
  @Nullable
  private AccessibilityNodeInfoCompat currentPaneContainer(
      @Nullable AccessibilityNodeInfoCompat start) {
    return (start == null)
        ? null
        : AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            start, Filter.node(n -> !TextUtils.isEmpty(n.getPaneTitle())));
  }

  /**
   * Navigates to default target or native macro granularity target.
   *
   * <p>This navigation action happens in three use cases:
   *
   * <ul>
   *   <li>The user is navigating with default granularity
   *   <li>The use is navigating with native macro granularity.
   *   <li>The user is navigating with micro granularity, but reaches edge of current node, and
   *       needs to move focus to another node. In this case {@link
   *       NavigationAction#isNavigatingWithMicroGranularity} is set to {@code true}.
   * </ul>
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToDefaultOrNativeMacroGranularityTarget(
      @NonNull AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      EventId eventId) {
    int searchDirection = navigationAction.searchDirection;
    int logicalDirection =
        TraversalStrategyUtils.getLogicalDirection(
            searchDirection, WindowUtils.isScreenLayoutRTL(service));

    // Use different logic when navigating with default granularity on WebView elements.
    // If the current node has web content, attempt HTML navigation only in 2 conditions:
    // 1. If currently focused is not a web view container OR
    // 2. If currently focused is a web view container but the logical direction is forward.
    // Consider the following linear order when navigating between web
    // views and native views assuming that a web view is in between native elements:
    // Native elements -> web view container -> inside web view container -> native elements.
    // Web view container should be focused only in the above order.
    AccessibilityNodeInfoCompat webContainer = null;
    if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT
        && WebInterfaceUtils.supportsWebActions(pivot)
        && (Role.getRole(pivot) != Role.ROLE_WEB_VIEW
            || logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD)) {
      // Navigate to html element with default granularity.
      if (navigateToHtmlTarget(pivot, navigationAction, eventId)) {
        return true;
      } else {
        // Ascend pivot to WebView container node, prepare to navigate out of WebView with normal
        // navigation.
        webContainer = WebInterfaceUtils.ascendToWebView(pivot);
        if (webContainer != null) {
          pivot = webContainer;
        }
      }
    }

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

    // Search for target node within current window.
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
    // Begin and end node collection for Diagnostic Overlay Controller before and
    // after call to searchFocus, so that only nodes traversed, but not focused
    // (as result of gesture swipe) are collected.
    DiagnosticOverlayControllerImpl.setNodeCollectionEnabled(true);
    AccessibilityNodeInfoCompat target =
        TraversalStrategyUtils.searchFocus(traversalStrategy, pivot, searchDirection, nodeFilter);
    DiagnosticOverlayControllerImpl.setNodeCollectionEnabled(false);

    // GRID traversal
    AccessibilityNodeInfoCompat grid =
        AccessibilityNodeInfoUtils.getMatchingAncestor(pivot, FILTER_SCROLLABLE_GRID);
    if (grid != null && target != null) {

      // For horizontal grids, the logical target may lie outside the current screen and the target
      // provided by the framework may be incorrect. We use GridTraversalManager, which uses
      // the grid's CollectionInfo and the pivot node and target node's CollectionItemInfo to
      // suggest an alternate target. If performing the scroll action failed, fallback to the
      // original searching focus strategy.
      Pair<Integer, Integer> targetPositionForScroll =
          GridTraversalManager.suggestOffScreenTarget(grid, pivot, target, logicalDirection);
      if (performActionScrollToPosition(grid, targetPositionForScroll, eventId)) {
        return true;
      }
    }

    // If the target is a web view, avoid focusing on it when the direction is backward.
    // Consider the following linear order when navigating between web
    // views and native views assuming that a web view is in between native elements:
    // Native elements -> web view container -> inside web view container -> native elements.
    // Web view container should be focused only in the above order.
    if ((navigationAction.targetType == NavigationTarget.TARGET_DEFAULT)
        && (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD)) {
      if ((target != null)
          && (Role.getRole(target) == Role.ROLE_WEB_VIEW)
          && !WebInterfaceUtils.supportsWebActions(pivot)) {
        if (navigateToHtmlTarget(/* pivot= */ target, navigationAction, eventId)) {
          return true;
        }
      }
    }

    // When the result of autoScrollAtEdge() is true but we receive no scrolled event, the
    // navigation
    // action is repeated in handleViewAutoScrollFailedForDirectionalNavigationAction() with
    // ignoreDescendantsOfPivot set to true. If for macro granularity, we repeat the scroll
    // action it would result in an infinite loop and so attempt to scroll is
    // made only if ignoreDescendantsOfPivot is set to false.
    if (NavigationTarget.isMacroGranularity(navigationAction.targetType)
        && !ignoreDescendantsOfPivot) {
      boolean scrolled =
          scrollForNativeMacroGranularity(
              target, navigationAction, getScrollFilter(navigationAction), eventId);
      // If the scroll was unsuccessful, we should not return.
      if (scrolled) {
        return true;
      }
    }

    // Optimize the traversal order of AutoCompleteTextView suggestions to make the suggestions
    // navigable immediately after AutoCompleteTextView.
    @Nullable AccessibilityWindowInfo currentWindow =
        AccessibilityNodeInfoUtils.getWindow(pivot.unwrap());
    AccessibilityNodeInfoCompat anchorNode = AccessibilityWindowInfoUtils.getAnchor(currentWindow);
    if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT
        && logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD
        && Role.getRole(pivot) == Role.ROLE_EDIT_TEXT) {
      // Set the target to the initial focus node of the anchored window.
      AccessibilityWindowInfo anchoredWindow =
          AccessibilityWindowInfoUtils.getAnchoredWindow(pivot.unwrap());
      if (anchoredWindow != null) {
        rootNode = AccessibilityWindowInfoUtils.getRootCompat(anchoredWindow);
        traversalStrategy = createTraversal(rootNode, logicalDirection);
        target =
            TraversalStrategyUtils.findFirstFocusInNodeTree(
                traversalStrategy, rootNode, logicalDirection, nodeFilter);
      }
    } else if (navigationAction.targetType == NavigationTarget.TARGET_DEFAULT
        && target == null
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
          traversalStrategy = createTraversal(rootNode, logicalDirection);
          target =
              TraversalStrategyUtils.searchFocus(
                  traversalStrategy, anchorNode, searchDirection, nodeFilter);
        }
      }
    }

    // Navigate across windows.
    if (isWindowNavigationSupported && target == null) {
      WindowTraversal windowTraversal = new WindowTraversal(service);
      boolean isScreenRtl = WindowUtils.isScreenLayoutRTL(service);
      DirectionalNavigationWindowFilter windowFilter =
          new DirectionalNavigationWindowFilter(service, searchState);

      if (currentWindow == null) {
        // Ideally currentWindow should never be null. Do the null check in case of exception.
        LogUtils.w(TAG, "Cannot navigate across window: unable to identify current window");
        return false;
      }

      // Skip one swipe if it's the last element in the last window.
      if (!reachEdge
          && (!windowFilter.accept(currentWindow)
              || needPauseWhenTraverseAcrossWindow(
                  windowTraversal, isScreenRtl, currentWindow, searchDirection, windowFilter))) {
        reachEdge = true;
        announceNativeMacroElement(logicalDirection, navigationAction.targetType, eventId);
        LogUtils.v(TAG, "Reach edge before searchTargetInNextOrPreviousWindow in:" + currentWindow);
        return false;
      }

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
                  /* shouldRestoreLastFocus= */ false,
                  /* accessibilityFocusActionHistory= */ null,
                  windowFilter,
                  NavigationTarget.createNodeFilter(
                      navigationAction.targetType, speakingNodesCache));
          if (reachEdgeBeforeSearch != reachEdge) {
            // Skip one swipe if reaching edge while searching windows in loop.
            announceNativeMacroElement(logicalDirection, navigationAction.targetType, eventId);
            return false;
          }
        }
      }
    }

    // Try to wrap around inside current window, which is equivalent to find the initial focus
    // in current window with the given search direction.
    if ((target == null) && reachEdge && navigationAction.shouldWrap) {
      target =
          TraversalStrategyUtils.findFirstFocusInNodeTree(
              traversalStrategy,
              rootNode,
              searchDirection,
              NavigationTarget.createNodeFilter(
                  navigationAction.targetType, traversalStrategy.getSpeakingNodesCache()));
    }

    // When searching the next focus, potentially consider the result of View.focusSearch() which
    // defines the next input focus in the given direction in the absence of an accessibility
    // service.
    // If only one of the TalkBack target and the focusSearch target is accessibility-focusable, or
    // exists in the first place, prefer that one.
    // If only one of them is input-focusable (or enabled), prefer the one that is not.
    // If both are input-focusable (and enabled), prefer the focusSearch target.
    if ((navigationAction.targetType == NavigationTarget.TARGET_DEFAULT)
        && TraversalStrategyUtils.isSpatialDirection(navigationAction.searchDirection)) {
      int focusDirection =
          TraversalStrategyUtils.nodeSearchDirectionToViewSearchDirection(
              navigationAction.searchDirection);
      AccessibilityNodeInfoCompat focusSearchTarget = pivot.focusSearch(focusDirection);
      // Potentially allow the currently focused node to keep focus even if not
      // accessibility-focusable.
      // The reason is that per default it is marked as not accessibility-focusable if it has focus.
      if ((focusSearchTarget != null)
          && focusSearchTarget.isAccessibilityFocused()
          && allowFocusResting(target)) {
        LogUtils.d(TAG, "Using focusSearch() target which is the already focused node.");
        return true;
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
    }

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
        return true;
      }
    }

    if (target != null) {
      return setAccessibilityFocusInternal(target, navigationAction, eventId);
    }

    // No target found.
    announceNativeMacroElement(logicalDirection, navigationAction.targetType, eventId);
    return false;
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
      EventId eventId) {
    if (targetPosition == null) {
      return false;
    }
    Bundle arguments = new Bundle();
    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, targetPosition.first);
    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, targetPosition.second);
    return pipeline.returnFeedback(
        eventId,
        Feedback.nodeAction(
            nodeInfo, AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION.getId(), arguments));
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
    // to make sure we have traversed the complete list before jumping to an element that is on
    // screen but out of the scrollable container. So the target that is found to be out of the
    // scrollable container is ignored.
    if ((focusedOrReferenceNodeParent != null)
        && (target != null)
        && !AccessibilityNodeInfoUtils.hasAncestor(target, focusedOrReferenceNodeParent)) {
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
              /* isRtl= */ WindowUtils.isScreenLayoutRTL(service));
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
  @Nullable
  private static AccessibilityNodeInfoCompat refreshAndGetFirstOrLastChild(
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
   * @param shouldRestoreLastFocus Whether to restore last focus in target window. When set to
   *     {@code true}, we try to restore last valid focus from {@link
   *     AccessibilityFocusActionHistory} in the accepted target window.
   * @param accessibilityFocusActionHistory The {@link AccessibilityFocusActionHistory} instance to
   *     query for the last focused node in a given window. It must not be {@code null} if {@code
   *     shouldRestoreLastFocus} is set to {@code true}.
   * @param windowFilter Filters {@link AccessibilityWindowInfo}.
   * @param nodeFilter Filters for target node.
   * @return Accepted target node in the previous or next accepted window.
   */
  @Nullable
  @VisibleForTesting
  public AccessibilityNodeInfoCompat searchTargetInNextOrPreviousWindow(
      @Nullable ScreenState currentScreenState,
      WindowTraversal windowTraversal,
      boolean isScreenRtl,
      @Nullable AccessibilityNodeInfoCompat currentFocus,
      AccessibilityWindowInfo currentWindow,
      @TraversalStrategy.SearchDirection int direction,
      FocusFinder focusFinder,
      boolean shouldRestoreLastFocus,
      AccessibilityFocusActionHistory.Reader accessibilityFocusActionHistory,
      Filter<AccessibilityWindowInfo> windowFilter,
      Filter<AccessibilityNodeInfoCompat> nodeFilter) {
    if (shouldRestoreLastFocus && (accessibilityFocusActionHistory == null)) {
      throw new IllegalArgumentException(
          "AccessibilityFocusActionHistory must not be null when shouldRestoreLastFocus is true.");
    }
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

      // Try to restore focus in the target window.
      if (shouldRestoreLastFocus) {
        final WindowIdentifier windowIdentifier =
            WindowIdentifier.create(targetWindow.getId(), currentScreenState);
        FocusActionRecord record =
            accessibilityFocusActionHistory.getLastFocusActionRecordInWindow(windowIdentifier);
        AccessibilityNodeInfoCompat focusToRestore =
            (record == null) ? null : record.getFocusedNode();
        if ((focusToRestore != null) && focusToRestore.refresh()) {
          return focusToRestore;
        }
      }

      // Search for the initial focusable node in the target window.
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
    ScrollableNodeInfo scrollableNodeInfo =
        ScrollableNodeInfo.findScrollableNodeForDirection(
            searchDirection,
            /* pivot= */ node,
            /* includeSelf= */ false,
            /* isRtl= */ WindowUtils.isScreenLayoutRTL(service));

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

    // Check the scroll direction.
    Boolean isHorizontal = null;
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

    if (isHorizontal == null) {
      // Not perform EnsureOnScreen if we can't make sure the data of scrolling direction is
      // correct.
      return false;
    }

    Rect scrollableNodeBounds = AccessibilityNodeInfoUtils.getNodeBoundsInScreen(scrollableNode);
    Rect nodeBounds = AccessibilityNodeInfoUtils.getNodeBoundsInScreen(node);
    boolean isRtl = WindowUtils.isScreenLayoutRTL(context);

    if (TraversalStrategyUtils.isSpatialDirection(searchDirection)) {
      searchDirection = TraversalStrategyUtils.getLogicalDirection(searchDirection, isRtl);
    }

    switch (searchDirection) {
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        if (isHorizontal) {
          if (isRtl) {
            if (scrollableNodeBounds.left == nodeBounds.left) {
              return true;
            }
          } else {
            if (scrollableNodeBounds.right == nodeBounds.right) {
              return true;
            }
          }
        } else if (scrollableNodeBounds.bottom == nodeBounds.bottom) {
          return true;
        }
        break;
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        if (isHorizontal) {
          if (isRtl) {
            if (scrollableNodeBounds.right == nodeBounds.right) {
              return true;
            }
          } else {
            if (scrollableNodeBounds.left == nodeBounds.left) {
              return true;
            }
          }
        } else if (scrollableNodeBounds.top == nodeBounds.top) {
          return true;
        }
        break;
      default: // Do nothing.
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
    // Clear the "reachEdge" flag.
    reachEdge = false;
    resetLastScrolledNodeForNativeMacroGranularity();
    return pipeline.returnFeedback(
        eventId,
        Feedback.focus(
                target,
                FocusActionInfo.builder()
                    .setSourceAction(FocusActionInfo.LOGICAL_NAVIGATION)
                    .setNavigationAction(navigationAction)
                    .build())
            .setForceRefocus(true));
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
  @Nullable
  private static Filter<AccessibilityNodeInfoCompat> getScrollFilter(
      NavigationAction navigationAction) {
    final int scrollAction;
    switch (navigationAction.actionType) {
      case NavigationAction.SCROLL_UP:
      case NavigationAction.SCROLL_LEFT:
      case NavigationAction.SCROLL_FORWARD:
        scrollAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
        break;
      case NavigationAction.SCROLL_DOWN:
      case NavigationAction.SCROLL_RIGHT:
      case NavigationAction.SCROLL_BACKWARD:
        scrollAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
        break;
      case NavigationAction.DIRECTIONAL_NAVIGATION:
        scrollAction =
            TraversalStrategyUtils.convertSearchDirectionToScrollAction(
                navigationAction.searchDirection);
        break;
      default:
        scrollAction = 0;
        break;
    }

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
  @Nullable
  private static Filter<AccessibilityNodeInfoCompat> getScrollOrPageActionFilter(
      NavigationAction navigationAction) {
    int pageAction = 0;
    int scrollAction = 0;
    switch (navigationAction.actionType) {
      case NavigationAction.SCROLL_UP:
        pageAction = ACTION_PAGE_DOWN.getId();
        scrollAction = ACTION_SCROLL_FORWARD.getId();
        break;
      case NavigationAction.SCROLL_DOWN:
        pageAction = ACTION_PAGE_UP.getId();
        scrollAction = ACTION_SCROLL_BACKWARD.getId();
        break;
      case NavigationAction.SCROLL_LEFT:
        pageAction = ACTION_PAGE_RIGHT.getId();
        scrollAction = ACTION_SCROLL_FORWARD.getId();
        break;
      case NavigationAction.SCROLL_RIGHT:
        pageAction = ACTION_PAGE_LEFT.getId();
        scrollAction = ACTION_SCROLL_BACKWARD.getId();
        break;
      default:
        pageAction = 0;
        scrollAction = 0;
        break;
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

    // No need to scroll when the pivot (focused item) is already the outermost item.
    // This is a workaround for widgets that do not correctly remove scroll actions, as of writing
    // a prominent example of this is leanback's VerticalGridView.
    if (isCollectionItemLastInDirection(pivot, scrollableNode, navigationAction.searchDirection)) {
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

    switch (searchDirection) {
      case SEARCH_FOCUS_UP:
        return item.getRowIndex() == 0;
      case SEARCH_FOCUS_DOWN:
        return item.getRowIndex() + item.getRowSpan() == container.getRowCount();
      case SEARCH_FOCUS_LEFT:
        return item.getColumnIndex() == 0;
      case SEARCH_FOCUS_RIGHT:
        return item.getColumnIndex() + item.getColumnSpan() == container.getColumnCount();
      case SEARCH_FOCUS_BACKWARD:
        return (container.getColumnCount() == 1 && item.getRowIndex() == 0)
            || (container.getRowCount() == 1 && item.getColumnIndex() == 0);
      case SEARCH_FOCUS_FORWARD:
        return (container.getColumnCount() == 1
                && item.getRowIndex() + item.getRowSpan() == container.getRowCount())
            || (container.getRowCount() == 1
                && item.getColumnIndex() + item.getColumnSpan() == container.getColumnCount());
      default:
        return false;
    }
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
        eventId);
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
        eventId);
  }

  private boolean performScrollActionInternal(
      @UserAction int userAction,
      @NonNull AccessibilityNodeInfoCompat scrollableNode,
      @NonNull AccessibilityNodeInfoCompat pivotNode,
      int scrollAction,
      NavigationAction sourceAction,
      ScrollTimeout scrollTimeout,
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
            scrollableNode, userAction, scrollAction, ScrollActionRecord.FOCUS, scrollTimeout));
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
        // Prevent changing the focus if it has already been changed, e.g. by another actor.
        // In the onAutoScrolled method, we will assign a new scrollCallback.
        autoScrollCallback.onAutoScrolled(scrolledNode, eventId, scrollDeltaX, scrollDeltaY);
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
    if ((refNode == null)) {
      // First child if direction is forward, else last child.
      boolean firstChild = (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD);
      refNode = refreshAndGetFirstOrLastChild(scrolledNode, firstChild);
    }

    // Local TraversalStrategy generated in sub-tree of a refreshed scrolledNode.
    TraversalStrategy localTraversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(
            scrolledNode, focusFinder, sourceAction.searchDirection);
    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            sourceAction.targetType, localTraversalStrategy.getSpeakingNodesCache());

    // Only if the refNode does not satisfy the desired macro granularity target type or is
    // default granularity, we look for the next target starting from
    // refNode. Else, just set the focus to refNode.
    AccessibilityNodeInfoCompat nodeToFocus;
    if (nodeFilter.accept(refNode) && !refNode.isAccessibilityFocused()) {
      nodeToFocus = refNode;
    } else {
      nodeToFocus =
          TraversalStrategyUtils.searchFocus(
              localTraversalStrategy, refNode, sourceAction.searchDirection, nodeFilter);
      setLastScrolledNodeForNativeMacroGranularity(scrolledNode);
      if (nodeToFocus == null) {
        boolean scrollSuccess = false;
        if (refNode != null && shouldKeepSearch(navigationAction)) {
          scrollSuccess =
              performScrollActionInternal(
                  ScrollActionRecord.ACTION_AUTO_SCROLL,
                  scrolledNode,
                  refNode,
                  TraversalStrategyUtils.convertSearchDirectionToScrollAction(
                      navigationAction.searchDirection),
                  navigationAction,
                  ScrollTimeout.SCROLL_TIMEOUT_LONG,
                  eventId);
        }
        if (!scrollSuccess) {
          announceNativeMacroElement(logicalDirection, navigationAction.targetType, eventId);
          // If no target is found and the scrolled screen doesn't have the accessibility
          // focus(refNode is not from AccessibilityFocusMonitor#getAccessibilityFocus), then search
          // for the new initial focus in the scrollable container.
          if (refNode != null && !refNode.isAccessibilityFocused()) {
            if (FILTER_SHOULD_FOCUS.accept(refNode)) {
              nodeToFocus = refNode;
            } else {
              nodeToFocus =
                  TraversalStrategyUtils.searchFocus(
                      localTraversalStrategy,
                      refNode,
                      sourceAction.searchDirection,
                      FILTER_SHOULD_FOCUS);
            }
            if (nodeToFocus != null) {
              setAccessibilityFocusInternal(nodeToFocus, navigationAction, eventId);
            }
          }
        }
        return;
      }
    }

    // If we're moving backward with default target from native views to WebView container node,
    // automatically descend to the last element in the WebView.
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      // We don't need to check role of the last focused node, because auto-scroll always
      // happens on native pivot.
      if (Role.getRole(nodeToFocus) == Role.ROLE_WEB_VIEW) {
        if (navigateToHtmlTarget(/* pivot= */ nodeToFocus, navigationAction, eventId)) {
          return;
        }
      }
    }
    setAccessibilityFocusInternal(nodeToFocus, navigationAction, eventId);
  }

  // TODO: Provides an overall experience of focusing on small nodes on both watch and
  //  phone devices.
  public static final int MAX_WEAR_AUTO_SCROLL_ATTEMPT = 5;
  public static final int MAX_WEAR_SCROLL_SCREEN_MULTIPLIER = 3;

  private boolean shouldKeepSearch(NavigationAction navigationAction) {
    if (!formFactorUtils.isAndroidWear()) {
      return false;
    }

    final Point screenPxSize = DisplayUtils.getScreenPixelSizeWithoutWindowDecor(service);
    return navigationAction.autoScrollAttempt <= MAX_WEAR_AUTO_SCROLL_ATTEMPT
        && (BuildVersionUtils.isAtLeastP() // Only build version at least P supports scroll delta.
            && Math.abs(navigationAction.prevScrollDeltaSumX)
                < MAX_WEAR_SCROLL_SCREEN_MULTIPLIER * screenPxSize.x
            && Math.abs(navigationAction.prevScrollDeltaSumY)
                < MAX_WEAR_SCROLL_SCREEN_MULTIPLIER * screenPxSize.y);
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
        // If pivot didn't move, give up. The container might not be scrollable in this direction.
        if (previousRect.equals(newRect)) {
          LogUtils.v(TAG, "Pivot didn't move, do not repeat navigation action.");
          return;
        }

        // Otherwise, repeat navigation action in hope that eventually a new item will be exposed.
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

    setAccessibilityFocusInternal(nodeToFocus, navigationAction, eventId);
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

  /** Announces if there are no more elements while using macro native granularity. */
  private void announceNativeMacroElement(
      int direction, @TargetType int macroTargetType, EventId eventId) {
    boolean forward = (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD);
    int resId = forward ? R.string.end_of_page : R.string.start_of_page;

    String target = null;
    try {
      if (NavigationTarget.isMacroGranularity(macroTargetType)) {
        target = NavigationTarget.macroTargetToDisplayName(/* context= */ service, macroTargetType);
      } else {
        // In case of any other target type, make no announcement.
        return;
      }
    } catch (IllegalArgumentException e) {
      LogUtils.w(TAG, "Invalid navigation target type.");
      return;
    }

    String text = service.getString(resId, target);
    announce(text, eventId);
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
  @Nullable
  public AccessibilityNodeInfoCompat searchScrollableNodeFromBfs(
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
          sumNavigationActionScrollDelta(scrollDeltaX, scrollDeltaY);

      LogUtils.d(
          TAG,
          "AutoScrollCallback onAutoScrolled, eventId="
              + eventId
              + ",navigationAction="
              + navigationAction);

      switch (sourceAction.actionType) {
        case NavigationAction.DIRECTIONAL_NAVIGATION:
          if (sourceAction.targetType == NavigationTarget.TARGET_DEFAULT) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithDefaultTarget(
                scrolledNode, pivot, navigationAction, eventId);
          } else if (NavigationTarget.isMacroGranularity(sourceAction.targetType)) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithMacroGranularityTarget(
                scrolledNode, navigationAction, eventId);
          }
          break;
        case NavigationAction.SCROLL_FORWARD:
          // fall through
        case NavigationAction.SCROLL_BACKWARD:
          parent.handleViewScrolledForScrollNavigationAction(
              scrolledNode, navigationAction, eventId);
          break;
        default:
          break;
      }
      clear();
    }

    private NavigationAction sumNavigationActionScrollDelta(int scrollDeltaX, int scrollDeltaY) {
      final NavigationAction.Builder builder = NavigationAction.Builder.copy(sourceAction);
      if (scrollDeltaX != DELTA_UNDEFINED) {
        builder.setPrevScrollDeltaSumX(sourceAction.prevScrollDeltaSumX + scrollDeltaX);
      }
      if (scrollDeltaY != DELTA_UNDEFINED) {
        builder.setPrevScrollDeltaSumY(sourceAction.prevScrollDeltaSumY + scrollDeltaY);
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
        case NavigationAction.DIRECTIONAL_NAVIGATION:
          parent.handleViewAutoScrollFailedForDirectionalNavigationAction(
              nodeToScroll, sourceAction);
          break;
        default:
          break;
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
                && !WindowUtils.isSystemBar(context, window))
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD));
      } else {
        return ((type == AccessibilityWindowInfo.TYPE_APPLICATION)
            || (type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER)
            || (type == AccessibilityWindowInfo.TYPE_SYSTEM
                && !WindowUtils.isSystemBar(context, window))
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD));
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
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY);
      } else {
        return (type == AccessibilityWindowInfo.TYPE_APPLICATION)
            || (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            || (type == AccessibilityWindowInfo.TYPE_SYSTEM)
            || (type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY);
      }
    }
  }

  private static boolean isSearchOverlay(Context context, AccessibilityWindowInfo window) {
    return (AccessibilityWindowInfoUtils.getType(window)
            == AccessibilityWindowInfoCompat.TYPE_ACCESSIBILITY_OVERLAY)
        && Objects.equals(window.getTitle(), context.getString(R.string.title_screen_search));
  }
}
