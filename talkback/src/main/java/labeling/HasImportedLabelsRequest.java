/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.labeling;

import com.google.android.accessibility.utils.labeling.LabelProviderClient;

public class HasImportedLabelsRequest extends LabelClientRequest<Boolean> {

  private OnHasImportedLabelsCompleteListener listener;

  public HasImportedLabelsRequest(
      LabelProviderClient client, OnHasImportedLabelsCompleteListener listener) {
    super(client);
    this.listener = listener;
  }

  @Override
  public Boolean doInBackground() {
    return mClient.hasImportedLabels();
  }

  @Override
  public void onPostExecute(Boolean result) {
    if (listener != null && result != null) {
      listener.onHasImportedRequestCompleted(result);
    }
  }

  public interface OnHasImportedLabelsCompleteListener {
    void onHasImportedRequestCompleted(boolean hasImportedLabels);
  }
}
