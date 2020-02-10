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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.utils.labeling.Label;

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
   * @param node Accessibility node to add a label for
   * @param isFromLocalContextMenu Sets to {@code true} if caller is from local context menu
   * @param pipeline the FeedbackReturner which needs to perform restore focus
   * @return True if showing the dialog was successful, otherwise false
   */
  public static boolean addLabel(
      Context context,
      AccessibilityNodeInfoCompat node,
      boolean isFromLocalContextMenu,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    if (context == null || node == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context);
    dialogManager.showAddLabelDialog(
        node.getViewIdResourceName(), isFromLocalContextMenu, pipeline);
    return true;
  }

  /**
   * Shows the dialog to edit the given label in the given context.
   *
   * @param context Context to display the dialog in
   * @param label Label to edit.
   * @param isFromLocalContextMenu Sets to {@code true} if caller is from local context menu
   * @param pipeline the FeedbackReturner which needs to perform restore focus
   * @return True if showing the dialog was successful, otherwise false
   */
  public static boolean editLabel(
      Context context,
      Label label,
      boolean isFromLocalContextMenu,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    if (context == null || label == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context);
    dialogManager.showEditLabelDialog(label.getId(), isFromLocalContextMenu, pipeline);
    return true;
  }

  /**
   * Shows the dialog to remove the given label in the given context.
   *
   * @param context Context to display the dialog in
   * @param label Label to remove.
   * @param isFromLocalContextMenu Sets to {@code true} if caller is from local context menu
   * @param pipeline the FeedbackReturner which needs to perform restore focus
   * @return True if showing the dialog was successful, otherwise false
   */
  public static boolean removeLabel(
      Context context,
      Label label,
      boolean isFromLocalContextMenu,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    if (context == null || label == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context);
    dialogManager.showRemoveLabelDialog(label.getId(), isFromLocalContextMenu, pipeline);
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
      boolean isFromLocalContextMenu,
      @Nullable Pipeline.FeedbackReturner pipeline) {
    AddLabelDialog addLabelDialog =
        new AddLabelDialog(context, resourceName, labelManager, pipeline);
    addLabelDialog.setSoftInputMode(true);
    addLabelDialog.setIsFromLocalContextMenu(isFromLocalContextMenu);
    addLabelDialog.showDialog();
    addLabelDialog.setButtonEnabled(DialogInterface.BUTTON_POSITIVE, false);
  }

  private void showEditLabelDialog(
      long labelId, boolean isFromLocalContextMenu, @Nullable Pipeline.FeedbackReturner pipeline) {
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
            editLabelDialog.setIsFromLocalContextMenu(isFromLocalContextMenu);
            editLabelDialog.showDialog();
          }
        });
  }

  private void showRemoveLabelDialog(
      long labelId, boolean isFromLocalContextMenu, @Nullable Pipeline.FeedbackReturner pipeline) {
    // We have to ensure we have the existing label object before we can
    // edit or remove it. We're not guaranteed that the cache will have
    // this available, so we have to query it directly.
    labelManager.getLabelForLabelIdFromDatabase(
        labelId,
        result -> {
          if (result != null) {
            RemoveLabelDialog removeLabelDialog =
                new RemoveLabelDialog(context, result, labelManager, pipeline);
            removeLabelDialog.setIsFromLocalContextMenu(isFromLocalContextMenu);
            removeLabelDialog.showDialog();
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
    private final OnEditorActionListener editActionListener =
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_DONE && !TextUtils.isEmpty(v.getText())) {
            handleDialogClick(DialogInterface.BUTTON_POSITIVE);
            return true;
          }
          return false;
        };

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
      editField.setOnEditorActionListener(editActionListener);
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

    public EditLabelDialog(
        Context context,
        Label existing,
        CustomLabelManager labelManager,
        Pipeline.FeedbackReturner pipeline) {
      super(context, R.string.label_dialog_title_edit, labelManager, pipeline);
      this.existing = existing;
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
  }

  /** Dialog class to remove label. */
  private static class RemoveLabelDialog extends BaseDialog {
    private Label existing;
    private CustomLabelManager labelManager;

    public RemoveLabelDialog(
        Context context,
        Label existing,
        CustomLabelManager labelManager,
        Pipeline.FeedbackReturner pipeline) {
      super(context, R.string.label_dialog_title_remove, pipeline);
      this.existing = existing;
      this.labelManager = labelManager;
    }

    @Override
    public void handleDialogClick(int buttonClicked) {
      if (buttonClicked == Dialog.BUTTON_POSITIVE) {
        labelManager.removeLabel(existing);
      } else if (buttonClicked == Dialog.BUTTON_NEGATIVE) {
        dismissDialog();
      }
    }

    @Override
    public void handleDialogDismiss() {
      labelManager.shutdown();
    }

    @Override
    public String getMessageString() {
      final CharSequence appName = getApplicationName(context, existing.getPackageName());
      final CharSequence message;
      if (TextUtils.isEmpty(appName)) {
        message = context.getString(R.string.label_remove_dialog_text, existing.getText());
      } else {
        message =
            context.getString(
                R.string.label_remove_dialog_text_app_name, existing.getText(), appName);
      }
      return message.toString();
    }

    @Override
    public View getCustomizedView() {
      return null;
    }
  }
}
