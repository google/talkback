/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.utils.traversal;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import com.android.talkback.R;

public class DirectionalTraversalStrategyTest extends TalkBackInstrumentationTestCase {

    /**
     * +-----+-------------+
     * |     |      2      |
     * |     +--------+----+
     * |  1  |    3   |  4 |
     * |     |        |    |
     * |     +--------+----+
     * |     |5 |6 |   7   |
     * +-----+--+--+-------+
     */

    private AccessibilityNodeInfoCompat mRoot;
    private AccessibilityNodeInfoCompat mNode1;
    private AccessibilityNodeInfoCompat mNode2;
    private AccessibilityNodeInfoCompat mNode3;
    private AccessibilityNodeInfoCompat mNode4;
    private AccessibilityNodeInfoCompat mNode5;
    private AccessibilityNodeInfoCompat mNode6;
    private AccessibilityNodeInfoCompat mNode7;

    private DirectionalTraversalStrategy mStrategy;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setContentView(R.layout.directional_traversal);
        initStrategy();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        AccessibilityNodeInfoUtils.recycleNodes(mRoot, mNode1, mNode2, mNode3, mNode4, mNode5,
                mNode6, mNode7);
        if (mStrategy != null) {
            mStrategy.recycle();
        }
    }

    @MediumTest
    public void testMoveRight1() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_RIGHT, mNode1, mNode3, mNode4);
    }

    @MediumTest
    public void testMoveRight2() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_RIGHT, mNode5, mNode6, mNode7);
    }

    @MediumTest
    public void testMoveRight3() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_RIGHT, mNode2,
                (AccessibilityNodeInfoCompat) null);
    }

    @MediumTest
    public void testMoveDown1() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_DOWN, mNode2, mNode3, mNode6);
    }

    @MediumTest
    public void testMoveDown2() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_DOWN, mNode4, mNode7);
    }

    @MediumTest
    public void testMoveLeft1() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_LEFT, mNode4, mNode3, mNode1);
    }

    @MediumTest
    public void testMoveLeft2() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_LEFT, mNode7, mNode6, mNode5, mNode1);
    }

    @MediumTest
    public void testMoveUp1() {
        assertOrder(TraversalStrategy.SEARCH_FOCUS_UP, mNode7, mNode4, mNode2);
    }

    @MediumTest
    public void testFocusInitialRight() {
        getService().getCursorController().setCursor(getNodeForId(R.id.directional_5));
        assertEquals(mNode1, mStrategy.focusInitial(mRoot, TraversalStrategy.SEARCH_FOCUS_RIGHT));
    }

    @MediumTest
    public void testFocusInitialLeft() {
        getService().getCursorController().setCursor(getNodeForId(R.id.directional_5));
        assertEquals(mNode7, mStrategy.focusInitial(mRoot, TraversalStrategy.SEARCH_FOCUS_LEFT));
    }

    @MediumTest
    public void testFocusInitialUp() {
        getService().getCursorController().setCursor(getNodeForId(R.id.directional_3));
        assertEquals(mNode6, mStrategy.focusInitial(mRoot, TraversalStrategy.SEARCH_FOCUS_UP));
    }

    @MediumTest
    public void testFocusInitialDown() {
        getService().getCursorController().setCursor(getNodeForId(R.id.directional_3));
        assertEquals(mNode2, mStrategy.focusInitial(mRoot, TraversalStrategy.SEARCH_FOCUS_DOWN));
    }

    private void initStrategy() {
        mRoot = getNodeForId(R.id.directional_root);
        mNode1 = getNodeForId(R.id.directional_1);
        mNode2 = getNodeForId(R.id.directional_2);
        mNode3 = getNodeForId(R.id.directional_3);
        mNode4 = getNodeForId(R.id.directional_4);
        mNode5 = getNodeForId(R.id.directional_5);
        mNode6 = getNodeForId(R.id.directional_6);
        mNode7 = getNodeForId(R.id.directional_7);

        mStrategy = new DirectionalTraversalStrategy(mRoot);
    }

    private void assertOrder(@TraversalStrategy.SearchDirection int direction,
            AccessibilityNodeInfoCompat fromNode,
            AccessibilityNodeInfoCompat... toNodes) {
        AccessibilityNodeInfoCompat currentNode = fromNode;

        int size = toNodes.length;
        for (int i = 0; i < size; ++i) {
            currentNode = mStrategy.findFocus(currentNode, direction);
            assertEquals(toNodes[i], currentNode);
        }
    }

}
