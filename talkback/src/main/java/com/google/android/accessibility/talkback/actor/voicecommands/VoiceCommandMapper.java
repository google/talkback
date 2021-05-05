/*
 * Copyright (C) 2020 Google Inc.
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
package com.google.android.accessibility.talkback.actor.voicecommands;

import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_NEXT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_TOP;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.BRIGHTEN;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.DIM;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.COPY;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CUT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.DELETE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.END_SELECT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.INSERT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.PASTE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.SELECT_ALL;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.START_SELECT;
import static com.google.android.accessibility.talkback.Feedback.VoiceRecognition.Action.SHOW_COMMAND_LIST;
import static com.google.android.accessibility.talkback.actor.SystemActionPerformer.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.ContinuousRead;
import com.google.android.accessibility.talkback.Feedback.DimScreen;
import com.google.android.accessibility.talkback.Feedback.SystemAction;
import com.google.android.accessibility.talkback.Feedback.VoiceRecognition;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Mappers;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Feedback-mapper for voice command. This class reacts to voice command actions. */
public class VoiceCommandMapper {
  public static final String LOG_TAG = "VoiceCommandMapper";

  public static @Nullable Feedback handleSpeechCommand(
      EventId eventId, Mappers.Variables variables, int depth) {
    Interpretation.VoiceCommand voiceCommand = variables.voiceCommand(depth);

    LogUtils.v(LOG_TAG, "handleSpeechCommand() command=\"%s\"", voiceCommand.toString());

    @Nullable AccessibilityNodeInfoCompat node = voiceCommand.targetNode();
    @Nullable CharSequence text = voiceCommand.text();

    switch (voiceCommand.command()) {
      case VOICE_COMMAND_NEXT_GRANULARITY:
        if (voiceCommand.granularity() == null) {
          return toFeedback(eventId, Feedback.nextHeading(INPUT_MODE_TOUCH).build());
        } else {
          return toFeedback(
              eventId,
              Feedback.nextGranularity(INPUT_MODE_TOUCH, voiceCommand.granularity()).build());
        }

      case VOICE_COMMAND_SELECT_ALL:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, SELECT_ALL));
        }
        break;

      case VOICE_COMMAND_START_SELECT:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, START_SELECT));
        }
        break;

      case VOICE_COMMAND_END_SELECT:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, END_SELECT));
        }
        break;

      case VOICE_COMMAND_COPY:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, COPY));
        }
        break;

      case VOICE_COMMAND_CUT:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, CUT));
        }
        break;

      case VOICE_COMMAND_PASTE:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, PASTE));
        }
        break;

      case VOICE_COMMAND_DELETE:
        if (node != null) {
          return toFeedback(eventId, Feedback.edit(node, DELETE));
        }
        break;

      case VOICE_COMMAND_INSERT:
        if (node != null && !TextUtils.isEmpty(text)) {
          return toFeedback(eventId, Feedback.edit(node, INSERT).setText(text));
        }
        break;

      case VOICE_COMMAND_LABEL:
        if (node != null && !TextUtils.isEmpty(text)) {
          return toFeedback(eventId, Feedback.label(text.toString(), node).build().label());
        }
        break;

      case VOICE_COMMAND_REPEAT_SEARCH:
        return toFeedback(eventId, Feedback.repeatSearch());

      case VOICE_COMMAND_FIND:
        if (!TextUtils.isEmpty(text)) {
          return toFeedback(eventId, Feedback.searchFromTop(text));
        }
        break;

      case VOICE_COMMAND_START_AT_TOP:
        return toFeedback(eventId, START_AT_TOP);

      case VOICE_COMMAND_START_AT_NEXT:
        return toFeedback(eventId, START_AT_NEXT);

      case VOICE_COMMAND_FIRST:
        return toFeedback(eventId, Feedback.focusTop(INPUT_MODE_TOUCH).build().focusDirection());

      case VOICE_COMMAND_LAST:
        return toFeedback(eventId, Feedback.focusBottom(INPUT_MODE_TOUCH).build().focusDirection());

      case VOICE_COMMAND_HOME:
        return toFeedback(eventId, AccessibilityService.GLOBAL_ACTION_HOME);

      case VOICE_COMMAND_BACK:
        return toFeedback(eventId, AccessibilityService.GLOBAL_ACTION_BACK);

      case VOICE_COMMAND_RECENT:
        return toFeedback(eventId, AccessibilityService.GLOBAL_ACTION_RECENTS);

      case VOICE_COMMAND_ALL_APPS:
        return toFeedback(eventId, GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);

      case VOICE_COMMAND_NOTIFICATIONS:
        return toFeedback(eventId, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);

      case VOICE_COMMAND_QUICK_SETTINGS:
        return toFeedback(eventId, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);

      case VOICE_COMMAND_BRIGHTEN_SCREEN:
        return toFeedback(eventId, BRIGHTEN);

      case VOICE_COMMAND_DIM_SCREEN:
        return toFeedback(eventId, DIM);

      case VOICE_COMMAND_SHOW_COMMAND_LIST:
        return toFeedback(eventId, SHOW_COMMAND_LIST);

      default:
        break;
    }
    return null;
  }

  private static Feedback toFeedback(
      @Nullable EventId eventId, Feedback.EditText.Builder editText) {
    return Feedback.create(eventId, Feedback.part().setEdit(editText.build()).build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, int systemActionId) {
    return Feedback.create(
        eventId, Feedback.part().setSystemAction(SystemAction.create(systemActionId)).build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, VoiceRecognition.Action action) {
    return Feedback.create(
        eventId,
        Feedback.part()
            .setVoiceRecognition(Feedback.voiceRecognition(action).build().voiceRecognition())
            .build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, DimScreen.Action action) {
    return Feedback.create(
        eventId,
        Feedback.part().setDimScreen(Feedback.dimScreen(action).build().dimScreen()).build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, ContinuousRead.Action action) {
    return Feedback.create(
        eventId,
        Feedback.part()
            .setContinuousRead(Feedback.continuousRead(action).build().continuousRead())
            .build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, Feedback.Focus.Builder focus) {
    return Feedback.create(eventId, Feedback.part().setFocus(focus.build()).build());
  }

  private static Feedback toFeedback(
      @Nullable EventId eventId, Feedback.FocusDirection focusDirection) {
    return Feedback.create(eventId, Feedback.part().setFocusDirection(focusDirection).build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, Feedback.Label label) {
    return Feedback.create(eventId, Feedback.part().setLabel(label).build());
  }
}
