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

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.Role;
import com.android.utils.StringBuilderUtils;

/** Rule for processing pages of ViewPagers. */
public class RulePagerPage implements NodeSpeechRule {

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        if (node != null) {
            AccessibilityNodeInfoCompat parent = node.getParent();
            if (parent != null) {
                try {
                    int visibleChildren = 0;
                    int totalChildren = parent.getChildCount();
                    for (int i = 0; i < totalChildren; ++i) {
                        AccessibilityNodeInfoCompat child = parent.getChild(i);
                        if (child != null) {
                            if (child.isVisibleToUser()) {
                                visibleChildren++;
                            }
                            child.recycle();
                        }
                    }
                    return Role.getRole(parent) == Role.ROLE_PAGER && visibleChildren == 1;
                } finally {
                    parent.recycle();
                }
            }
        }

        return false;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
                               AccessibilityEvent event) {
        SpannableStringBuilder output = new SpannableStringBuilder();

        final CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
        final CharSequence roleText;
        if (node.getRoleDescription() != null) {
            roleText = node.getRoleDescription();
        } else {
            roleText = context.getString(R.string.value_pager_page);
        }

        StringBuilderUtils.append(output, nodeText, roleText);

        return output;
    }

}
