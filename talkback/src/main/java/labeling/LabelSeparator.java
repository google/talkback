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

import android.text.TextUtils;
import com.google.android.accessibility.utils.labeling.Label;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelSeparator {

  private List<Label> importedNewLabels = new ArrayList<>();
  private List<Label> importedConflictLabels = new ArrayList<>();
  private List<Label> existingConflictLabels = new ArrayList<>();

  public LabelSeparator(List<Label> currentLabels, List<Label> newLabels) {
    separate(currentLabels, newLabels);
  }

  private void separate(List<Label> currentLabels, List<Label> importedLabels) {
    if (currentLabels == null || currentLabels.size() == 0) {
      if (importedLabels != null) {
        importedNewLabels.addAll(importedLabels);
      }
      return;
    }

    if (importedLabels == null || importedLabels.size() == 0) {
      return;
    }

    Map<LabelWrapper, Label> labelMap = getCurrentLabelMap(currentLabels);
    for (Label importedLabel : importedLabels) {
      if (importedLabel == null) {
        continue;
      }

      LabelWrapper wrapper = new LabelWrapper(importedLabel);
      Label existingLabel = labelMap.get(wrapper);
      if (existingLabel != null) {
        existingConflictLabels.add(existingLabel);
        importedConflictLabels.add(importedLabel);
      } else {
        importedNewLabels.add(importedLabel);
      }
    }
  }

  private Map<LabelWrapper, Label> getCurrentLabelMap(List<Label> currentLabels) {
    HashMap<LabelWrapper, Label> result = new HashMap<>();
    if (currentLabels == null) {
      return result;
    }

    for (Label label : currentLabels) {
      if (label != null) {
        LabelWrapper wrapper = new LabelWrapper(label);
        result.put(wrapper, label);
      }
    }

    return result;
  }

  public List<Label> getImportedNewLabels() {
    return importedNewLabels;
  }

  public List<Label> getImportedConflictLabels() {
    return importedConflictLabels;
  }

  public List<Label> getExistingConflictLabels() {
    return existingConflictLabels;
  }

  /** Wrapper class to have separate hashCode/equals logic for HashMap */
  // public visibility for testing
  public static class LabelWrapper {

    private Label label;

    public LabelWrapper(Label label) {
      this.label = label;
    }

    @Override
    public int hashCode() {
      int hash = 17;
      hash += label.getPackageName() != null ? label.getPackageName().hashCode() : 0;
      hash +=
          31 * hash + label.getPackageSignature() != null
              ? label.getPackageSignature().hashCode()
              : 0;
      hash += 31 * hash + label.getViewName() != null ? label.getViewName().hashCode() : 0;
      hash += 31 * hash + label.getLocale() != null ? label.getLocale().hashCode() : 0;
      hash += 31 * hash + label.getPackageVersion();
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof LabelWrapper)) {
        return false;
      }

      LabelWrapper wrapper = (LabelWrapper) obj;
      return TextUtils.equals(label.getPackageName(), wrapper.label.getPackageName())
          && TextUtils.equals(label.getPackageSignature(), wrapper.label.getPackageSignature())
          && TextUtils.equals(label.getViewName(), wrapper.label.getViewName())
          && TextUtils.equals(label.getLocale(), wrapper.label.getLocale())
          && label.getPackageVersion() == wrapper.label.getPackageVersion();
    }
  }
}
