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

import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_FOR_GESTURE_NAVIGATION_USER;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_FOR_TV;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_FOR_WATCH;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_PRACTICE_GESTURE;
import static com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId.TRAINING_ID_TUTORIAL_PRACTICE_GESTURE_PRE_R;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;

/** Starts a {@link TrainingActivity} to show tutorial. */
public class TutorialInitiator {

  private static final int NAV_BAR_MODE_GESTURAL = 2;

  /** Returns an intent to start tutorial for the first run users. */
  public static Intent createFirstRunTutorialIntent(Context context) {
    if (FormFactorUtils.getInstance().isAndroidWear()) {
      return TrainingActivity.createTrainingIntent(context, TRAINING_ID_TUTORIAL_FOR_WATCH);
    } else if (FormFactorUtils.getInstance().isAndroidTv()) {
      return TrainingActivity.createTrainingIntent(context, TRAINING_ID_TUTORIAL_FOR_TV);
    } else {
      return TrainingActivity.createTrainingIntent(
          context,
          isGestureNavigateEnabled(context)
              ? (FeatureSupport.isMultiFingerGestureSupported()
                  ? TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER
                  : TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R)
              : (FeatureSupport.isMultiFingerGestureSupported()
                  ? TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER
                  : TRAINING_ID_FIRST_RUN_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R),
          /* showExitBanner= */ true);
    }
  }

  /** Returns an intent to start tutorial. */
  public static Intent createTutorialIntent(Context context) {
    if (FormFactorUtils.getInstance().isAndroidWear()) {
      return TrainingActivity.createTrainingIntent(context, TRAINING_ID_TUTORIAL_FOR_WATCH);
    } else if (FormFactorUtils.getInstance().isAndroidTv()) {
      return TrainingActivity.createTrainingIntent(context, TRAINING_ID_TUTORIAL_FOR_TV);
    } else if (isGestureNavigateEnabled(context)) {
      return TrainingActivity.createTrainingIntent(
          context,
          FeatureSupport.isMultiFingerGestureSupported()
              ? TRAINING_ID_TUTORIAL_FOR_GESTURE_NAVIGATION_USER
              : TRAINING_ID_TUTORIAL_FOR_GESTURE_NAVIGATION_USER_PRE_R);
    } else {
      return TrainingActivity.createTrainingIntent(
          context,
          FeatureSupport.isMultiFingerGestureSupported()
              ? TRAINING_ID_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER
              : TRAINING_ID_TUTORIAL_FOR_3_BUTTON_NAVIGATION_USER_PRE_R);
    }
  }

  public static Intent createPracticeGesturesIntent(Context context) {
    return TrainingActivity.createTrainingIntent(
        context,
        FeatureSupport.isMultiFingerGestureSupported()
            ? TRAINING_ID_TUTORIAL_PRACTICE_GESTURE
            : TRAINING_ID_TUTORIAL_PRACTICE_GESTURE_PRE_R);
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
}
