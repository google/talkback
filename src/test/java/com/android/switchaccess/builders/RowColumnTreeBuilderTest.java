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
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.switchaccess.AccessibilityNodeActionNode;
import com.android.switchaccess.ClearFocusNode;
import com.android.switchaccess.ContextMenuNode;
import com.android.switchaccess.GlobalActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.OptionScanSelectionNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.test.SwitchAccessNodeCompatTest;
import com.android.switchaccess.treebuilding.RowColumnTreeBuilder;
import com.android.talkback.BuildConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric tests for RowColumnTreeBuilder
 */
@Config(
        constants = BuildConfig.class,
        manifest = Config.NONE,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class RowColumnTreeBuilderTest {
    /*
     * We build a simple tree of AccessibilityNodeInfoCompats
     *
     *                     root0
     *                      /  \
     *                    n0   n1_____
     *                        /  \    \
     *                      n10  n11  n12
     * All are clickable.
     */
    private SwitchAccessNodeCompat mRoot0, mN0, mN1, mN10, mN11, mN12;

    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final RowColumnTreeBuilder mRowColumnTreeBuilder = new RowColumnTreeBuilder(mContext);

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        /* Build accessibility node tree */
        mRoot0 = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        mRoot0.setContentDescription("root0");

        AccessibilityNodeInfo n0Info = AccessibilityNodeInfo.obtain();
        n0Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN0 = new SwitchAccessNodeCompat(n0Info);
        mN0.setContentDescription("mN0");
        mN0.setVisibleToUser(true);
        mN0.setClickable(true);

        AccessibilityNodeInfo n1Info = AccessibilityNodeInfo.obtain();
        n1Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN1 = new SwitchAccessNodeCompat(n1Info);
        mN1.setContentDescription("mN1");
        mN1.setVisibleToUser(true);
        mN1.setClickable(true);

        AccessibilityNodeInfo n10Info = AccessibilityNodeInfo.obtain();
        n10Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN10 = new SwitchAccessNodeCompat(n10Info);
        mN10.setContentDescription("mN10");
        mN10.setVisibleToUser(true);
        mN10.setClickable(true);

        AccessibilityNodeInfo n11Info = AccessibilityNodeInfo.obtain();
        n11Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN11 = new SwitchAccessNodeCompat(n11Info);
        mN11.setVisibleToUser(true);
        mN11.setClickable(true);
        mN11.setContentDescription("mN11");

        AccessibilityNodeInfo n12Info = AccessibilityNodeInfo.obtain();
        n12Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN12 = new SwitchAccessNodeCompat(n12Info);
        mN12.setVisibleToUser(true);
        mN12.setClickable(true);
        mN12.setContentDescription("mN12");

        mRoot0.setClickable(false);
        mRoot0.setFocusable(false);
        ShadowAccessibilityNodeInfo shadowRoot =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mRoot0.getInfo());
        shadowRoot.addChild((AccessibilityNodeInfo) mN0.getInfo());
        shadowRoot.addChild((AccessibilityNodeInfo) mN1.getInfo());

        final ShadowAccessibilityNodeInfo shadowN1 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN1.getInfo());
        shadowN1.addChild((AccessibilityNodeInfo) mN10.getInfo());
        shadowN1.addChild((AccessibilityNodeInfo) mN11.getInfo());
        shadowN1.addChild((AccessibilityNodeInfo) mN12.getInfo());
    }

    @After
    public void tearDown() {
        mRoot0.recycle();
        mN0.recycle();
        mN1.recycle();
        mN10.recycle();
        mN11.recycle();
        mN12.recycle();
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test
    public void buildTreeWithNoActions_treeHasOnlyClearFocusNode() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setVisibleToUser(true);
        info.setContentDescription("buildTreeWithNoActions_treeHasOnlyClearFocusNode info");
        OptionScanNode tree = mRowColumnTreeBuilder
                .addViewHierarchyToTree(new SwitchAccessNodeCompat(info), null);
        assertTrue(tree instanceof ClearFocusNode);
        info.recycle();
        tree.recycle();
    }

    @Test
    public void buildTreeWithTwoRows_hasExpectedStructure() {
        /*
         * n0, n10, and n11 have the same y coordinate above. n1. n1 and n12 are
         * lower and themselves have the same y coordinate.
         */
        Rect N0_BOUNDS = new Rect(10, 30, 90, 50);
        Rect N1_BOUNDS = new Rect(10, 80, 90, 180);
        Rect N10_BOUNDS = new Rect(110, 30, 190, 50);
        Rect N11_BOUNDS = new Rect(210, 30, 290, 50);
        Rect N12_BOUNDS = new Rect(110, 80, 190, 180);

        mN0.setBoundsInScreen(N0_BOUNDS);
        mN1.setBoundsInScreen(N1_BOUNDS);
        mN10.setBoundsInScreen(N10_BOUNDS);
        mN11.setBoundsInScreen(N11_BOUNDS);
        mN12.setBoundsInScreen(N12_BOUNDS);

        OptionScanNode treeRoot = mRowColumnTreeBuilder.addViewHierarchyToTree(
                mRoot0, new GlobalActionNode(0, null, "global action label"));

        /*
         * The expected structure is:
         * OptionScanSelectionNode
         * -OptionScanSelectionNode for the row
         * --AccessibilityNodeActionNode with rect: Rect([N0_BOUNDS])
         * --OptionScanSelectionNode
         * ---AccessibilityNodeActionNode with rect: Rect([N10_BOUNDS])
         * ---OptionScanSelectionNode
         * ----AccessibilityNodeActionNode with rect: Rect([N11_BOUNDS])
         * ----ClearFocusNode
         * -OptionScanSelectionNode (not for the row, since there are only 2 views)
         * --AccessibilityNodeActionNode with rect: Rect([N1_BOUNDS])
         * --OptionScanSelectionNode
         * ---AccessibilityNodeActionNode with rect: Rect([N12_BOUNDS])
         * ---Whatever this tree is attached to...
         */
        OptionScanSelectionNode selectionNodeRoot = (OptionScanSelectionNode) treeRoot;
        assertEquals(2, selectionNodeRoot.getChildCount());

        /* First row with N0, N10, and N11 */
        OptionScanSelectionNode rowSelectionNode =
                (OptionScanSelectionNode) selectionNodeRoot.getChild(0);
        assertEquals(2, rowSelectionNode.getChildCount());

        AccessibilityNodeActionNode n0Node =
                (AccessibilityNodeActionNode) rowSelectionNode.getChild(0);
        assertTrue(n0Node.getRectsForNodeHighlight().contains(N0_BOUNDS));

        OptionScanSelectionNode midRowSelectionNode =
                (OptionScanSelectionNode) rowSelectionNode.getChild(1);
        assertEquals(2, rowSelectionNode.getChildCount());

        AccessibilityNodeActionNode n10Node =
                (AccessibilityNodeActionNode) midRowSelectionNode.getChild(0);
        assertTrue(n10Node.getRectsForNodeHighlight().contains(N10_BOUNDS));

        OptionScanSelectionNode endRowSelectionNode =
                (OptionScanSelectionNode) midRowSelectionNode.getChild(1);
        assertEquals(2, rowSelectionNode.getChildCount());

        AccessibilityNodeActionNode n11Node =
                (AccessibilityNodeActionNode) endRowSelectionNode.getChild(0);
        assertTrue(n11Node.getRectsForNodeHighlight().contains(N11_BOUNDS));
        assertTrue(endRowSelectionNode.getChild(1) instanceof ClearFocusNode);

        /* Rest of tree */
        OptionScanSelectionNode n1SelectionNode =
                (OptionScanSelectionNode) selectionNodeRoot.getChild(1);
        assertEquals(2, n1SelectionNode.getChildCount());

        AccessibilityNodeActionNode n1Node =
                (AccessibilityNodeActionNode) n1SelectionNode.getChild(0);
        assertTrue(n1Node.getRectsForNodeHighlight().contains(N1_BOUNDS));

        OptionScanSelectionNode n12SelectionNode =
                (OptionScanSelectionNode) n1SelectionNode.getChild(1);
        assertEquals(2, n12SelectionNode.getChildCount());

        AccessibilityNodeActionNode n12Node =
                (AccessibilityNodeActionNode) n12SelectionNode.getChild(0);
        assertTrue(n12Node.getRectsForNodeHighlight().contains(N12_BOUNDS));
        assertTrue(n12SelectionNode.getChild(1) instanceof GlobalActionNode);
        treeRoot.recycle();
    }

    @Test
    public void buildTreeWithContainer_hasExpectedStructure() {
        /*
         * mN0 is above mN1
         * mN1 encloses mN10, mN11, and mN12
         */
        Rect N0_BOUNDS = new Rect(0, 0, 500, 100);
        Rect N1_BOUNDS = new Rect(0, 100, 500, 500);
        Rect N10_BOUNDS = new Rect(0, 100, 500, 200);
        Rect N11_BOUNDS = new Rect(0, 200, 500, 300);
        Rect N12_BOUNDS = new Rect(0, 300, 500, 400);

        mN0.setBoundsInScreen(N0_BOUNDS);
        mN1.setBoundsInScreen(N1_BOUNDS);
        mN10.setBoundsInScreen(N10_BOUNDS);
        mN11.setBoundsInScreen(N11_BOUNDS);
        mN12.setBoundsInScreen(N12_BOUNDS);

        OptionScanNode treeRoot = mRowColumnTreeBuilder.addViewHierarchyToTree(
                mRoot0, new GlobalActionNode(0, null, "global action label"));

        /*
         * The expected structure is essentially linear scanning:
         * OptionScanSelectionNode
         * -AccessibilityNodeActionNode with rect: Rect([N0_BOUNDS])
         * -OptionScanSelectionNode
         * --AccessibilityNodeActionNode with rect: Rect([N1_BOUNDS])
         * --OptionScanSelectionNode
         * ---AccessibilityNodeActionNode with rect: Rect([N10_BOUNDS])
         * ---OptionScanSelectionNode
         * ----AccessibilityNodeActionNode with rect: Rect([N11_BOUNDS])
         * ----OptionScanSelectionNode
         * -----AccessibilityNodeActionNode with rect: Rect([N12_BOUNDS])
         * -----Whatever this tree is attached to...
         */
        OptionScanSelectionNode selectionNodeRoot = (OptionScanSelectionNode) treeRoot;
        assertEquals(2, selectionNodeRoot.getChildCount());

        AccessibilityNodeActionNode n0Node =
                (AccessibilityNodeActionNode) selectionNodeRoot.getChild(0);
        assertEqualThenRecycle2nd(mN0, n0Node.getNodeInfoCompat());

        OptionScanSelectionNode n1SelectionNode =
                (OptionScanSelectionNode) selectionNodeRoot.getChild(1);
        assertEquals(2, n1SelectionNode.getChildCount());

        AccessibilityNodeActionNode n1Node =
                (AccessibilityNodeActionNode) n1SelectionNode.getChild(0);
        assertEqualThenRecycle2nd(mN1, n1Node.getNodeInfoCompat());

        OptionScanSelectionNode n10SelectionNode =
                (OptionScanSelectionNode) n1SelectionNode.getChild(1);
        assertEquals(2, n10SelectionNode.getChildCount());

        AccessibilityNodeActionNode n10Node =
                (AccessibilityNodeActionNode) n10SelectionNode.getChild(0);
        assertEqualThenRecycle2nd(mN10, n10Node.getNodeInfoCompat());

        /* Dispense with the rest - this covers the key ordering of the container */
        treeRoot.recycle();
    }

    @Test
    public void testBuildTreeWithDuplicateBounds_shouldBeReasonable() {
        /*
         * mN0 is above mN1
         * mN1 matches mN10, and encloses mN11, and mN12
         */
        Rect N0_BOUNDS = new Rect(0, 0, 500, 100);
        Rect N1_BOUNDS = new Rect(0, 100, 500, 500);
        Rect N11_BOUNDS = new Rect(0, 200, 500, 300);
        Rect N12_BOUNDS = new Rect(0, 300, 500, 400);

        mN0.setBoundsInScreen(N0_BOUNDS);
        mN1.setBoundsInScreen(N1_BOUNDS);
        mN10.setBoundsInScreen(N1_BOUNDS);
        mN11.setBoundsInScreen(N11_BOUNDS);
        mN12.setBoundsInScreen(N12_BOUNDS);

        OptionScanNode treeRoot = mRowColumnTreeBuilder.addViewHierarchyToTree(
                mRoot0, new GlobalActionNode(0, null, "global action label"));

        /*
         * The expected structure is essentially linear scanning:
         * OptionScanSelectionNode
         * -AccessibilityNodeActionNode for mN0
         * -OptionScanSelectionNode
         * --ContextMenuNode
         * ---ContextMenuNode for mN1
         * ----AccessibilityNodeActionNode for mN1
         * ----ContextMenuNode for mN10
         * -----AccessibilityNodeActionNode for mN10
         * -----ClearFocusNode
         * --OptionScanSelectionNode
         * ---AccessibilityNodeActionNode for mN11
         * ---OptionScanSelectionNode
         * ----AccessibilityNodeActionNode for mN12
         * ----Whatever this tree is attached to...
         */
        OptionScanSelectionNode selectionNodeRoot = (OptionScanSelectionNode) treeRoot;
        assertEquals(2, selectionNodeRoot.getChildCount());

        AccessibilityNodeActionNode n0Node =
                (AccessibilityNodeActionNode) selectionNodeRoot.getChild(0);
        assertEqualThenRecycle2nd(mN0, n0Node.getNodeInfoCompat());

        OptionScanSelectionNode n1SelectionNode =
                (OptionScanSelectionNode) selectionNodeRoot.getChild(1);
        assertEquals(2, n1SelectionNode.getChildCount());

        ContextMenuNode n1ContextMenuNode =
                (ContextMenuNode) n1SelectionNode.getChild(0);
        assertEquals(2, n1ContextMenuNode.getChildCount());

        AccessibilityNodeActionNode n1Node =
                (AccessibilityNodeActionNode) n1ContextMenuNode.getChild(0);
        assertEqualThenRecycle2nd(mN1, n1Node.getNodeInfoCompat());

        ContextMenuNode n10ContextMenuNode =
                (ContextMenuNode) n1ContextMenuNode.getChild(1);
        assertEquals(2, n10ContextMenuNode.getChildCount());

        AccessibilityNodeActionNode n10Node =
                (AccessibilityNodeActionNode) n10ContextMenuNode.getChild(0);
        assertEqualThenRecycle2nd(mN10, n10Node.getNodeInfoCompat());

        treeRoot.recycle();
    }

    private void assertEqualThenRecycle2nd(SwitchAccessNodeCompat n0, SwitchAccessNodeCompat n1) {
        assertEquals(n0, n1);
        n1.recycle();
    }
}
