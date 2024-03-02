/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.INTERRUPT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.MUTE_NEXT_FOCUS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.utils.Performance.EventId;

/**
 * When entering continuous reading mode {@link FullScreenReadActor}, a user can see first-time-use
 * dialog to understand new continuous reading controls.
 * <li>Handles two condition for focused node restoration.
 * <li>1. Continuous reading is triggered by context menu.
 * <li>2. Continuous reading is not triggered by context menu, like gestures.
 */
public class FullScreenReadDialog extends FirstTimeUseDialog {

  /** Event type that is handled by FullScreenReadDialog. */
  private static final int MASK_EVENT_HANDLED_BY_FULL_SCREEN_READ_DIALOG =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  /** The parent service */
  private boolean waitingForContentFocus = false;

  private Pipeline.FeedbackReturner pipeline;

  public FullScreenReadDialog(TalkBackService service) {
    super(
        service,
        /* showDialogPreference= */ R.string.pref_show_continuous_reading_mode_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_continuous_reading_mode,
        /* dialogMainMessageResId= */ R.string.dialog_message_continuous_reading_mode,
        /* checkboxTextResId= */ R.string.always_show_this_message_label);
    setIncludeNegativeButton(false);
  }

  @Override
  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public boolean isWaitingForContentFocus() {
    return waitingForContentFocus;
  }

  public void setWaitingForContentFocus(boolean wait) {
    waitingForContentFocus = wait;
  }

  /**
   * TODO: this is the only one dialog that needs the close rules to defer actions,
   * clear next window announcement. We may put these rules into {@link BaseDialog} or other unified
   * class to handle if more dialogs needs them.
   */
  @Override
  public void handleDialogDismiss() {
    EventState.getInstance()
        .setFlag(EventState.EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
    EventState.getInstance()
        .setFlag(EventState.EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.focus(MUTE_NEXT_FOCUS));
    waitingForContentFocus = true;
  }

  /** Caches focused node for dialog to restore if Reading is not triggered from Context menu. */
  public void showDialogBeforeReading(EventId eventId) {
    pipeline.returnFeedback(eventId, Feedback.continuousRead(INTERRUPT));
    showDialog();
  }
}
