/*
 * Copyright 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.ENABLE_PASSTHROUGH;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.DialogInterface;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;

/**
 * The first time that user initiates a pass through, we show a dialog teaching users what it is.
 */
public class PassThroughModeDialog extends FirstTimeUseDialog {
  private Pipeline.FeedbackReturner pipeline;

  public PassThroughModeDialog(Context context) {
    // TODO, to meet UI doc, we need to support 1-button dialog in the future.
    super(
        context,
        /* showDialogPreference= */ R.string.pref_show_pass_through_mode_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_pass_through_mode,
        /* dialogMainMessageResId= */ R.string.dialog_message_pass_through_mode,
        /* checkboxTextResId= */ R.string.always_show_this_message_label);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    super.handleDialogClick(buttonClicked);
    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      // User confirms to enter pass through mode, so perform the pass-through action
      // unconditionally.
      pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.passThroughMode(ENABLE_PASSTHROUGH));
    }
  }
}
