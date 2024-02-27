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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_ADD;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_CLEAR;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_REMOVE;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_TEXT_REPLACE;
import static com.google.android.accessibility.talkback.compositor.Compositor.QUEUE_MODE_INTERRUPTIBLE_IF_LONG;
import static com.google.android.accessibility.talkback.compositor.CompositorUtils.getCleanupString;
import static com.google.android.accessibility.talkback.compositor.CompositorUtils.joinCharSequences;
import static com.google.android.accessibility.utils.StringBuilderUtils.joinFields;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalTag;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalText;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityInterpretationFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Event feedback rules for the input text events, {@link TextEventInterpretation}.
 *
 * <p>These rules will provide the event feedback output function by inputting the {@link
 * HandleEventOptions} and outputting {@link EventFeedback}.
 *
 * <ul>
 *   Input text events with speech feedback.
 *   <li>EVENT_TYPE_INPUT_TEXT_CLEAR,
 *   <li>EVENT_TYPE_INPUT_TEXT_REMOVE,
 *   <li>EVENT_TYPE_INPUT_TEXT_ADD,
 *   <li>EVENT_TYPE_INPUT_TEXT_REPLACE,
 * </ul>
 *
 * <ul>
 *   Input text events with no speech feedback.
 *   <li>EVENT_TYPE_INPUT_CHANGE_INVALID,
 *   <li>EVENT_TYPE_SET_TEXT_BY_ACTION,
 * </ul>
 */
public final class InputTextFeedbackRules {

  private static final String TAG = "InputTextFeedbackRules";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param globalVariables the global compositor variables
   */
  public static void addFeedbackRules(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      GlobalVariables globalVariables) {
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_TEXT_CLEAR, (eventOptions) -> inputTextClear(eventOptions, context));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_TEXT_REMOVE, (eventOptions) -> inputTextRemove(eventOptions, context));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_TEXT_ADD,
        (eventOptions) -> inputTextAdd(eventOptions, context, globalVariables));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_TEXT_REPLACE, (eventOptions) -> inputTextReplace(eventOptions, context));
  }

  private static EventFeedback inputTextClear(HandleEventOptions eventOptions, Context context) {
    AccessibilityEvent event = eventOptions.eventObject;
    boolean isCutAction =
        AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
                eventOptions.eventInterpretation)
            .getIsCutAction();
    CharSequence changedText =
        isCutAction
            ? joinCharSequences(
                context.getString(R.string.template_text_cut, event.getBeforeText()),
                context.getString(R.string.value_text_cleared))
            : context.getString(R.string.value_text_cleared);
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);
    CharSequence ttsOutput = joinCharSequences(changedText, notifyErrorState);

    LogUtils.v(
        TAG,
        joinFields(
            " ttsOutputRule= ",
            optionalText("changedText", changedText),
            optionalText("notifyErrorState", notifyErrorState),
            optionalTag("isCutAction", isCutAction)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(false)
        .build();
  }

  private static EventFeedback inputTextRemove(HandleEventOptions eventOptions, Context context) {
    TextEventInterpretation textEventInterpretation =
        AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
            eventOptions.eventInterpretation);
    boolean isCutAction = textEventInterpretation.getIsCutAction();
    CharSequence removedText = getCleanupString(textEventInterpretation.getRemovedText(), context);
    CharSequence changedText =
        isCutAction
            ? context.getString(R.string.template_text_cut, removedText)
            : context.getString(R.string.template_text_removed, removedText);
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);
    CharSequence ttsOutput = joinCharSequences(changedText, notifyErrorState);

    // Check whether to hint for WORD echoing when it's not empty.
    int queueMode =
        TextUtils.isEmpty(textEventInterpretation.getInitialWord())
            ? QUEUE_MODE_INTERRUPT
            : QUEUE_MODE_INTERRUPTIBLE_IF_LONG;

    LogUtils.v(
        TAG,
        joinFields(
            " ttsOutputRule= ",
            optionalText("changedText", changedText),
            optionalText("notifyErrorState", notifyErrorState),
            optionalTag(" isCutAction", isCutAction)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(queueMode)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .setTtsPitch(1.2d)
        .build();
  }

  private static EventFeedback inputTextAdd(
      HandleEventOptions eventOptions, Context context, GlobalVariables globalVariables) {
    TextEventInterpretation textEventInterpretation =
        AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
            eventOptions.eventInterpretation);
    CharSequence initialWord = getCleanupString(textEventInterpretation.getInitialWord(), context);
    boolean isInitialWordEmpty = TextUtils.isEmpty(initialWord);

    CharSequence changedText;
    if (!isInitialWordEmpty) {
      changedText = initialWord;
    } else {
      boolean isPasteAction = textEventInterpretation.getIsPasteAction();
      CharSequence interpretationAddedText =
          getCleanupString(textEventInterpretation.getAddedText(), context);
      CharSequence addedText =
          globalVariables.getGlobalSayCapital()
              ? CompositorUtils.prependCapital(interpretationAddedText, context)
              : interpretationAddedText;
      changedText =
          isPasteAction ? context.getString(R.string.template_text_pasted, addedText) : addedText;
      LogUtils.v(TAG, " isPasteAction=%b ", isPasteAction);
    }
    CharSequence notifyMaxLengthReachedState =
        AccessibilityNodeFeedbackUtils.notifyMaxLengthReachedStateText(
            eventOptions.sourceNode, context);
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);

    CharSequence ttsOutput =
        joinCharSequences(changedText, notifyMaxLengthReachedState, notifyErrorState);

    // Check whether to hint for WORD echoing when it's not empty.
    int queueMode = isInitialWordEmpty ? QUEUE_MODE_INTERRUPT : QUEUE_MODE_INTERRUPTIBLE_IF_LONG;

    LogUtils.v(
        TAG,
        joinFields(
            " ttsOutputRule= ",
            optionalText("changedText", changedText),
            optionalText("notifyMaxLengthReachedState", notifyMaxLengthReachedState),
            optionalText("notifyErrorState", notifyErrorState)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(queueMode)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .build();
  }

  private static EventFeedback inputTextReplace(HandleEventOptions eventOptions, Context context) {
    TextEventInterpretation textEventInterpretation =
        AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
            eventOptions.eventInterpretation);
    CharSequence addedText = getCleanupString(textEventInterpretation.getAddedText(), context);
    CharSequence removedText = getCleanupString(textEventInterpretation.getRemovedText(), context);
    CharSequence changedText =
        context.getString(R.string.template_text_replaced, addedText, removedText);
    boolean isPasteAction =
        AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
                eventOptions.eventInterpretation)
            .getIsPasteAction();
    CharSequence spellingTextAddedState = isPasteAction ? "" : spelling(addedText, context);
    CharSequence notifyMaxLengthReachedState =
        AccessibilityNodeFeedbackUtils.notifyMaxLengthReachedStateText(
            eventOptions.sourceNode, context);
    CharSequence notifyErrorState =
        AccessibilityNodeFeedbackUtils.notifyErrorStateText(eventOptions.sourceNode, context);

    CharSequence ttsOutput =
        joinCharSequences(
            changedText, spellingTextAddedState, notifyMaxLengthReachedState, notifyErrorState);

    LogUtils.v(
        TAG,
        joinFields(
            " ttsOutputRule= ",
            optionalText("changedText", changedText),
            optionalText("spellingTextAddedState", spellingTextAddedState),
            optionalText("notifyMaxLengthReachedState", notifyMaxLengthReachedState),
            optionalText("notifyErrorState", notifyErrorState)));

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

  /** Returns the text for feedback spelling the words. */
  public static CharSequence spelling(CharSequence word, Context context) {
    if (word.length() <= 1) {
      return "";
    }
    return Stream.of(word)
        .map(c -> SpeechCleanupUtils.cleanUp(context, c))
        .collect(Collectors.joining());
  }

  private InputTextFeedbackRules() {}
}
