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
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.talkbacktests.R;

public class RoleDescriptionTest extends BaseTestContent {

    public RoleDescriptionTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_role_description, container, false);
        AccessibilityDelegateCompat buttonDelegate = new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                                                          AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setRoleDescription(getString(R.string.role_entry_point));
            }
        };
        View mailButton = view.findViewById(R.id.test_role_description_mailButton);
        mailButton.setContentDescription(getString(R.string.role_send_email));
        ViewCompat.setAccessibilityDelegate(mailButton, buttonDelegate);

        View playButton = view.findViewById(R.id.test_role_description_playButton);
        playButton.setContentDescription(getString(R.string.role_send_email));
        ViewCompat.setAccessibilityDelegate(playButton, buttonDelegate);

        View lockButton = view.findViewById(R.id.test_role_description_lockButton);
        lockButton.setContentDescription(getString(R.string.role_lock_screen));
        ViewCompat.setAccessibilityDelegate(lockButton, buttonDelegate);

        return view;
    }
}