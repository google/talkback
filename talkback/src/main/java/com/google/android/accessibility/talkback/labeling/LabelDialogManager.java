/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.labeling;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import androidx.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/**
 * Manages the dialogs for adding, editing, and removing custom labels. If the context is from
 * {@link com.google.android.accessibility.talkback.TalkBackService}, shows dialog as accessibility
 * overlay.
 */
public class LabelDialogManager {

  private final Context context;
  private final CustomLabelManager labelManager;

  private LabelDialogManager(Context context) {
    this.context = context;
    labelManager = new CustomLabelManager(context);
  }

  /**
   * Shows the dialog to add a label for the given node in the given context.
   *
   * @param context Context to display the dialog in
   * @param resourceName Resource name from Accessibility node to add a label
   * @param needToRestoreFocus Sets to {@code true} if caller needs to restore focus
   * @param pipeline the FeedbackReturner which needs to perform restore focus
   * @return True if showing the dialog was successful, otherwise false
   */
  public static boolean addLabel(
      Context context,
      String resourceName,
      boolean needToRestoreFocus,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    if (context == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context);
    dialogManager.showAddLabelDialog(resourceName, needToRestoreFocus, pipeline);
    return true;
  }

  /**
   * Shows the dialog to edit the given label in the given context.
   *
   * @param context Context to display the dialog in
   * @param viewLabelId Label Id to edit.
   * @param needToRestoreFocus Sets to {@code true} if caller needs to restore focus
   * @param pipeline the FeedbackReturner which needs to perform restore focus
   * @return True if showing the dialog was successful, otherwise false
   */
  public static boolean editLabel(
      Context context,
      long viewLabelId,
      boolean needToRestoreFocus,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    if ((context == null) || (viewLabelId == Label.NO_ID)) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context);
    dialogManager.showEditLabelDialog(viewLabelId, needToRestoreFocus, pipeline);
    return true;
  }


  /**
   * Computes the common name for an application.
   *
   * @param packageName The package name of the application
   * @return The common name for the application
   */
  private static CharSequence getApplicationName(Context context, String packageName) {
    final PackageManager pm = context.getPackageManager();
    ApplicationInfo appInfo;
    try {
      appInfo = pm.getApplicationInfo(packageName, 0);
    } catch (NameNotFoundException e) {
      appInfo = null;
    }

    final CharSequence appLabel;
    if (appInfo != null) {
      appLabel = context.getPackageManager().getApplicationLabel(appInfo);
    } else {
      appLabel = null;
    }

    return appLabel;
  }

  private void showAddLabelDialog(
      final String resourceName,
      boolean needToRestoreFocus,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    AddLabelDialog addLabelDialog =
        new AddLabelDialog(context, resourceName, labelManager, pipeline);
    addLabelDialog.setSoftInputMode(true);
    addLabelDialog.setRestoreFocus(needToRestoreFocus);
    addLabelDialog.showDialog();
    addLabelDialog.setButtonEnabled(DialogInterface.BUTTON_POSITIVE, false);
  }

  private void showEditLabelDialog(
      long labelId, boolean needToRestoreFocus, @Nullable Pipeline.FeedbackReturner pipeline) {
    // We have to ensure we have the existing label object before we can
    // edit or remove it. We're not guaranteed that the cache will have
    // this available, so we have to query it directly.
    labelManager.getLabelForLabelIdFromDatabase(
        labelId,
        result -> {
          if (result != null) {
            EditLabelDialog editLabelDialog =
                new EditLabelDialog(context, result, labelManager, pipeline);
            editLabelDialog.setSoftInputMode(true);
            editLabelDialog.setRestoreFocus(needToRestoreFocus);
            editLabelDialog.setNeutralButtonStringRes(R.string.label_dialog_title_remove);
            editLabelDialog.showDialog();
          }
        });
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Dialogs

  /**
   * Base abstract Dialog class to edit label. Used by {@code AddLabelDialog} and {@code
   * EditLabelDialog}.
   */
  private abstract static class BaseEditLabelDialog extends BaseDialog {
    protected CustomLabelManager labelManager;
    @Nullable protected EditText editField;

    private final TextWatcher textValidator =
        new TextWatcher() {
          @Override
          public void afterTextChanged(Editable text) {
            setButtonEnabled(DialogInterface.BUTTON_POSITIVE, !TextUtils.isEmpty(text));
          }

          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };

    public BaseEditLabelDialog(
        Context context,
        int titleResId,
        CustomLabelManager labelManager,
        Pipeline.FeedbackReturner pipeline) {
      super(context, titleResId, pipeline);
      this.labelManager = labelManager;
    }

    /** Handles positive button click events in dialog. */
    protected abstract void onPositiveAction();

    /** Setup customized view */
    protected void setupCustomizedView() {}

    @Override
    public void handleDialogClick(int buttonClicked) {
      if (buttonClicked == Dialog.BUTTON_POSITIVE) {
        onPositiveAction();
      } else if (buttonClicked == Dialog.BUTTON_NEGATIVE) {
        dismissDialog();
      }
    }

    @Override
    public void handleDialogDismiss() {
      labelManager.shutdown();
      editField = null;
    }

    @Override
    public View getCustomizedView() {
      final LayoutInflater li = LayoutInflater.from(context);
      final View dialogView = li.inflate(R.layout.label_addedit_dialog, null);
      editField = dialogView.findViewById(R.id.label_dialog_edit_text);
      editField.setOnEditorActionListener(
          (TextView textView, int actionId, KeyEvent keyEvent) -> {
            if (actionId == IME_ACTION_DONE) {
              InputMethodManager imm =
                  (InputMethodManager) context.getSystemService(INPUT_METHOD_SERVICE);
              imm.hideSoftInputFromWindow(editField.getWindowToken(), /* flags= */ 0);
              return true;
            }
            return false;
          });
      editField.addTextChangedListener(textValidator);
      editField.requestFocus();
      setupCustomizedView();
      return dialogView;
    }
  }

  /** Dialog class to add label. */
  private static class AddLabelDialog extends BaseEditLabelDialog {
    private String resourceName;

    public AddLabelDialog(
        Context context,
        String resourceName,
        CustomLabelManager labelManager,
        Pipeline.FeedbackReturner pipeline) {
      super(context, R.string.label_dialog_title_add, labelManager, pipeline);
      this.resourceName = resourceName;
    }

    @Override
    public void onPositiveAction() {
      if (editField != null) {
        labelManager.addLabel(resourceName, editField.getText().toString());
      }
    }

    @Override
    public String getMessageString() {
      return context.getString(R.string.label_dialog_text, resourceName);
    }
  }

  /** Dialog class to edit label. */
  private static class EditLabelDialog extends BaseEditLabelDialog {
    private Label existing;
    @Nullable private final Pipeline.FeedbackReturner pipeline;
    private final Context context;

    public EditLabelDialog(
        Context context,
        Label existing,
        CustomLabelManager labelManager,
        Pipeline.FeedbackReturner pipeline) {
      super(context, R.string.label_dialog_title_edit, labelManager, pipeline);
      this.context = context;
      this.existing = existing;
      this.pipeline = pipeline;
    }

    @Override
    public void onPositiveAction() {
      if (editField != null) {
        existing.setText(editField.getText().toString());
        existing.setTimestamp(System.currentTimeMillis());
        labelManager.updateLabel(existing);
      }
    }

    @Override
    public void setupCustomizedView() {
      if (editField != null) {
        editField.setText(existing.getText());
      }
    }

    @Override
    public String getMessageString() {
      return context.getString(R.string.label_dialog_text, existing.getViewName());
    }

    @Override
    public void handleDialogClick(int buttonClicked) {
      if (buttonClicked == Dialog.BUTTON_NEUTRAL) {
        labelManager.removeLabel(existing);
        if (pipeline != null) {
          pipeline.returnFeedback(
              EVENT_ID_UNTRACKED,
              Feedback.speech(
                  context.getString(R.string.label_dialog_confirm_label_remove),
                  SpeakOptions.create()
                      .setFlags(
                          FeedbackItem.FLAG_NO_HISTORY
                              | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                              | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                              | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE
                              | FeedbackItem.FLAG_SKIP_DUPLICATE)));
        }
      } else {
        // Parent class handles ok and cancel button.
        super.handleDialogClick(buttonClicked);
      }
    }
  }
}
