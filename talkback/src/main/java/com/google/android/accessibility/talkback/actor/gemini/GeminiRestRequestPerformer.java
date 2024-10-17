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

import android.content.Context;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.accessibility.talkback.actor.gemini.DataFieldUtils.GeminiResponse;
import org.json.JSONException;
import org.json.JSONObject;

/** Performs Gemini REST requests. */
public class GeminiRestRequestPerformer {
  private static final int TIMEOUT_MS = 5_000;
  private static final int MAX_RETRIES = 2;

  interface GeminiRestResponseCallback {
    void onResponse(GeminiResponse response);

    void onFailure(String reason);

    void onCancelled();
  }

  private final RequestQueue requestQueue;

  public GeminiRestRequestPerformer(Context context) {
    requestQueue = Volley.newRequestQueue(context);
  }

  public boolean isKeylessInitialized() {
    return false;
  }

  /** Performs an HTTP POST request to the given URL with the specified message body. */
  public void performRequest(String url, JSONObject postData, GeminiRestResponseCallback callback) {
    JsonObjectRequest stringRequest =
        new JsonObjectRequest(
            Request.Method.POST,
            url,
            postData,
            response -> {
              try {
                GeminiResponse result = DataFieldUtils.parseGeminiResponse(response);
                callback.onResponse(result);
              } catch (JSONException e) {
                callback.onFailure(e.toString());
              }
            },
            error -> {
              callback.onFailure(error.toString());
            });

    stringRequest.setRetryPolicy(
        new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    requestQueue.add(stringRequest);
  }

  public void cancelExistingRequestIfNeeded() {}

  public boolean hasPendingTransaction() {
    return false;
  }
}
