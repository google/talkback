/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.dynamicfeature;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;

/** A dialog to uninstall a module. */
public abstract class UninstallDialog extends BaseDialog {

  public UninstallDialog(Context context, @StringRes int title) {
    super(context, title, /* pipeline= */ null);
    setPositiveButtonStringRes(R.string.delete_dialog_positive_button_text);
    setNegativeButtonStringRes(R.string.delete_dialog_negative_button_text);
  }

  @Override
  public String getMessageString() {
    return null;
  }

  @SuppressLint("InflateParams")
  @Override
  public View getCustomizedView() {
    LayoutInflater inflater = LayoutInflater.from(context);
    final ScrollView root =
        (ScrollView) inflater.inflate(R.layout.confirm_download_dialog, /* root= */ null);

    TextView subtitle = root.findViewById(R.id.confirm_download_dialog_subtitle);
    subtitle.setVisibility(View.GONE);
    TextView message = root.findViewById(R.id.confirm_download_dialog_message);
    message.setText(R.string.delete_dialog_message);

    return root;
  }

  @Override
  public void handleDialogDismiss() {
    // Do nothing for dismissing dialog.
  }
}
