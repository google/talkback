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

import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_ADVANCE_CONTINUOUS_READING;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_EARCON;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_EARCON_RATE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_EARCON_VOLUME;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_HAPTIC;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_PREVENT_DEVICE_SLEEP;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_REFRESH_SOURCE_NODE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_ADD_TO_HISTORY;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_CLEAR_QUEUE_GROUP;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_FORCE_FEEDBACK;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_INTERRUPT_SAME_GROUP;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_OUTPUT;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_PITCH;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_QUEUE_MODE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.OUTPUT_TTS_SKIP_DUPLICATE;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.Compositor.Event;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.Optional;

/**
 * Provides {@link EventFeedback} by the event feedback rule. And the rule is parsed from
 * compositor.json by {@link ParseTree}.
 */
public class ParseTreeFeedbackProvider implements EventFeedbackProvider {

  /** ParseTree event parser that provides feedback outputs. */
  private final ParseTree parseTree;
  /** VariableFactory to create the variable delegate. */
  private final VariablesFactory variablesFactory;

  ParseTreeFeedbackProvider(ParseTree parseTree, VariablesFactory variablesFactory) {
    this.variablesFactory = variablesFactory;
    this.parseTree = parseTree;
  }

  @Override
  public EventFeedback buildEventFeedback(@Event int event, HandleEventOptions eventOptions) {
    ParseTree.VariableDelegate variables =
        variablesFactory.createLocalVariableDelegate(
            eventOptions.eventObject, eventOptions.sourceNode, eventOptions.eventInterpretation);

    // Refresh source node, and re-create the variable delegate using fresh source node if needed.
    boolean refreshSourceNode =
        parseTree.parseEventToBool(
            event, OUTPUT_REFRESH_SOURCE_NODE, /* defaultValue= */ false, variables);
    if (eventOptions.sourceNode != null && refreshSourceNode) {
      AccessibilityNodeInfoCompat newSourceNode =
          AccessibilityNodeInfoUtils.refreshNode(eventOptions.sourceNode);
      eventOptions.source(newSourceNode);
      variables =
          variablesFactory.createLocalVariableDelegate(
              eventOptions.eventObject, eventOptions.sourceNode, eventOptions.eventInterpretation);
    }

    return EventFeedback.builder()
        .setTtsOutput(
            Optional.ofNullable(parseTree.parseEventToString(event, OUTPUT_TTS_OUTPUT, variables)))
        .setQueueMode(
            parseTree.parseEventToEnum(
                event,
                OUTPUT_TTS_QUEUE_MODE,
                /* defaultValue= */ SpeechController.QUEUE_MODE_INTERRUPT,
                variables))
        .setTtsAddToHistory(
            parseTree.parseEventToBool(
                event, OUTPUT_TTS_ADD_TO_HISTORY, /* defaultValue= */ false, variables))
        .setForceFeedbackEvenIfAudioPlaybackActive(
            parseTree.parseEventToBool(
                event,
                OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE,
                /* defaultValue= */ false,
                variables))
        .setForceFeedbackEvenIfMicrophoneActive(
            parseTree.parseEventToBool(
                event,
                OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE,
                /* defaultValue= */ false,
                variables))
        .setForceFeedbackEvenIfSsbActive(
            parseTree.parseEventToBool(
                event,
                OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE,
                /* defaultValue= */ false,
                variables))
        .setForceFeedbackEvenIfPhoneCallActive(
            parseTree.parseEventToBool(
                event,
                OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE,
                /* defaultValue= */ true,
                variables))
        .setTtsForceFeedback(
            parseTree.parseEventToBool(
                event, OUTPUT_TTS_FORCE_FEEDBACK, /* defaultValue= */ false, variables))
        .setTtsInterruptSameGroup(
            parseTree.parseEventToBool(
                event, OUTPUT_TTS_INTERRUPT_SAME_GROUP, /* defaultValue= */ false, variables))
        .setTtsClearQueueGroup(
            parseTree.parseEventToEnum(
                event,
                OUTPUT_TTS_CLEAR_QUEUE_GROUP,
                /* defaultValue= */ SpeechController.UTTERANCE_GROUP_DEFAULT,
                variables))
        .setTtsSkipDuplicate(
            parseTree.parseEventToBool(
                event, OUTPUT_TTS_SKIP_DUPLICATE, /* defaultValue= */ false, variables))
        .setTtsPitch(
            parseTree.parseEventToNumber(
                event, OUTPUT_TTS_PITCH, /* defaultValue= */ 1.0d, variables))
        .setAdvanceContinuousReading(
            parseTree.parseEventToBool(
                event, OUTPUT_ADVANCE_CONTINUOUS_READING, /* defaultValue= */ false, variables))
        .setPreventDeviceSleep(
            parseTree.parseEventToBool(
                event, OUTPUT_PREVENT_DEVICE_SLEEP, /* defaultValue= */ false, variables))
        .setRefreshSourceNode(refreshSourceNode)
        .setHaptic(
            parseTree.parseEventToInteger(event, OUTPUT_HAPTIC, /* defaultValue= */ -1, variables))
        .setEarcon(
            parseTree.parseEventToInteger(event, OUTPUT_EARCON, /* defaultValue= */ -1, variables))
        .setEarconRate(
            parseTree.parseEventToNumber(
                event, OUTPUT_EARCON_RATE, /* defaultValue= */ 1.0d, variables))
        .setEarconVolume(
            parseTree.parseEventToNumber(
                event, OUTPUT_EARCON_VOLUME, /* defaultValue= */ 1.0d, variables))
        .build();
  }
}
