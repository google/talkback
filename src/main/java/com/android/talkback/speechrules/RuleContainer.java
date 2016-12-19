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
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.TabWidget;

import com.android.talkback.R;
import com.android.utils.Role;

/**
 * Formats speech for {@link AbsListView} and {@link TabWidget} widgets.
 */
public class RuleContainer implements NodeSpeechRule {
    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent ev) {
        int role = Role.getRole(node);
        return role == Role.ROLE_GRID || role == Role.ROLE_TAB_BAR || role == Role.ROLE_LIST;
    }

    @Override
    public CharSequence format(
            Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final int childCount = node.getChildCount();
        return formatWithChildren(context, node, childCount);
    }

    private CharSequence formatWithChildren(
            Context context, AccessibilityNodeInfoCompat node, int childCount) {
        final CharSequence roleText = Role.getRoleDescriptionOrDefault(context, node);
        return context.getResources().getQuantityString(R.plurals.template_containers, childCount,
                roleText, childCount);
    }
}
