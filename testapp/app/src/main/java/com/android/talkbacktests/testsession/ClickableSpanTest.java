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
import android.support.design.widget.Snackbar;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class ClickableSpanTest extends BaseTestContent {

    public ClickableSpanTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_clickable_span, container, false);
        final String rawText = getString(R.string.example_clickable_span_text);
        ClickableSpan span1 = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Snackbar.make(widget,
                        getString(R.string.clickable_span_action_template,
                                rawText.substring(19, 29)),
                        Snackbar.LENGTH_LONG).show();
            }
        };
        ClickableSpan span2 = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Snackbar.make(widget,
                        getString(R.string.clickable_span_action_template,
                                rawText.substring(67, 76)),
                        Snackbar.LENGTH_LONG).show();
            }
        };

        TextView textView = (TextView) view.findViewById(R.id.test_clickable_span_text_view);
        SpannableString text = new SpannableString(rawText);
        text.setSpan(span1, 19, 29, 0);
        text.setSpan(span2, 67, 76, 0);
        textView.setText(text);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        return view;
    }
}