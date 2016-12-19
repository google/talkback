/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkbacktests.testsession;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class LabelTest extends BaseTestContent {

    public LabelTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        final View view = inflater.inflate(R.layout.test_label, container, false);
        final View firstNameLabel = view.findViewById(R.id.test_label_firstNameLabel);
        final View firstNameField = view.findViewById(R.id.test_label_firstNameField);
        final View lastNameLabel = view.findViewById(R.id.test_label_lastNameLabel);
        final View lastNameField = view.findViewById(R.id.test_label_lastNameField);
        final View ageLabel = view.findViewById(R.id.test_label_ageLabel);
        final View ageField = view.findViewById(R.id.test_label_ageField);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            //Test setLabeledBy()
            View.AccessibilityDelegate firstNameDelegate = new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                                                              AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setLabeledBy(firstNameLabel);
                }
            };
            firstNameField.setAccessibilityDelegate(firstNameDelegate);
            //Test setLabelFor()
            lastNameLabel.setLabelFor(R.id.test_label_lastNameField);


            //Test Case with both setLabeledBy() and setLabelFor()
            View.AccessibilityDelegate ageFieldDelegate = new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                                                              AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setLabeledBy(ageLabel);
                }
            };
            ageField.setAccessibilityDelegate(ageFieldDelegate);
            ageLabel.setLabelFor(R.id.test_label_ageField);
        } else {
            TextView warning = (TextView) view.findViewById(R.id.test_label_warning);
            warning.setText(getString(R.string.min_api_level_warning,
                    Build.VERSION_CODES.JELLY_BEAN_MR1,
                    Build.VERSION.SDK_INT));
            warning.setFocusable(true);
            firstNameLabel.setEnabled(false);
            firstNameField.setEnabled(false);
            lastNameLabel.setEnabled(false);
            lastNameField.setEnabled(false);
        }
        return view;
    }
}