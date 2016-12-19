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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class CustomActionTest extends BaseTestContent {

    public CustomActionTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_custom_action, container, false);
        EditText editText = (EditText) view.findViewById(R.id.test_custom_action_editText);
        editText.setText(R.string.custom_action_default_message);

        TextView warning = (TextView) view.findViewById(R.id.test_custom_action_warning);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            editText.setAccessibilityDelegate(new MyAccessibilityDelegate());
        } else {
            editText.setEnabled(false);
            warning.setText(getString(R.string.min_api_level_warning, Build.VERSION_CODES.LOLLIPOP,
                    Build.VERSION.SDK_INT));
            warning.setFocusable(true);
        }

        return view;
    }

    private final class MyAccessibilityDelegate extends View.AccessibilityDelegate {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.custom_action_1,
                        getString(R.string.action_clear_message)));
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.custom_action_2,
                        getString(R.string.action_restore_message)));
            }
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            switch (action) {
                case (R.id.custom_action_1):
                    ((EditText) host).setText("");
                    return true;
                case (R.id.custom_action_2):
                    ((EditText) host).setText(getString(R.string.custom_action_default_message));
                    return true;
                default:
                    return super.performAccessibilityAction(host, action, args);
            }
        }
    }
}