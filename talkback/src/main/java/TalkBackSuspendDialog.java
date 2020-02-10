/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.DialogInterface;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;
import com.google.android.accessibility.utils.Performance.EventId;

/**
 * Shows a confirmation dialog when user suspends TalkBack. {@link
 * TalkBackService#requestSuspendTalkBack}
 */
public class TalkBackSuspendDialog extends FirstTimeUseDialog {

  private final TalkBackService service;

  public TalkBackSuspendDialog(TalkBackService service) {
    super(
        service,
        /* showDialogPreference= */ R.string.pref_show_suspension_confirmation_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_suspend_talkback,
        /* dialogMainMessageResId= */ R.string.dialog_message_suspend_talkback,
        /* checkboxTextResId= */ R.string.show_suspend_warning_label);
    this.service = service;
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    super.handleDialogClick(buttonClicked);

    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking menu events performance.
      service.suspendTalkBack(eventId);
    }
  }

  /**
   * Shows a dialog asking the user to confirm suspension of TalkBack.
   *
   * @param automaticResume preference specifying when TalkBack should automatically resume {@link
   *     TalkBackService#automaticResume}
   */
  public void confirmSuspendTalkBack(String automaticResume) {
    int messageResId;
    if (automaticResume.equals(service.getString(R.string.resume_screen_keyguard))) {
      messageResId = R.string.message_resume_keyguard;
    } else if (automaticResume.equals(service.getString(R.string.resume_screen_manual))) {
      messageResId = R.string.message_resume_manual;
    } else { // screen on is the default value
      messageResId = R.string.message_resume_screen_on;
    }
    setCustomizedDialogMessageResId(messageResId, INVALID_RES_ID);
    showDialog();
  }
}
