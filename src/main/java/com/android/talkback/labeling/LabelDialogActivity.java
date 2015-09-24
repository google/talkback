/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.talkback.labeling;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
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

import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.DirectLabelFetchRequest;
import com.android.utils.labeling.Label;
import com.android.utils.labeling.LabelOperationUtils;

/**
 * A general purpose activity for adding, editing, and removing custom labels.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LabelDialogActivity extends Activity {

    private static final String ACTION_ADD_LABEL = LabelOperationUtils.ACTION_ADD_LABEL;
    private static final String ACTION_EDIT_LABEL = LabelOperationUtils.ACTION_EDIT_LABEL;
    private static final String ACTION_REMOVE_LABEL = LabelOperationUtils.ACTION_REMOVE_LABEL;

    private static final String
            EXTRA_STRING_RESOURCE_NAME = LabelOperationUtils.EXTRA_STRING_RESOURCE_NAME;
    private static final String EXTRA_LONG_LABEL_ID = LabelOperationUtils.EXTRA_LONG_LABEL_ID;

    private Intent mStartIntent;
    private CustomLabelManager mLabelManager;

    private Button mPositiveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLabelManager = new CustomLabelManager(this);
        mStartIntent = getIntent();
        if (!ensureIntentConsistency(mStartIntent)) {
            // TODO(CB): showErrorDialog();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLabelManager.shutdown();
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            service.resetFocusedNode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ACTION_ADD_LABEL.equals(mStartIntent.getAction())) {
            showAddLabelDialog();
        } else {
            // We have to ensure we have the existing label object before we can
            // edit or remove it. We're not guaranteed that the cache will have
            // this available, so we have to query it directly.
            // TODO: This can be avoided if Labels were parceled and
            // sent through the intent
            mLabelManager.getLabelForLabelIdFromDatabase(
                    mStartIntent.getExtras().getLong(EXTRA_LONG_LABEL_ID),
                    new DirectLabelFetchRequest.OnLabelFetchedListener() {
                        @Override
                        public void onLabelFetched(Label result) {
                            if (result == null) {
                                // TODO(CB): showErrorDialog();
                                finish();
                                return;
                            }

                            if (ACTION_EDIT_LABEL.equals(mStartIntent.getAction())) {
                                showEditLabelDialog(result);
                            } else if (ACTION_REMOVE_LABEL.equals(mStartIntent.getAction())) {
                                showRemoveLabelDialog(result);
                            }
                        }
                    });
        }
    }

    private boolean ensureIntentConsistency(Intent intent) {
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        if (TextUtils.isEmpty(action) || (extras == null) || extras.isEmpty()) {
            // This activity requires an intent with an action and extras
            return false;
        }

        if (ACTION_ADD_LABEL.equals(action)) {
            // The ACTION_ADD_LABEL action requires a view resource name
            final String resourceName = extras.getString(EXTRA_STRING_RESOURCE_NAME);
            if (!TextUtils.isEmpty(resourceName)) {
                return true;
            }
        } else if (ACTION_EDIT_LABEL.equals(action) || ACTION_REMOVE_LABEL.equals(action)) {
            // The ACTION_EDIT_LABEL and ACTION_REMOVE_LABEL actions require a label ID
            final long labelId = extras.getLong(EXTRA_LONG_LABEL_ID, Long.MIN_VALUE);
            if (labelId != Long.MIN_VALUE) {
                return true;
            }
        }

        // The intent provided an invalid action
        return false;
    }

    /**
     * Computes the common name for an application.
     *
     * @param packageName The package name of the application
     * @return The common name for the application
     */
    private CharSequence getApplicationName(String packageName) {
        final PackageManager pm = getPackageManager();
        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            appInfo = null;
        }

        final CharSequence appLabel;
        if (appInfo != null) {
            appLabel = getPackageManager().getApplicationLabel(appInfo);
        } else {
            appLabel = null;
        }

        return appLabel;
    }

    private void showAddLabelDialog() {
        setTheme(R.style.DialogStyle);
        final LayoutInflater li = LayoutInflater.from(this);
        final View dialogView = li.inflate(R.layout.label_addedit_dialog, null);
        final EditText editField = (EditText) dialogView.findViewById(R.id.label_dialog_edit_text);
        editField.setOnEditorActionListener(mEditActionListener);
        editField.addTextChangedListener(mTextValidator);

        final OnClickListener buttonClickListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == Dialog.BUTTON_POSITIVE) {
                    final String resourceName = mStartIntent.getExtras()
                            .getString(EXTRA_STRING_RESOURCE_NAME);
                    mLabelManager.addLabel(
                            resourceName, editField.getText().toString());
                } else if (which == Dialog.BUTTON_NEGATIVE) {
                    dialog.dismiss();
                }
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setMessage(R.string.label_dialog_text)
                .setTitle(R.string.label_dialog_title_add)
                .setPositiveButton(android.R.string.ok, buttonClickListener)
                .setNegativeButton(android.R.string.cancel, buttonClickListener)
                .setOnDismissListener(mDismissListener)
                .setCancelable(true);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();

        mPositiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
        mPositiveButton.setEnabled(false);
    }

    private void showEditLabelDialog(final Label existing) {
        setTheme(R.style.DialogStyle);
        final LayoutInflater li = LayoutInflater.from(this);
        final View dialogView = li.inflate(R.layout.label_addedit_dialog, null);
        final EditText editField = (EditText) dialogView.findViewById(R.id.label_dialog_edit_text);
        editField.setText(existing.getText());
        editField.setOnEditorActionListener(mEditActionListener);
        editField.addTextChangedListener(mTextValidator);

        final OnClickListener buttonClickListener = new OnClickListener() {
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

        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setMessage(R.string.label_dialog_text)
                .setTitle(R.string.label_dialog_title_edit)
                .setPositiveButton(android.R.string.ok, buttonClickListener)
                .setNegativeButton(android.R.string.cancel, buttonClickListener)
                .setOnDismissListener(mDismissListener)
                .setCancelable(true);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();

        mPositiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
    }

    private void showRemoveLabelDialog(final Label existing) {
        setTheme(R.style.DialogStyle);
        final CharSequence appName = getApplicationName(existing.getPackageName());

        final CharSequence message;
        if (TextUtils.isEmpty(appName)) {
            message = getString(R.string.label_remove_dialog_text, existing.getText());
        } else {
            message = getString(
                    R.string.label_remove_dialog_text_app_name, existing.getText(), appName);
        }

        final OnClickListener buttonClickListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == Dialog.BUTTON_POSITIVE) {
                    mLabelManager.removeLabel(existing);
                } else if (which == Dialog.BUTTON_NEGATIVE) {
                    dialog.dismiss();
                }
            }
        };

        new AlertDialog.Builder(this)
            .setMessage(message)
            .setTitle(R.string.label_dialog_title_remove)
            .setPositiveButton(android.R.string.yes, buttonClickListener)
            .setNegativeButton(android.R.string.no, buttonClickListener)
            .setOnDismissListener(mDismissListener)
            .setCancelable(true)
            .create()
            .show();
    }

    private final OnDismissListener mDismissListener = new OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            finish();
        }
    };

    private final OnEditorActionListener mEditActionListener = new OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if ((mPositiveButton != null) && (actionId == EditorInfo.IME_ACTION_DONE)
                    && !TextUtils.isEmpty(v.getText())) {
                mPositiveButton.callOnClick();
                return true;
            }

            return false;
        }
    };

    private final TextWatcher mTextValidator = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable text) {
            if (mPositiveButton != null) {
                mPositiveButton.setEnabled(!TextUtils.isEmpty(text));
            }
        }

        @Override
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

        @Override
        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
    };
}
