/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.android.accessibility.talkback.dialog;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.RESTORE_ON_NEXT_WINDOW;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.widget.DialogUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * This is a base class to handle show, dismiss and click events from dialogs. If the context is
 * from {@link TalkBackService}, sets window type to accessibility overlay, registers and
 * unregisters to {@link TalkBackService} for screen monitor and restores focus.
 */
public abstract class BaseDialog {
  private static final String TAG = BaseDialog.class.getSimpleName();

  protected final Context context;
  private final int dialogTitleResId;
  private @Nullable AlertDialog dialog;
  private @Nullable FeedbackReturner pipeline;
  private boolean isSoftInputMode = false;
  private boolean isFromLocalContextMenu = false;

  public BaseDialog(Context context, int dialogTitleResId, FeedbackReturner pipeline) {
    this.context = context;
    this.dialogTitleResId = dialogTitleResId;
    this.pipeline = pipeline;
  }

  ////////////////////////////////////////////////////////////////////////////
  // Basic setter for dialog

  /** Handles Ok and Cancel button click events in dialog. */
  public abstract void handleDialogClick(int buttonClicked);

  /** Handles dialog dismissed event. */
  public abstract void handleDialogDismiss();

  /** Gets the message string for dialog to display. */
  public abstract String getMessageString();

  /** Gets the customized view for dialog to display. */
  public abstract View getCustomizedView();

  ////////////////////////////////////////////////////////////////////////////
  // Optional setter for dialog

  /**
   * Enables the button on the dialog.
   *
   * @param button the button on the dialog, either {@link DialogInterface#BUTTON_POSITIVE} or
   *     {@link DialogInterface#BUTTON_NEGATIVE}
   * @param enabled enable status
   */
  public void setButtonEnabled(int button, boolean enabled) {
    if (dialog == null
        || button != DialogInterface.BUTTON_POSITIVE && button != DialogInterface.BUTTON_NEGATIVE) {
      return;
    }
    dialog.getButton(button).setEnabled(enabled);
  }

  /** Sets to {@code true} if focus on EditText and needs to launch IME automatically. */
  public void setSoftInputMode(boolean isSoftInputMode) {
    this.isSoftInputMode = isSoftInputMode;
  }

  /** Sets to {@code true} if the dialog is generated from local context menu. */
  public void setIsFromLocalContextMenu(boolean isFromLocalContextMenu) {
    this.isFromLocalContextMenu = isFromLocalContextMenu;
  }

  ////////////////////////////////////////////////////////////////////////////
  // Status controller for dialog

  /** Returns and shows the dialog with ok/cancel button. */
  public AlertDialog showDialog() {
    // Only show one dialog at a time.
    if (dialog != null && dialog.isShowing()) {
      return dialog;
    }

    final DialogInterface.OnClickListener onClickListener =
        (dialog, buttonClicked) -> clickDialogInternal(buttonClicked);
    final DialogInterface.OnDismissListener onDismissListener = dialog -> dismissDialogInternal();

    AlertDialog.Builder dialogBuilder =
        new AlertDialog.Builder(context)
            .setTitle(dialogTitleResId)
            .setNegativeButton(android.R.string.cancel, onClickListener)
            .setPositiveButton(android.R.string.ok, onClickListener)
            .setOnDismissListener(onDismissListener)
            .setCancelable(true);

    String message = getMessageString();
    if (!TextUtils.isEmpty(message)) {
      dialogBuilder.setMessage(message);
    }
    View customizedView = getCustomizedView();
    if (customizedView != null) {
      dialogBuilder.setView(customizedView);
    }

    dialog = dialogBuilder.create();
    if (isSoftInputMode) {
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    if (context instanceof TalkBackService) {
      DialogUtils.setWindowTypeToDialog(dialog.getWindow());
    } else {
      LogUtils.v(
          TAG,
          "Create BaseDialog from context not instance of TalkBackService, class:"
              + context.getClass());
    }
    dialog.show();

    registerServiceDialog();
    return dialog;
  }

  /** Cancels dialog. */
  public void cancelDialog() {
    if (dialog != null && dialog.isShowing()) {
      dialog.cancel();
    }
  }

  /** Dismisses dialog. */
  public void dismissDialog() {
    if (dialog != null) {
      dialog.dismiss();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////

  /** Registers screen monitor for dialog. When the screen turns off, cancel dialog. */
  private void registerServiceDialog() {
    if (context instanceof TalkBackService) {
      ((TalkBackService) context).registerDialog(dialog);
    }
  }

  /**
   * Unregisters screen monitor for dialog and restores focus if isFromLocalContextMenu is {@code
   * true}.
   */
  private void unregisterServiceDialog() {
    if (context instanceof TalkBackService) {
      ((TalkBackService) context).unregisterDialog(dialog);
    }
  }

  private void dismissDialogInternal() {
    handleDialogDismiss();
    unregisterServiceDialog();
    dialog = null;
  }

  private void clickDialogInternal(int buttonClicked) {
    handleDialogClick(buttonClicked);
    // If it is triggered by local context menu, restores focus after executing actions by clicking
    // buttons.
    if ((buttonClicked == DialogInterface.BUTTON_POSITIVE
            || buttonClicked == DialogInterface.BUTTON_NEGATIVE)
        && context instanceof TalkBackService
        && isFromLocalContextMenu
        && pipeline != null) {
      pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.focus(RESTORE_ON_NEXT_WINDOW));
    }
  }
}
