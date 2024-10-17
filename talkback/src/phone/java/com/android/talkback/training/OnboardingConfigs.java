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

package com.google.android.accessibility.talkback.training;

import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_EXIT;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.DEFAULT_BUTTONS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageAndContentPredicate.SUPPORT_EXIT_BANNER;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_DETAILED_IMAGE_DESCRIPTIONS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_GOOGLE_DISABILITY_SUPPORT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_NEW_BRAILLE_SHORTCUTS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_PUNCTUATION_AND_SYMBOLS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_UPDATE_WELCOME;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingUtils.GUP_SUPPORT_PORTAL_URL;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingUtils.IMAGE_DESCRIPTION_SUPPORTED_LANGUAGES_URL;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingUtils.VERBOSITY_OPTION_HELP_CENTER_URL;

import android.accessibilityservice.AccessibilityService;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageAndContentPredicate;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

final class OnboardingConfigs {

  private OnboardingConfigs() {}

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages
  static final PageConfig.Builder welcomeToUpdatedTalkBackForMultiFingerGestures =
      PageConfig.builder(
              PageId.PAGE_ID_WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES,
              R.string.welcome_to_updated_talkback_title)
          .addText(R.string.welcome_to_android11_text)
          .addNote(R.string.new_shortcut_gesture_note, PageAndContentPredicate.GESTURE_CHANGED)
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
  // For TB 15.0.1
  static final PageConfig.Builder updateWelcome =
      PageConfig.builder(PAGE_ID_UPDATE_WELCOME, R.string.welcome_to_updated_talkback_title)
          .addExitBanner(SUPPORT_EXIT_BANNER)
          .addText(R.string.update_welcome_text_head)
          .addTextWithBullet(R.string.update_welcome_text_item_one)
          .addTextWithBullet(R.string.update_welcome_text_item_two, /* subText= */ true)
          .addTextWithBullet(R.string.update_welcome_text_item_three, /* subText= */ true)
          .addTextWithBullet(R.string.update_welcome_text_item_four, /* subText= */ true)
          .addTextWithBullet(R.string.update_welcome_text_item_five, /* subText= */ true)
          .addTextWithTtsSpan(
              R.string.update_welcome_text_tail, R.string.update_welcome_text_tail_tts);

  static final PageConfig.Builder imageDescription =
      PageConfig.builder(
              PAGE_ID_DETAILED_IMAGE_DESCRIPTIONS,
              R.string.onboarding_detailed_image_descriptions_title)
          .addText(R.string.onboarding_detailed_image_descriptions_content_1)
          .addImage(
              R.drawable.onboarding_sample_image,
              R.string.onboarding_detailed_image_descriptions_image_content_description)
          .addTextWithTtsSpan(
              R.string.onboarding_detailed_image_descriptions_content_2,
              R.string.onboarding_detailed_image_descriptions_content_2_tts)
          .addText(R.string.onboarding_detailed_image_descriptions_content_3)
          .addSubTextWithLink(
              R.string.onboarding_detailed_image_descriptions_content_4,
              IMAGE_DESCRIPTION_SUPPORTED_LANGUAGES_URL);

  static final PageConfig.Builder googleDisabilitySupport =
      PageConfig.builder(
              PAGE_ID_GOOGLE_DISABILITY_SUPPORT,
              R.string.onboarding_google_disability_support_title)
          .addTextWithTtsSpan(
              R.string.onboarding_google_disability_support_content,
              R.string.onboarding_google_disability_support_content_tts)
          .addTextWithLink(
              R.string.onboarding_google_disability_support_content_with_link,
              GUP_SUPPORT_PORTAL_URL);

  // TODO: Add URL to learn more string when URL is confirmed.
  static final PageConfig.Builder punctuationAndSymbols =
      PageConfig.builder(
              PAGE_ID_PUNCTUATION_AND_SYMBOLS, R.string.onboarding_punctuation_and_symbols_title)
          .addTextWithTtsSpan(
              R.string.onboarding_punctuation_and_symbols_content,
              R.string.onboarding_punctuation_and_symbols_content_tts)
          .addTextWithLink(
              R.string.onboarding_punctuation_and_symbols_content_with_link,
              VERBOSITY_OPTION_HELP_CENTER_URL);

  static final PageConfig.Builder newBrailleShortcuts =
      PageConfig.builder(
              PAGE_ID_NEW_BRAILLE_SHORTCUTS, R.string.new_braille_shortcuts_and_languages_title)
          .addText(R.string.new_braille_shortcuts_introduction)
          .addHeading(R.string.new_braille_shortcuts_heading_one)
          .addTextWithBullet(R.string.new_braille_display_shortcuts_bullet_point_one)
          .addTextWithBullet(
              R.string.new_braille_display_shortcuts_bullet_point_two, /* subText= */ true)
          .addHeading(R.string.new_braille_shortcuts_heading_two)
          .addTextWithBullet(R.string.new_braille_keyboard_shortcuts_bullet_point_one)
          .addTextWithBullet(
              R.string.new_braille_keyboard_shortcuts_bullet_point_two, /* subText= */ true)
          .addText(R.string.new_braille_shortcuts_end);

  // Training
  static TrainingConfig getTalkBackOnBoardingForSettings() {
    return constructOnBoardingConfigBuilder()
        .setSupportNavigateUpArrow(true)
        .setExitButtonOnlyShowOnLastPage(true)
        .build();
  }

  static TrainingConfig getTalkBackOnBoardingForUpdated() {
    return constructOnBoardingConfigBuilder().build();
  }

  static final TrainingConfig ON_BOARDING_FOR_MULTIFINGER_GESTURES =
      TrainingConfig.builder(R.string.welcome_to_updated_talkback_title)
          .setPages(ImmutableList.of(welcomeToUpdatedTalkBackForMultiFingerGestures))
          .setButtons(ImmutableList.of(BUTTON_TYPE_EXIT))
          .build();

  private static TrainingConfig.Builder constructOnBoardingConfigBuilder() {
    List<PageConfig.Builder> pages = new ArrayList<>();
    pages.add(updateWelcome);
    pages.add(imageDescription);
    pages.add(googleDisabilitySupport);
    pages.add(punctuationAndSymbols);
    return TrainingConfig.builder(R.string.new_feature_in_talkback_title)
        .setPages(pages)
        .addPageEndOfSection(newBrailleShortcuts)
        .setButtons(DEFAULT_BUTTONS);
  }
}
