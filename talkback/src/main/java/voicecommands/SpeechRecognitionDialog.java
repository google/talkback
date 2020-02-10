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

package com.google.android.accessibility.talkback.voicecommands;

import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;

/** Displays first-time use dialog for voice commands. {@link SpeechRecognitionManager} */
public class SpeechRecognitionDialog extends FirstTimeUseDialog {

  private final SpeechRecognitionManager speechRecognitionManager;

  public SpeechRecognitionDialog(
      TalkBackService service, SpeechRecognitionManager speechRecognitionManager) {
    super(
        service,
        /* showDialogPreference= */ R.string.pref_show_voice_command_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_voice_commands,
        /* dialogMainMessageResId= */ R.string.dialog_message_voice_commands,
        /* checkboxTextResId= */ R.string.always_show_this_message_label);
    this.speechRecognitionManager = speechRecognitionManager;
  }

  @Override
  public void handleDialogDismiss() {
    // Starts Listening voice commands after dialog is dismissed.
    if (!speechRecognitionManager.isListening()) {
      speechRecognitionManager.startListening(/* checkDialog= */ false);
    }
  }
}
