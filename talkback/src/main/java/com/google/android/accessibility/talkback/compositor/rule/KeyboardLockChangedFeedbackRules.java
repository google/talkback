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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_CAPS_LOCK_OFF;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_CAPS_LOCK_ON;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_NUM_LOCK_OFF;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_NUM_LOCK_ON;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SCROLL_LOCK_OFF;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SCROLL_LOCK_ON;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for keyboard lock events. The events are for notifying the keyboard lock
 * state. These rules will provide the event feedback output function by inputting the {@link
 * HandleEventOptions} and outputting {@link EventFeedback}.
 *
 * <ul>
 *   <li>EVENT_CAPS_LOCK_ON
 *   <li>EVENT_CAPS_LOCK_OFF
 *   <li>EVENT_NUM_LOCK_ON
 *   <li>EVENT_NUM_LOCK_OFF
 *   <li>EVENT_SCROLL_LOCK_ON
 *   <li>EVENT_SCROLL_LOCK_OFF
 * </ul>
 */
public class KeyboardLockChangedFeedbackRules {

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
        EVENT_CAPS_LOCK_ON,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.value_caps_lock_on)))
                .setQueueMode(QUEUE_MODE_INTERRUPT)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
    eventFeedbackRules.put(
        EVENT_CAPS_LOCK_OFF,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.value_caps_lock_off)))
                .setQueueMode(QUEUE_MODE_INTERRUPT)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());

    eventFeedbackRules.put(
        EVENT_NUM_LOCK_ON,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.value_num_lock_on)))
                .setQueueMode(QUEUE_MODE_INTERRUPT)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
    eventFeedbackRules.put(
        EVENT_NUM_LOCK_OFF,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.value_num_lock_off)))
                .setQueueMode(QUEUE_MODE_INTERRUPT)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());

    eventFeedbackRules.put(
        EVENT_SCROLL_LOCK_ON,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.value_scroll_lock_on)))
                .setQueueMode(QUEUE_MODE_INTERRUPT)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
    eventFeedbackRules.put(
        EVENT_SCROLL_LOCK_OFF,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(Optional.of(context.getString(R.string.value_scroll_lock_off)))
                .setQueueMode(QUEUE_MODE_INTERRUPT)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .build());
  }

  private KeyboardLockChangedFeedbackRules() {}
}
