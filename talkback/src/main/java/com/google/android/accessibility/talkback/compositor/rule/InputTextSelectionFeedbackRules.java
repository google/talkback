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

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL;
import static com.google.android.accessibility.talkback.compositor.Compositor.QUEUE_MODE_INTERRUPTIBLE_IF_LONG;
import static com.google.android.accessibility.talkback.compositor.Compositor.VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS;
import static com.google.android.accessibility.talkback.compositor.CompositorUtils.getCleanupString;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.content.Context;
import android.text.TextUtils;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityInterpretationFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for the input text selection events, {@link TextEventInterpretation}.
 *
 * <p>These rules will provide the event feedback output function by inputting the {@link
 * HandleEventOptions} and outputting {@link EventFeedback}.
 *
 * <ul>
 *   Input text selection events with speech feedback.
 *   <li>EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION,
 *   <li>EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION,
 *   <li>EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED,
 *   <li>EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL,
 *   <li>EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD,
 * </ul>
 *
 * <ul>
 *   Input text selection events with no speech feedback.
 *   <li>EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT,
 *   <li>EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING,
 *   <li>EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END,
 *   <li>EVENT_TYPE_INPUT_SELECTION_CUT,
 *   <li>EVENT_TYPE_INPUT_SELECTION_PASTE,
 *   <li>EVENT_TYPE_INPUT_SELECTION_SELECT_ALL,
 * </ul>
 */
public final class InputTextSelectionFeedbackRules {

  private static final String TAG = "InputTextSelectionFeedbackRules";

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
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION,
        eventOptions ->
            inputSelectionMoveCursorNoSelection(eventOptions, context, globalVariables));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION,
        eventOptions -> inputSelectionMoveCursorWithSelection(eventOptions, context));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED,
        eventOptions -> inputSelectionMoveCursorSelectionCleared(eventOptions, context));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL,
        (eventOptions) -> inputSelectionTextTraversal(eventOptions, context, globalVariables));
    eventFeedbackRules.put(
        EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD,
        eventOptions -> inputSelectionSelectAllWithKeyboard(eventOptions, context));
  }

  private static EventFeedback inputSelectionMoveCursorNoSelection(
      HandleEventOptions eventOptions, Context context, GlobalVariables globalVariables) {
    CharSequence eventTraversedText =
        getCleanupString(
            AccessibilityInterpretationFeedbackUtils.getEventTraversedText(
                eventOptions.eventInterpretation, globalVariables.getUserPreferredLocale()),
            context);
    CharSequence textTraversedState =
        globalVariables.getGlobalSayCapital()
            ? CompositorUtils.prependCapital(eventTraversedText, context)
            : eventTraversedText;
    String notificationTypeEdgeOfFieldState = "";
    if (eventOptions.eventObject.getToIndex() == 0) {
      notificationTypeEdgeOfFieldState =
          context.getString(R.string.notification_type_beginning_of_field);
    } else if (eventOptions.eventObject.getToIndex() == eventOptions.eventObject.getItemCount()) {
      notificationTypeEdgeOfFieldState = context.getString(R.string.notification_type_end_of_field);
    }

    CharSequence ttsOutput =
        CompositorUtils.joinCharSequences(textTraversedState, notificationTypeEdgeOfFieldState);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= ",
            StringBuilderUtils.optionalText("textTraversedState", textTraversedState),
            StringBuilderUtils.optionalText(
                "notificationTypeEdgeOfFieldState", notificationTypeEdgeOfFieldState)));

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

  private static EventFeedback inputSelectionMoveCursorWithSelection(
      HandleEventOptions eventOptions, Context context) {
    CharSequence deselectedText =
        getCleanupString(
            AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
                    eventOptions.eventInterpretation)
                .getDeselectedText(),
            context);
    CharSequence textDeselectedState =
        TextUtils.isEmpty(deselectedText)
            ? ""
            : context.getString(R.string.template_text_unselected, deselectedText);
    CharSequence selectedText =
        getCleanupString(
            AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
                    eventOptions.eventInterpretation)
                .getSelectedText(),
            context);
    CharSequence textSelectedState =
        TextUtils.isEmpty(selectedText)
            ? ""
            : context.getString(R.string.template_text_selected, selectedText);

    CharSequence ttsOutput =
        CompositorUtils.joinCharSequences(textDeselectedState, textSelectedState);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= ",
            StringBuilderUtils.optionalText("textDeselectedState", textDeselectedState),
            StringBuilderUtils.optionalText("textSelectedState", textSelectedState)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .build();
  }

  private static EventFeedback inputSelectionMoveCursorSelectionCleared(
      HandleEventOptions eventOptions, Context context) {
    String selectionClearedState = context.getString(R.string.notification_type_selection_cleared);
    CharSequence notificationTypeEdgeOfFieldState = "";
    if (eventOptions.eventObject.getToIndex() == 0) {
      notificationTypeEdgeOfFieldState =
          context.getString(R.string.notification_type_beginning_of_field);
    } else if (eventOptions.eventObject.getToIndex() == eventOptions.eventObject.getItemCount()) {
      notificationTypeEdgeOfFieldState = context.getString(R.string.notification_type_end_of_field);
    }

    CharSequence ttsOutput =
        CompositorUtils.joinCharSequences(selectionClearedState, notificationTypeEdgeOfFieldState);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " ttsOutputRule= ",
            StringBuilderUtils.optionalText("selectionClearedState", selectionClearedState),
            StringBuilderUtils.optionalText(
                "notificationTypeEdgeOfFieldState", notificationTypeEdgeOfFieldState)));

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(QUEUE_MODE_INTERRUPTIBLE_IF_LONG)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .build();
  }

  private static EventFeedback inputSelectionTextTraversal(
      HandleEventOptions eventOptions, Context context, GlobalVariables globalVariables) {
    CharSequence eventTraversedText =
        getCleanupString(
            AccessibilityInterpretationFeedbackUtils.getEventTraversedText(
                eventOptions.eventInterpretation, globalVariables.getUserPreferredLocale()),
            context);
    CharSequence ttsOutput =
        globalVariables.getGlobalSayCapital()
            ? CompositorUtils.prependCapital(eventTraversedText, context)
            : eventTraversedText;

    // According to TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY: Represents the event of
    // traversing the text of a view at a given movement granularity. At the same time, we set the
    // new feedback as uninterruptible when length is not too long
    int queueMode =
        (ttsOutput.length() <= VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS)
            ? QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH
            : QUEUE_MODE_INTERRUPT;
    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(queueMode)
        .setTtsAddToHistory(true)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION)
        .setTtsInterruptSameGroup(true)
        .setAdvanceContinuousReading(true)
        .setTtsForceFeedback(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(true)
        .setForceFeedbackEvenIfSsbActive(true)
        .build();
  }

  private static EventFeedback inputSelectionSelectAllWithKeyboard(
      HandleEventOptions eventOptions, Context context) {
    CharSequence textOrDescription =
        getCleanupString(
            AccessibilityInterpretationFeedbackUtils.safeTextInterpretation(
                    eventOptions.eventInterpretation)
                .getTextOrDescription(),
            context);
    // Announce selected text.
    CharSequence ttsOutput =
        context.getString(
            R.string.template_announce_selected_text,
            TextUtils.isEmpty(textOrDescription) ? "" : textOrDescription);

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

  private InputTextSelectionFeedbackRules() {}
}
