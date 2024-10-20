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

package com.google.android.accessibility.talkback.trainingcommon;

import android.content.Context;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TrainingSectionId;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;

/** A delegate class to transit the statistics to a persistent storage. */
public class TrainingMetricStore {
  /** Types of the training. */
  public enum Type {
    TUTORIAL,
    ONBOARDING,
  }

  public TrainingMetricStore(Context context, Type trainingType) {}

  public void onTutorialEntered(@TrainingSectionId int event) {}

  public void onTutorialEvent(@TrainingSectionId int event) {}

  public void onTrainingPause(PageId pageId) {}

  public void onTrainingResume(PageId pageId) {}

  public void onTrainingPageEntered(PageId pageId) {}

  public void onTrainingPageLeft(PageId pageId) {}

  public void onTrainingPageCompleted(PageId pageId) {}
}
