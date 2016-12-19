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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.android.talkbacktests.R;

public class FocusSynchronizationTest extends BaseTestContent implements View.OnClickListener {

    private AlertDialog mDialog;

    public FocusSynchronizationTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_focus_synchronization, container, false);
        view.findViewById(R.id.test_focus_synchronization_button1).setOnClickListener(this);
        view.findViewById(R.id.test_focus_synchronization_button2).setOnClickListener(this);
        view.findViewById(R.id.test_focus_synchronization_editText).requestFocus();
        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.test_focus_synchronization_button1:
                showFirstAlert();
                break;
            case R.id.test_focus_synchronization_button2:
                showSecondAlert();
                break;
        }
    }

    private void showFirstAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(LayoutInflater.from(getContext())
                .inflate(R.layout.test_focus_synchronization_dialog, null))
                .setPositiveButton(R.string.alert_signin_button, null)
                .setNegativeButton(R.string.alert_cancel_button, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                EditText password = (EditText) dialog.findViewById(R.id.alertPassword);
                if (password != null) {
                    password.requestFocus();
                }
            }
        });
        dialog.show();
    }

    private void showSecondAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(LayoutInflater.from(getContext())
                .inflate(R.layout.test_focus_synchronization_dialog2, null))
                .setPositiveButton(R.string.alert_ok_button, null);
        builder.create().show();
    }
}