/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.talkback.labeling;

import android.annotation.TargetApi;
import android.os.Build;
import com.android.utils.labeling.Label;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import com.android.talkback.BuildConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * Tests for LabelSeparator
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class LabelSeparatorTest {

    @Test
    public void equalLabelsSetsSeparation_shouldReturnNoNewLabels() throws JSONException {
        List<Label> initialLabels = createLabelsList1();
        List<Label> sameNewLabels = createLabelsList1();
        LabelSeparator separator = new LabelSeparator(initialLabels, sameNewLabels);
        assertTrue(separator.getImportedNewLabels().size() == 0);
        assertTrue(isLabelListsEquals(separator.getImportedConflictLabels(), initialLabels));
    }

    @Test
    public void uniqueLabelsSetsSeparation_shouldReturnAllNewLabels() throws JSONException {
        List<Label> initialLabels = createLabelsList1();
        List<Label> allNewLabels = createLabelsList2();
        LabelSeparator separator = new LabelSeparator(initialLabels, allNewLabels);
        assertTrue(separator.getImportedConflictLabels().size() == 0);
        assertTrue(isLabelListsEquals(separator.getImportedNewLabels(), allNewLabels));
    }

    @Test
    public void newAndExistenceLabelMixTest() throws JSONException {
        List<Label> initialLabels = createLabelsList1();

        List<Label> labelSet1 = createLabelsList1();
        List<Label> labelSet2 = createLabelsList2();
        List<Label> existenceAndNewLabels = new ArrayList<>();
        existenceAndNewLabels.addAll(labelSet1);
        existenceAndNewLabels.addAll(labelSet2);

        LabelSeparator separator = new LabelSeparator(initialLabels, existenceAndNewLabels);
        assertTrue(isLabelListsEquals(separator.getImportedNewLabels(), labelSet2));
        assertTrue(isLabelListsEquals(separator.getImportedConflictLabels(), labelSet1));
    }

    private List<Label> createLabelsList1() {
        List<Label> labels = new ArrayList<>();
        labels.add(new Label("packageName1", "packageSignature1", "viewName1", "labelText1",
                "locale1", 0, "", 0));
        labels.add(new Label("packageName2", "packageSignature2", "viewName2", "labelText2",
                "locale2", 0, "", 0));
        return labels;
    }

    private List<Label> createLabelsList2() {
        List<Label> labels = new ArrayList<>();
        labels.add(new Label("packageName1_list2", "packageSignature1_list2", "viewName1_list2",
                "labelText1_list2", "locale1_list2", 0, "", 0));
        labels.add(new Label("packageName2_list2", "packageSignature2_list2", "viewName2_list2",
                "labelText2_list2", "locale2_list2", 0, "", 0));
        labels.add(new Label("packageName3_list2", "packageSignature3_list2", "viewName3_list2",
                "labelText3_list2", "locale3_list2", 0, "", 0));
        return labels;
    }

    private boolean isLabelListsEquals(List<Label> initial, List<Label> parsed) {
        List<LabelSeparator.LabelWrapper> wrapperList = new ArrayList<>();
        for (Label label : initial) {
            wrapperList.add(new LabelSeparator.LabelWrapper(label));
        }

        for (Label label : parsed) {
            if (!wrapperList.remove(new LabelSeparator.LabelWrapper(label))) {
                return false;
            }
        }

        return wrapperList.size() == 0;
    }
}

