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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE;
import static com.google.android.accessibility.talkback.compositor.Compositor.QUEUE_MODE_INTERRUPTIBLE_IF_LONG;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for the input text password events, {@link TextEventInterpretation}.
 *
 * <p>These rules will provide the event feedback output function by inputting the {@link
 * HandleEventOptions} and outputting {@link EventFeedback}.
 *
 * <ul>
 *   Input text password events with speech feedback.
 *   <li>EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD,
 *   <li>EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE,
 *   <li>EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE,
 * </ul>
 */
public final class InputTextPasswordFeedbackRules {

  private static final String TAG = "InputTextPasswordFeedbackRules";

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
        EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD,
        (eventOptions) -> inputTextPasswordAdd(eventOptions, context));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE,
        (eventOptions) -> inputTextPasswordRemove(eventOptions, context));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE,
        (eventOptions) -> inputTextPasswordReplace(eventOptions, context));
  }

  private static EventFeedback inputTextPasswordAdd(
      HandleEventOptions eventOptions, Context context) {
    String symbolBullet = context.getString(com.google.android.accessibility.utils.R.string.symbol_bullet);
    CharSequence notifyMaxLengthReachedState =
        AccessibilityNodeFeedbackUtils.notifyMaxLengthReachedStateText(
            eventOptions.sourceNode, context);
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);

    CharSequence ttsOutput =
        CompositorUtils.joinCharSequences(
            symbolBullet, notifyMaxLengthReachedState, notifyErrorState);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= ",
            StringBuilderUtils.optionalText("symbolBullet", symbolBullet),
            StringBuilderUtils.optionalText(
                "notifyMaxLengthReachedState", notifyMaxLengthReachedState),
            StringBuilderUtils.optionalText("notifyErrorState", notifyErrorState)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .build();
  }

  private static EventFeedback inputTextPasswordRemove(
      HandleEventOptions eventOptions, Context context) {
    String textRemovedState =
        context.getString(
            R.string.template_text_removed, context.getString(com.google.android.accessibility.utils.R.string.symbol_bullet));
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);
    CharSequence ttsOutput = CompositorUtils.joinCharSequences(textRemovedState, notifyErrorState);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= ",
            StringBuilderUtils.optionalText("textRemovedState", textRemovedState),
            StringBuilderUtils.optionalText("notifyErrorState", notifyErrorState)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .setTtsPitch(1.2d)
        .build();
  }

  private static EventFeedback inputTextPasswordReplace(
      HandleEventOptions eventOptions, Context context) {
    String addedCount = String.valueOf(eventOptions.eventObject.getAddedCount());
    String removedCount = String.valueOf(eventOptions.eventObject.getRemovedCount());
    String textReplacedState =
        context.getString(R.string.template_replaced_characters, addedCount, removedCount);
    CharSequence notifyMaxLengthReachedState =
        AccessibilityNodeFeedbackUtils.notifyMaxLengthReachedStateText(
            eventOptions.sourceNode, context);
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);

    CharSequence ttsOutput =
        CompositorUtils.joinCharSequences(
            textReplacedState, notifyMaxLengthReachedState, notifyErrorState);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= ",
            StringBuilderUtils.optionalText("textReplacedState", textReplacedState),
            StringBuilderUtils.optionalText(
                "notifyMaxLengthReachedState", notifyMaxLengthReachedState),
            StringBuilderUtils.optionalText("notifyErrorState", notifyErrorState)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .setTtsPitch(1.2d)
        .build();
  }

  private InputTextPasswordFeedbackRules() {}
}
