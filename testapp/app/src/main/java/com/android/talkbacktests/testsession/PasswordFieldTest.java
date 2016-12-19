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
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class PasswordFieldTest extends BaseTestContent {
    private enum TextInputType {
        TEXT_PASSWORD(R.string.input_text_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD),
        TEXT_WEB_PASSWORD(R.string.input_text_web_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
        NUMBER_PASSWORD(R.string.input_number_password,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD),
        TEXT_VISIBLE_PASSWORD(R.string.input_text_visible_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
        DATE(R.string.input_date,
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE),
        PHONE(R.string.input_phone,
                InputType.TYPE_CLASS_PHONE),
        EMAIL_ADDRESS(R.string.input_email_address,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        public final int nameResId;
        public final int id;

        TextInputType(int nameResId, int id) {
            this.nameResId = nameResId;
            this.id = id;
        }
    }

    public PasswordFieldTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_password_field, container, false);
        ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.test_password_field_container);

        for (TextInputType inputType : TextInputType.values()) {
            View child = inflater.inflate(R.layout.test_password_field_item, viewGroup, false);
            TextView textView = (TextView) child.findViewById(R.id.textView);
            EditText editText = (EditText) child.findViewById(R.id.editText);

            String inputTypeString = getString(inputType.nameResId);
            textView.setText(getString(R.string.input_type_description_template, inputTypeString));
            editText.setInputType(inputType.id);
            editText.setHint(inputTypeString);

            viewGroup.addView(child);
        }
        return view;
    }
}