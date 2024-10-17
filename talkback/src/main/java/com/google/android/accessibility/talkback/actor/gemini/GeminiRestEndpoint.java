/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.accessibility.talkback.actor.gemini;

import static com.google.android.accessibility.talkback.actor.gemini.DataFieldUtils.FINISH_REASON_STOP;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.text.TextUtils;
import com.google.android.accessibility.talkback.actor.gemini.DataFieldUtils.GeminiResponse;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.ErrorReason;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.FinishReason;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.GeminiEndpoint;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.GeminiResponseListener;
import com.google.android.accessibility.talkback.actor.gemini.GeminiRestRequestPerformer.GeminiRestResponseCallback;
import com.google.android.accessibility.utils.NetworkUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Util class to communicate with Gemini APIs */
public class GeminiRestEndpoint implements GeminiEndpoint {

  private static final String TAG = "GeminiEndpoint";
  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/";
  private static final String GEMINI_URL_PARAM = ":generateContent?key=";
  private static final String GEMINI_URL_NO_PARAM = ":generateContent";

  private final Context context;
  private final SharedPreferences prefs;
  private final String model;
  private final String url;
  private final String urlWithApiKey;
  private final GeminiRestRequestPerformer requestPerformer;
  private final String safetyThresholdHarassment;
  private final String safetyThresholdHateSpeech;
  private final String safetyThresholdSexuallyExplicit;
  private final String safetyThresholdDangerousContent;
  private final String prefixPrompt;

  public GeminiRestEndpoint(
      Context context, String apiKey, GeminiRestRequestPerformer requestPerformer) {
    this.context = context;
    model = GeminiConfiguration.getGeminiModel(context);
    url = GEMINI_URL + model + GEMINI_URL_NO_PARAM;
    if (!TextUtils.isEmpty(apiKey)) {
      urlWithApiKey = GEMINI_URL + model + GEMINI_URL_PARAM + apiKey;
    } else {
      urlWithApiKey = "";
    }
    this.requestPerformer = requestPerformer;
    safetyThresholdHarassment = GeminiConfiguration.getSafetyThresholdHarassment(context);
    safetyThresholdHateSpeech = GeminiConfiguration.getSafetyThresholdHateSpeech(context);
    safetyThresholdSexuallyExplicit =
        GeminiConfiguration.getSafetyThresholdSexuallyExplicit(context);
    safetyThresholdDangerousContent =
        GeminiConfiguration.getSafetyThresholdDangerousContent(context);
    prefixPrompt = GeminiConfiguration.getPrefixPrompt(context);
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
  }

  private boolean isSupported() {
    return !TextUtils.isEmpty(urlWithApiKey) || requestPerformer.isKeylessInitialized();
  }

  @Override
  public boolean createRequestGeminiCommand(
      String command,
      Bitmap screenshotCapture,
      boolean manualTrigger,
      GeminiResponseListener geminiResponseListener) {
    LogUtils.v(TAG, "createRequestGeminiCommand - %s", model);
    if (!checkGeminiAvailability(command, screenshotCapture, geminiResponseListener)) {
      return false;
    }

    try {
      // Set safety settings
      JSONArray safetySettings =
          DataFieldUtils.createSafetySettingsJson(
              safetyThresholdHarassment,
              safetyThresholdHateSpeech,
              safetyThresholdSexuallyExplicit,
              safetyThresholdDangerousContent);

      // Encode screenshot in base64
      String encodedImage = DataFieldUtils.encodeImage(screenshotCapture);
      if (TextUtils.isEmpty(encodedImage)) {
        LogUtils.e(TAG, "Bitmap compression failed!");
        geminiResponseListener.onError(ErrorReason.BITMAP_COMPRESSION_FAIL);
        return false;
      }

      JSONObject postData =
          DataFieldUtils.createPostDataJson(prefixPrompt + command, encodedImage, safetySettings);

      String urlTarget = TextUtils.isEmpty(urlWithApiKey) ? url : urlWithApiKey;
      requestPerformer.performRequest(
          urlTarget,
          postData,
          new GeminiRestResponseCallback() {
            @Override
            public void onResponse(GeminiResponse response) {
              if (response.finishReason() != null
                  && response.finishReason().equals(FINISH_REASON_STOP)) { // Positive acknowledge
                geminiResponseListener.onResponse(FinishReason.STOP, response.text());
                LogUtils.v(TAG, "Gemini succeeds");
              } else { // Redefine the hint of these kinds when the use cases are understood.
                geminiResponseListener.onResponse(FinishReason.ERROR_BLOCKED, /* text= */ null);
                LogUtils.v(
                    TAG,
                    "Gemini finishes by some reason:%s",
                    (response.blockReason() != null)
                        ? response.blockReason()
                        : response.finishReason());
              }
            }

            @Override
            public void onFailure(String reason) {
              LogUtils.w(TAG, "ErrorResponse processing Gemini request:%s", reason);
              geminiResponseListener.onResponse(FinishReason.ERROR_RESPONSE, /* text= */ null);
            }

            @Override
            public void onCancelled() {
              LogUtils.v(TAG, "Cancelled a pending task.");
              geminiResponseListener.onError(ErrorReason.JOB_CANCELLED);
            }
          });
    } catch (JSONException e) {
      LogUtils.e(TAG, "Error processing Gemini request: " + e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public void cancelCommand() {
    requestPerformer.cancelExistingRequestIfNeeded();
  }

  @Override
  public boolean hasPendingTransaction() {
    return requestPerformer.hasPendingTransaction();
  }

  private boolean checkGeminiAvailability(
      String command, Bitmap screenshotCapture, GeminiResponseListener geminiResponseListener) {
    if (!isSupported()) {
      LogUtils.d(TAG, "Gemini API is not supported");
      geminiResponseListener.onError(ErrorReason.UNSUPPORTED);
      return false;
    }

    if (!NetworkUtils.isNetworkConnected(context)) {
      LogUtils.d(TAG, "Internet is not connected");
      geminiResponseListener.onError(ErrorReason.NETWORK_ERROR);
      return false;
    }

    if (screenshotCapture == null) {
      LogUtils.d(TAG, "screenshot is not provided.");
      geminiResponseListener.onError(ErrorReason.NO_IMAGE);
      return false;
    }
    if (TextUtils.isEmpty(command)) {
      LogUtils.d(TAG, "command part is empty.");
      geminiResponseListener.onError(ErrorReason.EMPTY_PROMPT);
      return false;
    }
    return true;
  }
}
