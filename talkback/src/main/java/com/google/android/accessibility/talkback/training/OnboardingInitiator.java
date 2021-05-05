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

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.PageConfig.PageContentPredicate;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.common.collect.ImmutableList;

/** Starts a {@link TrainingActivity} to show onboarding. */
public class OnboardingInitiator {

  static final int NEW_GESTURE_NOTIFICATION_ID = 1;

  /** Sets onboarding preferences to true to ignore onboarding. */
  public static void ignoreOnboarding(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    if (!prefs.getBoolean(context.getString(R.string.pref_update_talkback91_shown_key), false)) {
      prefs
          .edit()
          .putBoolean(context.getString(R.string.pref_update_talkback91_shown_key), true)
          .apply();
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

  /**
   * Shows onboarding if users update TalkBack to 9.1, or shows a updated notification if users
   * update to Android R after having TalkBack 9.1.
   */
  public static void showOnboarding91IfNecessary(Context context) {
    String newFeatureTalkBack91ShownKey =
        context.getString(R.string.pref_update_talkback91_shown_key);
    String updateMultiFingerGesturesShownKey =
        context.getString(R.string.pref_update_multi_finger_gestures_shown_key);
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    boolean isOnboardingFor91Shown =
        sharedPreferences.getBoolean(newFeatureTalkBack91ShownKey, false);
    boolean isOnboardingForMultiFingerGesturesShown =
        sharedPreferences.getBoolean(updateMultiFingerGesturesShownKey, false);

    if (isOnboardingFor91Shown) {
      if (!isOnboardingForMultiFingerGesturesShown
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
                        TrainingActivity.createTrainingIntent(context, ON_BOARDING_ANDROID_11),
                        PendingIntent.FLAG_UPDATE_CURRENT),
                    /* autoCancel= */ true));
        sharedPreferences.edit().putBoolean(updateMultiFingerGesturesShownKey, true).apply();
      }
    } else {
      if (FeatureSupport.isMultiFingerGestureSupported()) {
        context.startActivity(
            TrainingActivity.createTrainingIntent(context, ON_BOARDING_TALKBACK_91));
        // Device is Android R, so it's unnecessary to show an onboarding for R.
        sharedPreferences.edit().putBoolean(updateMultiFingerGesturesShownKey, true).apply();
      } else {
        context.startActivity(
            TrainingActivity.createTrainingIntent(context, ON_BOARDING_TALKBACK_91_PRE_R));
      }
      sharedPreferences.edit().putBoolean(newFeatureTalkBack91ShownKey, true).apply();
    }
  }

  /** Returns an intent to start onboarding. */
  public static Intent createOnboardingIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        FeatureSupport.isMultiFingerGestureSupported()
            ? ON_BOARDING_TALKBACK_91
            : ON_BOARDING_TALKBACK_91_PRE_R);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages

  private static final PageConfig.Builder WELCOME_TO_UPDATED_TALKBACK_PAGE_PRE_R =
      PageConfig.builder(R.string.welcome_to_updated_talkback_title)
          .addText(R.string.welcome_to_updated_talkback_text_pre_r);
  private static final PageConfig.Builder NAVIGATION_AND_SETTING_SELECTOR_PAGE_PRE_R =
      PageConfig.builder(R.string.setting_selector_heading)
          .addText(R.string.setting_selector_text_pre_r)
          .addTextWithIcon(
              R.string.try_setting_selector_gesture_pre_r, R.drawable.ic_gesture_downthenup)
          .addText(R.string.customize_setting_selector_text_pre_r);
  private static final PageConfig.Builder MORE_NEW_FEATURES_PAGE_PRE_R =
      PageConfig.builder(R.string.more_new_feature_title)
          .addTextWithBullet(R.string.more_new_feature_context_menu_text_pre_r)
          .addTextWithActualGestureAndBullet(
              R.string.more_new_feature_voice_command_text_with_actual_gesture,
              R.string.shortcut_value_voice_commands,
              R.string.more_new_feature_voice_command_default_text)
          .addTextWithBullet(R.string.more_new_feature_braille_keyboard_text);
  private static final PageConfig.Builder WELCOME_TO_UPDATED_TALKBACK_PAGE =
      PageConfig.builder(R.string.welcome_to_updated_talkback_title)
          .addText(R.string.welcome_to_updated_talkback_text);
  private static final PageConfig.Builder NEW_SHORTCUT_GESTURE_PAGE =
      PageConfig.builder(R.string.new_shortcut_gesture_title)
          .addText(R.string.new_shortcut_gesture_text)
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
  private static final PageConfig.Builder NAVIGATION_AND_SETTING_SELECTOR_PAGE =
      PageConfig.builder(R.string.setting_selector_heading)
          .addText(R.string.setting_selector_text)
          .addTextWithIcon(
              R.string.try_setting_selector_gesture, R.drawable.ic_gesture_3fingerright)
          .addText(R.string.customize_setting_selector_text);
  private static final PageConfig.Builder MORE_NEW_FEATURES_PAGE =
      PageConfig.builder(R.string.more_new_feature_title)
          .addTextWithBullet(R.string.more_new_feature_context_menu_text)
          .addTextWithActualGestureAndBullet(
              R.string.more_new_feature_voice_command_text_with_actual_gesture,
              R.string.shortcut_value_voice_commands,
              R.string.more_new_feature_voice_command_default_text)
          .addTextWithBullet(R.string.more_new_feature_braille_keyboard_text);
  private static final PageConfig.Builder WELCOME_TO_ANDROID11 =
      PageConfig.builder(R.string.welcome_to_android11)
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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Training

  static final TrainingConfig ON_BOARDING_TALKBACK_91_PRE_R =
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
          .setPages(
              ImmutableList.of(
                  WELCOME_TO_UPDATED_TALKBACK_PAGE_PRE_R,
                  NAVIGATION_AND_SETTING_SELECTOR_PAGE_PRE_R,
                  MORE_NEW_FEATURES_PAGE_PRE_R))
          .setButtons(DEFAULT_BUTTONS)
          .build();
  static final TrainingConfig ON_BOARDING_TALKBACK_91 =
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
          .setPages(
              ImmutableList.of(
                  WELCOME_TO_UPDATED_TALKBACK_PAGE,
                  NEW_SHORTCUT_GESTURE_PAGE,
                  NAVIGATION_AND_SETTING_SELECTOR_PAGE,
                  MORE_NEW_FEATURES_PAGE))
          .setButtons(DEFAULT_BUTTONS)
          .build();
  static final TrainingConfig ON_BOARDING_ANDROID_11 =
      TrainingConfig.builder(R.string.welcome_to_android11)
          .setPages(ImmutableList.of(WELCOME_TO_ANDROID11))
          .setButtons(ImmutableList.of(BUTTON_TYPE_EXIT))
          .build();
}
