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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class SimpleTextFragment extends Fragment {
    private String mText = "";

    public static SimpleTextFragment create(String text) {
        SimpleTextFragment instance = new SimpleTextFragment();
        instance.setText(text);
        return instance;
    }

    public void setText(String text) {
        if (text != null) {
            mText = text;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View view = inflater.inflate(R.layout.fragment_simple_text, container, false);
        TextView textView = (TextView) view.findViewById(R.id.textview);
        textView.setText(mText);

        return view;
    }
}
