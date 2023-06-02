/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.core.util.Pair;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.libraries.surveys.PresentSurveyRequest;
import com.google.android.libraries.surveys.PresentSurveyRequest.SurveyEventListener;
import com.google.android.libraries.surveys.SurveyData;
import com.google.android.libraries.surveys.SurveyMetadata;
import com.google.android.libraries.surveys.SurveyRequest;
import com.google.android.libraries.surveys.SurveyRequest.ErrorType;
import com.google.android.libraries.surveys.SurveyRequest.RequestSurveyCallback;
import com.google.android.libraries.surveys.SurveysClient;
import googledata.experiments.mobile.accessibility_suite.features.HatsSurveyConfig;
import java.util.ArrayList;
import java.util.List;
import org.chromium.net.CronetEngine;

/**
 * The requester of HaTS, it shows a survey on a specific client activity (ex. TalkBack Settings)
 * and intents to collect user feedback. The client activity needs to contain a Framelayout with ID
 * R.id.survey_prompt_parent_sheet.
 */
public class HatsSurveyRequester {

  private static final String TAG = "HatsSurveyRequesterImpl";
  // MAX_PROMPT_WIDTH has no effect on the prompt for now, see b/78031571 for details.
  private static final int MAX_PROMPT_WIDTH = 340;
  private static final String MULTI_FINGER_GESTURE_KEY = "multi_finger_gesture";
  private final Activity activity;
  private SurveysClient surveysClient;
  private SurveyMetadata surveyMetadata;

  public HatsSurveyRequester(Activity activity) {
    this.activity = activity;
  }

  /** Requests Hats survey. */
  public void requestSurvey() {
    Context context = activity.getApplicationContext();

    if (!allowSurvey(context)) {
      return;
    }

    // TODO: Use keyless to request HaTS, instead of using API key directly.
    CronetEngine.Builder cronetEngineBuilder = new CronetEngine.Builder(context);
    surveysClient = SurveysClient.create(context, cronetEngineBuilder.build());
    SurveyRequest surveyRequest =
        SurveyRequest.newBuilder(context, HatsSurveyConfig.triggerId(context))
            .setRequestSurveyCallback(
                new RequestSurveyCallback() {
                  @Override
                  public void onRequestSuccess(SurveyData surveyData) {
                    presentSurvey(surveysClient, surveyData);
                  }

                  @Override
                  public void onRequestFailed(String triggerId, ErrorType errorType) {
                    LogUtils.w(
                        TAG,
                        "Survey error : Failed to fetch survey (trigger id: %s, error: %s.)",
                        triggerId,
                        errorType);
                  }
                })
            .setEnableProofMode(HatsSurveyConfig.enableProofMode(context))
            .setApiKey(HatsSurveyConfig.apiKey(context))
            .build();

    surveysClient.requestSurvey(surveyRequest);
  }

  private boolean allowSurvey(Context context) {
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);

    // Can't find the FrameLayout to show the survey.
    if (activity.findViewById(R.id.survey_prompt_parent_sheet) == null) {
      return false;
    }

    // Don't request the survey on TV, ARC or wearable devices.
    if (FeatureSupport.isArc() || FeatureSupport.isTv(context) || FeatureSupport.isWatch(context)) {
      return false;
    }

    // The user doesn't enable TalkBack before.
    if (sharedPreferences.getBoolean(TalkBackService.PREF_FIRST_TIME_USER, true)) {
      return false;
    }

    // During setup.
    if (!SettingsUtils.allowLinksOutOfSettings(context)) {
      return false;
    }

    return true;
  }

  private void presentSurvey(SurveysClient surveysClient, SurveyData surveyData) {
    SurveyEventListener surveyEventListener =
        new SurveyEventListener() {
          @Override
          public void onSurveyPrompted(SurveyMetadata metadata) {
            surveyMetadata = metadata;
          }

          @Override
          public void onSurveyClosed(SurveyMetadata surveyMetadata) {
            if (activity.isFinishing()) {
              return;
            }

            // Notify users that the survey was closed.
            Context context = activity.getApplicationContext();
            Toast.makeText(context, R.string.survey_closed, Toast.LENGTH_SHORT).show();
          }

          @Override
          public void onPresentSurveyFailed(
              SurveyMetadata surveyMetadata, PresentSurveyRequest.ErrorType errorType) {
            LogUtils.w(TAG, "Survey onPresentSurveyFailed : %s", errorType);
          }
        };

    // Product Specific Data.
    List<Pair<String, String>> productData = new ArrayList<>();
    productData.add(
        new Pair<>(
            MULTI_FINGER_GESTURE_KEY,
            String.valueOf(FeatureSupport.isMultiFingerGestureSupported())));

    PresentSurveyRequest.Builder builder =
        PresentSurveyRequest.newBuilder(/* activity= */ activity, surveyData)
            .setSurveyEventListener(surveyEventListener)
            .insertIntoParent(R.id.survey_prompt_parent_sheet, MAX_PROMPT_WIDTH)
            .setPsd(productData);

    surveysClient.presentSurvey(builder.build());
  }

  /** Dismisses Hats survey. */
  public void dismissSurvey() {
    if ((surveysClient != null) && (surveyMetadata != null)) {
      surveysClient.dismissSurvey(surveyMetadata, activity);
    }
  }
}
