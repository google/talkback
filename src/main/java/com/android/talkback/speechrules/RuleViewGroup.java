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
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;

/**
 * Formats speech for ViewGroup widgets.
 */
public class RuleViewGroup implements NodeSpeechRule {
    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ViewGroup.class);
    }

    @Override
    public CharSequence format(
            Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);

        if (!TextUtils.isEmpty(nodeText)) {
            LogUtils.log(this, Log.VERBOSE, "Using node text: %s", nodeText);
            return nodeText;
        }

        // Don't use event text for ViewGroup, since this is automatically
        // populated with child text.

        return null;
    }

}
