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
import android.widget.EditText;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class EditFieldTest extends BaseTestContent {

    private final String mYes;
    private final String mNo;

    public EditFieldTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
        mYes = getString(R.string.yes);
        mNo = getString(R.string.no);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_edit_field, container, false);
        ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.test_edit_field_container);

        for (int flag = 0; flag < 8; flag++) {
            View child = inflater.inflate(R.layout.test_password_field_item, viewGroup, false);
            TextView textView = (TextView) child.findViewById(R.id.textView);
            EditText editText = (EditText) child.findViewById(R.id.editText);

            textView.setText(
                    getString(R.string.edit_field_introduction,
                            (flag & (1 << 0)) == 0 ? mNo : mYes,
                            (flag & (1 << 1)) == 0 ? mNo : mYes,
                            (flag & (1 << 2)) == 0 ? mNo : mYes));

            if ((flag & (1 << 0)) != 0) editText.setText(R.string.edit_field_default_text);
            if ((flag & (1 << 1)) != 0)
                editText.setContentDescription(getString(R.string.edit_field_default_content_description));
            if ((flag & (1 << 2)) != 0) editText.setHint(R.string.edit_field_default_hint);

            viewGroup.addView(child);
        }

        return view;
    }
}