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

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

import com.android.talkback.R;

public class OrderedTraversalControllerTest extends TalkBackInstrumentationTestCase {

    /**
     *          n1
     *        /   \
     *      n2     n5
     *     / \    /  \
     *   n3  n4  n6  n7
     */

    private AccessibilityNodeInfoCompat mNode1;
    private AccessibilityNodeInfoCompat mNode2;
    private AccessibilityNodeInfoCompat mNode3;
    private AccessibilityNodeInfoCompat mNode4;
    private AccessibilityNodeInfoCompat mNode5;
    private AccessibilityNodeInfoCompat mNode6;
    private AccessibilityNodeInfoCompat mNode7;

    private OrderedTraversalController mController;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setContentView(R.layout.ordered_traversal);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        AccessibilityNodeInfoUtils.recycleNodes(mNode1, mNode2, mNode3, mNode4, mNode5, mNode6,
                mNode7);
        if (mController != null) {
            mController.recycle();
        }
    }

    @MediumTest
    public void testOrderWithoutReordering() {
        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode3, mNode4, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode4, mNode3, mNode2, mNode1);
    }

    @MediumTest
    public void testFirstSiblingsAfterSecond() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view3 = getActivity().findViewById(R.id.node3);
        view3.setAccessibilityTraversalAfter(R.id.node4);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode4, mNode3, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode3, mNode4, mNode2, mNode1);
    }

    @MediumTest
    public void testSecondSiblingsBeforeFirst() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view4 = getActivity().findViewById(R.id.node4);
        view4.setAccessibilityTraversalBefore(R.id.node3);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode4, mNode3, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode3, mNode4, mNode2, mNode1);
    }

    @MediumTest
    public void testFirstSiblingsBeforeSecond() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view3 = getActivity().findViewById(R.id.node3);
        view3.setAccessibilityTraversalBefore(R.id.node4);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode3, mNode4, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode4, mNode3, mNode2, mNode1);
    }

    @MediumTest
    public void testSecondSiblingsAfterFirst() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view4 = getActivity().findViewById(R.id.node4);
        view4.setAccessibilityTraversalAfter(R.id.node3);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode3, mNode4, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode4, mNode3, mNode2, mNode1);
    }

    @MediumTest
    public void testParentAfterNodeInOtherSubtree() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view2 = getActivity().findViewById(R.id.node2);
        view2.setAccessibilityTraversalAfter(R.id.node5);

        initController(false);
        assertForwardOrder(mNode1, mNode5, mNode6, mNode7, mNode2, mNode3, mNode4);
        assertBackwardOrder(mNode4, mNode3, mNode2, mNode7, mNode6, mNode5, mNode1);
    }

    @MediumTest
    public void testParentBeforeNodeInOtherSubtree() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view2 = getActivity().findViewById(R.id.node2);
        view2.setAccessibilityTraversalBefore(R.id.node7);

        initController(false);
        assertForwardOrder(mNode1, mNode5, mNode6, mNode2, mNode3, mNode4, mNode7);
        assertBackwardOrder(mNode7, mNode4, mNode3, mNode2, mNode6, mNode5, mNode1);
    }

    @MediumTest
    public void testMoveParentAfterChildKeepOrder() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view2 = getActivity().findViewById(R.id.node2);
        view2.setAccessibilityTraversalAfter(R.id.node4);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode3, mNode4, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode4, mNode3, mNode2, mNode1);
    }

    @MediumTest
    public void testMoveChildBeforeParent() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view4 = getActivity().findViewById(R.id.node4);
        view4.setAccessibilityTraversalBefore(R.id.node2);

        initController(false);
        assertForwardOrder(mNode1, mNode4, mNode2, mNode3, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode3, mNode2, mNode4, mNode1);
    }

    @MediumTest
    public void testMoveChildAfterParent() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view3 = getActivity().findViewById(R.id.node3);
        view3.setAccessibilityTraversalAfter(R.id.node2);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode4, mNode3, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode3, mNode4, mNode2, mNode1);
    }

    @MediumTest
    public void testMoveNodeAfterNodeInOtherSubtree() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view3 = getActivity().findViewById(R.id.node3);
        view3.setAccessibilityTraversalAfter(R.id.node6);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode4, mNode5, mNode6, mNode3, mNode7);
        assertBackwardOrder(mNode7, mNode3, mNode6, mNode5, mNode4, mNode2, mNode1);
    }

    @MediumTest
    public void testMoveNodeBeforeNodeInOtherSubtree() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view3 = getActivity().findViewById(R.id.node3);
        view3.setAccessibilityTraversalBefore(R.id.node7);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode4, mNode5, mNode6, mNode3, mNode7);
        assertBackwardOrder(mNode7, mNode3, mNode6, mNode5, mNode4, mNode2, mNode1);
    }

    @MediumTest
    public void testMultipleReorderingKeepReorderingChainCase1() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view3 = getActivity().findViewById(R.id.node3);
        View view7 = getActivity().findViewById(R.id.node7);
        view3.setAccessibilityTraversalBefore(R.id.node7);
        view7.setAccessibilityTraversalAfter(R.id.node2);

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode4, mNode3, mNode7, mNode5, mNode6);
        assertBackwardOrder(mNode6, mNode5, mNode7, mNode3, mNode4, mNode2, mNode1);
    }

    @MediumTest
    public void testMultipleReorderingKeepReorderingChainCase2() {
        if (!checkApiLevelSuffice()) {
            return;
        }

        View view2 = getActivity().findViewById(R.id.node2);
        View view7 = getActivity().findViewById(R.id.node7);
        view2.setAccessibilityTraversalAfter(R.id.node7);
        view7.setAccessibilityTraversalAfter(R.id.node1);

        initController(false);
        assertForwardOrder(mNode1, mNode5, mNode6, mNode7, mNode2, mNode3, mNode4);
        assertBackwardOrder(mNode4, mNode3, mNode2, mNode7, mNode6, mNode5, mNode1);
    }

    @MediumTest
    public void testWebDescendantsExcludedWhenNotRequested() {
        View view2 = getActivity().findViewById(R.id.node2);
        view2.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT);
            }
        });

        initController(false);
        assertForwardOrder(mNode1, mNode2, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode2, mNode1);
    }

    @MediumTest
    public void testWebDescendantsIncludedWhenRequested() {
        View view2 = getActivity().findViewById(R.id.node2);
        view2.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT);
            }
        });

        initController(true);
        assertForwardOrder(mNode1, mNode2, mNode3, mNode4, mNode5, mNode6, mNode7);
        assertBackwardOrder(mNode7, mNode6, mNode5, mNode4, mNode3, mNode2, mNode1);
    }

    // Should be called after view reordering
    private void initController(boolean includeViewsSupportingWebActionsInTree) {
        mNode1 = getNodeForId(R.id.node1);
        mNode2 = getNodeForId(R.id.node2);
        mNode3 = getNodeForId(R.id.node3);
        mNode4 = getNodeForId(R.id.node4);
        mNode5 = getNodeForId(R.id.node5);
        mNode6 = getNodeForId(R.id.node6);
        mNode7 = getNodeForId(R.id.node7);

        mController = new OrderedTraversalController();
        mController.initOrder(mNode1, includeViewsSupportingWebActionsInTree);
    }

    private void assertForwardOrder(AccessibilityNodeInfoCompat... nodes) {
        int size = nodes.length;
        AccessibilityNodeInfoCompat targetNode = mController.findFirst();
        assertEquals(nodes[0], targetNode);
        for (int i = 1; i < size; i++) {
            AccessibilityNodeInfoCompat node = nodes[i];
            targetNode = mController.findNext(targetNode);
            assertEquals(node, targetNode);
        }
    }

    private void assertBackwardOrder(AccessibilityNodeInfoCompat... nodes) {
        int size = nodes.length;
        AccessibilityNodeInfoCompat targetNode = mController.findLast();
        assertEquals(nodes[0], targetNode);
        for (int i = 1; i < size; i++) {
            AccessibilityNodeInfoCompat node = nodes[i];
            targetNode = mController.findPrevious(targetNode);
            assertEquals(node, targetNode);
        }
    }

    private boolean checkApiLevelSuffice() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }
}
