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
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate.ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate.SUPPORT_EXIT_BANNER;
import static com.google.android.accessibility.talkback.trainingcommon.content.PageButton.PageButtonAction.OPEN_LOOKOUT_PAGE;
import static com.google.android.accessibility.talkback.trainingcommon.content.PageButton.PageButtonAction.OPEN_READING_MODE_PAGE;

import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.common.collect.ImmutableList;

final class TutorialConfigs {

  private TutorialConfigs() {}

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages

  static final PageConfig.Builder WELCOME_TO_TALKBACK_PAGE =
      PageConfig.builder(PageId.PAGE_ID_WELCOME_TO_TALKBACK, R.string.welcome_to_talkback_title)
          .setOnlyOneFocus(true)
          .addExitBanner(SUPPORT_EXIT_BANNER)
          .addText(R.string.welcome_to_talkback_text);

  static final PageConfig.Builder EXPLORE_BY_TOUCH_PAGE =
      PageConfig.builder(PageId.PAGE_ID_EXPLORE_BY_TOUCH, R.string.explore_by_touch_title)
          .addText(R.string.explore_by_touch_text);

  static final PageConfig.Builder SCROLLING_PAGE =
      PageConfig.builder(PageId.PAGE_ID_SCROLLING, R.string.scrolling_title)
          .addText(R.string.scrolling_text)
          .addList(R.array.tutorial_scrolling_items);

  static final PageConfig.Builder GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER =
      PageConfig.builder(
              PageId.PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER, R.string.gestures_title)
          .addText(R.string.gestures_text)
          .addTextWithIcon(R.string.gestures_home_text, R.drawable.ic_gesture_2fingeredgeup)
          .addTextWithIcon(
              R.string.gestures_overview_screen_text, R.drawable.ic_gesture_2fingeredgeuphold)
          .addTextWithIcon(R.string.gestures_back_text, R.drawable.ic_gesture_2fingerinward)
          .addTextWithIcon(
              R.string.gestures_open_notifications_text_for_gesture_navigation,
              R.drawable.ic_gesture_2fingeredgedown)
          .addTextWithIcon(
              R.string.gestures_accessibility_shortcut_text,
              R.drawable.ic_gesture_3fingeredgeup,
              ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT);

  static final PageConfig.Builder GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER =
      PageConfig.builder(
              PageId.PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER, R.string.gestures_title)
          .addTextWithIcon(
              R.string.gestures_open_notifications_text_for_3_button_navigation,
              R.drawable.ic_gesture_2fingeredgedown);

  static final PageConfig.Builder MENUS_PAGE =
      PageConfig.builder(PageId.PAGE_ID_MENUS, R.string.menus_title)
          .addHeading(R.string.talkback_menu_title)
          .addText(R.string.menus_talkback_menu_text)
          .addHeading(R.string.setting_selector_heading)
          .addText(R.string.menus_selector_text);

  static final PageConfig.Builder MENUS_PAGE_PRE_R =
      PageConfig.builder(PageId.PAGE_ID_MENUS_PRE_R, R.string.menus_title)
          .addHeading(R.string.talkback_menu_title)
          .addText(R.string.menus_talkback_menu_text_pre_r)
          .addHeading(R.string.setting_selector_heading)
          .addText(R.string.menus_selector_text_pre_r);

  static final PageConfig.Builder TUTORIAL_FINISHED_PAGE =
      PageConfig.builder(PageId.PAGE_ID_TUTORIAL_FINISHED, R.string.all_set_title)
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
              R.string.practice_gestures_link_text,
              R.string.practice_gestures_link_subtext,
              R.drawable.ic_gesture_googblue_24dp,
              R.string.practice_gestures_title);

  static final PageConfig.Builder TUTORIAL_INDEX_PAGE =
      PageConfig.builder(PageId.PAGE_ID_TUTORIAL_INDEX, R.string.talkback_tutorial_title)
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
          .addLink(
              R.string.practice_gestures_link_text,
              R.string.practice_gestures_link_subtext,
              R.drawable.ic_gesture_googblue_24dp,
              R.string.practice_gestures_title);

  static final PageConfig.Builder USING_TEXT_BOXES_PAGE =
      PageConfig.builder(PageId.PAGE_ID_USING_TEXT_BOXES, R.string.using_text_boxes_title)
          .addText(R.string.using_text_boxes_text)
          .addEditTextWithHint(R.string.edit_box_hint);

  static final PageConfig.Builder TYPING_TEXT_PAGE =
      PageConfig.builder(PageId.PAGE_ID_TYPING_TEXT, R.string.typing_text_title)
          .addText(R.string.typing_text_text)
          .addEditTextWithHint(R.string.enter_text_here)
          .addText(R.string.typing_text_with_braille_keyboard_text);

  static final PageConfig.Builder MOVING_CURSOR_PAGE =
      PageConfig.builder(PageId.PAGE_ID_MOVING_CURSOR, R.string.moving_cursor_title)
          .addText(R.string.moving_cursor_text)
          .addEditTextWithContent(R.string.edit_box_text);

  static final PageConfig.Builder SELECTING_TEXT_PAGE =
      PageConfig.builder(PageId.PAGE_ID_SELECTING_TEXT, R.string.selecting_text_title)
          .addText(R.string.selecting_text_text)
          .addEditTextWithContent(R.string.edit_box_text);

  static final PageConfig.Builder SELECTING_TEXT_PAGE_PRE_R =
      PageConfig.builder(PageId.PAGE_ID_SELECTING_TEXT_PRE_R, R.string.selecting_text_title)
          .addText(R.string.selecting_text_text_pre_r)
          .addEditTextWithContent(R.string.edit_box_text);

  // TODO Provides a selecting text page for the devices without multiple finger
  // gestures
  static final PageConfig.Builder COPY_CUT_PASTE_PAGE =
      PageConfig.builder(PageId.PAGE_ID_COPY_CUT_PASTE, R.string.copy_cut_paste_title)
          .addText(R.string.copy_text)
          .addEditTextWithContent(R.string.edit_box_text)
          .addText(R.string.cut_paste_text)
          .addEditTextWithHint(R.string.edit_box_hint_paste_text);

  static final PageConfig.Builder COPY_CUT_PASTE_PAGE_PRE_R =
      PageConfig.builder(PageId.PAGE_ID_COPY_CUT_PASTE_PRE_R, R.string.copy_cut_paste_title)
          .addText(R.string.copy_text_pre_r)
          .addEditTextWithContent(R.string.edit_box_text)
          .addText(R.string.cut_paste_text_pre_r)
          .addEditTextWithHint(R.string.edit_box_hint_paste_text);

  static final PageConfig.Builder READ_BY_CHARACTER =
      PageConfig.builder(PageId.PAGE_ID_READ_BY_CHARACTER, R.string.read_by_character_title)
          .addText(
              R.string.read_by_character_text, ImmutableList.of(R.string.granularity_character));

  static final PageConfig.Builder READ_BY_CHARACTER_PRE_R =
      PageConfig.builder(PageId.PAGE_ID_READ_BY_CHARACTER_PRE_R, R.string.read_by_character_title)
          .addText(
              R.string.read_by_character_text_pre_r,
              ImmutableList.of(R.string.granularity_character));

  static final PageConfig.Builder JUMP_BETWEEN_CONTROLS =
      PageConfig.builder(PageId.PAGE_ID_JUMP_BETWEEN_CONTROLS, R.string.jump_between_controls_title)
          .addText(
              R.string.jump_between_controls_text,
              ImmutableList.of(R.string.granularity_native_control))
          .addButton(R.string.button_1)
          .addButton(R.string.button_2)
          .addButton(R.string.button_3)
          .addButton(R.string.button_4);

  static final PageConfig.Builder JUMP_BETWEEN_CONTROLS_PRE_R =
      PageConfig.builder(
              PageId.PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R, R.string.jump_between_controls_title)
          .addText(
              R.string.jump_between_controls_text_pre_r,
              ImmutableList.of(R.string.granularity_native_control))
          .addButton(R.string.button_1)
          .addButton(R.string.button_2)
          .addButton(R.string.button_3)
          .addButton(R.string.button_4);

  static final PageConfig.Builder JUMP_BETWEEN_LINKS =
      PageConfig.builder(PageId.PAGE_ID_JUMP_BETWEEN_LINKS, R.string.jump_between_links_title)
          .addText(
              R.string.jump_between_links_text, ImmutableList.of(R.string.granularity_native_link))
          .addText(R.string.paragraph1_text)
          .addTextWithLink(R.string.link1_text)
          .addText(R.string.paragraph2_text)
          .addTextWithLink(R.string.link2_text)
          .addText(R.string.paragraph3_text)
          .addTextWithLink(R.string.link3_text)
          .addText(R.string.paragraph4_text)
          .addTextWithLink(R.string.link4_text)
          .addTextWithLink(R.string.target_link_text);

  static final PageConfig.Builder JUMP_BETWEEN_LINKS_PRE_R =
      PageConfig.builder(PageId.PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R, R.string.jump_between_links_title)
          .addText(
              R.string.jump_between_links_text_pre_r,
              ImmutableList.of(R.string.granularity_native_link))
          .addText(R.string.paragraph1_text)
          .addTextWithLink(R.string.link1_text)
          .addText(R.string.paragraph2_text)
          .addTextWithLink(R.string.link2_text)
          .addText(R.string.paragraph3_text)
          .addTextWithLink(R.string.link3_text)
          .addText(R.string.paragraph4_text)
          .addTextWithLink(R.string.link4_text)
          .addTextWithLink(R.string.target_link_text);

  static final PageConfig.Builder JUMP_BETWEEN_HEADINGS =
      PageConfig.builder(PageId.PAGE_ID_JUMP_BETWEEN_HEADINGS, R.string.jump_between_headings_title)
          .addText(
              R.string.jump_between_headings_text,
              ImmutableList.of(R.string.granularity_native_heading))
          .addDivider()
          .addHeading(R.string.content_heading)
          .addText(R.string.content_text)
          .addDivider()
          .addHeading(R.string.navigation_heading)
          .addText(R.string.find_finish_button_text);

  static final PageConfig.Builder JUMP_BETWEEN_HEADINGS_PRE_R =
      PageConfig.builder(
              PageId.PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R, R.string.jump_between_headings_title)
          .addText(
              R.string.jump_between_headings_text_pre_r,
              ImmutableList.of(R.string.granularity_native_heading))
          .addDivider()
          .addHeading(R.string.content_heading)
          .addText(R.string.content_text)
          .addDivider()
          .addHeading(R.string.navigation_heading)
          .addText(R.string.find_finish_button_text);

  static final PageConfig.Builder VOICE_COMMANDS =
      PageConfig.builder(PageId.PAGE_ID_VOICE_COMMANDS, R.string.voice_commands_title)
          .addText(R.string.voice_commands_text);

  static final PageConfig.Builder PRACTICE_GESTURES =
      PageConfig.builder(PageId.PAGE_ID_PRACTICE_GESTURES, R.string.practice_gestures_title)
          .addText(R.string.practice_gestures_text)
          .captureAllGestures();

  static final PageConfig.Builder PRACTICE_GESTURES_PRE_R =
      PageConfig.builder(PageId.PAGE_ID_PRACTICE_GESTURES_PRE_R, R.string.practice_gestures_title)
          .addText(R.string.practice_gestures_text_pre_r)
          .captureAllGestures();

  static final PageConfig.Builder MAKING_CALLS =
      PageConfig.builder(PageId.PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS, R.string.making_calls_title)
          .addText(R.string.making_calls_text)
          .addTip(R.string.making_calls_tips);

  static final PageConfig.Builder SENDING_MESSAGES =
      PageConfig.builder(
              PageId.PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES, R.string.sending_messages_title)
          .addText(R.string.sending_messages_text)
          .addTip(R.string.sending_message_tips);

  static final PageConfig.Builder READING_WEB_EMAILS =
      PageConfig.builder(
              PageId.PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS, R.string.reading_web_emails_title)
          .addText(R.string.reading_web_emails_text)
          .addButton(R.string.get_the_app_button, OPEN_READING_MODE_PAGE)
          .addTip(R.string.reading_web_emails_tips);

  static final PageConfig.Builder LOOKOUT =
      PageConfig.builder(PageId.PAGE_ID_ADDITIONAL_TIPS_LOOKOUT, R.string.lookout_title)
          .addText(R.string.lookout_text)
          .addButton(R.string.get_the_app_button, OPEN_LOOKOUT_PAGE)
          .addTip(R.string.lookout_tips);

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // First-run Training

  static final TrainingConfig FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER =
      TrainingConfig.builder(R.string.welcome_to_talkback_title)
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER,
              MENUS_PAGE)
          .addPage(
              TUTORIAL_FINISHED_PAGE,
              /* hasNavigationButtonBar= */ true,
              /* showPageNumber= */ false,
              /* isEndOfSection= */ true)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE, TYPING_TEXT_PAGE, MOVING_CURSOR_PAGE, SELECTING_TEXT_PAGE)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER, JUMP_BETWEEN_CONTROLS, JUMP_BETWEEN_LINKS)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES)
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R =
      TrainingConfig.builder(R.string.welcome_to_talkback_title)
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER,
              MENUS_PAGE_PRE_R)
          .addPage(
              TUTORIAL_FINISHED_PAGE,
              /* hasNavigationButtonBar= */ true,
              /* showPageNumber= */ false,
              /* isEndOfSection= */ true)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE,
              TYPING_TEXT_PAGE,
              MOVING_CURSOR_PAGE,
              SELECTING_TEXT_PAGE_PRE_R)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE_PRE_R)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER_PRE_R, JUMP_BETWEEN_CONTROLS_PRE_R, JUMP_BETWEEN_LINKS_PRE_R)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS_PRE_R)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES_PRE_R)
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER =
      TrainingConfig.builder(R.string.welcome_to_talkback_title)
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER,
              MENUS_PAGE)
          .addPage(
              TUTORIAL_FINISHED_PAGE,
              /* hasNavigationButtonBar= */ true,
              /* showPageNumber= */ false,
              /* isEndOfSection= */ true)

          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE, TYPING_TEXT_PAGE, MOVING_CURSOR_PAGE, SELECTING_TEXT_PAGE)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER, JUMP_BETWEEN_CONTROLS, JUMP_BETWEEN_LINKS)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES)
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R =
      TrainingConfig.builder(R.string.welcome_to_talkback_title)
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER,
              MENUS_PAGE_PRE_R)
          .addPage(
              TUTORIAL_FINISHED_PAGE,
              /* hasNavigationButtonBar= */ true,
              /* showPageNumber= */ false,
              /* isEndOfSection= */ true)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE,
              TYPING_TEXT_PAGE,
              MOVING_CURSOR_PAGE,
              SELECTING_TEXT_PAGE_PRE_R)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE_PRE_R)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER_PRE_R, JUMP_BETWEEN_CONTROLS_PRE_R, JUMP_BETWEEN_LINKS_PRE_R)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS_PRE_R)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES_PRE_R)
          .setButtons(DEFAULT_BUTTONS)
          .build();

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Training in Settings

  static final TrainingConfig TUTORIAL_FOR_GESTURE_NAVIGATION_USER =
      TrainingConfig.builder(R.string.talkback_tutorial_title)
          .addPageWithoutNumberAndNavigationBar(TUTORIAL_INDEX_PAGE)
          // Basic navigation pages
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER)
          .addPageEndOfSection(MENUS_PAGE)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE, TYPING_TEXT_PAGE, MOVING_CURSOR_PAGE, SELECTING_TEXT_PAGE)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER, JUMP_BETWEEN_CONTROLS, JUMP_BETWEEN_LINKS)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Additional Tips
          .addPages(MAKING_CALLS, SENDING_MESSAGES, READING_WEB_EMAILS)
          .addPageEndOfSection(LOOKOUT)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES)
          .setButtons(DEFAULT_BUTTONS)
          .setSupportNavigateUpArrow(true)
          .setExitButtonOnlyShowOnLastPage(true)
          .build();

  static final TrainingConfig TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R =
      TrainingConfig.builder(R.string.talkback_tutorial_title)
          .addPageWithoutNumberAndNavigationBar(TUTORIAL_INDEX_PAGE)
          // Basic navigation pages
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER)
          .addPageEndOfSection(MENUS_PAGE_PRE_R)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE,
              TYPING_TEXT_PAGE,
              MOVING_CURSOR_PAGE,
              SELECTING_TEXT_PAGE_PRE_R)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE_PRE_R)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER_PRE_R, JUMP_BETWEEN_CONTROLS_PRE_R, JUMP_BETWEEN_LINKS_PRE_R)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS_PRE_R)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Additional Tips
          .addPages(MAKING_CALLS, SENDING_MESSAGES, READING_WEB_EMAILS)
          .addPageEndOfSection(LOOKOUT)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES_PRE_R)
          .setButtons(DEFAULT_BUTTONS)
          .setSupportNavigateUpArrow(true)
          .setExitButtonOnlyShowOnLastPage(true)
          .build();

  static final TrainingConfig TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER =
      TrainingConfig.builder(R.string.talkback_tutorial_title)
          .addPageWithoutNumberAndNavigationBar(TUTORIAL_INDEX_PAGE)
          // Basic navigation pages
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER)
          .addPageEndOfSection(MENUS_PAGE)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE, TYPING_TEXT_PAGE, MOVING_CURSOR_PAGE, SELECTING_TEXT_PAGE)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER, JUMP_BETWEEN_CONTROLS, JUMP_BETWEEN_LINKS)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Additional Tips
          .addPages(MAKING_CALLS, SENDING_MESSAGES, READING_WEB_EMAILS)
          .addPageEndOfSection(LOOKOUT)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES)
          .setButtons(DEFAULT_BUTTONS)
          .setSupportNavigateUpArrow(true)
          .setExitButtonOnlyShowOnLastPage(true)
          .build();

  static final TrainingConfig TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R =
      TrainingConfig.builder(R.string.talkback_tutorial_title)
          .addPageWithoutNumberAndNavigationBar(TUTORIAL_INDEX_PAGE)
          // Basic navigation pages
          .addPages(
              WELCOME_TO_TALKBACK_PAGE,
              EXPLORE_BY_TOUCH_PAGE,
              SCROLLING_PAGE,
              GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER)
          .addPageEndOfSection(MENUS_PAGE_PRE_R)
          // Text editing pages
          .addPages(
              USING_TEXT_BOXES_PAGE,
              TYPING_TEXT_PAGE,
              MOVING_CURSOR_PAGE,
              SELECTING_TEXT_PAGE_PRE_R)
          .addPageEndOfSection(COPY_CUT_PASTE_PAGE_PRE_R)
          // Reading navigation pages.
          .addPages(READ_BY_CHARACTER_PRE_R, JUMP_BETWEEN_CONTROLS_PRE_R, JUMP_BETWEEN_LINKS_PRE_R)
          .addPageEndOfSection(JUMP_BETWEEN_HEADINGS_PRE_R)
          // Voice commands page.
          .addPageEndOfSection(VOICE_COMMANDS)
          // Additional Tips
          .addPages(MAKING_CALLS, SENDING_MESSAGES, READING_WEB_EMAILS)
          .addPageEndOfSection(LOOKOUT)
          // Practice gestures page.
          .addPageEndOfSection(PRACTICE_GESTURES_PRE_R)
          .setButtons(DEFAULT_BUTTONS)
          .setSupportNavigateUpArrow(true)
          .setExitButtonOnlyShowOnLastPage(true)
          .build();

  static final TrainingConfig TUTORIAL_PRACTICE_GESTURE =
      TrainingConfig.builder(R.string.practice_gestures_title)
          .addPages(PRACTICE_GESTURES)
          .setButtons(DEFAULT_BUTTONS)
          .build();

  static final TrainingConfig TUTORIAL_PRACTICE_GESTURE_PRE_R =
      TrainingConfig.builder(R.string.practice_gestures_title)
          .addPages(PRACTICE_GESTURES_PRE_R)
          .setButtons(DEFAULT_BUTTONS)
          .build();
}
