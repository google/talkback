/*
 * Copyright (C) 2012 Google Inc.
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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WebActivity extends Activity {
  @Nullable WebView webView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    if (intent == null) {
      finish();
      return;
    }

    Uri uri = intent.getData();
    if (uri == null) {
      finish();
      return;
    }

    setContentView(R.layout.web_activity);

    webView = (WebView) findViewById(R.id.web);
    webView.setWebViewClient(new AllowlistWebViewClient());
    webView.loadUrl(uri.toString());
  }

  @Override
  public boolean onKeyDown(int keyCode, @Nullable KeyEvent event) {
    // Enable going back in the browsing history with the back key.
    if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
      webView.goBack();
    } else {
      finish();
    }
    return true;
  }

  static class AllowlistWebViewClient extends WebViewClient {
    private static final String TAG = "AllowlistWebViewClient";

    private static final ImmutableList<Pattern> GOOGLE_HOST_PATTERNS =
        ImmutableList.of(
            Pattern.compile("[a-z]+.google.com"),
            Pattern.compile("[a-z]+.google.[a-z][a-z]"),
            Pattern.compile("[a-z]+.google.co.[a-z][a-z]"),
            Pattern.compile("[a-z]+.google.com.[a-z][a-z]"),
            Pattern.compile("[a-z]+.gstatic.com"),
            Pattern.compile("fonts.googleapis.com"));

    @Override
    public @Nullable WebResourceResponse shouldInterceptRequest(
        @NonNull WebView view, @NonNull WebResourceRequest request) {
      String host = request.getUrl().getHost();

      if (host == null) {
        LogUtils.i(TAG, "Failed to load URL because host was null.");
        return createDenyResponse();
      }

      for (Pattern pattern : GOOGLE_HOST_PATTERNS) {
        if (pattern.matcher(host).matches()) {
          return null; // No interception.
        }
      }

      LogUtils.i(TAG, "Failed to load URL because it is not allow-listed. Host: %s", host);
      return createDenyResponse();
    }

    private static @NonNull WebResourceResponse createDenyResponse() {
      return new WebResourceResponse("", "", 403, "Denied", null, null);
    }
  }
}
