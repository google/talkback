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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.text.TextUtils;
import android.view.Window;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.utils.RemoteIntentUtils;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A class to show notification dialog. */
public class NotificationActivity extends FragmentActivity {

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
  public static final String EXTRA_INT_DIALOG_BUTTON = "button";

  /**
   * An optional extra key that references the {@link Notification} ID of notification to dismiss
   * when the user accepts the notification dialog within this activity.
   */
  public static final String EXTRA_INT_NOTIFICATION_ID = "notificationId";

  /** An optional extra key that open the URL from the button for the more details. */
  public static final String EXTRA_STRING_WEB_URL_ID = "url";

  /** The URL which is opened from NotificationActivity and launches the web in phone. */
  public static final String HELP_WEB_URL =
      "https://support.google.com/accessibility/android/answer/6283677";

  private int notificationId = Integer.MIN_VALUE;

  private A11yAlertDialogWrapper a11yAlertDialogWrapper;

  /**
   * Creates an Intent to starts {@link NotificationActivity}.
   *
   * @param context The context to use in the Intent
   * @param titleResId A string resource ID of title to show in the notification dialog.
   * @param messageResId A string resource ID of message to show in the notification dialog.
   * @param notificationId An identifier of this notification. Invalid if id is Integer.MIN_VALIE
   *     and will not dismiss the notification.
   * @param buttonTextResId The text id in the button of this notification.
   * @param url The url to launch web from the button in this notification.
   */
  public static Intent createStartIntent(
      Context context,
      @StringRes int titleResId,
      @StringRes int messageResId,
      int notificationId,
      @StringRes int buttonTextResId,
      @Nullable String url) {
    // Build the intent to run NotificationActivity.
    final Intent notificationIntent = new Intent(context, NotificationActivity.class);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    notificationIntent.putExtra(NotificationActivity.EXTRA_INT_DIALOG_TITLE, titleResId);
    notificationIntent.putExtra(NotificationActivity.EXTRA_INT_DIALOG_MESSAGE, messageResId);
    notificationIntent.putExtra(NotificationActivity.EXTRA_INT_DIALOG_BUTTON, buttonTextResId);
    notificationIntent.putExtra(NotificationActivity.EXTRA_INT_NOTIFICATION_ID, notificationId);
    notificationIntent.putExtra(NotificationActivity.EXTRA_STRING_WEB_URL_ID, url);
    return notificationIntent;
  }

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
    final String url = extras.getString(EXTRA_STRING_WEB_URL_ID, null);

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
        (dialog, which) -> {
          if (notificationId != Integer.MIN_VALUE) {
            dismissNotification();
          }
          Window window = a11yAlertDialogWrapper.getWindow();
          if (!TextUtils.isEmpty(url) && window != null) {
            Uri uri = Uri.parse(url);
            // This is used for the wear only.
            RemoteIntentUtils.startRemoteActivityToOpenUriOnPhone(
                uri,
                NotificationActivity.this.getApplicationContext(),
                window.getDecorView(),
                success -> dialog.dismiss());
          }
        };

    a11yAlertDialogWrapper =
        A11yAlertDialogWrapper.materialDialogBuilder(this, getSupportFragmentManager())
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setPositiveButton(acceptButtonText, acceptButtonListener)
            // Sets OnDismissListener for the wear. This listener solves the issue that the activity
            // will not close by swiping left to right when Alertdialog dismisses. This only affects
            // the wear version.
            .setOnDismissListener((dialog) -> finish())
            // Sets the icon of the button for the wear only.
            .setPositiveButtonIconId(R.drawable.ic_open_in_phone)
            .create();
    a11yAlertDialogWrapper.show();
  }

  private void dismissNotification() {
    final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.cancel(notificationId);
  }
}
