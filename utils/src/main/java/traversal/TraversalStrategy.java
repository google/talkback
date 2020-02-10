/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.utils.traversal;

import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Strategy the is defined an order of traversing through the nodes of AccessibilityNodeInfo
 * hierarchy
 */
public interface TraversalStrategy {

  public static final int SEARCH_FOCUS_UNKNOWN = 0;
  public static final int SEARCH_FOCUS_FORWARD = 1;
  public static final int SEARCH_FOCUS_BACKWARD = 2;
  public static final int SEARCH_FOCUS_LEFT = 3;
  public static final int SEARCH_FOCUS_RIGHT = 4;
  public static final int SEARCH_FOCUS_UP = 5;
  public static final int SEARCH_FOCUS_DOWN = 6;

  /** Direction to search for an item to focus. */
  @IntDef({
    SEARCH_FOCUS_FORWARD,
    SEARCH_FOCUS_BACKWARD,
    SEARCH_FOCUS_LEFT,
    SEARCH_FOCUS_RIGHT,
    SEARCH_FOCUS_UP,
    SEARCH_FOCUS_DOWN
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface SearchDirection {}

  /** Direction to search for an item to focus, or unknown. */
  @IntDef({
    SEARCH_FOCUS_UNKNOWN,
    SEARCH_FOCUS_FORWARD,
    SEARCH_FOCUS_BACKWARD,
    SEARCH_FOCUS_LEFT,
    SEARCH_FOCUS_RIGHT,
    SEARCH_FOCUS_UP,
    SEARCH_FOCUS_DOWN
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface SearchDirectionOrUnknown {}

  /**
   * The method searches next node to be focused
   *
   * @param startNode - pivot node the search is start from
   * @param direction - direction to find focus
   * @return {@link android.support.v4.view.accessibility.AccessibilityNodeInfoCompat} node that has
   *     next focus
   */
  public @Nullable AccessibilityNodeInfoCompat findFocus(
      AccessibilityNodeInfoCompat startNode, @SearchDirection int direction);

  /**
   * Finds the initial focusable accessibility node in hierarchy started from root node when
   * searching in the given direction.
   *
   * <p>For example, if {@code direction} is {@link #SEARCH_FOCUS_FORWARD}, then the method should
   * return the first node in the traversal order. If {@code direction} is {@link
   * #SEARCH_FOCUS_BACKWARD} then the method should return the last node in the traversal order.
   *
   * @param root - root node
   * @param direction - the direction to search from
   * @return returns the first node that could be focused
   */
  public @Nullable AccessibilityNodeInfoCompat focusInitial(
      AccessibilityNodeInfoCompat root, @SearchDirection int direction);

  /**
   * Calculating if node is speaking node according to AccessibilityNodeInfoUtils.isSpeakingNode()
   * method is time consuming. Traversal strategy may use cache for already calculated values. If
   * traversal strategy does not need in such cache use it could return null.
   *
   * @return speaking node cache map. Could be null if cache is not used by traversal strategy
   */
  public Map<AccessibilityNodeInfoCompat, Boolean> getSpeakingNodesCache();

  /**
   * When there is no need in traversal strategy object it must be recycled before garbage collected
   */
  public void recycle();
}
