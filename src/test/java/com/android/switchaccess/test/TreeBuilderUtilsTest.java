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

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.AccessibilityNodeActionNode;
import com.android.switchaccess.ClearFocusNode;
import com.android.switchaccess.ContextMenuNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.OptionScanSelectionNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.switchaccess.TreeBuilderUtils;
import com.android.talkback.contextmenu.ContextMenu;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests for TreeBuilderUtils.
 */
@Config(
        emulateSdk = 18,
        shadows = {
        ShadowAccessibilityNodeInfo.class,
        ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class,
        ShadowAccessibilityNodeInfoCompat.class,
        ShadowAccessibilityNodeInfoCompat.ShadowAccessibilityActionCompat.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricTestRunner.class)
public class TreeBuilderUtilsTest {
    OptionScanNode mBaseTree;

    @Before
    public void setUp() {
        mBaseTree = new ClearFocusNode();
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
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
                TreeBuilderUtils.addCompatToTree(compatWithNoActions, mBaseTree);
        compatWithNoActions.recycle();
        assertTrue(resultTree instanceof ClearFocusNode);
    }

    @Test
    public void testAddCompatToTree_clickableNode_shouldAddOneSelectionAndOneActionNode() {
        SwitchAccessNodeCompat compatWithOneAction =
                new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        compatWithOneAction.setClickable(true);
        compatWithOneAction.setVisibleToUser(true);
        OptionScanNode resultTree =
                TreeBuilderUtils.addCompatToTree(compatWithOneAction, mBaseTree);
        compatWithOneAction.recycle();
        OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) resultTree;
        assertEquals(2, selectionNode.getChildCount());
        assertTrue(selectionNode.getChild(0) instanceof AccessibilityNodeActionNode);
        assertTrue(selectionNode.getChild(1) instanceof ClearFocusNode);
        resultTree.recycle();
    }

    @Test
    public void testAddCompatToTree_scrollableNode_shouldAddContextMenu() {
        SwitchAccessNodeCompat compatWithScrollActions =
                new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        compatWithScrollActions.setScrollable(true);
        compatWithScrollActions.setVisibleToUser(true);
        OptionScanNode resultTree =
                TreeBuilderUtils.addCompatToTree(compatWithScrollActions, mBaseTree);
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
                TreeBuilderUtils.addCompatToTree(invisibleCompat, mBaseTree);
        invisibleCompat.recycle();
        assertTrue(resultTree instanceof ClearFocusNode);
    }
}
