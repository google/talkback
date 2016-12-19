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

package com.android.talkback.speechrules;

import com.google.android.marvin.talkback.TalkBackService;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.Role;

/**
 * Rule for processing ViewPagers, providing different feedback for pagers with multiple pages
 * and those with only single pages.
 */
public class RulePager extends RuleDefault {

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        return Role.getRole(node) == Role.ROLE_PAGER;
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        if (hasMultiplePages(node)) {
            TalkBackService talkBack = TalkBackService.getInstance();
            if (talkBack == null || talkBack.isDeviceTelevision()) {
                return super.getHintText(context, node);
            } else {
                return context.getString(R.string.template_hint_pager);
            }
        } else {
            return context.getString(R.string.template_hint_pager_single_page);
        }
    }

    private static boolean hasMultiplePages(AccessibilityNodeInfoCompat node) {
        return node != null && AccessibilityNodeInfoUtils.supportsAnyAction(node,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
    }

}
