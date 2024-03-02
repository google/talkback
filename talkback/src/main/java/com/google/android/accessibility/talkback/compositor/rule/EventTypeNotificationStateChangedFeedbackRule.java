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
package com.google.android.accessibility.talkback.compositor.rule;

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_NOTIFICATION_STATE_CHANGED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.app.Notification;
import android.content.Context;
import android.text.TextUtils;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Event feedback rules for {@link EVENT_TYPE_NOTIFICATION_STATE_CHANGED} event. These rules will
 * provide the event feedback output function by inputting the {@link HandleEventOptions} and
 * outputting {@link EventFeedback}.
 */
public final class EventTypeNotificationStateChangedFeedbackRule {

  private static final String TAG = "EventTypeNotificationStateChangedFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param globalVariables the global compositor variables
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      GlobalVariables globalVariables) {
    eventFeedbackRules.put(
        EVENT_TYPE_NOTIFICATION_STATE_CHANGED,
        eventOptions -> {
          int role = Role.getSourceRole(eventOptions.eventObject);
          boolean isToast = role == Role.ROLE_TOAST;

          CharSequence ttsOutput;
          if (isToast) {
            ttsOutput =
                AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
                    eventOptions.eventObject, globalVariables.getUserPreferredLocale());
            LogUtils.v(
                TAG, " ttsOutputRule= eventContentDescriptionOrEventAggregateText, role=toast");
          } else {
            CharSequence notificationCategory =
                getNotificationCategoryStateText(
                    context, AccessibilityEventUtils.extractNotification(eventOptions.eventObject));
            CharSequence notificationDetails =
                getNotificationDetailsStateText(
                    AccessibilityEventUtils.extractNotification(eventOptions.eventObject));
            ttsOutput =
                CompositorUtils.joinCharSequences(notificationCategory, notificationDetails);
            LogUtils.v(
                TAG,
                StringBuilderUtils.joinFields(
                    " ttsOutputRule= ",
                    StringBuilderUtils.optionalText("notificationCategory", notificationCategory),
                    StringBuilderUtils.optionalText("notificationDetails", notificationDetails),
                    StringBuilderUtils.optionalText(", role", Role.roleToString(role))));
          }

          int queueMode = isToast ? QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH : QUEUE_MODE_QUEUE;

          return EventFeedback.builder()
              .setTtsOutput(Optional.of(ttsOutput))
              .setQueueMode(queueMode)
              .setTtsAddToHistory(true)
              .setForceFeedbackEvenIfAudioPlaybackActive(isToast)
              .setForceFeedbackEvenIfMicrophoneActive(isToast)
              .setForceFeedbackEvenIfSsbActive(false)
              .setForceFeedbackEvenIfPhoneCallActive(isToast)
              .build();
        });
  }

  /** Returns the notification category state text. */
  public static CharSequence getNotificationCategoryStateText(
      Context context, @Nullable Notification notification) {
    if (notification == null || notification.category == null) {
      return "";
    }
    switch (notification.category) {
      case Notification.CATEGORY_CALL:
        return context.getString(R.string.notification_category_call);
      case Notification.CATEGORY_MESSAGE:
        return context.getString(R.string.notification_category_msg);
      case Notification.CATEGORY_EMAIL:
        return context.getString(R.string.notification_category_email);
      case Notification.CATEGORY_EVENT:
        return context.getString(R.string.notification_category_event);
      case Notification.CATEGORY_PROMO:
        return context.getString(R.string.notification_category_promo);
      case Notification.CATEGORY_ALARM:
        return context.getString(R.string.notification_category_alarm);
      case Notification.CATEGORY_PROGRESS:
        return context.getString(R.string.notification_category_progress);
      case Notification.CATEGORY_SOCIAL:
        return context.getString(R.string.notification_category_social);
      case Notification.CATEGORY_ERROR:
        return context.getString(R.string.notification_category_err);
      case Notification.CATEGORY_TRANSPORT:
        return context.getString(R.string.notification_category_transport);
      case Notification.CATEGORY_SYSTEM:
        return context.getString(R.string.notification_category_sys);
      case Notification.CATEGORY_SERVICE:
        return context.getString(R.string.notification_category_service);
      default:
        return "";
    }
  }

  /** Returns the notification details state text. */
  public static CharSequence getNotificationDetailsStateText(@Nullable Notification notification) {
    if (notification == null) {
      return "";
    }

    List<CharSequence> notificationDetails = new ArrayList<>();
    CharSequence notificationTickerText = notification.tickerText;

    if (notification.extras != null) {
      // Get notification title and text from the Notification Extras bundle.
      CharSequence notificationTitle = notification.extras.getCharSequence("android.title");
      CharSequence notificationText = notification.extras.getCharSequence("android.text");

      if (!TextUtils.isEmpty(notificationTitle)) {
        notificationDetails.add(notificationTitle);
      }

      if (!TextUtils.isEmpty(notificationText)) {
        notificationDetails.add(notificationText);
      } else {
        notificationDetails.add(notificationTickerText);
      }
    }

    CharSequence text =
        notificationDetails.isEmpty()
            ? null
            : StringBuilderUtils.getAggregateText(notificationDetails);
    return text == null ? "" : text;
  }
}
