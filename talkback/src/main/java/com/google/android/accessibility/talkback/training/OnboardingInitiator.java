/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.training;

import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_ON_BOARDING_FOR_MULTIFINGER_GESTURES;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_ON_BOARDING_TALKBACK;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_ON_BOARDING_TALKBACK_WITHOUT_DESCRIBE_IMAGE;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivity;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Starts a {@link TrainingActivity} to show onboarding. */
public class OnboardingInitiator {

  static final int NEW_GESTURE_NOTIFICATION_ID = 1;

  @StringRes @VisibleForTesting
  public static final int NEW_FEATURE_SHOWN_KEY = R.string.pref_update_welcome_14_1_shown_key;

  /** A list of legacy preferences for old onboardings. */
  private static final int[] legacyKey = {
    R.string.pref_update_talkback91_shown_key,
    R.string.pref_update_welcome_12_2_shown_key,
    R.string.pref_update_welcome_13_0_shown_key,
    R.string.pref_update_welcome_13_1_shown_key,
    R.string.pref_update_welcome_14_0_shown_key
  };

  /** Sets onboarding preferences to true to ignore onboarding. */
  public static void markAllOnboardingAsShown(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    if (!hasOnboardingForNewFeaturesBeenShown(prefs, context)) {
      markOnboardingForNewFeaturesAsShown(prefs, context);
    }
    if (FeatureSupport.isMultiFingerGestureSupported()
        && !hasOnboardingForMultiFingerGestureSupportBeenShown(prefs, context)) {
      markOnboardingForMultiFingerGesturesAsShown(prefs, context);
    }
  }

  /** Checks if onboarding has been shown. */
  public static boolean hasOnboardingForNewFeaturesBeenShown(
      SharedPreferences prefs, Context context) {
    return SharedPreferencesUtils.getBooleanPref(
        prefs, context.getResources(), NEW_FEATURE_SHOWN_KEY, false);
  }

  /**
   * Shows onboarding if users update TalkBack, or shows a updated notification if users update to
   * Android R after having new TalkBack.
   */
  public static void showOnboardingIfNecessary(Context context) {
    FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();
    if (formFactorUtils.isAndroidTv() || formFactorUtils.isAndroidWear()) {
      return;
    }

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);

    if (hasOnboardingForNewFeaturesBeenShown(prefs, context)) {
      if (!hasOnboardingForMultiFingerGestureSupportBeenShown(prefs, context)
          && FeatureSupport.isMultiFingerGestureSupported()) {
        // Shows a notification to notify that new gestures are supported in TalkBack.
        // Builds an intent to run TrainingActivity when the notification is clicked.
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
            .notify(
                NEW_GESTURE_NOTIFICATION_ID,
                NotificationUtils.createNotification(
                    context,
                    context.getString(R.string.new_gesture_notification_title),
                    context.getString(R.string.new_gesture_notification_title),
                    context.getString(R.string.new_gesture_notification_content),
                    PendingIntent.getActivity(
                        context,
                        0,
                        TrainingActivity.createTrainingIntent(
                            context,
                            TRAINING_ID_ON_BOARDING_FOR_MULTIFINGER_GESTURES,
                            /* showExitBanner= */ true),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE),
                    /* autoCancel= */ true));
        markOnboardingForMultiFingerGesturesAsShown(prefs, context);
      }
    } else {
      if (FeatureSupport.isMultiFingerGestureSupported()) {
        // Device is Android R, so it's unnecessary to show an onboarding for R.
        markOnboardingForMultiFingerGesturesAsShown(prefs, context);
      }
      context.startActivity(createOnboardingIntent(context, /* showExitBanner= */ true));
      markOnboardingForNewFeaturesAsShown(prefs, context);
    }
  }

  private static boolean hasOnboardingForMultiFingerGestureSupportBeenShown(
      SharedPreferences prefs, Context context) {
    return prefs.getBoolean(
        context.getString(R.string.pref_update_multi_finger_gestures_shown_key),
        /* defValue= */ false);
  }

  private static void markOnboardingForMultiFingerGesturesAsShown(
      SharedPreferences prefs, Context context) {
    prefs
        .edit()
        .putBoolean(
            context.getString(R.string.pref_update_multi_finger_gestures_shown_key),
            /* value= */ true)
        .apply();
  }

  /** Returns an intent to start onboarding. */
  public static Intent createOnboardingIntent(Context context) {
    return createOnboardingIntent(context, false);
  }

  /** Returns an intent to start onboarding. */
  public static Intent createOnboardingIntent(Context context, boolean showExitBanner) {
    return TrainingActivity.createTrainingIntent(
        context,
        (ImageCaptioner.supportsImageDescription(context)
                || ImageCaptioner.supportsIconDetection(context))
            ? TRAINING_ID_ON_BOARDING_TALKBACK
            : TRAINING_ID_ON_BOARDING_TALKBACK_WITHOUT_DESCRIBE_IMAGE,
        showExitBanner);
  }

  /** Sets the preference of showing new feature pages and removes legacy of preferences. */
  private static void markOnboardingForNewFeaturesAsShown(
      SharedPreferences prefs, Context context) {
    prefs.edit().putBoolean(context.getString(NEW_FEATURE_SHOWN_KEY), true).apply();
    removeLegacyPref(prefs, context);
  }

  private static void removeLegacyPref(SharedPreferences prefs, Context context) {
    Editor editor = prefs.edit();
    for (int key : legacyKey) {
      editor.remove(context.getString(key));
    }
    editor.apply();
  }

  private OnboardingInitiator() {}
}
