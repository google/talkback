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

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivityInterfaceInjector.PageConfigMapper;
import com.google.android.accessibility.talkback.trainingcommon.tv.TvTutorialInitiator;
import com.google.android.accessibility.talkback.trainingcommon.tv.VendorConfigReader;

/** The implementation of {@link PageConfigMapper} for handset platform. */
public final class PageConfigMapperImpl implements PageConfigMapper {

  @Override
  @Nullable
  public PageConfig getPage(PageId pageId, Context context, int vendorPageIndex) {
    switch (pageId) {
      case PAGE_ID_WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES:
        return OnboardingConfigs.welcomeToUpdatedTalkBackForMultiFingerGestures.build();
      case PAGE_ID_WELCOME_TO_TALKBACK:
        return TutorialConfigs.WELCOME_TO_TALKBACK_PAGE.build();
      case PAGE_ID_EXPLORE_BY_TOUCH:
        return TutorialConfigs.EXPLORE_BY_TOUCH_PAGE.build();
      case PAGE_ID_SCROLLING:
        return TutorialConfigs.SCROLLING_PAGE.build();
      case PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER:
        return TutorialConfigs.GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER.build();
      case PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER:
        return TutorialConfigs.GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER.build();
      case PAGE_ID_MENUS:
        return TutorialConfigs.MENUS_PAGE.build();
      case PAGE_ID_MENUS_PRE_R:
        return TutorialConfigs.MENUS_PAGE_PRE_R.build();
      case PAGE_ID_TUTORIAL_FINISHED:
        return TutorialConfigs.TUTORIAL_FINISHED_PAGE.build();
      case PAGE_ID_TUTORIAL_INDEX:
        return TutorialConfigs.TUTORIAL_INDEX_PAGE.build();
      case PAGE_ID_USING_TEXT_BOXES:
        return TutorialConfigs.USING_TEXT_BOXES_PAGE.build();
      case PAGE_ID_TYPING_TEXT:
        return TutorialConfigs.TYPING_TEXT_PAGE.build();
      case PAGE_ID_MOVING_CURSOR:
        return TutorialConfigs.MOVING_CURSOR_PAGE.build();
      case PAGE_ID_SELECTING_TEXT:
        return TutorialConfigs.SELECTING_TEXT_PAGE.build();
      case PAGE_ID_SELECTING_TEXT_PRE_R:
        return TutorialConfigs.SELECTING_TEXT_PAGE_PRE_R.build();
      case PAGE_ID_COPY_CUT_PASTE:
        return TutorialConfigs.COPY_CUT_PASTE_PAGE.build();
      case PAGE_ID_COPY_CUT_PASTE_PRE_R:
        return TutorialConfigs.COPY_CUT_PASTE_PAGE_PRE_R.build();
      case PAGE_ID_READ_BY_CHARACTER:
        return TutorialConfigs.READ_BY_CHARACTER.build();
      case PAGE_ID_READ_BY_CHARACTER_PRE_R:
        return TutorialConfigs.READ_BY_CHARACTER_PRE_R.build();
      case PAGE_ID_JUMP_BETWEEN_CONTROLS:
        return TutorialConfigs.JUMP_BETWEEN_CONTROLS.build();
      case PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R:
        return TutorialConfigs.JUMP_BETWEEN_CONTROLS_PRE_R.build();
      case PAGE_ID_JUMP_BETWEEN_LINKS:
        return TutorialConfigs.JUMP_BETWEEN_LINKS.build();
      case PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R:
        return TutorialConfigs.JUMP_BETWEEN_LINKS_PRE_R.build();
      case PAGE_ID_JUMP_BETWEEN_HEADINGS:
        return TutorialConfigs.JUMP_BETWEEN_HEADINGS.build();
      case PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R:
        return TutorialConfigs.JUMP_BETWEEN_HEADINGS_PRE_R.build();
      case PAGE_ID_VOICE_COMMANDS:
        return TutorialConfigs.VOICE_COMMANDS.build();
      case PAGE_ID_PRACTICE_GESTURES:
        return TutorialConfigs.PRACTICE_GESTURES.build();
      case PAGE_ID_PRACTICE_GESTURES_PRE_R:
        return TutorialConfigs.PRACTICE_GESTURES_PRE_R.build();
      case PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS:
        return TutorialConfigs.MAKING_CALLS.build();
      case PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES:
        return TutorialConfigs.SENDING_MESSAGES.build();
      case PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS:
        return TutorialConfigs.READING_WEB_EMAILS.build();
      case PAGE_ID_ADDITIONAL_TIPS_LOOKOUT:
        return TutorialConfigs.LOOKOUT.build();
      case PAGE_ID_VOICE_COMMAND_OVERVIEW:
        return VoiceCommandAndHelpConfigs.VoiceCommandOverView.build();
      case PAGE_ID_VOICE_COMMAND_READING_CONTROLS:
        return VoiceCommandAndHelpConfigs.VoiceCommandReadingControls.build();
      case PAGE_ID_VOICE_COMMAND_FIND_ITEMS:
        return VoiceCommandAndHelpConfigs.VoiceCommandFindItems.build();
      case PAGE_ID_VOICE_COMMAND_TEXT_EDITING:
        return VoiceCommandAndHelpConfigs.VoiceCommandTextEditing.build();
      case PAGE_ID_VOICE_COMMAND_DEVICE_NAVIGATION:
        return VoiceCommandAndHelpConfigs.VoiceCommandDeviceNavigation.build();
      case PAGE_ID_VOICE_COMMAND_OTHER_COMMANDS:
        return VoiceCommandAndHelpConfigs.VoiceCommandOtherCommands.build();
      case PAGE_ID_UPDATE_WELCOME:
        return OnboardingConfigs.updateWelcome.build();
      case PAGE_ID_DESCRIBE_IMAGES:
        return OnboardingConfigs.describeImages.build();
      case PAGE_ID_SPELL_CHECK_FOR_BRAILLE_KEYBOARD:
        return OnboardingConfigs.spellCheckForBrailleKeyboard.build();
      case PAGE_ID_AUTO_SCROLL_FOR_BRAILLE_DISPLAY:
        return OnboardingConfigs.autoScrollForBrailleDisplay.build();
      case PAGE_ID_NEW_BRAILLE_SHORTCUTS_AND_LANGUAGES:
        return OnboardingConfigs.newBrailleShortcutsAndLanguages.build();
      case PAGE_ID_TV_OVERVIEW:
      case PAGE_ID_TV_SHORTCUT:
      case PAGE_ID_TV_REMOTE:
        return TvTutorialInitiator.getPageConfigForDefaultPage(
            context, VendorConfigReader.retrieveConfig(context), pageId);
      case PAGE_ID_TV_VENDOR:
        return TvTutorialInitiator.getPageConfigForVendorPage(
            VendorConfigReader.retrieveConfig(context), vendorPageIndex);
      case PAGE_ID_UNKNOWN:
      case PAGE_ID_FINISHED:
      default:
        return null;
    }
  }
}
