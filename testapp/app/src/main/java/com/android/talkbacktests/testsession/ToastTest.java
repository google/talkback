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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.talkbacktests.R;

public class ToastTest extends BaseTestContent implements View.OnClickListener {

    private Toast mDefaultToast;
    private Toast mCustomToast;

    public ToastTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_toast, container, false);

        view.findViewById(R.id.test_toast_button1).setOnClickListener(this);
        view.findViewById(R.id.test_toast_button2).setOnClickListener(this);

        mDefaultToast = Toast.makeText(context, R.string.default_toast, Toast.LENGTH_LONG);
        mCustomToast = new Toast(context);
        mCustomToast.setGravity(Gravity.RIGHT, 0, 0);
        mCustomToast.setDuration(Toast.LENGTH_LONG);
        mCustomToast.setView(inflater.inflate(R.layout.custom_toast, null));

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.test_toast_button1:
                mDefaultToast.show();
                break;
            case R.id.test_toast_button2:
                mCustomToast.show();
                break;
        }
    }
}