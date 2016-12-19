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
import android.os.Bundle;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

import com.android.talkbacktests.R;

public class DismissibleViewTest extends BaseTestContent implements View.OnClickListener {

    private View mDismissibleView;
    private LinearLayout mContainer;

    public DismissibleViewTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_dismissible_view, container, false);

        view.findViewById(R.id.test_dismissible_view_dismiss_button).setOnClickListener(this);
        view.findViewById(R.id.test_dismissible_view_reset_button).setOnClickListener(this);

        mContainer = (LinearLayout) view.findViewById(R.id.test_dismissible_view_container);
        mDismissibleView = view.findViewById(R.id.dismissible_view);
        ViewCompat.setAccessibilityDelegate(mDismissibleView, new MyDelegate());

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.test_dismissible_view_dismiss_button:
                if (mContainer.getChildCount() > 1) {
                    mDismissibleView.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_DISMISS,
                            null);
                }
                break;
            case R.id.test_dismissible_view_reset_button:
                if (mContainer.getChildCount() < 2) {
                    mContainer.addView(mDismissibleView);
                }
                break;
        }
    }

    private final class MyDelegate extends AccessibilityDelegateCompat {

        @Override
        public void onInitializeAccessibilityNodeInfo(View host,
                                                      AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.addAction(AccessibilityNodeInfoCompat.ACTION_DISMISS);
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_DISMISS:
                    mContainer.removeView(mDismissibleView);
                    return true;
                default:
                    return super.performAccessibilityAction(host, action, args);
            }
        }
    }
}