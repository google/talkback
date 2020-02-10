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
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WebActivity extends Activity {

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

    WebView webView = (WebView) findViewById(R.id.web);
    webView.setWebViewClient(new WhitelistWebViewClient());
    webView.loadUrl(uri.toString());
  }

  private static class WhitelistWebViewClient extends WebViewClient {
    @Override
    public @Nullable WebResourceResponse shouldInterceptRequest(
        WebView view, WebResourceRequest request) {
      final String host = request.getUrl().getHost();
      // Allow URLs from Google for the TOS and Privacy Policy.
      if ((host != null)
          && (host.matches("[a-z]*.google.com")
              || host.matches("[a-z]*.google.[a-z][a-z]")
              || host.matches("[a-z]*.google.co.[a-z][a-z]")
              || host.matches("[a-z]*.google.com.[a-z][a-z]")
              || host.matches("[a-z]*.gstatic.com")
              || host.equals("fonts.googleapis.com"))) {
        return super.shouldInterceptRequest(view, request);
      }
      return new WebResourceResponse("", "", 403, "Denied", null, null);
    }
  }
}
