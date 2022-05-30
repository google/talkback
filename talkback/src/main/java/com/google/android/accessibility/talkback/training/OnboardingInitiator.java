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

import static com.google.android.accessibility.talkback.training.NavigationButtonBar.BUTTON_TYPE_EXIT;
import static com.google.android.accessibility.talkback.training.NavigationButtonBar.DEFAULT_BUTTONS;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_TEXT_IN_IMAGES;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_UPDATE_WELCOME_12_2;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_VOLUME_CONTROL_CHANGES;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_WINDOW_NAVIGATION;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_WINDOW_NAVIGATION_PRE_R;
import static com.google.android.accessibility.talkback.training.TrainingConfig.TrainingId.TRAINING_ID_ON_BOARDING_FOR_MULTIFINGER_GESTURES;
import static com.google.android.accessibility.talkback.training.TrainingConfig.TrainingId.TRAINING_ID_ON_BOARDING_TALKBACK_12_2;
import static com.google.android.accessibility.talkback.training.TrainingConfig.TrainingId.TRAINING_ID_ON_BOARDING_TALKBACK_12_2_PRE_R;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.PageConfig.PageContentPredicate;
import com.google.android.accessibility.talkback.training.PageConfig.PageId;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.common.collect.ImmutableList;

/** Starts a {@link TrainingActivity} to show onboarding. */
public class OnboardingInitiator {

  static final int NEW_GESTURE_NOTIFICATION_ID = 1;

  @StringRes
  private static final int newFeatureShownKey = R.string.pref_update_welcome_12_2_shown_key;

  /** A list of legacy preferences for old onboardings. */
  private static final int[] legacyKey = {R.string.pref_update_talkback91_shown_key};

  /** Sets onboarding preferences to true to ignore onboarding. */
  public static void ignoreOnboarding(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    if (!hasOnboardingBeenShown(prefs, context)) {
      updateNewFeaturePreference(prefs, context);
    }
    if (FeatureSupport.isMultiFingerGestureSupported()) {
      if (!prefs.getBoolean(
          context.getString(R.string.pref_update_multi_finger_gestures_shown_key), false)) {
        prefs
            .edit()
            .putBoolean(
                context.getString(R.string.pref_update_multi_finger_gestures_shown_key), true)
            .apply();
      }
    }
  }

  /** Checks if onboarding has been shown. */
  public static boolean hasOnboardingBeenShown(SharedPreferences prefs, Context context) {
    return SharedPreferencesUtils.getBooleanPref(
        prefs, context.getResources(), newFeatureShownKey, false);
  }

  /**
   * Shows onboarding if users update TalkBack, or shows a updated notification if users update to
   * Android R after having new TalkBack.
   */
  public static void showOnboardingIfNecessary(Context context) {
    String updateMultiFingerGesturesShownKey =
        context.getString(R.string.pref_update_multi_finger_gestures_shown_key);
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    boolean hasOnboardingForMultiFingerGesturesBeeShown =
        sharedPreferences.getBoolean(updateMultiFingerGesturesShownKey, false);

    if (hasOnboardingBeenShown(sharedPreferences, context)) {
      if (!hasOnboardingForMultiFingerGesturesBeeShown
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
                            context, TRAINING_ID_ON_BOARDING_FOR_MULTIFINGER_GESTURES),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE),
                    /* autoCancel= */ true));
        sharedPreferences.edit().putBoolean(updateMultiFingerGesturesShownKey, true).apply();
      }
    } else {
      if (FeatureSupport.isMultiFingerGestureSupported()) {
        context.startActivity(
            TrainingActivity.createTrainingIntent(context, TRAINING_ID_ON_BOARDING_TALKBACK_12_2));
        // Device is Android R, so it's unnecessary to show an onboarding for R.
        sharedPreferences.edit().putBoolean(updateMultiFingerGesturesShownKey, true).apply();
      } else {
        context.startActivity(
            TrainingActivity.createTrainingIntent(
                context, TRAINING_ID_ON_BOARDING_TALKBACK_12_2_PRE_R));
      }
      updateNewFeaturePreference(sharedPreferences, context);
    }
  }

  /** Returns an intent to start onboarding. */
  public static Intent createOnboardingIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        FeatureSupport.isMultiFingerGestureSupported()
            ? TRAINING_ID_ON_BOARDING_TALKBACK_12_2
            : TRAINING_ID_ON_BOARDING_TALKBACK_12_2_PRE_R);
  }

  /** Sets the preference of showing new feature pages and removes legacy of preferences. */
  private static void updateNewFeaturePreference(SharedPreferences prefs, Context context) {
    prefs.edit().putBoolean(context.getString(newFeatureShownKey), true).apply();
    removeLegacyPref(prefs, context);
  }

  private static void removeLegacyPref(SharedPreferences prefs, Context context) {
    Editor editor = prefs.edit();
    for (int key : legacyKey) {
      editor.remove(context.getString(key));
    }
    editor.apply();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages

  static final PageConfig.Builder WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES =
      PageConfig.builder(
              PageId.PAGE_ID_WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES,
              R.string.welcome_to_updated_talkback_title)
          .addText(R.string.welcome_to_android11_text)
          .addNote(R.string.new_shortcut_gesture_note, PageContentPredicate.GESTURE_CHANGED)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_pause_or_play_media_text,
              R.string.new_shortcut_gesture_pause_or_play_media_subtext,
              R.drawable.ic_gesture_2fingerdoubletap)
          .captureGesture(
              AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP,
              R.string.new_shortcut_gesture_pause_media_announcement)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_stop_speech_text, R.drawable.ic_gesture_2fingertap)
          .captureGesture(
              AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP,
              R.string.new_shortcut_gesture_stop_speech_announcement)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_reading_menu_text, R.drawable.ic_gesture_3fingerright)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_copy_text_text, R.drawable.ic_gesture_3fingerdoubletap)
          .captureGesture(
              AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP,
              R.string.new_shortcut_gesture_copy_text_announcement)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_paste_text_text, R.drawable.ic_gesture_3fingertripletap)
          .captureGesture(
              AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP,
              R.string.new_shortcut_gesture_paste_text_announcement)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_cut_text_text,
              R.drawable.ic_gesture_3fingerdoubletaphold)
          .captureGesture(
              AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD,
              R.string.new_shortcut_gesture_cut_text_announcement)
          .addTextWithIcon(
              R.string.new_shortcut_gesture_selection_mode_text,
              R.drawable.ic_gesture_2fingerdoubletaphold)
          .captureGesture(
              AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD,
              R.string.new_shortcut_gesture_selection_mode_on_announcement);
  static final PageConfig.Builder UPDATE_WELCOME_12_2 =
      PageConfig.builder(PAGE_ID_UPDATE_WELCOME_12_2, R.string.welcome_to_updated_talkback_title)
          .addText(R.string.update_welcome_12_2);
  static final PageConfig.Builder VOLUME_CONTROL_CHANGES =
      PageConfig.builder(PAGE_ID_VOLUME_CONTROL_CHANGES, R.string.volume_control_changes_title)
          .addText(R.string.volume_control_changes_text);
  static final PageConfig.Builder TEXT_IN_IMAGES =
      PageConfig.builder(PAGE_ID_TEXT_IN_IMAGES, R.string.text_in_images_title)
          .addText(R.string.text_in_images_text);
  static final PageConfig.Builder WINDOW_NAVIGATION =
      PageConfig.builder(PAGE_ID_WINDOW_NAVIGATION, R.string.window_navigation_title)
          .addText(R.string.window_navigation_text);
  static final PageConfig.Builder WINDOW_NAVIGATION_PRE_R =
      PageConfig.builder(PAGE_ID_WINDOW_NAVIGATION_PRE_R, R.string.window_navigation_title)
          .addText(R.string.window_navigation_text_pre_r);

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Training

  static final TrainingConfig ON_BOARDING_TALKBACK_12_2 =
      TrainingConfig.builder(R.string.new_feature_in_talkback_title)
          .setPages(
              ImmutableList.of(
                  UPDATE_WELCOME_12_2, VOLUME_CONTROL_CHANGES, TEXT_IN_IMAGES, WINDOW_NAVIGATION))
          .setButtons(DEFAULT_BUTTONS)
          .build();
  static final TrainingConfig ON_BOARDING_TALKBACK_12_2_PRE_R =
      TrainingConfig.builder(R.string.new_feature_in_talkback_title)
          .setPages(
              ImmutableList.of(
                  UPDATE_WELCOME_12_2,
                  VOLUME_CONTROL_CHANGES,
                  WINDOW_NAVIGATION_PRE_R))
          .setButtons(DEFAULT_BUTTONS)
          .build();
  static final TrainingConfig ON_BOARDING_FOR_MULTIFINGER_GESTURES =
      TrainingConfig.builder(R.string.welcome_to_updated_talkback_title)
          .setPages(ImmutableList.of(WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES))
          .setButtons(ImmutableList.of(BUTTON_TYPE_EXIT))
          .build();
}
