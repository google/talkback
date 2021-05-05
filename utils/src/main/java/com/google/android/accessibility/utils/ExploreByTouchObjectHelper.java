/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.utils;

import android.graphics.Rect;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import androidx.customview.widget.ExploreByTouchHelper;
import java.util.LinkedList;
import java.util.List;

/**
 * Extension of {@link ExploreByTouchHelper} for custom views that rely on a single class for
 * logical units.
 *
 * <p>This should be applied to the parent view using {@link ViewCompat#setAccessibilityDelegate}:
 *
 * <pre>
 * mHelper = new ExploreByTouchHelper(context, someView);
 * ViewCompat.setAccessibilityDelegate(someView, mHelper);
 * </pre>
 */
public abstract class ExploreByTouchObjectHelper<T> extends ExploreByTouchHelper {
  /**
   * Constructs a new object-based Explore by Touch helper.
   *
   * @param parentView The view whose virtual hierarchy is exposed by this helper.
   */
  public ExploreByTouchObjectHelper(View parentView) {
    super(parentView);
  }

  /**
   * Populates an event of the specified type with information about an item and attempts to send it
   * up through the view hierarchy.
   *
   * <p>You should call this method after performing a user action that normally fires an
   * accessibility event, such as clicking on an item.
   *
   * <pre>
   * public void performItemClick(T item) {
   *   ...
   *   sendEventForItem(item, AccessibilityEvent.TYPE_VIEW_CLICKED);
   * }
   * </pre>
   *
   * @param item The item for which to send an event.
   * @param eventType The type of event to send.
   * @return {@code true} if the event was sent successfully.
   */
  public boolean sendEventForItem(T item, int eventType) {
    final int virtualViewId = getVirtualViewIdForItem(item);
    return sendEventForVirtualView(virtualViewId, eventType);
  }

  @Override
  protected final boolean onPerformActionForVirtualView(
      int virtualViewId, int action, Bundle arguments) {
    final T item = getItemForVirtualViewId(virtualViewId);
    return item != null && performActionForItem(item, action);
  }

  @Override
  protected final void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
    final T item = getItemForVirtualViewId(virtualViewId);
    if (item == null) {
      return;
    }

    populateEventForItem(item, event);
    event.setClassName(item.getClass().getName());
  }

  @Override
  protected final void onPopulateNodeForVirtualView(
      int virtualViewId, AccessibilityNodeInfoCompat node) {
    final T item = getItemForVirtualViewId(virtualViewId);
    if (item == null) {
      return;
    }

    populateNodeForItem(item, node);
    node.setClassName(item.getClass().getName());
  }

  @Override
  protected final void getVisibleVirtualViews(List<Integer> virtualViewIds) {
    final List<T> items = new LinkedList<>();
    getVisibleItems(items);

    for (T item : items) {
      final int virtualViewId = getVirtualViewIdForItem(item);
      virtualViewIds.add(virtualViewId);
    }
  }

  @Override
  protected final int getVirtualViewAt(float x, float y) {
    final T item = getItemAt(x, y);
    if (item == null) {
      return INVALID_ID;
    }

    return getVirtualViewIdForItem(item);
  }

  /**
   * Performs an accessibility action on the specified item. See {@link
   * AccessibilityNodeInfoCompat#performAction(int, Bundle)}.
   *
   * <p>Developers <b>must</b> handle any actions added manually in {@link #populateNodeForItem}.
   *
   * <p>The helper class automatically handles focus management resulting from {@link
   * AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS} and {@link
   * AccessibilityNodeInfoCompat#ACTION_CLEAR_ACCESSIBILITY_FOCUS}.
   *
   * @param item The item on which to perform the action.
   * @param action The accessibility action to perform.
   * @return {@code true} if the action was performed successfully.
   */
  protected abstract boolean performActionForItem(T item, int action);

  /**
   * Populates an event with information about the specified item.
   *
   * <p>Developers <b>must</b> populate the following required fields:
   *
   * <ul>
   *   <li>event content, see {@link AccessibilityEvent#getText()} or {@link
   *       AccessibilityEvent#setContentDescription}
   * </ul>
   *
   * <p>The helper class automatically populates some required fields:
   *
   * <ul>
   *   <li>package name, see {@link AccessibilityEvent#setPackageName}
   *   <li>item class name, see {@link AccessibilityEvent#setClassName}
   *   <li>event source, see {@link AccessibilityRecordCompat#setSource(View, int)}
   * </ul>
   *
   * @param item The item for which to populate the event.
   * @param event The event to populate.
   */
  protected abstract void populateEventForItem(T item, AccessibilityEvent event);

  /**
   * Populates a node with information about the specified item.
   *
   * <p>Developers <b>must</b> populate the following required fields:
   *
   * <ul>
   *   <li>node content, see {@link AccessibilityNodeInfoCompat#setText} or {@link
   *       AccessibilityNodeInfoCompat#setContentDescription}
   *   <li>parent-relative bounds, see {@link AccessibilityNodeInfoCompat#setBoundsInParent}
   * </ul>
   *
   * <p>The helper class automatically populates some required fields:
   *
   * <ul>
   *   <li>package name, see {@link AccessibilityNodeInfoCompat#setPackageName}
   *   <li>item class name, see {@link AccessibilityNodeInfoCompat#setClassName}
   *   <li>parent view, see {@link AccessibilityNodeInfoCompat#setParent(View)}
   *   <li>node source, see {@link AccessibilityNodeInfoCompat#setSource(View, int)}
   *   <li>visibility, see {@link AccessibilityNodeInfoCompat#setVisibleToUser}
   *   <li>screen-relative bounds, see {@link AccessibilityNodeInfoCompat#setBoundsInScreen(Rect)}
   * </ul>
   *
   * <p>The helper class also automatically handles accessibility focus management by adding one of:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_CLEAR_ACCESSIBILITY_FOCUS}
   * </ul>
   *
   * @param item The item for which to populate the node.
   * @param node The node to populate.
   */
  protected abstract void populateNodeForItem(T item, AccessibilityNodeInfoCompat node);

  /**
   * Populates a list with the parent view's visible items.
   *
   * @param items The list to populate with visible items.
   */
  protected abstract void getVisibleItems(List<T> items);

  /**
   * Returns the item under the specified parent-relative coordinates.
   *
   * @param x The parent-relative x coordinate.
   * @param y The parent-relative y coordinate.
   * @return The item under coordinates (x,y).
   */
  protected abstract T getItemAt(float x, float y);

  /**
   * Returns the unique identifier for an item. If the specified item does not exist, returns {@link
   * #INVALID_ID}.
   *
   * <p>Developers <b>must</b> provide a one-to-one mapping consistent with the result of {@link
   * #getItemForVirtualViewId}.
   *
   * @param item The item whose identifier to return.
   * @return A unique identifier, or {@link #INVALID_ID}.
   */
  protected abstract int getVirtualViewIdForItem(T item);

  /**
   * Returns the item for a unique identifier. If the specified item does not exist, or if the
   * specified identifier is {@link #INVALID_ID}, returns {@code null}.
   *
   * <p>Developers <b>must</b> provide a one-to-one mapping consistent with the result of {@link
   * #getVirtualViewIdForItem}.
   *
   * @param id The identifier for the item to return.
   * @return An item, or {@code null}.
   */
  protected abstract T getItemForVirtualViewId(int id);
}
