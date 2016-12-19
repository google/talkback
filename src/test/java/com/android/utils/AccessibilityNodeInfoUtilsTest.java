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
import com.android.talkback.BuildConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import static org.junit.Assert.*;

@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class})
@RunWith(RobolectricGradleTestRunner.class)
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
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
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
        AccessibilityNodeInfo targetInfo = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo childInfo = AccessibilityNodeInfo.obtain();
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(targetInfo)).addChild(childInfo);
        AccessibilityNodeInfoCompat targetAncestor = new AccessibilityNodeInfoCompat(targetInfo);
        AccessibilityNodeInfoCompat childNode = new AccessibilityNodeInfoCompat(childInfo);

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

    private void addChild(AccessibilityNodeInfoCompat parent, AccessibilityNodeInfoCompat child) {
        AccessibilityNodeInfo parentInfo = (AccessibilityNodeInfo) parent.getInfo();
        AccessibilityNodeInfo childInfo = (AccessibilityNodeInfo) child.getInfo();
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parentInfo)).addChild(childInfo);
    }

    private AccessibilityNodeInfoCompat obtainTestNode(String contentDescription,
            boolean focusable) {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        node.setContentDescription(contentDescription);
        node.setFocusable(focusable);
        return new AccessibilityNodeInfoCompat(node);
    }

    @Test(timeout=100)
    public void hasMatchingDescendant_withLoop_shouldNotHang() {
        // (a)---->(b)----->(c)---->(d)---->(e)---->(f*)
        //  |       ^        ^       |
        //  +-----  |  ------+       |
        //          +----------------+

        AccessibilityNodeInfoCompat a = obtainTestNode("a", false);
        AccessibilityNodeInfoCompat b = obtainTestNode("b", false);
        AccessibilityNodeInfoCompat c = obtainTestNode("c", false);
        AccessibilityNodeInfoCompat d = obtainTestNode("d", false);
        AccessibilityNodeInfoCompat e = obtainTestNode("e", false);
        AccessibilityNodeInfoCompat f = obtainTestNode("f", true);

        addChild(a, b);
        addChild(a, c);
        addChild(b, c);
        addChild(c, d);
        addChild(d, a);
        addChild(d, e);
        addChild(e, f);

        assertTrue(AccessibilityNodeInfoUtils.hasMatchingDescendant(a, focusableFilter));

        a.recycle();
        b.recycle();
        c.recycle();
        d.recycle();
        e.recycle();
        f.recycle();
    }

    @Test(timeout=100)
    public void hasMatchingDescendant_withSelfLoop_shouldNotHang() {
        // (a)---->(b)---->(c)---->(d*)
        //         ^ \              ^ \
        //         \_/              \_/

        AccessibilityNodeInfoCompat a = obtainTestNode("a", false);
        AccessibilityNodeInfoCompat b = obtainTestNode("b", false);
        AccessibilityNodeInfoCompat c = obtainTestNode("c", false);
        AccessibilityNodeInfoCompat d = obtainTestNode("d", true);

        addChild(a, b);
        addChild(b, b);
        addChild(b, c);
        addChild(c, d);
        addChild(d, d);

        assertTrue(AccessibilityNodeInfoUtils.hasMatchingDescendant(a, focusableFilter));

        a.recycle();
        b.recycle();
        c.recycle();
        d.recycle();
    }

    @Test
    public void countMatchingAncestors() {
        // (a)---->(b)----->(c*)---->(d)---->(e*)---->(f*)

        AccessibilityNodeInfoCompat a = obtainTestNode("a", false);
        AccessibilityNodeInfoCompat b = obtainTestNode("b", false);
        AccessibilityNodeInfoCompat c = obtainTestNode("c", true);
        AccessibilityNodeInfoCompat d = obtainTestNode("d", false);
        AccessibilityNodeInfoCompat e = obtainTestNode("e", true);
        AccessibilityNodeInfoCompat f = obtainTestNode("f", true);

        addChild(a, b);
        addChild(b, c);
        addChild(c, d);
        addChild(d, e);
        addChild(e, f);

        assertEquals(2, AccessibilityNodeInfoUtils.countMatchingAncestors(f, focusableFilter));

        a.recycle();
        b.recycle();
        c.recycle();
        d.recycle();
        e.recycle();
        f.recycle();
    }

    @Test(timeout=100)
    public void countMatchingAncestors_withLoop_shouldNotHang() {
        // (a)---->(b)----->(c*)---->(d)---->(e*)---->(f*)
        //  ^                         |
        //  |                         |
        //  +<------------------------+

        AccessibilityNodeInfoCompat a = obtainTestNode("a", false);
        AccessibilityNodeInfoCompat b = obtainTestNode("b", false);
        AccessibilityNodeInfoCompat c = obtainTestNode("c", true);
        AccessibilityNodeInfoCompat d = obtainTestNode("d", false);
        AccessibilityNodeInfoCompat e = obtainTestNode("e", true);
        AccessibilityNodeInfoCompat f = obtainTestNode("f", true);

        addChild(a, b);
        addChild(b, c);
        addChild(c, d);
        addChild(d, a);
        addChild(d, e);
        addChild(e, f);

        assertEquals(0, AccessibilityNodeInfoUtils.countMatchingAncestors(f, focusableFilter));

        a.recycle();
        b.recycle();
        c.recycle();
        d.recycle();
        e.recycle();
        f.recycle();
    }

}
