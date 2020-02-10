/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.TextUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

public class NotificationActivity extends Activity {

  private static final String TAG = "NotificationActivity";

  /**
   * An optional extra key that references the string resource ID of the title to show in the
   * notification dialog within this activity. Defaults to "TalkBack" if not provided.
   */
  public static final String EXTRA_INT_DIALOG_TITLE = "title";

  /**
   * A required extra key that references the string resource ID of the message to show in the
   * notification dialog within this activity.
   */
  public static final String EXTRA_INT_DIALOG_MESSAGE = "message";

  /**
   * An optional extra key that references the string resource ID of the button text to show in the
   * notification dialog within this activity. Defaults to "Ok" if not provided.
   */
  private static final String EXTRA_INT_DIALOG_BUTTON = "button";

  /**
   * An optional extra key that references the {@link Notification} ID of notification to dismiss
   * when the user accepts the notification dialog within this activity.
   */
  public static final String EXTRA_INT_NOTIFICATION_ID = "notificationId";

  private int notificationId = Integer.MIN_VALUE;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final Bundle extras = getIntent().getExtras();
    if (extras == null) {
      LogUtils.w(TAG, "NotificationActivity received an empty extras bundle.");
      finish();
      return;
    }

    notificationId = extras.getInt(EXTRA_INT_NOTIFICATION_ID, Integer.MIN_VALUE);

    final int titleRes = extras.getInt(EXTRA_INT_DIALOG_TITLE, -1);
    final int messageRes = extras.getInt(EXTRA_INT_DIALOG_MESSAGE, -1);
    final int buttonRes = extras.getInt(EXTRA_INT_DIALOG_BUTTON, -1);

    final CharSequence dialogTitle =
        (titleRes != -1) ? getString(titleRes) : getString(R.string.talkback_title);
    final CharSequence dialogMessage = (messageRes != -1) ? getString(messageRes) : null;
    final CharSequence acceptButtonText =
        (buttonRes != -1) ? getString(buttonRes) : getString(android.R.string.ok);

    if (TextUtils.isEmpty(dialogMessage)) {
      // No point in showing an empty message.
      finish();
      return;
    }

    final OnClickListener acceptButtonListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (notificationId != Integer.MIN_VALUE) {
              dismissNotification();
            }
            dialog.dismiss();
            finish();
          }
        };

    new AlertDialog.Builder(this)
        .setTitle(dialogTitle)
        .setMessage(dialogMessage)
        .setCancelable(false)
        .setPositiveButton(acceptButtonText, acceptButtonListener)
        .create()
        .show();
  }

  private void dismissNotification() {
    final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.cancel(notificationId);
  }
}
