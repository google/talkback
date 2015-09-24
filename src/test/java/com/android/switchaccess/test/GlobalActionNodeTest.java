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

package com.android.switchaccess.test;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import com.android.switchaccess.GlobalActionNode;
import com.android.switchaccess.SwitchAccessService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for GlobalActionNode
 */
@Config(
        emulateSdk = 18,
        shadows = {
                ShadowAccessibilityService.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricTestRunner.class)
public class GlobalActionNodeTest {
    private SwitchAccessService mSwitchControlService = new SwitchAccessService();
    private ShadowAccessibilityService mShadowService;
    private Context mContext = RuntimeEnvironment.application.getApplicationContext();

    @Before
    public void setUp() {
        mShadowService =
                (ShadowAccessibilityService) ShadowExtractor.extract(mSwitchControlService);
    }

    @Test
    public void getGlobalActionList_hasBackButton() {
        List<GlobalActionNode> list = GlobalActionNode
                .getGlobalActionList(mSwitchControlService);
        GlobalActionNode back = getNodeInListWithLabel(list, "Back", mContext);
        back.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_BACK));
    }

    @Test
    public void getGlobalActionList_hasHomeButton() {
        List<GlobalActionNode> list = GlobalActionNode
                .getGlobalActionList(mSwitchControlService);
        GlobalActionNode back = getNodeInListWithLabel(list, "Home", mContext);
        back.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_HOME));
    }

    @Test
    public void getGlobalActionList_hasNotificationsButton() {
        List<GlobalActionNode> list = GlobalActionNode
                .getGlobalActionList(mSwitchControlService);
        GlobalActionNode back = getNodeInListWithLabel(list, "Notifications", mContext);
        back.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
    }

    @Test
    public void getGlobalActionList_hasRecentsButton() {
        List<GlobalActionNode> list = GlobalActionNode
                .getGlobalActionList(mSwitchControlService);
        GlobalActionNode back = getNodeInListWithLabel(list, "Overview", mContext);
        back.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_RECENTS));
    }

    @Test
    public void getGlobalActionList_hasQuickSettingsButton() {
        List<GlobalActionNode> list = GlobalActionNode
                .getGlobalActionList(mSwitchControlService);
        GlobalActionNode back = getNodeInListWithLabel(list, "Quick Settings", mContext);
        back.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS));
    }

    @Test
    public void getRects_returnsEmptySet() {
        GlobalActionNode globalActionNode = new GlobalActionNode(0, mSwitchControlService, "a");
        assertTrue(globalActionNode.getRectsForNodeHighlight().isEmpty());
    }

    private GlobalActionNode getNodeInListWithLabel(List<GlobalActionNode> list,
            CharSequence label, Context context) {
        for (GlobalActionNode action : list) {
            if (TextUtils.equals(label, action.getActionLabel(context))) {
                return action;
            }
        }
        return null;
    }
}
