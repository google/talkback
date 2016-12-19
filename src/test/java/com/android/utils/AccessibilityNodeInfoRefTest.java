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

package com.android.utils;

/**
 * Tests for AccessibilityNodeInfoRef
 */

import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.talkback.BuildConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.internal.ShadowExtractor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

@Config(
        constants = BuildConfig.class,
        manifest = Config.NONE,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class})
@RunWith(RobolectricGradleTestRunner.class)
public class AccessibilityNodeInfoRefTest {

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @After
    public void tearDown() {
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test
    public void lastDescentWithoutChildren_shouldReturnFalseAndDoNotResetInnerNode() {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        node.setVisibleToUser(true);
        AccessibilityNodeInfoCompat compat = new AccessibilityNodeInfoCompat(node);
        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(compat);
        assertFalse(ref.lastDescendant());
        assertEquals(compat, ref.get());
        node.recycle();
        ref.recycle();
    }

    @Test
    public void lastDescentWithChildren_shouldReturnTrueAndSetItselfForLastChildren() {
        AccessibilityNodeInfo parentNode = AccessibilityNodeInfo.obtain();
        parentNode.setVisibleToUser(true);
        parentNode.setContentDescription("Parent");
        AccessibilityNodeInfo child1Node = AccessibilityNodeInfo.obtain();
        child1Node.setVisibleToUser(true);
        child1Node.setContentDescription("Child1");
        AccessibilityNodeInfo child2Node = AccessibilityNodeInfo.obtain();
        child2Node.setVisibleToUser(true);
        child2Node.setContentDescription("Child2");

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).addChild(child1Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).addChild(child2Node);

        AccessibilityNodeInfoCompat parentCompat = new AccessibilityNodeInfoCompat(parentNode);

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(parentCompat);
        System.out.println("lastDescentWithChildren_shouldReturnTrueAndSetItselfForLastChildren");
        assertTrue(ref.lastDescendant());
        assertEquals(child2Node, ref.get().getInfo());
        parentNode.recycle();
        child1Node.recycle();
        child2Node.recycle();
        ref.recycle();
    }

    @Test
    public void lastDescentWithLoop_shouldReturnFalse() {
        AccessibilityNodeInfo parentNode = AccessibilityNodeInfo.obtain();
        parentNode.setVisibleToUser(true);
        AccessibilityNodeInfo child1Node = AccessibilityNodeInfo.obtain();
        child1Node.setVisibleToUser(true);
        AccessibilityNodeInfo child2Node = AccessibilityNodeInfo.obtain();
        child2Node.setVisibleToUser(true);

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).addChild(child1Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).addChild(child2Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(child2Node)).addChild(parentNode);

        AccessibilityNodeInfoCompat parentCompat = new AccessibilityNodeInfoCompat(parentNode);

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(parentCompat);
        assertFalse(ref.lastDescendant());
        parentNode.recycle();
        child1Node.recycle();
        child2Node.recycle();
        ref.recycle();
    }

    @Test
    public void parentForNodeWithoutParent_shouldReturnFalseAndDoNotResetInnerNode() {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        node.setVisibleToUser(true);
        AccessibilityNodeInfoCompat compat = new AccessibilityNodeInfoCompat(node);
        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(compat);
        assertFalse(ref.parent());
        assertEquals(compat, ref.get());
        node.recycle();
        ref.recycle();
    }

    @Test
    public void parentWithParents_shouldReturnTrueAndSetItselfForParent() {
        AccessibilityNodeInfo currentNode = AccessibilityNodeInfo.obtain();
        currentNode.setVisibleToUser(true);
        AccessibilityNodeInfo parentNode = AccessibilityNodeInfo.obtain();
        parentNode.setVisibleToUser(true);

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).addChild(currentNode);

        AccessibilityNodeInfoCompat currentCompat = new AccessibilityNodeInfoCompat(currentNode);
        AccessibilityNodeInfoCompat parentCompat = new AccessibilityNodeInfoCompat(parentNode);

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(currentCompat);
        assertTrue(ref.parent());
        assertEquals(parentCompat, ref.get());
        parentNode.recycle();
        currentNode.recycle();
        ref.recycle();
    }

    @Test
    public void parentWithLoopedParents_shouldReturnFalse() {
        AccessibilityNodeInfo curNode = AccessibilityNodeInfo.obtain();
        curNode.setVisibleToUser(true);
        AccessibilityNodeInfo parentNode = AccessibilityNodeInfo.obtain();
        parentNode.setVisibleToUser(true);

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).addChild(curNode);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(curNode)).addChild(parentNode);

        /* Set nodes to be invisible to force the the code to traverse the loop */
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentNode)).setVisibleToUser(false);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(curNode)).setVisibleToUser(false);

        AccessibilityNodeInfoCompat currentCompat = new AccessibilityNodeInfoCompat(curNode);

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(currentCompat);
        assertFalse(ref.parent());
        parentNode.recycle();
        curNode.recycle();
        ref.recycle();
    }

    @Test
    public void parentWithSelfReferencedParent_shouldReturnFalse() {
        AccessibilityNodeInfo curNode = AccessibilityNodeInfo.obtain();
        curNode.setVisibleToUser(true);
        AccessibilityNodeInfo parentNode = AccessibilityNodeInfo.obtain();
        parentNode.setVisibleToUser(true);

        curNode.setContentDescription("Current");
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(curNode)).addChild(curNode);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(curNode)).setVisibleToUser(true);

        AccessibilityNodeInfoCompat currentCompat = new AccessibilityNodeInfoCompat(curNode);

        AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(currentCompat);
        assertFalse(ref.parent());
        parentNode.recycle();
        curNode.recycle();
        ref.recycle();
    }
}
