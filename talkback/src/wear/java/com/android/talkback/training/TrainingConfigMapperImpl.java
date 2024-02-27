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
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivityInterfaceInjector.TrainingConfigMapper;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId;

/** The implementation of {@link TrainingConfigMapper} for wear platform. */
public final class TrainingConfigMapperImpl implements TrainingConfigMapper {

  @Override
  @Nullable
  public TrainingConfig getTraining(TrainingId trainingId, @Nullable Context context) {
    switch (trainingId) {
      case TRAINING_ID_TUTORIAL_FOR_WATCH:
        return WearTutorialInitiator.createTutorialForWatch();
        // Uncomment it when wear platform supports speech recognize.
        // case TRAINING_ID_VOICE_COMMAND_HELP_FOR_WATCH:
        //   return VoiceCommandAndHelpConfigs.VOICE_COMMAND_HELP_FOR_WATCH;
      default:
        return null;
    }
  }
}
