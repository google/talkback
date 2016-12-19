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

package com.android.talkback.labeling;

import android.text.TextUtils;
import com.android.utils.labeling.Label;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelSeparator {

    private List<Label> mImportedNewLabels = new ArrayList<>();
    private List<Label> mImportedConflictLabels = new ArrayList<>();
    private List<Label> mExistingConflictLabels = new ArrayList<>();

    public LabelSeparator(List<Label> currentLabels, List<Label> newLabels) {
        separate(currentLabels, newLabels);
    }

    private void separate(List<Label> currentLabels, List<Label> importedLabels) {
        if (currentLabels == null || currentLabels.size() == 0) {
            if (importedLabels != null) {
                mImportedNewLabels.addAll(importedLabels);
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
                mExistingConflictLabels.add(existingLabel);
                mImportedConflictLabels.add(importedLabel);
            } else {
                mImportedNewLabels.add(importedLabel);
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
        return mImportedNewLabels;
    }

    public List<Label> getImportedConflictLabels() {
        return mImportedConflictLabels;
    }

    public List<Label> getExistingConflictLabels() {
        return mExistingConflictLabels;
    }

    /**
     * Wrapper class to have separate hashCode/equals logic for HashMap
     */
    // reduced visibility for testing
    static class LabelWrapper {

        private Label mLabel;

        public LabelWrapper(Label label) {
            mLabel = label;
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash += mLabel.getPackageName() != null ? mLabel.getPackageName().hashCode() : 0;
            hash += 31*hash + mLabel.getPackageSignature() != null ?
                    mLabel.getPackageSignature().hashCode() : 0;
            hash += 31*hash + mLabel.getViewName() != null ? mLabel.getViewName().hashCode() : 0;
            hash += 31*hash + mLabel.getLocale() != null ? mLabel.getLocale().hashCode() : 0;
            hash += 31*hash + mLabel.getPackageVersion();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof LabelWrapper)) {
                return false;
            }

            LabelWrapper wrapper = (LabelWrapper) obj;
            return TextUtils.equals(mLabel.getPackageName(), wrapper.mLabel.getPackageName()) &&
                    TextUtils.equals(mLabel.getPackageSignature(),
                            wrapper.mLabel.getPackageSignature()) &&
                    TextUtils.equals(mLabel.getViewName(), wrapper.mLabel.getViewName()) &&
                    TextUtils.equals(mLabel.getLocale(), wrapper.mLabel.getLocale()) &&
                    mLabel.getPackageVersion() == wrapper.mLabel.getPackageVersion();
        }
    }
}
