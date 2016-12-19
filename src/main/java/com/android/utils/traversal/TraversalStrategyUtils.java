package com.android.utils.traversal;

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

public class TraversalStrategyUtils {

    private TraversalStrategyUtils() {
        // Prevent utility class from being instantiated.
    }

    /**
     * Depending on whether the direction is spatial or logical, returns the appropriate
     * traversal strategy to handle the case.
     */
    public static TraversalStrategy getTraversalStrategy(AccessibilityNodeInfoCompat root,
            @TraversalStrategy.SearchDirection int direction) {
        switch (direction) {
            case TraversalStrategy.SEARCH_FOCUS_BACKWARD:
            case TraversalStrategy.SEARCH_FOCUS_FORWARD:
                return new OrderedTraversalStrategy(root);
            case TraversalStrategy.SEARCH_FOCUS_LEFT:
            case TraversalStrategy.SEARCH_FOCUS_RIGHT:
            case TraversalStrategy.SEARCH_FOCUS_UP:
            case TraversalStrategy.SEARCH_FOCUS_DOWN:
                return new DirectionalTraversalStrategy(root);
        }

        throw new IllegalArgumentException("direction must be a SearchDirection");
    }

    /**
     * Determines whether the given search direction corresponds to an actual spatial direction
     * as opposed to a logical direction.
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
        }

        throw new IllegalArgumentException("direction must be a SearchDirection");
    }

    /**
     * Converts a spatial direction to a logical direction based on whether the user is LTR or RTL.
     * If the direction is already a logical direction, it is returned.
     */
    public static @TraversalStrategy.SearchDirection int getLogicalDirection(
            @TraversalStrategy.SearchDirection int direction,
            boolean isRtl) {
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
        }

        throw new IllegalArgumentException("direction must be a SearchDirection");
    }

    /**
     * Returns the scroll action for the given {@link TraversalStrategy.SearchDirection} if the
     * scroll action is available on the current SDK version. Otherwise, returns 0.
     */
    public static int convertSearchDirectionToScrollAction(
            @TraversalStrategy.SearchDirection int direction) {
        boolean supportsDirectional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

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
     * Returns the {@link TraversalStrategy.SearchDirectionOrUnknown} for the given scroll
     * action; {@link TraversalStrategy#SEARCH_FOCUS_UNKNOWN} is returned for a scroll action
     * that can't be handled (e.g. because the current API level doesn't support it).
     */
    public static @TraversalStrategy.SearchDirectionOrUnknown int
            convertScrollActionToSearchDirection(int scrollAction) {
        boolean supportsDirectional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

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

}
