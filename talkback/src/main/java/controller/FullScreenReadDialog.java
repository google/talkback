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

package com.google.android.accessibility.talkback.controller;

import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SAVE_GRANULARITY;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import androidx.annotation.IntDef;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * When entering continuous reading mode {@link FullScreenReadControllerApp}, a user can see
 * first-time-use dialog to understand new continuous reading controls.
 * <li>Handles two condition for focused node restoration.
 * <li>1. Continuous reading is triggered by context menu.
 * <li>2. Continuous reading is not triggered by context menu, like gestures.
 */
public class FullScreenReadDialog extends FirstTimeUseDialog implements AccessibilityEventListener {

  /** Continuous reading type for dialog */
  @IntDef({STATE_READING_FROM_BEGINNING, STATE_READING_FROM_NEXT})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ReadState {}

  private static final int STATE_READING_FROM_BEGINNING = 1;
  private static final int STATE_READING_FROM_NEXT = 2;

  @ReadState private int state;

  /** Event type that is handled by FullScreenReadDialog. */
  private static final int MASK_EVENT_HANDLED_BY_FULL_SCREEN_READ_DIALOG =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  /** The parent service */
  private final TalkBackService service;

  private final FullScreenReadController fullScreenReadController;

  private final Pipeline.FeedbackReturner pipeline;

  public FullScreenReadDialog(
      FullScreenReadController fullScreenReadController,
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline) {
    super(
        service,
        /* showDialogPreference= */ R.string.pref_show_continuous_reading_mode_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_continuous_reading_mode,
        /* dialogMainMessageResId= */ R.string.dialog_message_continuous_reading_mode,
        /* checkboxTextResId= */ R.string.always_show_this_message_label);
    this.service = service;
    this.fullScreenReadController = fullScreenReadController;
    this.pipeline = pipeline;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENT_HANDLED_BY_FULL_SCREEN_READ_DIALOG;
  }

  /**
   * TODO: defers read-from-top by window stable and defers read-from-next by a11y
   * focus event.
   */
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    // To make sure the window is ready to restore focused node and start reading.
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      service.postRemoveEventListener(this);
      service.getFullScreenReadController().startReadingWithoutDialog(EVENT_ID_UNTRACKED, state);
    }
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
    service.addEventListener(this);
  }

  /**
   * Caches focused node and granularity for dialog to restore if Reading is not triggered from
   * Context menu.
   *
   * @param state Reading state that from next or from beginning
   * @param fromContextMenu Flag to check if Reading is triggered from Context menu
   */
  public void showDialogBeforeReading(
      @FullScreenReadDialog.ReadState int state, boolean fromContextMenu, EventId eventId) {
    this.state = state;
    if (!fromContextMenu) {
      pipeline.returnFeedback(eventId, Feedback.focusDirection(SAVE_GRANULARITY));
    }
    fullScreenReadController.interrupt();
    showDialog();
  }
}
