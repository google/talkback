/*
 * Copyright (C) 2024 Google Inc.
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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_HEADS_UP_NOTIFICATION_APPEARED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_HEADS_UP_NOTIFICATION_APPEARED} event. These rules will
 * provide the event feedback output function by inputting the {@link HandleEventOptions} and
 * outputting {@link EventFeedback}.
 */
public final class HeadsUpNotificationAppearedFeedbackRule {

  private static final String TAG = "HeadsUpNotificationAppearedFeedbackRule";

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
        EVENT_HEADS_UP_NOTIFICATION_APPEARED,
        eventOptions -> {
          final CharSequence gesture = globalVariables.getGestureForNextWindowShortcut();
          CharSequence ttsOutput =
              (FeatureSupport.isMultiFingerGestureSupported() && gesture != null)
                  ? context.getString(R.string.heads_up_window_available_with_gesture, gesture)
                  : context.getString(R.string.heads_up_window_available);

          final CharSequence notificationCategory =
              EventTypeNotificationStateChangedFeedbackRule.getNotificationCategoryStateText(
                  context, AccessibilityEventUtils.extractNotification(eventOptions.eventObject));
          // Disable the hint if usage hints are disabled.
          // Disable the hint for calls since calls have the media control hint.
          if (!globalVariables.getUsageHintEnabled()
              || notificationCategory
                  .toString()
                  .equals(context.getString(R.string.notification_category_call))) {
            ttsOutput = "";
          }
          // Copy the values set for incoming phone calls in
          // EventTypeNotificationStateChangedFeedbackRule.
          return EventFeedback.builder()
              .setTtsOutput(Optional.of(ttsOutput))
              .setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
              .setTtsAddToHistory(true)
              .setForceFeedbackEvenIfAudioPlaybackActive(true)
              .setForceFeedbackEvenIfMicrophoneActive(true)
              .setForceFeedbackEvenIfSsbActive(false)
              .setForceFeedbackEvenIfPhoneCallActive(true)
              .build();
        });
  }
}
