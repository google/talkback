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

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.GeminiEndpoint;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.GeminiResponseListener;

/** Util class to communicate with image captioning through Aratea. */
public class ArateaEndpoint implements GeminiEndpoint {

  public ArateaEndpoint(Context context, Application application) {}

  @Override
  public boolean createRequestGeminiCommand(
      String text,
      Bitmap image,
      boolean manualTrigger,
      GeminiResponseListener geminiResponseListener) {
    return false;
  }

  @Override
  public void cancelCommand() {}

  @Override
  public boolean hasPendingTransaction() {
    return false;
  }
}
