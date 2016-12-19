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
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class URLSpanTest extends BaseTestContent {

    public URLSpanTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_url_span, container, false);

        URLSpan urlSpan1 = new URLSpan("http://developer.android.com/index.html");
        TextView textView1 = (TextView) view.findViewById(R.id.test_url_span_textview1);
        SpannableString text1 = new SpannableString("Android Developer Homepage");
        text1.setSpan(urlSpan1, 0, 26, 0);
        textView1.setText(text1);
        textView1.setMovementMethod(LinkMovementMethod.getInstance());

        URLSpan urlSpan2 = new URLSpan("http://developer.android.com/guide/index.html");
        URLSpan urlSpan3 = new URLSpan("http://developer.android.com" +
                "/guide/topics/ui/accessibility/index.html");
        TextView textView2 = (TextView) view.findViewById(R.id.test_url_span_textview2);
        SpannableString text2 = new SpannableString(getString(R.string.sample_spanned_text_2));
        text2.setSpan(urlSpan2, 0, 5, 0);
        text2.setSpan(urlSpan3, 48, 89, 0);
        textView2.setText(text2);
        textView2.setMovementMethod(LinkMovementMethod.getInstance());

        StyleSpan styleSpanBold = new StyleSpan(Typeface.BOLD);
        StyleSpan stypeSpanItalic = new StyleSpan(Typeface.ITALIC);
        StyleSpan styleSpanBoldItalic = new StyleSpan(Typeface.BOLD_ITALIC);

        TextView textView3 = (TextView) view.findViewById(R.id.test_url_span_textview3);
        SpannableString text3 = new SpannableString(getString(R.string.sample_spanned_text_2));
        text3.setSpan(styleSpanBold, 0, 5, 0);
        text3.setSpan(stypeSpanItalic, 32, 42, 0);
        text3.setSpan(styleSpanBoldItalic, 56, 69, 0);
        textView3.setText(text3);

        TextView textView4 = (TextView) view.findViewById(R.id.test_url_span_textview4);
        SpannableString text4 = new SpannableString(getString(R.string.sample_spanned_text_3));
        text4.setSpan(styleSpanBold, 0, 4, 0);
        text4.setSpan(stypeSpanItalic, 15, 18, 0);
        text4.setSpan(styleSpanBoldItalic, 33, 38, 0);
        textView4.setText(text4);

        return view;
    }
}