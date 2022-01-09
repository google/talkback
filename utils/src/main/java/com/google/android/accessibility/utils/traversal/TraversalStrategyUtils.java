package com.google.android.accessibility.utils.traversal;

import static com.google.android.accessibility.utils.output.DiagnosticOverlayUtils.SEARCH_FOCUS_FAIL;

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.NodeActionFilter;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.output.DiagnosticOverlayUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TraversalStrategyUtils {

  private static final String TAG = "TraversalStrategyUtils";

  private TraversalStrategyUtils() {
    // Prevent utility class from being instantiated.
  }

  /** Recycles the given traversal strategy. */
  public static void recycle(@Nullable TraversalStrategy traversalStrategy) {
    if (traversalStrategy != null) {
      traversalStrategy.recycle();
    }
  }

  /**
   * Depending on whether the direction is spatial or logical, returns the appropriate traversal
   * strategy to handle the case.
   */
  public static TraversalStrategy getTraversalStrategy(
      AccessibilityNodeInfoCompat root,
      FocusFinder focusFinder,
      @TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        return new OrderedTraversalStrategy(root);
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
      case TraversalStrategy.SEARCH_FOCUS_UP:
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return new DirectionalTraversalStrategy(root, focusFinder);
      default: // fall out
    }

    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /** Converts {@link TraversalStrategy.SearchDirection} to view focus direction. */
  public static int nodeSearchDirectionToViewSearchDirection(
      @TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        return View.FOCUS_FORWARD;
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        return View.FOCUS_BACKWARD;
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return View.FOCUS_LEFT;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return View.FOCUS_RIGHT;
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return View.FOCUS_UP;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
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
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        return false;
      case TraversalStrategy.SEARCH_FOCUS_UP:
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return true;
      default: // fall out
    }

    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /**
   * Converts a spatial direction to a logical direction based on whether the user is LTR or RTL. If
   * the direction is already a logical direction, it is returned.
   */
  public static @TraversalStrategy.SearchDirection int getLogicalDirection(
      @TraversalStrategy.SearchDirection int direction, boolean isRtl) {
    @TraversalStrategy.SearchDirection int left;
    @TraversalStrategy.SearchDirection int right;
    if (isRtl) {
      left = TraversalStrategy.SEARCH_FOCUS_FORWARD;
      right = TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    } else {
      left = TraversalStrategy.SEARCH_FOCUS_BACKWARD;
      right = TraversalStrategy.SEARCH_FOCUS_FORWARD;
    }

    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return left;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return right;
      case TraversalStrategy.SEARCH_FOCUS_UP:
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        return TraversalStrategy.SEARCH_FOCUS_FORWARD;
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
    if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
    } else {
      if (direction == TraversalStrategy.SEARCH_FOCUS_LEFT) {
        return AccessibilityAction.ACTION_SCROLL_LEFT.getId();
      } else if (direction == TraversalStrategy.SEARCH_FOCUS_RIGHT) {
        return AccessibilityAction.ACTION_SCROLL_RIGHT.getId();
      } else if (direction == TraversalStrategy.SEARCH_FOCUS_UP) {
        return AccessibilityAction.ACTION_SCROLL_UP.getId();
      } else if (direction == TraversalStrategy.SEARCH_FOCUS_DOWN) {
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
  public static @TraversalStrategy.SearchDirectionOrUnknown int
      convertScrollActionToSearchDirection(int scrollAction) {
    if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    } else {
      if (scrollAction == AccessibilityAction.ACTION_SCROLL_LEFT.getId()) {
        return TraversalStrategy.SEARCH_FOCUS_LEFT;
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_RIGHT.getId()) {
        return TraversalStrategy.SEARCH_FOCUS_RIGHT;
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_UP.getId()) {
        return TraversalStrategy.SEARCH_FOCUS_UP;
      } else if (scrollAction == AccessibilityAction.ACTION_SCROLL_DOWN.getId()) {
        return TraversalStrategy.SEARCH_FOCUS_DOWN;
      }
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  /**
   * Determines if the current item is at the logical edge of a list by checking the scrollable
   * predecessors of the items going forwards and backwards.
   *
   * @param node The node to check.
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return true if the current item is at the edge of a list.
   */
  public static boolean isEdgeListItem(
      AccessibilityNodeInfoCompat node, TraversalStrategy traversalStrategy) {
    return isEdgeListItem(
            node,
            /* ignoreDescendantsOfPivot= */ false,
            TraversalStrategy.SEARCH_FOCUS_BACKWARD,
            null,
            traversalStrategy)
        || isEdgeListItem(
            node,
            /* ignoreDescendantsOfPivot= */ false,
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            null,
            traversalStrategy);
  }

  /**
   * Determines if the current item is at the edge of a list by checking the scrollable predecessors
   * of the items in a relative or absolute direction.
   *
   * @param pivot The node to check.
   * @param ignoreDescendantsOfPivot Whether to ignore descendants of pivot when searching down the
   *     node tree.
   * @param direction The direction in which to check.
   * @param filter (Optional) Filter used to validate list-type ancestors.
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return true if the current item is at the edge of a list.
   */
  private static boolean isEdgeListItem(
      AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      @TraversalStrategy.SearchDirection int direction,
      @Nullable Filter<AccessibilityNodeInfoCompat> filter,
      TraversalStrategy traversalStrategy) {
    if (pivot == null) {
      return false;
    }

    int scrollAction = TraversalStrategyUtils.convertSearchDirectionToScrollAction(direction);
    if (scrollAction != 0) {
      NodeActionFilter scrollableFilter = new NodeActionFilter(scrollAction);
      Filter<AccessibilityNodeInfoCompat> comboFilter = scrollableFilter.and(filter);
      return isMatchingEdgeListItem(
          pivot, ignoreDescendantsOfPivot, direction, comboFilter, traversalStrategy);
    }

    return false;
  }

  /**
   * Convenience method determining if the current item is at the edge of a list and suitable
   * autoscroll. Calls {@code isEdgeListItem} with {@code FILTER_AUTO_SCROLL}.
   *
   * @param pivot The node to check.
   * @param ignoreDescendantsOfPivot Whether to ignore descendants of pivot when search down the
   *     node tree.
   * @param direction The direction in which to check, one of:
   *     <ul>
   *       <li>{@code -1} to check backward
   *       <li>{@code 0} to check both backward and forward
   *       <li>{@code 1} to check forward
   *     </ul>
   *
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return true if the current item is at the edge of a list.
   */
  public static boolean isAutoScrollEdgeListItem(
      AccessibilityNodeInfoCompat pivot,
      boolean ignoreDescendantsOfPivot,
      int direction,
      TraversalStrategy traversalStrategy) {
    return isEdgeListItem(
        pivot,
        ignoreDescendantsOfPivot,
        direction,
        AccessibilityNodeInfoUtils.FILTER_AUTO_SCROLL,
        traversalStrategy);
  }

  /**
   * Utility method for determining if a searching past a particular node will fall off the edge of
   * a scrollable container.
   *
   * @param cursor Node to check.
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
      boolean ignoreDescendantsOfCursor,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> filter,
      TraversalStrategy traversalStrategy) {
    AccessibilityNodeInfoCompat ancestor = null;
    AccessibilityNodeInfoCompat nextFocusNode = null;
    AccessibilityNodeInfoCompat searchedAncestor = null;
    AccessibilityNodeInfoCompat webViewNode = null;

    try {
      ancestor = AccessibilityNodeInfoUtils.getMatchingAncestor(cursor, filter);
      if (ancestor == null) {
        // Not contained in a scrollable list.
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
      nextFocusNode = searchFocus(traversalStrategy, cursor, direction, focusNodeFilter);
      if ((nextFocusNode == null) || nextFocusNode.equals(ancestor)) {
        // Can't move from this position.
        return true;
      }

      // if nextFocusNode is in WebView and not visible to user we still could set
      // accessibility  focus on it and WebView scrolls itself to show newly focused item
      // on the screen. But there could be situation that node is inside WebView bounds but
      // WebView is [partially] outside the screen bounds. In that case we don't ask WebView
      // to set accessibility focus but try to scroll scrollable parent to get the WebView
      // with nextFocusNode inside it to the screen bounds.
      if (!nextFocusNode.isVisibleToUser()
          && WebInterfaceUtils.hasNativeWebContent(nextFocusNode)) {
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

      searchedAncestor = AccessibilityNodeInfoUtils.getMatchingAncestor(nextFocusNode, filter);
      while (searchedAncestor != null) {
        if (ancestor.equals(searchedAncestor)) {
          return false;
        }
        AccessibilityNodeInfoCompat temp = searchedAncestor;
        searchedAncestor = AccessibilityNodeInfoUtils.getMatchingAncestor(searchedAncestor, filter);
        temp.recycle();
      }
      // Moves outside of the scrollable container.
      return true;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(
          ancestor, nextFocusNode, searchedAncestor, webViewNode);
    }
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

    AccessibilityNodeInfoCompat targetNode = AccessibilityNodeInfoCompat.obtain(currentFocus);
    Set<AccessibilityNodeInfoCompat> seenNodes = new HashSet<>();
    try {
      do {
        seenNodes.add(targetNode);
        targetNode = traversal.findFocus(targetNode, direction);
        DiagnosticOverlayUtils.appendLog(SEARCH_FOCUS_FAIL, targetNode);

        if (seenNodes.contains(targetNode)) {
          LogUtils.e(TAG, "Found duplicate during traversal: %s", targetNode);
          return null;
        }
      } while (targetNode != null && !filter.accept(targetNode));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(seenNodes);
    }

    return targetNode;
  }

  @Nullable
  public static AccessibilityNodeInfoCompat findInitialFocusInNodeTree(
      TraversalStrategy traversalStrategy,
      AccessibilityNodeInfoCompat root,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> nodeFilter) {
    if (root == null) {
      return null;
    }
    AccessibilityNodeInfoCompat initialNode = null;
    try {
      initialNode = traversalStrategy.focusInitial(root, direction);

      if (nodeFilter.accept(initialNode)) {
        return AccessibilityNodeInfoUtils.obtain(initialNode);
      }
      return TraversalStrategyUtils.searchFocus(
          traversalStrategy, initialNode, direction, nodeFilter);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(initialNode);
    }
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

  public static String directionToString(@TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        return "SEARCH_FOCUS_FORWARD";
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
        return "SEARCH_FOCUS_BACKWARD";
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return "SEARCH_FOCUS_LEFT";
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return "SEARCH_FOCUS_RIGHT";
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return "SEARCH_FOCUS_UP";
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return "SEARCH_FOCUS_DOWN";
      case TraversalStrategy.SEARCH_FOCUS_UNKNOWN:
        return "SEARCH_FOCUS_UNKNOWN";
      default:
        return "(unhandled)";
    }
  }
}
