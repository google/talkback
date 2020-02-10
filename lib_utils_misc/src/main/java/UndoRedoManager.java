package com.google.android.libraries.accessibility.utils.undo;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.widget.EditText;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.common.annotations.VisibleForTesting;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Manages undo/redo operations of all {@link AccessibilityNodeInfoCompat} instances that are
 * on the screen.
 */
public class UndoRedoManager {

  private static UndoRedoManager manager = null;

  /**
   * The {@code SIZE_LIMIT} is the number of nodes acceptable to keep track of for Undo/Redo
   * operations.
   * <p>
   * The {@code STALE_NODE_LIFE_LIMIT} is the number of milliseconds from the last use of a node
   * when it should stop being tracked.
   */
  private static final int STALE_NODE_LIFE_LIMIT = 2 * 60 * 60 * 1000; // 2 hours in milliseconds
  @VisibleForTesting
  static final int SIZE_LIMIT = 20;

  @VisibleForTesting
  final Map<AccessibilityNodeInfoCompat, ActionTimeline> nodesToTimelines = new HashMap<>();

  @VisibleForTesting
  final Map<ActionTimeline, AccessibilityNodeInfoCompat> timelinesToNodes = new HashMap<>();

  @VisibleForTesting final Queue<ActionTimeline> leastRecentlyUsedTimelines;

  /**
   * Indicates the behavior of the UndoRedoManager instance when removing timelines; whether it
   * recycles nodes or not.
   */
  public enum RecycleBehavior {
    DO_RECYCLE_NODES,
    DO_NOT_RECYCLE_NODES
  }

  // The above maps may store copies of nodes which are not recycled in the natural flow of the
  // program. If this is the case, we need to recycle the nodes here once we no longer need them,
  // and this should be set to DO_RECYCLE_NODES.
  private RecycleBehavior recycleNodesWhenRemovingTimelines;

  private UndoRedoManager() {
    leastRecentlyUsedTimelines = new PriorityBlockingQueue<>(SIZE_LIMIT + 2);
  }

  /**
   * Returns the singleton instance of UndoRedoManager. Whether this instance recycles nodes when
   * removing timelines depends on parameter {@code recycleNodesWhenRemovingTimelines}.
   *
   * @param recycleNodesWhenRemovingTimelines A {@link RecycleBehavior} indicating whether the
   *     UndoRedoManger should recycle nodes when removing timelines
   */
  public static synchronized UndoRedoManager getInstance(
      RecycleBehavior recycleNodesWhenRemovingTimelines) {
    if (manager == null) {
      manager = new UndoRedoManager();
    }

    manager.recycleNodesWhenRemovingTimelines = recycleNodesWhenRemovingTimelines;
    return manager;
  }

  /**
   * Begins tracking the history of the view that corresponds to the specified {@link
   * AccessibilityNodeInfoCompat}. If the view is already being tracked, this method will
   * essentially clear the history for that view.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} corresponding the view to track the
   *     history for
   * @param timeline the {@link ActionTimeline} to use for the given {@link
   *     AccessibilityNodeInfoCompat}
   * @return the corresponding {@link ActionTimeline} that was created to track the view
   */
  public synchronized ActionTimeline beginTrackingView(
      AccessibilityNodeInfoCompat nodeCompat,
      ActionTimeline timeline) {
    // If the node is editable, we're dealing with a text view
    if ((nodeCompat == null) || (timeline == null) || !isSupportedNode(nodeCompat)) {
      return null;
    }

    if (isBeingTracked(nodeCompat)) {
      stopTrackingView(nodeCompat);
    }

    // Add the compat and timeline into the map, and all the timeline into the min heap
    nodesToTimelines.put(nodeCompat, timeline);
    timelinesToNodes.put(timeline, nodeCompat);
    leastRecentlyUsedTimelines.add(timeline);

    clearUntilTimeLimit();
    clearUntilSizeLimit();
    return timeline;
  }

  /**
   * Stops tracking the history of the view that corresponds to the specified {@link
   * AccessibilityNodeInfoCompat}. This method recycles the node only if the last time that
   * #getInstance was called, recycleNodesWhenRemovingTimelines was set to DO_RECYCLE_NODES.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} to stop tracking for
   */
  public synchronized void stopTrackingView(AccessibilityNodeInfoCompat nodeCompat) {
    final ActionTimeline timeline = nodesToTimelines.remove(nodeCompat);
    AccessibilityNodeInfoCompat nodeToRecycle = timelinesToNodes.remove(timeline);
    leastRecentlyUsedTimelines.remove(timeline);
    if (recycleNodesWhenRemovingTimelines == RecycleBehavior.DO_RECYCLE_NODES) {
      nodeToRecycle.recycle();
    }
  }

  /**
   * Stops tracking the history of the view that corresponds to the specified {@link
   * ActionTimeline}.
   *
   * @param timelineForView the {@link ActionTimeline} to stop tracking for
   */
  public synchronized void stopTrackingView(ActionTimeline timelineForView) {
    stopTrackingView(timelinesToNodes.get(timelineForView));
  }

  /**
   * Checks to see if a particular {@link AccessibilityNodeInfoCompat} is currently being tracked.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} to check
   * @return {@code true} if {@code nodeCompat}'s history is being tracked
   */
  public synchronized boolean isBeingTracked(AccessibilityNodeInfoCompat nodeCompat) {
    return nodesToTimelines.containsKey(nodeCompat);
  }

  /**
   * Returns a particular {@link AccessibilityNodeInfoCompat}'s corresponding {@link
   * ActionTimeline}.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} to get the {@link ActionTimeline} for
   * @param timeline the {@link ActionTimeline} to use if none exists for the given {@link
   *     AccessibilityNodeInfoCompat} yet
   * @return the {@link ActionTimeline} corresponding to {@code nodeCompat}; {@code null} if not
   *     tracked
   */
  public synchronized ActionTimeline getTimelineForNodeCompat(
      AccessibilityNodeInfoCompat nodeCompat,
      ActionTimeline timeline) {

    if (isBeingTracked(nodeCompat)) {
      final ActionTimeline existingTimeline = nodesToTimelines.get(nodeCompat);
      existingTimeline.updateTimeStamp();
      return existingTimeline;
    } else {
      return beginTrackingView(nodeCompat, timeline);
    }
  }

  /**
   * Updates the timestamp for a given node if it is being tracked, or begins to track it if it is
   * not currently being tracked.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} to updated the timestamp for
   * @param timeline the {@link ActionTimeline} to use if none exists for the given {@link
   *     AccessibilityNodeInfoCompat} yet
   * @return {@code true} if the node timestamp update was successful
   */
  public synchronized boolean updateCacheForNodeCompat(
      AccessibilityNodeInfoCompat nodeCompat,
      ActionTimeline timeline) {

    final ActionTimeline actionTimeline;
    if (isBeingTracked(nodeCompat)) {
      actionTimeline = getTimelineForNodeCompat(nodeCompat, timeline);
    } else {
      actionTimeline = beginTrackingView(nodeCompat, timeline);
      if (actionTimeline == null) {
        return false;
      }
    }

    actionTimeline.logicInBetweenActions();
    return true;
  }

  /**
   * Updates the position of the {@link ActionTimeline} in the heap. Should be invoked after the
   * time in {@code timeline} has been updated.
   *
   * @param timeline the {@link ActionTimeline} to updated the position of
   * @return {@code true} if the update was successful
   */
  synchronized boolean updateHeapPosition(ActionTimeline timeline) {
    if (leastRecentlyUsedTimelines.remove(timeline)) {
      leastRecentlyUsedTimelines.add(timeline);
      return true;
    }

    return false;
  }

  /**
   * Clears the {@link AccessibilityNodeInfoCompat} instances and their corresponding {@link
   * ActionTimeline} instances that are being tracked based on when they were last modified. If an
   * {@link AccessibilityNodeInfoCompat} was last modified over STALE_NODE_LIFE_LIMIT milliseconds
   * ago, it will be removed from consideration.
   */
  private synchronized void clearUntilTimeLimit() {
    final Date currTime = new Date();

    while (!leastRecentlyUsedTimelines.isEmpty()) {
      final ActionTimeline timeline = leastRecentlyUsedTimelines.peek();
      if (currTime.getTime() - timeline.getLastTimeUsed().getTime() > STALE_NODE_LIFE_LIMIT) {
        stopTrackingView(timeline);
      } else {
        break;
      }
    }
  }

  /**
   * Ensures that no more than SIZE_LIMIT {@link AccessibilityNodeInfoCompat} instances and their
   * corresponding {@link ActionTimeline} instances are being tracked at any given time.
   */
  private synchronized void clearUntilSizeLimit() {
    while (leastRecentlyUsedTimelines.size() > SIZE_LIMIT) {
      final ActionTimeline timelineToRemove = leastRecentlyUsedTimelines.peek();
      stopTrackingView(timelineToRemove);
    }
  }

  /**
   * Checks if we support the {@link AccessibilityNodeInfoCompat} in our {@link UndoRedoManager}.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} to verify
   * @return {@code true} if the node is supported
   */
  private static synchronized boolean isSupportedNode(AccessibilityNodeInfoCompat nodeCompat) {
    // Editability check
    return AccessibilityNodeInfoUtils.isVisible(nodeCompat)
        && AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(nodeCompat, EditText.class);
  }
}
