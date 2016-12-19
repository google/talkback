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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class LiveRegionTest extends BaseTestContent implements View.OnClickListener {

    public LiveRegionTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    private int mCount;

    private TextView mTextView1;
    private TextView mTextView2;
    private TextView mTextView3;

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        mCount = 1;
        View view = inflater.inflate(R.layout.test_live_region, container, false);

        view.findViewById(R.id.test_live_region_button1).setOnClickListener(this);
        view.findViewById(R.id.test_live_region_button2).setOnClickListener(this);
        view.findViewById(R.id.test_live_region_button3).setOnClickListener(this);

        mTextView1 = (TextView) view.findViewById(R.id.content25_text1);
        mTextView2 = (TextView) view.findViewById(R.id.content25_text2);
        mTextView3 = (TextView) view.findViewById(R.id.content25_text3);

        return view;
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.test_live_region_button1):
                mTextView1.setText(getString(R.string.live_region_announcement_template, mCount));
                mCount++;
                break;
            case (R.id.test_live_region_button2):
                mTextView2.setText(getString(R.string.live_region_announcement_template, mCount));
                mCount++;
                break;
            case (R.id.test_live_region_button3):
                mTextView3.setText(getString(R.string.live_region_announcement_template, mCount));
                mCount++;
                break;
        }
    }
}