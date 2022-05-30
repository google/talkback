/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_NODE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.FOCUS_FOR_TOUCH;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.INITIAL_FOCUS_FIRST_CONTENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.INITIAL_FOCUS_FOLLOW_INPUT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.INITIAL_FOCUS_RESTORE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_NODE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.RESTORE;

import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.NodeAction;
import com.google.android.accessibility.talkback.Mappers;
import com.google.android.accessibility.talkback.Mappers.Variables;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.ArrayList;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Feedback-mapper for window & touch events generating focus actions. */
public class FocusFeedbackMapper {

  // Static methods only
  private FocusFeedbackMapper() {}

  /** Maps window-events to focus actions. */
  public static Feedback mapWindowChangeToFocusAction(
      EventId eventId, Mappers.Variables variables, int depth) {

    LogDepth.logFunc(Mappers.LOG_TAG, ++depth, "mapWindowChangeToFocusAction");

    // Force restore accessibility-focus.
    @Nullable ScreenState screenState = variables.screenState(depth);
    ArrayList<Feedback.Part> feedbackFailovers = new ArrayList<>();
    if (variables.forceRestoreFocus(depth)) {
      feedbackFailovers.add(toFeedbackPart(RESTORE, screenState));
    }

    // Fail-over to restore accessibility-focus on resurfaced window.
    boolean initialFocusEnabled = variables.isInitialFocusEnabled(depth);
    if (initialFocusEnabled) {
      feedbackFailovers.add(
          Feedback.part()
              .setFocus(Feedback.focus(INITIAL_FOCUS_RESTORE).setScreenState(screenState).build())
              .build());
    }

    // Fail-over to move accessibility-focus to input-focus.
    feedbackFailovers.add(toFeedbackPart(INITIAL_FOCUS_FOLLOW_INPUT, screenState));

    // Fail-over to accessibility-focus on first non-title content.
    if (initialFocusEnabled) {
      feedbackFailovers.add(toFeedbackPart(INITIAL_FOCUS_FIRST_CONTENT, screenState));
    }
    return Feedback.create(eventId, feedbackFailovers);
  }

  private static Feedback.Part toFeedbackPart(
      Feedback.Focus.Action action, @Nullable ScreenState screenState) {
    return Feedback.part()
        .setFocus(Feedback.focus(action).setScreenState(screenState).build())
        .build();
  }

  /** Maps touch-events to focus actions. */
  public static @Nullable Feedback mapTouchToFocusAction(
      EventId eventId, Mappers.Variables variables, int depth) {

    LogDepth.logFunc(Mappers.LOG_TAG, ++depth, "mapTouchToFocusAction");

    @Nullable AccessibilityNodeInfoCompat touchTarget = variables.touchTarget(depth); // Not owner

    switch (variables.touchAction(depth)) {
      case TOUCH_NOTHING:
        return Feedback.create(
            eventId,
            Feedback.sound(R.raw.view_entered).vibration(R.array.view_hovered_pattern).build());

      case TOUCH_START:
        return Feedback.create(eventId, Feedback.part().setInterruptGentle(true).build());

      case TOUCH_FOCUSED_NODE:
        return toFeedback(
            eventId, Feedback.focus(FOCUS_FOR_TOUCH).setTarget(touchTarget).setForceRefocus(true));

      case TOUCH_UNFOCUSED_NODE:
        return toFeedback(eventId, Feedback.focus(FOCUS_FOR_TOUCH).setTarget(touchTarget));

      case LIFT:
        if (variables.liftToType(depth)) {
          return toFeedback(eventId, Feedback.focus(CLICK_NODE).setTarget(touchTarget));
        }
        break;

      case TAP:
        if (variables.singleTap(depth)) {
          return toFeedback(eventId, Feedback.focus(CLICK_NODE).setTarget(touchTarget));
        }
        break;

      case LONG_PRESS:
        return toFeedback(eventId, Feedback.focus(LONG_CLICK_NODE).setTarget(touchTarget));

      default:
        return null;
    }
    return null;
  }

  private static Feedback toFeedback(@Nullable EventId eventId, Feedback.Focus.Builder focus) {
    return Feedback.create(eventId, Feedback.part().setFocus(focus.build()).build());
  }

  /** Feedback-mapping function. */
  public static Feedback.Part.@Nullable Builder onNodeManuallyScrolled(
      Variables variables, int depth, FocusFinder focusFinder) {

    LogDepth.logFunc(Mappers.LOG_TAG, ++depth, "onNodeManuallyScrolled");

    @SearchDirectionOrUnknown int direction = variables.scrollDirection(depth);
    @Nullable AccessibilityNodeInfoCompat scrolledNode = variables.source(depth); // Not owner.
    if (scrolledNode == null) {
      return null;
    }

    // Try to focus on the next/previous focusable node.
    TraversalStrategy traversalStrategy =
        TraversalStrategyUtils.getTraversalStrategy(scrolledNode, focusFinder, direction);
    final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache =
        traversalStrategy.getSpeakingNodesCache();
    Filter.NodeCompat nodeFilter =
        new Filter.NodeCompat(
            (node) -> AccessibilityNodeInfoUtils.shouldFocusNode(node, speakingNodeCache));
    @Nullable AccessibilityNodeInfoCompat nodeToFocus =
        TraversalStrategyUtils.findInitialFocusInNodeTree(
            traversalStrategy, scrolledNode, direction, nodeFilter);

    if (nodeToFocus == null) {
      return null;
    }

    FocusActionInfo focusActionInfo =
        new FocusActionInfo.Builder().setSourceAction(FocusActionInfo.MANUAL_SCROLL).build();

    @Nullable AccessibilityNode nodeToFocusCopy = AccessibilityNode.obtainCopy(nodeToFocus);
    return Feedback.part()
        .setFocus(Feedback.focus(nodeToFocus, focusActionInfo).build())
        .setNodeAction(
            NodeAction.builder()
                .setTarget(nodeToFocusCopy)
                .setActionId(AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId())
                .build());
  }
}
