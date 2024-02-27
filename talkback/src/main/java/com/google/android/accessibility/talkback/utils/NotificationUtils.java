/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.accessibility.talkback.utils;

import static android.content.Context.RECEIVER_EXPORTED;
import static com.google.android.accessibility.talkback.permission.PermissionRequestActivity.ACTION_DONE;

import android.Manifest.permission;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.permission.PermissionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility class for notification */
public class NotificationUtils {

  private static final String TALKBACK_CHANNEL_ID = "TalkBackChannel";

  /**
   * Sets up {@link NotificationChannel} for TalkBack notifications.
   *
   * @param context The context.
   */
  private static void setupNotificationChannel(Context context) {
    if (FeatureSupport.supportNotificationChannel()) {
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel notificationChannel =
          new NotificationChannel(
              TALKBACK_CHANNEL_ID,
              context.getString(R.string.talkback_notification_channel_name),
              NotificationManager.IMPORTANCE_HIGH);
      notificationChannel.setDescription(
          context.getString(R.string.talkback_notification_channel_description));
      notificationChannel.setSound(null, null);
      notificationManager.createNotificationChannel(notificationChannel);
    }
  }

  /**
   * Builds and returns {@link Notification} for TalkBack with {@link NotificationChannel}.
   *
   * @param context The context.
   * @param ticker Text that summarizes this notification for accessibility services.
   * @param title Text that shows on notification title.
   * @param content Text that shows on notification content description.
   * @param pendingIntent Intent performed with target action when clicking the notification.
   * @return The notification with channel.
   */
  public static Notification createNotification(
      Context context, String ticker, String title, String content, PendingIntent pendingIntent) {
    return createNotification(
        context, ticker, title, content, pendingIntent, /* autoCancel= */ false);
  }

  /**
   * Builds and returns {@link Notification} for TalkBack with {@link NotificationChannel}.
   *
   * @param context The context.
   * @param ticker Text that summarizes this notification for accessibility services.
   * @param title Text that shows on notification title.
   * @param content Text that shows on notification content description.
   * @param pendingIntent Intent performed with target action when clicking the notification.
   * @param autoCancel The notification is automatically canceled when the user clicks it.
   * @return The notification with channel.
   */
  public static Notification createNotification(
      Context context,
      String ticker,
      String title,
      String content,
      PendingIntent pendingIntent,
      boolean autoCancel) {
    return createDefaultNotificationBuilder(context)
        .setTicker(ticker)
        .setContentTitle(title)
        .setContentText(content)
        .setContentIntent(pendingIntent)
        .setAutoCancel(autoCancel)
        .setOngoing(true)
        .setWhen(0)
        .build();
  }

  /** Retrieves default {@link NotificationCompat.Builder} for Android Accessibility Suite. */
  public static NotificationCompat.Builder createDefaultNotificationBuilder(Context context) {
    setupNotificationChannel(context);
    return new NotificationCompat.Builder(context, TALKBACK_CHANNEL_ID)
        .setSmallIcon(R.drawable.quantum_gm_ic_accessibility_new_vd_theme_24);
  }

  /** Requests post notification permission for TalkBack. */
  public static void requestPostNotificationPermissionIfNeeded(Context context) {
    requestPostNotificationPermissionIfNeeded(context, null);
  }

  /** Requests post notification permission for TalkBack. */
  public static void requestPostNotificationPermissionIfNeeded(
      Context context, @Nullable BroadcastReceiver broadcastReceiver) {
    if (hasPostNotificationPermission(context)) {
      return;
    }

    if (broadcastReceiver != null) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_DONE);
      ContextCompat.registerReceiver(context, broadcastReceiver, filter, RECEIVER_EXPORTED);
    }
    PermissionUtils.requestPermissions(context, permission.POST_NOTIFICATIONS);
  }

  public static boolean hasPostNotificationPermission(Context context) {
    if (!FeatureSupport.postNotificationsPermission()) {
      return true;
    }

    return ContextCompat.checkSelfPermission(context, permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED;
  }
}
