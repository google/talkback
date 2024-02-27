/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor.rule;

import static android.view.View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE;
import static android.view.View.ACCESSIBILITY_LIVE_REGION_NONE;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_VIEW_SELECTED;
import static com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider.EMPTY_FEEDBACK;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EarconFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_TYPE_VIEW_SELECTED} event. These rules will provide the
 * event feedback output function by inputting the {@link HandleEventOptions} and outputting {@link
 * EventFeedback}.
 */
public final class EventTypeViewSelectedFeedbackRule {

  private static final String TAG = "EventTypeViewSelectedFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param globalVariables the global compositor variables
   * @param roleDescriptionExtractor the node role description extractor
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor) {
    eventFeedbackRules.put(
        EVENT_TYPE_VIEW_SELECTED,
        (eventOptions) ->
            viewSelected(
                eventOptions.eventObject,
                eventOptions.sourceNode,
                globalVariables,
                roleDescriptionExtractor));
  }

  private static EventFeedback viewSelected(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor) {
    if (node == null) {
      LogUtils.e(TAG, "viewSelected() error: node is null.");
      return EMPTY_FEEDBACK;
    }

    Locale preferredLocale = globalVariables.getPreferredLocaleByNode(node);
    int role = Role.getRole(node);
    return EventFeedback.builder()
        .setTtsOutput(computeTtsOutput(node, event, preferredLocale, roleDescriptionExtractor))
        .setQueueMode(queueMode(node, role))
        .setTtsAddToHistory(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(false)
        .setTtsClearQueueGroup(ttsClearQueueGroup(role))
        .setTtsSkipDuplicate((role == Role.ROLE_PROGRESS_BAR || role == Role.ROLE_SEEK_CONTROL))
        .setHaptic(haptic(role, event, preferredLocale))
        .setEarcon(earcon(role, event, node, preferredLocale))
        .setEarconRate(EarconFeedbackUtils.getProgressBarChangeEarconRate(event, node))
        .setEarconVolume(EarconFeedbackUtils.getProgressBarChangeEarconVolume(event, node))
        .build();
  }

  private static Optional<CharSequence> computeTtsOutput(
      AccessibilityNodeInfoCompat node,
      AccessibilityEvent event,
      Locale preferredLocale,
      RoleDescriptionExtractor roleDescriptionExtractor) {
    CharSequence output;
    CharSequence eventDescription =
        AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
            event, preferredLocale);
    int role = Role.getRole(node);
    switch (role) {
      case Role.ROLE_SEEK_CONTROL:
        output = roleDescriptionExtractor.nodeRoleDescriptionText(node, event);
        LogUtils.v(TAG, "computeTtsOutput(): role= seekBar, ttsOutput= nodeRoleDescriptionText");
        break;
      case Role.ROLE_PROGRESS_BAR:
        StringBuilder log = new StringBuilder();
        log.append("role= progressBar");
        boolean hasUniqueEventText =
            !event.getText().isEmpty() && !node.getText().equals(eventDescription);
        if ((event.getSource() == null
            || node.isFocused()
            || node.isAccessibilityFocused()
            || node.getLiveRegion() != ACCESSIBILITY_LIVE_REGION_NONE)) {
          output =
              hasUniqueEventText
                  ? eventDescription
                  : roleDescriptionExtractor.getSeekBarStateDescription(node, event);
          log.append(", ttsOutput=")
              .append(hasUniqueEventText ? "eventDescription" : "seekBarStateDescription");
        } else {
          output = "";
        }
        log.append(", hasUniqueEventText=").append(hasUniqueEventText);
        log.append(", nodeIsFocused=").append(node.isFocused());
        log.append(", nodeIsAccessibilityFocused=").append(node.isAccessibilityFocused());
        log.append(", nodeLiveRegion=").append(node.getLiveRegion());
        LogUtils.v(TAG, "computeTtsOutput(): %s", log.toString());
        break;
      default:
        output = eventDescription;
        LogUtils.v(TAG, "computeTtsOutput(): role= %d, ttsOutput= eventDescription", role);
    }
    return Optional.of(output);
  }

  private static int queueMode(AccessibilityNodeInfoCompat node, int role) {
    if (role == Role.ROLE_PROGRESS_BAR) {
      return (node.getLiveRegion() == ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
          ? QUEUE_MODE_INTERRUPT
          : QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
    } else if (role == Role.ROLE_ACTION_BAR_TAB || role == Role.ROLE_TAB_BAR) {
      return QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
    } else {
      return QUEUE_MODE_INTERRUPT;
    }
  }

  private static int ttsClearQueueGroup(int role) {
    return (role == Role.ROLE_PROGRESS_BAR || role == Role.ROLE_SEEK_CONTROL)
        ? role
        : SpeechController.UTTERANCE_GROUP_DEFAULT;
  }

  private static int earcon(
      int role,
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Locale preferredLocale) {
    if (role == Role.ROLE_PROGRESS_BAR) {
      return EarconFeedbackUtils.getProgressBarChangeEarcon(event, node, preferredLocale);
    }
    if (role == Role.ROLE_SEEK_CONTROL) {
      return -1;
    }
    return TextUtils.isEmpty(
            AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
                event, preferredLocale))
        ? -1
        : R.raw.focus_actionable;
  }

  private static int haptic(int role, AccessibilityEvent event, Locale preferredLocale) {
    if (role == Role.ROLE_PROGRESS_BAR || role == Role.ROLE_SEEK_CONTROL) {
      return -1;
    }
    return TextUtils.isEmpty(
            AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
                event, preferredLocale))
        ? -1
        : R.array.view_focused_or_selected_pattern;
  }

  private EventTypeViewSelectedFeedbackRule() {}
}
