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

import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_DOWNLOAD_ICON_DETECTION;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_DOWNLOAD_IMAGE_DESCRIPTION;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_EXIT;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.DEFAULT_BUTTONS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate.ICON_DETECTION_AND_IMAGE_DESCRIPTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate.ICON_DETECTION_AVAILABLE_BUT_IMAGE_DESCRIPTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate.IMAGE_DESCRIPTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate.SUPPORT_EXIT_BANNER;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_AUTO_SCROLL_FOR_BRAILLE_DISPLAY;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_DESCRIBE_IMAGES;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_NEW_BRAILLE_SHORTCUTS_AND_LANGUAGES;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_SPELL_CHECK_FOR_BRAILLE_KEYBOARD;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_UPDATE_WELCOME;
import static com.google.android.accessibility.talkback.trainingcommon.content.PageButton.PageButtonAction.BRAILLE_TUTORIAL;

import android.accessibilityservice.AccessibilityService;
import android.os.Message;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.android.accessibility.talkback.trainingcommon.content.Text.TextWithActualGestureParameter;
import com.google.common.collect.ImmutableList;

final class OnboardingConfigs {

  private OnboardingConfigs() {}

  private static final String BRAILLE_SUPPORT_LANGUAGE_LINK =
      "https://support.google.com/accessibility/android/topic/10601975?hl=en&ref_topic=3529932&sjid=7471933143919295090-AP";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages
  static final PageConfig.Builder welcomeToUpdatedTalkBackForMultiFingerGestures =
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
  // For TB 14.1
  static final PageConfig.Builder updateWelcome =
      PageConfig.builder(PAGE_ID_UPDATE_WELCOME, R.string.welcome_to_updated_talkback_title)
          .addExitBanner(SUPPORT_EXIT_BANNER)
          .addText(R.string.update_welcome_text);
  static final PageConfig.Builder describeImages =
      PageConfig.builder(PAGE_ID_DESCRIBE_IMAGES, R.string.describe_images_title)
          .addTextWithActualGesture(
              ImmutableList.of(
                  // If (ICON_DETECTION_AND_IMAGE_DESCRIPTION_UNAVAILABLE)
                  TextWithActualGestureParameter.create(
                      R.string.describe_images_text,
                      R.string.shortcut_value_talkback_breakout,
                      R.string.title_pref_shortcut_3finger_1tap,
                      ICON_DETECTION_AND_IMAGE_DESCRIPTION_UNAVAILABLE),
                  // else if (ICON_DETECTION_AVAILABLE_BUT_IMAGE_DESCRIPTION_UNAVAILABLE)
                  TextWithActualGestureParameter.create(
                      R.string.describe_images_text_icon_exist,
                      R.string.shortcut_value_talkback_breakout,
                      R.string.title_pref_shortcut_3finger_1tap,
                      ICON_DETECTION_AVAILABLE_BUT_IMAGE_DESCRIPTION_UNAVAILABLE),
                  // else
                  TextWithActualGestureParameter.create(
                      R.string.describe_images_text_icon_and_image_exist,
                      R.string.shortcut_value_talkback_breakout,
                      R.string.title_pref_shortcut_3finger_1tap,
                      /* predicate= */ null)))
          .addButton(
              R.string.download_image_descriptions_button,
              Message.obtain(/* h= */ null, MSG_DOWNLOAD_IMAGE_DESCRIPTION),
              IMAGE_DESCRIPTION_UNAVAILABLE)
          // Shows the icon detection download button only when the image description hasn't been
          // downloaded.
          .addButton(
              R.string.download_icon_descriptions_button,
              Message.obtain(/* h= */ null, MSG_DOWNLOAD_ICON_DETECTION),
              ICON_DETECTION_AND_IMAGE_DESCRIPTION_UNAVAILABLE)
          .addText(R.string.describe_images_gesture_hint);
  static final PageConfig.Builder spellCheckForBrailleKeyboard =
      PageConfig.builder(
              PAGE_ID_SPELL_CHECK_FOR_BRAILLE_KEYBOARD,
              R.string.spell_check_for_braille_keyboard_title)
          .addText(R.string.spell_check_for_braille_keyboard_text)
          .addButton(R.string.view_tutorial_button_text, BRAILLE_TUTORIAL);
  static final PageConfig.Builder autoScrollForBrailleDisplay =
      PageConfig.builder(
              PAGE_ID_AUTO_SCROLL_FOR_BRAILLE_DISPLAY,
              R.string.auto_scroll_for_braille_display_title)
          .addText(R.string.auto_scroll_for_braille_display_text);

  static final PageConfig.Builder newBrailleShortcutsAndLanguages =
      PageConfig.builder(
              PAGE_ID_NEW_BRAILLE_SHORTCUTS_AND_LANGUAGES,
              R.string.new_braille_shortcuts_and_languages_title)
          .addText(R.string.new_braille_shortcuts_and_languages_text)
          .addTextWithLink(R.string.learn_more_braille_languages, BRAILLE_SUPPORT_LANGUAGE_LINK);

  // Training
  static final TrainingConfig ON_BOARDING_TALKBACK =
      TrainingConfig.builder(R.string.new_feature_in_talkback_title)
          .setPages(
              ImmutableList.of(
                  updateWelcome,
                  describeImages,
                  spellCheckForBrailleKeyboard,
                  autoScrollForBrailleDisplay))
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig ON_BOARDING_TALKBACK_WITHOUT_DESCRIBE_IMAGE =
      TrainingConfig.builder(R.string.new_feature_in_talkback_title)
          .setPages(
              ImmutableList.of(
                  updateWelcome, spellCheckForBrailleKeyboard, autoScrollForBrailleDisplay))
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig ON_BOARDING_FOR_MULTIFINGER_GESTURES =
      TrainingConfig.builder(R.string.welcome_to_updated_talkback_title)
          .setPages(ImmutableList.of(welcomeToUpdatedTalkBackForMultiFingerGestures))
          .setButtons(ImmutableList.of(BUTTON_TYPE_EXIT))
          .build();
}
