/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonUtils {

  public static JSONObject readFromRawFile(Context context, int rawFileResId)
      throws IOException, JSONException {
    try (InputStream stream = context.getResources().openRawResource(rawFileResId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
      StringBuilder stringBuilder = new StringBuilder();
      String input;
      while ((input = reader.readLine()) != null) {
        stringBuilder.append(input).append("\n");
      }
      return new JSONObject(stringBuilder.toString());
    }
  }

  public static @Nullable String getString(JSONObject jsonObject, String key) throws JSONException {
    if (jsonObject != null && jsonObject.has(key)) {
      return jsonObject.getString(key);
    }

    return null;
  }

  public static int getInt(JSONObject jsonObject, String key) throws JSONException {
    if (jsonObject != null && jsonObject.has(key)) {
      return jsonObject.getInt(key);
    }

    return -1;
  }

  public static @Nullable JSONArray getJsonArray(JSONObject jsonObject, String key)
      throws JSONException {
    if (jsonObject != null && jsonObject.has(key)) {
      return jsonObject.getJSONArray(key);
    }

    return null;
  }

  public static @Nullable JSONObject getJsonObject(JSONObject jsonObject, String key)
      throws JSONException {
    if (jsonObject != null && jsonObject.has(key)) {
      return jsonObject.getJSONObject(key);
    }

    return null;
  }
}
