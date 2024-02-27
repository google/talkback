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
package com.google.android.accessibility.talkback.compositor.rule;

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_ORIENTATION_LANDSCAPE;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_ORIENTATION_PORTRAIT;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SPOKEN_FEEDBACK_DISABLED;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SPOKEN_FEEDBACK_ON;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for service events. The service events are for notifying the system
 * configurations changed and TalkBack service state. These rules will provide the event feedback
 * output function by inputting the {@link HandleEventOptions} and outputting {@link EventFeedback}.
 *
 * <ul>
 *   <li>EVENT_SPOKEN_FEEDBACK_ON
 *   <li>EVENT_SPOKEN_FEEDBACK_DISABLED
 *   <li>EVENT_ORIENTATION_PORTRAIT
 *   <li>EVENT_ORIENTATION_LANDSCAPE
 * </ul>
 */
public final class ServiceStateChangedFeedbackRules {

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   */
  public static void addFeedbackRules(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context) {

    eventFeedbackRules.put(
        EVENT_SPOKEN_FEEDBACK_ON,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.talkback_on)))
                .setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
    eventFeedbackRules.put(
        EVENT_SPOKEN_FEEDBACK_DISABLED,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.talkback_disabled)))
                .setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());

    eventFeedbackRules.put(
        EVENT_ORIENTATION_PORTRAIT,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.orientation_portrait)))
                .setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
    eventFeedbackRules.put(
        EVENT_ORIENTATION_LANDSCAPE,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.orientation_landscape)))
                .setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
  }

  private ServiceStateChangedFeedbackRules() {}
}
