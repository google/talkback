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
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.NodePathDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.Map;

/** Handles the use case when a node is scrolled by dragging two fingers on screen. */
public class FocusProcessorForManualScroll {

  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;

  // Object-wrapper around static-method getAccessibilityFocus(), for test-mocking.
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  public FocusProcessorForManualScroll(
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  public boolean onNodeManuallyScrolled(
      AccessibilityNodeInfoCompat scrolledNode,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {
    // Nodes to be recycled.
    AccessibilityNodeInfoCompat currentA11yFocusedNode = null;
    AccessibilityNodeInfoCompat lastA11yFocusedNode = null;
    AccessibilityNodeInfoCompat nodeToFocus = null;

    TraversalStrategy traversalStrategy = null;
    try {
      currentA11yFocusedNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      if (AccessibilityNodeInfoUtils.shouldFocusNode(currentA11yFocusedNode)) {
        return false;
      }

      NodePathDescription lastFocusNodePathDescription =
          actorState.getFocusHistory().getLastFocusNodePathDescription();
      if (lastFocusNodePathDescription == null) {
        return false;
      }

      // We use attributes from AccessibilityNodeInfo, including viewIdResourceName to match
      // ancestor node. However, on pre-OMR1 devices, source node of TYPE_VIEW_SCROLLED events
      // always have null viewIdResourceName unless we call refresh().
      if (!BuildVersionUtils.isAtLeastOMR1()) {
        scrolledNode.refresh();
      }
      if (!lastFocusNodePathDescription.containsNodeByHashAndIdentity(scrolledNode)) {
        return false;
      }

      // Try to focus on the next/previous focusable node.
      traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(scrolledNode, direction);
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache =
          traversalStrategy.getSpeakingNodesCache();
      Filter<AccessibilityNodeInfoCompat> nodeFilter =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat obj) {
              return AccessibilityNodeInfoUtils.shouldFocusNode(obj, speakingNodeCache);
            }
          };

      nodeToFocus =
          TraversalStrategyUtils.findInitialFocusInNodeTree(
              traversalStrategy, scrolledNode, direction, nodeFilter);

      if (nodeToFocus == null) {
        return false;
      }

      FocusActionInfo focusActionInfo =
          new FocusActionInfo.Builder().setSourceAction(FocusActionInfo.MANUAL_SCROLL).build();

      return pipeline.returnFeedback(eventId, Feedback.focus(nodeToFocus, focusActionInfo));

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(
          currentA11yFocusedNode, lastA11yFocusedNode, nodeToFocus);
      TraversalStrategyUtils.recycle(traversalStrategy);
    }
  }
}
