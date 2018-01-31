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

import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;

/** The {@link FocusProcessor} to handle accessibility focus after a view is scrolled. */
public class FocusProcessorForScroll extends FocusProcessor {
  /**
   * Timeout to clear auto-scroll information after being notified with an auto-scroll action from
   * CursorController.
   */
  private static final int CLEAR_SCROLLED_NODE_DELAY_MS = 1000;

  /** Event types that are handled by FocusProcessorForScroll. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_FOR_SCROLL =
      AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private AccessibilityFocusManager mA11yFocusManager;

  private CursorController mCursorController;

  private final AutoScrollHandler mAutoScrollHandler;

  // ////////////////////////////////////////////////////////////////////////////////////////////////
  // Some data cached from scroll actions.

  /**
   * The node being auto-scrolled.
   *
   * <p><strong>Usage:</strong> This is used to check whether a TYPE_VIEW_SCROLLED event results
   * from an auto-scroll action, by comparing this node and the source node of scroll event.
   *
   * <p>Value is assigned when being notified with an auto-scroll action from CursorController.
   */
  private AccessibilityNodeInfoCompat mActionScrolledNode;

  private AccessibilityNodeInfoCompat mLastFocusedItem;

  // Cache data from the previous scroll event.
  // TODO: We can cache AccessibilityRecord or AccessibilityEvent directly.
  private @TraversalStrategy.SearchDirectionOrUnknown int mLastScrollDirection;
  private int mLastScrollFromIndex = -1;
  private int mLastScrollToIndex = -1;
  private int mLastScrollX = -1;
  private int mLastScrollY = -1;

  private boolean mIsRetryAutoScroll = false;

  FocusProcessorForScroll(
      AccessibilityFocusManager accessibilityFocusManager, CursorController cursorController) {
    mA11yFocusManager = accessibilityFocusManager;
    mCursorController = cursorController;
    mAutoScrollHandler = new AutoScrollHandler(this);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_FOR_SCROLL;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        handleViewScrolled(event, eventId);
        break;
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        AccessibilityNodeInfoUtils.recycleNodes(mLastFocusedItem);
        mLastFocusedItem = null;
        // Clear the state of the last scroll event.
        // TODO: We should clear the state for some other events.
        // Fragment transition doesn't file WINDOW_STATE_CHANGED event, there could be some corner
        // cases like(cl/156108654 solved this by checking whether the cached state is
        // legal or not, we can clean up the illegal state earlier when we receive some events.).
        clearScrollAction();
        mCursorController.setNavigationEnabled(true);
        mLastScrollFromIndex = -1;
        mLastScrollToIndex = -1;
        break;
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        AccessibilityNodeInfoUtils.recycleNodes(mLastFocusedItem);
        mLastFocusedItem = AccessibilityNodeInfoUtils.toCompat(event.getSource());
        break;
      default:
        break;
    }
  }

  private void handleViewScrolled(AccessibilityEvent event, EventId eventId) {
    final AccessibilityNodeInfoCompat source =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (source == null) {
      return;
    }
    // TODO: Consider a better way to check whether the source action of the scroll event.
    if (mActionScrolledNode == null) {
      handleViewManualScrolled(source, getScrollDirection(event), eventId);
    } else if (source.equals(mActionScrolledNode)) {
      handleViewAutoScrolled(source, mLastScrollDirection, eventId);
    } else {
      // mActionScrolledNode is assigned immediately after successfully performing auto
      // scroll action on a node. Thus the next TYPE_VIEW_SCROLLED event should be fired
      // by this node. If for some reason we don't receive the event from the correct
      // node, do nothing here and let the {@link mAutoScrollHandler} to clear the state.
      source.recycle();
      return;
    }

    source.recycle();

    mLastScrollFromIndex = event.getFromIndex();
    mLastScrollToIndex = event.getToIndex();
    mLastScrollX = event.getScrollX();
    mLastScrollY = event.getScrollY();
  }

  private void handleViewAutoScrolled(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      @TraversalStrategy.SearchDirectionOrUnknown int direction,
      EventId eventId) {
    clearScrollAction();
    // SEARCH_FOCUS_UNKNOWN can be passed, so need to guarantee that direction is a
    // @TraversalStrategy.SearchDirection before continuing.
    if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return;
    }

    AccessibilityNodeInfoCompat accessibilityFocused = null;

    try {
      accessibilityFocused = mA11yFocusManager.getAccessibilityFocus();
      final boolean validAccessibilityFocus =
          AccessibilityNodeInfoUtils.shouldFocusNode(accessibilityFocused);
      // There are cases when scrollable container was scrolled and application set
      // focus on node that is on new container page. We should keep this focus.
      final boolean hasInputFocus =
          accessibilityFocused != null && accessibilityFocused.isFocused();

      if (validAccessibilityFocus && hasInputFocus) {
        // Focused on valid node and scrolled not by scroll action, then keep focus.
        return;
      }

      if (validAccessibilityFocus) {
        // After auto-scroll action, we still have a valid a11y focus on the screen.
        if (!AccessibilityNodeInfoUtils.hasAncestor(accessibilityFocused, scrolledNode)) {
          return;
        }
        final TraversalStrategy traversal =
            TraversalStrategyUtils.getTraversalStrategy(scrolledNode, direction);
        try {
          if (!focusNextFocusedNode(
                  traversal, accessibilityFocused, scrolledNode, direction, eventId)
              && !mIsRetryAutoScroll) {
            // If we cannot find the next accessibility focus under the scrolled node,
            // then this scroll action might be just scrolling to show the margin of
            // the container, and doesn't expose more children node in the container.
            // We should repeat the last navigation action.
            mCursorController.repeatLastNavigationAction();
          }
        } finally {
          traversal.recycle();
        }
      } else {
        if (mLastFocusedItem == null) {
          // There was no focus - don't set focus.
          return;
        }

        // We have to check both upwards(ancestor) and downwards(descendant) to avoid
        // false positive cases introduced by some accessibility cache update issue.
        // (Sometimes hasAncestor(A, B) returns true, but hasDescendant(B, A) check
        // returns false.)
        if (AccessibilityNodeInfoUtils.hasAncestor(mLastFocusedItem, scrolledNode)
            && AccessibilityNodeInfoUtils.hasDescendant(scrolledNode, mLastFocusedItem)) {
          TraversalStrategy traversal =
              TraversalStrategyUtils.getTraversalStrategy(scrolledNode, direction);
          // If the previous focus is still under the node tree of the scrollable view,
          // but it's now not valid for some reason(e.g. invisible). Then look for the
          // next focusable node starting from the previous focus.
          try {
            if (focusNextFocusedNode(traversal, mLastFocusedItem, null, direction, eventId)) {
              return;
            }
          } finally {
            traversal.recycle();
          }
        }

        // mLastFocusedItem is not in the source
        // If the previous focus has been detached from the scrollable view, or it's not a
        // auto-scroll action, we should conservatively focus on the very first/last child
        // of the source.
        if (tryFocusingChild(scrolledNode, direction, eventId)) {
          return;
        }

        // Finally, try focusing the scrollable node itself.
        mA11yFocusManager.tryFocusing(scrolledNode, false, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(accessibilityFocused);
      mCursorController.setNavigationEnabled(true);
    }
  }

  private void handleViewManualScrolled(
      @NonNull AccessibilityNodeInfoCompat scrolledNode,
      @TraversalStrategy.SearchDirectionOrUnknown int direction,
      EventId eventId) {
    // SEARCH_FOCUS_UNKNOWN can be passed, so need to guarantee that direction is a
    // @TraversalStrategy.SearchDirection before continuing.
    if (direction == TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      return;
    }

    AccessibilityNodeInfoCompat accessibilityFocused = null;
    try {

      accessibilityFocused = mA11yFocusManager.getAccessibilityFocus();
      final boolean validAccessibilityFocus =
          AccessibilityNodeInfoUtils.shouldFocusNode(accessibilityFocused);
      // There are cases when scrollable container was scrolled and application set
      // focus on node that is on new container page. We should keep this focus.

      if (!validAccessibilityFocus) {
        if (mLastFocusedItem == null || !mLastFocusedItem.isAccessibilityFocused()) {
          // There was no focus - don't set focus.
          return;
        }

        // Check if mLastFocusedItem is in the same accessibility node tree as the scrolled item
        // If the previous focus has been detached from the scrollable view, or it's not a
        // auto-scroll action, we should conservatively focus on the very first/last child
        // of the source.
        if ((AccessibilityNodeInfoUtils.hasAncestor(mLastFocusedItem, scrolledNode)
            || AccessibilityNodeInfoUtils.hasDescendant(scrolledNode, mLastFocusedItem))) {
          // TODO: Decompose tryFocusingChild to A11yFocusManager.tryFocusing() and
          // FocusProcessorForScroll.findChildFromNode().
          if (tryFocusingChild(scrolledNode, direction, eventId)) {
            return;
          }
        }

        // Finally, try focusing the scrollable node itself.
        mA11yFocusManager.tryFocusing(scrolledNode, false, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(accessibilityFocused);
    }
  }

  private boolean focusNextFocusedNode(
      TraversalStrategy traversal,
      AccessibilityNodeInfoCompat node,
      final AccessibilityNodeInfoCompat root,
      @TraversalStrategy.SearchDirection int direction,
      final EventId eventId) {
    if (node == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null
                && AccessibilityNodeInfoUtils.shouldFocusNode(node)
                && (root == null || AccessibilityNodeInfoUtils.hasAncestor(node, root))
                && mA11yFocusManager.tryFocusing(node, false, eventId);
          }
        };

    AccessibilityNodeInfoCompat candidateFocus =
        TraversalStrategyUtils.searchFocus(traversal, node, direction, filter);

    return candidateFocus != null;
  }

  /**
   * If {@code wasMovingForward} is true, moves to the first focusable child. Otherwise, moves to
   * the last focusable child.
   */
  private boolean tryFocusingChild(
      AccessibilityNodeInfoCompat parent,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {
    AccessibilityNodeInfoCompat child = null;

    try {
      child = findChildFromNode(parent, direction);
      return child != null && mA11yFocusManager.tryFocusing(child, false, eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(child);
    }
  }

  /**
   * Gets the scroll direction by comparing the index from the given scroll event and the index
   * cached from the last scroll event.
   */
  private @TraversalStrategy.SearchDirectionOrUnknown int getScrollDirection(
      AccessibilityEvent event) {
    // TODO: Check if we should use Role to check AdapterViews and ScrollViews.
    // Check scroll of AdapterViews
    if (event.getFromIndex() > mLastScrollFromIndex || event.getToIndex() > mLastScrollToIndex) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getFromIndex() < mLastScrollFromIndex
        || event.getToIndex() < mLastScrollToIndex) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    // Check scroll of ScrollViews.
    if (event.getScrollX() > mLastScrollX || event.getScrollY() > mLastScrollY) {
      return TraversalStrategy.SEARCH_FOCUS_FORWARD;
    } else if (event.getScrollX() < mLastScrollX || event.getScrollY() < mLastScrollY) {
      return TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    }

    return TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
  }

  private void clearScrollAction() {
    mLastScrollDirection = TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
    if (mActionScrolledNode != null) {
      mActionScrolledNode.recycle();
    }

    mAutoScrollHandler.cancelTimeout();
    mActionScrolledNode = null;
    mIsRetryAutoScroll = false;
  }

  /**
   * Callback to be invoked when auto-scroll action is performed in {@link CursorController}.
   *
   * @param scrolledNode Auto-scrolled node
   * @param action Direction of the scroll.
   * @param auto If {@code true}, then the scroll was initiated automatically. If {@code false},
   *     then the user initiated the scroll action.
   * @param isRepeatNavigationForAutoScroll If {@code true}, the scroll action is triggered by
   */
  // TODO: Break dependency between CursorController and this class.
  void onScroll(
      AccessibilityNodeInfoCompat scrolledNode,
      int action,
      boolean auto,
      boolean isRepeatNavigationForAutoScroll) {
    if (scrolledNode == null) {
      clearScrollAction();
    } else {
      mAutoScrollHandler.cancelTimeout();
    }
    mIsRetryAutoScroll = isRepeatNavigationForAutoScroll;
    @TraversalStrategy.SearchDirectionOrUnknown
    int direction = TraversalStrategyUtils.convertScrollActionToSearchDirection(action);
    if (direction != TraversalStrategy.SEARCH_FOCUS_UNKNOWN) {
      mLastScrollDirection = direction;
      if (mActionScrolledNode != null) {
        mActionScrolledNode.recycle();
      }

      if (scrolledNode != null) {
        mActionScrolledNode = AccessibilityNodeInfoCompat.obtain(scrolledNode);
        mAutoScrollHandler.clearAutoScrollNodeDelayed();
      }
    }
  }

  /**
   * Returns the first focusable child found while traversing the child of the specified node in a
   * specific direction. Only traverses direct children.
   *
   * @param root The node to search within.
   * @param direction The direction to search, one of the {@link TraversalStrategy.SearchDirection}
   *     constants.
   * @return The first focusable child encountered in the specified direction.
   */
  private static AccessibilityNodeInfoCompat findChildFromNode(
      AccessibilityNodeInfoCompat root, @TraversalStrategy.SearchDirection int direction) {
    if (root == null || root.getChildCount() == 0) {
      return null;
    }

    final TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(root, direction);

    AccessibilityNodeInfoCompat pivotNode = traversalStrategy.focusInitial(root, direction);

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null
                && AccessibilityNodeInfoUtils.shouldFocusNode(
                    node, traversalStrategy.getSpeakingNodesCache());
          }
        };

    try {
      if (filter.accept(pivotNode)) {
        return AccessibilityNodeInfoCompat.obtain(pivotNode);
      }

      return TraversalStrategyUtils.searchFocus(traversalStrategy, pivotNode, direction, filter);
    } finally {
      if (pivotNode != null) {
        pivotNode.recycle();
      }
    }
  }

  /**
   * A handler to reset the cached auto-scrollable node after timeout.
   *
   * <p>When we perform auto-scroll action in {@link CursorController}, we cache the scrolled node
   * as {@link FocusProcessorForScroll#mActionScrolledNode}. This node will be reset to null when we
   * receive {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} event from that node. Sometimes due to
   * the app implementation, even if the scroll action is successfully performed, no corresponding
   * event is received. Then we use this handler to reset the cached node.
   */
  private static class AutoScrollHandler extends WeakReferenceHandler<FocusProcessorForScroll> {
    private static final int MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE = 1;

    private AutoScrollHandler(FocusProcessorForScroll parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, FocusProcessorForScroll parent) {
      if (msg.what == MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE) {
        parent.clearScrollAction();
        parent.mLastScrollFromIndex = -1;
        parent.mLastScrollToIndex = -1;
        parent.mCursorController.setNavigationEnabled(true);
      }
    }

    /**
     * Sets {@link FocusProcessorForScroll#mActionScrolledNode} to null and clear scroll action data
     * after timeout.
     */
    private void clearAutoScrollNodeDelayed() {
      sendEmptyMessageDelayed(MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE, CLEAR_SCROLLED_NODE_DELAY_MS);
    }

    /**
     * Cancels any pending messages to set @link ProcessorFocusAndSingleTap#mActionScrolledNode} to
     * null and clear scroll action data.
     */
    private void cancelTimeout() {
      removeMessages(MESSAGE_WHAT_CLEAR_AUTO_SCROLL_NODE);
    }
  }
}
