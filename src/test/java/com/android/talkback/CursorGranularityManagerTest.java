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

package com.android.talkback;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Tests for CursorGranularityManager
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)

public class CursorGranularityManagerTest {

    private Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private CursorGranularityManager mGranularityManager;

    @Before
    public void setUp() {
        mGranularityManager = new CursorGranularityManager(mContext);
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @After
    public void tearDown() {
        try {
            mGranularityManager.shutdown();
            assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        } finally {
            ShadowAccessibilityNodeInfo.resetObtainedInstances();
        }
    }

    @Test
    public void setGranularityTest() {
        AccessibilityNodeInfoCompat node = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        assertTrue(mGranularityManager.setGranularityAt(node, CursorGranularity.CHARACTER));
        assertEquals(CursorGranularity.CHARACTER, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.isLockedTo(node));
        node.recycle();
    }

    @Test
    public void keepGranularityBetweenNodesWithGranularityTest() {
        AccessibilityNodeInfoCompat node1 = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        AccessibilityNodeInfoCompat node2 = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        assertTrue(mGranularityManager.setGranularityAt(node1, CursorGranularity.CHARACTER));
        mGranularityManager.onNodeFocused(node2);
        assertEquals(CursorGranularity.CHARACTER, mGranularityManager.getCurrentGranularity());
        node1.recycle();
        node2.recycle();
    }

    @Test
    public void keepGranularityBetweenNodesWithLackOfGranularityTest() {
        AccessibilityNodeInfoCompat node1 = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        AccessibilityNodeInfoCompat node2 = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
        AccessibilityNodeInfoCompat node3 = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        assertTrue(mGranularityManager.setGranularityAt(node1, CursorGranularity.CHARACTER));
        mGranularityManager.onNodeFocused(node2);
        assertEquals(CursorGranularity.DEFAULT, mGranularityManager.getCurrentGranularity());
        mGranularityManager.onNodeFocused(node3);
        assertEquals(CursorGranularity.CHARACTER, mGranularityManager.getCurrentGranularity());
        node1.recycle();
        node2.recycle();
        node3.recycle();
    }

    @Test
    public void nextGranularityWithLoopingTest() {
        AccessibilityNodeInfoCompat node = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER |
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD |
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_HIGHER));
        assertEquals(CursorGranularity.CHARACTER, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_HIGHER));
        assertEquals(CursorGranularity.WORD, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_HIGHER));
        assertEquals(CursorGranularity.LINE, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_HIGHER));
        assertEquals(CursorGranularity.DEFAULT, mGranularityManager.getCurrentGranularity());
        node.recycle();
    }

    @Test
    public void previousGranularityWithLoopingTest() {
        AccessibilityNodeInfoCompat node = createNodeWithGranularities(
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER |
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD |
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_LOWER));
        assertEquals(CursorGranularity.LINE, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_LOWER));
        assertEquals(CursorGranularity.WORD, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_LOWER));
        assertEquals(CursorGranularity.CHARACTER, mGranularityManager.getCurrentGranularity());
        assertTrue(mGranularityManager.adjustGranularityAt(node,
                CursorGranularityManager.CHANGE_GRANULARITY_LOWER));
        assertEquals(CursorGranularity.DEFAULT, mGranularityManager.getCurrentGranularity());
        node.recycle();
    }

    private AccessibilityNodeInfoCompat createNodeWithGranularities(int granularities) {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(node)).
                setMovementGranularities(granularities);
        return new AccessibilityNodeInfoCompat(node);
    }
}
