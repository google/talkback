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

/** The implementation of {@link PageConfigMapper} for wear platform. */
public final class PageConfigMapperImpl implements PageConfigMapper {

  @Override
  @Nullable
  public PageConfig getPage(PageId pageId, Context context, int vendorPageIndex) {
    switch (pageId) {
      case PAGE_ID_WELCOME_TO_TALKBACK_WATCH:
        return WearTutorialInitiator.WELCOME_TO_TALKBACK_WATCH_PAGE.build();
      case PAGE_ID_WATCH_SCROLLING:
        return WearTutorialInitiator.SCROLLING_WATCH_PAGE.build();
      case PAGE_ID_WATCH_GO_BACK:
        return WearTutorialInitiator.GO_BACK_WATCH_PAGE.build();
      case PAGE_ID_WATCH_VOLUME_UP:
        return WearTutorialInitiator.VOLUME_UP_WATCH_PAGE.build();
      case PAGE_ID_WATCH_VOLUME_DOWN:
        return WearTutorialInitiator.VOLUME_DOWN_WATCH_PAGE.build();
      case PAGE_ID_WATCH_OPEN_TALKBACK_MENU:
        return WearTutorialInitiator.OPEN_TALKBACK_MENU_WATCH_PAGE.build();
      case PAGE_ID_WATCH_END_TUTORIAL:
        return WearTutorialInitiator.END_TUTORIAL_WATCH_PAGE.build();
        // Uncomment it when wear platform supports speech recognize.
        // case PAGE_ID_VOICE_COMMAND_FIND_ITEMS_FOR_WATCH:
        //   return VoiceCommandAndHelpConfigs.VOICE_COMMAND_FIND_ITEMS_FOR_WATCH.build();
      default:
        return null;
    }
  }
}
