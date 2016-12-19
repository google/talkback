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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.GlobalActionNode;
import com.android.switchaccess.SwitchAccessPreferenceActivity;
import com.android.switchaccess.SwitchAccessService;
import com.android.talkback.BuildConfig;
import com.android.talkback.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.util.List;


/**
 * Test for GlobalActionNode
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityService.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class GlobalActionNodeTest {
    private static int NUM_GLOBAL_ACTIONS = 5;
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
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem back = getNodeInListWithLabel(list,
                mContext.getString(R.string.global_action_back), mContext);
        back.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_BACK));
    }

    @Test
    public void getGlobalActionList_hasHomeButton() {
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem home = getNodeInListWithLabel(list,
                mContext.getString(R.string.global_action_home), mContext);
        home.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_HOME));
    }

    @Test
    public void getGlobalActionList_hasNotificationsButton() {
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem notifications = getNodeInListWithLabel(list,
                mContext.getString(R.string.global_action_notifications), mContext);
        notifications.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
    }

    @Test
    public void getGlobalActionList_hasRecentsButton() {
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem recents = getNodeInListWithLabel(list,
                mContext.getString(R.string.global_action_overview), mContext);
        recents.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_RECENTS));
    }

    @Test
    public void getGlobalActionList_hasQuickSettingsButton() {
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem quickSettings = getNodeInListWithLabel(list,
                mContext.getString(R.string.global_action_quick_settings), mContext);
        quickSettings.performAction();
        List<Integer> globalActions = mShadowService.getGlobalActionsPerformed();
        assertEquals(1, globalActions.size());
        assertTrue(globalActions.contains(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS));
    }

    @Test
    public void getGlobalActionList_autoselectUsingMenuWithAutoSelectOff_enableAutoselectButton() {
        setAutoSelectGlobalMenuPref(R.string.switch_access_choose_action_show_menu_key);
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        assertEquals(NUM_GLOBAL_ACTIONS + 1, list.size());
        GlobalActionNode enableAutoSelectNode = (GlobalActionNode) list.get(NUM_GLOBAL_ACTIONS);
        assertEquals(mContext.getString(R.string.switch_access_global_menu_enable_autoselect),
                enableAutoSelectNode.getActionLabel(mContext));
    }

    @Test
    public void enableAutoSelectNode_enablesAutoselect() {
        setAutoSelectGlobalMenuPref(R.string.switch_access_choose_action_show_menu_key);
        assertFalse(SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext));
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem enableAutoSelectNode = list.get(NUM_GLOBAL_ACTIONS);
        enableAutoSelectNode.performAction();
        assertTrue(SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext));
    }

    @Test
    public void getGlobalActionList_autoselectUsingMenuWithAutoSelectOn_disableAutoselectButton() {
        setAutoSelectGlobalMenuPref(R.string.switch_access_choose_action_auto_select_key);
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        assertEquals(NUM_GLOBAL_ACTIONS + 1, list.size());
        GlobalActionNode disableAutoSelectNode = (GlobalActionNode) list.get(NUM_GLOBAL_ACTIONS);
        assertEquals(mContext.getString(R.string.switch_access_global_menu_disable_autoselect),
                disableAutoSelectNode.getActionLabel(mContext));
    }

    @Test
    public void disableAutoSelectNode_enablesAutoselect() {
        setAutoSelectGlobalMenuPref(R.string.switch_access_choose_action_auto_select_key);
        assertTrue(SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext));
        List<ContextMenuItem> list = GlobalActionNode.getGlobalActionList(mSwitchControlService);
        ContextMenuItem enableAutoSelectNode = list.get(NUM_GLOBAL_ACTIONS);
        enableAutoSelectNode.performAction();
        assertFalse(SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext));
    }

    @Test
    public void getRects_returnsEmptySet() {
        ContextMenuItem globalActionNode = new GlobalActionNode(0, mSwitchControlService, "a");
        assertTrue(globalActionNode.getRectsForNodeHighlight().isEmpty());
    }

    private GlobalActionNode getNodeInListWithLabel(List<ContextMenuItem> list,
            CharSequence label, Context context) {
        for (ContextMenuItem contextMenuItem : list) {
            GlobalActionNode action = (GlobalActionNode) contextMenuItem;
            if (TextUtils.equals(label, action.getActionLabel(context))) {
                return action;
            }
        }
        return null;
    }

    private void setAutoSelectGlobalMenuPref(int newValueResourceId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putString(
                mContext.getString(R.string.switch_access_choose_action_global_menu_behavior_key),
                mContext.getString(newValueResourceId)).apply();
    }
}
