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
import java.util.ArrayList;
import java.util.List;

public class ImportLabelRequest extends LabelClientRequest<Integer> {

  private final List<Label> labels;
  private final boolean overrideExistingLabels;
  private final OnImportLabelCallback callback;

  public ImportLabelRequest(
      LabelProviderClient client,
      List<Label> labels,
      boolean overrideExistingLabels,
      OnImportLabelCallback listener) {
    super(client);
    this.labels = labels;
    this.overrideExistingLabels = overrideExistingLabels;
    callback = listener;
  }

  @Override
  public Integer doInBackground() {
    if (!mClient.isInitialized()) {
      return 0;
    }

    mClient.deleteLabels(CustomLabelManager.SOURCE_TYPE_BACKUP);
    mClient.updateSourceType(
        CustomLabelManager.SOURCE_TYPE_IMPORT, CustomLabelManager.SOURCE_TYPE_USER);
    List<Label> currentLabels = mClient.getCurrentLabels();
    if (currentLabels == null) {
      currentLabels = new ArrayList<>();
    }

    LabelSeparator separator = new LabelSeparator(currentLabels, labels);
    List<Label> newLabels = separator.getImportedNewLabels();
    int updateCount = 0;
    int labelCount = newLabels.size();
    for (int index = 0; index < labelCount; index++) {
      Label label = newLabels.get(index);
      mClient.insertLabel(label, CustomLabelManager.SOURCE_TYPE_IMPORT);
      updateCount++;
    }

    if (overrideExistingLabels) {
      List<Label> existentConflictLabels = separator.getExistingConflictLabels();
      int existentLabelCount = existentConflictLabels.size();
      for (int index = 0; index < existentLabelCount; index++) {
        Label label = existentConflictLabels.get(index);
        mClient.updateLabelSourceType(label.getId(), CustomLabelManager.SOURCE_TYPE_BACKUP);
      }

      List<Label> importedConflictLabels = separator.getImportedConflictLabels();
      int importedLabelCount = importedConflictLabels.size();
      for (int index = 0; index < importedLabelCount; index++) {
        Label label = importedConflictLabels.get(index);
        mClient.insertLabel(label, CustomLabelManager.SOURCE_TYPE_IMPORT);
        updateCount++;
      }
    }

    return updateCount;
  }

  @Override
  public void onPostExecute(Integer result) {
    if (callback != null && result != null) {
      callback.onLabelImported(result);
    }
  }

  public interface OnImportLabelCallback {
    public void onLabelImported(int changedLabelsCount);
  }
}
