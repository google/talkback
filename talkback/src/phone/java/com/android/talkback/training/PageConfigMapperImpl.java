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
      case PAGE_ID_EXPLORE_BY_TOUCH:
      case PAGE_ID_SCROLLING:
      case PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER:
      case PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER:
      case PAGE_ID_ADJUSTING_VOLUME:
      case PAGE_ID_MENUS:
      case PAGE_ID_MENUS_PRE_R:
      case PAGE_ID_TUTORIAL_FINISHED:
      case PAGE_ID_TUTORIAL_INDEX:
      case PAGE_ID_USING_TEXT_BOXES:
      case PAGE_ID_TYPING_TEXT:
      case PAGE_ID_MOVING_CURSOR:
      case PAGE_ID_SELECTING_TEXT:
      case PAGE_ID_SELECTING_TEXT_PRE_R:
      case PAGE_ID_COPY_CUT_PASTE:
      case PAGE_ID_COPY_CUT_PASTE_PRE_R:
      case PAGE_ID_TYPO_CORRECTION:
      case PAGE_ID_TYPO_CORRECTION_PRE_R:
      case PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH:
      case PAGE_ID_TYPO_CORRECTION_NOT_ENGLISH_PRE_R:
      case PAGE_ID_READ_BY_CHARACTER:
      case PAGE_ID_READ_BY_CHARACTER_PRE_R:
      case PAGE_ID_JUMP_BETWEEN_CONTROLS:
      case PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R:
      case PAGE_ID_JUMP_BETWEEN_LINKS:
      case PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R:
      case PAGE_ID_JUMP_BETWEEN_HEADINGS:
      case PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R:
      case PAGE_ID_VOICE_COMMANDS:
      case PAGE_ID_PRACTICE_GESTURES:
      case PAGE_ID_PRACTICE_GESTURES_PRE_R:
      case PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS:
      case PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES:
      case PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS:
      case PAGE_ID_ADDITIONAL_TIPS_LOOKOUT:
      case PAGE_ID_ADDITIONAL_TIPS_CHECKING_NOTIFICATIONS:
        return TutorialConfigUtils.getInitialPageBuilder(pageId).build();
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
      case PAGE_ID_DETAILED_IMAGE_DESCRIPTIONS:
        return OnboardingConfigs.imageDescription.build();
      case PAGE_ID_GOOGLE_DISABILITY_SUPPORT:
        return OnboardingConfigs.googleDisabilitySupport.build();
      case PAGE_ID_PUNCTUATION_AND_SYMBOLS:
        return OnboardingConfigs.punctuationAndSymbols.build();
      case PAGE_ID_NEW_BRAILLE_SHORTCUTS:
        return OnboardingConfigs.newBrailleShortcuts.build();
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
