/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkbacktests.testsession;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.talkbacktests.MainActivity;
import com.android.talkbacktests.R;

public class NotificationTest extends BaseTestContent implements View.OnClickListener {

    private final static int NOTIFICATION_ID_MAIN_MENU = 123543623;
    private final static int NOTIFICATION_ID_LAST_VIEW = 124543642;

    public NotificationTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_notification, container, false);
        view.findViewById(R.id.test_notification_button1).setOnClickListener(this);
        view.findViewById(R.id.test_notification_button2).setOnClickListener(this);
        view.findViewById(R.id.test_notification_button3).setOnClickListener(this);
        view.findViewById(R.id.test_notification_button_ticker).setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.test_notification_button1):
                showSimpleNotification();
                break;
            case (R.id.test_notification_button2):
                showCustomNotification();
                break;
            case (R.id.test_notification_button3):
                cancelNotifications();
                break;
            case (R.id.test_notification_button_ticker):
                showTickerNotification();
                break;
            default:
                // Do nothing.
        }
    }

    private void showSimpleNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.normal_notification_title))
                .setContentText(getString(R.string.normal_notification_text))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getContext());
        stackBuilder.addParentStack(MainActivity.class);

        Intent resultIntent = new Intent(getContext(), MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent = stackBuilder
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_MAIN_MENU, builder.build());
    }

    private void showTickerNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.ticker_notification_title))
                .setContentText(getString(R.string.ticker_notification_text))
                .setTicker(getString(R.string.ticker_notification_ticker))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_MAIN_MENU, builder.build());
    }

    private void showCustomNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        RemoteViews contentView = new RemoteViews(getContext().getPackageName(),
                R.layout.custom_notification);
        contentView.setImageViewResource(R.id.notification_image,
                android.R.drawable.ic_dialog_email);
        contentView.setTextViewText(R.id.notification_title,
                getString(R.string.custom_notification_title));
        contentView.setTextViewText(R.id.notification_text,
                getString(R.string.custom_notification_text));
        builder.setContent(contentView);

        Intent resultIntent = new Intent(getContext(), MainActivity.class);
        resultIntent.setAction(Intent.ACTION_MAIN);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent
                .getActivity(getContext(), 0, resultIntent, 0);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_LAST_VIEW, builder.build());
    }

    private void cancelNotifications() {
        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }
}