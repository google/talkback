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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.switchaccess.*;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.treebuilding.LinearScanTreeBuilder;

import com.android.talkback.BuildConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

/**
 * Robolectric tests for LinearScanTreeBuilder
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
public class LinearScanTreeBuilderTest {
    private static final Rect N0_BOUNDS = new Rect(10, 10, 90, 20);
    private static final Rect N1_BOUNDS = new Rect(10, 30, 90, 80);
    private static final Rect N10_BOUNDS = new Rect(10, 30, 90, 50);
    private static final Rect N11_BOUNDS = new Rect(10, 60, 90, 80);

    /*
     * We build a simple tree of AccessibilityNodeInfos
     *
     *                     root0
     *                      /  \
     *                    n0   n1
     *                        /  \
     *                      n10  n11
     * n0, n10, and n11 are clickable; root and n1 are not.
     * n0 is located above n1. n10 is located above n11, and both are inside n1.
     */
    private AccessibilityNodeInfo mRoot0, mN0, mN1, mN10, mN11;
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final LinearScanTreeBuilder mLinearScanTreeBuilder =
            new LinearScanTreeBuilder(mContext);
    @Before
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        /* Build accessibility node tree */
        mRoot0 = AccessibilityNodeInfo.obtain();
        mRoot0.setVisibleToUser(true);
        mRoot0.setContentDescription("root0");

        mN0 = AccessibilityNodeInfo.obtain();
        mN0.setVisibleToUser(true);
        mN0.setClickable(true);
        mN0.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN0.setContentDescription("mN0");
        mN0.setBoundsInScreen(N0_BOUNDS);

        mN1 = AccessibilityNodeInfo.obtain();
        mN1.setVisibleToUser(true);
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
        ShadowAccessibilityNodeInfo shadowN11 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN11);
        shadowN11.setContentDescription("mN11");
        shadowN11.setBoundsInScreen(N11_BOUNDS);

        mRoot0.setClickable(false);
        mRoot0.setFocusable(false);
        ShadowAccessibilityNodeInfo shadowRoot =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mRoot0);
        shadowRoot.addChild(mN0);
        shadowRoot.addChild(mN1);

        mN1.setClickable(false);
        final ShadowAccessibilityNodeInfo shadowN1 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN1);
        shadowN1.addChild(mN10);
        shadowN1.addChild(mN11);
    }

    @After
    public void tearDown() {
        mRoot0.recycle();
        mN0.recycle();
        mN1.recycle();
        mN10.recycle();
        mN11.recycle();
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test
    public void buildTreeWithNoActions_treeHasOnlyClearFocusNode() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setVisibleToUser(true);
        info.setClickable(false);
        info.setContentDescription("buildTreeWithNoActions_treeHasOnlyClearFocusNode info");
        info.setBoundsInScreen(N0_BOUNDS);
        OptionScanNode tree = mLinearScanTreeBuilder
                .addViewHierarchyToTree(new SwitchAccessNodeCompat(info), null);
        assertTrue(tree instanceof ClearFocusNode);
        info.recycle();
        tree.recycle();
    }

    @Test
    public void buildFullTree_hasExpectedStructure() {
        CharSequence globalActionLabel = "global action label";
        OptionScanNode treeRoot = mLinearScanTreeBuilder
                .addViewHierarchyToTree(new SwitchAccessNodeCompat(mRoot0),
                        new GlobalActionNode(0, null, globalActionLabel));
        assertTrue(treeRoot instanceof OptionScanSelectionNode);
        OptionScanSelectionNode selectionNodeRoot = (OptionScanSelectionNode) treeRoot;
        assertEquals(2, selectionNodeRoot.getChildCount());
        assertTrue(selectionNodeRoot.getChild(0).getRectsForNodeHighlight().contains(N0_BOUNDS));
        OptionScanNode n1Node = selectionNodeRoot.getChild(1);
        assertTrue(n1Node instanceof OptionScanSelectionNode);
        OptionScanSelectionNode selectionNodeN1 = (OptionScanSelectionNode) n1Node;
        assertEquals(2, selectionNodeN1.getChildCount());
        assertTrue(selectionNodeN1.getChild(0).getRectsForNodeHighlight().contains(N10_BOUNDS));
        OptionScanNode n11Node = selectionNodeN1.getChild(1);
        assertTrue(n11Node instanceof OptionScanSelectionNode);
        OptionScanSelectionNode selectionNodeN11 = (OptionScanSelectionNode) n11Node;
        assertEquals(2, selectionNodeN11.getChildCount());
        assertTrue(selectionNodeN11.getChild(0).getRectsForNodeHighlight().contains(N11_BOUNDS));
        OptionScanNode globalActionNode = selectionNodeN11.getChild(1);
        assertTrue(globalActionNode instanceof GlobalActionNode);
        treeRoot.recycle();
    }
}
