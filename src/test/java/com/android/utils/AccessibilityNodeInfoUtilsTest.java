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
 * Tests for AccessibilityNodeInfoUtils
 */

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfoCompat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import static org.junit.Assert.*;

@Config(
        emulateSdk = 18,
        shadows = {
                ShadowAccessibilityNodeInfoCompat.class,
                ShadowAccessibilityNodeInfo.class})
@RunWith(RobolectricTestRunner.class)
public class AccessibilityNodeInfoUtilsTest {
    private NodeFilter focusableFilter = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return node.isFocusable();
        }
    };

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @After
    public void tearDown() {
        assertFalse(ShadowAccessibilityNodeInfoCompat.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test
    public void searchFromBfsFromNullNode_shouldReturnNull() {
        assertNull(AccessibilityNodeInfoUtils.searchFromBfs(null, focusableFilter));
    }

    @Test
    public void searchFromBfsNodeMatchesSelf_shouldReturnSelf() {
        AccessibilityNodeInfoCompat compat =
                new AccessibilityNodeInfoCompat(AccessibilityNodeInfo.obtain());
        compat.setFocusable(true);
        AccessibilityNodeInfoCompat returned =
                AccessibilityNodeInfoUtils.searchFromBfs(compat, focusableFilter);
        assertEquals(compat, returned);
        compat.recycle();
        returned.recycle();
    }

    @Test
    public void searchFromBfsNodeMatchesChild_shouldReturnChild() {
        AccessibilityNodeInfo parent = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo child = AccessibilityNodeInfo.obtain();
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parent)).addChild(child);

        AccessibilityNodeInfoCompat parentCompat = new AccessibilityNodeInfoCompat(parent);
        AccessibilityNodeInfoCompat childCompat = new AccessibilityNodeInfoCompat(child);
        parent.setFocusable(false);
        child.setFocusable(true);
        AccessibilityNodeInfoCompat returned =
                AccessibilityNodeInfoUtils.searchFromBfs(parentCompat, focusableFilter);
        assertEquals(childCompat, returned);
        parentCompat.recycle();
        childCompat.recycle();
        returned.recycle();
    }

    @Test
    public void searchFromBfsNodeMatchesNothing_shouldReturnNull() {
        AccessibilityNodeInfoCompat compat =
                new AccessibilityNodeInfoCompat(AccessibilityNodeInfo.obtain());
        compat.setFocusable(false);
        assertNull(AccessibilityNodeInfoUtils.searchFromBfs(compat, focusableFilter));
        compat.recycle();
    }

    @Test
    public void getRootForNull_shouldReturnNull() {
        AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(null);
        assertNull(root);
    }

    @Test
    public void getRootForRootNode_shouldReturnSelf() {
        AccessibilityNodeInfoCompat compat =
                new AccessibilityNodeInfoCompat(AccessibilityNodeInfo.obtain());
        AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(compat);
        assertEquals(root, compat);
        compat.recycle();
        root.recycle();
    }

    @Test
    public void getRootForNonRootNode_shouldReturnRootNode() {
        AccessibilityNodeInfo level1Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level2Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level3Node = AccessibilityNodeInfo.obtain();
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level1Node)).addChild(level2Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level2Node)).addChild(level3Node);

        AccessibilityNodeInfoCompat startNode = new AccessibilityNodeInfoCompat(level3Node);
        AccessibilityNodeInfoCompat targetNode = new AccessibilityNodeInfoCompat(level1Node);
        AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoUtils.getRoot(startNode);
        assertEquals(targetNode, rootNode);
        level1Node.recycle();
        level2Node.recycle();
        level3Node.recycle();
        rootNode.recycle();
    }

    @Test
    public void getRootForNodeWithLoop_shouldReturnNull() {
        AccessibilityNodeInfo level1Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level2Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level3Node = AccessibilityNodeInfo.obtain();

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level1Node)).addChild(level2Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level2Node)).addChild(level3Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level3Node)).addChild(level1Node);

        AccessibilityNodeInfoCompat startNode = new AccessibilityNodeInfoCompat(level3Node);
        AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoUtils.getRoot(startNode);
        assertNull(rootNode);
        level1Node.recycle();
        level2Node.recycle();
        level3Node.recycle();
    }

    @Test(timeout=100)
    public void isFocusableWithLoop_shouldNotHang() {
        AccessibilityNodeInfo level1Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level2Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level3Node = AccessibilityNodeInfo.obtain();

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level1Node)).addChild(level2Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level2Node)).addChild(level3Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level3Node)).addChild(level1Node);

        AccessibilityNodeInfoCompat startNode = new AccessibilityNodeInfoCompat(level1Node);
        AccessibilityNodeInfoUtils.isAccessibilityFocusable(startNode);
        level1Node.recycle();
        level2Node.recycle();
        level3Node.recycle();
    }

    @Test(timeout=100)
    public void shouldFocusNodeWithLoop_shouldNotHang() {
        AccessibilityNodeInfo level1Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level2Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level3Node = AccessibilityNodeInfo.obtain();

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level1Node)).addChild(level2Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level2Node)).addChild(level3Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level3Node)).addChild(level1Node);

        AccessibilityNodeInfoCompat startNode = new AccessibilityNodeInfoCompat(level1Node);
        AccessibilityNodeInfoUtils.shouldFocusNode(startNode);
        level1Node.recycle();
        level2Node.recycle();
        level3Node.recycle();
    }

    @Test
    public void hasAncestorWithSameAncestor_shouldReturnTrue() {
        AccessibilityNodeInfoCompat targetAncestor = AccessibilityNodeInfoCompat.obtain();
        AccessibilityNodeInfoCompat childNode = AccessibilityNodeInfoCompat.obtain();

        ((ShadowAccessibilityNodeInfoCompat) ShadowExtractor.extract(targetAncestor))
                .addChild(childNode);

        assertTrue(AccessibilityNodeInfoUtils.hasAncestor(childNode, targetAncestor));

        targetAncestor.recycle();
        childNode.recycle();
    }

    @Test
    public void hasAncestorWithoutSameAncestor_shouldReturnFalse() {
        AccessibilityNodeInfoCompat targetAncestor = AccessibilityNodeInfoCompat.obtain();
        AccessibilityNodeInfoCompat childNode = AccessibilityNodeInfoCompat.obtain();

        assertFalse(AccessibilityNodeInfoUtils.hasAncestor(childNode, targetAncestor));

        targetAncestor.recycle();
        childNode.recycle();
    }
}
