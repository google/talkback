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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;

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
    setupNotificationChannel(context);
    final Notification notification =
        new NotificationCompat.Builder(context, TALKBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_info)
            .setTicker(ticker)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setWhen(0)
            .build();
    return notification;
  }
}
