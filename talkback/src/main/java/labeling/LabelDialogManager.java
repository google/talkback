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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.labeling.Label;

/** Manages the accessibility overlay dialogs for adding, editing, and removing custom labels. */
public class LabelDialogManager {
  private static final long RESET_FOCUSED_NODE_DELAY = 250;

  private final Context mContext;
  private final CustomLabelManager mLabelManager;
  private final boolean mAccessibilityOverlay;
  private Button mPositiveButton;

  private LabelDialogManager(Context context, boolean overlay) {
    mContext = context;
    mLabelManager = new CustomLabelManager(context);
    mAccessibilityOverlay = overlay;
  }

  /**
   * Shows the dialog to add a label for the given node in the given context.
   *
   * @param overlay True if an accessibility overlay/system dialog needs to be used, in which case
   *     the context must be an accessibility service. False if the context is a normal activity and
   *     not a service.
   * @return True if showing the dialog was successful, otherwise false.
   */
  public static boolean addLabel(
      Context context, AccessibilityNodeInfoCompat node, boolean overlay) {
    if (context == null || node == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context, overlay);
    dialogManager.showAddLabelDialog(node.getViewIdResourceName());
    return true;
  }

  /**
   * Shows the dialog to edit the given label in the given context.
   *
   * @param overlay True if an accessibility overlay/system dialog needs to be used, in which case
   *     the context must be an accessibility service. False if the context is a normal activity and
   *     not a service.
   * @return True if showing the dialog was successful, otherwise false.
   */
  public static boolean editLabel(Context context, Label label, boolean overlay) {
    if (context == null || label == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context, overlay);
    dialogManager.showEditLabelDialog(label.getId());
    return true;
  }

  /**
   * Shows the dialog to remove the given label in the given context.
   *
   * @param overlay True if an accessibility overlay/system dialog needs to be used, in which case
   *     the context must be an accessibility service. False if the context is a normal activity and
   *     not a service.
   * @return True if showing the dialog was successful, otherwise false.
   */
  public static boolean removeLabel(Context context, Label label, boolean overlay) {
    if (context == null || label == null) {
      return false;
    }

    LabelDialogManager dialogManager = new LabelDialogManager(context, overlay);
    dialogManager.showRemoveLabelDialog(label.getId());
    return true;
  }

  /**
   * Computes the common name for an application.
   *
   * @param packageName The package name of the application
   * @return The common name for the application
   */
  private CharSequence getApplicationName(String packageName) {
    final PackageManager pm = mContext.getPackageManager();
    ApplicationInfo appInfo;
    try {
      appInfo = pm.getApplicationInfo(packageName, 0);
    } catch (NameNotFoundException e) {
      appInfo = null;
    }

    final CharSequence appLabel;
    if (appInfo != null) {
      appLabel = mContext.getPackageManager().getApplicationLabel(appInfo);
    } else {
      appLabel = null;
    }

    return appLabel;
  }

  private void showAddLabelDialog(final String resourceName) {
    final LayoutInflater li = LayoutInflater.from(mContext);
    final View dialogView = li.inflate(R.layout.label_addedit_dialog, null);
    final EditText editField = (EditText) dialogView.findViewById(R.id.label_dialog_edit_text);
    editField.setOnEditorActionListener(mEditActionListener);
    editField.addTextChangedListener(mTextValidator);

    final OnClickListener buttonClickListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == Dialog.BUTTON_POSITIVE) {
              mLabelManager.addLabel(resourceName, editField.getText().toString());
            } else if (which == Dialog.BUTTON_NEGATIVE) {
              dialog.dismiss();
            }
          }
        };

    final AlertDialog.Builder builder =
        new AlertDialog.Builder(mContext)
            .setView(dialogView)
            .setMessage(mContext.getString(R.string.label_dialog_text, resourceName))
            .setTitle(R.string.label_dialog_title_add)
            .setPositiveButton(android.R.string.ok, buttonClickListener)
            .setNegativeButton(android.R.string.cancel, buttonClickListener)
            .setOnDismissListener(mDismissListener)
            .setCancelable(true);

    final AlertDialog dialog = builder.create();
    showDialog(dialog);

    mPositiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
    mPositiveButton.setEnabled(false);
  }

  private void showEditLabelDialog(long labelId) {
    // We have to ensure we have the existing label object before we can
    // edit or remove it. We're not guaranteed that the cache will have
    // this available, so we have to query it directly.
    mLabelManager.getLabelForLabelIdFromDatabase(
        labelId,
        new DirectLabelFetchRequest.OnLabelFetchedListener() {
          @Override
          public void onLabelFetched(Label result) {
            if (result != null) {
              showEditLabelDialog(result);
            }
          }
        });
  }

  private void showRemoveLabelDialog(long labelId) {
    // We have to ensure we have the existing label object before we can
    // edit or remove it. We're not guaranteed that the cache will have
    // this available, so we have to query it directly.
    mLabelManager.getLabelForLabelIdFromDatabase(
        labelId,
        new DirectLabelFetchRequest.OnLabelFetchedListener() {
          @Override
          public void onLabelFetched(Label result) {
            if (result != null) {
              showRemoveLabelDialog(result);
            }
          }
        });
  }

  private void showEditLabelDialog(final Label existing) {
    final LayoutInflater li = LayoutInflater.from(mContext);
    final View dialogView = li.inflate(R.layout.label_addedit_dialog, null);
    final EditText editField = (EditText) dialogView.findViewById(R.id.label_dialog_edit_text);
    editField.setText(existing.getText());
    editField.setOnEditorActionListener(mEditActionListener);
    editField.addTextChangedListener(mTextValidator);

    final OnClickListener buttonClickListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == Dialog.BUTTON_POSITIVE) {
              existing.setText(editField.getText().toString());
              existing.setTimestamp(System.currentTimeMillis());
              mLabelManager.updateLabel(existing);
            } else if (which == Dialog.BUTTON_NEGATIVE) {
              dialog.dismiss();
            }
          }
        };

    final AlertDialog.Builder builder =
        new AlertDialog.Builder(mContext)
            .setView(dialogView)
            .setMessage(R.string.label_dialog_text)
            .setTitle(R.string.label_dialog_title_edit)
            .setPositiveButton(android.R.string.ok, buttonClickListener)
            .setNegativeButton(android.R.string.cancel, buttonClickListener)
            .setOnDismissListener(mDismissListener)
            .setCancelable(true);

    final AlertDialog dialog = builder.create();
    showDialog(dialog);

    mPositiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
  }

  private void showRemoveLabelDialog(final Label existing) {
    final CharSequence appName = getApplicationName(existing.getPackageName());

    final CharSequence message;
    if (TextUtils.isEmpty(appName)) {
      message = mContext.getString(R.string.label_remove_dialog_text, existing.getText());
    } else {
      message =
          mContext.getString(
              R.string.label_remove_dialog_text_app_name, existing.getText(), appName);
    }

    final OnClickListener buttonClickListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == Dialog.BUTTON_POSITIVE) {
              mLabelManager.removeLabel(existing);
            } else if (which == Dialog.BUTTON_NEGATIVE) {
              dialog.dismiss();
            }
          }
        };

    final AlertDialog.Builder builder =
        new AlertDialog.Builder(mContext)
            .setMessage(message)
            .setTitle(R.string.label_dialog_title_remove)
            .setPositiveButton(android.R.string.ok, buttonClickListener)
            .setNegativeButton(android.R.string.cancel, buttonClickListener)
            .setOnDismissListener(mDismissListener)
            .setCancelable(true);

    final AlertDialog dialog = builder.create();
    showDialog(dialog);
  }

  private void showDialog(AlertDialog dialog) {
    if (mAccessibilityOverlay) {
      if (BuildVersionUtils.isAtLeastLMR1()) {
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
      } else {
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
      }

      // Only need to register overlay dialogs (since they'll cover the lock screen).
      TalkBackService service = TalkBackService.getInstance();
      if (service != null) {
        service.getRingerModeAndScreenMonitor().registerDialog(dialog);
      }
    }
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    dialog.show();
  }

  private final OnDismissListener mDismissListener =
      new OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
          mLabelManager.shutdown();
          TalkBackService service = TalkBackService.getInstance();
          if (service != null) {
            EventId eventId = EVENT_ID_UNTRACKED; // Performance not tracked for menu events.
            service.resetFocusedNode(RESET_FOCUSED_NODE_DELAY, eventId);
            service.getRingerModeAndScreenMonitor().unregisterDialog(dialog);
          }
        }
      };

  private final OnEditorActionListener mEditActionListener =
      new OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
          if ((mPositiveButton != null)
              && (actionId == EditorInfo.IME_ACTION_DONE)
              && !TextUtils.isEmpty(v.getText())) {
            mPositiveButton.callOnClick();
            return true;
          }

          return false;
        }
      };

  private final TextWatcher mTextValidator =
      new TextWatcher() {
        @Override
        public void afterTextChanged(Editable text) {
          if (mPositiveButton != null) {
            mPositiveButton.setEnabled(!TextUtils.isEmpty(text));
          }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
      };
}
