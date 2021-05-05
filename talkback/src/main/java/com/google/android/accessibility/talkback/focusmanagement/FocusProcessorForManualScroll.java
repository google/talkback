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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Mappers;
import com.google.android.accessibility.talkback.Mappers.Variables;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Feedback-mapper for manual scroll events. */
public class FocusProcessorForManualScroll {
  // TODO Move static feedback-mapping methods into AccessibilityFocusMapper.

  private FocusProcessorForManualScroll() {} // Not instantiable

  /** Feedback-mapping function. */
  public static Feedback.Part.Builder onNodeManuallyScrolled(
      @Nullable EventId eventId, Variables variables, int depth, FocusFinder focusFinder) {
    LogDepth.logFunc(Mappers.LOG_TAG, ++depth, "onNodeManuallyScrolled");

    @SearchDirectionOrUnknown int direction = variables.scrollDirection(depth);
    @Nullable AccessibilityNodeInfoCompat scrolledNode = variables.source(depth); // Not owner.
    if (scrolledNode == null) {
      return null;
    }

    @Nullable AccessibilityNodeInfoCompat nodeToFocus = null;
    TraversalStrategy traversalStrategy = null;
    try {

      // Try to focus on the next/previous focusable node.
      traversalStrategy =
          TraversalStrategyUtils.getTraversalStrategy(scrolledNode, focusFinder, direction);
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache =
          traversalStrategy.getSpeakingNodesCache();
      Filter.NodeCompat nodeFilter =
          new Filter.NodeCompat(
              (node) -> AccessibilityNodeInfoUtils.shouldFocusNode(node, speakingNodeCache));
      nodeToFocus =
          TraversalStrategyUtils.findInitialFocusInNodeTree(
              traversalStrategy, scrolledNode, direction, nodeFilter);

      if (nodeToFocus == null) {
        return null;
      }

      FocusActionInfo focusActionInfo =
          new FocusActionInfo.Builder().setSourceAction(FocusActionInfo.MANUAL_SCROLL).build();

      return Feedback.part().setFocus(Feedback.focus(nodeToFocus, focusActionInfo).build());

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(nodeToFocus);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }
}
