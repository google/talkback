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
import android.os.Handler;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.talkbacktests.R;

public class StandardAndroidWidgetTest extends BaseTestContent implements View.OnClickListener {
    private ProgressBar mProgressBar;
    private int mProgressStatus;
    private Handler mHandler = new Handler();
    private Runnable mRunnable;
    private int mCount = 0;

    public StandardAndroidWidgetTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, final Context context) {
        View view = inflater.inflate(R.layout.test_standard_android_widget, container, false);
        final Button contDescButton = (Button) view.findViewById(
                R.id.test_standard_android_widget_button2);
        contDescButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCount++;
                contDescButton.setContentDescription(
                        getString(R.string.toast_content_changed_template, mCount));
            }
        });
        Button toastButton = (Button) view.findViewById(R.id.test_standard_android_widget_button3);
        toastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(context, R.string.toast_expand_button_clicked, Toast.LENGTH_LONG)
                        .show();
            }
        });
        ViewCompat.setAccessibilityDelegate(toastButton, new AccessibilityDelegateCompat() {
            public void onInitializeAccessibilityNodeInfo(View host,
                                                          AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        getString(R.string.expand_button_action_label)));
            }
        });

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context,
                R.array.city_array, android.R.layout.simple_spinner_item);

        Spinner spinner = (Spinner) view.findViewById(R.id.test_standard_android_widget_spinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        AutoCompleteTextView autocomplete =
                (AutoCompleteTextView) view.findViewById(
                        R.id.test_standard_android_widget_autocomplete);
        autocomplete.setAdapter(adapter);

        mProgressBar = (ProgressBar) view.findViewById(
                R.id.test_standard_android_widget_progress_bar);
        mRunnable = new Runnable() {

            @Override
            public void run() {
                mProgressStatus += 5;
                mProgressBar.setProgress(mProgressStatus);
                if (mProgressStatus < 100) {
                    mHandler.postDelayed(this, 1000);
                }
            }
        };
        resetProgressBar();

        View resetProgressBarButton = view.findViewById(
                R.id.test_standard_android_widget_reset_button);
        resetProgressBarButton.setOnClickListener(this);

        return view;
    }

    private void resetProgressBar() {
        mHandler.removeCallbacks(mRunnable);
        mProgressStatus = 0;
        mRunnable.run();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.test_standard_android_widget_reset_button) {
            resetProgressBar();
        }
    }
}
