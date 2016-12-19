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
import android.content.Context;
import android.os.Build;
import com.android.utils.labeling.Label;
import com.android.talkback.BuildConfig;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * Tests for CustomLabelManager
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class CustomLabelMigrationManagerTest {

    private Context mContext = RuntimeEnvironment.application.getApplicationContext();

    @Test
    public void labelsParseUnparse_shouldReturnEqualLabels() throws JSONException {
        List<Label> initialLabels = createLabels();
        CustomLabelMigrationManager manager = new CustomLabelMigrationManager(mContext);
        String jsonText = manager.generateJsonText(initialLabels);
        List<Label> parsedLabels = manager.parseLabels(jsonText);
        checkLabelsEquality(initialLabels, parsedLabels);
    }

    private List<Label> createLabels() {
        List<Label> labels = new ArrayList<>();
        labels.add(new Label("packageName1", "packageSignature1", "viewName1", "labelText1",
                "locale1", 0, "", 0));
        labels.add(new Label("packageName2", "packageSignature2", "viewName2", "labelText2",
                "locale2", 0, "", 0));
        return labels;
    }

    private void checkLabelsEquality(List<Label> initial, List<Label> parsed) {
        // assume separator works properly while it is tested separately
        LabelSeparator separator = new LabelSeparator(initial, parsed);
        List<Label> newLabels = separator.getImportedNewLabels();
        List<Label> existentLabels = separator.getImportedConflictLabels();
        assertTrue(newLabels.size() == 0);
        assertTrue(existentLabels.size() == initial.size());
    }
}

