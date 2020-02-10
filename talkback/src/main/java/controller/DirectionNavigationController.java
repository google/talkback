/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.talkback.controller;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK;
import static com.google.android.accessibility.utils.input.CursorGranularity.CHARACTER;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.CursorGranularity.WORD;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Event-interpreter and feedback-mapper for directional navigation. This class reacts to navigation
 * actions, and either traverses with intra-node granularity or changes accessibility focus.
 */
public class DirectionNavigationController
    implements AccessibilityEventListener, KeyComboManager.KeyComboListener {

  private static final String TAG = "DirectionNavigationController";

  private final Context context;

  // TODO: Replace with ActorState.
  private AccessibilityFocusManager accessibilityFocusManager;

  private final Pipeline.FeedbackReturner pipeline;

  public DirectionNavigationController(Context context, Pipeline.FeedbackReturner pipeline) {
    this.context = context;
    this.pipeline = pipeline;
  }

  public void setAccessibilityFocusManager(AccessibilityFocusManager accessibilityFocusManager) {
    this.accessibilityFocusManager = accessibilityFocusManager;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods handling KeyCombo shortcuts.

  @Override
  public boolean onComboPerformed(int id, EventId eventId) {
    switch (id) {
      case KeyComboManager.ACTION_NAVIGATE_NEXT:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_DEFAULT:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(DEFAULT));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_DEFAULT:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(DEFAULT));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_UP:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_UP)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_DOWN:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_DOWN)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_FIRST:
        pipeline.returnFeedback(eventId, Feedback.focusTop(INPUT_MODE_KEYBOARD));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_LAST:
        pipeline.returnFeedback(eventId, Feedback.focusBottom(INPUT_MODE_KEYBOARD));
        return true;
      case KeyComboManager.ACTION_PERFORM_CLICK:
        pipeline.returnFeedback(eventId, Feedback.focus(CLICK));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_WORD:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(WORD));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WORD:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(WORD));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CHARACTER:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(CHARACTER));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHARACTER:
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(CHARACTER));
        return true;
      case KeyComboManager.ACTION_PERFORM_LONG_CLICK:
        pipeline.returnFeedback(eventId, Feedback.focus(LONG_CLICK));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_BUTTON:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_BUTTON, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_BUTTON:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_BUTTON, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CHECKBOX:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CHECKBOX, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHECKBOX:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CHECKBOX, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_ARIA_LANDMARK:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_ARIA_LANDMARK:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_EDIT_FIELD:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_EDIT_FIELD, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_EDIT_FIELD:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_EDIT_FIELD, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_FOCUSABLE_ITEM:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_FOCUSABLE_ITEM, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_FOCUSABLE_ITEM:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_FOCUSABLE_ITEM, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_1:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_1, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_1:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_1, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_2:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_2, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_2:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_2, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_3:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_3, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_3:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_3, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_4:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_4, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_4:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_4, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_5:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_5, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_5:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_5, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_6:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_6, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_6:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_6, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LINK:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LINK, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LINK:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LINK, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CONTROL:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CONTROL, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CONTROL:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CONTROL, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_GRAPHIC:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_GRAPHIC, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_GRAPHIC:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_GRAPHIC, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST_ITEM:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST_ITEM, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST_ITEM:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST_ITEM, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_TABLE:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_TABLE, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_TABLE:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_TABLE, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_COMBOBOX:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_COMBOBOX, true /* forward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_COMBOBOX:
        performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_COMBOBOX, false /* backward */, eventId);
        return true;
      case KeyComboManager.ACTION_NAVIGATE_NEXT_WINDOW:
        pipeline.returnFeedback(
            eventId, Feedback.nextWindow(INPUT_MODE_KEYBOARD).setDefaultToInputFocus(true));
        return true;
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WINDOW:
        pipeline.returnFeedback(
            eventId, Feedback.previousWindow(INPUT_MODE_KEYBOARD).setDefaultToInputFocus(true));
        return true;
      default:
        return false;
    }
  }

  private boolean performWebNavigationKeyCombo(
      @TargetType int targetType, boolean forward, EventId eventId) {
    @SearchDirection
    int direction =
        forward ? TraversalStrategy.SEARCH_FOCUS_FORWARD : TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    return pipeline.returnFeedback(
        eventId,
        Feedback.focusDirection(direction)
            .setInputMode(INPUT_MODE_KEYBOARD)
            .setHtmlTargetType(targetType));
  }

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (accessibilityFocusManager == null) {
      return;
    }
    FocusActionInfo actionInfo = accessibilityFocusManager.getFocusActionInfoFromEvent(event);
    if (actionInfo == null) {
      LogUtils.w(TAG, "Unable to find source action info for event: %s", event);
      return;
    }
    AccessibilityNodeInfoCompat sourceNode = null;
    try {
      sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());

      // Node reference only, do not recyle.
      AccessibilityNodeInfoCompat moveToNode =
          AccessibilityNodeInfoUtils.isKeyboard(event, sourceNode) ? null : sourceNode;

      @SearchDirection int linearDirection = SEARCH_FOCUS_UNKNOWN;

      if (actionInfo.sourceAction == FocusActionInfo.LOGICAL_NAVIGATION) {
        NavigationAction navigationAction = actionInfo.navigationAction;
        if ((navigationAction != null)
            && (navigationAction.originalNavigationGranularity != null)
            && navigationAction.originalNavigationGranularity.isMicroGranularity()) {

          linearDirection =
              TraversalStrategyUtils.getLogicalDirection(
                  navigationAction.searchDirection, WindowManager.isScreenLayoutRTL(context));
        }
      }

      pipeline.returnFeedback(
          eventId, Feedback.directionNavigationFollowTo(moveToNode, linearDirection));

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
    }
  }

}
