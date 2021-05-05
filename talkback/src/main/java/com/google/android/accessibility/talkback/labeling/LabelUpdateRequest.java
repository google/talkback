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

import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelProviderClient;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

public class LabelUpdateRequest extends LabelClientRequest<Boolean> {

  private static final String TAG = "LabelUpdateRequest";

  private Label label;
  private final CustomLabelManager.OnLabelsInPackageChangeListener listener;

  public LabelUpdateRequest(
      LabelProviderClient client,
      Label label,
      CustomLabelManager.OnLabelsInPackageChangeListener listener) {
    super(client);
    this.label = label;
    this.listener = listener;
  }

  @Override
  public Boolean doInBackground() {
    LogUtils.v(TAG, "Spawning new LabelUpdateRequest(%d) for label: %s", hashCode(), label);

    if (label == null || label.getId() == Label.NO_ID) {
      return false;
    }

    boolean result = mClient.updateLabel(label, CustomLabelManager.SOURCE_TYPE_USER);
    if (result) {
      // remove copy of the label from import backup
      mClient.deleteLabel(
          label.getPackageName(),
          label.getViewName(),
          label.getLocale(),
          label.getPackageVersion(),
          CustomLabelManager.SOURCE_TYPE_BACKUP);
    }

    return result;
  }

  @Override
  public void onPostExecute(Boolean result) {
    LogUtils.v(TAG, "LabelUpdateRequest(%d) complete. Result: %s", hashCode(), result);

    if (listener != null && result) {
      listener.onLabelsInPackageChanged(label.getPackageName());
    }
  }
}
