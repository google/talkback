package com.google.android.accessibility.utils.traversal;

import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.SEARCH_FOCUS_FAIL;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_LEFT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_RIGHT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.ScrollableNodeInfo;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TraversalStrategyUtils {

  private static final String TAG = "TraversalStrategyUtils";

  private TraversalStrategyUtils() {
    // Prevent utility class from being instantiated.
  }

  /**
   * Recycles the given traversal strategy.
   *
   * @deprecated Accessibility is discontinuing recycling.
   */
  @Deprecated
  public static void recycle(@Nullable TraversalStrategy traversalStrategy) {}

  /**
   * Depending on whether the direction is spatial or logical, returns the appropriate traversal
   * strategy to handle the case.
   */
  public static TraversalStrategy getTraversalStrategy(
      AccessibilityNodeInfoCompat root,
      FocusFinder focusFinder,
      @TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case SEARCH_FOCUS_BACKWARD:
      case SEARCH_FOCUS_FORWARD:
        return new OrderedTraversalStrategy(root);
      case SEARCH_FOCUS_LEFT:
      case SEARCH_FOCUS_RIGHT:
      case SEARCH_FOCUS_UP:
      case SEARCH_FOCUS_DOWN:
        return new DirectionalTraversalStrategy(root, focusFinder);
      default: // fall out
    }

    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /** Converts {@link TraversalStrategy.SearchDirection} to view focus direction. */
  public static int nodeSearchDirectionToViewSearchDirection(
      @TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case SEARCH_FOCUS_FORWARD:
        return View.FOCUS_FORWARD;
      case SEARCH_FOCUS_BACKWARD:
        return View.FOCUS_BACKWARD;
      case SEARCH_FOCUS_LEFT:
        return View.FOCUS_LEFT;
      case SEARCH_FOCUS_RIGHT:
        return View.FOCUS_RIGHT;
      case SEARCH_FOCUS_UP:
        return View.FOCUS_UP;
      case SEARCH_FOCUS_DOWN:
        return View.FOCUS_DOWN;
      default:
        throw new IllegalArgumentException("Direction must be a SearchDirection");
    }
  }

  /**
   * Determines whether the given search direction corresponds to an actual spatial direction as
   * opposed to a logical direction.
   */
  public static boolean isSpatialDirection(@TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case SEARCH_FOCUS_FORWARD:
      case SEARCH_FOCUS_BACKWARD:
        return false;
      case SEARCH_FOCUS_UP:
      case SEARCH_FOCUS_DOWN:
      case SEARCH_FOCUS_LEFT:
      case SEARCH_FOCUS_RIGHT:
        return true;
      default: // fall out
    }

    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /** Returns {@code true} if {@code searchDirection} is logical (forward or backward). */
  public static boolean isLogicalDirection(@TraversalStrategy.SearchDirection int direction) {
    return direction == SEARCH_FOCUS_FORWARD || direction == SEARCH_FOCUS_BACKWARD;
  }

  /**
   * Converts a spatial direction to a logical direction based on whether the user is LTR or RTL. If
   * the direction is already a logical direction, it is returned.
   */
  @TraversalStrategy.SearchDirection
  public static int getLogicalDirection(
      @TraversalStrategy.SearchDirection int direction, boolean isRtl) {
    @TraversalStrategy.SearchDirection int left;
    @TraversalStrategy.SearchDirection int right;
    if (isRtl) {
      left = SEARCH_FOCUS_FORWARD;
      right = SEARCH_FOCUS_BACKWARD;
    } else {
      left = SEARCH_FOCUS_BACKWARD;
      right = SEARCH_FOCUS_FORWARD;
    }

    switch (direction) {
      case SEARCH_FOCUS_LEFT:
        return left;
      case SEARCH_FOCUS_RIGHT:
        return right;
      case SEARCH_FOCUS_UP:
      case SEARCH_FOCUS_BACKWARD:
        return SEARCH_FOCUS_BACKWARD;
      case SEARCH_FOCUS_DOWN:
      case SEARCH_FOCUS_FORWARD:
        return SEARCH_FOCUS_FORWARD;
      default: // fall out
    }

    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /**
   * Returns the scroll action for the given {@link TraversalStrategy.SearchDirection} if the scroll
   * action is available on the current SDK version. Otherwise, returns 0.
   */
  public static int convertSearchDirectionToScrollAction(
      @TraversalStrategy.SearchDirection int direction) {
    if (direction == SEARCH_FOCUS_FORWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
    } else if (direction == SEARCH_FOCUS_BACKWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
    } else {
      if (direction == SEARCH_FOCUS_LEFT) {
        return AccessibilityAction.ACTION_SCROLL_LEFT.getId();
      } else if (direction == SEARCH_FOCUS_RIGHT) {
        return AccessibilityAction.ACTION_SCROLL_RIGHT.getId();
      } else if (direction == SEARCH_FOCUS_UP) {
        return AccessibilityAction.ACTION_SCROLL_UP.getId();
      } else if (direction == SEARCH_FOCUS_DOWN) {
        return AccessibilityAction.ACTION_SCROLL_DOWN.getId();
      }
    }

    return 0;
  }

  /**
   * Returns the {@link TraversalStrategy.SearchDirectionOrUnknown} for the given scroll action;
   * {@link TraversalStrategy#SEARCH_FOCUS_UNKNOWN} is returned for a scroll action that can't be
   * handled (e.g. because the current API level doesn't support it).
   */
  @TraversalStrategy.SearchDirectionOrUnknown
  public static int convertScrollActionToSearchDirection(int scrollAction) {
    if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
      return SEARCH_FOCUS_FORWARD;
    } else if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      return SEARCH_FOCUS_BACKWARD;
    } else {
      if (scrollAction == AccessibilityAction.ACTION_SCROLL_LEFT.getId()) {
        return SEARCH_FOCUS_LEFT;
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_RIGHT.getId()) {
        return SEARCH_FOCUS_RIGHT;
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_UP.getId()) {
        return SEARCH_FOCUS_UP;
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_DOWN.getId()) {
        return SEARCH_FOCUS_DOWN;
      }
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  /**
   * Convenience method determining if the current item is at the edge of a scrollable view and
   * suitable autoscroll. Calls {@code isEdgeListItem} with {@code FILTER_AUTO_SCROLL}.
   *
   * @param pivot The node to check.
   * @param scrollableNodeInfo The info about the scrollable container for which is checked if the
   *     pivot is at the edge or not.
   * @param ignoreDescendantsOfPivot Whether to ignore descendants of pivot when search down the
   *     node tree.
   * @param searchDirection The direction in which to check.
   * @return true if the current item is at the edge of a list.
   */
  public static boolean isAutoScrollEdgeListItem(
      AccessibilityNodeInfoCompat pivot,
      @NonNull ScrollableNodeInfo scrollableNodeInfo,
      boolean ignoreDescendantsOfPivot,
      @TraversalStrategy.SearchDirection int searchDirection,
      FocusFinder focusFinder) {

    Integer supportedDirection = scrollableNodeInfo.getSupportedScrollDirection(searchDirection);
    if (supportedDirection == null) {
      return false;
    }

    TraversalStrategy traversalStrategy =
        scrollableNodeInfo.getSupportedTraversalStrategy(supportedDirection, focusFinder);
    return isMatchingEdgeListItem(
        pivot,
        scrollableNodeInfo.getNode(),
        ignoreDescendantsOfPivot,
        supportedDirection,
        AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL,
        traversalStrategy);
  }

  /**
   * Utility method for determining if a searching past a particular node will fall off the edge of
   * a scrollable container.
   *
   * @param cursor Node to check.
   * @param scrollableNode The scrollable container that for checking the cursor is at the edge or
   *     not.
   * @param ignoreDescendantsOfCursor Whether to ignore descendants of cursor when search down the
   *     node tree.
   * @param direction The direction in which to move from the cursor.
   * @param filter Filter used to validate list-type ancestors.
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return {@code true} if focusing search in the specified direction will fall off the edge of
   *     the container.
   */
  private static boolean isMatchingEdgeListItem(
      final AccessibilityNodeInfoCompat cursor,
      final @NonNull AccessibilityNodeInfoCompat scrollableNode,
      boolean ignoreDescendantsOfCursor,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> filter,
      TraversalStrategy traversalStrategy) {
    AccessibilityNodeInfoCompat webViewNode = null;

    boolean cursorNodeNotContainedInScrollableList =
        !scrollableNode.isScrollable()
            || !(AccessibilityNodeInfoUtils.hasAncestor(cursor, scrollableNode)
                || scrollableNode.equals(cursor));

    if (cursorNodeNotContainedInScrollableList) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> focusNodeFilter =
        AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS;
    if (ignoreDescendantsOfCursor) {
      focusNodeFilter =
          focusNodeFilter.and(
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat obj) {
                  return !AccessibilityNodeInfoUtils.hasAncestor(obj, cursor);
                }
              });
    }
    AccessibilityNodeInfoCompat nextFocusNode =
        searchFocus(traversalStrategy, cursor, direction, focusNodeFilter);

    if ((nextFocusNode == null) || nextFocusNode.equals(scrollableNode)) {
      // Can't move from this position.
      return true;
    }

    // if nextFocusNode is in WebView and not visible to user we still could set
    // accessibility  focus on it and WebView scrolls itself to show newly focused item
    // on the screen. But there could be situation that node is inside WebView bounds but
    // WebView is [partially] outside the screen bounds. In that case we don't ask WebView
    // to set accessibility focus but try to scroll scrollable parent to get the WebView
    // with nextFocusNode inside it to the screen bounds.
    if (!nextFocusNode.isVisibleToUser() && WebInterfaceUtils.hasNativeWebContent(nextFocusNode)) {
      webViewNode =
          AccessibilityNodeInfoUtils.getMatchingAncestor(
              nextFocusNode,
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return Role.getRole(node) == Role.ROLE_WEB_VIEW;
                }
              });

      if (webViewNode != null
          && (!webViewNode.isVisibleToUser()
              || isNodeInBoundsOfOther(webViewNode, nextFocusNode))) {
        return true;
      }
    }

    AccessibilityNodeInfoCompat searchedAncestor =
        AccessibilityNodeInfoUtils.getMatchingAncestor(nextFocusNode, filter);
    while (searchedAncestor != null) {
      if (scrollableNode.equals(searchedAncestor)) {
        return false;
      }
      searchedAncestor = AccessibilityNodeInfoUtils.getMatchingAncestor(searchedAncestor, filter);
    }
    // Moves outside of the scrollable container.
    return true;
  }

  /**
   * Search focus that satisfied specified node filter from currentFocus to specified direction
   * according to OrderTraversal strategy
   *
   * @param traversal - order traversal strategy
   * @param currentFocus - node that is starting point of focus search
   * @param direction - direction the target focus is searching to
   * @param filter - filters focused node candidate
   * @return node that could be focused next
   */
  public static @Nullable AccessibilityNodeInfoCompat searchFocus(
      TraversalStrategy traversal,
      AccessibilityNodeInfoCompat currentFocus,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> filter) {
    if (traversal == null || currentFocus == null) {
      return null;
    }

    if (filter == null) {
      filter = DEFAULT_FILTER;
    }

    AccessibilityNodeInfoCompat targetNode = currentFocus;
    Set<AccessibilityNodeInfoCompat> seenNodes = new HashSet<>();

    do {
      seenNodes.add(targetNode);
      targetNode = traversal.findFocus(targetNode, direction);
      DiagnosticOverlayUtils.appendLog(SEARCH_FOCUS_FAIL, targetNode);

      if (seenNodes.contains(targetNode)) {
        LogUtils.e(TAG, "Found duplicate during traversal: %s", targetNode);
        return null;
      }
    } while (targetNode != null && !filter.accept(targetNode));

    return targetNode;
  }

  /**
   * Finds the first focusable accessibility node in hierarchy started from root node when searching
   * in the given direction.
   *
   * <p>For example, if {@code direction} is {@link TraversalStrategy#SEARCH_FOCUS_FORWARD}, then
   * the method should return the first node in the traversal order. If {@code direction} is {@link
   * TraversalStrategy#SEARCH_FOCUS_BACKWARD} then the method should return the last node in the
   * traversal order.
   *
   * @param traversalStrategy the traversal strategy
   * @param root the root node
   * @param direction the direction to search from
   * @param nodeFilter the {@link Filter} to determine which nodes to focus
   * @return returns the first node that matches nodeFilter
   */
  public static @Nullable AccessibilityNodeInfoCompat findFirstFocusInNodeTree(
      TraversalStrategy traversalStrategy,
      AccessibilityNodeInfoCompat root,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter) {
    if (root == null) {
      return null;
    }
    AccessibilityNodeInfoCompat firstNode = traversalStrategy.focusFirst(root, direction);

    if (nodeFilter.accept(firstNode)) {
      return firstNode;
    }
    return TraversalStrategyUtils.searchFocus(traversalStrategy, firstNode, direction, nodeFilter);
  }

  private static boolean isNodeInBoundsOfOther(
      AccessibilityNodeInfoCompat outerNode, AccessibilityNodeInfoCompat innerNode) {
    if (outerNode == null || innerNode == null) {
      return false;
    }

    Rect outerRect = new Rect();
    Rect innerRect = new Rect();
    outerNode.getBoundsInScreen(outerRect);
    innerNode.getBoundsInScreen(innerRect);

    if (outerRect.top > innerRect.bottom || outerRect.bottom < innerRect.top) {
      return false;
    }

    //noinspection RedundantIfStatement
    if (outerRect.left > innerRect.right || outerRect.right < innerRect.left) {
      return false;
    }

    return true;
  }

  private static final Filter<AccessibilityNodeInfoCompat> DEFAULT_FILTER =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null;
        }
      };

  public static String directionToString(
      @TraversalStrategy.SearchDirectionOrUnknown int direction) {
    switch (direction) {
      case SEARCH_FOCUS_FORWARD:
        return "SEARCH_FOCUS_FORWARD";
      case SEARCH_FOCUS_BACKWARD:
        return "SEARCH_FOCUS_BACKWARD";
      case SEARCH_FOCUS_LEFT:
        return "SEARCH_FOCUS_LEFT";
      case SEARCH_FOCUS_RIGHT:
        return "SEARCH_FOCUS_RIGHT";
      case SEARCH_FOCUS_UP:
        return "SEARCH_FOCUS_UP";
      case SEARCH_FOCUS_DOWN:
        return "SEARCH_FOCUS_DOWN";
      case TraversalStrategy.SEARCH_FOCUS_UNKNOWN:
        return "SEARCH_FOCUS_UNKNOWN";
      default:
        return "(unhandled)";
    }
  }
}
