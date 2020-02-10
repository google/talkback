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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.ScrollEventInterpreter;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.UserAction;
import com.google.android.accessibility.talkback.focusmanagement.AutoScrollActor.AutoScrollRecord.Source;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.NodeActionFilter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import java.util.Map;

/** Handles the use case of logical navigation actions. */
public class FocusProcessorForLogicalNavigation {

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "FocusProcForLogicalNav";

  private static final Filter<AccessibilityNodeInfoCompat>
      SCROLLABLE_ROLE_FILTER_FOR_DIRECTION_NAVIGATION =
          AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL;

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

  private final AccessibilityService service;
  private ActorState actorState;
  private final ScreenStateMonitor screenStateMonitor;
  private Pipeline.FeedbackReturner pipeline;
  private final boolean isWindowNavigationSupported;

  // Whether the previous navigation action reaches the edge of the window.
  private boolean reachEdge = false;

  /** The last node that was scrolled while navigating with native macro granularity. */
  @Nullable private AccessibilityNodeInfoCompat lastScrolledNodeForNativeMacroGranularity;

  /** Callback to handle scroll success or failure. */
  private @Nullable AutoScrollCallback scrollCallback;

  // Object-wrapper around static-method getAccessibilityFocus(), for test-mocking.
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FocusProcessorForLogicalNavigation(
      AccessibilityService service,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ScreenStateMonitor screenStateMonitor) {
    this.service = service;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.screenStateMonitor = screenStateMonitor;
    isWindowNavigationSupported = !FeatureSupport.isTv(service);
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
    if (navigationAction.actionType == NavigationAction.UNKNOWN) {
      LogUtils.w(TAG, "Cannot perform navigation action: action type undefined.");
      return false;
    }
    AccessibilityNodeInfoCompat pivot = getPivotNodeForNavigationAction(navigationAction);
    if (pivot == null) {
      // Ideally the pivot should never be null. Do the null check in case of exception.
      LogUtils.w(TAG, "Cannot find pivot node for %s", navigationAction);
      return false;
    }
    try {
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
        default:
          return false;
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(pivot);
    }
  }

  /**
   * Moves focus to next node after current focused-node, which matches search-filter. Returns
   * success flag.
   */
  public boolean searchAndFocus(boolean startAtRoot, Filter<AccessibilityNodeInfoCompat> filter) {
    @Nullable AccessibilityNode found = search(startAtRoot, /* focus= */ true, filter);
    try {
      return (found != null);
    } finally {
      AccessibilityNode.recycle("FocusProcessorForLogicalNavigation.searchAndFocus()", found);
    }
  }

  /**
   * Finds next node which matches search-filter. Optionally focuses matching node. Returns matching
   * node, which caller must recycle.
   */
  private @Nullable AccessibilityNode search(
      boolean startAtRoot, boolean focus, Filter<AccessibilityNodeInfoCompat> filter) {

    AccessibilityNodeInfoCompat start = null;
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat target = null;
    TraversalStrategy traversalStrategy = null;
    try {
      // Try to find current accessibility-focused node.
      if (!startAtRoot) {
        start = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      }
      // Find root node, or return failure.
      if (start == null || !start.refresh()) {
        // Start from root node of active window.
        AccessibilityNodeInfoUtils.recycleNodes(start);
        rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(service);
        if (rootNode == null) {
          return null;
        }
        start = AccessibilityNodeInfoCompat.obtain(rootNode); // Copy, to not double-recycle root.
      } else {
        // Derive root node from start node.
        rootNode = AccessibilityNodeInfoUtils.getRoot(start);
        if (rootNode == null) {
          return null;
        }
      }

      // Search forward for node satisfying filter.
      @SearchDirection int direction = TraversalStrategy.SEARCH_FOCUS_FORWARD;
      traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(rootNode, direction);
      target = TraversalStrategyUtils.searchFocus(traversalStrategy, start, direction, filter);
      if (target == null) {
        return null;
      }

      // Focus first matching node.
      // Focus is implemented in the same function as searching, because they use the same rootNode
      // and traversalStrategy.
      if (focus) {
        EventId eventId = EVENT_ID_UNTRACKED;
        ensureOnScreen(target, /* shouldScroll= */ false, direction, traversalStrategy, eventId);
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
      // Null target so that it is not recycled, then return target.
      AccessibilityNode targetNode = AccessibilityNode.takeOwnership(target);
      target = null;
      return targetNode;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(start, rootNode, target);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
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
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the node.
   */
  private AccessibilityNodeInfoCompat getPivotNodeForNavigationAction(
      NavigationAction navigationAction) {
    AccessibilityNodeInfoCompat pivot =
        accessibilityFocusMonitor.getAccessibilityFocus(
            navigationAction.useInputFocusAsPivotIfEmpty);

    // If we cannot find a pivot, or the pivot is not accessible, choose the root node if the
    // active window.
    if (pivot == null || !pivot.refresh()) {
      AccessibilityNodeInfoUtils.recycleNodes(pivot);
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
      AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      EventId eventId) {
    if (NavigationTarget.isHtmlTarget(navigationAction.targetType)) {
      // Apply different logic when navigating with html granularity in WebView.
      return navigateToHtmlTarget(pivot, navigationAction, eventId);
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
    // Nodes and traversal strategy to be recycled.
    AccessibilityNodeInfoCompat target = null;
    AccessibilityNodeInfoCompat rootNode = null;
    TraversalStrategy traversalStrategy = null;

    try {
      rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
      if (rootNode == null) {
        LogUtils.w(TAG, "Cannot perform jump action: unable to find root node.");
        return false;
      }

      @SearchDirection
      int searchDirection =
          navigationAction.actionType == NavigationAction.JUMP_TO_TOP
              ? TraversalStrategy.SEARCH_FOCUS_FORWARD
              : TraversalStrategy.SEARCH_FOCUS_BACKWARD;

      traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(rootNode, searchDirection);

      // Always use default granularity when jumping to the beginning/end of the window.
      target =
          TraversalStrategyUtils.findInitialFocusInNodeTree(
              traversalStrategy,
              rootNode,
              searchDirection,
              NavigationTarget.createNodeFilter(
                  NavigationTarget.TARGET_DEFAULT, traversalStrategy.getSpeakingNodesCache()));
      if (target != null) {
        ensureOnScreen(
            target, /* shouldScroll= */ true, searchDirection, traversalStrategy, eventId);
        return setAccessibilityFocusInternal(target, navigationAction, eventId);
      }
      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(target, rootNode);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }

  /**
   * Handles {@link NavigationAction#SCROLL_FORWARD} and {@link NavigationAction#SCROLL_BACKWARD}
   * actions.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean onScrollAction(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    // Node to be recycled.
    AccessibilityNodeInfoCompat scrollableNode = null;
    AccessibilityNodeInfoCompat rootNode = null;
    try {
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
        scrollableNode = AccessibilityNodeInfoUtils.obtain(pivot);
      } else if ((pivot != null) && pivot.isAccessibilityFocused()) {
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
              ScrollEventInterpreter.ACTION_SCROLL_SHORTCUT,
              scrollableNode,
              pivot,
              scrollAction,
              navigationAction,
              eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(scrollableNode, rootNode);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Internal directional navigation methods based on target types.

  /**
   * Navigates to html target from a web pivot.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the pivot.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToHtmlTarget(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    if (!WebInterfaceUtils.supportsWebActions(pivot)) {
      LogUtils.w(TAG, "Cannot navigate to HTML target: current pivot is not a web element.");
      if (navigationAction.inputMode == InputModeManager.INPUT_MODE_KEYBOARD) {
        announceKeyboardShortcutNotSupported(eventId);
      }
      return false;
    }

    int webNavigationDirection =
        WebInterfaceUtils.searchDirectionToWebNavigationDirection(
            /* context= */ service, navigationAction.searchDirection);
    if (webNavigationDirection == 0) {
      LogUtils.w(TAG, "Cannot navigate to HTML target: invalid direction.");
      return false;
    }

    String htmlElementType = NavigationTarget.targetTypeToHtmlElement(navigationAction.targetType);
    if (htmlElementType == null) {
      LogUtils.w(TAG, "Cannot navigate to HTML target: invalid targetType.");
      return false;
    }

    boolean result =
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirectionHtml(
                pivot,
                webNavigationDirection,
                htmlElementType,
                FocusActionInfo.builder()
                    .setSourceAction(FocusActionInfo.LOGICAL_NAVIGATION)
                    .setNavigationAction(navigationAction)
                    .build()));
    if (!result && NavigationTarget.isHtmlTarget(navigationAction.targetType)) {
      announceNoMoreWebOrNativeMacroElement(
          /* forward= */ (webNavigationDirection == WebInterfaceUtils.DIRECTION_FORWARD),
          navigationAction.targetType,
          eventId);
    }
    return result;
  }

  /**
   * Navigate into the next or previous window.
   *
   * <p>Called when the user performs window navigation with keyboard shortcuts.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the pivot.
   *
   * @return {@code true} if any accessibility action is successfully performed.
   */
  private boolean navigateToWindowTarget(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    AccessibilityWindowInfo currentWindow = AccessibilityNodeInfoUtils.getWindow(pivot.unwrap());
    if (currentWindow == null) {
      return false;
    }
    AccessibilityNodeInfoCompat target = null;
    Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache = new HashMap<>();
    try {
      WindowManager windowManager = new WindowManager(service);
      boolean isScreenRtl = WindowManager.isScreenLayoutRTL(service);
      target =
          searchTargetInNextOrPreviousWindow(
              screenStateMonitor.getCurrentScreenState(),
              windowManager,
              isScreenRtl,
              currentWindow,
              navigationAction.searchDirection,
              /* shouldRestoreLastFocus= */ true,
              actorState.getFocusHistory(),
              AccessibilityWindowInfoUtils.FILTER_WINDOW_FOR_WINDOW_NAVIGATION,
              NavigationTarget.createNodeFilter(
                  NavigationTarget.TARGET_DEFAULT, speakingNodeCache));
      return (target != null) && setAccessibilityFocusInternal(target, navigationAction, eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(target);
    }
  }

  /**
   * Navigates to default target or native macro granularity target.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the {@code pivot}.
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
      AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      EventId eventId) {
    // Nodes and traversal strategy to be recycled.
    AccessibilityNodeInfoCompat webContainer = null;
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat target = null;
    TraversalStrategy traversalStrategy = null;

    try {

      int logicalDirection =
          TraversalStrategyUtils.getLogicalDirection(
              navigationAction.searchDirection, WindowManager.isScreenLayoutRTL(service));

      // Use different logic when navigating with default granularity on WebView elements.
      // If the current node has web content, attempt HTML navigation only in 2 conditions:
      // 1. If currently focused is not a web view container OR
      // 2. If currently focused is a web view container but the logical direction is forward.
      // Consider the following linear order when navigating between web
      // views and native views assuming that a web view is in between native elements:
      // Native elements -> web view container -> inside web view container -> native elements.
      // Web view container should be focused only in the above order.
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

      rootNode = AccessibilityNodeInfoUtils.getRoot(pivot);
      if (rootNode == null) {
        LogUtils.w(TAG, "Cannot perform navigation action: unable to find root node.");
        return false;
      }
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(rootNode, navigationAction.searchDirection);

      // Perform auto-scroll action if necessary.

      if (shouldAutoScroll(pivot, ignoreDescendantsOfPivot, navigationAction, traversalStrategy)
          && tryAutoScroll(pivot, navigationAction, eventId)) {
        return true;
      }

      // Search for target node within current window.
      Filter<AccessibilityNodeInfoCompat> nodeFilter =
          NavigationTarget.createNodeFilter(
              navigationAction.targetType, traversalStrategy.getSpeakingNodesCache());
      if (ignoreDescendantsOfPivot) {
        final AccessibilityNodeInfoCompat pivotCopy = AccessibilityNodeInfoUtils.obtain(pivot);
        nodeFilter =
            new Filter<AccessibilityNodeInfoCompat>() {
              @Override
              public boolean accept(AccessibilityNodeInfoCompat node) {
                return !AccessibilityNodeInfoUtils.hasAncestor(node, pivotCopy);
              }
            }.and(nodeFilter);
      }
      target =
          TraversalStrategyUtils.searchFocus(
              traversalStrategy, pivot, navigationAction.searchDirection, nodeFilter);

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

      // When the result of tryAutoScroll() is true but we receive no scrolled event, the navigation
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

      // Navigate across windows.

      if (target == null) {
        AccessibilityWindowInfo currentWindow =
            AccessibilityNodeInfoUtils.getWindow(pivot.unwrap());
        WindowManager windowManager = new WindowManager(service);
        boolean isScreenRtl = WindowManager.isScreenLayoutRTL(service);

        if (currentWindow == null) {
          // Ideally currentWindow should never be null. Do the null check in case of exception.
          LogUtils.w(TAG, "Cannot navigate across window: unable to identify current window");
          return false;
        }

        // Skip one swipe if it's the last element in the last window.
        if (!reachEdge
            && needPauseWhenTraverseAcrossWindow(
                windowManager, isScreenRtl, currentWindow, navigationAction.searchDirection)) {
          reachEdge = true;
          announceNoMoreWebOrNativeMacroElement(
              /* forward= */ (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD),
              navigationAction.targetType,
              eventId);
          return false;
        }

        // Do not navigate across windows if the focus is currently in system window(e.g. status
        // bar).
        if (isWindowNavigationSupported
            && AccessibilityWindowInfoUtils.FILTER_WINDOW_DIRECTIONAL_NAVIGATION.accept(
                currentWindow)) {
          Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache = new HashMap<>();
          target =
              searchTargetInNextOrPreviousWindow(
                  screenStateMonitor.getCurrentScreenState(),
                  windowManager,
                  isScreenRtl,
                  currentWindow,
                  navigationAction.searchDirection,
                  /* shouldRestoreLastFocus= */ false,
                  /* accessibilityFocusActionHistory= */ null,
                  AccessibilityWindowInfoUtils.FILTER_WINDOW_DIRECTIONAL_NAVIGATION,
                  NavigationTarget.createNodeFilter(
                      navigationAction.targetType, speakingNodeCache));
        }
      }

      // Try to wrap around inside current window, which is equivalent to find the initial focus
      // in current window with the given search direction.

      if ((target == null) && reachEdge && navigationAction.shouldWrap) {
        target =
            TraversalStrategyUtils.findInitialFocusInNodeTree(
                traversalStrategy,
                rootNode,
                navigationAction.searchDirection,
                NavigationTarget.createNodeFilter(
                    navigationAction.targetType, traversalStrategy.getSpeakingNodesCache()));
      }

      if (target != null) {
        if ((navigationAction.targetType == NavigationTarget.TARGET_DEFAULT)
            && TraversalStrategyUtils.isSpatialDirection(navigationAction.searchDirection)) {
          int focusDirection =
              TraversalStrategyUtils.nodeSearchDirectionToViewSearchDirection(
                  navigationAction.searchDirection);
          AccessibilityNodeInfoCompat focusSearchTarget = pivot.focusSearch(focusDirection);

          // TODO: Revisit the condition in which we use focusSearchTarget instead of
          // TalkBack navigation target.
          if ((focusSearchTarget != null)
              && !target.equals(focusSearchTarget)
              && target.isFocusable()
              && AccessibilityNodeInfoUtils.shouldFocusNode(focusSearchTarget)
              && AccessibilityNodeInfoUtils.supportsAction(
                  focusSearchTarget, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)) {
            LogUtils.d(TAG, "Using focusSearch() target instead of TalkBack navigation target.");
            AccessibilityNodeInfoUtils.recycleNodes(target);
            target = focusSearchTarget;
          } else {
            AccessibilityNodeInfoUtils.recycleNodes(focusSearchTarget);
          }
        }

        ensureOnScreen(
            target,
            navigationAction.shouldScroll,
            navigationAction.searchDirection,
            traversalStrategy,
            eventId);
        return setAccessibilityFocusInternal(target, navigationAction, eventId);
      }
      announceNoMoreWebOrNativeMacroElement(
          /* forward= */ (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD),
          navigationAction.targetType,
          eventId);
      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(webContainer, rootNode, target);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }

  /** Returns {@code true} if current window is the last window on screen in traversal order. */
  private static boolean needPauseWhenTraverseAcrossWindow(
      WindowManager windowManager,
      boolean isScreenRtl,
      AccessibilityWindowInfo currentWindow,
      @SearchDirection int searchDirection) {
    if (!AccessibilityWindowInfoUtils.FILTER_WINDOW_DIRECTIONAL_NAVIGATION.accept(currentWindow)) {
      // Need pause before looping traversal in non-application window
      return true;
    }
    @TraversalStrategy.SearchDirection
    int logicalDirection = TraversalStrategyUtils.getLogicalDirection(searchDirection, isScreenRtl);
    if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return windowManager.isLastWindow(currentWindow, AccessibilityWindowInfo.TYPE_APPLICATION);
    } else if (logicalDirection == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return windowManager.isFirstWindow(currentWindow, AccessibilityWindowInfo.TYPE_APPLICATION);
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
    AccessibilityNodeInfoCompat a11yFocusedNode = null;
    // Recycling of nodes varies with value of hasValidAccessibilityFocus and this boolean helps to
    // recycle the correct nodes.
    boolean recycleFocusedOrReferenceNodeParent = false;

    try {
      a11yFocusedNode =
          accessibilityFocusMonitor.getAccessibilityFocus(
              navigationAction.useInputFocusAsPivotIfEmpty);
      boolean hasValidAccessibilityFocus =
          (a11yFocusedNode != null) && AccessibilityNodeInfoUtils.isVisible(a11yFocusedNode);

      if (hasValidAccessibilityFocus) {
        referenceNode = a11yFocusedNode;
        focusedOrReferenceNodeParent =
            AccessibilityNodeInfoUtils.getMatchingAncestor(a11yFocusedNode, filterScrollable);
        recycleFocusedOrReferenceNodeParent = true;
      } else {
        // If a11y focus is not valid, we try to get the first child of the last scrolled container
        // to keep as a reference for scrolling. A visibility check is not required as it is just a
        // reference to start the scroll.
        referenceNode =
            getFirstOrLastChild(lastScrolledNodeForNativeMacroGranularity, /*firstChild= */ true);
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
      if (target == null) {
        if (tryAutoScroll(referenceNode, navigationAction, eventId)) {
          return true;
        }
      }
      return false;
    } finally {
      if (recycleFocusedOrReferenceNodeParent) {
        AccessibilityNodeInfoUtils.recycleNodes(a11yFocusedNode, focusedOrReferenceNodeParent);
      } else {
        AccessibilityNodeInfoUtils.recycleNodes(a11yFocusedNode);
      }
    }
  }

  /**
   * Returns the first or the last child.
   *
   * @param node The parent node whose first or last child is returned.
   * @param firstChild If {@code true} indicates first child, else last child.
   * @return First or last child of the {@code node}
   */
  private static AccessibilityNodeInfoCompat getFirstOrLastChild(
      @Nullable AccessibilityNodeInfoCompat node, boolean firstChild) {
    if (node != null && node.getChildCount() > 0) {
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
   *       shouldRestoreLastFocus} is set to {@code true}, and we only accept windows with {@link
   *       AccessibilityWindowInfo#TYPE_APPLICATION} and {@link
   *       AccessibilityWindowInfo#TYPE_SYSTEM}.
   *   <li>Users performs directional navigation across the first/last element of a window, in which
   *       case {@code shouldRestoreLastFocus} is set to {@code false}, and we only accept windows
   *       with {@link AccessibilityWindowInfo#TYPE_APPLICATION} and {@link
   *       AccessibilityWindowInfo#TYPE_SPLIT_SCREEN_DIVIDER}.
   * </ul>
   *
   * @param windowManager WindowManager used to iterate though sorted window list.
   * @param isScreenRtl Whether it's in RTL mode.
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
  @VisibleForTesting
  static AccessibilityNodeInfoCompat searchTargetInNextOrPreviousWindow(
      @Nullable ScreenState currentScreenState,
      WindowManager windowManager,
      boolean isScreenRtl,
      AccessibilityWindowInfo currentWindow,
      @TraversalStrategy.SearchDirection int direction,
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
      // Search for a target window.
      targetWindow =
          (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD)
              ? windowManager.getNextWindow(targetWindow)
              : windowManager.getPreviousWindow(targetWindow);
      if ((targetWindow == null) || currentWindow.equals(targetWindow)) {
        return null;
      }
      if (!windowFilter.accept(targetWindow)) {
        continue;
      }

      // Try to restore focus in the target window.
      if (shouldRestoreLastFocus) {
        final int windowId = targetWindow.getId();
        final CharSequence windowTitle =
            (currentScreenState == null) ? null : currentScreenState.getWindowTitle(windowId);
        FocusActionRecord record =
            accessibilityFocusActionHistory.getLastFocusActionRecordInWindow(windowId, windowTitle);
        AccessibilityNodeInfoCompat focusToRestore =
            (record == null) ? null : record.getFocusedNode();
        if ((focusToRestore != null) && focusToRestore.refresh()) {
          return focusToRestore;
        } else {
          AccessibilityNodeInfoUtils.recycleNodes(focusToRestore);
        }
      }

      // Search for the initial focusable node in the target window.
      AccessibilityNodeInfoCompat rootCompat = null;
      TraversalStrategy traversalStrategy = null;

      try {
        rootCompat = AccessibilityNodeInfoUtils.toCompat(targetWindow.getRoot());
        if (rootCompat != null) {
          traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(rootCompat, direction);

          AccessibilityNodeInfoCompat focus =
              TraversalStrategyUtils.findInitialFocusInNodeTree(
                  traversalStrategy, rootCompat, direction, nodeFilter);
          if (focus != null) {
            return focus;
          }
        }
      } finally {
        // Recycle nodes and traversal strategy.
        AccessibilityNodeInfoUtils.recycleNodes(rootCompat);
        TraversalStrategyUtils.recycle(traversalStrategy);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Logic related to accessibility focus action.

  private void ensureOnScreen(
      AccessibilityNodeInfoCompat node,
      boolean shouldScroll,
      @SearchDirection int searchDirection,
      TraversalStrategy traversalStrategy,
      EventId eventId) {
    boolean needToEnsureOnScreen =
        shouldScroll
            && (TraversalStrategyUtils.isAutoScrollEdgeListItem(
                    node, /* ignoreDescendantsOfPivot= */ false, searchDirection, traversalStrategy)
                || TraversalStrategyUtils.isSpatialDirection(searchDirection));
    if (needToEnsureOnScreen) {
      pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_SHOW_ON_SCREEN.getId()));
    }
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

  private boolean shouldAutoScroll(
      AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      NavigationAction navigationAction,
      TraversalStrategy traversalStrategy) {
    if (!navigationAction.shouldScroll) {
      return false;
    }
    return TraversalStrategyUtils.isAutoScrollEdgeListItem(
        pivot, ignoreDescendantsOfPivot, navigationAction.searchDirection, traversalStrategy);
  }

  /**
   * Returns scrollable node filter for given {@link NavigationAction}.
   *
   * <p>This is consistent with what we used in {@link
   * TraversalStrategyUtils#isAutoScrollEdgeListItem(AccessibilityNodeInfoCompat, boolean, int,
   * TraversalStrategy)}. It consists of {@link NodeActionFilter} to check supported scroll action,
   * and {@link AccessibilityNodeInfoUtils#FILTER_AUTO_SCROLL} to match white-listed {@link Role}.
   */
  @Nullable
  private static Filter<AccessibilityNodeInfoCompat> getScrollFilter(
      NavigationAction navigationAction) {
    final int scrollAction;
    switch (navigationAction.actionType) {
      case NavigationAction.SCROLL_FORWARD:
        scrollAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
        break;
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
        || (navigationAction.actionType == NavigationAction.SCROLL_BACKWARD)) {
      return new NodeActionFilter(scrollAction).and(SCROLLABLE_ROLE_FILTER_FOR_SCROLL_GESTURE);
    } else {
      return new NodeActionFilter(scrollAction)
          .and(SCROLLABLE_ROLE_FILTER_FOR_DIRECTION_NAVIGATION);
    }
  }

  /**
   * Attempts to scroll based on the specified {@link NavigationAction}.
   *
   * <p><strong>Note:</strong> Caller is responsible to recycle the pivot.
   */
  private boolean tryAutoScroll(
      AccessibilityNodeInfoCompat pivot, NavigationAction navigationAction, EventId eventId) {
    AccessibilityNodeInfoCompat scrollableNode = null;
    try {
      int scrollAction =
          TraversalStrategyUtils.convertSearchDirectionToScrollAction(
              navigationAction.searchDirection);

      Filter<AccessibilityNodeInfoCompat> nodeFilter = getScrollFilter(navigationAction);

      if (nodeFilter == null) {
        return false;
      }

      scrollableNode = AccessibilityNodeInfoUtils.getMatchingAncestor(pivot, nodeFilter);
      return (scrollableNode != null)
          && performScrollActionInternal(
              ScrollEventInterpreter.ACTION_AUTO_SCROLL,
              scrollableNode,
              pivot,
              scrollAction,
              navigationAction,
              eventId);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(scrollableNode);
    }
  }

  /**
   * <strong>Note: </strong> Caller is responsible to recycle {@code scrollableNode} and {@code
   * pivotNode}.
   */
  private boolean performScrollActionInternal(
      @UserAction int userAction,
      AccessibilityNodeInfoCompat scrollableNode,
      AccessibilityNodeInfoCompat pivotNode,
      int scrollAction,
      NavigationAction sourceAction,
      EventId eventId) {
    if ((sourceAction.actionType == NavigationAction.SCROLL_BACKWARD
            || sourceAction.actionType == NavigationAction.SCROLL_FORWARD)
        && !AccessibilityNodeInfoUtils.hasAncestor(pivotNode, scrollableNode)) {
      // Don't update a11y focus in callback if pivot is not a descendant of scrollable node.
      scrollCallback = null;
    } else {
      scrollCallback =
          new AutoScrollCallback(this, sourceAction, AccessibilityNodeInfoUtils.obtain(pivotNode));
    }
    return pipeline.returnFeedback(
        eventId, Feedback.scroll(scrollableNode, userAction, scrollAction, Source.FOCUS));
  }

  /** Determines feedback for auto-scroll success after directional-navigation action. */
  public void onAutoScrolled(AccessibilityNodeInfoCompat scrolledNode, EventId eventId) {
    if (scrollCallback != null) {
      scrollCallback.onAutoScrolled(scrolledNode, eventId);
      scrollCallback = null;
    }
  }

  /** Determines feedback for auto-scroll failure after directional-navigation action. */
  public void onAutoScrollFailed(AccessibilityNodeInfoCompat scrolledNode) {
    if (scrollCallback != null) {
      scrollCallback.onAutoScrollFailed(scrolledNode);
      scrollCallback = null;
    }
  }

  private void handleViewScrolledForScrollNavigationAction(
      AccessibilityNodeInfoCompat scrolledNode, NavigationAction sourceAction, EventId eventId) {
    AccessibilityNodeInfoCompat currentFocus = null;
    AccessibilityNodeInfoCompat nodeToFocus = null;
    TraversalStrategy traversalStrategy = null;
    try {
      currentFocus =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      boolean hasValidA11yFocus = AccessibilityNodeInfoUtils.isVisible(currentFocus);
      if (hasValidA11yFocus
          && !AccessibilityNodeInfoUtils.hasAncestor(currentFocus, scrolledNode)) {
        // Do nothing if there is a valid focus outside of the scrolled container.
        return;
      }
      // 1. Visible, inside scrolledNode
      // 2. Invisible, no focus.
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(
              scrolledNode, TraversalStrategy.SEARCH_FOCUS_FORWARD);
      // Use TARGET_DEFAULT regardless of the current granularity.
      Filter<AccessibilityNodeInfoCompat> nodeFilter =
          NavigationTarget.createNodeFilter(
              NavigationTarget.TARGET_DEFAULT, traversalStrategy.getSpeakingNodesCache());
      @SearchDirection
      int direction =
          (sourceAction.actionType == NavigationAction.SCROLL_FORWARD)
              ? TraversalStrategy.SEARCH_FOCUS_FORWARD
              : TraversalStrategy.SEARCH_FOCUS_BACKWARD;
      if (hasValidA11yFocus) {
        nodeToFocus =
            TraversalStrategyUtils.searchFocus(
                traversalStrategy, currentFocus, direction, nodeFilter);
      } else {
        nodeToFocus =
            TraversalStrategyUtils.findInitialFocusInNodeTree(
                traversalStrategy, scrolledNode, direction, nodeFilter);
      }
      if (nodeToFocus != null) {
        setAccessibilityFocusInternal(nodeToFocus, sourceAction, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus, nodeToFocus);
      TraversalStrategyUtils.recycle(traversalStrategy);
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
      AccessibilityNodeInfoCompat scrolledNode, NavigationAction sourceAction, EventId eventId) {
    // Nodes to be recycled.
    AccessibilityNodeInfoCompat nodeToFocus = null;
    // Local TraversalStrategy generated in sub-tree of scrolledNode.
    TraversalStrategy localTraversalStrategy = null;
    try {
      localTraversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(scrolledNode, sourceAction.searchDirection);
      Filter<AccessibilityNodeInfoCompat> nodeFilter =
          NavigationTarget.createNodeFilter(
              sourceAction.targetType, localTraversalStrategy.getSpeakingNodesCache());

      NavigationAction navigationAction =
          NavigationAction.Builder.copy(sourceAction)
              .setAutoScrollAttempt(sourceAction.autoScrollAttempt + 1)
              .build();

      int logicalDirection =
          TraversalStrategyUtils.getLogicalDirection(
              navigationAction.searchDirection, WindowManager.isScreenLayoutRTL(service));

      // Try to find the next focusable node based on current focus.
      // For native macro granularity, we try to find the reference node to start the search from.
      // This workaround is required due to http://, else we can use focusBeforeScroll
      // as the start node even for macro granularity.
      // TODO: Remove this workaround after http:// is fixed.
      AccessibilityNodeInfoCompat refNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      if (!AccessibilityNodeInfoUtils.isVisible(refNode)) {
        refNode = null;
      }
      if ((refNode == null)) {
        // First child if direction is forward, else last child.
        boolean firstChild = (logicalDirection == TraversalStrategy.SEARCH_FOCUS_FORWARD);
        refNode = getFirstOrLastChild(scrolledNode, firstChild);
      }

      // Only if the refNode does not satisfy the desired macro granularity target type or is
      // default granularity, we look for the next target starting from
      // refNode. Else, just set the focus to refNode.
      if (nodeFilter.accept(refNode) && !refNode.isAccessibilityFocused()) {
        nodeToFocus = refNode;
      } else {
        nodeToFocus =
            TraversalStrategyUtils.searchFocus(
                localTraversalStrategy, refNode, sourceAction.searchDirection, nodeFilter);

        if (nodeToFocus == null) {
          setLastScrolledNodeForNativeMacroGranularity(scrolledNode);
          // Since there is no visible/valid accessibility focus on screen, we play safe and don't
          // repeat navigation action without a valid pivot node.
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
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToFocus);
      TraversalStrategyUtils.recycle(localTraversalStrategy);
    }
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
      AccessibilityNodeInfoCompat scrolledNode,
      AccessibilityNodeInfoCompat focusBeforeScroll,
      NavigationAction sourceAction,
      EventId eventId) {
    // Nodes to be recycled.
    AccessibilityNodeInfoCompat nodeToFocus = null;
    // Local TraversalStrategy generated in sub-tree of scrolledNode.
    TraversalStrategy localTraversalStrategy = null;
    try {
      localTraversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(scrolledNode, sourceAction.searchDirection);
      Filter<AccessibilityNodeInfoCompat> nodeFilter =
          NavigationTarget.createNodeFilter(
              sourceAction.targetType, localTraversalStrategy.getSpeakingNodesCache());
      boolean validAccessibilityFocus =
          focusBeforeScroll.refresh() && AccessibilityNodeInfoUtils.isVisible(focusBeforeScroll);

      NavigationAction navigationAction =
          NavigationAction.Builder.copy(sourceAction)
              .setAutoScrollAttempt(sourceAction.autoScrollAttempt + 1)
              .build();

      if (validAccessibilityFocus) {
        // Try to find the next focusable node based on current focus.
        nodeToFocus =
            TraversalStrategyUtils.searchFocus(
                localTraversalStrategy,
                focusBeforeScroll,
                sourceAction.searchDirection,
                nodeFilter);
        if (nodeToFocus == null) {
          // If no more item is exposed, repeat navigation action.
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
                localTraversalStrategy,
                focusBeforeScroll,
                sourceAction.searchDirection,
                nodeFilter);
        // Fallback solution: Use the first/last item under scrollable node as the target.
        if (nodeToFocus == null) {
          nodeToFocus =
              TraversalStrategyUtils.findInitialFocusInNodeTree(
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
              navigationAction.searchDirection, WindowManager.isScreenLayoutRTL(service));
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
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToFocus);
      TraversalStrategyUtils.recycle(localTraversalStrategy);
    }
  }

  private void setLastScrolledNodeForNativeMacroGranularity(
      AccessibilityNodeInfoCompat scrolledNode) {
    AccessibilityNodeInfoUtils.recycleNodes(lastScrolledNodeForNativeMacroGranularity);
    lastScrolledNodeForNativeMacroGranularity = AccessibilityNodeInfoCompat.obtain(scrolledNode);
  }

  public void resetLastScrolledNodeForNativeMacroGranularity() {
    AccessibilityNodeInfoUtils.recycleNodes(lastScrolledNodeForNativeMacroGranularity);
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
      AccessibilityNodeInfoCompat nodeToScroll, NavigationAction sourceAction) {
    // When auto-scroll fails, we don't search down the scrolled container, instead, we jump out of
    // it searching for the next target. Thus we use 'nodeToScroll' as the pivot and
    // 'ignoreDescendantsOfPivot' is set to TRUE.
    onDirectionalNavigationAction(
        /* pivot= */ nodeToScroll,
        /* ignoreDescendantsOfPivot= */ true,
        sourceAction,
        /* eventId= */ null);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to make announcement.
  // TODO: Think about moving this into Compositor.

  private void announceKeyboardShortcutNotSupported(EventId eventId) {
    announce(service.getString(R.string.keycombo_announce_shortcut_not_supported), eventId);
  }

  /**
   * Announces if there are no more elements while navigating web or using macro native granularity.
   */
  private void announceNoMoreWebOrNativeMacroElement(
      boolean forward, @TargetType int htmlOrMacroTargetType, EventId eventId) {
    int resId = forward ? R.string.end_of_page : R.string.start_of_page;

    String target = null;
    try {
      if (NavigationTarget.isHtmlTarget(htmlOrMacroTargetType)) {
        target =
            NavigationTarget.htmlTargetToDisplayName(/* context= */ service, htmlOrMacroTargetType);
      } else if (NavigationTarget.isMacroGranularity(htmlOrMacroTargetType)) {
        target =
            NavigationTarget.macroTargetToDisplayName(
                /* context= */ service, htmlOrMacroTargetType);
      } else {
        // Incase of any other target type, make no announcement.
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
            .setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT)
            .setFlags(FeedbackItem.FLAG_FORCED_FEEDBACK);
    pipeline.returnFeedback(eventId, Feedback.speech(text, speakOptions));
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

    AutoScrollCallback(
        FocusProcessorForLogicalNavigation parent,
        NavigationAction sourceAction,
        AccessibilityNodeInfoCompat pivot) {
      this.parent = parent;
      this.sourceAction = sourceAction;
      this.pivot = pivot;
    }

    public void onAutoScrolled(AccessibilityNodeInfoCompat scrolledNode, EventId eventId) {
      switch (sourceAction.actionType) {
        case NavigationAction.DIRECTIONAL_NAVIGATION:
          if (sourceAction.targetType == NavigationTarget.TARGET_DEFAULT) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithDefaultTarget(
                scrolledNode, pivot, sourceAction, eventId);
          } else if (NavigationTarget.isMacroGranularity(sourceAction.targetType)) {
            parent.handleViewAutoScrolledForDirectionalNavigationWithMacroGranularityTarget(
                scrolledNode, sourceAction, eventId);
          }
          break;
        case NavigationAction.SCROLL_FORWARD:
          // fall through
        case NavigationAction.SCROLL_BACKWARD:
          parent.handleViewScrolledForScrollNavigationAction(scrolledNode, sourceAction, eventId);
          break;
        default:
          break;
      }
      clear();
    }

    public void onAutoScrollFailed(AccessibilityNodeInfoCompat nodeToScroll) {
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

    /** Recycles cached nodes. */
    private void clear() {
      AccessibilityNodeInfoUtils.recycleNodes(pivot);
    }
  }
}
