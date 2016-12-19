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

import com.android.switchaccess.*;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.treebuilding.HuffmanTreeBuilder;
import com.android.talkback.BuildConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Robolectric tests for HuffmanTreeBuilder
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
public class HuffmanTreeBuilderTest {
    private static final Rect N0_BOUNDS = new Rect(10, 10, 30, 30);
    private static final Rect N00_BOUNDS = new Rect(10, 40, 30, 70);
    private static final Rect N01_BOUNDS = new Rect(40, 40, 70, 70);
    private static final Rect N1_BOUNDS = new Rect(80, 40, 110, 70);
    private static final Rect N10_BOUNDS = new Rect(10, 80, 30, 110);
    private static final Rect N11_BOUNDS = new Rect(40, 80, 70, 110);
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private SwitchAccessNodeCompat mWindowRoot0, mN0, mN1, mN10, mN11, mN00, mN01;
    private String mProbabilityContext = "";
    private ProbabilityModelReader mMockProbabilityModelReader = mock(ProbabilityModelReader.class);

    /*
     * We build Huffman Tree from AccessibilityNodeInfosCompats:
     *
     *                     root0
     *                     /    \
     *                   n0     n1
     *                  / \    /  \
     *                n00 n01 n10 n11
     *
     * of which root, n0 and n1, are not clickable. Here's the content description of the
     * clickable nodes: n00: "a", n01 : "e", n10: "t", and n11: "!". Based on the English letter
     * frequency (which is the probability model used for the purpose of the following tests) the
     * ordering of these clickable nodes would be n11, n10, n00, n01 (ascending order).
     */
    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        /* Build accessibility node tree */
        mWindowRoot0 = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        mWindowRoot0.setClickable(false);
        mWindowRoot0.setFocusable(false);
        mWindowRoot0.setContentDescription("mWindowRoot0");

        mN0 = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        mN0.setVisibleToUser(true);
        mN0.setClickable(false);
        mN0.setContentDescription("mN0");
        mN0.setBoundsInScreen(N0_BOUNDS);

        AccessibilityNodeInfo n00Info = AccessibilityNodeInfo.obtain();
        n00Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN00 = new SwitchAccessNodeCompat(n00Info);
        mN00.setVisibleToUser(true);
        mN00.setClickable(true);
        mN00.setContentDescription("a");
        mN00.setBoundsInScreen(N00_BOUNDS);

        AccessibilityNodeInfo n01Info = AccessibilityNodeInfo.obtain();
        n01Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        n01Info.setContentDescription("e");
        n01Info.setBoundsInScreen(N01_BOUNDS);
        mN01 = new SwitchAccessNodeCompat(n01Info);
        mN01.setVisibleToUser(true);
        mN01.setClickable(true);

        mN1 = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        mN1.setVisibleToUser(true);
        mN1.setClickable(false);
        mN1.setContentDescription("mN1");
        mN1.setBoundsInScreen(N1_BOUNDS);

        AccessibilityNodeInfo n10Info = AccessibilityNodeInfo.obtain();
        n10Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mN10 = new SwitchAccessNodeCompat(n10Info);
        mN10.setClickable(true);
        mN10.setVisibleToUser(true);
        mN10.setContentDescription("t");
        mN10.setBoundsInScreen(N10_BOUNDS);

        AccessibilityNodeInfo n11Info = AccessibilityNodeInfo.obtain();
        n11Info.setContentDescription("!");
        n11Info.setBoundsInScreen(N11_BOUNDS);
        mN11 = new SwitchAccessNodeCompat(n11Info);
        mN11.setClickable(false);
        mN11.setVisibleToUser(true);

        ShadowAccessibilityNodeInfo shadowRoot =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mWindowRoot0.getInfo());
        shadowRoot.addChild((AccessibilityNodeInfo) mN0.getInfo());
        shadowRoot.addChild((AccessibilityNodeInfo) mN1.getInfo());

        final ShadowAccessibilityNodeInfo shadowN0 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN0.getInfo());
        shadowN0.addChild((AccessibilityNodeInfo) mN00.getInfo());
        shadowN0.addChild((AccessibilityNodeInfo) mN01.getInfo());

        final ShadowAccessibilityNodeInfo shadowN1 =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mN1.getInfo());
        shadowN1.addChild((AccessibilityNodeInfo) mN10.getInfo());
        shadowN1.addChild((AccessibilityNodeInfo) mN11.getInfo());

        /* Set up the mock probabilityModelReader */
        MockitoAnnotations.initMocks(this);
        Map<SwitchAccessNodeCompat, Double> probabilityDistribution = new HashMap<>();
        probabilityDistribution.put(mN00, 0.08167);
        probabilityDistribution.put(mN01, 0.12702);
        probabilityDistribution.put(mN10, 0.09056);
        probabilityDistribution.put(mN11, 0.0001);
        when(mMockProbabilityModelReader.getProbabilityDistribution(eq(""), anySet()))
                .thenReturn(probabilityDistribution);
    }

    @After
    public void tearDown() {
        mWindowRoot0.recycle();
        mN0.recycle();
        mN00.recycle();
        mN01.recycle();
        mN1.recycle();
        mN10.recycle();
        mN11.recycle();
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
    }

    @Test
    public void buildTreeWithNoActions_treeHasOnlyClearFocusNode() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setVisibleToUser(true);
        info.setContentDescription("buildTreeWithNoActions_treeHasOnlyClearFocusNode info");
        HuffmanTreeBuilder treeBuilder =
                new HuffmanTreeBuilder(mContext, 2, mMockProbabilityModelReader);
        OptionScanNode tree = treeBuilder
                .buildTreeFromNodeTree(new SwitchAccessNodeCompat(info), null, mProbabilityContext);
        assertTrue(tree instanceof ClearFocusNode);
        info.recycle();
        tree.recycle();
    }

    @Test
    public void buildTreeWithDefaultProbabilityNode_includesNode() {
        makeN11Clickable();
        HuffmanTreeBuilder treeBuilder =
                new HuffmanTreeBuilder(mContext, 2, mMockProbabilityModelReader);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode) treeBuilder
                .buildTreeFromNodeTree(mWindowRoot0, null, mProbabilityContext);

        assertEquals(1, treeRoot.getChild(0).getRectsForNodeHighlight().size());
        assertTrue(treeRoot.getChild(1).getRectsForNodeHighlight().contains(N11_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void buildFullTreeDegreeTwo_hasExpectedStructure() {
        HuffmanTreeBuilder treeBuilder =
                new HuffmanTreeBuilder(mContext, 2, mMockProbabilityModelReader);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode) treeBuilder
                .buildTreeFromNodeTree(mWindowRoot0, null, mProbabilityContext);

        assertTrue(treeRoot.getChild(0).getRectsForNodeHighlight().contains(N01_BOUNDS));
        OptionScanSelectionNode firstLevelNode1 = (OptionScanSelectionNode)treeRoot.getChild(1);
        assertEquals(2, firstLevelNode1.getRectsForNodeHighlight().size());
        assertTrue(firstLevelNode1.getChild(0).getRectsForNodeHighlight().contains(N00_BOUNDS));
        OptionScanSelectionNode secondLevelNode0 = (OptionScanSelectionNode)
                firstLevelNode1.getChild(1);
        assertTrue(secondLevelNode0.getChild(1) instanceof ClearFocusNode);
        assertTrue(secondLevelNode0.getRectsForNodeHighlight().contains(N10_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void buildFullTreeDegreeThree_hasExpectedStructure() {
        makeN11Clickable();
        HuffmanTreeBuilder treeBuilder =
                new HuffmanTreeBuilder(mContext, 3, mMockProbabilityModelReader);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode) treeBuilder
                .buildTreeFromNodeTree(mWindowRoot0, null, mProbabilityContext);
        assertEquals(3, treeRoot.getChildCount());
        OptionScanSelectionNode firstChild = (OptionScanSelectionNode)treeRoot.getChild(0);
        assertTrue(firstChild.getRectsForNodeHighlight().contains(N00_BOUNDS));
        assertEquals(2, firstChild.getRectsForNodeHighlight().size());
        assertTrue(firstChild.getChild(2) instanceof ClearFocusNode);
        assertTrue(treeRoot.getChild(0).getRectsForNodeHighlight().contains(N11_BOUNDS));
        assertTrue(treeRoot.getChild(1).getRectsForNodeHighlight().contains(N10_BOUNDS));
        assertTrue(treeRoot.getChild(2).getRectsForNodeHighlight().contains(N01_BOUNDS));
        treeRoot.recycle();
    }

    private void makeN11Clickable() {
        AccessibilityNodeInfo n11Info = (AccessibilityNodeInfo) mN11.getInfo();
        n11Info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        n11Info.setClickable(true);
    }
}