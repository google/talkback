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

public class DirectLabelFetchRequest extends LabelClientRequest<Label> {

  private long labelId;
  private OnLabelFetchedListener onLabelFetchedListener;

  public DirectLabelFetchRequest(
      LabelProviderClient client, long labelId, OnLabelFetchedListener listener) {
    super(client);
    this.labelId = labelId;
    onLabelFetchedListener = listener;
  }

  @Override
  public Label doInBackground() {
    return mClient.getLabelById(labelId);
  }

  @Override
  public void onPostExecute(Label result) {
    if (onLabelFetchedListener != null) {
      onLabelFetchedListener.onLabelFetched(result);
    }
  }

  public interface OnLabelFetchedListener {
    void onLabelFetched(Label result);
  }
}
