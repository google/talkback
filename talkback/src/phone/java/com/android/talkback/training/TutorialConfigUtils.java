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

import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.DEFAULT_BUTTONS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageAndContentPredicate.ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageAndContentPredicate.SUPPORT_EXIT_BANNER;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageAndContentPredicate.TALKBACK_ON;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_ADDITIONAL_TIPS_CHECKING_NOTIFICATIONS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_ADDITIONAL_TIPS_LOOKOUT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_ADJUSTING_VOLUME;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_COPY_CUT_PASTE;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_COPY_CUT_PASTE_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_EXPLORE_BY_TOUCH;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_JUMP_BETWEEN_CONTROLS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_JUMP_BETWEEN_HEADINGS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_JUMP_BETWEEN_LINKS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_MENUS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_MENUS_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_MOVING_CURSOR;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_PRACTICE_GESTURES;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_PRACTICE_GESTURES_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_READ_BY_CHARACTER;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_READ_BY_CHARACTER_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_SCROLLING;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_SELECTING_TEXT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_SELECTING_TEXT_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TUTORIAL_FINISHED;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TUTORIAL_INDEX;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TYPING_TEXT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TYPO_CORRECTION;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TYPO_CORRECTION_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_USING_TEXT_BOXES;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_VOICE_COMMANDS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_WELCOME_TO_TALKBACK;
import static com.google.android.accessibility.talkback.trainingcommon.content.PageButton.PageButtonAction.OPEN_LOOKOUT_PAGE;
import static com.google.android.accessibility.talkback.trainingcommon.content.PageButton.PageButtonAction.OPEN_READING_MODE_PAGE;

import android.content.Context;
import android.text.TextUtils;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.TextServicesManager;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.android.accessibility.talkback.trainingcommon.TrainingUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TutorialConfigUtils {
  private static final String TAG = "TutorialConfigUtils";

  private TutorialConfigUtils() {}

  private static final int DEFAULT_ANNOUNCEMENT_INITIAL_DELAY_MS = 2 * 60 * 1000; // 2 minutes.
  private static final int DEFAULT_ANNOUNCEMENT_REPEATED_DELAY_MS = 30 * 1000; // 30 seconds.

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages

  @Nullable
  static PageConfig.Builder getInitialPageBuilder(PageId pageId) {
    switch (pageId) {
      case PAGE_ID_WELCOME_TO_TALKBACK:
        return PageConfig.builder(PAGE_ID_WELCOME_TO_TALKBACK, R.string.welcome_to_talkback_title)
            .setOnlyOneFocus(true)
            .setIdleAnnouncement(
                R.string.welcome_to_talkback_page_idle_announcement,
                DEFAULT_ANNOUNCEMENT_INITIAL_DELAY_MS,
                DEFAULT_ANNOUNCEMENT_REPEATED_DELAY_MS)
            .addExitBanner(SUPPORT_EXIT_BANNER)
            .addTextWithTtsSpan(
                R.string.welcome_to_talkback_text, R.string.welcome_to_talkback_text_tts);
      case PAGE_ID_EXPLORE_BY_TOUCH:
        return PageConfig.builder(PAGE_ID_EXPLORE_BY_TOUCH, R.string.explore_by_touch_title)
            .addText(R.string.explore_by_touch_text);
      case PAGE_ID_SCROLLING:
        return PageConfig.builder(PAGE_ID_SCROLLING, R.string.scrolling_title)
            .addText(R.string.scrolling_text)
            .addList(R.array.tutorial_scrolling_item_titles)
            .addWholeScreenText(R.string.scrolling_text_completed);
      case PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER:
        return PageConfig.builder(
                PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER, R.string.system_gestures_title)
            .addText(R.string.system_gestures_text)
            .addTextWithIcon(
                R.string.system_gestures_home_text, R.drawable.ic_gesture_2fingeredgeup)
            .addTextWithIcon(
                R.string.system_gestures_overview_screen_text,
                R.drawable.ic_gesture_2fingeredgeuphold)
            .addTextWithIcon(
                R.string.system_gestures_back_text, R.drawable.ic_gesture_2fingerinward)
            .addTextWithIcon(
                R.string.system_gestures_open_notifications_text_for_gesture_navigation,
                R.drawable.ic_gesture_2fingeredgedown)
            .addTextWithIcon(
                R.string.system_gestures_accessibility_shortcut_text,
                R.drawable.ic_gesture_3fingeredgeup,
                ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT);
      case PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER:
        return PageConfig.builder(
                PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER, R.string.system_gestures_title)
            .addTextWithIcon(
                R.string.system_gestures_open_notifications_text_for_3_button_navigation,
                R.drawable.ic_gesture_2fingeredgedown);
      case PAGE_ID_ADJUSTING_VOLUME:
        return PageConfig.builder(PAGE_ID_ADJUSTING_VOLUME, R.string.adjusting_volume_title)
            .addText(R.string.adjusting_volume_head)
            .addTextWithBullet(R.string.adjusting_volume_first_instruction)
            .addTextWithBullet(R.string.adjusting_volume_second_instruction, /* subText= */ true)
            .addText(R.string.adjusting_volume_tail);
      case PAGE_ID_MENUS:
        return PageConfig.builder(PageId.PAGE_ID_MENUS, R.string.menus_title)
            .addHeading(R.string.talkback_menu_title)
            .addText(R.string.menus_talkback_menu_text)
            .addHeading(R.string.setting_selector_heading)
            .addText(R.string.menus_selector_text);
      case PAGE_ID_MENUS_PRE_R:
        return PageConfig.builder(PAGE_ID_MENUS_PRE_R, R.string.menus_title)
            .addHeading(R.string.talkback_menu_title)
            .addText(R.string.menus_talkback_menu_text_pre_r)
            .addHeading(R.string.setting_selector_heading)
            .addText(R.string.menus_selector_text_pre_r);
      case PAGE_ID_TUTORIAL_FINISHED:
        return PageConfig.builder(PAGE_ID_TUTORIAL_FINISHED, R.string.all_set_title)
            .addText(R.string.all_set_text)
            .addLink(
                R.string.text_editing_link_text,
                R.string.text_editing_link_subtext,
                R.drawable.ic_text_fields_alt_googblue_24dp,
                R.string.using_text_boxes_title)
            .addLink(
                R.string.reading_navigation_link_text,
                R.string.reading_navigation_link_subtext,
                R.drawable.ic_chrome_reader_mode_googblue_24dp,
                R.string.read_by_character_title)
            .addLink(
                R.string.voice_commands_link_text,
                R.string.voice_commands_link_subtext,
                R.drawable.ic_keyboard_voice_googblue_24dp,
                R.string.voice_commands_title)
            .addLink(
                R.string.additional_tips_link_text,
                R.string.additional_tips_link_subtext,
                R.drawable.ic_tips_and_updates_24dp,
                R.string.making_calls_title)
            .addLink(
                R.string.practice_gestures_link_text,
                R.string.practice_gestures_link_subtext,
                R.drawable.ic_gesture_googblue_24dp,
                R.string.practice_gestures_title);
      case PAGE_ID_TUTORIAL_INDEX:
        return PageConfig.builder(PAGE_ID_TUTORIAL_INDEX, R.string.talkback_tutorial_title)
            .addText(R.string.talkback_tutorial_text)
            .addLink(
                R.string.basic_navigation_link_text,
                R.string.basic_navigation_link_subtext,
                R.drawable.ic_navigation_googblue_24dp,
                R.string.welcome_to_talkback_title)
            .addLink(
                R.string.text_editing_link_text,
                R.string.text_editing_link_subtext,
                R.drawable.ic_text_fields_alt_googblue_24dp,
                R.string.using_text_boxes_title)
            .addLink(
                R.string.reading_navigation_link_text,
                R.string.reading_navigation_link_subtext,
                R.drawable.ic_chrome_reader_mode_googblue_24dp,
                R.string.read_by_character_title)
            .addLink(
                R.string.voice_commands_link_text,
                R.string.voice_commands_link_subtext,
                R.drawable.ic_keyboard_voice_googblue_24dp,
                R.string.voice_commands_title)
            .addLink(
                R.string.additional_tips_link_text,
                R.string.additional_tips_link_subtext,
                R.drawable.ic_tips_and_updates_24dp,
                R.string.making_calls_title)
            .addLinkCondition(
                R.string.practice_gestures_link_text,
                R.string.practice_gestures_link_subtext,
                R.drawable.ic_gesture_googblue_24dp,
                TALKBACK_ON,
                context -> {
                  if (SettingsUtils.allowLinksOutOfSettings(context)) {
                    A11yAlertDialogWrapper.materialDialogBuilder(context)
                        .setTitle(R.string.talkback_inactive_title)
                        .setMessage(R.string.talkback_inactive_message)
                        .setCancelable(true)
                        .setOnCancelListener(null)
                        .setPositiveButton(
                            R.string.talkback_inactive_go_to_settings_button,
                            (dialog, which) ->
                                context.startActivity(
                                    TrainingUtils
                                        .getAccessibilitySettingsAndHighLightTalkBackIntent()))
                        .setNegativeButton(R.string.talkback_inactive_not_now_button, null)
                        .create()
                        .show();
                  }
                },
                R.string.practice_gestures_title);
      case PAGE_ID_USING_TEXT_BOXES:
        return PageConfig.builder(PAGE_ID_USING_TEXT_BOXES, R.string.using_text_boxes_title)
            .addText(R.string.using_text_boxes_text)
            .addEditTextWithHint(R.string.edit_box_hint);
      case PAGE_ID_TYPING_TEXT:
        return PageConfig.builder(PAGE_ID_TYPING_TEXT, R.string.typing_text_title)
            .addText(R.string.typing_text_text)
            .addEditTextWithHint(R.string.enter_text_here)
            .addText(R.string.typing_text_with_braille_keyboard_text);
      case PAGE_ID_MOVING_CURSOR:
        return PageConfig.builder(PAGE_ID_MOVING_CURSOR, R.string.moving_cursor_title)
            .addText(R.string.moving_cursor_text)
            .addEditTextWithContent(R.string.edit_box_text);
      case PAGE_ID_SELECTING_TEXT:
        return PageConfig.builder(PAGE_ID_SELECTING_TEXT, R.string.selecting_text_title)
            .addText(R.string.selecting_text_text)
            .addEditTextWithContent(R.string.edit_box_text);
      case PAGE_ID_SELECTING_TEXT_PRE_R:
        return PageConfig.builder(
                PageId.PAGE_ID_SELECTING_TEXT_PRE_R, R.string.selecting_text_title)
            .addText(R.string.selecting_text_text_pre_r)
            .addEditTextWithContent(R.string.edit_box_text);
      case PAGE_ID_COPY_CUT_PASTE:
        return PageConfig.builder(PAGE_ID_COPY_CUT_PASTE, R.string.copy_cut_paste_title)
            .addText(R.string.copy_text)
            .addEditTextWithContent(R.string.edit_box_text)
            .addText(R.string.cut_paste_text)
            .addEditTextWithHint(R.string.edit_box_hint_paste_text);
      case PAGE_ID_COPY_CUT_PASTE_PRE_R:
        return PageConfig.builder(
                PageId.PAGE_ID_COPY_CUT_PASTE_PRE_R, R.string.copy_cut_paste_title)
            .addText(R.string.copy_text_pre_r)
            .addEditTextWithContent(R.string.edit_box_text)
            .addText(R.string.cut_paste_text_pre_r)
            .addEditTextWithHint(R.string.edit_box_hint_paste_text);
      case PAGE_ID_TYPO_CORRECTION:
        return PageConfig.builder(PAGE_ID_TYPO_CORRECTION, R.string.typo_correction_title)
            .addText(R.string.typo_correction_text)
            .addEditTextWithContent(R.string.typo_correction_typo_example);
      case PAGE_ID_TYPO_CORRECTION_PRE_R:
        return PageConfig.builder(PAGE_ID_TYPO_CORRECTION_PRE_R, R.string.typo_correction_title)
            .addText(R.string.typo_correction_text_pre_r)
            .addEditTextWithContent(R.string.typo_correction_typo_example);
      case PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH:
        return PageConfig.builder(
                PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH, R.string.typo_correction_title)
            .addText(R.string.typo_correction_text_supported_but_not_english)
            .addEditTextWithHint(R.string.typo_correction_editbox_hint);
      case PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH_PRE_R:
        return PageConfig.builder(
                PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH_PRE_R, R.string.typo_correction_title)
            .addText(R.string.typo_correction_text_supported_but_not_english_pre_r)
            .addEditTextWithHint(R.string.typo_correction_editbox_hint);
      case PAGE_ID_READ_BY_CHARACTER:
        return PageConfig.builder(PAGE_ID_READ_BY_CHARACTER, R.string.read_by_character_title)
            .addText(
                R.string.read_by_character_text, ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_character));
      case PAGE_ID_READ_BY_CHARACTER_PRE_R:
        return PageConfig.builder(PAGE_ID_READ_BY_CHARACTER_PRE_R, R.string.read_by_character_title)
            .addText(
                R.string.read_by_character_text_pre_r,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_character));
      case PAGE_ID_JUMP_BETWEEN_CONTROLS:
        return PageConfig.builder(
                PAGE_ID_JUMP_BETWEEN_CONTROLS, R.string.jump_between_controls_title)
            .addText(
                R.string.jump_between_controls_text,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_native_control))
            .addButton(R.string.button_1)
            .addButton(R.string.button_2)
            .addButton(R.string.button_3)
            .addButton(R.string.button_4);
      case PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R:
        return PageConfig.builder(
                PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R, R.string.jump_between_controls_title)
            .addText(
                R.string.jump_between_controls_text_pre_r,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_native_control))
            .addButton(R.string.button_1)
            .addButton(R.string.button_2)
            .addButton(R.string.button_3)
            .addButton(R.string.button_4);
      case PAGE_ID_JUMP_BETWEEN_LINKS:
        return PageConfig.builder(PAGE_ID_JUMP_BETWEEN_LINKS, R.string.jump_between_links_title)
            .addText(
                R.string.jump_between_links_text,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_native_link))
            .addText(R.string.paragraph1_text)
            .addTextWithLink(R.string.link1_text)
            .addText(R.string.paragraph2_text)
            .addTextWithLink(R.string.link2_text)
            .addText(R.string.paragraph3_text)
            .addTextWithLink(R.string.link3_text)
            .addText(R.string.paragraph4_text)
            .addTextWithLink(R.string.link4_text)
            .addTextWithLink(R.string.target_link_text);
      case PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R:
        return PageConfig.builder(
                PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R, R.string.jump_between_links_title)
            .addText(
                R.string.jump_between_links_text_pre_r,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_native_link))
            .addText(R.string.paragraph1_text)
            .addTextWithLink(R.string.link1_text)
            .addText(R.string.paragraph2_text)
            .addTextWithLink(R.string.link2_text)
            .addText(R.string.paragraph3_text)
            .addTextWithLink(R.string.link3_text)
            .addText(R.string.paragraph4_text)
            .addTextWithLink(R.string.link4_text)
            .addTextWithLink(R.string.target_link_text);
      case PAGE_ID_JUMP_BETWEEN_HEADINGS:
        return PageConfig.builder(
                PAGE_ID_JUMP_BETWEEN_HEADINGS, R.string.jump_between_headings_title)
            .addText(
                R.string.jump_between_headings_text,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_native_heading))
            .addDivider()
            .addHeading(R.string.content_heading)
            .addText(R.string.content_text)
            .addDivider()
            .addHeading(R.string.navigation_heading)
            .addText(R.string.find_finish_button_text);
      case PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R:
        return PageConfig.builder(
                PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R, R.string.jump_between_headings_title)
            .addText(
                R.string.jump_between_headings_text_pre_r,
                ImmutableList.of(com.google.android.accessibility.utils.R.string.granularity_native_heading))
            .addDivider()
            .addHeading(R.string.content_heading)
            .addText(R.string.content_text)
            .addDivider()
            .addHeading(R.string.navigation_heading)
            .addText(R.string.find_finish_button_text);
      case PAGE_ID_VOICE_COMMANDS:
        return PageConfig.builder(PAGE_ID_VOICE_COMMANDS, R.string.voice_commands_title)
            .addText(R.string.voice_commands_text);
      case PAGE_ID_PRACTICE_GESTURES:
        return PageConfig.builder(
                PageId.PAGE_ID_PRACTICE_GESTURES, R.string.practice_gestures_title)
            .addText(R.string.practice_gestures_text)
            .captureAllGestures();
      case PAGE_ID_PRACTICE_GESTURES_PRE_R:
        return PageConfig.builder(PAGE_ID_PRACTICE_GESTURES_PRE_R, R.string.practice_gestures_title)
            .addText(R.string.practice_gestures_text_pre_r)
            .captureAllGestures();
      case PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS:
        return PageConfig.builder(PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS, R.string.making_calls_title)
            .addText(R.string.making_calls_text)
            .addTip(R.string.making_calls_tips);
      case PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES:
        return PageConfig.builder(
                PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES, R.string.sending_messages_title)
            .addText(R.string.sending_messages_text)
            .addTip(R.string.sending_message_tips);
      case PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS:
        return PageConfig.builder(
                PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS, R.string.reading_web_emails_title)
            .addText(R.string.reading_web_emails_text)
            .addButton(R.string.get_the_app_button, OPEN_READING_MODE_PAGE)
            .addTipWithTtsSpan(
                R.string.reading_web_emails_tips, R.string.reading_web_emails_tips_tts);
      case PAGE_ID_ADDITIONAL_TIPS_LOOKOUT:
        return PageConfig.builder(PAGE_ID_ADDITIONAL_TIPS_LOOKOUT, R.string.lookout_title)
            .addText(R.string.lookout_text)
            .addButton(R.string.get_the_app_button, OPEN_LOOKOUT_PAGE)
            .addTip(R.string.lookout_tips);
      case PAGE_ID_ADDITIONAL_TIPS_CHECKING_NOTIFICATIONS:
        return PageConfig.builder(
                PAGE_ID_ADDITIONAL_TIPS_CHECKING_NOTIFICATIONS,
                R.string.checking_notifications_title)
            .addText(R.string.checking_notifications_text_head)
            .addTextWithBullet(R.string.checking_notifications_step_one)
            .addTextWithBullet(R.string.checking_notifications_step_two, /* subText= */ true)
            .addTextWithTtsSpan(
                R.string.checking_notifications_text_tail,
                R.string.checking_notifications_text_tail_tts)
            .addTip(R.string.checking_notifications_tips);
      default:
        LogUtils.w(TAG, "No matched: " + pageId);
        return null;
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // First-run Training

  static TrainingConfig getFirstRunTutorial(Context context) {
    boolean gestureNavigation = FeatureSupport.isGestureNavigateEnabled(context);
    boolean multiFingerGestureSupported = FeatureSupport.isMultiFingerGestureSupported();
    boolean duringSetupWizard = !SettingsUtils.allowLinksOutOfSettings(context);
    List<PageConfig.Builder> pages = new ArrayList<>();
    pages.add(getInitialPageBuilder(PAGE_ID_WELCOME_TO_TALKBACK));
    pages.add(getInitialPageBuilder(PAGE_ID_EXPLORE_BY_TOUCH));
    pages.add(getInitialPageBuilder(PAGE_ID_SCROLLING));
    if (!duringSetupWizard) {
      pages.add(
          gestureNavigation
              ? getInitialPageBuilder(PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER)
              : getInitialPageBuilder(PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER));
    }
    pages.add(getInitialPageBuilder(PAGE_ID_ADJUSTING_VOLUME));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_MENUS)
            : getInitialPageBuilder(PAGE_ID_MENUS_PRE_R));
    TrainingConfig.Builder builder =
        TrainingConfig.builder(R.string.welcome_to_talkback_title)
            .addPages(pages.toArray(new PageConfig.Builder[0]))
            .addPage(
                getInitialPageBuilder(PAGE_ID_TUTORIAL_FINISHED),
                /* hasNavigationButtonBar= */ true,
                /* showPageNumber= */ false,
                /* isEndOfSection= */ true);
    // Text editing pages
    pages.clear();
    pages.add(getInitialPageBuilder(PAGE_ID_USING_TEXT_BOXES));
    pages.add(getInitialPageBuilder(PAGE_ID_TYPING_TEXT));
    pages.add(getInitialPageBuilder(PAGE_ID_MOVING_CURSOR));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_SELECTING_TEXT)
            : getInitialPageBuilder(PAGE_ID_SELECTING_TEXT_PRE_R));
    builder
        .addPages(pages.toArray(new PageConfig.Builder[0]))
        .addPageEndOfSection(
            multiFingerGestureSupported
                ? getInitialPageBuilder(PAGE_ID_COPY_CUT_PASTE)
                : getInitialPageBuilder(PAGE_ID_COPY_CUT_PASTE_PRE_R));
    // Reading navigation pages.
    pages.clear();
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_READ_BY_CHARACTER)
            : getInitialPageBuilder(PAGE_ID_READ_BY_CHARACTER_PRE_R));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_CONTROLS)
            : getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_LINKS)
            : getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R));
    builder
        .addPages(pages.toArray(new PageConfig.Builder[0]))
        .addPageEndOfSection(
            multiFingerGestureSupported
                ? getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_HEADINGS)
                : getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R));
    // Voice commands page.
    builder.addPageEndOfSection(getInitialPageBuilder(PAGE_ID_VOICE_COMMANDS));
    // Additional Tips
    builder.addPages(
        getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS),
        getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES),
        getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS));
    if (multiFingerGestureSupported) {
      builder
          .addPages(getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_LOOKOUT))
          .addPageEndOfSection(
              getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_CHECKING_NOTIFICATIONS));
    } else {
      builder.addPageEndOfSection(getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_LOOKOUT));
    }
    // Practice gestures page.
    builder
        .addPageEndOfSection(
            multiFingerGestureSupported
                ? getInitialPageBuilder(PAGE_ID_PRACTICE_GESTURES)
                : getInitialPageBuilder(PAGE_ID_PRACTICE_GESTURES_PRE_R))
        .setButtons(DEFAULT_BUTTONS);

    return builder.build();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Training in Settings

  static TrainingConfig getTutorialInSettings(Context context) {
    boolean gestureNavigation = FeatureSupport.isGestureNavigateEnabled(context);
    boolean multiFingerGestureSupported = FeatureSupport.isMultiFingerGestureSupported();
    TrainingConfig.Builder builder =
        TrainingConfig.builder(R.string.talkback_tutorial_title)
            .addPageWithoutNumberAndNavigationBar(getInitialPageBuilder(PAGE_ID_TUTORIAL_INDEX));
    List<PageConfig.Builder> pages = new ArrayList<>();
    // Basic navigation pages
    pages.add(getInitialPageBuilder(PAGE_ID_WELCOME_TO_TALKBACK));
    pages.add(getInitialPageBuilder(PAGE_ID_EXPLORE_BY_TOUCH));
    pages.add(getInitialPageBuilder(PAGE_ID_SCROLLING));
    pages.add(
        gestureNavigation
            ? getInitialPageBuilder(PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER)
            : getInitialPageBuilder(PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER));
    pages.add(getInitialPageBuilder(PAGE_ID_ADJUSTING_VOLUME));
    builder
        .addPages(pages.toArray(new PageConfig.Builder[0]))
        .addPageEndOfSection(
            multiFingerGestureSupported
                ? getInitialPageBuilder(PAGE_ID_MENUS)
                : getInitialPageBuilder(PAGE_ID_MENUS_PRE_R));
    // Text editing pages
    pages.clear();
    pages.add(getInitialPageBuilder(PAGE_ID_USING_TEXT_BOXES));
    pages.add(getInitialPageBuilder(PAGE_ID_TYPING_TEXT));
    pages.add(getInitialPageBuilder(PAGE_ID_MOVING_CURSOR));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_SELECTING_TEXT)
            : getInitialPageBuilder(PAGE_ID_SELECTING_TEXT_PRE_R));
    Optional<PageId> spellCheckPageId = getCorrectSpellCheckTutorialPageId(context);
    PageConfig.Builder copyCutPastePage =
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_COPY_CUT_PASTE)
            : getInitialPageBuilder(PAGE_ID_COPY_CUT_PASTE_PRE_R);
    if (spellCheckPageId.isPresent()) {
      pages.add(copyCutPastePage);
      builder
          .addPages(pages.toArray(new PageConfig.Builder[0]))
          .addPageEndOfSection(getInitialPageBuilder(spellCheckPageId.get()));
    } else {
      builder
          .addPages(pages.toArray(new PageConfig.Builder[0]))
          .addPageEndOfSection(copyCutPastePage);
    }
    // Reading navigation pages
    pages.clear();
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_READ_BY_CHARACTER)
            : getInitialPageBuilder(PAGE_ID_READ_BY_CHARACTER_PRE_R));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_CONTROLS)
            : getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R));
    pages.add(
        multiFingerGestureSupported
            ? getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_LINKS)
            : getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R));
    builder
        .addPages(pages.toArray(new PageConfig.Builder[0]))
        .addPageEndOfSection(
            multiFingerGestureSupported
                ? getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_HEADINGS)
                : getInitialPageBuilder(PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R));
    // Voice commands page.
    builder.addPageEndOfSection(getInitialPageBuilder(PAGE_ID_VOICE_COMMANDS));
    // Additional Tips
    builder.addPages(
        getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS),
        getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES),
        getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS));
    if (multiFingerGestureSupported) {
      builder
          .addPages(getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_LOOKOUT))
          .addPageEndOfSection(
              getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_CHECKING_NOTIFICATIONS));
    } else {
      builder.addPageEndOfSection(getInitialPageBuilder(PAGE_ID_ADDITIONAL_TIPS_LOOKOUT));
    }
    // Practice gestures page.
    builder
        .addPageEndOfSection(
            multiFingerGestureSupported
                ? getInitialPageBuilder(PAGE_ID_PRACTICE_GESTURES)
                : getInitialPageBuilder(PAGE_ID_PRACTICE_GESTURES_PRE_R))
        .setButtons(DEFAULT_BUTTONS)
        .setSupportNavigateUpArrow(true)
        .setExitButtonOnlyShowOnLastPage(true);

    return builder.build();
  }

  static final TrainingConfig TUTORIAL_PRACTICE_GESTURE =
      TrainingConfig.builder(R.string.practice_gestures_title)
          .addPages(getInitialPageBuilder(PAGE_ID_PRACTICE_GESTURES))
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig TUTORIAL_PRACTICE_GESTURE_PRE_R =
      TrainingConfig.builder(R.string.practice_gestures_title)
          .addPages(getInitialPageBuilder(PAGE_ID_PRACTICE_GESTURES_PRE_R))
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static TrainingConfig getSpellCheckTutorial(Context context) {
    return TrainingConfig.builder(R.string.typo_correction_title)
        .addPageWithoutNumberAndNavigationBar(
            getInitialPageBuilder(getCorrectSpellCheckTutorialPageId(context).get()))
        .setButtons(DEFAULT_BUTTONS)
        .setSupportNavigateUpArrow(true)
        .build();
  }

  static boolean isSpellCheckSupported(Context context) {
    return getCorrectSpellCheckTutorialPageId(context).isPresent();
  }

  private static Optional<PageId> getCorrectSpellCheckTutorialPageId(Context context) {
    boolean multiFingerGestureSupported = FeatureSupport.isMultiFingerGestureSupported();
    Locale systemLocale = Locale.getDefault();
    if (systemLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
      return Optional.of(
          multiFingerGestureSupported ? PAGE_ID_TYPO_CORRECTION : PAGE_ID_TYPO_CORRECTION_PRE_R);
    } else if (isSpellCheckerSupported(context, systemLocale)) {
      return Optional.of(
          multiFingerGestureSupported
              ? PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH
              : PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH_PRE_R);
    }
    return Optional.empty();
  }

  private static boolean isSpellCheckerSupported(Context context, Locale locale) {
    // API only supports Android S or above.
    if (!BuildVersionUtils.isAtLeastS()) {
      return false;
    }
    TextServicesManager textServicesManager =
        (TextServicesManager) context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
    if (textServicesManager == null) {
      return false;
    }
    SpellCheckerInfo spellCheckerInfo = textServicesManager.getCurrentSpellCheckerInfo();
    if (spellCheckerInfo == null) {
      return false;
    }
    String localeInSpellCheckerLocaleFormat = toSpellCheckerLocale(locale);
    for (int i = 0; i < spellCheckerInfo.getSubtypeCount(); i++) {
      String subTypeLocale = spellCheckerInfo.getSubtypeAt(i).getLocale();
      if (localeInSpellCheckerLocaleFormat.equals(subTypeLocale)) {
        return true;
      }
      String localeLanguage = locale.getLanguage();
      if (!TextUtils.isEmpty(localeLanguage) && localeLanguage.equals(subTypeLocale)) {
        // Language code matched is the basement candidate.
        return true;
      }
    }
    return false;
  }

  private static String toSpellCheckerLocale(Locale locale) {
    // SpellCheckerInfo use '-' to be delimiter instead of Locale's '_';
    char delimiter = '_';
    if (TextUtils.isEmpty(locale.getLanguage())) {
      return "";
    } else if (TextUtils.isEmpty(locale.getCountry())) {
      return locale.getLanguage();
    }
    return locale.getLanguage() + delimiter + locale.getCountry();
  }
}
