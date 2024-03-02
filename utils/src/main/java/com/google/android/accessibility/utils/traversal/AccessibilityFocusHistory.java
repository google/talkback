/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.collection.LruCache;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Cache for recently accessibility-focused nodes. Uses {@link
 * AccessibilityNodeInfoCompat#hashCode()} as key.
 */
public class AccessibilityFocusHistory {
  public static final long NOT_FOUND = -1;

  private static final int MAX_HISTORY_SIZE = 256;

  private final LruCache<Integer, Long> cache = new LruCache<>(MAX_HISTORY_SIZE);

  /**
   * Records that a node has been accessibility-focused.
   *
   * <p>To be called on every {@link AccessibilityEvent} of type {@code
   * TYPE_VIEW_ACCESSIBILITY_FOCUSED}.
   */
  public void onFocusEvent(AccessibilityEvent event) {
    AccessibilityNodeInfo node = event.getSource();
    if (node == null) {
      return;
    }
    int nodeId = node.hashCode();
    cache.put(nodeId, event.getEventTime());
  }

  /**
   * Returns the time when the given node has been focused the last time.
   *
   * <p>If the node is not found in the history, {@link #NOT_FOUND} is returned. This may happen if
   * it has never been focused, or it has been too long and the history has reached its maximum
   * size.
   *
   * <p>The node is identified by its hash code.
   */
  public long getTimeOfLastFocusForNode(AccessibilityNodeInfoCompat node) {
    Long time = cache.get(node.hashCode());
    if (time == null) {
      return NOT_FOUND;
    }
    return time;
  }
}
