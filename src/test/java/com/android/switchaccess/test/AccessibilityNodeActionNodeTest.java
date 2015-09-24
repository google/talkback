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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.switchaccess.*;

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
import java.util.Set;

/**
 * Tests for AccessibilityNodeActionNode
 */
@Config(
        emulateSdk = 18,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class,
                ShadowAccessibilityNodeInfoCompat.class,
                ShadowAccessibilityNodeInfoCompat.ShadowAccessibilityActionCompat.class,
                ShadowAccessibilityNodeInfo.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricTestRunner.class)
public class AccessibilityNodeActionNodeTest {
    private static final Rect NODE_BOUNDS = new Rect(10, 10, 90, 20);
    private static final int ACTION_ID = 1234;
    private static final String ACTION_NAME = "OneTwoThreeFour";

    private SwitchAccessNodeCompat mNodeInfoCompat;
    private Context mContext = RuntimeEnvironment.application.getApplicationContext();
    AccessibilityNodeActionNode mAccessibilityNodeActionNode;

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfoCompat.resetObtainedInstances();
        mNodeInfoCompat = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        ShadowAccessibilityNodeInfoCompat shadowCompat =
                (ShadowAccessibilityNodeInfoCompat) ShadowExtractor.extract(mNodeInfoCompat);
        shadowCompat.setBoundsInScreen(NODE_BOUNDS);
        mAccessibilityNodeActionNode = new AccessibilityNodeActionNode(
                mNodeInfoCompat, new AccessibilityActionCompat(ACTION_ID, ACTION_NAME));
    }

    @After
    public void tearDown() {
        try {
            mAccessibilityNodeActionNode.recycle();
            mNodeInfoCompat.recycle();
            assertFalse(ShadowAccessibilityNodeInfoCompat.areThereUnrecycledNodes(true));
        } finally {
            ShadowAccessibilityNodeInfoCompat.resetObtainedInstances();
        }
    }

    @Test
    public void performAction_actionReceivedByNode() {
        mAccessibilityNodeActionNode.performAction();
        AccessibilityNodeInfo info = (AccessibilityNodeInfo) mNodeInfoCompat.getInfo();
        ShadowAccessibilityNodeInfo shadowInfo =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(info);

        List<Integer> performedActions = shadowInfo.getPerformedActions();
        assertEquals(1, performedActions.size());
        assertEquals(ACTION_ID, performedActions.get(0).intValue());
    }

    @Test
    public void getRectsForNodeHighlight_shouldReturnBounds() {
        Set<Rect> returnedRects = mAccessibilityNodeActionNode.getRectsForNodeHighlight();
        assertEquals(1, returnedRects.size());
        assertTrue(returnedRects.contains(NODE_BOUNDS));
    }

    @Test
    public void getActionLabel_shouldReturnName() {
        assertTrue(TextUtils.equals(ACTION_NAME,
                mAccessibilityNodeActionNode.getActionLabel(mContext)));
    }

    @Test
    public void getNodeInfoCompat_shouldBehave() {
        AccessibilityNodeInfoCompat compat = mAccessibilityNodeActionNode.getNodeInfoCompat();
        assertEquals(mNodeInfoCompat, compat);
        compat.recycle();
    }
}
