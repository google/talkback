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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_ANNOUNCEMENT;
import static com.google.android.accessibility.talkback.compositor.Compositor.QUEUE_MODE_INTERRUPTIBLE_IF_LONG;

import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_TYPE_ANNOUNCEMENT} event.
 *
 * <p>These rules will provide the event feedback output function by inputting the {@link
 * HandleEventOptions} and outputting {@link EventFeedback}.
 */
public class EventTypeAnnouncementFeedbackRule {

  private static final String TAG = "EventTypeAnnouncementFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param globalVariables the global compositor variables
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      GlobalVariables globalVariables) {
    eventFeedbackRules.put(
        EVENT_TYPE_ANNOUNCEMENT,
        (eventOptions) -> {
          CharSequence ttsOutput =
              AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
                  eventOptions.eventObject, globalVariables.getUserPreferredLocale());

          LogUtils.v(TAG, " ttsOutputRule= eventContentDescriptionOrEventAggregateText, ");

          return EventFeedback.builder()
              .setTtsOutput(Optional.of(ttsOutput))
              .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
              .setTtsAddToHistory(true)
              .setTtsSkipDuplicate(true)
              .setForceFeedbackEvenIfAudioPlaybackActive(true)
              .setForceFeedbackEvenIfMicrophoneActive(true)
              .setForceFeedbackEvenIfSsbActive(false)
              .setHaptic(R.array.notification_pattern)
              .build();
        });
  }

  private EventTypeAnnouncementFeedbackRule() {}
}
