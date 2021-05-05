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

import static com.google.android.accessibility.talkback.training.NavigationButtonBar.DEFAULT_BUTTONS;
import static com.google.android.accessibility.talkback.training.PageConfig.PageContentPredicate.ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT;
import static com.google.android.accessibility.talkback.training.content.PageContentConfig.UNKNOWN_RESOURCE_ID;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;

/** Starts a {@link TrainingActivity} to show tutorial. */
public class TutorialInitiator {

  private static final int NAV_BAR_MODE_GESTURAL = 2;

  /** Returns an intent to start tutorial for the first run users. */
  public static Intent createFirstRunTutorialIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        isGestureNavigateEnabled(context)
            ? (FeatureSupport.isMultiFingerGestureSupported()
                ? FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER
                : FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R)
            : (FeatureSupport.isMultiFingerGestureSupported()
                ? FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER
                : FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R));
  }

  /** Returns an intent to start tutorial. */
  public static Intent createTutorialIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        isGestureNavigateEnabled(context)
            ? (FeatureSupport.isMultiFingerGestureSupported()
                ? TUTORIAL_FOR_GESTURE_NAVIGATION_USER
                : TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R)
            : (FeatureSupport.isMultiFingerGestureSupported()
                ? TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER
                : TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R));
  }

  public static Intent createPracticeGesturesIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        FeatureSupport.isMultiFingerGestureSupported()
            ? TUTORIAL_PRACTICE_GESTURE
            : TUTORIAL_PRACTICE_GESTURE_PRE_R);
  }

  private static boolean isGestureNavigateEnabled(Context context) {
    Resources resources = context.getResources();
    int resourceId = resources.getIdentifier("config_navBarInteractionMode", "integer", "android");
    if (resourceId > 0) {
      return resources.getInteger(resourceId) == NAV_BAR_MODE_GESTURAL;
    }
    // Device doesn't support gesture navigation.
    return false;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages

  private static final PageConfig.Builder WELCOME_TO_TALKBACK_PAGE =
      PageConfig.builder(R.string.welcome_to_talkback_title)
          .setOnlyOneFocus(true)
          .addText(R.string.welcome_to_talkback_text);

  private static final PageConfig.Builder EXPLORE_BY_TOUCH_PAGE =
      PageConfig.builder(R.string.explore_by_touch_title).addText(R.string.explore_by_touch_text);

  private static final PageConfig.Builder SCROLLING_PAGE =
      PageConfig.builder(R.string.scrolling_title)
          .addText(R.string.scrolling_text)
          .addList(R.array.tutorial_scrolling_items);

  private static final PageConfig.Builder GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER =
      PageConfig.builder(R.string.gestures_title)
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

  private static final PageConfig.Builder GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER =
      PageConfig.builder(R.string.gestures_title)
          .addTextWithIcon(
              R.string.gestures_open_notifications_text_for_3_button_navigation,
              R.drawable.ic_gesture_2fingeredgedown);

  private static final PageConfig.Builder MENUS_PAGE =
      PageConfig.builder(R.string.menus_title)
          .addHeading(R.string.talkback_menu_title)
          .addText(R.string.menus_talkback_menu_text)
          .addHeading(R.string.setting_selector_heading)
          .addText(R.string.menus_selector_text);

  private static final PageConfig.Builder MENUS_PAGE_PRE_R =
      PageConfig.builder(R.string.menus_title)
          .addHeading(R.string.talkback_menu_title)
          .addText(R.string.menus_talkback_menu_text_pre_r)
          .addHeading(R.string.setting_selector_heading)
          .addText(R.string.menus_selector_text_pre_r);

  private static final PageConfig.Builder TUTORIAL_FINISHED_PAGE =
      PageConfig.builder(R.string.all_set_title)
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

  private static final PageConfig.Builder TUTORIAL_INDEX_PAGE =
      PageConfig.builder(R.string.talkback_tutorial_title)
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
              R.string.practice_gestures_link_text,
              R.string.practice_gestures_link_subtext,
              R.drawable.ic_gesture_googblue_24dp,
              R.string.practice_gestures_title);

  private static final PageConfig.Builder USING_TEXT_BOXES_PAGE =
      PageConfig.builder(R.string.using_text_boxes_title)
          .addText(R.string.using_text_boxes_text)
          .addEditTextWithHint(R.string.edit_box_hint);

  private static final PageConfig.Builder TYPING_TEXT_PAGE =
      PageConfig.builder(R.string.typing_text_title)
          .addText(R.string.typing_text_text)
          .addEditTextWithHint(R.string.enter_text_here)
          .addText(R.string.typing_text_with_braille_keyboard_text);

  private static final PageConfig.Builder MOVING_CURSOR_PAGE =
      PageConfig.builder(R.string.moving_cursor_title)
          .addText(R.string.moving_cursor_text)
          .addEditTextWithContent(R.string.edit_box_text);

  private static final PageConfig.Builder SELECTING_TEXT_PAGE =
      PageConfig.builder(R.string.selecting_text_title)
          .addText(R.string.selecting_text_text)
          .addEditTextWithContent(R.string.edit_box_text);

  private static final PageConfig.Builder SELECTING_TEXT_PAGE_PRE_R =
      PageConfig.builder(R.string.selecting_text_title)
          .addText(R.string.selecting_text_text_pre_r)
          .addEditTextWithContent(R.string.edit_box_text);

  // TODO Provides a selecting text page for the devices without multiple finger
  // gestures
  private static final PageConfig.Builder COPY_CUT_PASTE_PAGE =
      PageConfig.builder(R.string.copy_cut_paste_title)
          .addText(R.string.copy_text)
          .addEditTextWithContent(R.string.edit_box_text)
          .addText(R.string.cut_paste_text)
          .addEditTextWithContent(UNKNOWN_RESOURCE_ID);

  private static final PageConfig.Builder COPY_CUT_PASTE_PAGE_PRE_R =
      PageConfig.builder(R.string.copy_cut_paste_title)
          .addText(R.string.copy_text_pre_r)
          .addEditTextWithContent(R.string.edit_box_text)
          .addText(R.string.cut_paste_text_pre_r)
          .addEditTextWithContent(UNKNOWN_RESOURCE_ID);

  private static final PageConfig.Builder READ_BY_CHARACTER =
      PageConfig.builder(R.string.read_by_character_title)
          .addText(R.string.read_by_character_text, R.string.granularity_character);

  private static final PageConfig.Builder READ_BY_CHARACTER_PRE_R =
      PageConfig.builder(R.string.read_by_character_title)
          .addText(R.string.read_by_character_text_pre_r, R.string.granularity_character);

  private static final PageConfig.Builder JUMP_BETWEEN_CONTROLS =
      PageConfig.builder(R.string.jump_between_controls_title)
          .addText(R.string.jump_between_controls_text, R.string.granularity_native_control)
          .addButton(R.string.button_1)
          .addButton(R.string.button_2)
          .addButton(R.string.button_3)
          .addButton(R.string.button_4);

  private static final PageConfig.Builder JUMP_BETWEEN_CONTROLS_PRE_R =
      PageConfig.builder(R.string.jump_between_controls_title)
          .addText(R.string.jump_between_controls_text_pre_r, R.string.granularity_native_control)
          .addButton(R.string.button_1)
          .addButton(R.string.button_2)
          .addButton(R.string.button_3)
          .addButton(R.string.button_4);

  private static final PageConfig.Builder JUMP_BETWEEN_LINKS =
      PageConfig.builder(R.string.jump_between_links_title)
          .addText(R.string.jump_between_links_text, R.string.granularity_native_link)
          .addText(R.string.paragraph1_text)
          .addTextWithLink(R.string.link1_text)
          .addText(R.string.paragraph2_text)
          .addTextWithLink(R.string.link2_text)
          .addText(R.string.paragraph3_text)
          .addTextWithLink(R.string.link3_text)
          .addText(R.string.paragraph4_text)
          .addTextWithLink(R.string.link4_text)
          .addTextWithLink(R.string.target_link_text);

  private static final PageConfig.Builder JUMP_BETWEEN_LINKS_PRE_R =
      PageConfig.builder(R.string.jump_between_links_title)
          .addText(R.string.jump_between_links_text_pre_r, R.string.granularity_native_link)
          .addTextWithLink(R.string.link1_text)
          .addText(R.string.paragraph2_text)
          .addTextWithLink(R.string.link2_text)
          .addText(R.string.paragraph3_text)
          .addTextWithLink(R.string.link3_text)
          .addText(R.string.paragraph4_text)
          .addTextWithLink(R.string.link4_text)
          .addTextWithLink(R.string.target_link_text);

  private static final PageConfig.Builder JUMP_BETWEEN_HEADINGS =
      PageConfig.builder(R.string.jump_between_headings_title)
          .addText(R.string.jump_between_headings_text, R.string.granularity_native_heading)
          .addDivider()
          .addHeading(R.string.content_heading)
          .addText(R.string.content_text)
          .addDivider()
          .addHeading(R.string.navigation_heading)
          .addText(R.string.find_finish_button_text);

  private static final PageConfig.Builder JUMP_BETWEEN_HEADINGS_PRE_R =
      PageConfig.builder(R.string.jump_between_headings_title)
          .addText(R.string.jump_between_headings_text_pre_r, R.string.granularity_native_heading)
          .addDivider()
          .addHeading(R.string.content_heading)
          .addText(R.string.content_text)
          .addDivider()
          .addHeading(R.string.navigation_heading)
          .addText(R.string.find_finish_button_text);

  private static final PageConfig.Builder VOICE_COMMANDS =
      PageConfig.builder(R.string.voice_commands_title).addText(R.string.voice_commands_text);

  private static final PageConfig.Builder PRACTICE_GESTURES =
      PageConfig.builder(R.string.practice_gestures_title)
          .addText(R.string.practice_gestures_text)
          .captureAllGestures();

  private static final PageConfig.Builder PRACTICE_GESTURES_PRE_R =
      PageConfig.builder(R.string.practice_gestures_title)
          .addText(R.string.practice_gestures_text_pre_r)
          .captureAllGestures();

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // First-run Training

  static final TrainingConfig FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER =
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
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
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
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
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
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
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
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
