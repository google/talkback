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

import com.android.talkback.BuildConfig;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.TargetApi;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;

import android.graphics.Rect;

import com.android.switchaccess.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Tests for OptionScanSelectionNode.
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class OptionScanSelectionNodeTest {
    private static final Rect NODE_BOUNDS_1 = new Rect(10, 10, 90, 20);
    private static final Rect NODE_BOUNDS_2 = new Rect(110, 110, 190, 120);
    private static final Rect NODE_BOUNDS_3 = new Rect(210, 210, 290, 220);

    private OptionScanNode mMockNode1 = mock(OptionScanNode.class);
    private OptionScanNode mMockNode2 = mock(OptionScanNode.class);
    private OptionScanNode mMockNode3 = mock(OptionScanNode.class);

    @Captor
    ArgumentCaptor<Iterable<Rect>> mRectSetCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockNode1.getRectsForNodeHighlight())
                .thenReturn(new HashSet<>(Arrays.asList(NODE_BOUNDS_1)));
        when(mMockNode2.getRectsForNodeHighlight())
                .thenReturn(new HashSet<>(Arrays.asList(NODE_BOUNDS_2, NODE_BOUNDS_3)));
    }

    @Test
    public void testThreeOptions_shouldHaveThreeChildren() {
        OptionScanSelectionNode node =
                new OptionScanSelectionNode(mMockNode1, mMockNode2, mMockNode3);
        assertEquals(3, node.getChildCount());
    }

    @Test
    public void testThreeOptions_returnsChildrenInOrder() {
        OptionScanSelectionNode node =
                new OptionScanSelectionNode(mMockNode1, mMockNode2, mMockNode3);
        assertEquals(mMockNode1, node.getChild(0));
        assertEquals(mMockNode2, node.getChild(1));
        assertEquals(mMockNode3, node.getChild(2));
    }

    @Test
    public void outOfBoundsChild_returnsNull() {
        OptionScanSelectionNode node =
                new OptionScanSelectionNode(mMockNode1, mMockNode2, mMockNode3);
        assertNull(node.getChild(-1));
        assertNull(node.getChild(3));
    }

    @Test
    public void getRectsForNodeHighlight_shouldReturnAllRects() {
        OptionScanSelectionNode node = new OptionScanSelectionNode(mMockNode1, mMockNode2);
        Set<Rect> returnedRects = node.getRectsForNodeHighlight();
        assertEquals(3, returnedRects.size());
        assertTrue(returnedRects.contains(NODE_BOUNDS_1));
        assertTrue(returnedRects.contains(NODE_BOUNDS_2));
        assertTrue(returnedRects.contains(NODE_BOUNDS_3));
    }

    @Test
    public void showSelections_highlightsPerimetersInPaints() {
        OptionScanSelectionNode node = new OptionScanSelectionNode(mMockNode1, mMockNode2);
        OverlayController mockOverlayController = mock(OverlayController.class);
        Paint[] paints = new Paint[2];
        paints[0] = new Paint();
        paints[0].setColor(Color.WHITE);
        paints[1] = new Paint();
        paints[1].setColor(Color.RED);
        node.showSelections(mockOverlayController, paints);

        /* Verify first highlight */
        verify(mockOverlayController).highlightPerimeterOfRects(
                mRectSetCaptor.capture(), eq(paints[0]));
        Iterator<Rect> rectIterator1 = mRectSetCaptor.getValue().iterator();
        assertEquals(NODE_BOUNDS_1, rectIterator1.next());
        assertFalse(rectIterator1.hasNext());
        /* Verify second highlight */
        verify(mockOverlayController).highlightPerimeterOfRects(
                mRectSetCaptor.capture(), eq(paints[1]));
        Iterator<Rect> rectIterator2 = mRectSetCaptor.getValue().iterator();
        Rect rect1 = rectIterator2.next();
        Rect rect2 = rectIterator2.next();
        assertFalse(rectIterator2.hasNext());
        assertTrue((rect1.equals(NODE_BOUNDS_2)) || (rect2.equals(NODE_BOUNDS_2)));
        assertTrue((rect1.equals(NODE_BOUNDS_3)) || (rect2.equals(NODE_BOUNDS_3)));
    }
}
