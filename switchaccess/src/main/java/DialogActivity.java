/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.Settings;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.utils.SpannableUrl;
import com.google.android.accessibility.utils.UrlDialogAdapter;
import com.google.android.accessibility.utils.UrlUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages launching dialogs from outside the setup wizard or settings page. It is difficult to
 * launch a dialog from just a service, so this class enables Switch Access to launch dialogs. This
 * class is currently only used to display a list of URLs when a view with multiple URLs is clicked
 * and to display a permissions dialog for adjusting volume under Do Not Disturb mode.
 */
public class DialogActivity extends Activity {

  private static final String TAG = "DialogActivity";

  public static final String EXTRA_URL_LIST = "extra_url_list";

  public static final String ACTION_REQUEST_DO_NOT_DISTURB_PERMSISSION = "request_dnd_permission";

  public static final String DO_NOT_DISTURB_DISMISSED = "dnd_dismiss";

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final Intent intent = getIntent();
    if (intent == null) {
      LogUtils.w(TAG, "DialogActivity started without an intent.");
      finish();
      return;
    }

    String action = intent.getAction();
    if ((action != null) && action.equals(ACTION_REQUEST_DO_NOT_DISTURB_PERMSISSION)) {
      displayDoNotDisturbPermissionDialog();
    } else {
      displayUrlDialog(intent);
    }
  }

  private void displayDoNotDisturbPermissionDialog() {
    AlertDialog.Builder requestDoNotDisturbPermissionDialog = new AlertDialog.Builder(this);
    requestDoNotDisturbPermissionDialog
        .setTitle(R.string.dialog_do_not_disturb_permission_title)
        .setMessage(R.string.dialog_do_not_disturb_permission_message)
        .setOnDismissListener(
            new OnDismissListener() {
              @Override
              public void onDismiss(DialogInterface dialog) {
                // Due to how the AlertDialog is displayed with the Switch Access menu, the Switch
                // Access menu is hidden before the dialog is displayed. When the dialog is
                // dismissed, send a broadcast that will be captured by the OverlayController to
                // restore the previous menu state.
                Context service = SwitchAccessService.getInstance();
                if ((service != null) && !isFinishing()) {
                  service.sendBroadcast(new Intent(DO_NOT_DISTURB_DISMISSED));
                }

                finish();
              }
            })
        .setNegativeButton(
            android.R.string.cancel,
            (dialog, which) -> {
              dialog.dismiss();
            })
        .setPositiveButton(
            R.string.dialog_go_to_do_not_disturb_settings_button,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && (notificationManager != null)
                    && !notificationManager.isNotificationPolicyAccessGranted()) {
                  Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                  startActivity(intent);
                  finish();
                }
              }
            });
    AlertDialog dialog = requestDoNotDisturbPermissionDialog.create();
    dialog.show();
  }

  private void displayUrlDialog(@NonNull Intent intent) {
    // Get the list of URLs and display the dialog.
    ArrayList<SpannableUrl> spannableUrls = intent.getParcelableArrayListExtra(EXTRA_URL_LIST);
    // Intent#getParcelableArrayListExtra can return null, but we ensure that it is not null in
    // SwitchAccessAction#attemptToClickUrl.
    @SuppressWarnings("nullness:argument.type.incompatible")
    UrlDialogAdapter adapter = new UrlDialogAdapter(this, spannableUrls);
    @SuppressWarnings("nullness:dereference.of.nullable")
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_url_list)
            .setAdapter(
                adapter,
                (urlDialog, selectedIndex) -> {
                  if (selectedIndex >= 0) {
                    UrlUtils.openUrlWithIntent(
                        this.getApplicationContext(), spannableUrls.get(selectedIndex).path());
                  }
                  finish();
                })
            .setNegativeButton(android.R.string.cancel, (dialogInterface, id) -> finish())
            .create();
    dialog.show();
  }
}
