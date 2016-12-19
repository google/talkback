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
import android.widget.TextView;

import com.android.talkbacktests.R;

public class FocusTest extends BaseTestContent {

    public FocusTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_focus, container, false);
        TextView textView1 = (TextView) view.findViewById(R.id.test_focus_text1);
        TextView textView2 = (TextView) view.findViewById(R.id.test_focus_text2);
        TextView textView3 = (TextView) view.findViewById(R.id.test_focus_text3);
        TextView textView4 = (TextView) view.findViewById(R.id.test_focus_text4);

        textView1.setText(getString(R.string.ordered_text_view, 1));
        textView2.setText(getString(R.string.ordered_text_view, 2));
        textView3.setText(getString(R.string.ordered_text_view, 3));
        textView4.setText(getString(R.string.ordered_text_view, 4));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            textView1.setAccessibilityTraversalBefore(R.id.test_focus_text4);
            textView2.setAccessibilityTraversalBefore(R.id.test_focus_text1);
            textView3.setAccessibilityTraversalBefore(R.id.test_focus_text2);
            textView4.setAccessibilityTraversalBefore(R.id.test_focus_text3);

            textView4.setAccessibilityTraversalAfter(R.id.test_focus_text1);
            textView1.setAccessibilityTraversalAfter(R.id.test_focus_text2);
            textView2.setAccessibilityTraversalAfter(R.id.test_focus_text3);
            textView3.setAccessibilityTraversalAfter(R.id.test_focus_text4);
        } else {
            TextView warning = (TextView) view.findViewById(R.id.test_focus_warning);
            warning.setText(getString(R.string.min_api_level_warning,
                    Build.VERSION_CODES.LOLLIPOP_MR1,
                    Build.VERSION.SDK_INT));
            warning.setFocusable(true);
            textView1.setEnabled(false);
            textView2.setEnabled(false);
            textView3.setEnabled(false);
            textView4.setEnabled(false);
        }
        return view;
    }
}