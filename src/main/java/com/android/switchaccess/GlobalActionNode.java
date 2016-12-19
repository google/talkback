/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;

import com.android.talkback.R;

import java.util.*;

/**
 * Option scan node that performs a global action
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class GlobalActionNode extends OptionScanActionNode {

    private int mAction;
    private CharSequence mActionLabel;
    protected AccessibilityService mService;

    public static List<ContextMenuItem> getGlobalActionList(AccessibilityService service) {
        List<ContextMenuItem> globalActionList = new ArrayList<>();
        globalActionList.add(new GlobalActionNode(AccessibilityService.GLOBAL_ACTION_BACK,
                service, service.getResources().getString(R.string.global_action_back)));
        globalActionList.add(new GlobalActionNode(AccessibilityService.GLOBAL_ACTION_HOME,
                service, service.getResources().getString(R.string.global_action_home)));
        globalActionList.add(new GlobalActionNode(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                service, service.getResources().getString(R.string.global_action_notifications)));
        globalActionList.add(new GlobalActionNode(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
                service, service.getResources().getString(R.string.global_action_quick_settings)));
        globalActionList.add(new GlobalActionNode(AccessibilityService.GLOBAL_ACTION_RECENTS,
                service, service.getResources().getString(R.string.global_action_overview)));

        /* Controls auto-select behavior from menu */
        globalActionList.add(new GlobalActionNode(0, service, null) {
            @Override
            public CharSequence getActionLabel(Context context) {
                /* Return the opposite of state of preference */
                boolean isAutoSelectOn =
                        SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(this.mService);
                return context.getString(isAutoSelectOn
                        ? R.string.switch_access_global_menu_disable_autoselect
                        : R.string.switch_access_global_menu_enable_autoselect);
            }

            @Override
            public void performAction() {
                /* Toggle preference */
                SwitchAccessPreferenceActivity.setGlobalMenuAutoselectOn(this.mService,
                        !SwitchAccessPreferenceActivity
                                .isGlobalMenuAutoselectOn(this.mService));
            }
        });
        return globalActionList;
    }

    public GlobalActionNode(int action, AccessibilityService service,
            CharSequence localizedActionLabel) {
        mAction = action;
        mActionLabel = localizedActionLabel;
        mService = service;
    }

    @Override
    public CharSequence getActionLabel(Context context) {
        return mActionLabel;
    }

    @Override
    public void performAction() {
        mService.performGlobalAction(mAction);
    }

    @Override
    public Set<Rect> getRectsForNodeHighlight() {
        /* The action is never shown directly on the screen */
        return Collections.emptySet();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GlobalActionNode)) {
            return false;
        }
        GlobalActionNode otherNode = (GlobalActionNode) other;
        return otherNode.mAction == mAction;
    }
}