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
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.switchaccess.*;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityService;
import com.android.switchaccess.test.ShadowAccessibilityWindowInfo;
import com.android.switchaccess.treebuilding.TalkBackOrderNDegreeTreeBuilder;
import com.android.talkback.BuildConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

/**
 * Robolectric tests for NDegreeTreeBuilder
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class,
                ShadowAccessibilityService.class,
                ShadowAccessibilityWindowInfo.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class TalkBackOrderNDegreeTreeBuilderTest {
    private final List<AccessibilityWindowInfo> mWindows = new ArrayList<>();
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private static final Rect WINDOW_0_BOUNDS = new Rect(10, 10, 110, 110);
    private static final Rect N0_BOUNDS = new Rect(10, 10, 30, 30);
    private static final Rect N00_BOUNDS = new Rect(10, 40, 30, 70);
    private static final Rect N01_BOUNDS = new Rect(40, 40, 70, 70);
    private static final Rect N000_BOUNDS = new Rect(80, 40, 110, 70);
    private static final Rect N001_BOUNDS = new Rect(40, 120, 70, 150);
    private static final Rect N1_BOUNDS = new Rect(10, 80, 30, 110);
    private static final Rect N10_BOUNDS = new Rect(10, 120, 30, 150);
    private static final Rect N11_BOUNDS = new Rect(80, 120, 110, 150);
    private AccessibilityNodeInfo mWindowRoot0, mN0, mN1, mN10, mN11, mN00, mN01, mN000, mN001;
    private List<SwitchAccessWindowInfo> mExtendedWindows = new ArrayList<>();
    private ShadowAccessibilityWindowInfo mShadowWindow0;

    /*
     * We build a tree of degree N from AccessibilityNodeInfos:
     *
     *                       root0
     *                     /       \
     *                   n0         n1
     *                  /  \       /   \
     *                n00  n01   n10   n11
     *               /  \
     *            n000  n001
     *
     * all of which, other than the root, are clickable.
     */
    @Before
    public void setUp() {
        /* For some reason this value becomes 22 when I allow the manifest to load */
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 21);
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        /* Build accessibility node tree */
        mWindows.add(AccessibilityWindowInfo.obtain());

        mWindowRoot0 = AccessibilityNodeInfo.obtain();
        mWindowRoot0.setClickable(false);
        mWindowRoot0.setFocusable(false);
        mWindowRoot0.setContentDescription("mWindowRoot0");

        mN0 = AccessibilityNodeInfo.obtain();
        mN0.setVisibleToUser(true);
        mN0.setClickable(true);
        mN0.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN0.setContentDescription("mN0");
        mN0.setBoundsInScreen(N0_BOUNDS);

        mN00 = AccessibilityNodeInfo.obtain();
        mN00.setVisibleToUser(true);
        mN00.setClickable(true);
        mN00.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN00.setContentDescription("mN00");
        mN00.setBoundsInScreen(N00_BOUNDS);

        mN000 = AccessibilityNodeInfo.obtain();
        mN000.setVisibleToUser(true);
        mN000.setClickable(true);
        mN000.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN000.setContentDescription("mN000");
        mN000.setBoundsInScreen(N000_BOUNDS);

        mN001 = AccessibilityNodeInfo.obtain();
        mN001.setVisibleToUser(true);
        mN001.setClickable(true);
        mN001.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN001.setContentDescription("mN001");
        mN001.setBoundsInScreen(N001_BOUNDS);

        mN01 = AccessibilityNodeInfo.obtain();
        mN01.setVisibleToUser(true);
        mN01.setClickable(true);
        mN01.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN01.setContentDescription("mN01");
        mN01.setBoundsInScreen(N01_BOUNDS);

        mN1 = AccessibilityNodeInfo.obtain();
        mN1.setVisibleToUser(true);
        mN1.setClickable(true);
        mN1.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN1.setContentDescription("mN1");
        mN1.setBoundsInScreen(N1_BOUNDS);

        mN10 = AccessibilityNodeInfo.obtain();
        mN10.setClickable(true);
        mN10.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN10.setVisibleToUser(true);
        mN10.setContentDescription("mN10");
        mN10.setBoundsInScreen(N10_BOUNDS);

        mN11 = AccessibilityNodeInfo.obtain();
        mN11.setClickable(true);
        mN11.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN11.setVisibleToUser(true);
        mN11.setContentDescription("mN11");
        mN11.setBoundsInScreen(N11_BOUNDS);

        ShadowAccessibilityNodeInfo shadowRoot =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mWindowRoot0);
        shadowRoot.addChild(mN0);
        shadowRoot.addChild(mN1);

        final ShadowAccessibilityNodeInfo shadowN0 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN0);
        shadowN0.addChild(mN00);
        shadowN0.addChild(mN01);

        final ShadowAccessibilityNodeInfo shadowN00 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN00);
        shadowN00.addChild(mN000);
        shadowN00.addChild(mN001);

        final ShadowAccessibilityNodeInfo shadowN1 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN1);
        shadowN1.addChild(mN10);
        shadowN1.addChild(mN11);

        mShadowWindow0 = (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0));
        mShadowWindow0.setBoundsInScreen(WINDOW_0_BOUNDS);
        mShadowWindow0.setRoot(mWindowRoot0);
        mExtendedWindows = SwitchAccessWindowInfo.convertZOrderWindowList(mWindows);
    }

    @After
    public void tearDown() {
        mWindowRoot0.recycle();
        mN0.recycle();
        mN00.recycle();
        mN000.recycle();
        mN001.recycle();
        mN01.recycle();
        mN1.recycle();
        mN10.recycle();
        mN11.recycle();
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
    }

    @Test
    public void buildTreeWithHigherDegreeThanNodesAvailable_hasExpectedStructure() {
        configureNumOptionScanningSwitches(5);
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        /* Change the root to a node that will have four clickable children */
        mShadowWindow0.setRoot(mN0);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode) treeBuilder
                .addWindowListToTree(mExtendedWindows, new ClearFocusNode());
        /* four clickable children + context menu node */
        assertEquals(5, treeRoot.getChildCount());
        treeRoot.recycle();
    }

    @Test
    public void buildTreeWithZeroLengthWindowList_hasContextMenu() {
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        OptionScanNode treeRoot = treeBuilder.addWindowListToTree(
                new ArrayList<SwitchAccessWindowInfo>(), new ClearFocusNode());
        assertTrue(treeRoot instanceof ClearFocusNode);
        treeRoot.recycle();
    }

    @Test
    public void buildTreeWithNullWindowListNullContextMenu_hasOnlyClearFocusNode() {
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        OptionScanNode treeRoot = treeBuilder.addWindowListToTree(null, null);
        assertTrue(treeRoot instanceof ClearFocusNode);
        treeRoot.recycle();
    }

    @Test
    public void buildDegreeThreeFullTree_hasExpectedStructure() {
        /* with 8 total clickable nodes and a tree of degree 3, the nodes are not evenly
         * distributed among the three branches */
        configureNumOptionScanningSwitches(3);
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        CharSequence globalActionLabel0 = "global action label 0";
        CharSequence globalActionLabel1 = "global action label 1";

        ContextMenuItem globalNode0 = new GlobalActionNode(0, null, globalActionLabel0);
        ContextMenuItem globalNode1 = new GlobalActionNode(1, null, globalActionLabel1);

        OptionScanNode contextMenuTree = treeBuilder
                .buildContextMenu(Arrays.asList(globalNode0, globalNode1));
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode) treeBuilder
                .addWindowListToTree(mExtendedWindows, contextMenuTree);
        assertEquals(3, treeRoot.getChildCount());

        OptionScanSelectionNode firstLevelNode0 = (OptionScanSelectionNode) treeRoot.getChild(0);
        OptionScanSelectionNode firstLevelNode1 = (OptionScanSelectionNode) treeRoot.getChild(1);
        OptionScanSelectionNode firstLevelNode2 = (OptionScanSelectionNode) treeRoot.getChild(2);
        /* check the structure of the tree rooted at firstLevelNode0 */
        assertEquals(firstLevelNode0.getRectsForNodeHighlight().size(), 3);
        assertTrue(firstLevelNode0.getRectsForNodeHighlight().contains(N0_BOUNDS));
        assertTrue(firstLevelNode0.getRectsForNodeHighlight().contains(N00_BOUNDS));
        assertTrue(firstLevelNode0.getRectsForNodeHighlight().contains(N000_BOUNDS));
        assertTrue(firstLevelNode0.getChild(0) instanceof AccessibilityNodeActionNode);
        assertTrue(firstLevelNode0.getChild(1) instanceof AccessibilityNodeActionNode);
        OptionScanSelectionNode secondLevelNode0 = (OptionScanSelectionNode)firstLevelNode0
                .getChild(2);
        assertEquals(2, secondLevelNode0.getChildCount());
        assertTrue(secondLevelNode0.getChild(1) instanceof ContextMenuNode);

        /* check the structure of the tree rooted at firstLevelNode1 */
        assertEquals(firstLevelNode1.getRectsForNodeHighlight().size(), 3);
        assertTrue(firstLevelNode1.getRectsForNodeHighlight().contains(N001_BOUNDS));
        assertTrue(firstLevelNode1.getRectsForNodeHighlight().contains(N01_BOUNDS));
        assertTrue(firstLevelNode1.getRectsForNodeHighlight().contains(N1_BOUNDS));
        OptionScanSelectionNode secondLevelNode1 = (OptionScanSelectionNode)firstLevelNode1
                .getChild(2);
        assertEquals(2, secondLevelNode1.getChildCount());
        assertTrue(secondLevelNode1.getChild(1) instanceof ContextMenuNode);

        /* check the structure of the tree rooted at firstLevelNode2 */
        assertEquals(firstLevelNode2.getRectsForNodeHighlight().size(), 2);
        assertTrue(firstLevelNode2.getRectsForNodeHighlight().contains(N10_BOUNDS));
        assertTrue(firstLevelNode2.getRectsForNodeHighlight().contains(N11_BOUNDS));
        assertTrue(firstLevelNode2.getChild(2) instanceof ContextMenuNode);

        contextMenuTree.recycle();
        treeRoot.recycle();
    }

    @Test
    public void buildDegreeFourFullTree_hasExpectedStructure() {
        /* with 8 total clickable nodes and a tree of degree 4, the nodes are evenly
         * distributed among the four branches */
        configureNumOptionScanningSwitches(4);
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode) treeBuilder
                .addWindowListToTree(mExtendedWindows, new ClearFocusNode());

        assertEquals(4, treeRoot.getChildCount());
        OptionScanSelectionNode firstLevelNode0 = (OptionScanSelectionNode) treeRoot.getChild(0);
        OptionScanSelectionNode firstLevelNode1 = (OptionScanSelectionNode) treeRoot.getChild(1);
        OptionScanSelectionNode firstLevelNode2 = (OptionScanSelectionNode) treeRoot.getChild(2);
        OptionScanSelectionNode firstLevelNode3 = (OptionScanSelectionNode) treeRoot.getChild(3);
        /* check the structure of the tree rooted at firstLevelNode0 */
        assertEquals(firstLevelNode0.getRectsForNodeHighlight().size(), 2);
        assertTrue(firstLevelNode0.getRectsForNodeHighlight().contains(N0_BOUNDS));
        assertTrue(firstLevelNode0.getRectsForNodeHighlight().contains(N00_BOUNDS));
        assertTrue(firstLevelNode0.getChild(2) instanceof ClearFocusNode);

        /* check the structure of the tree rooted at firstLevelNode1 */
        assertEquals(firstLevelNode1.getRectsForNodeHighlight().size(), 2);
        assertTrue(firstLevelNode1.getRectsForNodeHighlight().contains(N000_BOUNDS));
        assertTrue(firstLevelNode1.getRectsForNodeHighlight().contains(N001_BOUNDS));
        assertTrue(firstLevelNode1.getChild(2) instanceof ClearFocusNode);

        /* check the structure of the tree rooted at firstLevelNode2 */
        assertEquals(firstLevelNode2.getRectsForNodeHighlight().size(), 2);
        assertTrue(firstLevelNode2.getRectsForNodeHighlight().contains(N01_BOUNDS));
        assertTrue(firstLevelNode2.getRectsForNodeHighlight().contains(N1_BOUNDS));
        assertTrue(firstLevelNode2.getChild(2) instanceof ClearFocusNode);

        /* check the structure of the tree rooted at firstLevelNode3 */
        assertEquals(firstLevelNode3.getRectsForNodeHighlight().size(), 2);
        assertTrue(firstLevelNode3.getRectsForNodeHighlight().contains(N10_BOUNDS));
        assertTrue(firstLevelNode3.getRectsForNodeHighlight().contains(N11_BOUNDS));
        assertTrue(firstLevelNode3.getChild(2) instanceof ClearFocusNode);

        treeRoot.recycle();
    }

    @Test
    public void buildNoActionsContextMenuTree_hasOnlyClearFocusNode() {
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        List<ContextMenuItem> actions = new ArrayList<>();
        OptionScanNode contextMenuTree = treeBuilder.buildContextMenu(actions);
        assertTrue(contextMenuTree instanceof ClearFocusNode);
        contextMenuTree.recycle();
    }

    @Test
    public void buildContextMenuTree_order2_hasExpectedStructure() {
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        ContextMenuItem globalNode0 = new GlobalActionNode(0, null, null);
        ContextMenuItem globalNode1 = new GlobalActionNode(1, null, null);
        ContextMenuItem globalNode2 = new GlobalActionNode(2, null, null);

        ContextMenuNode contextMenuTree = (ContextMenuNode) treeBuilder
                .buildContextMenu(Arrays.asList(globalNode0, globalNode1, globalNode2));

        /*
         * Expected structure:
         *                                       /-globalNode0     /-globalNode1
         *                     /-contextMenuNode---contextMenuNode---clearFocus
         * rootContextMenuNode---contextMenuNode---globalNode2
         *                                       \-clearFocus
         */
        assertEquals(2, contextMenuTree.getChildCount());

        ContextMenuNode secondLevelNode0 = (ContextMenuNode) contextMenuTree.getChild(0);
        assertEquals(2, secondLevelNode0.getChildCount());
        assertEquals(globalNode0, secondLevelNode0.getChild(0));
        assertEquals(globalNode1, ((ContextMenuNode) secondLevelNode0.getChild(1)).getChild(0));
        assertTrue(((ContextMenuNode) secondLevelNode0.getChild(1)).getChild(1)
                instanceof ClearFocusNode);

        ContextMenuNode secondLevelNode1 = (ContextMenuNode) contextMenuTree.getChild(1);
        assertEquals(2, secondLevelNode1.getChildCount());
        assertEquals(globalNode2, secondLevelNode1.getChild(0));
        assertTrue(secondLevelNode1.getChild(1) instanceof ClearFocusNode);
        contextMenuTree.recycle();
    }

    @Test
    public void buildContextMenuTree_order3_hasExpectedStructure() {
        configureNumOptionScanningSwitches(3);
        TalkBackOrderNDegreeTreeBuilder treeBuilder = new TalkBackOrderNDegreeTreeBuilder(mContext);
        ContextMenuItem globalNode0 = new GlobalActionNode(0, null, null);
        ContextMenuItem globalNode1 = new GlobalActionNode(1, null, null);
        ContextMenuItem globalNode2 = new GlobalActionNode(2, null, null);
        ContextMenuItem globalNode3 = new GlobalActionNode(3, null, null);

        ContextMenuNode contextMenuTree = (ContextMenuNode) treeBuilder.buildContextMenu(
                Arrays.asList(globalNode0, globalNode1, globalNode2, globalNode3));

        /*
         * Expected structure:
         *                                       /-globalNode0
         *                     /-contextMenuNode---globalNode1
         *                     /-globalNode2     \-cleanFocus
         * rootContextMenuNode---contextMenuNode---globalNode3
         *                                       \-clearFocus
         */
        assertEquals(3, contextMenuTree.getChildCount());

        ContextMenuNode secondLevelNode0 = (ContextMenuNode) contextMenuTree.getChild(0);
        assertEquals(globalNode2, contextMenuTree.getChild(1));
        ContextMenuNode secondLevelNode1 = (ContextMenuNode) contextMenuTree.getChild(2);

        assertEquals(globalNode0, secondLevelNode0.getChild(0));
        assertEquals(globalNode1, secondLevelNode0.getChild(1));
        assertTrue(secondLevelNode0.getChild(2) instanceof ClearFocusNode);

        assertEquals(globalNode3, secondLevelNode1.getChild(0));
        assertTrue(secondLevelNode1.getChild(1) instanceof ClearFocusNode);

        contextMenuTree.recycle();
    }

    /* Configure the specified number of option scanning preferences */
    private void configureNumOptionScanningSwitches(int num) {
        assertTrue(num > 0);
        assertTrue(num <= TalkBackOrderNDegreeTreeBuilder.OPTION_SCAN_SWITCH_CONFIG_IDS.length);
        SharedPreferences.Editor prefEditor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        for (int i = 0; i < TalkBackOrderNDegreeTreeBuilder.OPTION_SCAN_SWITCH_CONFIG_IDS.length;
                i++) {
            String prefKey = mContext
                    .getString(TalkBackOrderNDegreeTreeBuilder.OPTION_SCAN_SWITCH_CONFIG_IDS[i]);
            prefEditor.remove(prefKey);
            if (i < num) {
                Set<String> prefStringSet = new HashSet<>(1);
                prefStringSet.add(String.format("%d", i));
                prefEditor.putStringSet(prefKey, prefStringSet);
            }
        }
        prefEditor.apply();
    }
}
