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

package com.google.android.accessibility.talkback.labeling;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import com.google.android.accessibility.talkback.R;

public class LabelImportActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstance) {
    super.onCreate(savedInstance);

    Intent intent = getIntent();
    if (intent == null) {
      notifyFailure();
      finish();
      return;
    }

    Uri uri = intent.getData();
    if (uri == null) {
      notifyFailure();
      finish();
      return;
    }

    showChooseModeDialog(uri);
  }

  private void notifyFailure() {
    Toast.makeText(getApplicationContext(), R.string.label_import_failed, Toast.LENGTH_SHORT)
        .show();
  }

  private void showChooseModeDialog(final Uri uri) {
    final DialogInterface.OnClickListener buttonClickListener =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            boolean overrideExistentLabels = true;
            if (which == Dialog.BUTTON_POSITIVE) {
              overrideExistentLabels = false;
              dialog.dismiss();
            } else if (which == Dialog.BUTTON_NEGATIVE) {
              overrideExistentLabels = true;
              dialog.dismiss();
            }

            CustomLabelMigrationManager exporter =
                new CustomLabelMigrationManager(getApplicationContext());
            exporter.importLabels(
                uri,
                overrideExistentLabels,
                new CustomLabelMigrationManager.SimpleLabelMigrationCallback() {
                  @Override
                  public void onLabelImported(int updateCount) {
                    Toast.makeText(
                            getApplicationContext(),
                            getResources()
                                .getQuantityString(
                                    R.plurals.label_import_succeeded, updateCount, updateCount),
                            Toast.LENGTH_SHORT)
                        .show();
                  }

                  @Override
                  public void onFail() {
                    notifyFailure();
                  }
                });
          }
        };

    new AlertDialog.Builder(this)
        .setMessage(R.string.label_import_dialog_message)
        .setTitle(R.string.label_import_dialog_title)
        .setPositiveButton(R.string.label_import_dialog_skip, buttonClickListener)
        .setNegativeButton(R.string.label_import_dialog_override, buttonClickListener)
        .setCancelable(true)
        .setOnDismissListener(
            new DialogInterface.OnDismissListener() {
              @Override
              public void onDismiss(DialogInterface dialog) {
                LabelImportActivity.this.finish();
              }
            })
        .create()
        .show();
  }
}
