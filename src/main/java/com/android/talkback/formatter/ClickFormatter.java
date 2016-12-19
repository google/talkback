/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.talkback.formatter;

import com.android.talkback.FeedbackItem;
import com.google.android.marvin.talkback.TalkBackService;

import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;
import com.android.talkback.Utterance;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.NodeFilter;
import com.android.utils.Role;

/**
 * Formats feedback for clicking on nodes. Provides additional feedback on state change if the
 * clicked object is a checkable, or if it has a checkable descendant.
 */
public class ClickFormatter implements EventSpeechRule.AccessibilityEventFormatter {

    public static final int MIN_API_LEVEL = Build.VERSION_CODES.LOLLIPOP;

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        AccessibilityNodeInfoCompat source = record.getSource();
        AccessibilityNodeInfoCompat refreshedSource =
                AccessibilityNodeInfoUtils.refreshNode(source);

        try {
            if (refreshedSource == null) {
                // Node no longer exists.
                return false;
            }

            // Is source directly checkable?
            if (refreshedSource.isCheckable()) {
                utterance.addSpoken(getStateText(refreshedSource, context));
                return true;
            }

            // Does source contain non-focusable checkable child?
            if (refreshedSource.isAccessibilityFocused() || refreshedSource.isFocused()) {
                AccessibilityNodeInfoCompat checkableChild = findCheckableChild(refreshedSource);
                if (checkableChild != null) {
                    utterance.addSpoken(getStateText(checkableChild, context));
                    checkableChild.recycle();
                    return true;
                }
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source, refreshedSource);
        }

        utterance.addSpokenFlag(FeedbackItem.FLAG_NO_SPEECH);
        return true;
    }

    private AccessibilityNodeInfoCompat findCheckableChild(AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.getSelfOrMatchingDescendant(node, new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return node.isCheckable();
            }
        });
    }

    private String getStateText(AccessibilityNodeInfoCompat node, Context context) {
        switch (Role.getRole(node)) {
            case Role.ROLE_SWITCH:
            case Role.ROLE_TOGGLE_BUTTON:
                if (node.isChecked()) {
                    return context.getString(R.string.value_on);
                } else {
                    return context.getString(R.string.value_off);
                }
            default:
                if (node.isChecked()) {
                    return context.getString(R.string.value_checked);
                } else {
                    return context.getString(R.string.value_not_checked);
                }
        }
    }
}
