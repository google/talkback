/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.actor.voicecommands;

import static com.google.android.accessibility.talkback.Feedback.VoiceRecognition.Action.START_LISTENING;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.DialogInterface;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;

/** Displays first-time use dialog for voice commands. {@link SpeechRecognition} */
public class SpeechRecognitionDialog extends FirstTimeUseDialog {

  private final Pipeline.FeedbackReturner pipeline;

  public SpeechRecognitionDialog(Context context, Pipeline.FeedbackReturner pipeline) {
    super(
        context,
        /* showDialogPreference= */ R.string.pref_show_voice_command_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_voice_commands,
        /* dialogMainMessageResId= */ R.string.dialog_message_voice_commands,
        /* checkboxTextResId= */ R.string.always_show_this_message_label);
    this.pipeline = pipeline;
    this.setMainMessage(
        context.getString(
            (R.string.dialog_message_voice_commands), context.getString(R.string.title_pref_help)));
    this.setPositiveButtonStringRes(R.string.start_voice_command);
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    super.handleDialogClick(buttonClicked);
    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED, Feedback.voiceRecognition(START_LISTENING, /* checkDialog= */ false));
    }
  }
}
