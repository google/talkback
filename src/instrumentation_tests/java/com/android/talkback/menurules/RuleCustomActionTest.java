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

package com.android.talkback.menurules;

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.talkback.R;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ListMenu;

import com.google.android.marvin.talkback.TalkBackService;

import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import java.util.List;

public class RuleCustomActionTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);
    }

    @MediumTest
    public void testNodeWithCustomActions_shouldHaveOwnActions() throws Throwable {
        if (Build.VERSION.SDK_INT < RuleCustomAction.MIN_API_LEVEL) {
            return;
        }

        setContentView(R.layout.nested_views);

        View button = getActivity().findViewById(R.id.nested_button_1);
        button.setAccessibilityDelegate(new AccessibilityDelegate1());

        mTalkBack.getCursorController().setCursor(getNodeForId(R.id.nested_button_1));
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat buttonInfo = getNodeForId(R.id.nested_button_1);
        waitForAccessibilityIdleSync();

        RuleCustomAction customAction = new RuleCustomAction();

        ListMenu listMenu = new ListMenu(mTalkBack);
        List<ContextMenuItem> items = customAction.getMenuItemsForNode(mTalkBack,
                listMenu.getMenuItemBuilder(), buttonInfo);

        assertEquals(2, items.size());
        assertHasItemWithTitle(items, "CA1");
        assertHasItemWithTitle(items, "CA2");
    }

    @MediumTest
    public void testNodeWithoutCustomActions_shouldInheritParentActions() throws Throwable {
        if (Build.VERSION.SDK_INT < RuleCustomAction.MIN_API_LEVEL) {
            return;
        }

        setContentView(R.layout.nested_views);

        View parentFrame = getActivity().findViewById(R.id.parent_frame);
        parentFrame.setAccessibilityDelegate(new AccessibilityDelegate1());

        mTalkBack.getCursorController().setCursor(getNodeForId(R.id.nested_button_1));
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat buttonInfo = getNodeForId(R.id.nested_button_1);
        waitForAccessibilityIdleSync();

        RuleCustomAction customAction = new RuleCustomAction();

        ListMenu listMenu = new ListMenu(mTalkBack);
        List<ContextMenuItem> items = customAction.getMenuItemsForNode(mTalkBack,
                listMenu.getMenuItemBuilder(), buttonInfo);

        assertEquals(2, items.size());
        assertHasItemWithTitle(items, "CA1");
        assertHasItemWithTitle(items, "CA2");
    }

    @MediumTest
    public void testNodeWithCustomActions_shouldNotInheritParentActions() throws Throwable {
        if (Build.VERSION.SDK_INT < RuleCustomAction.MIN_API_LEVEL) {
            return;
        }

        setContentView(R.layout.nested_views);

        View parentFrame = getActivity().findViewById(R.id.parent_frame);
        parentFrame.setAccessibilityDelegate(new AccessibilityDelegate1());
        View button = getActivity().findViewById(R.id.nested_button_1);
        button.setAccessibilityDelegate(new AccessibilityDelegate2());

        mTalkBack.getCursorController().setCursor(getNodeForId(R.id.nested_button_1));
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat buttonInfo = getNodeForId(R.id.nested_button_1);
        waitForAccessibilityIdleSync();

        RuleCustomAction customAction = new RuleCustomAction();

        ListMenu listMenu = new ListMenu(mTalkBack);
        List<ContextMenuItem> items = customAction.getMenuItemsForNode(mTalkBack,
                listMenu.getMenuItemBuilder(), buttonInfo);

        assertEquals(2, items.size());
        assertHasItemWithTitle(items, "CA3");
        assertHasItemWithTitle(items, "CA4");
    }

    @MediumTest
    public void testNodeWithoutCustomActions_shouldInheritHierarchyActions() throws Throwable {
        if (Build.VERSION.SDK_INT < RuleCustomAction.MIN_API_LEVEL) {
            return;
        }

        setContentView(R.layout.nested_views);

        View parentFrame = getActivity().findViewById(R.id.parent_frame);
        parentFrame.setAccessibilityDelegate(new AccessibilityDelegate1());
        View nestedFrame = getActivity().findViewById(R.id.nested_frame);
        nestedFrame.setAccessibilityDelegate(new AccessibilityDelegate2());

        mTalkBack.getCursorController().setCursor(getNodeForId(R.id.nested_button_2));
        waitForAccessibilityIdleSync();

        AccessibilityNodeInfoCompat buttonInfo = getNodeForId(R.id.nested_button_2);
        waitForAccessibilityIdleSync();

        RuleCustomAction customAction = new RuleCustomAction();

        ListMenu listMenu = new ListMenu(mTalkBack);
        List<ContextMenuItem> items = customAction.getMenuItemsForNode(mTalkBack,
                listMenu.getMenuItemBuilder(), buttonInfo);

        assertEquals(2, items.size());
        assertHasItemWithTitle(items, "CA3");
        assertHasItemWithTitle(items, "CA4");
    }

    private void assertHasItemWithTitle(List<ContextMenuItem> items, String title) {
        if (Build.VERSION.SDK_INT < RuleCustomAction.MIN_API_LEVEL) {
            return;
        }

        boolean itemFound = false;
        for (ContextMenuItem item : items) {
            if (item.getTitle().equals(title)) {
                itemFound = true;
                break;
            }
        }

        assertTrue(itemFound);
    }

    private class AccessibilityDelegate1 extends AccessibilityDelegate {

        @Override
        public void onInitializeAccessibilityNodeInfo(View host,
                AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.addAction(new AccessibilityAction(R.id.custom_action_1, "CA1"));
            info.addAction(new AccessibilityAction(R.id.custom_action_2, "CA2"));
        }

    }

    private class AccessibilityDelegate2 extends AccessibilityDelegate {

        @Override
        public void onInitializeAccessibilityNodeInfo(View host,
                                                      AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.addAction(new AccessibilityAction(R.id.custom_action_3, "CA3"));
            info.addAction(new AccessibilityAction(R.id.custom_action_4, "CA4"));
        }

    }

    private class AccessibilityDelegate3 extends AccessibilityDelegate {

        @Override
        public void onInitializeAccessibilityNodeInfo(View host,
                                                      AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.addAction(new AccessibilityAction(R.id.custom_action_5, "CA5"));
        }

    }

}
