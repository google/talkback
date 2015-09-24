/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.talkback.formatter;

import android.app.Notification;
import android.content.Context;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews;

import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Formatter that returns an utterance to announce text replacement.
 */
public class NotificationFormatter implements EventSpeechRule.AccessibilityEventFormatter {
    /** The maximum number of history items to keep track of. */
    private static final int MAX_HISTORY_SIZE = 1;

    private static final long MAX_HISTORY_AGE = (60 * 1000);

    /** The notification history. Used to detect duplicate notifications. */
    private final LinkedList<Notification> mNotificationHistory = new LinkedList<>();

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final Notification notification = extractNotification(event);

        if (notification == null) return false;
        if (isRecent(notification)) return false;

        final CharSequence typeText = getTypeText(context, notification);
        final CharSequence tickerText = notification.tickerText;

        if (!TextUtils.isEmpty(typeText)) utterance.addSpoken(typeText);
        if (!TextUtils.isEmpty(tickerText)) utterance.addSpoken(tickerText);

        return !utterance.getSpoken().isEmpty();
    }

    /**
     * Extracts a {@link Notification} from an {@link AccessibilityEvent}.
     *
     * @param event The event to extract from.
     * @return The extracted Notification, or {@code null} on error.
     */
    private Notification extractNotification(AccessibilityEvent event) {
        final Parcelable parcelable = event.getParcelableData();

        if (!(parcelable instanceof Notification)) {
            return null;
        }

        return (Notification) parcelable;
    }

    /**
     * Manages the notification history and returns whether a notification event
     * is recent. Returns false if the {@link AccessibilityEvent} does not
     * contain a {@link Notification}.
     *
     * @param notification The notification.
     * @return {@code true} if the notification is recent.
     */
    private synchronized boolean isRecent(Notification notification) {
        final Notification foundInHistory = removeFromHistory(notification);

        // If we didn't find the notification in history, set the notification
        // time to now. Otherwise, keep the event time of the previous entry.
        if (foundInHistory != null) {
            notification.when = foundInHistory.when;
        } else {
            notification.when = SystemClock.uptimeMillis();
        }

        addToHistory(notification);

        return (foundInHistory != null);
    }

    /**
     * Searches for and removes a {@link Notification} from history.
     *
     * @param notification The notification to remove from history.
     * @return {@code true} if the notification was found and removed.
     */
    private Notification removeFromHistory(Notification notification) {
        final Iterator<Notification> historyIterator = mNotificationHistory.iterator();

        while (historyIterator.hasNext()) {
            final Notification recentNotification = historyIterator.next();
            final long age = SystemClock.uptimeMillis() - recentNotification.when;

            // Don't compare against old entries, just remove them.
            if (age > MAX_HISTORY_AGE) {
                historyIterator.remove();
                continue;
            }

            if (notificationsAreEqual(notification, recentNotification)) {
                historyIterator.remove();
                return recentNotification;
            }
        }

        return null;
    }

    /**
     * Adds the specified {@link Notification} to history and ensures the size
     * of history does not exceed the specified limit.
     *
     * @param notification The notification to add.
     */
    private void addToHistory(Notification notification) {
        mNotificationHistory.addFirst(notification);

        if (mNotificationHistory.size() > MAX_HISTORY_SIZE) {
            mNotificationHistory.removeLast();
        }
    }

    /**
     * Compares two {@link Notification}s.
     *
     * @param first The first notification to compare.
     * @param second The second notification to compare.
     * @return {@code true} is the notifications are equal.
     */
    private boolean notificationsAreEqual(Notification first, Notification second) {
        if (!TextUtils.equals(first.tickerText, second.tickerText)) {
            return false;
        }

        final RemoteViews firstView = first.contentView;
        final RemoteViews secondView = second.contentView;

        return remoteViewsAreEqual(firstView, secondView);

    }

    /**
     * Compares to {@link RemoteViews} objects.
     *
     * @param firstView The first view to compare.
     * @param secondView The second view to compare.
     */
    private boolean remoteViewsAreEqual(RemoteViews firstView, RemoteViews secondView) {
        if (firstView == secondView) {
            return true;
        }

        if (firstView == null || secondView == null) {
            return false;
        }

        final String firstPackage = firstView.getPackage();
        final String secondPackage = secondView.getPackage();

        if (!TextUtils.equals(firstPackage, secondPackage)) {
            return false;
        }

        final int firstLayoutId = firstView.getLayoutId();
        final int secondLayoutId = secondView.getLayoutId();

        return firstLayoutId == secondLayoutId;

    }

    /**
     * Returns text describing the type of {@link Notification} contained within
     * an {@link AccessibilityEvent}.
     *
     * @param context The parent context.
     * @param notification The notification.
     * @return Text describing the type of notification.
     */
    private CharSequence getTypeText(Context context, Notification notification) {
        switch (notification.icon) {
            case android.R.drawable.stat_notify_missed_call:
                return context.getResources().getString(R.string.notification_type_missed_call);
            case android.R.drawable.stat_notify_call_mute:
                return context.getResources().getString(R.string.notification_type_status_mute);
            case android.R.drawable.stat_notify_chat:
                return context.getResources().getString(R.string.notification_type_status_chat);
            case android.R.drawable.stat_notify_error:
                return context.getResources().getString(R.string.notification_type_status_error);
            case android.R.drawable.stat_notify_more:
                return context.getResources().getString(R.string.notification_type_status_more);
            case android.R.drawable.stat_notify_sdcard:
                return context.getResources().getString(R.string.notification_type_status_sdcard);
            case android.R.drawable.stat_notify_sdcard_usb:
                return context.getResources().getString(
                        R.string.notification_type_status_sdcard_usb);
            case android.R.drawable.stat_notify_sync:
                return context.getResources().getString(R.string.notification_status_sync);
            case android.R.drawable.stat_notify_sync_noanim:
                return context.getResources().getString(
                        R.string.notification_type_status_sync_noanim);
            case android.R.drawable.stat_notify_voicemail:
                return context.getResources().getString(
                        R.string.notification_type_status_voicemail);
            default:
                return null;
        }
    }

}
