/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.talkback.menurules;

import android.view.Menu;
import com.android.talkback.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.TextUtils;
import android.view.MenuItem;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ContextMenuItemBuilder;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.PerformActionUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Adds custom actions to the local context menu.
 */
// TODO(KM): Update this to Build.VERSION_CODES.L once it is available
@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class RuleCustomAction implements NodeMenuRule {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.KITKAT_WATCH;

    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        List<AccessibilityActionCompat> actions = node.getActionList();
        return actions != null && !actions.isEmpty();
    }

    @Override
    public List<ContextMenuItem> getMenuItemsForNode(TalkBackService service,
                     ContextMenuItemBuilder menuItemBuilder, AccessibilityNodeInfoCompat node) {
        List<ContextMenuItem> menu = new LinkedList<>();

        for (AccessibilityActionCompat action : AccessibilityNodeInfoUtils.getCustomActions(node)) {
            CharSequence label = action.getLabel();
            int id = action.getId();
            if (TextUtils.isEmpty(label)) {
                continue;
            }

            ContextMenuItem item = menuItemBuilder.createMenuItem(service, Menu.NONE, id, Menu.NONE,
                    label);
            item.setOnMenuItemClickListener(new CustomMenuItem(id,
                    AccessibilityNodeInfoCompat.obtain(node)));
            item.setCheckable(false);
            menu.add(item);
        }

        return menu;
    }

    @Override
    public boolean canCollapseMenu() {
        return true;
    }

    @Override
    public CharSequence getUserFriendlyMenuName(Context context) {
        return context.getString(R.string.title_custom_action);
    }

    private static class CustomMenuItem implements MenuItem.OnMenuItemClickListener {
        final int mId;
        final AccessibilityNodeInfoCompat mNode;

        CustomMenuItem(int id, AccessibilityNodeInfoCompat node) {
            mId = id;
            mNode = node;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            boolean ret = PerformActionUtils.performAction(mNode, mId);
            mNode.recycle();
            return ret;
        }
    }
}
