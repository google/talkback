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

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Base64;
import android.util.Pair;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Utility methods for generating/parsing the data of Gemini requests and responses. */
public class DataFieldUtils {

  private static final String TAG = "GeminiDataFieldUtils";

  private static final String CANDIDATES = "candidates";
  private static final String FINISH_REASON = "finishReason";
  private static final String CONTENT = "content";
  private static final String PARTS = "parts";
  private static final String TEXT = "text";
  private static final String SAFETY_RATINGS = "safetyRatings";
  private static final String SAFETY_SETTINGS = "safetySettings";
  private static final String PROMPT_FEEDBACK = "promptFeedback";
  private static final String BLOCK_REASON = "blockReason";
  private static final String CATEGORY = "category";
  private static final String PROBABILITY = "probability";
  private static final String THRESHOLD = "threshold";
  private static final String CONTENTS = "contents";
  private static final String INLINE_DATA = "inlineData";
  private static final String MIME_TYPE = "mimeType";
  private static final String IMAGE_JPEG = "image/jpeg";
  private static final String DATA = "data";
  private static final String HARASSMENT_CATEGORY = "HARM_CATEGORY_HARASSMENT";
  private static final String HATE_SPEECH_CATEGORY = "HARM_CATEGORY_HATE_SPEECH";
  private static final String SEXUALLY_EXPLICIT_CATEGORY = "HARM_CATEGORY_SEXUALLY_EXPLICIT";
  private static final String DANGEROUS_CONTENT = "HARM_CATEGORY_DANGEROUS_CONTENT";

  private static final String HARM_PROBABILITY_LOW = "LOW";
  private static final String HARM_PROBABILITY_MEDIUM = "MEDIUM";
  private static final String HARM_PROBABILITY_HIGH = "HIGH";

  static final String FINISH_REASON_UNSPECIFIED = "FINISH_REASON_UNSPECIFIED";
  static final String FINISH_REASON_STOP = "STOP";

  private DataFieldUtils() {}

  public static JSONArray createSafetySettingsJson(
      String safetyThresholdHarassment,
      String safetyThresholdHateSpeech,
      String safetyThresholdSexuallyExplicit,
      String safetyThresholdDangerousContent)
      throws JSONException {
    return new JSONArray()
        .put(
            0,
            new JSONObject()
                .put(CATEGORY, HARASSMENT_CATEGORY)
                .put(THRESHOLD, safetyThresholdHarassment))
        .put(
            1,
            new JSONObject()
                .put(CATEGORY, HATE_SPEECH_CATEGORY)
                .put(THRESHOLD, safetyThresholdHateSpeech))
        .put(
            2,
            new JSONObject()
                .put(CATEGORY, SEXUALLY_EXPLICIT_CATEGORY)
                .put(THRESHOLD, safetyThresholdSexuallyExplicit))
        .put(
            3,
            new JSONObject()
                .put(CATEGORY, DANGEROUS_CONTENT)
                .put(THRESHOLD, safetyThresholdDangerousContent));
  }

  /**
   * Compresses a {@link Bitmap} to a base64 representation encoded as a string. It returns an empty
   * string if compression fails.
   */
  public static String encodeImage(Bitmap image) {
    byte[] byteArrayOutputStream = encodeImageToByteArray(image);
    if (byteArrayOutputStream == null) {
      return "";
    }
    return Base64.encodeToString(byteArrayOutputStream, Base64.NO_WRAP);
  }

  /**
   * Compresses a {@link Bitmap} to a base64 representation encoded as a byte array. It returns null
   * if compression fails.
   */
  public static byte[] encodeImageToByteArray(Bitmap image) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    if (!image.compress(CompressFormat.JPEG, 80, byteArrayOutputStream)) {
      LogUtils.e(TAG, "Bitmap compression failed!");
      return null;
    }
    return byteArrayOutputStream.toByteArray();
  }

  /** Creates a JSON object for the data body of a Gemini request. */
  public static JSONObject createPostDataJson(
      String command, String encodedImage, JSONArray safetySettings) throws JSONException {
    JSONObject prompt = new JSONObject();
    prompt.put(TEXT, command);

    JSONArray parts =
        new JSONArray()
            .put(0, prompt)
            .put(
                1,
                new JSONObject()
                    .put(
                        INLINE_DATA,
                        new JSONObject().put(MIME_TYPE, IMAGE_JPEG).put(DATA, encodedImage)));

    JSONObject postData = new JSONObject();
    postData.put(CONTENTS, new JSONArray().put(0, new JSONObject().put(PARTS, parts)));
    postData.put(SAFETY_SETTINGS, safetySettings);
    LogUtils.v(TAG, "Message Body JSONArray parts:%s", parts);
    LogUtils.v(TAG, "Message Body JSONArray safetySettings:%s", safetySettings);

    return postData;
  }

  public static GeminiResponse parseGeminiResponse(JSONObject response) throws JSONException {
    GeminiResponse.Builder resultBuilder = GeminiResponse.builder();
    List<Pair<String, String>> safetyReasons = new ArrayList<>();
    if (response.has(CANDIDATES) && response.getJSONArray(CANDIDATES).length() > 0) {
      JSONArray candidates = response.getJSONArray(CANDIDATES);
      JSONObject scanObject = candidates.getJSONObject(0);
      resultBuilder.setFinishReason(scanObject.getString(FINISH_REASON));
      if (scanObject.has(CONTENT)) {
        resultBuilder.setText(
            scanObject.getJSONObject(CONTENT).getJSONArray(PARTS).getJSONObject(0).getString(TEXT));
      }
      if (scanObject.has(SAFETY_RATINGS)) {
        parseSafetyReason(scanObject.getJSONArray(SAFETY_RATINGS), safetyReasons);
      }
    }
    resultBuilder.setSafetyRatings(ImmutableList.copyOf(safetyReasons));
    if (response.has(PROMPT_FEEDBACK)) {
      JSONObject promptFeedback = response.getJSONObject(PROMPT_FEEDBACK);
      if (promptFeedback.has(BLOCK_REASON)) {
        resultBuilder.setBlockReason(promptFeedback.getString(BLOCK_REASON));
      }
    }

    return resultBuilder.build();
  }

  private static void matchProbability(
      String category, String probability, List<Pair<String, String>> safetyReasons) {
    switch (probability) {
      case HARM_PROBABILITY_LOW:
      case HARM_PROBABILITY_MEDIUM:
      case HARM_PROBABILITY_HIGH:
        safetyReasons.add(new Pair<>(category, probability));
        break;
      default:
        break;
    }
  }

  private static void parseSafetyReason(
      JSONArray safetyRatings, List<Pair<String, String>> safetyReasons) throws JSONException {
    for (int j = 0; j < safetyRatings.length(); j++) {
      JSONObject safetyRating = safetyRatings.getJSONObject(j);
      if (safetyRating.has(CATEGORY) && safetyRating.has(PROBABILITY)) {
        matchProbability(
            safetyRating.getString(CATEGORY), safetyRating.getString(PROBABILITY), safetyReasons);
      }
    }
  }

  /** Class holding the parsed response from Gemini service. */
  @AutoValue
  public abstract static class GeminiResponse {
    abstract @Nullable String text();

    abstract @Nullable String finishReason();

    abstract @Nullable String blockReason();

    abstract ImmutableList<Pair<String, String>> safetyRatings();

    static Builder builder() {
      return new AutoValue_DataFieldUtils_GeminiResponse.Builder()
          .setSafetyRatings(ImmutableList.of());
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setText(String value);

      abstract Builder setFinishReason(String value);

      abstract Builder setBlockReason(String value);

      abstract Builder setSafetyRatings(ImmutableList<Pair<String, String>> value);

      abstract GeminiResponse build();
    }
  }
}
