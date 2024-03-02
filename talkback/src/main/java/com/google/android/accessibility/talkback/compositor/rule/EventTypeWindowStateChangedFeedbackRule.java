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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_WINDOW_STATE_CHANGED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.AccessibilityInterpretationFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.EventInterpretation;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_TYPE_WINDOW_STATE_CHANGED} event. These rules will provide
 * the event feedback output function by inputting the {@link HandleEventOptions} and outputting
 * {@link EventFeedback}.
 */
public final class EventTypeWindowStateChangedFeedbackRule {

  private static final String TAG = "EventTypeWindowStateChangedFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param globalVariables the global compositor variables
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      GlobalVariables globalVariables) {
    eventFeedbackRules.put(
        EVENT_TYPE_WINDOW_STATE_CHANGED,
        eventOptions -> windowStateChanged(eventOptions, context, globalVariables));
  }

  private static EventFeedback windowStateChanged(
      HandleEventOptions eventOptions, Context context, GlobalVariables globalVariables) {
    int role = Role.getSourceRole(eventOptions.eventObject);
    CharSequence eventDescription =
        AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
            eventOptions.eventObject, globalVariables.getUserPreferredLocale());

    CharSequence ttsOutput =
        getWindowStateChangedStateText(eventOptions.eventObject, role, eventDescription, context);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= windowStateChangedState, ",
            StringBuilderUtils.optionalText("role", Role.roleToString(role))));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(queueMode(role, ttsOutput))
        .setTtsAddToHistory(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(
            forceFeedbackEvenIfMicrophoneActive(eventOptions.eventInterpretation))
        .setForceFeedbackEvenIfSsbActive(true)
        .build();
  }

  private static CharSequence getWindowStateChangedStateText(
      AccessibilityEvent event, int role, CharSequence eventDescription, Context context) {
    switch (role) {
      case Role.ROLE_DRAWER_LAYOUT:
        return context.getString(R.string.template_drawer_opened, eventDescription);
      case Role.ROLE_ICON_MENU:
        return context.getString(R.string.value_options_menu_open);
      case Role.ROLE_SLIDING_DRAWER:
        return context.getString(R.string.value_sliding_drawer_opened);
      case Role.ROLE_LIST:
        if (TextUtils.isEmpty(eventDescription)) {
          return "";
        }
        // TODO: Consider removing this case, since we do not see it occur.
        // Feedback only when the first event text element and event content description are not
        // available.
        int itemCount = event.getItemCount();
        if (TextUtils.isEmpty(event.getContentDescription())
            && (!event.getText().isEmpty() && TextUtils.isEmpty(event.getText().get(0)))
            && itemCount > 0) {
          return context
              .getResources()
              .getQuantityString(
                  R.plurals.template_containers, itemCount, eventDescription, itemCount);
        }
        return "";
      default:
        return "";
    }
  }

  private static int queueMode(int role, CharSequence ttsOutput) {
    if (role == Role.ROLE_ICON_MENU || (role == Role.ROLE_LIST && !TextUtils.isEmpty(ttsOutput))) {
      return QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
    } else {
      return QUEUE_MODE_INTERRUPT;
    }
  }

  private static boolean forceFeedbackEvenIfMicrophoneActive(
      EventInterpretation eventInterpretation) {
    return !AccessibilityInterpretationFeedbackUtils.safeAccessibilityFocusInterpretation(
            eventInterpretation)
        .getIsInitialFocusAfterScreenStateChange();
  }

  private EventTypeWindowStateChangedFeedbackRule() {}
}
