/*
 * Copyright (C) 2011 The Android Open Source Project
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

/**
 * Default node processing rule. Returns a content description if available,
 * otherwise returns text.
 */
class RuleDefault implements NodeSpeechRule, NodeHintRule {
    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        return true;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        SpannableStringBuilder output = new SpannableStringBuilder();

        final CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
        final CharSequence roleText = Role.getRoleDescriptionOrDefault(context, node);

        StringBuilderUtils.append(output, nodeText, roleText);

        return output;
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        return NodeHintHelper.getDefaultHintString(context, node);
    }
}
