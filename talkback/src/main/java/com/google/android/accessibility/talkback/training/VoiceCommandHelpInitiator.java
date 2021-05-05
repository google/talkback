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
import static com.google.android.accessibility.talkback.training.PageConfig.PageContentPredicate.SUPPORT_SYSTEM_ACTIONS;

import android.content.Context;
import android.content.Intent;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;

/** Starts a {@link TrainingActivity} to show voice command help pages. */
public class VoiceCommandHelpInitiator {
  public static Intent createVoiceCommandHelpIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        FeatureSupport.isWatch(context) ? VOICE_COMMAND_HELP_FOR_WATCH : VOICE_COMMAND_HELP);
  }

  private VoiceCommandHelpInitiator() {}

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Pages

  private static final PageConfig.Builder voiceCommandOverview =
      PageConfig.builder(R.string.voice_commands_help_title)
          .hidePageNumber()
          .setEndOfSection()
          .addText(R.string.voice_commands_help_description)
          .addText(R.string.voice_commands_help_hint)
          .addLink(
              R.string.shortcut_title_reading_control,
              R.string.voice_commands_help_reading_controls_description,
              R.drawable.quantum_gm_ic_hearing_googblue_24,
              R.string.voice_commands_help_reading_controls_title)
          .addLink(
              R.string.voice_commands_help_find_items_title,
              R.string.voice_commands_help_find_items_description,
              R.drawable.quantum_gm_ic_search_googblue_24,
              R.string.voice_commands_help_find_items_title)
          .addLink(
              R.string.shortcut_title_text_editing,
              R.string.voice_commands_help_text_editing_description,
              R.drawable.quantum_gm_ic_text_fields_alt_googblue_24,
              R.string.shortcut_title_text_editing)
          .addLink(
              R.string.voice_commands_help_device_navigation_title,
              R.string.voice_commands_help_device_navigation_description,
              R.drawable.quantum_gm_ic_smartphone_googblue_24,
              R.string.voice_commands_help_device_navigation_title)
          .addLink(
              R.string.voice_commands_help_other_commands_title,
              R.string.voice_commands_help_other_commands_description,
              R.drawable.quantum_gm_ic_more_horiz_googblue_24,
              R.string.voice_commands_help_other_commands_title);

  private static final PageConfig.Builder voiceCommandReadingControls =
      PageConfig.builder(R.string.voice_commands_help_reading_controls_title)
          .addTextWithBullet(R.string.shortcut_read_from_current)
          .addTextWithBullet(R.string.title_read_from_top)
          .addTextWithBullet(R.string.voice_commands_help_next)
          .addSubText(R.string.voice_commands_help_next_description)
          .addTextWithBullet(R.string.voice_commands_help_navigation)
          .addSubText(R.string.voice_commands_help_navigation_description)
          .addTextWithBullet(R.string.voice_commands_help_speech_rate)
          .addSubText(R.string.voice_commands_help_speech_rate_description)
          .addTextWithBullet(R.string.voice_commands_help_verbosity)
          .addSubText(R.string.voice_commands_help_verbosity_description)
          .addTextWithBullet(R.string.voice_commands_first)
          .addSubText(R.string.voice_commands_help_first_description)
          .addTextWithBullet(R.string.voice_commands_last)
          .addSubText(R.string.voice_commands_help_last_description)
          .addTextWithBullet(R.string.voice_commands_languages)
          .addSubText(R.string.voice_commands_help_languages_description);

  private static final PageConfig.Builder voiceCommandFindItems =
      PageConfig.builder(R.string.voice_commands_help_find_items_title)
          .addTextWithBullet(R.string.voice_commands_help_find_text)
          .addSubText(R.string.voice_commands_help_find_text_description)
          .addTextWithBullet(R.string.voice_commands_find)
          .addSubText(R.string.voice_commands_help_find_description)
          .addTextWithBullet(R.string.voice_commands_screen_search)
          .addSubText(R.string.voice_commands_help_screen_search_description);

  private static final PageConfig.Builder voiceCommandFindItemsForWatch =
      PageConfig.builder(R.string.voice_commands_help_find_items_title)
          .addTextWithBullet(R.string.voice_commands_help_find_text)
          .addSubText(R.string.voice_commands_help_find_text_description)
          .addTextWithBullet(R.string.voice_commands_find)
          .addSubText(R.string.voice_commands_help_find_description);

  private static final PageConfig.Builder voiceCommandTextEditing =
      PageConfig.builder(R.string.shortcut_title_text_editing)
          .addTextWithBullet(R.string.voice_commands_help_type_text)
          .addTextWithBullet(android.R.string.selectAll)
          .addTextWithBullet(R.string.voice_commands_select)
          .addTextWithBullet(R.string.voice_commands_deselect)
          .addTextWithBullet(android.R.string.copy)
          .addSubText(R.string.voice_commands_help_copy_description)
          .addTextWithBullet(android.R.string.cut)
          .addSubText(R.string.voice_commands_help_cut_description)
          .addTextWithBullet(R.string.voice_commands_delete)
          .addSubText(R.string.voice_commands_help_delete_description)
          .addTextWithBullet(android.R.string.paste)
          .addTextWithBullet(R.string.voice_commands_edit_options);

  private static final PageConfig.Builder voiceCommandDeviceNavigation =
      PageConfig.builder(R.string.voice_commands_help_device_navigation_title)
          .hidePageNumber()
          .addTextWithBullet(R.string.voice_commands_home)
          .addTextWithBullet(R.string.voice_commands_back)
          .addTextWithBullet(R.string.voice_commands_notifications)
          .addTextWithBullet(R.string.voice_commands_quick_settings)
          .addTextWithBullet(R.string.voice_commands_overview)
          .addSubText(R.string.voice_commands_help_overview_description)
          .addTextWithBullet(R.string.voice_commands_all_apps, SUPPORT_SYSTEM_ACTIONS)
          .addTextWithBullet(R.string.voice_commands_assistant)
          .addSubText(R.string.voice_commands_help_assistant_description);

  private static final PageConfig.Builder voiceCommandOtherCommands =
      PageConfig.builder(R.string.voice_commands_help_other_commands_title)
          .addTextWithBullet(R.string.voice_commands_talkback_settings)
          .addTextWithBullet(R.string.shortcut_enable_dimming)
          .addSubText(R.string.voice_commands_help_hide_screen_description)
          .addTextWithBullet(R.string.shortcut_disable_dimming)
          .addSubText(R.string.voice_commands_help_show_screen_description)
          .addTextWithBullet(R.string.title_custom_action)
          .addSubText(R.string.voice_commands_help_actions_description)
          .addTextWithBullet(R.string.voice_commands_help_label)
          .addSubText(R.string.voice_commands_help_label_description)
          .addTextWithBullet(R.string.voice_commands_stop)
          .addSubText(R.string.voice_commands_help_stop_description);

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Training

  public static final TrainingConfig VOICE_COMMAND_HELP =
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
          .addPageWithoutNumberAndNavigationBar(voiceCommandOverview)
          .addPageEndOfSection(voiceCommandReadingControls)
          .addPageEndOfSection(voiceCommandFindItems)
          .addPageEndOfSection(voiceCommandTextEditing)
          .addPageEndOfSection(voiceCommandDeviceNavigation)
          .addPageEndOfSection(voiceCommandOtherCommands)
          .setButtons(DEFAULT_BUTTONS)
          .setSupportNavigateUpArrow(true)
          .build();

  public static final TrainingConfig VOICE_COMMAND_HELP_FOR_WATCH =
      TrainingConfig.builder(R.string.new_feature_talkback_91_title)
          .addPageWithoutNumberAndNavigationBar(voiceCommandOverview)
          .addPageEndOfSection(voiceCommandReadingControls)
          .addPageEndOfSection(voiceCommandFindItemsForWatch)
          .addPageEndOfSection(voiceCommandTextEditing)
          .addPageEndOfSection(voiceCommandDeviceNavigation)
          .addPageEndOfSection(voiceCommandOtherCommands)
          .setButtons(DEFAULT_BUTTONS)
          .setSupportNavigateUpArrow(true)
          .build();
}
