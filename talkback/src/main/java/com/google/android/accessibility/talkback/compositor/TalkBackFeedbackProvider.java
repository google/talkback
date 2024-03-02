/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.talkback.compositor;

import static android.view.View.ACCESSIBILITY_LIVE_REGION_NONE;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_VIEW_CLICKED;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_WINDOW_CONTENT_CHANGED;
import static com.google.android.accessibility.talkback.compositor.Compositor.FLAVOR_TV;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.Compositor.Event;
import com.google.android.accessibility.talkback.compositor.Compositor.Flavor;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.hint.AccessibilityFocusHint;
import com.google.android.accessibility.talkback.compositor.hint.tv.AccessibilityFocusHintForTV;
import com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor;
import com.google.android.accessibility.talkback.compositor.roledescription.TreeNodesDescription;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeAnnouncementFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeHoverEnterFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeNotificationStateChangedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeViewAccessibilityFocusedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeViewClickedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeViewFocusedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeViewLongClickedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeViewSelectedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeWindowContentChangedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeWindowStateChangedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.HintFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.InputDescribeNodeFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.InputTextFeedbackRules;
import com.google.android.accessibility.talkback.compositor.rule.InputTextPasswordFeedbackRules;
import com.google.android.accessibility.talkback.compositor.rule.InputTextSelectionFeedbackRules;
import com.google.android.accessibility.talkback.compositor.rule.KeyboardLockChangedFeedbackRules;
import com.google.android.accessibility.talkback.compositor.rule.MagnificationStateChangedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.ScrollPositionFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.ServiceStateChangedFeedbackRules;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides {@link EventFeedback} by the event feedback rule. And the event feedback rules are
 * implemented in Android programming language.
 */
public class TalkBackFeedbackProvider implements EventFeedbackProvider {

  private static final String TAG = "TalkBackFeedbackProvider";

  public static final EventFeedback EMPTY_FEEDBACK = EventFeedback.builder().build();

  private final Map<Integer, Function<HandleEventOptions, EventFeedback>> feedbackRules =
      new HashMap<>();

  TalkBackFeedbackProvider(
      @Flavor int flavor,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor,
      ProcessorPhoneticLetters processorPhoneticLetters) {
    generateEventFeedbackRules(
        flavor,
        context,
        imageContents,
        globalVariables,
        roleDescriptionExtractor,
        processorPhoneticLetters,
        new TreeNodesDescription(
            context, imageContents, globalVariables, roleDescriptionExtractor));
  }

  @VisibleForTesting
  TalkBackFeedbackProvider(Map<Integer, Function<HandleEventOptions, EventFeedback>> rules) {
    feedbackRules.putAll(rules);
  }

  @Override
  public EventFeedback buildEventFeedback(@Event int event, HandleEventOptions eventOptions) {
    Function<HandleEventOptions, EventFeedback> eventFeedbackFunction = feedbackRules.get(event);
    if (eventFeedbackFunction == null) {
      return EMPTY_FEEDBACK;
    }

    if (requestRefreshSourceNode(event, eventOptions.sourceNode)) {
      AccessibilityNodeInfoCompat newSourceNode =
          AccessibilityNodeInfoUtils.refreshNode(eventOptions.sourceNode);
      eventOptions.source(newSourceNode);
    }

    EventFeedback eventFeedback = eventFeedbackFunction.apply(eventOptions);

    LogUtils.v(TAG, " %s:  %s", Compositor.eventTypeToString(event), eventFeedback.toString());

    return eventFeedback;
  }

  /** Generates the event feedback rules for this provider. */
  private void generateEventFeedbackRules(
      @Flavor int flavor,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor,
      ProcessorPhoneticLetters processorPhoneticLetters,
      TreeNodesDescription treeNodesDescription) {
    ServiceStateChangedFeedbackRules.addFeedbackRules(feedbackRules, context);
    KeyboardLockChangedFeedbackRules.addFeedbackRules(feedbackRules, context);
    MagnificationStateChangedFeedbackRule.addFeedbackRules(feedbackRules, context, globalVariables);

    // Accessibility event
    EventTypeAnnouncementFeedbackRule.addFeedbackRule(feedbackRules, globalVariables);
    EventTypeHoverEnterFeedbackRule.addFeedbackRule(feedbackRules, globalVariables);
    EventTypeNotificationStateChangedFeedbackRule.addFeedbackRule(
        feedbackRules, context, globalVariables);
    EventTypeViewAccessibilityFocusedFeedbackRule.addFeedbackRule(
        feedbackRules,
        context,
        imageContents,
        globalVariables,
        processorPhoneticLetters,
        treeNodesDescription);
    EventTypeViewClickedFeedbackRule.addFeedbackRule(feedbackRules, context, globalVariables);
    EventTypeViewFocusedFeedbackRule.addFeedbackRule(feedbackRules, globalVariables);
    EventTypeViewLongClickedFeedbackRule.addFeedbackRule(feedbackRules);
    EventTypeViewSelectedFeedbackRule.addFeedbackRule(
        feedbackRules, globalVariables, roleDescriptionExtractor);
    EventTypeWindowContentChangedFeedbackRule.addFeedbackRule(
        feedbackRules, context, globalVariables, roleDescriptionExtractor, treeNodesDescription);
    EventTypeWindowStateChangedFeedbackRule.addFeedbackRule(
        feedbackRules, context, globalVariables);

    // Input text event
    InputTextFeedbackRules.addFeedbackRules(feedbackRules, context, globalVariables);
    InputTextPasswordFeedbackRules.addFeedbackRules(feedbackRules, context);
    InputTextSelectionFeedbackRules.addFeedbackRules(feedbackRules, context, globalVariables);
    InputDescribeNodeFeedbackRule.addFeedbackRule(feedbackRules, roleDescriptionExtractor);

    ScrollPositionFeedbackRule.addFeedbackRules(feedbackRules, context, globalVariables);

    // Hint
    AccessibilityFocusHint accessibilityFocusHint =
        (flavor == FLAVOR_TV)
            ? new AccessibilityFocusHintForTV(context, globalVariables)
            : new AccessibilityFocusHint(context, globalVariables);
    HintFeedbackRule.addFeedbackRule(
        feedbackRules, context, accessibilityFocusHint, globalVariables);
  }

  private boolean requestRefreshSourceNode(int event, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    if (event == EVENT_TYPE_VIEW_CLICKED) {
      return true;
    } else if (event == EVENT_TYPE_WINDOW_CONTENT_CHANGED) {
      return node == null ? false : (node.getLiveRegion() != ACCESSIBILITY_LIVE_REGION_NONE);
    }
    return false;
  }
}
