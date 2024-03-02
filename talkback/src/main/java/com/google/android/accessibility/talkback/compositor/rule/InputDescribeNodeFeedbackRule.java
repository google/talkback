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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_INPUT_DESCRIBE_NODE;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;

import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rule for {@link EVENT_INPUT_DESCRIBE_NODE}. These rules will provide the event
 * feedback output function by inputting the {@link HandleEventOptions} and outputting {@link
 * EventFeedback}.
 *
 * <p>Note: The event provides the feedback for the text-entry-key node that supports lift-to-type
 * feature.
 */
public final class InputDescribeNodeFeedbackRule {

  private static final String TAG = "InputDescribeNodeFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param roleDescriptionExtractor the node role description extractor
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      RoleDescriptionExtractor roleDescriptionExtractor) {
    eventFeedbackRules.put(
        EVENT_INPUT_DESCRIBE_NODE,
        (eventOptions) -> {
          CharSequence ttsOutput =
              roleDescriptionExtractor.nodeRoleDescriptionText(
                  eventOptions.sourceNode, eventOptions.eventObject);
          LogUtils.v(TAG, " ttsOutputRule= nodeRoleDescriptionText ");

          return EventFeedback.builder()
              .setTtsOutput(Optional.of(ttsOutput))
              .setQueueMode(QUEUE_MODE_QUEUE)
              .setTtsAddToHistory(true)
              .setAdvanceContinuousReading(true)
              .setForceFeedbackEvenIfAudioPlaybackActive(true)
              .setForceFeedbackEvenIfMicrophoneActive(true)
              .setForceFeedbackEvenIfSsbActive(true)
              .build();
        });
  }

  private InputDescribeNodeFeedbackRule() {}
}
