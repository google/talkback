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
import android.graphics.Bitmap;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.ErrorReason;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.GeminiEndpoint;
import com.google.android.accessibility.talkback.actor.gemini.GeminiActor.GeminiResponseListener;
import com.google.android.accessibility.utils.Consumer;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Util class to communicate with the on-device AI service. */
public class AiCoreEndpoint implements GeminiEndpoint {

  /** Callback interface for AiFeature download. */
  public interface AiFeatureDownloadCallback {
    void onDownloadProgress(long currentSizeInBytes, long totalSizeInBytes);

    void onDownloadCompleted();
  }

  public AiCoreEndpoint(Context context) {}

  public AiCoreEndpoint(Context context, boolean withService) {}

  public boolean hasAiCore() {
    return false;
  }

  public ListenableFuture<Boolean> hasAiCoreAsynchronous() {
    return Futures.immediateFuture(false);
  }

  public boolean needAiCoreUpdate() {
    return false;
  }

  public boolean needAstreaUpdate() {
    return false;
  }

  public boolean isAiFeatureAvailable() {
    return false;
  }

  public boolean isAiFeatureDownloadable() {
    return false;
  }

  public void displayAiFeatureDownloadDialog(Consumer<Void> buttonClickCallback) {}

  public void displayAiCoreUpdateDialog() {}

  public void displayAstreaUpdateDialog() {}

  public void setAiFeatureDownloadCallback(AiFeatureDownloadCallback downloadCallback) {}

  @Override
  public boolean createRequestGeminiCommand(
      String text,
      Bitmap image,
      boolean manualTrigger,
      GeminiResponseListener geminiResponseListener) {
    geminiResponseListener.onError(ErrorReason.UNSUPPORTED);
    return false;
  }

  /** Called to cancel a processing or pending command. */
  public void cancelCommand() {}

  /** Check if there's pending transaction. */
  public boolean hasPendingTransaction() {
    return false;
  }

  /** Called when the service is unbound. */
  public void onUnbind() {}
}
