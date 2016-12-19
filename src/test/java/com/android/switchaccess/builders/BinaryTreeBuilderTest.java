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

package com.android.switchaccess.builders;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import com.android.switchaccess.ClearFocusNode;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.ContextMenuNode;
import com.android.switchaccess.GlobalActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.OptionScanSelectionNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.talkback.BuildConfig;
import com.android.switchaccess.SwitchAccessWindowInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.treebuilding.BinaryTreeBuilder;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests for BinaryTreeBuilder
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class BinaryTreeBuilderTest {
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    BinaryTreeBuilder mBinaryTreeBuilder;
    OptionScanNode mBaseTree;

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        mBaseTree = new ClearFocusNode();
        mBinaryTreeBuilder = new BinaryTreeBuilder(mContext) {
            @Override
            public OptionScanNode addViewHierarchyToTree(SwitchAccessNodeCompat node,
                    OptionScanNode treeToBuildOn) {
                return null;
            }

            @Override
            public OptionScanNode addWindowListToTree(List<SwitchAccessWindowInfo> windowList,
                    OptionScanNode treeToBuildOn) {
                return null;
            }
        };
    }

    @After
    public void tearDown() {
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
    }

    @Test
    public void testAddCompatToTree_nodeWithNoActions_shouldAddNothing() {
        SwitchAccessNodeCompat compatWithNoActions =
                new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        compatWithNoActions.setClickable(false);
        OptionScanNode resultTree =
                mBinaryTreeBuilder.addCompatToTree(compatWithNoActions, mBaseTree);
        compatWithNoActions.recycle();
        assertTrue(resultTree instanceof ClearFocusNode);
    }

    @Test
    public void testAddCompatToTree_scrollableNode_shouldAddContextMenu() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.setVisibleToUser(true);

        SwitchAccessNodeCompat compatWithScrollActions = new SwitchAccessNodeCompat(info);
        OptionScanNode resultTree =
                mBinaryTreeBuilder.addCompatToTree(compatWithScrollActions, mBaseTree);
        OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) resultTree;
        compatWithScrollActions.recycle();
        assertEquals(2, selectionNode.getChildCount());
        assertTrue(selectionNode.getChild(0) instanceof ContextMenuNode);
        assertTrue(selectionNode.getChild(1) instanceof ClearFocusNode);
        resultTree.recycle();
    }

    @Test
    public void testAddCompatToTree_invisibleNode_shouldAddNothing() {
        SwitchAccessNodeCompat invisibleCompat =
                new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        invisibleCompat.setScrollable(true);
        invisibleCompat.setVisibleToUser(false);
        OptionScanNode resultTree =
                mBinaryTreeBuilder.addCompatToTree(invisibleCompat, mBaseTree);
        invisibleCompat.recycle();
        assertTrue(resultTree instanceof ClearFocusNode);
    }

    @Test
    public void testBuildContextMenuTreeWithNullList_shouldHaveClearFocusNode() {
        OptionScanNode node = mBinaryTreeBuilder.buildContextMenu(null);
        Assert.assertTrue(node instanceof ClearFocusNode);
        node.recycle();
    }

    @Test
    public void testBuildContextMenuTreeWithTwoItems_shouldHaveExpectedStructure() {
        CharSequence globalActionLabel0 = "global action label 0";
        CharSequence globalActionLabel1 = "global action label 1";
        ContextMenuItem globalNode0 = new GlobalActionNode(0, null, globalActionLabel0);
        ContextMenuItem globalNode1 = new GlobalActionNode(1, null, globalActionLabel1);

        ContextMenuNode contextMenuTree = (ContextMenuNode) mBinaryTreeBuilder
                .buildContextMenu(Arrays.asList(globalNode0, globalNode1));
        Assert.assertEquals(2, contextMenuTree.getChildCount());
        GlobalActionNode firstActionNode = (GlobalActionNode) contextMenuTree.getChild(0);
        Assert.assertTrue(TextUtils.equals(globalActionLabel0,
                firstActionNode.getActionLabel(mContext)));

        ContextMenuNode secondLevelOfMenu = (ContextMenuNode) contextMenuTree.getChild(1);
        Assert.assertEquals(2, secondLevelOfMenu.getChildCount());
        GlobalActionNode secondActionNode = (GlobalActionNode) secondLevelOfMenu.getChild(0);
        Assert.assertTrue(TextUtils.equals(globalActionLabel1,
                secondActionNode.getActionLabel(mContext)));

        Assert.assertTrue(secondLevelOfMenu.getChild(1) instanceof ClearFocusNode);
        contextMenuTree.recycle();
    }
}
