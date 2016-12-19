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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.android.talkbacktests.R;

public class SecurityOverlayTest extends BaseTestContent implements View.OnClickListener {
    private static final int REQUEST_CODE = 110;

    public SecurityOverlayTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_security_overlay, container, false);
        Button cameraButton = (Button) view.findViewById(R.id.test_security_overlay_button1);
        cameraButton.setText(getString(R.string.request_permission_template,
                getString(R.string.permission_name_camera)));
        cameraButton.setOnClickListener(this);
        Button calendarButton = (Button) view.findViewById(R.id.test_security_overlay_button2);
        calendarButton.setText(getString(R.string.request_permission_template,
                getString(R.string.permission_name_write_calendar)));
        calendarButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.test_security_overlay_button1:
                requestCameraPermission();
                break;
            case R.id.test_security_overlay_button2:
                requestCalendarPermission();
                break;
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    (Activity) getContext(),
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE);
        } else {
            Toast.makeText(
                    getContext(),
                    getString(R.string.permission_already_granted_template,
                            getString(R.string.permission_name_camera)),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestCalendarPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CALENDAR) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    (Activity) getContext(),
                    new String[]{Manifest.permission.WRITE_CALENDAR},
                    REQUEST_CODE);
        } else {
            Toast.makeText(
                    getContext(),
                    getString(R.string.permission_already_granted_template,
                            getString(R.string.permission_name_write_calendar)),
                    Toast.LENGTH_LONG).show();
        }
    }
}