package com.google.android.accessibility.utils.traversal;

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.compat.CompatUtils;
import java.util.HashSet;
import java.util.Set;

public class TraversalStrategyUtils {

  /**
   * Class for Samsung's TouchWiz implementation of AbsListView. May be {@code null} on non-Samsung
   * devices.
   */
  private static final Class<?> CLASS_TOUCHWIZ_TWABSLISTVIEW =
      CompatUtils.getClass("com.sec.android.touchwiz.widget.TwAbsListView");

  private TraversalStrategyUtils() {
    // Prevent utility class from being instantiated.
  }

  /**
   * Depending on whether the direction is spatial or logical, returns the appropriate traversal
   * strategy to handle the case.
   */
  public static TraversalStrategy getTraversalStrategy(
      AccessibilityNodeInfoCompat root, @TraversalStrategy.SearchDirection int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
      case TraversalStrategy.SEARCH_FOCUS_FORWARD:
        return new OrderedTraversalStrategy(root);
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
      case TraversalStrategy.SEARCH_FOCUS_UP:
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return new DirectionalTraversalStrategy(root);
      default: // fall out
    }

    throw new IllegalArgumentException("direction must be a SearchDirection");
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
    boolean supportsDirectional = BuildVersionUtils.isAtLeastM();

    if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      return AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
    } else if (supportsDirectional) {
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
    boolean supportsDirectional = BuildVersionUtils.isAtLeastM();

    if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    } else if (supportsDirectional) {
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
    return isEdgeListItem(node, TraversalStrategy.SEARCH_FOCUS_BACKWARD, null, traversalStrategy)
        || isEdgeListItem(node, TraversalStrategy.SEARCH_FOCUS_FORWARD, null, traversalStrategy);
  }

  /**
   * Determines if the current item is at the edge of a list by checking the scrollable predecessors
   * of the items in a relative or absolute direction.
   *
   * @param node The node to check.
   * @param direction The direction in which to check.
   * @param filter (Optional) Filter used to validate list-type ancestors.
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return true if the current item is at the edge of a list.
   */
  private static boolean isEdgeListItem(
      AccessibilityNodeInfoCompat node,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> filter,
      TraversalStrategy traversalStrategy) {
    if (node == null) {
      return false;
    }

    int scrollAction = TraversalStrategyUtils.convertSearchDirectionToScrollAction(direction);
    if (scrollAction != 0) {
      NodeActionFilter scrollableFilter = new NodeActionFilter(scrollAction);
      Filter<AccessibilityNodeInfoCompat> comboFilter = scrollableFilter.and(filter);
      return isMatchingEdgeListItem(node, direction, comboFilter, traversalStrategy);
    }

    return false;
  }

  /**
   * Convenience method determining if the current item is at the edge of a list and suitable
   * autoscroll. Calls {@code isEdgeListItem} with {@code FILTER_AUTO_SCROLL}.
   *
   * @param node The node to check.
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
      AccessibilityNodeInfoCompat node, int direction, TraversalStrategy traversalStrategy) {
    return isEdgeListItem(node, direction, FILTER_AUTO_SCROLL, traversalStrategy);
  }

  /**
   * Utility method for determining if a searching past a particular node will fall off the edge of
   * a scrollable container.
   *
   * @param cursor Node to check.
   * @param direction The direction in which to move from the cursor.
   * @param filter Filter used to validate list-type ancestors.
   * @param traversalStrategy - traversal strategy that is used to define order of node
   * @return {@code true} if focusing search in the specified direction will fall off the edge of
   *     the container.
   */
  private static boolean isMatchingEdgeListItem(
      AccessibilityNodeInfoCompat cursor,
      @TraversalStrategy.SearchDirection int direction,
      Filter<AccessibilityNodeInfoCompat> filter,
      TraversalStrategy traversalStrategy) {
    AccessibilityNodeInfoCompat ancestor = null;
    AccessibilityNodeInfoCompat nextFocusNode = null;
    AccessibilityNodeInfoCompat searchedAncestor = null;

    try {
      ancestor = AccessibilityNodeInfoUtils.getMatchingAncestor(cursor, filter);
      if (ancestor == null) {
        // Not contained in a scrollable list.
        return false;
      }

      nextFocusNode =
          searchFocus(
              traversalStrategy, cursor, direction, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
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
        AccessibilityNodeInfoCompat webViewNode =
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
      AccessibilityNodeInfoUtils.recycleNodes(ancestor, nextFocusNode, searchedAncestor);
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
  public static AccessibilityNodeInfoCompat searchFocus(
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

        if (seenNodes.contains(targetNode)) {
          LogUtils.log(
              AccessibilityNodeInfoUtils.class,
              Log.ERROR,
              "Found duplicate during traversal: %s",
              targetNode.getInfo());
          return null;
        }
      } while (targetNode != null && !filter.accept(targetNode));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(seenNodes);
    }

    return targetNode;
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

  /**
   * Convenience class for a {@link Filter<AccessibilityNodeInfoCompat>} that checks whether nodes
   * support a specific action.
   */
  private static class NodeActionFilter extends Filter<AccessibilityNodeInfoCompat> {
    private final int mAction;

    /**
     * Creates a new action filter with the specified action mask.
     *
     * @param action The ID of the action to accept.
     */
    public NodeActionFilter(int action) {
      mAction = action;
    }

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node) {
      return AccessibilityNodeInfoUtils.supportsAction(node, mAction);
    }
  }

  /**
   * Filter that defines which types of views should be auto-scrolled. Generally speaking, only
   * accepts views that are capable of showing partially-visible data.
   *
   * <p>Accepts the following classes (and sub-classes thereof):
   *
   * <ul>
   *   <li>{@link android.support.v7.widget.RecyclerView} (Should be classified as a List or Grid.)
   *   <li>{@link android.widget.AbsListView} (including both ListView and GridView)
   *   <li>{@link android.widget.AbsSpinner}
   *   <li>{@link android.widget.ScrollView}
   *   <li>{@link android.widget.HorizontalScrollView}
   *   <li>{@code com.sec.android.touchwiz.widget.TwAbsListView}
   * </ul>
   *
   * <p>Specifically excludes {@link android.widget.AdapterViewAnimator} and sub-classes, since they
   * represent overlapping views. Also excludes {@link android.support.v4.view.ViewPager} since it
   * exclusively represents off-screen views.
   */
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_AUTO_SCROLL =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (node.isScrollable()) {
            @Role.RoleName int role = Role.getRole(node);
            // TODO: Check if we should include ROLE_ADAPTER_VIEW as a target Role.
            return role == Role.ROLE_DROP_DOWN_LIST
                || role == Role.ROLE_LIST
                || role == Role.ROLE_GRID
                || role == Role.ROLE_SCROLL_VIEW
                || role == Role.ROLE_HORIZONTAL_SCROLL_VIEW
                || AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
                    node, CLASS_TOUCHWIZ_TWABSLISTVIEW);
          }

          return false;
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
